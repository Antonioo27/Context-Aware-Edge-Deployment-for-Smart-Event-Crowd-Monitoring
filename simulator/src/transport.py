"""
Multi-broker MQTT transport module for the crowd simulator.

Manages partitioned event buffers per monitoring area and broadcasts batches of Wi-Fi probe requests
to all discovered edge and cloud Mosquitto brokers with QoS 1 delivery guarantees and delivery tracking.
"""

import json
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
    Manages a single asynchronous Paho MQTT v3.1.1 client connection to a specific broker endpoint.
    Configures automatic reconnection backoffs, in-flight limits, Last Will and Testament status topics,
    and asynchronous callback hooks for connection and message publication.
    """

    def __init__(
        self,
        broker_url: str,
        client_id_prefix: str,
        config: SimConfig,
        on_publish_cb,
        on_disconnect_cb,
    ):
        """
        Initializes an isolated MQTT client connection for a specific broker endpoint URL.

        @param broker_url The target MQTT broker URL (e.g., tcp://host:port).
        @param client_id_prefix Base prefix string used to synthesize a collision-free MQTT client ID.
        @param config The global simulation configuration instance.
        @param on_publish_cb Callback triggered upon receiving PUBACK confirmations from this broker.
        @param on_disconnect_cb Callback triggered when the broker connection drops.
        """
        self.broker_url = broker_url
        self.config = config

        parsed = urlparse(broker_url if "://" in broker_url else f"tcp://{broker_url}")
        self.host = parsed.hostname or "localhost"
        self.port = parsed.port or 1883

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
        """
        Creates a wrapper delegating paho on_publish events to the external callback.

        @param external_cb The external callback function accepting (client, userdata, mid).
        @return Configured callback handler function.
        """
        def _cb(client, userdata, mid, *args):
            external_cb(client, userdata, mid, *args)
        return _cb

    def _on_connect(self, client, userdata, flags, reason_code, properties=None):
        """
        Handles broker connection acknowledgement, publishing an online simulator presence state upon success.

        @param client The Paho client instance.
        @param userdata User-defined private data.
        @param flags Response flags sent by the broker.
        @param reason_code The connection outcome status code.
        @param properties MQTT v5 / connection properties (if any).
        """
        if reason_code == 0:
            logger.info(f"[Transport] Connected to MQTT broker: {self.host}:{self.port}")
            client.publish(
                f"{self.config.mqtt_topic_prefix}/status/simulator",
                json.dumps({"state": "online", "broker": self.host}),
                qos=1,
                retain=True,
            )
        else:
            logger.warning(f"[Transport] Connection failed to {self.host}:{self.port} with code {reason_code}")

    def start(self) -> None:
        """
        Asynchronously initiates connection to the broker and starts the background network loop thread.
        """
        try:
            self.client.connect_async(self.host, self.port, keepalive=self.config.mqtt_keepalive)
            self.client.loop_start()
        except Exception as e:
            logger.error(f"[Transport] Error initiating connection to {self.broker_url}: {e}")

    def is_connected(self) -> bool:
        """
        Verifies if the client has an active, established socket connection to the broker.

        @return True if currently connected, False otherwise.
        """
        return self.client.is_connected()

    def publish(self, topic: str, payload: str, qos: int):
        """
        Enqueues an MQTT message for asynchronous transmission to the broker.

        @param topic The destination MQTT topic string.
        @param payload Serialized message payload string.
        @param qos Quality of Service level (0, 1, or 2).
        @return Paho MQTTMessageInfo object detailing transmission status and message ID.
        """
        return self.client.publish(topic, payload, qos=qos, retain=False)

    def close(self) -> None:
        """
        Publishes a graceful offline shutdown status, disconnects from the broker, and stops the network thread.
        """
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
    Broadcasts probe batches across a pool of connected MQTT brokers using area-partitioned topics.
    Maintains per-area memory buffers, batch sequencing numbers, flush timers, and delivery tracking.
    """

    def __init__(self, config: SimConfig):
        """
        Initializes the publisher with area buffers, sequence counters, and broker connection pools.

        @param config The global simulation configuration instance.
        """
        self.config = config
        self._area_ids = [a.area_id for a in config.areas if a.monitored]

        self._buffers: dict[str, list[ProbeEvent]] = {a: [] for a in self._area_ids}
        run_offset = int(time.time())
        self._batch_seq: dict[str, int] = {a: run_offset for a in self._area_ids}
        self._last_flush_sim: dict[str, float] = {a: 0.0 for a in self._area_ids}

        self._max_buffer_per_area = max(500, config.batch_max_events * 20)

        self._lock = threading.Lock()
        self._pending_mids: set[tuple] = set()
        self.stats = {
            "probes_published": 0,
            "probes_requeued": 0,
            "probes_dropped": 0,
            "probes_unknown_area": 0,
            "batches_published": 0,
            "batches_acked": 0,
            "reconnects": 0,
        }

        self._brokers: list[SingleBrokerConnection] = []
        self._init_broker_pool()

    def _init_broker_pool(self) -> None:
        """
        Instantiates SingleBrokerConnection objects for all configured broker URLs in broadcast topology.
        """
        urls = self.config.broker_urls if self.config.broker_urls else ["tcp://localhost:1883"]
        logger.info(f"[Transport] Initializing broadcast pool for {len(urls)} brokers: {urls}")

        for idx, url in enumerate(urls):
            conn = SingleBrokerConnection(
                broker_url=url,
                client_id_prefix=f"{self.config.mqtt_client_id}-{idx}",
                config=self.config,
                on_publish_cb=self._on_publish,
                on_disconnect_cb=self._on_disconnect,
            )
            self._brokers.append(conn)

    def start(self) -> None:
        """
        Starts network loops and establishes connections for all brokers in the connection pool.
        """
        for b in self._brokers:
            b.start()

    def _on_disconnect(self, client, userdata, *args) -> None:
        """
        Callback tracking reconnection attempts when a broker connection drops.

        @param client Paho client instance.
        @param userdata User-defined private data.
        """
        with self._lock:
            self.stats["reconnects"] += 1

    def _on_publish(self, client, userdata, mid, *args) -> None:
        """
        Callback invoked when a broker sends a PUBACK confirmation for a QoS 1 message.

        @param client Paho client instance acknowledging the message.
        @param userdata User-defined private data.
        @param mid The unique message identifier assigned by Paho.
        """
        with self._lock:
            key = (client, mid)
            if key in self._pending_mids:
                self._pending_mids.discard(key)
                self.stats["batches_acked"] += 1

    def is_connected(self) -> bool:
        """
        Checks whether at least one broker in the broadcast pool is currently connected.

        @return True if at least one broker connection is active, False otherwise.
        """
        return any(b.is_connected() for b in self._brokers)

    def enqueue(self, events: list[ProbeEvent], now: float) -> None:
        """
        Routes incoming probe events into their respective area buffers, immediately flushing
        any buffer that reaches or exceeds the configured batch_max_events limit.

        @param events List of new ProbeEvent instances to enqueue.
        @param now Current simulation timestamp in seconds.
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

    def flush_if_due(self, now: float) -> None:
        """
        Flushes any area buffers that have exceeded the batch_max_seconds temporal timeout threshold.

        @param now Current simulation timestamp in seconds.
        """
        for area_id, buf in self._buffers.items():
            if buf and (now - self._last_flush_sim[area_id]) >= self.config.batch_max_seconds:
                self._flush_area(area_id, now)

    def flush(self, now: float | None = None) -> None:
        """
        Forces immediate transmission of all pending buffered probe events across all monitored areas.

        @param now Optional current simulation timestamp in seconds.
        """
        for area_id in self._area_ids:
            self._flush_area(area_id, now if now is not None else self._last_flush_sim[area_id])

    def _flush_area(self, area_id: str, now: float) -> None:
        """
        Chunks and publishes buffered probe events for a single area to connected brokers.
        Enforces buffer capacity caps if no brokers are available.

        @param area_id The monitored area identifier whose buffer should be flushed.
        @param now Current simulation timestamp in seconds.
        """
        buf = self._buffers[area_id]
        if not buf:
            return

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

    def _publish_batch(self, area_id: str, chunk: list[ProbeEvent]) -> bool:
        """
        Constructs and transmits an aggregated JSON telemetry batch payload to topic 'event/probes/<area_id>'.
        Attaches monotonic batch sequence IDs and wall-clock UTC timestamps for transport latency benchmarking.

        @param area_id The monitored area identifier.
        @param chunk The list of probe events bundled into this batch.
        @return True if the batch was accepted by at least one connected broker, False otherwise.
        """
        cfg = self.config
        batch_id = self._batch_seq[area_id] + 1

        payload = {
            "area_id": area_id,
            "sensor_id": cfg.sensor_for_area(area_id),
            "batch_id": batch_id,
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
    def _probe_payload(event: ProbeEvent) -> dict:
        """
        Serializes an individual probe event for inclusion in the batch JSON payload, omitting redundant area IDs.

        @param event The ProbeEvent to serialize.
        @return Cleaned dictionary representation of the probe event.
        """
        d = event.to_dict()
        d.pop("area_id", None)
        return d

    def _enforce_buffer_cap(self, area_id: str) -> None:
        """
        Discards the oldest buffered probe events if the buffer size exceeds the max retention cap.

        @param area_id The area identifier to evaluate for memory capping.
        """
        buf = self._buffers[area_id]
        overflow = len(buf) - self._max_buffer_per_area
        if overflow > 0:
            del buf[:overflow]
            with self._lock:
                self.stats["probes_dropped"] += overflow

    def close(self, timeout: float = 5.0) -> None:
        """
        Performs a final buffer flush, waits for pending QoS 1 acknowledgements up to timeout,
        and disconnects all active broker connections cleanly.

        @param timeout Maximum duration in seconds to wait for in-flight PUBACK receipts.
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
        Returns a thread-safe snapshot copy of publisher telemetry statistics, buffer depths, and broker states.

        @return Dictionary detailing published probes, dropped probes, acked batches, and connectivity counts.
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
