"""
Markovian crowd dynamics and population transition module.

Simulates crowd relocation between venue areas based on stochastic transition matrices
combined with capacity saturation dampening, strictly conserving total population.
"""

import random
import numpy as np

from .models import Area, Device


class PopulationModel:
    """
    Manages attendee population transitions across venue zones using a saturated Markov chain.
    Ensures total attendee headcount conservation (sum(n_i) = N) while scaling transition
    probabilities inversely with destination zone saturation.
    """

    def __init__(
        self,
        areas: list[Area],
        devices: list[Device],
        rng: random.Random,
    ):
        """
        Initializes the population model with zone references, tracked devices, and a deterministic random generator.

        @param areas List of runtime Area entities.
        @param devices List of tracked Device entities representing the attendee population.
        @param rng Initialized random number generator for reproducible sampling.
        """
        self.areas = areas
        self.devices = devices
        self.rng = rng

        self.area_ids: list[str] = [a.config.area_id for a in areas]
        self.index_of: dict[str, int] = {aid: i for i, aid in enumerate(self.area_ids)}
        self.areas_by_id: dict[str, Area] = {a.config.area_id: a for a in areas}
        self.capacities = np.array([float(a.config.capacity) for a in areas], dtype=float)

    def seed_devices(self, initial_distribution: np.ndarray | None = None) -> None:
        """
        Distributes all devices across the venue areas at simulation initialization.
        Defaults to placing 100% of devices into the 'outside' reservoir zone if no custom distribution is provided.
        Uses cumulative distribution sampling to assign devices and syncs Area population counts.

        @param initial_distribution Optional probability distribution vector across areas summing to 1.
        """
        n_areas = len(self.area_ids)
        if initial_distribution is None:
            probs = np.zeros(n_areas)
            probs[self.index_of["outside"]] = 1.0
        else:
            probs = np.asarray(initial_distribution, dtype=float)
            probs = probs / probs.sum()

        cum = np.cumsum(probs)
        counts = np.zeros(n_areas, dtype=int)
        for device in self.devices:
            j = self._sample_index(cum, n_areas)
            device.area_id = self.area_ids[j]
            counts[j] += 1

        self._write_counts(counts)

    def apply_saturation(self, base_matrix: np.ndarray) -> np.ndarray:
        """
        Applies capacity saturation dampening to the target transition matrix.
        Inflows from zone i to zone j are scaled by destination residual capacity (1 - occupancy_j).
        Any rejected attendee mass that cannot enter saturated zones is reallocated onto the diagonal (staying put),
        preserving row-stochasticity (rows sum to 1.0).

        @param base_matrix Baseline row-stochastic transition probability matrix.
        @return Saturated row-stochastic transition probability matrix.
        """
        occ = self.occupancy_vector()
        accept = np.clip(1.0 - occ, 0.0, 1.0)

        out = base_matrix * accept[np.newaxis, :]
        np.fill_diagonal(out, 0.0)
        np.fill_diagonal(out, 1.0 - out.sum(axis=1))

        return out

    def step(self, transition_matrix: np.ndarray) -> None:
        """
        Advances the crowd population by one simulation tick.
        For each device in area i, samples its next destination from row i of the cumulative
        transition matrix via inverse-CDF sampling and updates individual device and area counters.

        @param transition_matrix Row-stochastic transition matrix for the current tick.
        """
        n_areas = transition_matrix.shape[0]
        cum_rows = np.cumsum(transition_matrix, axis=1)

        counts = np.zeros(n_areas, dtype=int)
        for device in self.devices:
            i = self.index_of[device.area_id]
            j = self._sample_index(cum_rows[i], n_areas)
            device.area_id = self.area_ids[j]
            counts[j] += 1

        self._write_counts(counts)

    def population_vector(self) -> np.ndarray:
        """
        Extracts the current headcount per area ordered by canonical area_ids. Always sums to N.

        @return 1D NumPy array of population counts.
        """
        return np.array([self.areas_by_id[aid].population for aid in self.area_ids], dtype=float)

    def occupancy_vector(self) -> np.ndarray:
        """
        Computes the relative occupancy fraction (population / capacity) for each area.

        @return 1D NumPy array of occupancy ratios.
        """
        return self.population_vector() / self.capacities

    def devices_in_area(self, area_id: str) -> list[Device]:
        """
        Filters and returns all tracked devices currently positioned within the specified area.

        @param area_id The unique area identifier.
        @return List of Device instances currently located in the area.
        """
        return [d for d in self.devices if d.area_id == area_id]

    @staticmethod
    def stationary_distribution(matrix: np.ndarray) -> np.ndarray:
        """
        Computes the stationary distribution (left eigenvector of eigenvalue 1: pi * P = pi)
        for a transition matrix, normalized to sum to 1.0.

        @param matrix Row-stochastic transition matrix.
        @return 1D NumPy array representing the steady-state probability distribution.
        """
        vals, vecs = np.linalg.eig(matrix.T)
        idx = int(np.argmin(np.abs(vals - 1.0)))
        pi = np.real(vecs[:, idx])
        return pi / pi.sum()

    def _sample_index(self, cum: np.ndarray, n_areas: int) -> int:
        """
        Samples an index from a cumulative probability distribution using the inverse-CDF technique with U ~ Uniform[0,1).

        @param cum Cumulative probability row vector.
        @param n_areas Total number of candidate destination areas.
        @return Selected area index.
        """
        u = self.rng.random()
        j = int(np.searchsorted(cum, u, side="right"))
        return min(j, n_areas - 1)

    def _write_counts(self, counts: np.ndarray) -> None:
        """
        Updates the population attribute of each runtime Area object based on sampled counts.

        @param counts 1D integer array containing the updated population per area index.
        """
        for aid, area in self.areas_by_id.items():
            area.population = int(counts[self.index_of[aid]])