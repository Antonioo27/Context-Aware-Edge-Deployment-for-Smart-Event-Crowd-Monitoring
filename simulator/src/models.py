"""
Core entity models shared across simulator modules.

Provides data structures representing Wi-Fi probe telemetry events,
tracked mobile devices, and runtime monitoring areas.
"""

from dataclasses import asdict, dataclass
from .config import AreaConfig


@dataclass
class ProbeEvent:
    """
    Data transfer entity representing a Wi-Fi probe request detected by an Access Point sensor.
    Carries sensor, area, temporal, and radio frequency signal strength metrics.
    """

    sensor_id: str
    area_id: str
    ts: str
    mac: str
    rssi: int

    def to_dict(self) -> dict:
        """
        Serializes the probe event dataclass into a standard dictionary.

        @return Dictionary representation of the probe event attributes.
        """
        return asdict(self)


@dataclass
class Device:
    """
    Represents an individual mobile device carried by an attendee moving through venue areas.
    Maintains positioning state, next scheduled probe emission timestamp, and radio characteristics.
    """

    mac: str
    area_id: str
    next_probe_at: float = 0.0
    is_fringe: bool = False
    rssi_base: float = 0.0

    def rotate_mac(self, new_mac: str) -> None:
        """
        Rotates the device's hardware MAC address to simulate privacy-preserving MAC randomization.

        @param new_mac The newly assigned randomized MAC address string.
        @throws NotImplementedError If MAC rotation is invoked directly on the entity.
        """
        raise NotImplementedError


@dataclass
class Area:
    """
    Represents the runtime operational state of a geographic area during simulation.
    Tracks live occupant population against static physical capacity limits.
    """

    config: "AreaConfig"
    population: int = 0

    @property
    def area_id(self) -> str:
        """
        Retrieves the unique identifier of the area from its static configuration.

        @return The area identifier string.
        """
        return self.config.area_id

    @property
    def occupancy(self) -> float:
        """
        Calculates the live occupancy ratio (population divided by capacity) used for flow saturation.

        @return The occupancy fraction (rho = population / capacity).
        """
        return self.population / self.config.capacity