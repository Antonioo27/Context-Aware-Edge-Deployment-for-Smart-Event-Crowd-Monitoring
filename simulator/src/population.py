import random
 
import numpy as np
 
from .models import Area, Device


class PopulationModel:
    """
    Muove i device tra le aree secondo una catena di Markov con
    saturazione, mantenendo sum(n_i) = N. dove n_i è il numero di persone nell'area i
    """
 
    def __init__(
        self,
        areas: list[Area],
        devices: list[Device],
        rng: random.Random,
    ):
        self.areas = areas
        self.devices = devices
        self.rng = rng

        # Ordine delle aree: indicizza righe/colonne della matrice
        self.area_ids: list[str] = [a.config.area_id for a in areas]
        self.index_of: dict[str, int] = {aid: i for i, aid in enumerate(self.area_ids)}
        self.areas_by_id: dict[str, Area] = {a.config.area_id: a for a in areas}
        self.capacities = np.array([float(a.config.capacity) for a in areas], dtype=float)

    # Costruzione dello stato iniziale

    def seed_devices(self, initial_distribution: np.ndarray | None = None):
        """
        Assegna i device alle aree all'avvio utilizzando una certa distribuzione
        """

        n_areas = len(self.area_ids)
        if initial_distribution is None:
            probs = np.zeros(n_areas)
            probs[self.index_of["outside"]] = 1.0
        else:
            probs = np.asarray(initial_distribution, dtype=float)
            probs = probs / probs.sum()  # Normalizziamo

        # Somma cumulativa
        cum = np.cumsum(probs)
        counts = np.zeros(n_areas, dtype=int)
        for device in self.devices:
            j = self._sample_index(cum, n_areas)
            device.area_id = self.area_ids[j]
            counts[j] += 1

        self._write_counts(counts)  



    def apply_saturation(self, base_matrix: np.ndarray):
        """
        Il flusso i -> j viene scalato della capacità residua di j. La massa che non riesce
        a muoversi resta nella propria area. La matrice risultante è stocastica.
        """
        occ = self.occupancy_vector()
        # array che indica la percentuale di persone che possono entrare in ogni area
        accept = np.clip(1.0 - occ, 0.0, 1.0)

        out = base_matrix * accept[np.newaxis, :]
        np.fill_diagonal(out, 0.0)  # Azzero i numeri sulla diagonale perchè sono sporchi
        np.fill_diagonal(out, 1.0 - out.sum(axis=1)) # Rimettiamo sulla diagonale ciò che avanza per fare somma = 1
     
        return out

    def step(self, transition_matrix: np.ndarray) -> None:
        """
        Avanza la popolazione di un tick.
        Per ogni device nell'area i, estrae la prossima area dalla riga
        i della matrice (Categorical) e aggiorna device.area_id.
        """
        n_areas = transition_matrix.shape[0]
        cum_rows = np.cumsum(transition_matrix, axis=1)

        counts = np.zeros(n_areas, dtype=int)
        for device in self.devices:
            # Dove si trova dispositivo in questo istante
            i = self.index_of[device.area_id]
            # Estrazione casuale della nuova posizione
            j = self._sample_index(cum_rows[i], n_areas)
            device.area_id = self.area_ids[j]
            counts[j] += 1

        self._write_counts(counts)

    def population_vector(self):
        """Vettore che restituisce il numero di persone in ogni area. Somma sempre a N."""
        return np.array([self.areas_by_id[aid].population for aid in self.area_ids], dtype=float)

    def occupancy_vector(self):
        """Vettore = (n_i / C_i) su tutte le aree."""
        return self.population_vector() / self.capacities
 
    def devices_in_area(self, area_id: str):
        """Tutti i device attualmente in una data area."""
        return [d for d in self.devices if d.area_id == area_id]
 
    @staticmethod
    def stationary_distribution(matrix: np.ndarray):
        """
        Autovettore sinistro pi di autovalore 1 (pi P = pi).
        Non serve al loop; utile per inizializzare una fase "a regime".
        """
        vals, vecs = np.linalg.eig(matrix.T)
        idx = int(np.argmin(np.abs(vals - 1.0)))
        pi = np.real(vecs[:, idx])
        return pi / pi.sum()


    # --- Helper ---

    def _sample_index(self, cum: np.ndarray, n_areas: int):
        """
        Inverse-CDF: pesca U~Uniform[0,1) e restituisce l'indice della riga della matrice di transizione.
        """
        u = self.rng.random()
        j = int(np.searchsorted(cum, u, side="right"))
        return min(j, n_areas - 1)

    def _write_counts(self, counts: np.ndarray):
        """
        Riporta i conteggi calcolati dentro gli oggetti Area.
        """
        for aid, area in self.areas_by_id.items():
            area.population = int(counts[self.index_of[aid]])