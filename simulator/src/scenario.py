"""
Event script and transition matrix interpolation module.

Defines temporal simulation phases and provides piecewise linear interpolation
between target transition probability matrices across successive event milestones
(e.g., ingress, concert peak, intermission, egress).
"""

from collections import defaultdict
from bisect import bisect_right
from dataclasses import dataclass

import numpy as np


@dataclass
class Phase:
    """
    Represents an operational phase within the event timeline, bounded by start and end timestamps,
    associated with a target Markov transition probability matrix.
    """

    name: str
    start_seconds: float
    end_seconds: float
    target_matrix: np.ndarray


class Scenario:
    """
    Manages the complete temporal script of an event by interpolating between sequential phase matrices.
    Performs piecewise linear interpolation between target matrices to ensure smooth, realistic crowd density curves
    without abrupt, unphysical step discontinuities.
    """

    def __init__(self, area_ids: list[str], phases: list[Phase]):
        """
        Initializes the scenario with area identifiers and ordered phases, validating dimensions and row-stochasticity.

        @param area_ids Canonical list of area identifiers defining matrix rows and columns.
        @param phases List of Phase instances defining sequential operational milestones.
        @throws ValueError If phases is empty, or if target matrices fail shape or row-stochasticity checks.
        """
        if not phases:
            raise ValueError("A scenario must define at least one phase")
        self.area_ids = list(area_ids)
        self.n_areas = len(area_ids)
        self.phases = sorted(phases, key=lambda p: p.start_seconds)
        self._starts = [p.start_seconds for p in self.phases]

        for ph in self.phases:
            matrix = ph.target_matrix
            if matrix.shape != (self.n_areas, self.n_areas):
                raise ValueError(f"phase '{ph.name}': matrix shape {matrix.shape}, expected ({self.n_areas}, {self.n_areas})")
            if not np.allclose(matrix.sum(axis=1), 1.0, atol=1e-9):
                raise ValueError(f"phase '{ph.name}': matrix rows do not sum to 1.0")

    def matrix_at(self, t: float) -> np.ndarray:
        """
        Computes the effective row-stochastic Markov transition matrix P(t) at simulation time t.
        Uses binary search to locate adjacent phases and computes piecewise linear interpolation weights:
        P(t) = (1 - w) * M_k + w * M_{k+1}.

        @param t Current simulation timestamp in seconds.
        @return Interpolated row-stochastic transition matrix for time t.
        """
        if t < self._starts[0]:
            return self.phases[0].target_matrix
        if t >= self._starts[-1]:
            return self.phases[-1].target_matrix

        k = bisect_right(self._starts, t) - 1
        t0, t1 = self._starts[k], self._starts[k + 1]
        w = (t - t0) / (t1 - t0)
        m0 = self.phases[k].target_matrix
        m1 = self.phases[k + 1].target_matrix
        return (1.0 - w) * m0 + w * m1

    def current_phase(self, t: float) -> Phase:
        """
        Identifies the active operational phase at simulation time t for telemetry and logging.

        @param t Current simulation timestamp in seconds.
        @return Active Phase entity instance.
        """
        if t <= self.phases[0].start_seconds:
            return self.phases[0]
        for ph in self.phases:
            if ph.start_seconds <= t < ph.end_seconds:
                return ph
        return self.phases[-1]

    @staticmethod
    def build_matrix(
        area_ids: list[str],
        stay_prob: float,
        flows: dict[tuple[str, str], float],
        stay_by_area: dict[str, float] | None = None,
    ) -> np.ndarray:
        """
        Synthesizes a valid row-stochastic transition matrix from high-level flow weights and staying probabilities.
        Allocates diagonal staying probabilities and normalizes off-diagonal transition weights proportionally.

        @param area_ids Canonical ordered list of area identifiers.
        @param stay_prob Default probability of remaining in an area across consecutive ticks.
        @param flows Directed edge weights mapping (source_area, target_area) tuples to relative flow intensities.
        @param stay_by_area Optional dictionary overriding staying probability for specific individual areas.
        @return Fully normalized row-stochastic transition matrix of shape (n, n).
        @throws ValueError If staying probabilities are outside [0, 1] or flow endpoints refer to unknown areas.
        """
        stay_by_area = stay_by_area or {}
        for s in (stay_prob, *stay_by_area.values()):
            if not (0.0 <= s <= 1.0):
                raise ValueError("staying probabilities must be in range [0, 1]")

        n = len(area_ids)
        idx = {a: i for i, a in enumerate(area_ids)}

        out_by_src: dict[str, dict[str, float]] = defaultdict(dict)
        for (src, dst), w in flows.items():
            if src not in idx or dst not in idx:
                raise ValueError(f"flow ({src}->{dst}) references an unknown area")
            out_by_src[src][dst] = w

        matrix = np.zeros((n, n), dtype=float)
        for a in area_ids:
            i = idx[a]
            outs = out_by_src.get(a, {})
            if not outs:
                matrix[i, i] = 1.0
                continue
            stay = stay_by_area.get(a, stay_prob)
            total = sum(outs.values())
            matrix[i, i] = stay
            for dst, w in outs.items():
                matrix[i, idx[dst]] += (1.0 - stay) * (w / total)
        return matrix

    @classmethod
    def from_dynamic_areas(cls, areas_config: list) -> "Scenario":
        """
        Dynamically constructs realistic 5-phase event transition matrices based on area operational classifications
        (OUTSIDE, ENTRANCE, TRANSIT, PEAK_ATTRACTION, SUSTAINED_ATTRACTION, EXIT, GENERIC):
        - Phase 1: Ingress & gradual inflow (0s - 240s) from outside/entrances to transit hubs and stands.
        - Phase 2: Main stage concert peak (240s - 480s) focusing attendee density into peak attractions.
        - Phase 3: Intermission & refreshment pause (480s - 650s) dispersing attendees into sustained food/stand areas.
        - Phase 4: Controlled egress & exit queueing (650s - 800s) draining venues toward exits.
        - Phase 5: Final venue clearance & evacuation (800s - 900s) returning population to outside reservoir.

        @param areas_config List of AreaConfig instances configured for the venue.
        @return Configured Scenario instance.
        """
        area_ids = [a.area_id for a in areas_config]

        by_type = defaultdict(list)
        for a in areas_config:
            by_type[a.area_type].append(a.area_id)

        outsides = by_type.get("OUTSIDE", ["outside"])
        entrances = by_type.get("ENTRANCE", []) or outsides
        exits = by_type.get("EXIT", []) or entrances
        transits = by_type.get("TRANSIT", [])
        peaks = by_type.get("PEAK_ATTRACTION", [])
        sustained = by_type.get("SUSTAINED_ATTRACTION", [])
        generics = by_type.get("GENERIC", [])

        hubs = transits if transits else entrances

        def connect_groups(source: list[str], targets: list[str], weight: float, flows_dict: dict) -> None:
            """
            Establishes directed flow weights between all pairs of source and target areas.

            @param source List of source area identifiers.
            @param targets List of target area identifiers.
            @param weight Relative flow intensity weight.
            @param flows_dict Destination flow dictionary to populate.
            """
            for s in source:
                for t in targets:
                    if s != t:
                        flows_dict[(s, t)] = weight

        arrival_flows: dict = {}
        connect_groups(outsides, entrances, 4.0, arrival_flows)
        connect_groups(entrances, hubs, 4.0, arrival_flows)
        connect_groups(hubs, sustained + generics, 3.0, arrival_flows)
        connect_groups(hubs, peaks, 2.0, arrival_flows)

        stay_arrival = {}
        for out in outsides:
            stay_arrival[out] = 0.9750
        for e in entrances:
            stay_arrival[e] = 0.8800
        for tr in transits:
            stay_arrival[tr] = 0.8800
        for s in sustained:
            stay_arrival[s] = 0.9500
        for p in peaks:
            stay_arrival[p] = 0.9700
        for ex in exits:
            stay_arrival[ex] = 0.9500

        m_arrival = cls.build_matrix(area_ids, stay_prob=0.960, flows=arrival_flows, stay_by_area=stay_arrival)

        peak_flows: dict = {}
        connect_groups(sustained + generics + entrances, hubs, 3.0, peak_flows)
        connect_groups(hubs, peaks, 5.0, peak_flows)

        stay_peak = {}
        for out in outsides:
            stay_peak[out] = 0.9950
        for e in entrances:
            stay_peak[e] = 0.8500
        for tr in transits:
            stay_peak[tr] = 0.9000
        for s in sustained:
            stay_peak[s] = 0.9500
        for p in peaks:
            stay_peak[p] = 0.9960
        for ex in exits:
            stay_peak[ex] = 0.9500

        m_peak = cls.build_matrix(area_ids, stay_prob=0.985, flows=peak_flows, stay_by_area=stay_peak)

        pause_flows: dict = {}
        connect_groups(peaks, hubs, 4.0, pause_flows)
        connect_groups(hubs, sustained, 5.0, pause_flows)

        stay_pause = {}
        for out in outsides:
            stay_pause[out] = 0.9950
        for e in entrances:
            stay_pause[e] = 0.9000
        for tr in transits:
            stay_pause[tr] = 0.9200
        for p in peaks:
            stay_pause[p] = 0.9800
        for s in sustained:
            stay_pause[s] = 0.9940
        for ex in exits:
            stay_pause[ex] = 0.9500

        m_pause = cls.build_matrix(area_ids, stay_prob=0.985, flows=pause_flows, stay_by_area=stay_pause)

        deflux_flows: dict = {}
        connect_groups(peaks + sustained + generics, hubs, 3.0, deflux_flows)
        connect_groups(hubs, exits, 4.0, deflux_flows)
        connect_groups(exits, outsides, 5.0, deflux_flows)

        stay_deflux = {}
        for out in outsides:
            stay_deflux[out] = 1.0000
        for e in entrances:
            stay_deflux[e] = 0.9000
        for tr in transits:
            stay_deflux[tr] = 0.9000
        for p in peaks:
            stay_deflux[p] = 0.9400
        for s in sustained:
            stay_deflux[s] = 0.9400
        for ex in exits:
            stay_deflux[ex] = 0.9300

        m_deflux = cls.build_matrix(area_ids, stay_prob=0.960, flows=deflux_flows, stay_by_area=stay_deflux)

        exit_flows: dict = {}
        connect_groups(exits, outsides, 6.0, exit_flows)
        connect_groups(hubs + entrances, exits, 4.0, exit_flows)
        connect_groups(peaks + sustained, hubs, 3.0, exit_flows)

        stay_exit = {}
        for out in outsides:
            stay_exit[out] = 1.0000
        for e in entrances:
            stay_exit[e] = 0.8500
        for tr in transits:
            stay_exit[tr] = 0.8500
        for ex in exits:
            stay_exit[ex] = 0.8500
        for p in peaks:
            stay_exit[p] = 0.8500
        for s in sustained:
            stay_exit[s] = 0.8500

        m_exit = cls.build_matrix(area_ids, stay_prob=0.900, flows=exit_flows, stay_by_area=stay_exit)

        phases = [
            Phase("arrival", 0.0, 240.0, m_arrival),
            Phase("concert_main", 240.0, 480.0, m_peak),
            Phase("food_pause", 480.0, 650.0, m_pause),
            Phase("deflux", 650.0, 800.0, m_deflux),
            Phase("exit", 800.0, 900.0, m_exit),
        ]
        return cls(area_ids, phases)