import logging
import os
import signal

from .config import SimConfig
from .control import ControlServer
from .engine import SimulationEngine

logger = logging.getLogger("simulator")

def _env_bool(name: str, default: bool):
    val = os.getenv(name)
    if val is None:
        return default
    val = val.strip().lower() in {"1", "true", "yes", "on"}

def main():
    """
    Carica la config dell'ambiente, avvia control server e fa girare loop
    """
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

    config = SimConfig.from_env()
    engine = SimulationEngine(config)

    control: ControlServer | None = None
    if _env_bool("SIM_CONTROL_ENABLED", True):
        host = os.getenv("SIM_CONTROL_HOST", "0.0.0.0")
        port = int(os.getenv("SIM_CONTROL_PORT", "8081"))
        control = ControlServer(engine, host=host, port=port)

    def _handle_signal(signum, frame):
        logger.info("segnale %s ricevuto: arresto in corso...", signum)
        engine.stop()

    signal.signal(signal.SIGINT, _handle_signal)
    signal.signal(signal.SIGTERM, _handle_signal)

    logger.info(
        "avvio: %d partecipanti, %d aree, durata %ds, seed %d, ~%.0f ev/s attesi",
        config.n_people, len(config.areas), config.duration_seconds,
        config.seed, config.expected_total_probe_rate(),
    )

    try:
        if control is not None:
            control.start()
            logger.info("control server su %s:%d", control.host, control.port)
        engine.run()   # blocca fino a fine scenario o segnale
    finally:
        if control is not None:
            control.stop()
        _log_summary(engine)

def _log_summary(engine: SimulationEngine) -> None:
    """Riepilogo finale: contatori del gateway e file prodotto."""
    stats = engine.gateway.stats
    logger.info(
        "fine. probe inviati=%d, falliti=%d, scartati=%d | ground truth: %s",
        stats["sent"], stats["failed"], stats["dropped"],
        engine.config.ground_truth_path,
    )

        
if __name__ == "__main__":
    main()