# Disabilito exit-on-error altrimenti eventuali eccezioni di Python terminano bruscamente lo script senza mostrare log
set +e
source .venv/bin/activate
SIM_BROKER_URLS="tcp://127.0.0.1:1883,tcp://127.0.0.1:1884,tcp://127.0.0.1:1885,tcp://127.0.0.1:1886" python -m simulator.src.main
