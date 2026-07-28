"""
Loop principale

Ad ogni tick chiede a Scenario la matrice P(t), la passa a PopulationModel per muovere i device, chiede
a ProbeEmitter gli eventi dovuti, li consegna al GatewayClient e registra la ground truth.

Espone anche gli agganci per gli interventi manuali
"""


import random
import threading
import time

from .config import SimConfig
from .emission import ProbeEmitter
from .groundtruth import GroundTruthRecorder
from .models import Area, Device
from .population import PopulationModel
from .scenario import Scenario
from .transport import GatewayClient
class SimulationEngine:
    """
    Orchestratore della simulazione
    """
 
    def __init__(self, config: SimConfig):

        config.validate()
        self.config = config
        self.rng = random.Random(config.seed)

        # Ordine canonico delle aree, condiviso da scenario e population.
        self.area_ids = config.area_ids()

        # Stato runtime delle aree e device (creati in setup()).
        self.areas: list[Area] = [Area(config=ac) for ac in config.areas]
        self.devices: list[Device] = []

        # Sottomoduli.
        self.scenario = Scenario.default_fiera(self.area_ids)
        self.population = PopulationModel(self.areas, self.devices, self.rng)
        self.emitter = ProbeEmitter(config, self.rng)
        self.gateway = GatewayClient(config)
        self.ground_truth = GroundTruthRecorder(config, self.area_ids)

        # Interventi manuali (mutati dal control server, altro thread).
        self._lock = threading.Lock()
        self._boosts: dict[str, dict] = {}     # area_id -> {factor, until}
        self._killed: set[str] = set()         # sensor_id spenti

        self._stop = threading.Event()


    # Ciclo di vita

    def setup(self):
        """
        Costruisce stato iniziale, crea device e li distribuisce nelle aree
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

    def run(self):
        """
        Esegue il loop fino a duration_seconds, rispettando tempo reale
        e chiamando tick() ad ogni passo
        """
        self.setup()
        tick = self.config.tick_seconds
        start = time.monotonic()
        t = 0.0
        try:
            while t < self.config.duration_seconds and not self._stop.is_set():
                self.tick(t)
                # Aspettiamo il tempo reale
                t += tick
                target = start + t
                lag = target - time.monotonic()
                if lag > 0:
                    self._stop.wait(lag)
        finally:
            self.teardown()

    def tick(self, t:float):
        """
        Singolo passo della simulazione
        1. P(t) = scenario.matricx_at(t) + saturazione
        2. population.step(P(t))
        3. events = emitter.emit_due(devices, t) (+ boost attivi)
        4. gateway.enqueue(events)
        5. ground_truth.record(t, areas)
        """
        with self._lock:
            boosts = {a: b.copy() for a, b in self._boosts.items()}
            killed = set(self._killed)

            base = self.scenario.matrix_at(t)
            p = self.population.apply_saturation(base)
            self.population.step(p)

            events = self.emitter.emit_due(self.devices, t)
            events.extend(self._boost_events(boosts, t))
            if killed:
                events = [e for e in events if e.sensor_id not in killed]

            self.gateway.enqueue(events)
            self.gateway.flush_if_due(t)
            self.ground_truth.record(t, self.areas)

    def teardown(self):
        """
        Flush finale del gateway e chiusura dei file.
        """
        self.gateway.close()
        self.ground_truth.close()

    def stop(self) -> None:
        """Ferma il loop in modo pulito (chiamabile da un altro thread)."""
        self._stop.set()

 
    # --- Interventi manuali (dal control server) ---
 
    def apply_boost(self, area_id: str, factor: float, duration_s: float):
        """
        Moltiplica temporaneamente il rate di emissione di un'area.
        Usato in demo; disattivato nei run sperimentali riproducibili.
        """
        if area_id not in self.population.index_of:
            return
        with self._lock:
            self._boosts[area_id] = {
                "factor": max(1.0, factor),
                "until": self._now + duration_s,
            }
 
    def apply_kill(self, sensor_id: str):
        """
        Simula un sensore offline: smette di emettere per quell'area.
        """
        with self._lock:
            self._killed.add(sensor_id)


    def restore_sensor(self, sensor_id: str) -> None:
        """Riattiva un sensore precedentemente spento."""
        with self._lock:
            self._killed.discard(sensor_id)

    # --- Helper ---

    @property
    def _now(self) -> float:
        return getattr(self, "_t", 0.0)

    def _boost_events(self, boosts: dict[str, dict], t: float) -> list:
        """
        Genera i probe extra per le aree in boost ancora attive.

        Per un fattore f, ogni device dell'area emette in media (f-1) probe
        extra in questo tick: un'iniezione additiva sopra l'emissione normale.
        """
        self._t = t  # memorizza per _now (usato da apply_boost)
        extra = []
        for area_id, b in boosts.items():
            if t >= b["until"]:
                with self._lock:
                    self._boosts.pop(area_id, None)
                continue
            surplus = b["factor"] - 1.0
            for d in self.population.devices_in_area(area_id):
                # parte intera garantita + parte frazionaria probabilistica
                k = int(surplus) + (1 if self.rng.random() < (surplus % 1.0) else 0)
                for _ in range(k):
                    extra.append(self.emitter._make_event(d, t))
        return extra

    def _new_mac(self, i: int) -> str:
        """MAC iniziale deterministico e unico per device."""
        return "02:00:00:%02x:%02x:%02x" % ((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF)