"""
Entry point del servizio di analisi. Per ora: legge la config, si connette
al broker e consuma i batch. Finestra e analisi si innestano nel loop.
"""

import logging
import signal
import time

from .config import AnalysisConfig
from .subscriber import ProbeSubscriber

logger = logging.getLogger("analysis")

# Ogni quanto stampare un riepilogo delle statistiche di trasporto.
STATS_INTERVAL_S = 30.0


def main():
    cfg = AnalysisConfig.from_env()   # solleva ValueError se la config non torna

    logging.basicConfig(
        level=getattr(logging, cfg.log_level, logging.INFO),
        format="%(asctime)s %(levelname)-7s %(name)s | %(message)s",
    )

    logger.info(
        "avvio analisi area=%s | broker %s:%d | topic=%s",
        cfg.area_id, cfg.mqtt_host, cfg.mqtt_port, cfg.topic_probes,
    )

    sub = ProbeSubscriber(cfg)

    def _handle_signal(signum, frame):
        logger.info("segnale %s ricevuto: arresto in corso...", signum)
        sub.stop()          # sblocca il generatore batches()

    signal.signal(signal.SIGINT, _handle_signal)
    signal.signal(signal.SIGTERM, _handle_signal)

    try:
        sub.start()
        if not sub.wait_connected(timeout=15):
            logger.warning("broker non raggiungibile: continuo a ritentare in background")

        next_stats = time.monotonic() + STATS_INTERVAL_S

        for batch in sub.batches(poll_timeout=1.0):
            logger.info(
                "batch=%s probes=%d (dichiarati %d, malformati %d) latenza=%.1f ms",
                batch.batch_id, batch.count_effective, batch.count_declared,
                batch.malformed_probes, batch.transport_latency_ms,
            )
            sub.ack(batch)

            now = time.monotonic()
            if now >= next_stats:
                logger.info("trasporto: %s", sub.stats)
                next_stats = now + STATS_INTERVAL_S
    finally:
        sub.stop()
        logger.info("riepilogo finale: %s", sub.stats)


if __name__ == "__main__":
    main()