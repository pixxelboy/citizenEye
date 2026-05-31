import gzip
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools" / "data" / "build_citizeneye_dataset.py"


class BuildCitizenEyeDatasetTest(unittest.TestCase):
    def test_builder_emits_manifest_gzipped_files_and_valid_hashes(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            active = tmp_path / "active.zip"
            scrutins = tmp_path / "scrutins.zip"
            dossiers = tmp_path / "dossiers.zip"
            out = tmp_path / "public"
            self._write_zip(active, {
                "json/organe/PO1.json": {"organe": {"uid": "PO1", "codeType": "GP", "libelle": "Groupe complet", "libelleAbrev": "GRC"}},
                "json/acteur/PA1.json": {"acteur": {"uid": "PA1", "etatCivil": {"ident": {"prenom": "Ada", "nom": "Martin"}}, "profession": {"libelleCourant": "Avocate"}, "adresses": {"adresse": {"valElec": "ada@assemblee.fr"}}, "mandats": {"mandat": [{"typeOrgane": "ASSEMBLEE", "election": {"lieu": {"departement": "Paris", "numDepartement": "75", "numCirco": "1"}}}, {"typeOrgane": "GP", "organes": {"organeRef": "PO1"}}]}}},
            })
            self._write_zip(scrutins, {
                "json/scrutin/VT1.json": {"scrutin": {"uid": "VT1", "numero": "12", "dateScrutin": "2026-01-02", "titre": "scrutin public sur l’amendement n° 1", "sort": {"libelle": "adopté"}, "syntheseVote": {"nombreVotants": "1", "nombreNonVotants": "0", "majoriteAbsolue": "1", "decompte": {"pour": "1", "contre": "0", "abstentions": "0"}}, "objet": {"libelle": "Objet", "referenceLegislative": "ART. 1", "dossierLegislatif": {"dossierRef": "DL1", "libelle": "Dossier"}}, "ventilationVotes": {"organe": {"groupes": {"groupe": {"libelle": "GRC", "vote": {"decompteVoix": {"pour": "1", "contre": "0", "abstentions": "0", "nonVotants": "0"}, "decompteNominatif": {"pours": {"votant": {"acteurRef": "PA1"}}}}}}}}}},
            })
            self._write_zip(dossiers, {
                "json/dossierParlementaire/DL1.json": {"dossierParlementaire": {"uid": "DL1", "legislature": "17", "titreDossier": {"titre": "Dossier"}, "procedureParlementaire": {"libelle": "Projet de loi"}, "actesLegislatifs": {"acteLegislatif": {"codeActe": "DEPOT", "texteAssocie": "B123", "libelleActe": {"libelleCourt": "Dépôt"}}}}},
            })

            result = subprocess.run([
                sys.executable, str(SCRIPT), "--out", str(out),
                "--active-deputies-url", active.as_uri(),
                "--scrutins-url", scrutins.as_uri(),
                "--dossiers-url", dossiers.as_uri(),
            ], cwd=ROOT, text=True, capture_output=True, check=True)

            self.assertIn("deputies=1", result.stdout)
            manifest = json.loads((out / "manifest.json").read_text())
            self.assertEqual("citizeneye-assemblee-v17", manifest["dataset"])
            self.assertEqual(3, len(manifest["files"]))
            for entry in manifest["files"]:
                path = out / entry["url"]
                self.assertTrue(path.exists())
                self.assertEqual(entry["bytes"], path.stat().st_size)
                with gzip.open(path, "rt", encoding="utf-8") as handle:
                    payload = json.load(handle)
                self.assertEqual(1, payload["schemaVersion"])
                if entry["name"] == "deputies":
                    self.assertEqual(
                        "https://www.assemblee-nationale.fr/dyn/static/tribun/17/photos/carre/1.jpg",
                        payload["deputies"][0]["photoUrl"],
                    )
                if entry["name"] == "dossiers":
                    self.assertEqual(
                        "https://www.assemblee-nationale.fr/dyn/17/dossiers/DL1",
                        payload["dossiersByRef"]["DL1"]["dossierUrl"],
                    )

    def _write_zip(self, path: Path, entries: dict):
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            for name, payload in entries.items():
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                zf.writestr(info, json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")))


if __name__ == "__main__":
    unittest.main()
