import json
import tempfile
import unittest
from datetime import date
from pathlib import Path

from tools.data import build_parliament_calendar_dataset as builder


class ParliamentCalendarDatasetTest(unittest.TestCase):
    def test_parse_assemblee_agenda_keeps_only_explicit_future_dates(self):
        payloads = [
            {
                "reunion": {
                    "uid": "RU1",
                    "@xsi:type": "seance_type",
                    "timeStampDebut": "2026-06-02T15:00:00.000+02:00",
                    "lieu": {"libelleLong": "Assemblée nationale"},
                    "ODJ": {"pointsODJ": {"pointODJ": {
                        "uid": "P1",
                        "objet": "Discussion de la proposition de loi test",
                        "typePointODJ": "Discussion",
                        "dossiersLegislatifsRefs": {"dossierRef": "DLR1"},
                        "textesAssocies": {"texteAssocie": "B123"},
                    }}},
                }
            },
            {
                "reunion": {
                    "uid": "PAST",
                    "@xsi:type": "seance_type",
                    "timeStampDebut": "2026-05-01T15:00:00.000+02:00",
                    "ODJ": {"pointsODJ": {"pointODJ": {"uid": "P2", "objet": "Ancien texte"}}},
                }
            },
            {
                "reunion": {
                    "uid": "UNKNOWN",
                    "@xsi:type": "seance_type",
                    "ODJ": {"pointsODJ": {"pointODJ": {"uid": "P3", "objet": "Prochainement"}}},
                }
            },
        ]

        items, skipped = builder.normalize_assemblee_agenda(payloads, today=date(2026, 5, 31), retrieved_at="2026-05-31T00:00:00Z")

        self.assertEqual(1, len(items))
        item = items[0]
        self.assertEqual("assemblee", item["source"])
        self.assertEqual("2026-06-02", item["eventDate"])
        self.assertEqual("2026-06-02T15:00:00+02:00", item["eventDateTime"])
        self.assertEqual("Discussion de la proposition de loi test", item["title"])
        self.assertEqual("DLR1", item["dossierRef"])
        self.assertEqual("debate", item["eventType"])
        self.assertEqual("explicit_date", item["confidence"])
        self.assertEqual("https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR1", item["officialUrl"])
        self.assertIn("https://data.assemblee-nationale.fr/static/openData/repository/17/vp/reunions/Agenda.json.zip", item["sourceUrls"])
        self.assertEqual(2, skipped)

    def test_deduplication_prefers_item_with_dossier_ref_and_stable_ids_are_deterministic(self):
        base = {
            "source": "assemblee",
            "sourceUid": "RU1:P1",
            "title": "Discussion du texte",
            "shortTitle": "Discussion du texte",
            "eventDate": "2026-06-02",
            "eventDateTime": "2026-06-02T15:00:00+02:00",
            "dateLabel": "2 juin 2026 à 15:00",
            "eventType": "debate",
            "stage": "Discussion",
            "chamber": "Assemblée nationale",
            "dossierRef": "DLR1",
            "legislature": "17",
            "officialUrl": "https://www.assemblee-nationale.fr/dyn/17/agendas/RU1",
            "dossierUrl": "https://www.assemblee-nationale.fr/dyn/17/dossiers/DLR1",
            "sourceUrls": ["https://www.assemblee-nationale.fr/dyn/17/agendas/RU1"],
            "retrievedAt": "2026-05-31T00:00:00Z",
            "confidence": "explicit_date",
            "raw": {"uid": "RU1"},
        }
        duplicate = dict(base, id="wrong", sourceUid="RU2:P2", officialUrl="https://www.assemblee-nationale.fr/dyn/17/agendas/RU2")

        first_id = builder.stable_item_id(base)
        second_id = builder.stable_item_id(base)
        deduped, skipped = builder.deduplicate_items([dict(base, id=first_id), duplicate])

        self.assertEqual(first_id, second_id)
        self.assertEqual(1, len(deduped))
        self.assertEqual(1, skipped)
        self.assertEqual(first_id, deduped[0]["id"])

    def test_validate_schema_rejects_missing_source_urls_and_unknown_dates(self):
        item = {
            "id": "calendar:x",
            "source": "assemblee",
            "sourceUid": "x",
            "title": "Texte",
            "shortTitle": "Texte",
            "eventDate": "2026-06-02",
            "eventDateTime": None,
            "dateLabel": "2 juin 2026",
            "eventType": "other",
            "stage": "Étape",
            "chamber": "Assemblée nationale",
            "dossierRef": None,
            "legislature": "17",
            "officialUrl": "",
            "dossierUrl": None,
            "sourceUrls": [],
            "retrievedAt": "2026-05-31T00:00:00Z",
            "confidence": "unknown_date",
            "raw": {},
        }

        errors = builder.validate_items([item])

        self.assertTrue(any("sourceUrls" in error for error in errors))
        self.assertTrue(any("confidence" in error for error in errors))

    def test_write_outputs_calendar_index_and_ingestion_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            report = builder.IngestionReport(generated_at="2026-05-31T00:00:00Z")
            report.add_endpoint("assemblee_agenda", "https://source.test", "success", fetched=1, skipped=0)
            item = {
                "id": "calendar:one",
                "source": "assemblee",
                "sourceUid": "RU1:P1",
                "title": "Texte",
                "shortTitle": "Texte",
                "eventDate": "2026-06-02",
                "eventDateTime": None,
                "dateLabel": "2 juin 2026",
                "eventType": "other",
                "stage": "Étape",
                "chamber": "Assemblée nationale",
                "dossierRef": None,
                "legislature": "17",
                "officialUrl": "https://source.test/event",
                "dossierUrl": None,
                "sourceUrls": ["https://source.test/event"],
                "retrievedAt": "2026-05-31T00:00:00Z",
                "confidence": "explicit_date",
                "raw": {"uid": "RU1"},
            }

            builder.write_outputs(out, [item], report)

            calendar = json.loads((out / "calendar.json").read_text())
            index = json.loads((out / "index.json").read_text())
            ingestion = json.loads((out / "ingestion-report.json").read_text())
            self.assertEqual(1, len(calendar["items"]))
            self.assertEqual("2026-06-02", index["nextEventDate"])
            self.assertEqual(1, index["itemCount"])
            self.assertEqual("success", ingestion["endpoints"][0]["status"])

    def test_safe_failure_marks_critical_endpoint_and_returns_nonzero(self):
        with tempfile.TemporaryDirectory() as tmp:
            code = builder.build_dataset(
                out_dir=Path(tmp),
                fetchers={"assemblee_agenda": lambda: (_ for _ in ()).throw(RuntimeError("offline"))},
                today=date(2026, 5, 31),
                generated_at="2026-05-31T00:00:00Z",
            )

            report = json.loads((Path(tmp) / "ingestion-report.json").read_text())
            self.assertNotEqual(0, code)
            self.assertEqual("failed", report["endpoints"][0]["status"])
            self.assertGreaterEqual(report["errorsCount"], 1)


if __name__ == "__main__":
    unittest.main()
