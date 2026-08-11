"""
Transporto MQTT Multi-broker del simulatore
Il buffer è partizionato per area, ogni area ha il suo topic (`event/probes/<area_id>`)
Il trasporto è MQTT con QoS 1, la publish() fatta da paho non è bloccante, il messaggio viene messo in una coda interna
e un thread di rete lo spedisce.

La conferma dell'invio (PUBACK) arriva in modo asincrono nella callback, da qui la distinzione
batch spediti e batch confermati.
"""


import json
from logging import config
import logging
import threading
import time
from datetime import datetime, timezone
from urllib.parse import urlparse
 
import paho.mqtt.client as mqtt

from .config import SimConfig
from .models import ProbeEvent


logger = logging.getLogger("simulator.transport")

class SingleBrokerConnection:
    """
    Rappresenta una singola connessione MQTT verso un broker specifico 
    """

    def __init__(
        self,
        broker_url: str,
        client_id_prefix: str,
        config: SimConfig,
        on_publish_cb,
        on_disconnect_cb,
    ):
        
        self.broker_url = broker_url
        self.config = config 

        parsed = urlparse(broker_url if "://" in broker_url else f"tcp://{broker_url}")
        self.host = parsed.hostname or "localhost"
        self.port = parsed.port or 1883

        # ID univoco per evitare conflitti tra connessioni allo stesso simulatore
        client_id = f"{client_id_prefix}-{self.host}-{self.port}"

        self.client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=client_id,
            protocol=mqtt.MQTTv311,
            clean_session=config.mqtt_clean_session,
        )

        self.client.reconnect_delay_set(min_delay=1, max_delay=8)
        self.client.max_inflight_messages_set(40)
        self.client.max_queued_messages_set(1000)

        self.client.will_set(
            f"{config.mqtt_topic_prefix}/status/simulator",
            json.dumps({"state": "offline", "reason": "lwt", "broker": self.host}),
            qos=1,
            retain=True,
        )

        self.client.on_connect = self._on_connect
        self.client.on_disconnect = on_disconnect_cb
        self.client.on_publish = self._on_publish_wrapper(on_publish_cb)

    def _on_publish_wrapper(self, external_cb):
        def _cb(client, userdata, mid, *args):
            external_cb(client, userdata, mid, *args)
        return _cb

    def _on_connect(self, client, userdata, flags, reason_code, properties=None):
        if reason_code == 0:
            logger.info(f"[Transport] Connesso al broker: {self.host}:{self.port}")
            client.publish(
                f"{self.config.mqtt_topic_prefix}/status/simulator",
                json.dumps({"state": "online", "broker": self.host}),
                qos=1,
                retain=True,
            )
        else:
            logger.warning(f"[Transport] Connessione fallita a {self.host}:{self.port} con codice {reason_code}")

    def start(self):
        try:
            self.client.connect_async(self.host, self.port, keepalive=self.config.mqtt_keepalive)
            self.client.loop_start()
        except Exception as e:
            logger.error(f"[Transport] Errore avvio connessione a {self.broker_url}: {e}")

    def is_connected(self) -> bool:
        return self.client.is_connected()

    def publish(self, topic: str, payload: str, qos: int):
        return self.client.publish(topic, payload, qos=qos, retain=False)

    def close(self):
        try:
            self.client.publish(
                f"{self.config.mqtt_topic_prefix}/status/simulator",
                json.dumps({"state": "offline", "reason": "shutdown", "broker": self.host}),
                qos=1,
                retain=True,
            )
            self.client.disconnect()
            self.client.loop_stop()
        except Exception:
            pass

class ProbePublisher:
    """
    Pubblica i probe sui broker MQTT in BROADCAST (un topic per area).
    Mantiene l'interfaccia compatibile con SimulationEngine (engine.py).
    """

    def __init__(self, config: SimConfig):
        
        self.config = config
        self._area_ids = [a.area_id for a in config.areas if a.monitored]

        # Buffer per ogni area
        self._buffers: dict[str, list[ProbeEvent]] = {a: [] for a in self._area_ids}
        # Contatore di batch per ogni area
        run_offset = int(time.time())
        self._batch_seq: dict[str, int] = {a: run_offset for a in self._area_ids}
        # Timer di flush per ogni area
        self._last_flush_sim: dict[str, float] = {a: 0.0 for a in self._area_ids}

        # Tetto del buffer, se il broker resta giù a lungo, scartiamo probe più vecchi
        self._max_buffer_per_area = max(500, config.batch_max_events * 20)

        # Serve un lock per le statistiche
        self._lock = threading.Lock()
        self._pending_mids: set[int] = set()
        self.stats = {
            "probes_published": 0,   # probe consegnati a paho
            "probes_requeued": 0,    # publish rifiutata, probe rimasti in buffer
            "probes_dropped": 0,     # scartati per tetto del buffer
            "probes_unknown_area": 0,
            "batches_published": 0,
            "batches_acked": 0,      # PUBACK ricevuti dal broker
            "reconnects": 0,
        }

        self._brokers: list[SingleBrokerConnection] = []
        self._init_broker_pool()

    def _init_broker_pool(self):
        urls = self.config.broker_urls if self.config.broker_urls else ["tcp://localhost:1883"]
        logger.info(f"[Transport] Inizializzazione pool broadcast per {len(urls)} broker: {urls}")

        for idx, url in enumerate(urls):
            conn = SingleBrokerConnection(
                broker_url=url,
                client_id_prefix=f"{self.config.mqtt_client_id}-{idx}",
                config=self.config,
                on_publish_cb=self._on_publish,
                on_disconnect_cb=self._on_disconnect,
            )
            self._brokers.append(conn)

    

    def start(self):
        """
        Avvia le connessioni ed i thread di rete per ciascun broker
        """
        for b in self._brokers:
            b.start()

    def _on_disconnect(self, client, userdata, *args):
        with self._lock:
            self.stats["reconnects"] += 1

    def _on_publish(self, client, userdata, mid, *args):
        """
        PUBACK ricevuto da uno dei broker
        """
        with self._lock:
            key = (client, mid)
            if key in self._pending_mids:
                self._pending_mids.discard(key)
                self.stats["batches_acked"] += 1

    def is_connected(self) -> bool:
        """Ritorna True se almeno UN broker del pool è connesso"""
        return any(b.is_connected() for b in self._brokers)

    # API usata dal motore di simulazione

    def enqueue(self, events: list[ProbeEvent], now: float):
        """
        Smista gli eventi nei buffer per area, aree che superano batch_max_events vengono inviate subito.
        """
        touched: set[str] = set()
        unknown = 0

        for e in events:
            area_id = getattr(e, "area_id", None)
            buf = self._buffers.get(area_id)
            if buf is None:
                unknown += 1
                continue
            buf.append(e)
            touched.add(area_id)

        if unknown:
            with self._lock:
                self.stats["probes_unknown_area"] += unknown

        for area_id in touched:
            if len(self._buffers[area_id]) >= self.config.batch_max_events:
                self._flush_area(area_id, now)
        
    def flush_if_due(self, now: float):
        """
        Spedisce i buffer per cui è passato batch_max_seconds dall'ultimo invio. Il timer è per area
        """
        for area_id, buf in self._buffers.items():
            if buf and (now - self._last_flush_sim[area_id]) >= self.config.batch_max_seconds:
                self._flush_area(area_id, now)

    def flush(self, now: float | None = None):
        """
        Forza l'invio di tutti i buffer, indipendentemente dal timer
        """
        for area_id in self._area_ids:
            self._flush_area(area_id, now if now is not None else self._last_flush_sim[area_id])


    # Invio

    def _flush_area(self, area_id: str, now: float):
        buf = self._buffers[area_id]
        if not buf:
            return

        # Se nessun broker è connesso, conserviamo i dati nei buffer
        if not self.is_connected():
            self._enforce_buffer_cap(area_id)
            return

        while buf:
            chunk = buf[: self.config.batch_max_events]
            if not self._publish_batch(area_id, chunk):
                break
            del buf[: len(chunk)]

        self._last_flush_sim[area_id] = now
        self._enforce_buffer_cap(area_id)

    def _publish_batch(self, area_id: str, chunk: list[ProbeEvent]):
        cfg = self.config

        # Batch_id progressivo per area, il ricevente può accorgersi di un buco nella sequenza
        # e quantificare i batch persi.
        batch_id = self._batch_seq[area_id] + 1

        payload = {
            "area_id": area_id,
            "sensor_id": cfg.sensor_for_area(area_id),
            "batch_id": batch_id,
            # Ora di parete (UTC), NON tempo simulato: serve al ricevente per
            # misurare la latenza di trasporto contro il proprio orologio.
            "sent_at": datetime.now(timezone.utc).isoformat(timespec="milliseconds"),
            "count": len(chunk),
            "probes": [self._probe_payload(e) for e in chunk],
        }

        raw = json.dumps(payload, separators=(",", ":"))
        topic = f"{cfg.mqtt_topic_prefix}/probes/{area_id}"

        published_any = False
        for broker in self._brokers:
            if broker.is_connected():
                info = broker.publish(topic, raw, qos=cfg.mqtt_qos)
                if info.rc == mqtt.MQTT_ERR_SUCCESS:
                    published_any = True
                    with self._lock:
                        if cfg.mqtt_qos > 0:
                            self._pending_mids.add((broker.client, info.mid))

        if not published_any:
            with self._lock:
                self.stats["probes_requeued"] += len(chunk)
            return False

        self._batch_seq[area_id] = batch_id

        with self._lock:
            self.stats["probes_published"] += len(chunk)
            self.stats["batches_published"] += 1

        return True

    @staticmethod
    def _probe_payload(event: ProbeEvent):
        """
        Serializza un probe
        """
        d = event.to_dict()
        d.pop("area_id", None)
        return d

    def _enforce_buffer_cap(self, area_id: str) -> None:
        """
        Se il buffer di un'area supera il tetto, scarta i piu' vecchi.
        """
        buf = self._buffers[area_id]
        overflow = len(buf) - self._max_buffer_per_area
        if overflow > 0:
            del buf[:overflow]
            with self._lock:
                self.stats["probes_dropped"] += overflow


    # Chiusura

    def close(self, timeout: float = 5.0):
        """
        Flush finale, attesa dei PUBACK che devono arrivare, disconnessione pulita
        """
        self.flush()

        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            with self._lock:
                if not self._pending_mids:
                    break
            time.sleep(0.05)

        for b in self._brokers:
            b.close()


    def snapshot_stats(self) -> dict:
        """
        Copia coerente delle statistiche, piu' lo stato dei buffer.
        """
        with self._lock:
            s = dict(self.stats)
            s["batches_in_flight"] = len(self._pending_mids)
        s["buffered"] = {a: len(b) for a, b in self._buffers.items() if b}
        s["connected"] = self.is_connected()
        s["connected_brokers"] = sum(1 for b in self._brokers if b.is_connected())
        s["total_brokers"] = len(self._brokers)
        return s



GatewayClient = ProbePublisher
