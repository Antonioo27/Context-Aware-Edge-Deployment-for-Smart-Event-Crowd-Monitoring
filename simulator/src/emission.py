"""
Wi-Fi probe request emission and radio frequency propagation simulation module.

Models packet emission per mobile device as a Poisson point process with truncated exponential inter-arrival intervals,
samples Received Signal Strength Indication (RSSI) from truncated Gaussian distributions with fringe attenuation,
and provides privacy-preserving randomized MAC address rotation.
"""

from datetime import datetime, timedelta, timezone
import math
import random

from .config import SimConfig
from .models import Device, ProbeEvent


DEFAULT_EPOCH = datetime(2026, 7, 23, 18, 0, 0, tzinfo=timezone.utc)


class ProbeEmitter:
    """
    Generates synthetic Wi-Fi probe requests for attendee devices using Poisson process timings
    and Gaussian radio frequency propagation profiles.
    """

    def __init__(self, config: SimConfig, rng: random.Random, epoch: datetime = DEFAULT_EPOCH):
        """
        Initializes the probe emitter with configuration parameters, random generator, and baseline timestamp epoch.

        @param config Global simulation configuration instance.
        @param rng Deterministic random number generator.
        @param epoch Base UTC datetime epoch representing simulation timestamp zero.
        """
        self.config = config
        self.rng = rng
        self.epoch = epoch

        self._sensor_of: dict[str, str] = {
            a.area_id: a.sensor_id for a in config.areas if a.monitored
        }
        self._next_rotation: dict[int, float] = {}

    def sample_next_interval(self) -> float:
        """
        Generates the exponential inter-arrival time until a device's subsequent probe emission
        using inverse transform sampling: dt = -mu * ln(1 - U) with U ~ Uniform[0, 1).
        Clips the resulting interval between probe_interval_min and probe_interval_max bounds.

        @return Sampled time interval in seconds.
        """
        u = 1.0 - self.rng.random()
        dt = -self.config.probe_interval_mean * math.log(u)

        return min(
            max(dt, self.config.probe_interval_min),
            self.config.probe_interval_max,
        )

    def schedule_first_probe(self, device: Device, now: float) -> None:
        """
        Staggers the initial probe emission timestamp for a device at startup to prevent artificial burst synchrony.

        @param device The Device instance to schedule.
        @param now Initial simulation timestamp in seconds.
        """
        device.next_probe_at = now + self.sample_next_interval()

    def assign_rssi_profile(self, device: Device) -> None:
        """
        Assigns baseline Received Signal Strength Indication (RSSI) parameters to a device.
        Categorizes a designated fraction (fringe_fraction) of devices as peripheral ("fringe") attendees
        with attenuated signal strength, while remaining attendees receive the standard nominal RSSI baseline.

        @param device The Device instance to configure.
        """
        if self.rng.random() < self.config.fringe_fraction:
            device.is_fringe = True
            device.rssi_base = self.config.fringe_rssi_mean
        else:
            device.is_fringe = False
            device.rssi_base = self.config.rssi_mean

    def emit_due(self, devices: list[Device], now: float) -> list[ProbeEvent]:
        """
        Evaluates scheduled probe deadlines across all active devices and generates ProbeEvent instances
        for devices positioned within monitored zones whose deadlines have matured up to the current clock time.
        Updates device schedules iteratively to account for any missed emission intervals.

        @param devices Complete list of attendee Device entities.
        @param now Current simulation timestamp in seconds.
        @return List of generated ProbeEvent instances.
        """
        events: list[ProbeEvent] = []
        for device in devices:
            while device.next_probe_at <= now:
                self.maybe_rotate_mac(device, now)
                if device.area_id in self._sensor_of:
                    events.append(self._make_event(device, now))
                device.next_probe_at += self.sample_next_interval()
        return events

    def _make_event(self, device: Device, now: float) -> ProbeEvent:
        """
        Constructs a serialized ProbeEvent instance for a single device emission.

        @param device The emitting Device entity.
        @param now Current simulation timestamp in seconds.
        @return New ProbeEvent instance.
        """
        return ProbeEvent(
            sensor_id=self._sensor_of[device.area_id],
            area_id=device.area_id,
            ts=self._format_ts(now),
            mac=device.mac,
            rssi=self.sample_rssi(device),
        )

    def sample_rssi(self, device: Device) -> int:
        """
        Samples an instantaneous RSSI decibel value from a truncated Gaussian distribution
        centered on the device's base profile, clipped between rssi_clip_min and rssi_clip_max.

        @param device The Device instance providing baseline signal parameters.
        @return Integer rounded RSSI decibel value (dBm).
        """
        value = self.rng.gauss(device.rssi_base, self.config.rssi_std)
        value = min(
            max(value, self.config.rssi_clip_min),
            self.config.rssi_clip_max,
        )
        return round(value)

    def maybe_rotate_mac(self, device: Device, now: float) -> None:
        """
        Conditionally rotates a device's hardware MAC address if privacy randomization is globally enabled
        and the per-device rotation period has elapsed.

        @param device The Device instance being evaluated.
        @param now Current simulation timestamp in seconds.
        """
        if not self.config.mac_rotation_enabled:
            return
        key = id(device)
        due = self._next_rotation.get(key)
        if due is None:
            self._next_rotation[key] = now + self.config.mac_rotation_period
            return
        if now >= due:
            device.mac = self._random_mac()
            self._next_rotation[key] = now + self.config.mac_rotation_period

    def _format_ts(self, sim_time: float) -> str:
        """
        Converts simulation elapsed seconds into an ISO-8601 UTC timestamp string anchored to the scenario epoch.

        @param sim_time Elapsed simulation seconds.
        @return ISO-8601 UTC formatted timestamp string with millisecond precision.
        """
        dt = self.epoch + timedelta(seconds=sim_time)
        return dt.isoformat(timespec="milliseconds").replace("+00:00", "Z")

    def _random_mac(self) -> str:
        """
        Generates a randomized, locally-administered MAC address string using the random number generator.

        @return Formatted hexadecimal MAC address string (xx:xx:xx:xx:xx:xx).
        """
        octets = [self.rng.randint(0, 255) for _ in range(6)]
        return ":".join(f"{o:02x}" for o in octets)