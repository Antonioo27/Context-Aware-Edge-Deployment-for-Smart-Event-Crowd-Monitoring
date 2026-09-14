"""
Global configuration and environment parsing module for the simulator.

Defines data structures and loading routines for simulation parameters,
venue area topologies, MQTT transport settings, and dynamic backend discovery.
"""

import os
from dataclasses import dataclass, field
import requests


@dataclass
class AreaConfig:
    """
    Static configuration specification for a venue area.
    Defines unique area identifiers, assigned sensor names, capacity limits,
    operational classification types, and monitoring flags.
    """

    area_id: str
    sensor_id: str
    capacity: int
    area_type: str = "GENERIC"
    monitored: bool = True


def default_fiera_areas() -> list[AreaConfig]:
    """
    Provides default fallback configuration containing six standard venue zones with distinct operational roles
    (outside, entrance, stage, food, stand, corridor, and exit).

    @return List of AreaConfig instances representing the default exhibition scenario.
    """
    return [
        AreaConfig(area_id="outside", sensor_id="", capacity=100_000, area_type="OUTSIDE", monitored=False),
        AreaConfig(area_id="entrance", sensor_id="ap-entrance-01", capacity=800, area_type="ENTRANCE"),
        AreaConfig(area_id="stage", sensor_id="ap-stage-01", capacity=3000, area_type="PEAK_ATTRACTION"),
        AreaConfig(area_id="food", sensor_id="ap-food-01", capacity=800, area_type="SUSTAINED_ATTRACTION"),
        AreaConfig(area_id="stand", sensor_id="ap-stand-01", capacity=1200, area_type="SUSTAINED_ATTRACTION"),
        AreaConfig(area_id="corridor", sensor_id="ap-corridor-01", capacity=1000, area_type="TRANSIT"),
        AreaConfig(area_id="exit", sensor_id="ap-exit-01", capacity=800, area_type="EXIT"),
    ]


def fetch_dynamic_areas(backend_url: str) -> list[AreaConfig]:
    """
    Retrieves dynamic monitoring area definitions from the Event Management backend API.
    Prepend an unmonitored 'outside' reservoir area, or falls back to static default areas
    if the service is unreachable or defines no areas.

    @param backend_url Base HTTP URL of the Event Management service.
    @return List of dynamically configured AreaConfig instances.
    """
    try:
        response = requests.get(f"{backend_url}/api/event", timeout=5)
        response.raise_for_status()
        event_data = response.json()

        raw_areas = event_data.get("areas", []) if isinstance(event_data, dict) else []

        dynamic_areas = [
            AreaConfig(
                area_id=area["name"],
                sensor_id=f"ap-{area['name'].lower().replace(' ', '_')}-01",
                capacity=area.get("capacity", 500),
                area_type=area.get("type", "GENERIC"),
                monitored=True,
            )
            for area in raw_areas
            if "name" in area
        ]

        if dynamic_areas:
            outside = AreaConfig(area_id="outside", sensor_id="", capacity=100_000, area_type="OUTSIDE", monitored=False)
            return [outside] + dynamic_areas
        else:
            print("[Info] No areas defined on backend event. Using default fallback configuration.")

    except Exception as e:
        print(f"[Warning] Unable to fetch areas from backend: {e}. Using fallback configuration.")

    return default_fiera_areas()


def fetch_dynamic_brokers(backend_url: str) -> list[str]:
    """
    Queries the Event Management backend to discover active edge and cloud Mosquitto MQTT broker endpoints.
    Falls back to local broker if discovery fails.

    @param backend_url Base HTTP URL of the Event Management service.
    @return List of discovered MQTT broker URLs.
    """
    try:
        response = requests.get(f"{backend_url}/api/nodes", timeout=5)
        response.raise_for_status()
        nodes_data = response.json()

        urls = [
            node["brokerUrl"]
            for node in nodes_data
            if "brokerUrl" in node and node["brokerUrl"]
        ]

        if urls:
            print(f"[Config] Discovery completed: found {len(urls)} MQTT brokers: {urls}")
            return urls
    except Exception as e:
        print(f"[Warning] Unable to fetch brokers from backend: {e}. Using local fallback broker.")

    return ["tcp://localhost:1883"]


@dataclass
class SimConfig:
    """
    Global configuration parameters governing crowd simulation, probe emission,
    radio frequency propagation models, MQTT multi-broker transport, and ground-truth logging.
    """

    n_people: int = 5000
    tick_seconds: float = 1.0
    duration_seconds: int = 900

    probe_interval_mean: float = 25.0
    probe_interval_min: float = 5.0
    probe_interval_max: float = 120.0

    rssi_mean: float = -65.0
    rssi_std: float = 10.0
    rssi_clip_min: float = -95.0
    rssi_clip_max: float = -30.0
    fringe_fraction: float = 0.06
    fringe_rssi_mean: float = -88.0

    mac_rotation_enabled: bool = False
    mac_rotation_period: float = 600.0

    seed: int = 42

    broker_urls: list[str] = field(default_factory=list)
    mqtt_host: str = "localhost"
    mqtt_port: int = 1883
    mqtt_keepalive: int = 30
    mqtt_client_id: str = "sim-probe-publisher"
    mqtt_qos: int = 1
    mqtt_clean_session: bool = False
    mqtt_topic_prefix: str = "event"

    batch_max_events: int = 50
    batch_max_seconds: float = 1.0

    backend_url: str = "http://192.168.58.2:30080"
    ground_truth_path: str = "ground_truth.csv"
    areas: list[AreaConfig] = field(default_factory=default_fiera_areas)

    @classmethod
    def from_env(cls) -> "SimConfig":
        """
        Factory method constructing a SimConfig instance populated from environment variables
        with automatic fallback to default parameter values and dynamic backend area discovery.

        @return A fully initialized and validated SimConfig instance.
        """
        cfg = cls()

        cfg.n_people = _env_int("SIM_N_PEOPLE", cfg.n_people)
        cfg.backend_url = os.getenv("SIM_BACKEND_URL", cfg.backend_url)
        cfg.tick_seconds = _env_float("SIM_TICK_SECOND", cfg.tick_seconds)
        cfg.duration_seconds = _env_int("SIM_DURATION_SECONDS", cfg.duration_seconds)

        cfg.areas = fetch_dynamic_areas(cfg.backend_url)

        env_brokers = os.getenv("SIM_BROKER_URLS")
        if env_brokers:
            cfg.broker_urls = [b.strip() for b in env_brokers.split(",") if b.strip()]
        else:
            cfg.broker_urls = fetch_dynamic_brokers(cfg.backend_url)

        cfg.probe_interval_mean = _env_float("SIM_PROBE_INTERVAL_MEAN", cfg.probe_interval_mean)
        cfg.probe_interval_min = _env_float("SIM_PROBE_INTERVAL_MIN", cfg.probe_interval_min)
        cfg.probe_interval_max = _env_float("SIM_PROBE_INTERVAL_MAX", cfg.probe_interval_max)

        cfg.mac_rotation_enabled = _env_bool("SIM_MAC_ROTATION_ENABLED", cfg.mac_rotation_enabled)
        cfg.mac_rotation_period = _env_float("SIM_MAC_ROTATION_PERIOD", cfg.mac_rotation_period)

        cfg.seed = _env_int("SIM_SEED", cfg.seed)

        cfg.mqtt_host = os.getenv("SIM_MQTT_HOST", cfg.mqtt_host)
        cfg.mqtt_port = _env_int("SIM_MQTT_PORT", cfg.mqtt_port)
        cfg.mqtt_keepalive = _env_int("SIM_MQTT_KEEPALIVE", cfg.mqtt_keepalive)
        cfg.mqtt_client_id = os.getenv("SIM_MQTT_CLIENT_ID", cfg.mqtt_client_id)
        cfg.mqtt_qos = _env_int("SIM_MQTT_QOS", cfg.mqtt_qos)
        cfg.mqtt_clean_session = _env_bool("SIM_MQTT_CLEAN_SESSION", cfg.mqtt_clean_session)
        cfg.mqtt_topic_prefix = os.getenv("SIM_MQTT_TOPIC_PREFIX", cfg.mqtt_topic_prefix)
        cfg.batch_max_events = _env_int("SIM_BATCH_MAX_EVENTS", cfg.batch_max_events)
        cfg.batch_max_seconds = _env_float("SIM_BATCH_MAX_SECONDS", cfg.batch_max_seconds)

        cfg.ground_truth_path = os.getenv("SIM_GROUND_TRUTH_PATH", cfg.ground_truth_path)

        cfg.validate()

        return cfg

    def validate(self) -> None:
        """
        Validates configuration sanity, constraints, parameter bounds, and MQTT topic conventions.

        @throws ValueError If any configuration parameter violates mathematical or protocol invariants.
        """
        if self.n_people <= 0:
            raise ValueError("n_people must be > 0")

        if self.tick_seconds <= 0:
            raise ValueError("tick_seconds must be > 0")

        if self.probe_interval_mean <= 0:
            raise ValueError("probe_interval_mean (mu) must be > 0")

        if not (self.probe_interval_min <= self.probe_interval_mean <= self.probe_interval_max):
            raise ValueError("must satisfy probe_interval_min <= mean <= max")

        if not self.areas:
            raise ValueError("at least one area is required")

        if self.mqtt_qos not in (0, 1, 2):
            raise ValueError("mqtt_qos must be 0, 1, or 2")

        if not (1 <= self.mqtt_port <= 65535):
            raise ValueError("mqtt_port out of range [1, 65535]")

        if not self.mqtt_client_id and not self.mqtt_clean_session:
            raise ValueError("empty client_id permitted only with clean_session=True")

        p = self.mqtt_topic_prefix
        if not p or p.startswith("/") or p.endswith("/") or "+" in p or "#" in p:
            raise ValueError("invalid mqtt_topic_prefix format")

        for a in self.areas:
            if any(c in a.area_id for c in ("/", "+", "#")):
                raise ValueError(f"invalid area_id for MQTT topic naming: {a.area_id}")

        ids = [a.area_id for a in self.areas]
        if len(ids) != len(set(ids)):
            raise ValueError("area_ids must be unique")

        monitored = [a for a in self.areas if a.monitored]
        if not monitored:
            raise ValueError("at least one monitored area is required")
        if any(not a.sensor_id for a in monitored):
            raise ValueError("every monitored area must have an assigned sensor_id")

        sensors = [a.sensor_id for a in self.areas]
        if len(sensors) != len(set(sensors)):
            raise ValueError("sensor_ids must be unique")

    def probe_rate_per_device(self) -> float:
        """
        Computes the expected Poisson emission rate (lambda = 1 / mu) for a single mobile device.

        @return Device emission frequency in events per second.
        """
        return 1.0 / self.probe_interval_mean

    def expected_total_probe_rate(self) -> float:
        """
        Calculates the aggregate expected probe arrival rate across the entire attendee population.

        @return Total expected emission rate in events per second.
        """
        return self.n_people * self.probe_rate_per_device()

    def area_ids(self) -> list[str]:
        """
        Returns the ordered list of area identifier strings indexing rows and columns in the Markov transition matrix.

        @return List of area identifiers.
        """
        return [a.area_id for a in self.areas]

    def sensor_for_area(self, area_id: str) -> str:
        """
        Resolves the access point sensor identifier assigned to a specific monitoring area.

        @param area_id The unique area identifier.
        @return The corresponding sensor identifier string.
        @throws KeyError If the area identifier is not found in configuration.
        """
        for a in self.areas:
            if a.area_id == area_id:
                return a.sensor_id
        raise KeyError(f"unknown area: {area_id}")


def _env_int(name: str, default: int) -> int:
    """
    Parses an integer from an environment variable with a default fallback.

    @param name The environment variable name.
    @param default Fallback value if the variable is not defined.
    @return Parsed integer value.
    """
    raw = os.getenv(name)
    return int(raw) if raw is not None else default


def _env_float(name: str, default: float) -> float:
    """
    Parses a float from an environment variable with a default fallback.

    @param name The environment variable name.
    @param default Fallback value if the variable is not defined.
    @return Parsed float value.
    """
    raw = os.getenv(name)
    return float(raw) if raw is not None else default


def _env_bool(name: str, default: bool) -> bool:
    """
    Parses a boolean from an environment variable matching common truthy values ('1', 'true', 'yes', 'on').

    @param name The environment variable name.
    @param default Fallback value if the variable is not defined.
    @return Parsed boolean value.
    """
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}