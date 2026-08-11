"""Visualizza la dinamica della popolazione senza gateway ne edge service:
guida population + scenario e disegna come la folla si muove tra le aree.

    python -m simulator.visualize            # salva crowd_lines.png
    python -m simulator.visualize --gif      # salva anche crowd_bars.gif
    python -m simulator.visualize --live     # finestra animata dal vivo

Richiede matplotlib (e pillow per il gif): strumento di sviluppo, NON va
nelle dipendenze del container.
"""
from __future__ import annotations

import argparse
import random

import numpy as np
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, PillowWriter

from .config import SimConfig
from .models import Area, Device
from .population import PopulationModel
from .scenario import Scenario


def simulate(config: SimConfig) -> tuple[list[str], np.ndarray]:
    """Esegue solo la dinamica delle popolazioni e ritorna (aree, storico).
    storico ha forma (durata+1, n_aree)."""
    rng = random.Random(config.seed)
    area_ids = config.area_ids()
    areas = [Area(config=ac) for ac in config.areas]
    # I device servono solo a muoversi: mac/rssi irrilevanti qui.
    devices = [
        Device(mac="", area_id=area_ids[0], next_probe_at=0.0,
               rssi_base=0.0, is_fringe=False)
        for _ in range(config.n_people)
    ]
    pop = PopulationModel(areas, devices, rng)
    pop.devices = devices          # come fa engine.setup
    pop.seed_devices()             # tutti in area 0 = outside
    scenario = Scenario.from_dynamic_areas(config.areas)

    steps = int(config.duration_seconds / config.tick_seconds)
    history = np.zeros((steps + 1, len(area_ids)))
    history[0] = pop.population_vector()
    for k in range(1, steps + 1):
        t = k * config.tick_seconds
        base = scenario.matrix_at(t)
        p = pop.apply_saturation(base)
        pop.step(p)
        history[k] = pop.population_vector()

    assert abs(history.sum(axis=1) - config.n_people).max() < 1e-6, \
        "massa non conservata"
    return area_ids, history


def plot_lines(area_ids, history, path="crowd_lines.png") -> None:
    ts = np.arange(history.shape[0])
    fig, ax = plt.subplots(figsize=(11, 5.5))
    for i, a in enumerate(area_ids):
        ax.plot(ts, history[:, i], label=a, lw=2)
    ax.set_xlabel("tempo (s)"); ax.set_ylabel("persone per area")
    ax.set_title("Movimento della folla tra le aree")
    ax.legend(loc="upper right", ncol=2, fontsize=9); ax.grid(alpha=0.3)
    fig.tight_layout(); fig.savefig(path, dpi=110)
    print("scritto", path)


def animate(area_ids, history, live: bool, gif_path="crowd_bars.gif") -> None:
    # Barre solo per le aree monitorate (outside e' il serbatoio, fuori scala).
    mon = [i for i, a in enumerate(area_ids) if a != "outside"]
    labels = [area_ids[i] for i in mon]
    fig, ax = plt.subplots(figsize=(9, 5))
    bars = ax.bar(labels, history[0, mon])
    ax.set_ylim(0, history[:, mon].max() * 1.1)
    ax.set_ylabel("persone")
    title = ax.set_title("t=0s")

    frames = range(0, history.shape[0], 3)

    def update(t):
        for b, i in zip(bars, mon):
            b.set_height(history[t, i])
        out = int(history[t, area_ids.index("outside")]) if "outside" in area_ids else 0
        title.set_text(f"t={t}s   (fuori: {out})")
        return list(bars) + [title]

    anim = FuncAnimation(fig, update, frames=frames, blit=False, interval=50)
    if live:
        plt.show()
    else:
        anim.save(gif_path, writer=PillowWriter(fps=20))
        print("scritto", gif_path)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--gif", action="store_true", help="salva anche il gif animato")
    ap.add_argument("--live", action="store_true", help="finestra animata dal vivo")
    args = ap.parse_args()

    config = SimConfig().from_env()
    area_ids, history = simulate(config)
    plot_lines(area_ids, history)
    if args.live:
        animate(area_ids, history, live=True)
    elif args.gif:
        animate(area_ids, history, live=False)


if __name__ == "__main__":
    main()