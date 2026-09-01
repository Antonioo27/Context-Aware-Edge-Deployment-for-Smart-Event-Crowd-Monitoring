import logging
import os
import signal

from .config import SimConfig
from .engine import SimulationEngine

logger = logging.getLogger("simulator")

def _env_bool(name: str, default: bool):
    val = os.getenv(name)
    if val is None:
        return default
    return val.strip().lower() in {"1", "true", "yes", "on"}

def main():
    """
    Carica la config dell'ambiente, fa girare loop
    """
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

    config = SimConfig.from_env()
    engine = SimulationEngine(config)

    if _env_bool("SIM_CONTROL_ENABLED", True):
        host = os.getenv("SIM_CONTROL_HOST", "0.0.0.0")
        port = int(os.getenv("SIM_CONTROL_PORT", "8081"))

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
    logger.info(
        "MQTT Broadcast verso %d broker: %s | client_id=%s, qos=%d, clean_session=%s, prefisso topic=%s",
        len(config.broker_urls), config.broker_urls, config.mqtt_client_id,
        config.mqtt_qos, config.mqtt_clean_session, config.mqtt_topic_prefix,
    )
    try:
        engine.run()   # blocca fino a fine scenario o segnale
    finally:
        _log_summary(engine)

def _log_summary(engine: SimulationEngine) -> None:
    """Riepilogo finale: contatori del publisher MQTT e file prodotto."""
    s = engine.publisher.snapshot_stats()
    logger.info(
        "fine. probe: pubblicati=%d, rimessi in coda=%d, scartati=%d, area ignota=%d",
        s["probes_published"], s["probes_requeued"],
        s["probes_dropped"], s["probes_unknown_area"],
    )
    logger.info(
        "batch: pubblicati=%d, confermati dal broker=%d, in volo=%d | riconnessioni=%d",
        s["batches_published"], s["batches_acked"],
        s["batches_in_flight"], s["reconnects"],
    )
    if s["buffered"]:
        logger.warning("probe rimasti in buffer alla chiusura: %s", s["buffered"])
    logger.info("ground truth: %s", engine.config.ground_truth_path)

        
if __name__ == "__main__":
    main()