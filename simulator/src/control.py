"""
Endpoint di controllo per gli interventi manuali dalla dashboard.
"""

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .engine import SimulationEngine
 
 
class ControlServer:
    #Piccolo server HTTP che inoltra i comandi manuali all'engine.
 
    def __init__(self, engine: SimulationEngine, host: str, port: int):
        self.engine = engine
        self.host = host
        self.port = port
        self._server: ThreadingHTTPServer | None = None
        self._thread: threading.Thread | None = None
 
    def start(self):
        """
        Avvia il server (in un thread demone separato dal loop di simulazione).
        """
        handler = self._make_handler()
        self._httpd = ThreadingHTTPServer((self.host, self.port), handler)
        self._thread = threading.Thread(target=self._httpd.serve_forever, daemon=True)
        self._thread.start()
        
 
    def stop(self):
        if self._httpd is not None:
            self._httpd.shutdown()
            self._httpd.server_close()
        if self._thread is not None:
            self._thread.join(timeout=2.0)
 
    # --- Handler (montati sugli endpoint) ---
 
    def handle_boost(self, area_id: str, factor: float, duration_s: float):
        """POST /control/boost -> engine.apply_boost(...)."""
        self.engine.apply_boost(area_id, factor, duration_s)
 
    def handle_kill(self, sensor_id: str):
        """POST /control/kill -> engine.apply_kill(...)."""
        self.engine.apply_kill(sensor_id)

    def handle_restore(self, sensor_id: str):
        """POST /control/restore -> engine.restore_sensor(...)."""
        self.engine.restore_sensor(sensor_id )

    # Helper

    def _make_handler(self):
        """
        Costruisce la classe handler con l'accesso a questo control server via closure
        """
        server = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):        # silenzia il log su stderr
                pass

            def _read_json(self) -> dict:
                length = int(self.headers.get("Content-Length", 0))
                if length == 0:
                    return {}
                raw = self.rfile.read(length)
                return json.loads(raw or b"{}")

            def _reply(self, code: int, body: dict) -> None:
                data = json.dumps(body).encode()
                self.send_response(code)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_POST(self):
                try:
                    payload = self._read_json()
                    if self.path == "/control/boost":
                        server.handle_boost(
                            payload["area_id"],
                            float(payload.get("factor", 3.0)),
                            float(payload.get("duration_s", 120.0)),
                        )
                    elif self.path == "/control/kill":
                        server.handle_kill(payload["sensor_id"])
                    elif self.path == "/control/restore":
                        server.handle_restore(payload["sensor_id"])
                    else:
                        return self._reply(404, {"error": "unknown endpoint"})
                    self._reply(200, {"ok": True})
                except (KeyError, ValueError) as exc:
                    self._reply(400, {"error": str(exc)})

        return Handler