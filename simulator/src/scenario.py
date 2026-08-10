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
 
    # @classmethod
    # def default_fiera(cls, area_ids: list[str]):
    #     """
    #     Costruisce il copione di default della fiera:
    #     riempimento -> picco al palco -> deflusso, piu' un'emergenza.
    #     """
    #     present = set(area_ids)

    #     def flows(*triples: tuple[str, str, float]) -> dict[tuple[str, str], float]:
    #         return {
    #             (s, d): w
    #             for (s, d, w) in triples
    #             if s in present and d in present
    #         }
        
    #     # Arrivo: si entra e ci si disperde verso le attrazioni via corridoio.
    #     arrivo = cls.build_matrix(area_ids, stay_prob=0.98, flows=flows(
    #         ("outside", "entrance", 1.0),  
    #         ("entrance", "corridor", 1.0),
    #         ("corridor", "stand", 2.0),
    #         ("corridor", "food",  1.5),
    #         ("corridor", "stage", 1.5),
    #         ("stand", "corridor", 1.0),
    #         ("food",  "corridor", 1.0),
    #     ), stay_by_area={"outside": 0.90, "corridor": 0.55, "entrance": 0.60})


    #     # Concerto: tutti convergono al palco, che trattiene e si riempie.
    #     concerto = cls.build_matrix(area_ids, stay_prob=0.99, flows=flows(
    #         ("entrance", "corridor", 1.0),
    #         ("stand", "corridor", 1.0),
    #         ("food",  "corridor", 1.0),
    #         ("corridor", "stage", 1.0),    # il corridoio incanala verso il palco
    #     ), stay_by_area={"corridor": 0.40, "entrance": 0.60})    # nessun flusso da stage -> si riempie

    #     # Deflusso: il palco si svuota, la gente si ridistribuisce.
    #     deflusso = cls.build_matrix(area_ids, stay_prob=0.98, flows=flows(
    #         ("stage", "corridor", 1.0),
    #         ("corridor", "food",  1.5),
    #         ("corridor", "stand", 1.0),
    #     ), stay_by_area={"corridor": 0.50, "stage": 0.95, "entrance": 0.60})

    #     # Uscita: tutti verso l'uscita via corridoio. exit trattiene -> crush.
    #     uscita = cls.build_matrix(area_ids, stay_prob=0.98, flows=flows(
    #         ("stage", "corridor", 1.0),
    #         ("stand", "corridor", 1.0),
    #         ("food",  "corridor", 1.0),
    #         ("entrance", "corridor", 1.0),
    #         ("corridor", "exit", 1.0),
    #         ("exit", "outside", 1.0),       # <- scarico verso il serbatoio
    #     ), stay_by_area={"corridor": 0.50, "stage": 0.92,
    #                      "stand": 0.95, "food": 0.95, "exit": 0.50, "entrance": 0.60})

    #     phases = [
    #         Phase("arrivo",   0.0,   150.0, arrivo),
    #         Phase("concerto", 150.0, 360.0, concerto),
    #         Phase("deflusso", 360.0, 480.0, deflusso),
    #         Phase("uscita",   480.0, 600.0, uscita),
    #     ]
    #     return cls(area_ids, phases)

    @classmethod
    def from_dynamic_areas(cls, areas_config: list):
        """
        Costruisce dinamicamente le matrici di transizione analizzando i ruoli/tag delle aree configurate nel backend.
        """
        area_ids = [a.area_id for a in areas_config]

        # Raggruppiamo le aree per ruolo
        by_type = defaultdict(list)
        for a in areas_config:
            by_type[a.area_type].append(a.area_id)

        outsides = by_type.get("OUTSIDE", ["outside"])
        entrances = by_type.get("ENTRANCE", []) or outsides
        exits = by_type.get("EXIT", []) or entrances
        transits = by_type.get("TRANSIT", [])
        peaks = by_type.get("PEAK_ATTRACTION", []) # Palchi principali, area, ecc..
        sustained = by_type.get("SUSTAINED_ATTRACTION", []) # Food court, stand, bar, ecc..
        generics = by_type.get("GENERIC", [])

        # Come hub di smistamento principale usiamo i corridoi, in alternativa le entrate
        hubs = transits if transits else entrances

        def connect_groups(source: list[str], targets: list[str], weight: float, flows_dict: dict):
            """
            Collega ogni area della sorgente a ogni area di destinazione
            """
            for s in source:
                for t in targets:
                    if s != t:
                        flows_dict[(s, t)] = weight

        # ==================================================================
        # FASE 1: ARRIVO E INGRESSO GRADUALE (0s - 180s)
        # ==================================================================
        arrivo_flows = {}
        connect_groups(outsides, entrances, 3.0, arrivo_flows)
        connect_groups(entrances, hubs, 3.0, arrivo_flows)
        connect_groups(hubs, sustained + peaks + generics, 1.0, arrivo_flows)

        stay_arrivo = {}
        for out in outsides:  stay_arrivo[out] = 0.990  # Svuotamento fluido di Outside
        for e in entrances:   stay_arrivo[e]   = 0.940  # ~16s permanenza in Ingress
        for tr in transits:   stay_arrivo[tr]  = 0.920  # ~12s transito in Corridoio
        for s in sustained:   stay_arrivo[s]   = 0.980
        for p in peaks:       stay_arrivo[p]   = 0.985
        for ex in exits:      stay_arrivo[ex]  = 0.950

        m_arrivo = cls.build_matrix(area_ids, stay_prob=0.980, flows=arrivo_flows, stay_by_area=stay_arrivo)

        # ==================================================================
        # FASE 2: CONCERTO MAIN STAGE (180s - 400s) -> Innesca HIGHLY_RISING
        # ==================================================================
        picco_flows = {}
        connect_groups(outsides, entrances, 2.0, picco_flows)
        connect_groups(entrances + sustained + generics, hubs, 3.0, picco_flows)
        connect_groups(hubs, peaks, 5.0, picco_flows)

        stay_picco = {}
        for out in outsides:  stay_picco[out] = 0.992
        for e in entrances:   stay_picco[e]   = 0.900  # Ingresso si svuota
        for tr in transits:   stay_picco[tr]  = 0.920
        for s in sustained:   stay_picco[s]   = 0.950  # Food/Stand si svuotano verso lo Stage
        for p in peaks:       stay_picco[p]   = 0.996  # Stage cattura la massa (~333s di permanenza)
        for ex in exits:      stay_picco[ex]  = 0.950

        m_picco = cls.build_matrix(area_ids, stay_prob=0.985, flows=picco_flows, stay_by_area=stay_picco)

        # ==================================================================
        # FASE 3: PAUSA SPETTACOLO / FOOD & STAND (400s - 600s)
        # ==================================================================
        pausa_flows = {}
        connect_groups(peaks, hubs, 4.0, pausa_flows)
        connect_groups(hubs, sustained, 5.0, pausa_flows)

        stay_pausa = {}
        for out in outsides:  stay_pausa[out] = 0.995
        for e in entrances:   stay_pausa[e]   = 0.900
        for tr in transits:   stay_pausa[tr]  = 0.920
        for p in peaks:       stay_pausa[p]   = 0.980  # Lo Stage si svuota
        for s in sustained:   stay_pausa[s]   = 0.994  # Food e Stand crescono fino a ~1000-1200 pers!
        for ex in exits:      stay_pausa[ex]  = 0.950

        m_pausa = cls.build_matrix(area_ids, stay_prob=0.985, flows=pausa_flows, stay_by_area=stay_pausa)

        # ==================================================================
        # FASE 4: DEFLUSSO E CODA ALLE USCITE (600s - 750s)
        # ==================================================================
        deflusso_flows = {}
        connect_groups(peaks + sustained + generics, hubs, 3.0, deflusso_flows)
        connect_groups(hubs, exits, 4.0, deflusso_flows)
        connect_groups(exits, outsides, 5.0, deflusso_flows) # Scarico continuo verso Outside

        stay_deflusso = {}
        for out in outsides:  stay_deflusso[out] = 1.000
        for e in entrances:   stay_deflusso[e]   = 0.900
        for tr in transits:   stay_deflusso[tr]  = 0.900
        for p in peaks:       stay_deflusso[p]   = 0.940
        for s in sustained:   stay_deflusso[s]   = 0.940
        for ex in exits:      stay_deflusso[ex]  = 0.930  # Flusso di ~14s attraverso le uscite

        m_deflusso = cls.build_matrix(area_ids, stay_prob=0.960, flows=deflusso_flows, stay_by_area=stay_deflusso)

        # ==================================================================
        # FASE 5: SGOMBERO FINALE (750s - 900s)
        # ==================================================================
        uscita_flows = {}
        connect_groups(exits, outsides, 6.0, uscita_flows)
        connect_groups(hubs + entrances, exits, 4.0, uscita_flows)
        connect_groups(peaks + sustained, hubs, 3.0, uscita_flows)

        stay_uscita = {}
        for out in outsides:  stay_uscita[out] = 1.000
        for e in entrances:   stay_uscita[e]   = 0.850
        for tr in transits:   stay_uscita[tr]  = 0.850
        for ex in exits:      stay_uscita[ex]  = 0.850
        for p in peaks:       stay_uscita[p]   = 0.850
        for s in sustained:   stay_uscita[s]   = 0.850

        m_uscita = cls.build_matrix(area_ids, stay_prob=0.900, flows=uscita_flows, stay_by_area=stay_uscita)

        # ==================================================================
        # CRONOPROGRAMMA DELLE FASI (900 Secondi = 15 Minuti)
        # ==================================================================
        phases = [
            Phase("arrivo",           0.0,   180.0, m_arrivo),
            Phase("concerto_main",  180.0,   400.0, m_picco),
            Phase("pausa_food",     400.0,   600.0, m_pausa),
            Phase("deflusso",       600.0,   750.0, m_deflusso),
            Phase("uscita",         750.0,   900.0, m_uscita),
        ]
        return cls(area_ids, phases)