
from dataclasses import dataclass, field
from datetime import datetime

class InvalidBatchError(ValueError):
    """Il payload ricevuto non rispetta il contratto del simulatore."""
 
 
@dataclass(frozen=True, slots=True)
class Probe:
    """Un singolo probe WiFi """
    sensor_id: str
    ts: datetime          
    mac: str
    rssi: int
 
 
@dataclass(slots=True)
class ProbeBatch:
    """Un batch di probe di una singola area, gia' validato."""
    area_id: str
    batch_id: int
    sent_at: datetime          # quando il simulatore ha pubblicato (UTC)
    received_at: datetime      # quando noi abbiamo ricevuto (UTC)
    count_declared: int        # campo "count" dichiarato nel messaggio
    probes: list[Probe]
    malformed_probes: int      # probe scartati 
 
    # Metadati MQTT: per l'ack manuale.
    mid: int = 0
    qos: int = 1
 
    @property
    def transport_latency_ms(self) -> float:
        """Latenza broker->servizio in millisecondi (richiede orologi allineati)."""
        return (self.received_at - self.sent_at).total_seconds() * 1000.0
 
    @property
    def count_effective(self) -> int:
        """Quanti probe sono realmente utilizzabili dopo il parsing."""
        return len(self.probes)
 