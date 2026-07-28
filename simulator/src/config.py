
import os
from dataclasses import dataclass, field

@dataclass
class AreaConfig:
    """
    Definizione statica di un'area della fiera.
    """
 
    area_id: str
    sensor_id: str
    capacity: int
    monitored: bool = True


def default_fiera_areas():
    """
    Sei aree con ruoli diversi.
    """
    return [
        AreaConfig(area_id="outside", sensor_id="", capacity=100_000, monitored=False),
        AreaConfig(area_id="entrance", sensor_id="ap-entrance-01", capacity=400),
        AreaConfig(area_id="stage", sensor_id="ap-stage-01", capacity=3000),
        AreaConfig(area_id="food", sensor_id="ap-food-01", capacity=800),
        AreaConfig(area_id="stand", sensor_id="ap-stand-01", capacity=1200),
        AreaConfig(area_id="corridor", sensor_id="ap-corridor-01", capacity=600),
        AreaConfig(area_id="exit", sensor_id="ap-exit-01", capacity=400),
    ]

@dataclass
class SimConfig:
    """
    Configurazione globale del simulatore
    """

    n_people: int = 5000

    # Passo del modello a flussi. Il tempo scorre 1:1 con la realtà
    tick_seconds: float = 1.0

    # Durata totale della simulazione in secondi 
    duration_seconds: int = 600

    # Emissione probe
    probe_interval_mean: float = 25.0

    probe_interval_min: float = 5.0

    probe_interval_max: float = 120.0

    # --- RSSI ---
    rssi_mean: float = -65.0
    rssi_std: float = 10.0
    rssi_clip_min: float = -95.0
    rssi_clip_max: float = -30.0
    fringe_fraction: float = 0.06
    #Frazione di device "ai margini" con RSSI medio piu' basso.
    fringe_rssi_mean: float = -88.0


    # --- MAC randomization (fase 2, disattivata all'inizio) ---
    mac_rotation_enabled: bool = False
    mac_rotation_period: float = 600.0


    # Determinismo
    seed: int = 42

    # --- Trasporto ---
    gateway_url: str = "http://gateway/api/v1/probes"
    batch_max_events: int = 50
    batch_max_seconds: float = 1.0

    # --- Ground truth ---
    ground_truth_path: str = "ground_truth.csv"

    # Definizione statica delle Aree 
    areas: list[AreaConfig] = field(default_factory=default_fiera_areas)


    @classmethod
    def from_env(cls):
        """
        Crea un oggetto SimConfig leggendo le variabili d'ambiente, con falback ai valori di default.

        Le aree non si leggono da env ma sono hardcodate in default_fiera_areas().
        """
        cfg = cls()

        cfg.n_people = _env_int("SIM_N_PEOPLE", cfg.n_people)
        cfg.tick_seconds = _env_float("SIM_TICK_SECOND", cfg.tick_seconds)
        cfg.duration_seconds = _env_int("SIM_DURATION_SECONDS", cfg.duration_seconds)

        cfg.probe_interval_mean = _env_float("SIM_PROBE_INTERVAL_MEAN", cfg.probe_interval_mean)
        cfg.probe_interval_min = _env_float("SIM_PROBE_INTERVAL_MIN", cfg.probe_interval_min)
        cfg.probe_interval_max = _env_float("SIM_PROBE_INTERVAL_MAX", cfg.probe_interval_max)

        cfg.mac_rotation_enabled = _env_bool("SIM_MAC_ROTATION_ENABLED", cfg.mac_rotation_enabled)
        cfg.mac_rotation_period = _env_float("SIM_MAC_ROTATION_PERIOD", cfg.mac_rotation_period)

        cfg.seed = _env_int("SIM_SEED", cfg.seed)
 
        cfg.gateway_url = os.getenv("SIM_GATEWAY_URL", cfg.gateway_url)
        cfg.batch_max_events = _env_int("SIM_BATCH_MAX_EVENTS", cfg.batch_max_events)
        cfg.batch_max_seconds = _env_float("SIM_BATCH_MAX_SECONDS", cfg.batch_max_seconds)
 
        cfg.ground_truth_path = os.getenv("SIM_GROUND_TRUTH_PATH", cfg.ground_truth_path)
 
        cfg.validate()

        return cfg

    def validate(self):
        """
        Controlli di sanità
        """

        if self.n_people <= 0:
            raise ValueError("n_people deve essere > 0")
        
        if self.tick_seconds <= 0:
            raise ValueError("tick_seconds deve essere > 0")

        if self.probe_interval_mean <= 0:
            raise ValueError("probe_interval_mean (mu) deve essere > 0")

        if not (self.probe_interval_min <= self.probe_interval_mean <= self.probe_interval_max):
            raise ValueError("deve valere probe_interval_min <= mean <= max")

        if not self.areas:
            raise ValueError("serve almeno un'area")

        ids = [a.area_id for a in self.areas]

        if len(ids) != len(set(ids)):
            raise ValueError("gli area_id devono essere unici")

        monitored = [a for a in self.areas if a.monitored]
        if not monitored:
            raise ValueError("serve almeno un'area monitorata")
        if any(not a.sensor_id for a in monitored):
            raise ValueError("ogni area monitorata deve avere un sensor_id")

        sensors = [a.sensor_id for a in self.areas]

        if len(sensors) != len(set(sensors)):
            raise ValueError("i sensor_id devono essere unici")


    # Quantità derivate

    def probe_rate_per_device(self):
        # lambda = 1/mu. Rate di emissione di un singolo device.
        return 1.0 / self.probe_interval_mean

    def expected_total_probe_rate(self):
        # rate grezzo atteso sull'intero evento
        return self.n_people * self.probe_rate_per_device()

    def area_ids(self):
        """
        Lista ordinata degli id delle aree (indicizza righe/colonne
        della matrice di transizione).
        """
        return [a.area_id for a in self.areas]
 
    def sensor_for_area(self, area_id: str) -> str:
        """
        sensor_id associato a un'area.
        """
        for a in self.areas:
            if a.area_id == area_id:
                return a.sensor_id
        raise KeyError(f"area sconosciuta: {area_id}")





# ----------------------------------------------------------------------
# Helper per la lettura tipizzata dell'ambiente
# ----------------------------------------------------------------------
 
def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    return int(raw) if raw is not None else default
 
 
def _env_float(name: str, default: float) -> float:
    raw = os.getenv(name)
    return float(raw) if raw is not None else default
 
 
def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}