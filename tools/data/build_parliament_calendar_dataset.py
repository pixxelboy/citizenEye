#!/usr/bin/env python3
"""Build a compact static parliamentary calendar for CitizenEye.

Source priority:
1. Official Assemblée nationale open-data agenda ZIP (`vp/reunions/Agenda.json.zip`).
   This is the critical source because it exposes explicit dated public sessions
   and commission meetings with order-of-day items.
2. Sénat calendar/dossier sources may be added as optional official sources when
   a stable machine-readable endpoint is confirmed. CIVIX is intentionally not
   used as an authoritative source for upcoming dates.

The builder never invents dates: only explicit timestamps/dates present in the
source payload are emitted. Unknown/vague dates are skipped and counted.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import sys
import time
import urllib.error
import urllib.request
import zipfile
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable

ASSEMBLEE_AGENDA_URL = "https://data.assemblee-nationale.fr/static/openData/repository/17/vp/reunions/Agenda.json.zip"
USER_AGENT = "CitizenEye parliament calendar dataset builder (+https://github.com/pixxelboy/citizenEye)"
DATASET_VERSION = 1
MAX_EVENTS = 300
DEFAULT_WINDOW_DAYS = 90
CRITICAL_SOURCES = {"assemblee_agenda"}
EVENT_TYPES = {"public_session", "committee", "vote", "debate", "hearing", "other"}
SOURCES = {"assemblee", "senat"}
CONFIDENCES = {"explicit_date", "official_date_label", "unknown_date"}
MONTHS = {
    1: "janvier", 2: "février", 3: "mars", 4: "avril", 5: "mai", 6: "juin",
    7: "juillet", 8: "août", 9: "septembre", 10: "octobre", 11: "novembre", 12: "décembre",
}


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def canonical_json(data: Any) -> str:
    return json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def clean_text(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, dict):
        for key in ("item", "libelle", "titre", "nom"):
            nested = clean_text(value.get(key))
            if nested:
                return nested
        return None
    if isinstance(value, list):
        parts = [clean_text(item) for item in value]
        text = " ; ".join(part for part in parts if part)
        return text or None
    text = re.sub(r"\s+", " ", str(value)).strip()
    return text or None


def as_list(value: Any) -> list[Any]:
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def parse_datetime(value: Any) -> datetime | None:
    text = clean_text(value)
    if not text:
        return None
    text = text.replace("Z", "+00:00")
    if ".000" in text:
        text = text.replace(".000", "")
    try:
        return datetime.fromisoformat(text)
    except ValueError:
        return None


def normalize_iso_datetime(value: Any) -> str | None:
    parsed = parse_datetime(value)
    if not parsed:
        return None
    return parsed.isoformat()


def stable_item_id(item: dict[str, Any]) -> str:
    basis = {
        "source": item.get("source"),
        "sourceUid": item.get("sourceUid"),
        "eventDate": item.get("eventDate"),
        "eventType": item.get("eventType"),
        "title": normalize_key(item.get("title")),
    }
    digest = hashlib.sha256(canonical_json(basis).encode("utf-8")).hexdigest()[:20]
    return f"parliament:calendar:{digest}"


def normalize_key(value: Any) -> str:
    text = clean_text(value) or ""
    text = text.lower()
    text = re.sub(r"[^a-z0-9àâäéèêëîïôöùûüçœæ]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def dossier_url(ref: str | None) -> str | None:
    ref = clean_text(ref)
    return f"https://www.assemblee-nationale.fr/dyn/17/dossiers/{ref}" if ref else None


def short_title(title: str, limit: int = 120) -> str:
    title = clean_text(title) or "Texte parlementaire"
    return title if len(title) <= limit else title[: limit - 1].rstrip() + "…"


def french_date_label(day: date, dt: datetime | None = None) -> str:
    label = f"{day.day} {MONTHS[day.month]} {day.year}"
    if dt is not None and not (dt.hour == 0 and dt.minute == 0):
        label += f" à {dt.hour:02d}:{dt.minute:02d}"
    return label


def extract_dossier_ref(point: dict[str, Any]) -> str | None:
    refs = point.get("dossiersLegislatifsRefs") if isinstance(point.get("dossiersLegislatifsRefs"), dict) else {}
    raw = refs.get("dossierRef")
    values = as_list(raw)
    return clean_text(values[0]) if values else None


def event_type_for(reunion: dict[str, Any], point: dict[str, Any]) -> str:
    type_text = " ".join(filter(None, [clean_text(reunion.get("@xsi:type")), clean_text(point.get("typePointODJ")), clean_text(point.get("objet"))])).lower()
    if "commission" in type_text:
        return "committee"
    if "audition" in type_text:
        return "hearing"
    if "vote" in type_text or "scrutin" in type_text:
        return "vote"
    if "discussion" in type_text or "débat" in type_text or "debat" in type_text:
        return "debate"
    if "seance" in type_text or "séance" in type_text:
        return "public_session"
    return "other"


def source_uid_for(reunion: dict[str, Any], point: dict[str, Any] | None = None) -> str:
    uid = clean_text(reunion.get("uid")) or "unknown"
    point_uid = clean_text((point or {}).get("uid"))
    return f"{uid}:{point_uid}" if point_uid else uid


def minimal_raw(reunion: dict[str, Any], point: dict[str, Any] | None = None) -> dict[str, Any]:
    raw = {
        "reunionUid": clean_text(reunion.get("uid")),
        "reunionType": clean_text(reunion.get("@xsi:type")),
        "timeStampDebut": clean_text(reunion.get("timeStampDebut")),
        "timeStampFin": clean_text(reunion.get("timeStampFin")),
        "etat": clean_text(((reunion.get("cycleDeVie") or {}).get("etat") if isinstance(reunion.get("cycleDeVie"), dict) else None)),
        "lieu": clean_text(((reunion.get("lieu") or {}).get("libelleLong") if isinstance(reunion.get("lieu"), dict) else None)),
    }
    if point:
        raw.update({
            "pointUid": clean_text(point.get("uid")),
            "typePointODJ": clean_text(point.get("typePointODJ")),
            "natureTravauxODJ": clean_text(point.get("natureTravauxODJ")),
            "dossierRef": extract_dossier_ref(point),
        })
    return {k: v for k, v in raw.items() if v not in (None, "")}


def normalize_assemblee_agenda(payloads: list[dict[str, Any]], *, today: date, retrieved_at: str, window_days: int = DEFAULT_WINDOW_DAYS) -> tuple[list[dict[str, Any]], int]:
    items: list[dict[str, Any]] = []
    skipped = 0
    max_day = today + timedelta(days=window_days)
    for payload in payloads:
        reunion = payload.get("reunion") if isinstance(payload.get("reunion"), dict) else payload
        if not isinstance(reunion, dict):
            skipped += 1
            continue
        dt = parse_datetime(reunion.get("timeStampDebut"))
        if not dt:
            skipped += 1
            continue
        event_day = dt.date()
        if event_day < today or event_day > max_day:
            skipped += 1
            continue
        odj = reunion.get("ODJ") if isinstance(reunion.get("ODJ"), dict) else {}
        points_container = odj.get("pointsODJ") if isinstance(odj.get("pointsODJ"), dict) else {}
        points = [p for p in as_list(points_container.get("pointODJ")) if isinstance(p, dict)]
        if not points:
            points = [{}]
        for point in points:
            title = clean_text(point.get("objet")) or clean_text(odj.get("resumeODJ")) or clean_text(reunion.get("organeReuniRef")) or "Réunion parlementaire"
            ref = extract_dossier_ref(point)
            official = dossier_url(ref) or ASSEMBLEE_AGENDA_URL
            source_urls = [ASSEMBLEE_AGENDA_URL]
            if official not in source_urls:
                source_urls.append(official)
            item = {
                "id": "",
                "source": "assemblee",
                "sourceUid": source_uid_for(reunion, point),
                "title": title,
                "shortTitle": short_title(title),
                "eventDate": event_day.isoformat(),
                "eventDateTime": normalize_iso_datetime(reunion.get("timeStampDebut")),
                "dateLabel": french_date_label(event_day, dt),
                "eventType": event_type_for(reunion, point),
                "stage": clean_text(point.get("typePointODJ")) or clean_text(odj.get("resumeODJ")) or "Agenda parlementaire",
                "chamber": "Assemblée nationale",
                "dossierRef": ref,
                "legislature": "17",
                "officialUrl": official,
                "dossierUrl": dossier_url(ref),
                "sourceUrls": source_urls,
                "retrievedAt": retrieved_at,
                "confidence": "explicit_date",
                "raw": minimal_raw(reunion, point),
            }
            item["id"] = stable_item_id(item)
            items.append(item)
    return items, skipped


def deduplicate_items(items: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], int]:
    seen: dict[tuple[str, str, str, str], dict[str, Any]] = {}
    skipped = 0
    for item in sorted(items, key=lambda x: (x.get("eventDateTime") or x.get("eventDate") or "", x.get("source"), x.get("sourceUid"))):
        key = (
            clean_text(item.get("dossierRef")) or clean_text(item.get("sourceUid")) or "",
            clean_text(item.get("eventDate")) or "",
            clean_text(item.get("eventType")) or "other",
            normalize_key(item.get("title")),
        )
        if key in seen:
            skipped += 1
            existing = seen[key]
            existing_urls = list(dict.fromkeys((existing.get("sourceUrls") or []) + (item.get("sourceUrls") or [])))
            existing["sourceUrls"] = existing_urls
            continue
        item = dict(item)
        item["id"] = stable_item_id(item)
        seen[key] = item
    return list(seen.values()), skipped


def validate_iso_date(value: Any) -> bool:
    if not isinstance(value, str):
        return False
    try:
        date.fromisoformat(value)
        return True
    except ValueError:
        return False


def validate_iso_datetime_or_null(value: Any) -> bool:
    if value is None:
        return True
    if not isinstance(value, str):
        return False
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
        return True
    except ValueError:
        return False


def validate_items(items: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    required = ["id", "source", "sourceUid", "title", "shortTitle", "eventDate", "dateLabel", "eventType", "stage", "chamber", "officialUrl", "sourceUrls", "retrievedAt", "confidence", "raw"]
    for index, item in enumerate(items):
        for field_name in required:
            if field_name not in item:
                errors.append(f"items[{index}].{field_name} missing")
        if not isinstance(item.get("id"), str) or not item.get("id"):
            errors.append(f"items[{index}].id must be stable string")
        if item.get("source") not in SOURCES:
            errors.append(f"items[{index}].source invalid")
        if not validate_iso_date(item.get("eventDate")):
            errors.append(f"items[{index}].eventDate invalid")
        if not validate_iso_datetime_or_null(item.get("eventDateTime")):
            errors.append(f"items[{index}].eventDateTime invalid")
        if item.get("eventType") not in EVENT_TYPES:
            errors.append(f"items[{index}].eventType invalid")
        if item.get("confidence") == "unknown_date" or item.get("confidence") not in CONFIDENCES:
            errors.append(f"items[{index}].confidence invalid for emitted calendar item")
        source_urls = item.get("sourceUrls")
        if not isinstance(source_urls, list) or not source_urls or not all(isinstance(x, str) and x.startswith("https://") for x in source_urls):
            errors.append(f"items[{index}].sourceUrls required")
        official = item.get("officialUrl")
        if not isinstance(official, str) or not official.startswith("https://"):
            errors.append(f"items[{index}].officialUrl required")
        if not isinstance(item.get("raw"), dict):
            errors.append(f"items[{index}].raw must be object")
    return errors


@dataclass
class EndpointResult:
    name: str
    url: str
    status: str
    fetchedCount: int = 0
    skippedCount: int = 0
    errorsCount: int = 0
    warningsCount: int = 0
    retrievedAt: str | None = None
    error: str | None = None
    notes: list[str] = field(default_factory=list)

    def as_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "url": self.url,
            "status": self.status,
            "fetchedCount": self.fetchedCount,
            "skippedCount": self.skippedCount,
            "errorsCount": self.errorsCount,
            "warningsCount": self.warningsCount,
            "retrievedAt": self.retrievedAt,
            "error": self.error,
            "notes": self.notes,
        }


@dataclass
class IngestionReport:
    generated_at: str
    endpoints: list[EndpointResult] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)

    def add_endpoint(self, name: str, url: str, status: str, *, fetched: int = 0, skipped: int = 0, error: Exception | str | None = None, notes: list[str] | None = None) -> None:
        self.endpoints.append(EndpointResult(
            name=name,
            url=url,
            status=status,
            fetchedCount=fetched,
            skippedCount=skipped,
            errorsCount=1 if status == "failed" else 0,
            retrievedAt=self.generated_at,
            error=str(error) if error else None,
            notes=notes or [],
        ))
        if status == "failed":
            self.errors.append(f"{name}: {error}")
        elif status == "warning" and error:
            self.warnings.append(f"{name}: {error}")

    @property
    def warnings_count(self) -> int:
        return len(self.warnings) + sum(e.warningsCount for e in self.endpoints)

    @property
    def errors_count(self) -> int:
        return len(self.errors) + sum(e.errorsCount for e in self.endpoints)

    def as_dict(self) -> dict[str, Any]:
        return {
            "dataset": "citizeneye-parliament-calendar",
            "schemaVersion": DATASET_VERSION,
            "generatedAt": self.generated_at,
            "status": "failed" if self.errors_count else ("warning" if self.warnings_count else "success"),
            "endpoints": [e.as_dict() for e in self.endpoints],
            "warnings": self.warnings,
            "errors": self.errors,
            "warningsCount": self.warnings_count,
            "errorsCount": self.errors_count,
        }


def download(url: str, *, timeout: int = 45, retries: int = 3) -> bytes:
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/zip,application/json,*/*"})
            with urllib.request.urlopen(req, timeout=timeout) as response:
                return response.read()
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = error
            if attempt < retries - 1:
                time.sleep(0.5 * (attempt + 1))
    raise RuntimeError(f"download failed for {url}: {last_error}")


def fetch_assemblee_agenda() -> list[dict[str, Any]]:
    data = download(ASSEMBLEE_AGENDA_URL)
    payloads: list[dict[str, Any]] = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in sorted(archive.namelist()):
            if name.endswith(".json"):
                payloads.append(json.loads(archive.read(name)))
    return payloads


def write_json_if_changed(path: Path, payload: dict[str, Any]) -> None:
    text = json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_outputs(out_dir: Path, items: list[dict[str, Any]], report: IngestionReport) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    generated_at = report.generated_at
    sorted_items = sorted(items, key=lambda x: (x.get("eventDateTime") or x.get("eventDate") or "", x.get("title") or ""))[:MAX_EVENTS]
    calendar = {
        "dataset": "citizeneye-parliament-calendar",
        "schemaVersion": DATASET_VERSION,
        "generatedAt": generated_at,
        "windowDays": DEFAULT_WINDOW_DAYS,
        "itemCount": len(sorted_items),
        "items": sorted_items,
    }
    next_event_date = sorted_items[0]["eventDate"] if sorted_items else None
    index = {
        "dataset": "citizeneye-parliament-calendar",
        "schemaVersion": DATASET_VERSION,
        "generatedAt": generated_at,
        "itemCount": len(sorted_items),
        "nextEventDate": next_event_date,
        "sources": [
            {"name": "Assemblée nationale agenda open data", "source": "assemblee", "url": ASSEMBLEE_AGENDA_URL, "critical": True},
        ],
        "files": [
            {"name": "calendar", "url": "calendar.json"},
            {"name": "ingestion-report", "url": "ingestion-report.json"},
        ],
        "warningsCount": report.warnings_count,
    }
    write_json_if_changed(out_dir / "calendar.json", calendar)
    write_json_if_changed(out_dir / "index.json", index)
    write_json_if_changed(out_dir / "ingestion-report.json", report.as_dict())


def build_dataset(*, out_dir: Path, fetchers: dict[str, Callable[[], list[dict[str, Any]]]] | None = None, today: date | None = None, generated_at: str | None = None) -> int:
    generated_at = generated_at or utc_now()
    today = today or datetime.now(timezone.utc).date()
    report = IngestionReport(generated_at=generated_at)
    fetchers = fetchers or {"assemblee_agenda": fetch_assemblee_agenda}
    all_items: list[dict[str, Any]] = []

    for name, fetcher in fetchers.items():
        if name == "assemblee_agenda":
            try:
                payloads = fetcher()
                items, skipped = normalize_assemblee_agenda(payloads, today=today, retrieved_at=generated_at)
                all_items.extend(items)
                report.add_endpoint(name, ASSEMBLEE_AGENDA_URL, "success", fetched=len(items), skipped=skipped, notes=["Source officielle critique: agenda open data de l’Assemblée nationale."])
            except Exception as error:
                report.add_endpoint(name, ASSEMBLEE_AGENDA_URL, "failed", error=error, notes=["Source critique indisponible; le calendrier daté ne peut pas être régénéré."])
        else:
            report.add_endpoint(name, "", "skipped", notes=["Source non configurée."])

    deduped, dedup_skipped = deduplicate_items(all_items)
    if dedup_skipped:
        report.warnings.append(f"{dedup_skipped} doublon(s) de calendrier supprimé(s).")
    errors = validate_items(deduped)
    if errors:
        report.errors.extend(errors)
        # Do not publish corrupt items; keep only the report for audit.
        write_outputs(out_dir, [], report)
        return 1
    critical_failed = any(endpoint.name in CRITICAL_SOURCES and endpoint.status == "failed" for endpoint in report.endpoints)
    write_outputs(out_dir, deduped, report)
    return 1 if critical_failed else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="public/data/parliament")
    args = parser.parse_args(argv)
    code = build_dataset(out_dir=Path(args.out))
    calendar_path = Path(args.out) / "calendar.json"
    count = 0
    if calendar_path.exists():
        count = json.loads(calendar_path.read_text(encoding="utf-8")).get("itemCount", 0)
    print(f"Parliament calendar ingestion complete: items={count} out={args.out}")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
