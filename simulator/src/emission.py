"""
Ogni device emette probe come processo di Poisson (intervalli esponenziali).
L'RSSI e' campionato da una normale troncata. 
Se la MAC rotation e' attiva, gestisce il cambio periodico di MAC.
"""


import random

from datetime import datetime, timedelta, timezone
import math
from .config import SimConfig
from .models import Device, ProbeEvent

# Epoca di riferimento dell'evento. 
# I timestamp si ottengono come EPOCH + tempo_di_simulazione

DEFAULT_EPOCH = datetime(2026, 7, 23, 18, 0, 0, tzinfo=timezone.utc)

class ProbeEmitter:
    
 
    def __init__(self, config: SimConfig, rng: random.Random, epoch: datetime = DEFAULT_EPOCH):
        self.config = config
        self.rng = rng
        self.epoch = epoch

        # Un sensore per area monitorata, dalla config, creaimo un dizionario area_id -> sensor_id
        self._sensor_of: dict[str, str] = {
            a.area_id: a.sensor_id for a in config.areas if a.monitored
        }

        #schedule di rotazione MAC
        self._next_rotation: dict[int, float] = {}   

             


    # Poisson

    def sample_next_interval(self):
        """
        Genera l'intervallo di tempo fino al prossimo probe di un device, gli intervalli brevi sono piu' probabili di quelli lunghi.
        Implementato come -mu * ln(U), U ~ Uniform(0,1).
        U è un numero casuale tra 0 ed 1, ln trasforma il numero in un intervallo esponenziale
        """
        u = 1.0 - self.rng.random()  # in (0,1]
        dt = -self.config.probe_interval_mean * math.log(u)

        # Troncatura o clip, perchè la formula sopra potrebbe generare intervalli troppo lunghi o troppo corti
        return min(
            max(dt, self.config.probe_interval_min),
            self.config.probe_interval_max,
        )


    def schedule_first_probe(self, device: Device, now: float):
        """
        Inizializza device.next_probe_at all'avvio, sfasando i device
        cosi' che emettano scaglionati, diamo a ogni device un primo intervallo casuale.
        """

        device.next_probe_at = now + self.sample_next_interval()

    def assign_rssi_profile(self, device: Device):
        """
        Assegna rssi_base e is_fringe al device.

        Una frazione dei device sta ai margini dell'area, con RSSI medio più basso.
        Quindi se cadiamo nel 6% il device prende la media RSSI bassa, altrimenti prende la media normale.
        """
        # 6%
        if self.rng.random() < self.config.fringe_fraction:
            device.is_fringe = True
            device.rssi_base = self.config.fringe_rssi_mean
        # 94%
        else:
            device.is_fringe = False
            device.rssi_base = self.config.rssi_mean

    # Emissione

    def emit_due(self, devices: list[Device], now: float):
        """
        Ad ogni tick l'engine gli passa la lista dei device e l'ora corrente, il metodo fa un controllo
        sul campo next_probe_at di ogni device e se il probe è dovuto, lo emette.
        """

        events: list[ProbeEvent] = []
        for device in devices:
            while device.next_probe_at <= now:
                self.maybe_rotate_mac(device, now)
                # Solo le aree monitorate emettono probe
                if device.area_id in self._sensor_of:
                    events.append(self._make_event(device, now))
                device.next_probe_at += self.sample_next_interval()
        return events



    
    def _make_event(self, device: Device, now: float):
        """
        Genera il probe di una singola emissione
        """
        return ProbeEvent(
            sensor_id=self._sensor_of[device.area_id],
            area_id=device.area_id,
            ts=self._format_ts(now),
            mac=device.mac,
            rssi=self.sample_rssi(device),
        )

     # --- RSSI ---
 
    def sample_rssi(self, device: Device):
        """
        Genera RSSI realistici utilizzando una normale, la maggior parte dei valori cade vicino alla media
        """
        value = self.rng.gauss(device.rssi_base, self.config.rssi_std)
        value = min(
            max(value, self.config.rssi_clip_min),
            self.config.rssi_clip_max
        )
        return round(value)

    # --- MAC ---
 
    def maybe_rotate_mac(self, device: Device, now: float) -> None:
        """Se la rotazione e' attiva e il periodo T_rot e' scaduto, assegna
        un nuovo MAC al device. No-op nello scenario base.
 
        Lo schedule per-device e' tenuto in un dict interno (chiave id(device))
        per non aggiungere ora un campo a Device; quando abiliteremo la
        rotazione per davvero conviene spostarlo su un campo del modello.
        """
        if not self.config.mac_rotation_enabled:
            return
        key = id(device)
        due = self._next_rotation.get(key)
        if due is None:
            # Prima volta che vediamo il device: programma la prima rotazione.
            self._next_rotation[key] = now + self.config.mac_rotation_period
            return
        if now >= due:
            device.mac = self._random_mac()
            self._next_rotation[key] = now + self.config.mac_rotation_period



     # --- Utilita' ---
 
    def _format_ts(self, sim_time: float) -> str:
        """Converte il tempo di simulazione (secondi) in un timestamp ISO-8601
        UTC con millisecondi, ancorato all'epoca dell'evento."""
        dt = self.epoch + timedelta(seconds=sim_time)
        return dt.isoformat(timespec="milliseconds").replace("+00:00", "Z")
 
    def _random_mac(self) -> str:
        """Genera un MAC casuale deterministico (via rng), per la rotazione."""
        octets = [self.rng.randint(0, 255) for _ in range(6)]
        return ":".join(f"{o:02x}" for o in octets)