import json
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools" / "data" / "build_civix_dataset.py"


class _CivixFixtureHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        payload = self.payload_for(parsed.path, query)
        if payload is None:
            self.send_response(404)
            self.end_headers()
            return
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        return

    def envelope(self, data, path, pagination=None):
        return {
            "publisher": "CIVIX.fr",
            "api_version": "v1-test",
            "retrieved_at": "2026-04-01T00:00:00Z",
            "schema_version": "2026-04-29",
            "meta": {"retrieved_at": "2026-04-01T00:00:00Z", "last_updated": "2026-04-01T00:00:00Z", "pagination": pagination},
            "source": {"provider": "Assemblée nationale", "via": "CIVIX.fr", "source_urls": ["https://www.assemblee-nationale.fr"], "civix_urls": {"api": path}},
            "data": data,
        }

    def payload_for(self, path, query):
        if path == "/.well-known/civix.json":
            return {"name": "CIVIX", "schema_version": "2026-04-29"}
        if path == "/api/v1/index":
            return self.envelope({"type": "index", "attributes": {"endpoints": []}}, path)
        if path == "/api/v1/deputes":
            return self.envelope({"type": "deputies", "attributes": {"results": [{"uid": "PA1", "prenom": "Ada", "nom": "Martin", "legislature": 17, "circ_num": 1, "circ_departement": "Paris", "groupe_uid": "PO1", "groupe_abrev": "GRC", "groupe_libelle": "Groupe complet", "urls": {"civix_api": "/api/v1/deputes?uid=PA1"}}]}}, path, {"page": 1, "page_size": 100, "next_page": None})
        if path == "/api/v1/groupes":
            return self.envelope({"type": "groupes", "attributes": {"groups": [{"uid": "PO1", "abbr": "GRC", "libelle": "Groupe complet", "legislature": 17, "effectif": 1, "urls": {"api": "/api/v1/groupes/GRC"}}]}}, path)
        if path == "/api/v1/scrutins":
            return self.envelope({"type": "scrutins", "attributes": {"results": [{"uid": "VT1", "legislature": 17, "numero": 12, "date_scrutin": "2026-04-01T00:00:00Z", "titre": "scrutin public sur un texte", "objet": {"libelle": "Objet", "dossierLegislatif": {"libelle": "Dossier test", "dossierRef": "DL1"}, "referenceLegislative": "ART. 1"}, "objet_libelle": "Objet", "sort": {"code": "adopté", "libelle": "L'Assemblée nationale a adopté"}, "type_vote": {"libelleTypeVote": "scrutin public ordinaire", "typeMajorite": "Majorité absolue"}, "tags": []}]}}, path, {"page": 1, "page_size": 100, "next_page": None})
        if path == "/api/v1/scrutins/VT1/votes":
            return self.envelope({"type": "votes", "attributes": {"votes": [{"acteur_uid": "PA1", "groupe_abrev": "GRC", "position": "pour"}]}}, path)
        return None


class BuildCivixDatasetTest(unittest.TestCase):
    def test_builder_emits_normalized_files_and_report(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), _CivixFixtureHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as tmp:
                out = Path(tmp) / "civix"
                result = subprocess.run([
                    sys.executable,
                    str(SCRIPT),
                    "--base-url",
                    f"http://127.0.0.1:{server.server_port}",
                    "--out",
                    str(out),
                    "--max-scrutin-votes",
                    "1",
                    "--retry-sleep",
                    "0",
                ], cwd=ROOT, text=True, capture_output=True, check=True)
                self.assertIn("CIVIX ingestion complete", result.stdout)
                for name in ["deputies", "groups", "scrutins", "votes", "dossiers", "stats", "index", "ingestion-report"]:
                    self.assertTrue((out / f"{name}.json").exists(), name)
                deputies = json.loads((out / "deputies.json").read_text())
                self.assertEqual("civix:deputy:PA1", deputies["deputies"][0]["id"])
                self.assertEqual("2026-04-01T00:00:00Z", deputies["civixFetchedAt"])
                votes = json.loads((out / "votes.json").read_text())
                self.assertEqual("VT1", votes["votes"][0]["scrutinId"])
                report = json.loads((out / "ingestion-report.json").read_text())
                statuses = {item["name"]: item["status"] for item in report["endpoints"]}
                self.assertEqual("success", statuses["deputies"])
                self.assertEqual("success", statuses["individual_votes"])
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
