"""
Classe per il trasporto dei dati verso il gateway.

Accumula eventi in un batch e li spedisce con un POST. Un buffer locale con retry evita
di perdere eventi se il gateway non è disponibile.

Nota: Sincrono per semplicità, da portare ad asincrono
"""


import requests

from .config import SimConfig
from .models import ProbeEvent

class GatewayClient:

    def __init__(self, config: SimConfig):
        
        self.config = config
        self._buffer: list[ProbeEvent] = []
        self._last_flush_sim: float = 0.0
        self._session = requests.Session()
        self._timeout = 2.0
        # Tetto del buffer: se il gateway resta giu' a lungo, scartiamo i
        # piu' vecchi invece di crescere all'infinito. Ampio ma finito.
        self._max_buffer = max(1000, config.batch_max_events * 50)
        self.stats = {"sent": 0, "failed": 0, "dropped": 0}

    def enqueue(self, events: list[ProbeEvent]):
        """
        Aggiunge eventi al buffer corrente, se il buffer supera batch_max_events, forza un flush
        """
        self._buffer.extend(events)
        if len(self._buffer) >= self.config.batch_max_events:
            self.flush()

    def flush_if_due(self, now: float):
        """
        Invia il batch se e' passato batch_max_seconds dall'ultimo
        invio o se il buffer e' pieno.
        """
        if self._buffer and (now - self._last_flush_sim) >= self.config.batch_max_seconds:
            self.flush()
        self._last_flush_sim = now

    def flush(self):
        """
        Forza l'invio immediato del batch corrente.
        """
        while self._buffer:
            chunk = self._buffer[: self.config.batch_max_events]
            if self._post(chunk):
                del self._buffer[: len(chunk)]
            else:
                break  # gateway giu': riprova al prossimo flush
        self._enforce_buffer_cap()

    def _post(self, events: list[ProbeEvent]):
        """
        Esegue il POST HTTP. Ritorna True se andato a buon fine.
        In caso di fallimento, gli eventi restano nel buffer per il retry.
        """
        payload = [e.to_dict() for e in events]
        try:
            resp = self._session.post(
                self.config.gateway_url, json=payload, timeout=self._timeout
            )
            ok = 200 <= resp.status_code < 300
        except requests.RequestException:
            ok = False

        if ok:
            self.stats["sent"] += len(events)
        else:
            self.stats["failed"] += len(events)
        return ok

    def _enforce_buffer_cap(self) -> None:
        """Se il buffer supera il tetto, scarta i piu' vecchi e li conta."""
        overflow = len(self._buffer) - self._max_buffer
        if overflow > 0:
            del self._buffer[:overflow]
            self.stats["dropped"] += overflow

    def close(self) -> None:
        """Flush finale e chiusura della sessione HTTP."""
        self.flush()
        self._session.close()