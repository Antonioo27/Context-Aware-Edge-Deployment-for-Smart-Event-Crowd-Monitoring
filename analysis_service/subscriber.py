"""
Modulo che si connette al broker MQTT, si sottoscrive al topic della propria area
valida e deserializza batch, scarta i duplicati, misura latenza di trasporto e consegna i batch ad un consumatore
tramite una coda thread-safe.

"""

import json
import logging
import queue
import threading
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Iterator
from .models import ProbeBatch, InvalidBatchError, Probe
from .config import AnalysisConfig

 
import paho.mqtt.client as mqtt
from paho.mqtt.enums import CallbackAPIVersion
 
LOGGER = logging.getLogger(__name__)


# Parsing

def _parse_iso8601(value: str):
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    dt = datetime.fromisoformat(value)
    if dt.tzinfo is None:                      # timestamp "naive": lo assumiamo UTC
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)

def parse_batch(
    payload: bytes,
    *,
    expected_area: str | None,
    received_at: datetime,
    mid: int = 0,
    qos: int = 1,
):

    """
    Trasforma il payload JSON in un ProbeBatch
    
    Se il batch è rotto -> InvalidBatchError, il messaggio viene scartato
    Se un singolo probe è rotto lo salto e lo conto
    """

    try:
        data: Any = json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise InvalidBatchError(f"payload non e' JSON UTF-8 valido: {exc}") from exc

    if not isinstance(data, dict):
        raise InvalidBatchError(f"payload non e' un oggetto JSON: {data!r}")

    for campo in ("area_id", "batch_id", "sent_at", "probes"):
        if campo not in data:
            raise InvalidBatchError(f"campo mancante: {campo}")

    area_id = str(data["area_id"])
    if expected_area is not None and area_id != expected_area:
        raise InvalidBatchError(f"area_id {area_id} non corrisponde a {expected_area}")

    try: 
        batch_id = int(data["batch_id"])
        sent_at = _parse_iso8601(data["sent_at"])
    except (TypeError, ValueError) as exc:
        raise InvalidBatchError(f"campo batch_id o sent_at non valido: {exc}") from exc

    raw_probes = data["probes"]
    if not isinstance(raw_probes, list):
        raise InvalidBatchError(f"campo probes non e' una lista: {raw_probes!r}")

    probes: list[Probe] = []
    malformed_probes = 0
    for item in raw_probes:
        try:
            probes.append(Probe(sensor_id=str(item["sensor_id"]), ts=_parse_iso8601(item["ts"]), mac=str(item["mac"]), rssi=int(item["rssi"])))
        except (TypeError, ValueError, KeyError) as exc:
            malformed_probes += 1

    return ProbeBatch(
        area_id=area_id,
        batch_id=batch_id,
        sent_at=sent_at,
        received_at=received_at,
        count_declared=int(data.get("count", len(probes))),
        probes=probes,
        malformed_probes=malformed_probes,
        mid=mid,
        qos=qos,
    )


 
@dataclass(slots=True)
class SubscriberStats:
    connected: bool = False
    connect_attempts: int = 0
    unexpected_disconnects: int = 0
    batches_received: int = 0
    batches_invalid: int = 0
    batches_duplicated: int = 0
    batches_dropped_full: int = 0
    probes_received: int = 0
    probes_malformed: int = 0
    last_batch_at: datetime | None = None
    last_latency_ms: float | None = None
    _latency_sum_ms: float = field(default=0.0, repr=False)
 
    @property
    def avg_latency_ms(self) -> float | None:
        if self.batches_received == 0:
            return None
        return self._latency_sum_ms / self.batches_received



# Subscriber

class ProbeSubscriber:
    """
    Sottoscrive un topic MQTT, deserializza i batch e li mette in una coda thread-safe.
    """

    def __init__(self, config: AnalysisConfig, logger: logging.Logger | None = None):

        self._cfg = config
        self._log = logger or LOGGER

        self._queue = queue.Queue[ProbeBatch](maxsize=config.max_queue_size)

        # Dedup: deque è una collections la cui quando viene aggiunto un elemento se è arrivata al limite scarta il più vecchio automaticamente. 
        # Usiamo anche il set per questioni di velocità nella ricerca
        self._seen_order: deque[int] = deque(maxlen=config.dedup_window)
        self._seen_ids: set[int] = set()

        self._lock = threading.Lock()
        self._stats = SubscriberStats()
        self._connected_event = threading.Event()
        self._stopping = threading.Event()
 
        self._client = self._build_client()

    def _build_client(self):
        cfg = self._cfg
        client = mqtt.Client(
            callback_api_version=CallbackAPIVersion.VERSION2,
            client_id=cfg.subscriber_client_id,
            clean_session=cfg.mqtt_clean_session,
            protocol=mqtt.MQTTv311,
            manual_ack=cfg.manual_ack,
        )

        if cfg.mqtt_username:
            client.username_pw_set(cfg.mqtt_username, cfg.mqtt_password)

        client.reconnect_delay_set(
            min_delay=cfg.reconnect_min_delay,
            max_delay=cfg.reconnect_max_delay,
        )

        # Last Will, pod muore senza disconnettersi.
        if cfg.status_topic:
            client.will_set(
                cfg.status_topic,
                payload=json.dumps({"area_id": cfg.area_id, "status": "offline"}),
                qos=1,
                retain=True,
            )

        client.on_connect = self._on_connect
        client.on_disconnect = self._on_disconnect
        client.on_subscribe = self._on_subscribe
        client.on_message = self._on_message
        return client


    def start(self):
        """
        Avvia il client in background

        connect_async + loop_start
        """

        cfg = self._cfg
        self._log.info(
            "Avvio subscriber area=%s client_id=%s topic=%s qos=%d clean_session=%s",
            cfg.area_id, cfg.subscriber_client_id, cfg.topic_probes, cfg.mqtt_qos, cfg.mqtt_clean_session,
        )

        self._client.connect_async(cfg.mqtt_host, cfg.mqtt_port, cfg.mqtt_keepalive)
        self._client.loop_start()

    def stop(self):
        """
        Disconnessione pulita
        """
    
        self._stopping.set()
        try:
            self._client.disconnect()
        except Exception:
            self._log.exception("Errore durante la disconnessione: %s", e)
        finally:
            self._client.loop_stop()
            self._log.info("Subscriber fermato: %s", self.stats)

    def __enter__(self):
        self.start()
        return self

    def __exit__(self, exc_type, exc, tb):
        self.stop()

    def wait_connected(self, timeout: float = 10.0):
        """
        Attende la prima connessione riuscita
        """
        return self._connected_event.wait(timeout)

    # Callback paho

    def _on_connect(self, client, userdata, connect_flags, reason_code, properties):
        with self._lock:
            self._stats.connect_attempts += 1

        if reason_code.is_failure:
            self._log.error("Connessione al broker rifiutata: %s", reason_code)
            return

        # Flag session present se true vuol dire che il broker ha trovato una sessione persistente con lo stesso client_id
        session_present = bool(getattr(connect_flags, "session_present", False))
        self._log.info("Connesso al broker, session_present=%s", session_present)

        # Ci si iscrive sempre, la subscribe è idempotente
        result, _ = client.subscribe(self._cfg.topic_probes, qos=self._cfg.mqtt_qos)
        if result != mqtt.MQTT_ERR_SUCCESS:
            self._log.error("Invio SUBSCRIBE fallito: codice %s", result)
            return

        with self._lock:
            self._stats.connected = True
        self._connected_event.set()

        if self._cfg.status_topic:
            client.publish(
                self._cfg.status_topic,
                payload=json.dumps({"area_id": self._cfg.area_id, "status": "online"}),
                qos=1,
                retain=True,
            )

    def _on_subscribe(self, client, userdata, mid, reason_code_list, properties):
        for rc in reason_code_list:
            if rc.is_failure:
                self._log.error("Sottoscrizione a %s rifiutata: %s", self._cfg.topic_probes, rc)
            else:
                self._log.info("Sottoscritto a %s (QoS concesso=%s)", self._cfg.topic_probes, rc.value)

    def _on_disconnect(self, client, userdata, disconnect_flags, reason_code, properties):
        self._connected_event.clear()
        with self._lock:
            self._stats.connected = False
            if not self._stopping.is_set():
                self._stats.unexpected_disconnects += 1

        if self._stopping.is_set():
            self._log.info("Disconnessione richiesta dall'applicazione")
        else:
            self._log.warning("Disconnesso dal broker (%s): riconnessione in corso", reason_code)

    def _on_message(self, client, userdata, message: mqtt.MQTTMessage):
        received_at = datetime.now(timezone.utc)
        try: 
            batch = parse_batch(
                message.payload,
                expected_area=self._cfg.area_id,
                received_at=received_at,
                mid=message.mid,
                qos=message.qos,
            )
        # Gestione messaggi corrotti
        except InvalidBatchError as exc:
            with self._lock:
                self._stats.batches_invalid += 1
            self._log.warning("Batch scartato su %s: %s", message.topic, exc)
            # Batch irrecuperabile, lo confermiamo altrimenti viene rimandato
            self._ack_raw(message.mid, message.qos)
            return
        
        except Exception as exc:   
            self._log.exception("Errore inatteso nel parsing del messaggio")
            self._ack_raw(message.mid, message.qos)
            return


        # Duplicati: con QoS il broker può consegnare due volte lo stesso batch
        with self._lock:
            if batch.batch_id in self._seen_ids:
                self._stats.batches_duplicated += 1
                duplicato = True
            else:
                duplicato = False
                if len(self._seen_order) == self._seen_order.maxlen:
                    self._seen_ids.discard(self._seen_order[0])  # esce dalla finestra
                self._seen_order.append(batch.batch_id)
                self._seen_ids.add(batch.batch_id)

        if duplicato:
            self._log.debug("Batch %s gia' visto: ignorato", batch.batch_id)
            self._ack_raw(message.mid, message.qos)
            return

        try: 
            self._queue.put_nowait(batch)
        except queue.Full:
            with self._lock:
                self._stats.batches_dropped_full += 1

            with self._lock:
                    self._seen_ids.discard(batch.batch_id)
            self._log.error(
                "Coda piena (%d): batch %s non accodato, il consumatore e' troppo lento",
                self._cfg.max_queue_size, batch.batch_id,
            )
            return

        with self._lock:
            self._stats.batches_received += 1
            self._stats.probes_received += batch.count_effective
            self._stats.probes_malformed += batch.malformed_probes
            self._stats.last_batch_at = received_at
            self._stats.last_latency_ms = batch.transport_latency_ms
            self._stats._latency_sum_ms += batch.transport_latency_ms

    # consumo

    def get(self, timeout:float | None = 1.0):
        """
        Preleva un batch dalla coda
        """
        try:
            return self._queue.get(timeout=timeout)
        except queue.Empty:
            return None

    def batches(self, poll_timeout: float = 1.0):
        """
        Iteratore bloccante sui batch ricevuti.
        Si interrompe quando viene chiamato stop()
        """

        while not self._stopping.is_set():
            batch = self.get(timeout=poll_timeout)
            if batch is not None:
                yield batch

    def ack(self, batch: ProbeBatch):
        """
        Conferma il batch al broker. Da chiamarlo dopo aver elaborato il batch
        """
        self._ack_raw(batch.mid, batch.qos)

    def _ack_raw(self, mid: int, qos: int):
        if not self._cfg.manual_ack or qos == 0:
            return
        try:
            self._client.ack(mid, qos)
        except Exception:                        
            self._log.exception("Ack del messaggio mid=%s fallito", mid)


    # diagnostica

    @property
    def is_connected(self):
        return self._connected_event.is_set()

    @property
    def pending(self):
        """Batch in coda non ancora elaborati."""
        return self._queue.qsize()

    @property
    def stats(self):
        """Fotografia per /healthz, log periodici e dashboard."""
        with self._lock:
            s = self._stats
            return {
                "area_id": self._cfg.area_id,
                "client_id": self._cfg.subscriber_client_id,
                "topic": self._cfg.topic_probes,
                "connected": s.connected,
                "connect_attempts": s.connect_attempts,
                "unexpected_disconnects": s.unexpected_disconnects,
                "batches_received": s.batches_received,
                "batches_invalid": s.batches_invalid,
                "batches_duplicated": s.batches_duplicated,
                "batches_dropped_full": s.batches_dropped_full,
                "probes_received": s.probes_received,
                "probes_malformed": s.probes_malformed,
                "queue_size": self._queue.qsize(),
                "last_batch_at": s.last_batch_at.isoformat() if s.last_batch_at else None,
                "last_latency_ms": s.last_latency_ms,
                "avg_latency_ms": s.avg_latency_ms,
            }

    def is_healthy(self, max_silence_s: float = 60.0):
        """
        Restituisce True se siamo connessi e abbiamo ricevuto qualcosa di recente.
        Utile per la liveness probe
        """
        if not self.is_connected:
            return False
        with self._lock:
            last = self._stats.last_batch_at
        if last is None:
            return True                          # appena avviato: non ancora giudicabile
        return (datetime.now(timezone.utc) - last).total_seconds() <= max_silence_s
