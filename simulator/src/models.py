"""
Entità condivise tra moduli

ProbeEvent è il payload che va verso il gateway
Device è l'oggetto che si muove tra le aree
Area è lo stato runtime di un'area

"""

from dataclasses import asdict, dataclass
from .config import AreaConfig

@dataclass
class ProbeEvent:
    """
    Schema: sensor_id, area_id, ts, mac, rssi
    """

    sensor_id: str
    area_id: str
    ts: str
    mac: str
    rssi: int

    def to_dict(self):
        return asdict(self) 


@dataclass
class Device:
    """
    Device centric, conosciamo popolazione area per area
    """

    mac: str
    area_id: str
    #Istante del prossimo probe
    next_probe_at: float = 0.0
    #true se il device è ai margini dell'area
    is_fringe: bool = 0.0
    #Il valore dipende da quanto è vicino al sensore
    rssi_base:float = False


    def rotate_mac(self, new_mac:str):
        #Usato quando la rotazione del MAC è attiva
        raise NotImplementedError


@dataclass
class Area:
    """
    Stato di un area runtime durante simulazione
    """

    config: "AreaConfig"
    #n_i(t): persone attualmente nell'area
    population:int = 0

    @property
    def area_id(self):
        return self.config.area_id

    @property
    def occupancy(self):
        """rho_i = n_i / C_i. Grado di riempimento, per la saturazione."""
        return self.population / self.config.capacity