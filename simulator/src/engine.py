"""
Main simulation execution engine module.

Orchestrates clock ticks, queries the scenario for time-dependent Markov transition matrices,
applies crowd flow saturation, drives Poisson probe request emissions, broadcasts batches via MQTT,
and logs exact ground truth populations.
"""

from datetime import datetime, timezone
import logging
import random
import threading
import time

from .config import SimConfig
from .emission import ProbeEmitter
from .groundtruth import GroundTruthRecorder
from .models import Area, Device
from .population import PopulationModel
from .scenario import Scenario
from .transport import ProbePublisher


logger = logging.getLogger("simulator.engine")


class SimulationEngine:
    """
    Central coordinator orchestrating discrete-time simulation ticks, real-time pacing,
    crowd relocation, telemetry emission, and interactive chaos testing injections.
    """

    def __init__(self, config: SimConfig):
        """
        Initializes the simulation engine, validating configuration parameters and instantiating sub-modules.

        @param config Validated global simulation configuration instance.
        """
        config.validate()
        self.config = config
        self.rng = random.Random(config.seed)

        self.area_ids = config.area_ids()
        self.areas: list[Area] = [Area(config=ac) for ac in config.areas]
        self.devices: list[Device] = []

        self.scenario = Scenario.from_dynamic_areas(config.areas)
        self.population = PopulationModel(self.areas, self.devices, self.rng)
        self.emitter = ProbeEmitter(config, self.rng)
        self.publisher = ProbePublisher(config)
        self.ground_truth = GroundTruthRecorder(config, self.area_ids)

        self._lock = threading.Lock()
        self._boosts: dict[str, dict] = {}
        self._killed: set[str] = set()
        self._stop = threading.Event()
        self._t = 0.0

    def setup(self) -> None:
        """
        Initializes runtime state, instantiates device entities with deterministic MACs,
        assigns baseline RSSI signal profiles, staggers initial probe schedules, and seeds devices into areas.
        """
        self.devices = [
            Device(mac=self._new_mac(i), area_id=self.area_ids[0], next_probe_at=0.0, rssi_base=0.0)
            for i in range(self.config.n_people)
        ]
        for d in self.devices:
            self.emitter.assign_rssi_profile(d)
            self.emitter.schedule_first_probe(d, now=0.0)

        self.population.devices = self.devices
        self.population.seed_devices()

    def run(self) -> None:
        """
        Executes the main simulation loop advancing at a 1:1 real-time pace up to duration_seconds.
        Handles clock lag detection, periodic tick scheduling, and graceful teardown upon completion or termination.
        """
        tick = self.config.tick_seconds
        self.publisher.start()
        self.setup()

        start = time.monotonic()
        self.t0_wall = datetime.now(timezone.utc)
        t = 0.0
        try:
            while t < self.config.duration_seconds and not self._stop.is_set():
                self.tick(t)
                t += tick
                target = start + t
                lag = target - time.monotonic()
                if lag > 0:
                    self._stop.wait(lag)
                elif lag < -tick:
                    logger.warning("tick t=%.0fs lagging behind wall-clock by %.2fs", t, -lag)
        finally:
            self.teardown()

    def tick(self, t: float) -> None:
        """
        Executes a single discrete simulation step:
        1. Evaluates active rate boosts and killed sensor filters.
        2. Interpolates the transition matrix P(t) from Scenario and applies flow saturation dampening.
        3. Steps the Markov population model to relocate attendee devices.
        4. Emits due probe requests and supplementary boost injections, filtering out killed sensors.
        5. Logs exact ground truth headcount per area.
        6. Enqueues and conditionally flushes telemetry batches to MQTT brokers.
        7. Logs periodic connectivity and buffer health diagnostics every 30 seconds.

        @param t Current simulation timestamp in seconds.
        """
        with self._lock:
            self._boosts = {a: b for a, b in self._boosts.items() if t < b["until"]}
            boosts = {a: b.copy() for a, b in self._boosts.items()}
            killed = set(self._killed)

            base = self.scenario.matrix_at(t)
            p = self.population.apply_saturation(base)
            self.population.step(p)

            events = self.emitter.emit_due(self.devices, t)
            events.extend(self._boost_events(boosts, t))
            if killed:
                events = [e for e in events if e.sensor_id not in killed]

            self.ground_truth.record(t, self.areas)

        self.publisher.enqueue(events, t)
        self.publisher.flush_if_due(t)

        if int(t) % 30 == 0 and t > 0:
            s = self.publisher.snapshot_stats()
            connected_str = f"{s['connected_brokers']}/{s['total_brokers']}"
            if s["connected_brokers"] == 0 or s["buffered"]:
                logger.warning(
                    "t=%.0fs active brokers=%s, buffered=%s, dropped=%d",
                    t, connected_str, s["buffered"], s["probes_dropped"],
                )

    def teardown(self) -> None:
        """
        Performs graceful cleanup on simulation shutdown, flushing outstanding MQTT batches and closing ground truth files.
        """
        self.publisher.close()
        self.ground_truth.close()

    def stop(self) -> None:
        """
        Signals the simulation thread to terminate execution cleanly.
        """
        self._stop.set()

    def apply_boost(self, area_id: str, factor: float, duration_s: float) -> None:
        """
        Temporarily amplifies the probe emission rate for a targeted area to simulate sudden crowd surges.

        @param area_id Target monitoring area identifier.
        @param factor Multiplication factor applied to probe emission rate (minimum 1.0).
        @param duration_s Duration in seconds for which the boost remains active.
        """
        if area_id not in self.population.index_of:
            return
        with self._lock:
            self._boosts[area_id] = {
                "factor": max(1.0, factor),
                "until": self._now + duration_s,
            }

    def apply_kill(self, sensor_id: str) -> None:
        """
        Simulates an Access Point sensor failure by suppressing all probe emissions originating from it.

        @param sensor_id The access point sensor identifier to disable.
        """
        with self._lock:
            self._killed.add(sensor_id)

    def restore_sensor(self, sensor_id: str) -> None:
        """
        Restores a previously disabled sensor to normal operation.

        @param sensor_id The access point sensor identifier to reactivate.
        """
        with self._lock:
            self._killed.discard(sensor_id)

    @property
    def _now(self) -> float:
        """
        Retrieves the most recent simulation clock timestamp.

        @return Current simulation time in seconds.
        """
        return getattr(self, "_t", 0.0)

    def _boost_events(self, boosts: dict[str, dict], t: float) -> list:
        """
        Generates synthetic supplementary probe events for areas currently subjected to active emission rate boosts.
        For a factor f, each device emits on average (f - 1) additional probes decomposed into integer and fractional parts.

        @param boosts Active boost configuration mapping area_id to factor and expiration time.
        @param t Current simulation timestamp in seconds.
        @return List of supplementary ProbeEvent instances.
        """
        self._t = t
        extra = []
        for area_id, b in boosts.items():
            surplus = b["factor"] - 1.0
            for d in self.population.devices_in_area(area_id):
                k = int(surplus) + (1 if self.rng.random() < (surplus % 1.0) else 0)
                for _ in range(k):
                    extra.append(self.emitter._make_event(d, t))
        return extra

    def _new_mac(self, i: int) -> str:
        """
        Generates a deterministic, unique locally-administered MAC address for device index i.

        @param i Integer index of the device.
        @return Formatted MAC address string (02:00:00:xx:xx:xx).
        """
        return "02:00:00:%02x:%02x:%02x" % ((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF)