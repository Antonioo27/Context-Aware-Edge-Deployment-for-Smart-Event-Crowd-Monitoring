"""
Il simulatore conosce la popolazione vera di ogni area ad ogni istante tramite questo file di ground truth.
Salvarla permette al modulo di valutazione sperimentale di confrontare la stima dell'edge con il valore reale e misurare
l'errore.
""" 

import csv

from .config import SimConfig
from .emission import DEFAULT_EPOCH
from .models import Area
from datetime import timedelta

class GroundTruthRecorder:
    """Scrive un CSV con popolazione vera per area, tick per tick."""
 
    def __init__(self, config: SimConfig, area_ids: list[str]) -> None:
        self.config = config
        self.area_ids = list(area_ids)
        self.epoch = DEFAULT_EPOCH
        self._flush_every = 20          # righe tra un flush e l'altro
        self._rows_since_flush = 0

        self._file = open(config.ground_truth_path, "w", newline="")
        self._writer = csv.writer(self._file)
        self._writer.writerow(["t", "ts"] + list(self.area_ids))

    def record(self, t: float, areas: list[Area]) -> None:
        """
        Registra una riga: tempo (simulato e ISO) + popolazione vera di
        ogni area, nell'ordine di area_ids.
        """
        by_id = {a.config.area_id: a for a in areas}
        row: list = [f"{t:.1f}", self._format_ts(t)]
        for aid in self.area_ids:
            row.append(by_id[aid].population)
        self._writer.writerow(row)

        self._rows_since_flush += 1
        if self._rows_since_flush >= self._flush_every:
            self._file.flush()
            self._rows_since_flush = 0

    def close(self) -> None:
        """Flush finale e chiusura del file."""
        if not self._file.closed:
            self._file.flush()
            self._file.close()

    def _format_ts(self, sim_time: float) -> str:
        """Stesso schema dell'emitter: ISO-8601 UTC, ancorato all'epoca."""
        dt = self.epoch + timedelta(seconds=sim_time)
        return dt.isoformat(timespec="milliseconds").replace("+00:00", "Z")