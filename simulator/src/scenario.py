"""
Uno scenario è una sequenza di fasi. Ogni fase ha una matrice di transizione "obiettivo".
L'idea è quella di avere matrici diverse in momenti diversi dell'evento.
Andiamo poi ad interpolare e non cambiare di colpo la matrice perchè altrimenti le curve di affollamento avrebbero
uno scalino netto, poco realistico.
"""

from dataclasses import dataclass
from collections import defaultdict
from bisect import bisect_right

import numpy as np


@dataclass
class Phase:
    """
    Una fase del copione: da start_s a end_s è la finestra temporale e target_matrix è la matrice obiettivo
    """
    name: str
    start_seconds: float
    end_seconds: float
    target_matrix: np.ndarray


class Scenario:
    """
    Il copione completo dell'evento: le fasi e la logica di
    interpolazione che produce P(t) a ogni tick.
    """
 
    def __init__(self, area_ids: list[str], phases: list[Phase]):
        if not phases:
            raise ValueError("Uno scenario deve avere almeno una fase")
        self.area_ids = list(area_ids)
        self.n_areas = len(area_ids)
        # Ordiniamo le fasi per start_seconds
        self.phases = sorted(phases, key=lambda p: p.start_seconds)
        self._starts = [p.start_seconds for p in self.phases]

        # Validazione
        for ph in self.phases:
            M = ph.target_matrix
            if M.shape != (self.n_areas, self.n_areas):
                raise ValueError(f"fase '{ph.name}': matrice {M.shape}, attesa " f"({self.n_areas}, {self.n_areas})")
            if not np.allclose(M.sum(axis=1), 1.0, atol=1e-9):
                raise ValueError(f"fase '{ph.name}': righe della matrice non sommano a 1")

 
    def matrix_at(self, t: float):
        """
        P(t): matrice di transizione effettiva al tempo t.
 
        Questo metodo preso in input t, resituisce P(t), ovvero la matrice di transizione da usare al tempo t.
        Se siamo in un periodo di transizione tra due fasi, la matrice viene interpolata linearmente.
        """
        if t < self._starts[0]:
            return self.phases[0].target_matrix
        if t >= self._starts[-1]:
            return self.phases[-1].target_matrix

        # Troviamo istantaneamente tra quali due fasi ci troviamo al secondo t
        k = bisect_right(self._starts, t) - 1
        t0, t1 = self._starts[k], self._starts[k + 1]
        # Capiamo se ci troviamo più verso la fase k o verso la fase k+1, e pesiamo le due matrici di conseguenza
        w = (t - t0) / (t1 - t0)
        m0 = self.phases[k].target_matrix
        m1 = self.phases[k + 1].target_matrix
        return (1.0 - w) * m0 + w * m1
 
    def current_phase(self, t: float) -> Phase:
        """
        La fase attiva al tempo t (per logging / dashboard).
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
    ):
        """
        Costruisce una matrice di transizione valida da una descrizione
        ad alto livello (probabilita' di restare + flussi salienti),
        garantendo che ogni riga sommi a 1.
        """

        stay_by_area = stay_by_area or {}
        for s in (stay_prob, *stay_by_area.values()):
            if not(0.0 <= s <= 1.0):
                raise ValueError("le porbabilità di permanenza devono stare in [0, 1]")
        
        n = len(area_ids)
        idx = {a: i for i, a in enumerate(area_ids)}

        out_by_src: dict[str, dict[str, float]] = defaultdict(dict)
        for (src, dst), w in flows.items():
            if src not in idx or dst not in idx:
                raise ValueError(f"flusso ({src}->{dst}) referenzia un'area ignota")
            out_by_src[src][dst] = w

        M = np.zeros((n, n), dtype=float)
        for a in area_ids:
            i = idx[a]
            outs = out_by_src.get(a, {})
            if not outs:
                M[i, i] = 1.0                       # nessun flusso: resta
                continue
            stay = stay_by_area.get(a, stay_prob)
            total = sum(outs.values())
            M[i, i] = stay
            for dst, w in outs.items():
                M[i, idx[dst]] += (1.0 - stay) * (w / total)
        return M
 
    @classmethod
    def default_fiera(cls, area_ids: list[str]):
        """
        Costruisce il copione di default della fiera:
        riempimento -> picco al palco -> deflusso, piu' un'emergenza.
        """
        present = set(area_ids)

        def flows(*triples: tuple[str, str, float]) -> dict[tuple[str, str], float]:
            return {
                (s, d): w
                for (s, d, w) in triples
                if s in present and d in present
            }
        
        # Arrivo: si entra e ci si disperde verso le attrazioni via corridoio.
        arrivo = cls.build_matrix(area_ids, stay_prob=0.98, flows=flows(
            ("outside", "entrance", 1.0),  
            ("entrance", "corridor", 1.0),
            ("corridor", "stand", 2.0),
            ("corridor", "food",  1.5),
            ("corridor", "stage", 1.5),
            ("stand", "corridor", 1.0),
            ("food",  "corridor", 1.0),
        ), stay_by_area={"outside": 0.90, "corridor": 0.55})


        # Concerto: tutti convergono al palco, che trattiene e si riempie.
        concerto = cls.build_matrix(area_ids, stay_prob=0.99, flows=flows(
            ("entrance", "corridor", 1.0),
            ("stand", "corridor", 1.0),
            ("food",  "corridor", 1.0),
            ("corridor", "stage", 1.0),    # il corridoio incanala verso il palco
        ), stay_by_area={"corridor": 0.40})    # nessun flusso da stage -> si riempie

        # Deflusso: il palco si svuota, la gente si ridistribuisce.
        deflusso = cls.build_matrix(area_ids, stay_prob=0.98, flows=flows(
            ("stage", "corridor", 1.0),
            ("corridor", "food",  1.5),
            ("corridor", "stand", 1.0),
        ), stay_by_area={"corridor": 0.50, "stage": 0.95})

        # Uscita: tutti verso l'uscita via corridoio. exit trattiene -> crush.
        uscita = cls.build_matrix(area_ids, stay_prob=0.98, flows=flows(
            ("stage", "corridor", 1.0),
            ("stand", "corridor", 1.0),
            ("food",  "corridor", 1.0),
            ("entrance", "corridor", 1.0),
            ("corridor", "exit", 1.0),
            ("exit", "outside", 1.0),       # <- scarico verso il serbatoio
        ), stay_by_area={"corridor": 0.50, "stage": 0.92,
                         "stand": 0.95, "food": 0.95, "exit": 0.85})

        phases = [
            Phase("arrivo",   0.0,   150.0, arrivo),
            Phase("concerto", 150.0, 360.0, concerto),
            Phase("deflusso", 360.0, 480.0, deflusso),
            Phase("uscita",   480.0, 600.0, uscita),
        ]
        return cls(area_ids, phases)