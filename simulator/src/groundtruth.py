"""
Ground truth recording module for the crowd simulator.

Persists the exact, simulated crowd count across all areas tick-by-tick into a CSV file.
Enables offline performance evaluation and benchmarking of edge regression estimation accuracy against ground reality.
"""

import csv
from datetime import timedelta

from .config import SimConfig
from .emission import DEFAULT_EPOCH
from .models import Area


class GroundTruthRecorder:
    """
    Streams exact crowd populations per monitored and unmonitored zone to a structured CSV file.
    Maintains periodic file flush thresholds and formats ISO-8601 UTC timestamps anchored to the scenario epoch.
    """

    def __init__(self, config: SimConfig, area_ids: list[str]) -> None:
        """
        Initializes the ground truth recorder, opening the target CSV file and writing column headers.

        @param config Global simulation configuration providing the output file path.
        @param area_ids List of unique area identifiers determining the column sequence in the CSV.
        """
        self.config = config
        self.area_ids = list(area_ids)
        self.epoch = DEFAULT_EPOCH
        self._flush_every = 20
        self._rows_since_flush = 0

        self._file = open(config.ground_truth_path, "w", newline="")
        self._writer = csv.writer(self._file)
        self._writer.writerow(["t", "ts"] + list(self.area_ids))

    def record(self, t: float, areas: list[Area]) -> None:
        """
        Appends a row capturing simulated time, formatted ISO timestamp, and ground-truth headcount per area.
        Flushes to disk every 20 recorded ticks to balance I/O overhead.

        @param t Current simulation time in seconds.
        @param areas List of live Area entity instances containing runtime population counts.
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
        """
        Flushes remaining buffered rows and closes the underlying CSV file descriptor cleanly.
        """
        if not self._file.closed:
            self._file.flush()
            self._file.close()

    def _format_ts(self, sim_time: float) -> str:
        """
        Converts simulation elapsed seconds into an ISO-8601 UTC timestamp string anchored to the scenario epoch.

        @param sim_time Elapsed simulation seconds.
        @return ISO-8601 UTC formatted timestamp string.
        """
        dt = self.epoch + timedelta(seconds=sim_time)
        return dt.isoformat(timespec="milliseconds").replace("+00:00", "Z")