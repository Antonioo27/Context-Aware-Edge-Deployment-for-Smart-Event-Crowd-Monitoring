"""
Application entry point for the smart event crowd simulator.

Initializes logging, parses environment configuration, registers POSIX signal handlers
for graceful termination, and runs the main simulation engine loop until scenario completion.
"""

import logging
import os
import signal

from .config import SimConfig
from .engine import SimulationEngine


logger = logging.getLogger("simulator")


def _env_bool(name: str, default: bool) -> bool:
    """
    Parses a boolean from an environment variable matching common truthy values ('1', 'true', 'yes', 'on').

    @param name The environment variable name.
    @param default Fallback boolean value if the variable is not set.
    @return Parsed boolean flag.
    """
    val = os.getenv(name)
    if val is None:
        return default
    return val.strip().lower() in {"1", "true", "yes", "on"}


def main() -> None:
    """
    Loads configuration from environment variables, configures signal interception for graceful shutdown,
    logs simulation deployment metadata, and blocks while driving the SimulationEngine until completion.
    """
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

    config = SimConfig.from_env()
    engine = SimulationEngine(config)

    def _handle_signal(signum, frame) -> None:
        """
        Intercepts POSIX termination signals (SIGINT, SIGTERM) and initiates graceful engine shutdown.

        @param signum The signal number received.
        @param frame The current execution stack frame.
        """
        logger.info("Signal %s received: initiating graceful shutdown...", signum)
        engine.stop()

    signal.signal(signal.SIGINT, _handle_signal)
    signal.signal(signal.SIGTERM, _handle_signal)

    logger.info(
        "Starting simulation: %d attendees, %d areas, duration %ds, seed %d, ~%.0f expected events/sec",
        config.n_people,
        len(config.areas),
        config.duration_seconds,
        config.seed,
        config.expected_total_probe_rate(),
    )
    logger.info(
        "MQTT Broadcast to %d brokers: %s | client_id=%s, qos=%d, clean_session=%s, topic_prefix=%s",
        len(config.broker_urls),
        config.broker_urls,
        config.mqtt_client_id,
        config.mqtt_qos,
        config.mqtt_clean_session,
        config.mqtt_topic_prefix,
    )
    try:
        engine.run()
    finally:
        _log_summary(engine)


def _log_summary(engine: SimulationEngine) -> None:
    """
    Emits final aggregated telemetry metrics including published, requeued, and dropped probe counters,
    broker acknowledgements, in-flight message counts, and ground truth file locations.

    @param engine The SimulationEngine instance holding the publisher and ground truth recorder.
    """
    s = engine.publisher.snapshot_stats()
    logger.info(
        "Simulation finished. Probes: published=%d, requeued=%d, dropped=%d, unknown_area=%d",
        s["probes_published"],
        s["probes_requeued"],
        s["probes_dropped"],
        s["probes_unknown_area"],
    )
    logger.info(
        "Batches: published=%d, acknowledged=%d, in_flight=%d | reconnects=%d",
        s["batches_published"],
        s["batches_acked"],
        s["batches_in_flight"],
        s["reconnects"],
    )
    if s["buffered"]:
        logger.warning("Probes remaining in buffer at shutdown: %s", s["buffered"])
    logger.info("Ground truth CSV written to: %s", engine.config.ground_truth_path)


if __name__ == "__main__":
    main()