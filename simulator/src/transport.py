"""
Transporto MQTT del simulatore
Il buffer è partizionato per area, ogni area ha il suo topic (`event/probes/<area_id>`)
Il trasporto è MQTT con QoS 1, la publish() fatta da paho non è bloccante, il messaggio viene messo in una coda interna
e un thread di rete lo spedisce.

La conferma dell'invio (PUBACK) arriva in modo asincrono nella callback, da qui la distinzione
batch spediti e batch confermati.
"""


import json
import threading
import time
from datetime import datetime, timezone
 
import paho.mqtt.client as mqtt

from .config import SimConfig
from .models import ProbeEvent

class ProbePublisher:
    """
    Pubblica i probe sul broker MQTT, un topic pera area
    """

    def __init__(self, config: SimConfig):
        
        self.config = config
        self._area_ids = [a.area_id for a in config.areas if a.monitored]

        # Buffer per ogni area
        self._buffers: dict[str, list[ProbeEvent]] = {a: [] for a in self._area_ids}
        # Contatore di batch per ogni area
        self._batch_seq: dict[str, int] = {a: 0 for a in self._area_ids}
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

        self._client = self._build_client()

    def _build_client(self):
        cfg = self.config

        client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=cfg.mqtt_client_id,
            protocol=mqtt.MQTTv311,
            clean_session=cfg.mqtt_clean_session,
        )

        # Ritardo esponenziale tra tentativi di riconnessione
        client.reconnect_delay_set(min_delay=1, max_delay=8)

        # Messaggi QoS>0 in volo senza PUBACK
        client.max_inflight_messages_set(40)  

        # Buffer interno di Paho
        client.max_queued_messages_set(1000)

        # Messaggio che verrà inviato se il broker muore o il client si disconnette in modo anomalo
        client.will_set(
            f"{cfg.mqtt_topic_prefix}/status/simulator",
            json.dumps({"state": "offline", "reason": "lwt"}),
            qos=1,
            retain=True,
        )

        client.on_connect = self._on_connect
        client.on_disconnect = self._on_disconnect
        client.on_publish = self._on_publish
        return client


    def start(self):
        """
        Avvia la connessione ed il thread di rete

        con connect_async() invece di connect() il pod del simulatore
        può partire prima di Mosquitto.
        """

        cfg = self.config
        self._client.connect_async(cfg.mqtt_host, cfg.mqtt_port, keepalive=cfg.mqtt_keepalive)
        self._client.loop_start()

    def _on_connect(self, client, userdata, flags, reason_code, properties=None):
        if reason_code == 0:
            client.publish(
                f"{self.config.mqtt_topic_prefix}/status/simulator",
                json.dumps({"state": "online"}),
                qos=1,
                retain=True,
            )

    def _on_disconnect(self, client, userdata, *args):
        with self._lock:
            self.stats["reconnects"] += 1

    def _on_publish(self, client, userdata, mid, *args):
        """
        PUBACK ricevuto: il broker ha preso in carico il batch
        """
        with self._lock:
            if mid in self._pending_mids:
                self._pending_mids.discard(mid)
                self.stats["batches_acked"] += 1

    def is_connected(self):
        return self._client.is_connected()


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

        # Se non siamo connessi non consegnamo niente, i probe restano nel buffer invece
        # di finire nella cosa interna della libreria   
        if not self._client.is_connected():
            self._enforce_buffer_cap(area_id)
            return

        while buf:
            chunk = buf[: self.config.batch_max_events]
            if not self._publish_batch(area_id, chunk):
                # publish rifiutata, esce dal ciclo e lascia il resto nel buffer
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

        info = self._client.publish(topic, raw, qos=cfg.mqtt_qos, retain=False)

        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            # Tipicamente MQTT_ERR_QUEUE_SIZE (coda interna piena) o
            # MQTT_ERR_NO_CONN (connessione caduta tra il check e la publish).
            with self._lock:
                self.stats["probes_requeued"] += len(chunk)
            return False

        self._batch_seq[area_id] = batch_id

        with self._lock:
            self.stats["probes_published"] += len(chunk)
            self.stats["batches_published"] += 1
            if cfg.mqtt_qos > 0:
                self._pending_mids.add(info.mid)
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

        self._client.publish(
            f"{self.config.mqtt_topic_prefix}/status/simulator",
            json.dumps({"state": "offline", "reason": "shutdown"}),
            qos=1,
            retain=True,
        )

        self._client.disconnect()
        self._client.loop_stop()


    def snapshot_stats(self) -> dict:
        """
        Copia coerente delle statistiche, piu' lo stato dei buffer.
        """
        with self._lock:
            s = dict(self.stats)
            s["batches_in_flight"] = len(self._pending_mids)
        s["buffered"] = {a: len(b) for a, b in self._buffers.items() if b}
        s["connected"] = self._client.is_connected()
        return s



GatewayClient = ProbePublisher
