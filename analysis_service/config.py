"""
Configurazione del servizio di analisi.

Un processo = una area. L'unica cosa che distingue i sei pod fra loro e'
AREA_ID: il resto sono parametri di comportamento, uguali per tutti.

Per ora contiene solo cio' che serve al trasporto. Finestra temporale,
soglie di affollamento, trend e warm-up si aggiungono qui quando i relativi
moduli esistono.
"""


import os
from dataclasses import dataclass

# Caratteri che non possono comparire in un livello di topic MQTT.
_TOPIC_FORBIDDEN = ("/", "+", "#")



@dataclass
class AnalysisConfig:
    """Configurazione del servizio di analisi di una singola area."""
 
    # Identita' del pod. Nessun default: un valore sbagliato qui farebbe
    # analizzare l'area sbagliata senza nessun errore visibile.
    area_id: str = ""
 
    # --- Trasporto MQTT --------------------------------------------------- #
    mqtt_host: str = "localhost"
    mqtt_port: int = 1883
    mqtt_keepalive: int = 30
 
    mqtt_qos: int = 1
    mqtt_clean_session: bool = False     # sessione persistente: copre le migrazioni
    mqtt_topic_prefix: str = "event"     # deve combaciare con SIM_MQTT_TOPIC_PREFIX
 
    mqtt_username: str | None = None
    mqtt_password: str | None = None
 
    # Ack manuale: si conferma al broker solo dopo aver elaborato il batch.
    manual_ack: bool = True
 
    # Rete di sicurezza in RAM fra il thread di rete di paho e il consumatore.
    max_queue_size: int = 200
 
    # Quanti batch_id ricordare per riconoscere i duplicati del QoS 1.
    dedup_window: int = 500
 
    # Backoff della riconnessione, gestito da paho.
    reconnect_min_delay: int = 1
    reconnect_max_delay: int = 30
 
    log_level: str = "INFO"

    # Costruzione

    @classmethod
    def from_env(cls):
        """Legge l'ambiente con fallback ai default, poi valida."""
        cfg = cls()
 
        # AREA_ID senza prefisso: e' l'identita' del pod, non un parametro.
        cfg.area_id = os.getenv("AREA_ID", cfg.area_id).strip()

        cfg.mqtt_host = os.getenv("MQTT_HOST", cfg.mqtt_host)
        cfg.mqtt_port = _env_int("MQTT_PORT", cfg.mqtt_port)
        cfg.mqtt_keepalive = _env_int("MQTT_KEEPALIVE", cfg.mqtt_keepalive)
        cfg.mqtt_qos = _env_int("MQTT_QOS", cfg.mqtt_qos)
        cfg.mqtt_clean_session = _env_bool("MQTT_CLEAN_SESSION", cfg.mqtt_clean_session)
        cfg.mqtt_topic_prefix = os.getenv("MQTT_TOPIC_PREFIX", cfg.mqtt_topic_prefix)
        cfg.mqtt_username = os.getenv("MQTT_USERNAME", cfg.mqtt_username)
        cfg.mqtt_password = os.getenv("MQTT_PASSWORD", cfg.mqtt_password)

        cfg.manual_ack = _env_bool("ANALYSIS_MANUAL_ACK", cfg.manual_ack)
        cfg.max_queue_size = _env_int("ANALYSIS_MAX_QUEUE_SIZE", cfg.max_queue_size)
        cfg.dedup_window = _env_int("ANALYSIS_DEDUP_WINDOW", cfg.dedup_window)

        cfg.log_level = os.getenv("ANALYSIS_LOG_LEVEL", cfg.log_level).upper()

        cfg.validate()
        return cfg

    # Validazione

    def validate(self):

        if not self.area_id:
            raise ValueError("AREA_ID e' obbligatorio: identifica l'area da analizzare")
        if any(c in self.area_id for c in _TOPIC_FORBIDDEN):
            raise ValueError(f"area_id non valido per un topic MQTT: {self.area_id}")

        if self.mqtt_qos not in (0, 1, 2):
            raise ValueError("mqtt_qos deve essere 0, 1 o 2")
        if self.mqtt_qos == 0 and not self.mqtt_clean_session:
            # Configurazione contraddittoria: la sessione persistente non
            # accoda i messaggi QoS 0, quindi crederesti di essere protetto
            # dalle migrazioni senza esserlo.
            raise ValueError("QoS 0 rende inutile la sessione persistente")
        if not (1 <= self.mqtt_port <= 65535):
            raise ValueError("mqtt_port fuori range")

        p = self.mqtt_topic_prefix
        if not p or p.startswith("/") or p.endswith("/") or "+" in p or "#" in p:
            raise ValueError("mqtt_topic_prefix non valido")

        if self.max_queue_size < 1:
            raise ValueError("max_queue_size deve essere >= 1")
        if self.dedup_window < 1:
            raise ValueError("dedup_window deve essere >= 1")

    # Quantita' derivate

    @property
    def topic_probes(self) -> str:
        return f"{self.mqtt_topic_prefix}/probes/{self.area_id}"

    @property
    def status_topic(self) -> str:
        # "analysis-<area>" e non solo "<area>": sullo stesso ramo /status/
        # pubblica anche il simulatore.
        return f"{self.mqtt_topic_prefix}/status/analysis-{self.area_id}"

    @property
    def subscriber_client_id(self) -> str:
        """
        Stabile nel tempo: e' la chiave con cui il broker ritrova la sessione
        persistente dopo una migrazione. Mai derivarlo dal nome del pod.
        """
        return f"analysis-{self.area_id}"


# Helper per la lettura tipizzata dell'ambiente


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    return int(raw) if raw is not None else default


def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}