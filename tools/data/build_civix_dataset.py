#!/usr/bin/env python3
"""Fetch CIVIX public API data into CitizenEye static JSON files.

The script is intentionally backend-free: it runs at build time (locally or in
GitHub Actions), normalizes CIVIX responses, validates the output, and writes
plain JSON files under public/data/civix for app/runtime consumers.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

DEFAULT_BASE_URL = "https://www.civix.fr"
DATASET_VERSION = 1
USER_AGENT = "CitizenEye static CIVIX dataset builder (+https://github.com/pixxelboy/citizenEye)"
CRITICAL_ENDPOINTS = {"metadata", "index", "deputies", "groups", "scrutins"}


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def stable_id(prefix: str, value: Any) -> str:
    text = str(value or "").strip()
    return f"civix:{prefix}:{text}" if text else f"civix:{prefix}:unknown"


def clean_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def valid_iso(value: Any) -> bool:
    if value in (None, ""):
        return True
    if not isinstance(value, str):
        return False
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
        return True
    except ValueError:
        return False


def as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def attrs(payload: dict[str, Any]) -> dict[str, Any]:
    data = payload.get("data")
    if isinstance(data, dict):
        attributes = data.get("attributes")
        if isinstance(attributes, dict):
            return attributes
    return {}


def result_list(payload: dict[str, Any], key: str) -> list[dict[str, Any]]:
    attributes = attrs(payload)
    for candidate in (key, "results", "groups"):
        values = attributes.get(candidate)
        if isinstance(values, list):
            return [x for x in values if isinstance(x, dict)]
    values = payload.get("results")
    if isinstance(values, list):
        return [x for x in values if isinstance(x, dict)]
    return []


def pagination(payload: dict[str, Any]) -> dict[str, Any]:
    meta = payload.get("meta") if isinstance(payload.get("meta"), dict) else {}
    page_meta = meta.get("pagination") if isinstance(meta.get("pagination"), dict) else None
    if page_meta:
        return page_meta
    attributes = attrs(payload)
    page_attr = attributes.get("pagination") if isinstance(attributes.get("pagination"), dict) else None
    return page_attr or {}


@dataclass
class EndpointReport:
    name: str
    url: str
    status: str = "skipped"
    fetched_count: int = 0
    skipped_count: int = 0
    page_count: int = 0
    started_at: str = field(default_factory=utc_now)
    finished_at: str | None = None
    error: str | None = None
    notes: list[str] = field(default_factory=list)

    def finish(self, status: str, *, error: Exception | str | None = None) -> None:
        self.status = status
        self.finished_at = utc_now()
        if error is not None:
            self.error = str(error)

    def as_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "url": self.url,
            "status": self.status,
            "fetchedCount": self.fetched_count,
            "skippedCount": self.skipped_count,
            "pageCount": self.page_count,
            "startedAt": self.started_at,
            "finishedAt": self.finished_at,
            "error": self.error,
            "notes": self.notes,
        }


class CivixClient:
    def __init__(self, base_url: str, timeout: int = 45, retries: int = 3, sleep_seconds: float = 0.25):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.retries = retries
        self.sleep_seconds = sleep_seconds

    def url(self, path: str, params: dict[str, Any] | None = None) -> str:
        if path.startswith("http"):
            base = path
        else:
            base = self.base_url + "/" + path.lstrip("/")
        if not params:
            return base
        query = urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
        return base + ("&" if "?" in base else "?") + query

    def get_json(self, path: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        url = self.url(path, params)
        last_error: Exception | None = None
        for attempt in range(1, self.retries + 1):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    return json.loads(response.read().decode("utf-8"))
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
                last_error = error
                if attempt == self.retries:
                    break
                time.sleep(self.sleep_seconds * attempt)
        raise RuntimeError(f"GET {url} failed after {self.retries} attempts: {last_error}")


def source_meta(payload: dict[str, Any], fallback_url: str) -> dict[str, Any]:
    source = payload.get("source") if isinstance(payload.get("source"), dict) else {}
    meta = payload.get("meta") if isinstance(payload.get("meta"), dict) else {}
    return {
        "publisher": payload.get("publisher") or "CIVIX.fr",
        "provider": source.get("provider") or "Assemblée nationale",
        "via": source.get("via") or "CIVIX.fr",
        "sourceUrls": as_list(source.get("source_urls")),
        "civixUrls": source.get("civix_urls") if isinstance(source.get("civix_urls"), dict) else {"api": fallback_url},
        "retrievedAt": payload.get("retrieved_at") or meta.get("retrieved_at"),
        "lastUpdated": meta.get("last_updated"),
        "citation": payload.get("citation") if isinstance(payload.get("citation"), dict) else None,
    }


def normalize_deputy(item: dict[str, Any]) -> dict[str, Any]:
    uid = clean_text(item.get("uid"))
    return {
        "id": stable_id("deputy", uid),
        "civixId": uid,
        "uid": uid,
        "firstName": clean_text(item.get("prenom")),
        "lastName": clean_text(item.get("nom")),
        "displayName": " ".join(x for x in [clean_text(item.get("prenom")), clean_text(item.get("nom"))] if x) or None,
        "legislature": item.get("legislature"),
        "constituencyNumber": item.get("circ_num"),
        "departmentName": clean_text(item.get("circ_departement")),
        "groupUid": clean_text(item.get("groupe_uid")),
        "groupAbbreviation": clean_text(item.get("groupe_abrev")),
        "groupLabel": clean_text(item.get("groupe_libelle")),
        "urls": item.get("urls") if isinstance(item.get("urls"), dict) else {},
        "raw": item,
    }


def normalize_group(item: dict[str, Any]) -> dict[str, Any]:
    abbr = clean_text(item.get("abbr"))
    uid = clean_text(item.get("uid")) or abbr
    return {
        "id": stable_id("group", uid),
        "civixId": uid,
        "uid": clean_text(item.get("uid")),
        "abbreviation": abbr,
        "label": clean_text(item.get("libelle")),
        "legislature": item.get("legislature"),
        "memberCount": item.get("effectif"),
        "urls": item.get("urls") if isinstance(item.get("urls"), dict) else {},
        "raw": item,
    }


def vote_result_summary(item: dict[str, Any]) -> dict[str, int | None]:
    synthese = item.get("synthese_vote") if isinstance(item.get("synthese_vote"), dict) else item.get("syntheseVote")
    decompte = synthese.get("decompte") if isinstance(synthese, dict) and isinstance(synthese.get("decompte"), dict) else {}
    def n(*keys: str) -> int | None:
        for key in keys:
            value = decompte.get(key) if key in decompte else item.get(key)
            try:
                return int(value) if value not in (None, "") else None
            except (TypeError, ValueError):
                continue
        return None
    return {"pour": n("pour"), "contre": n("contre"), "abstention": n("abstentions", "abstention"), "nonVotants": n("nonVotants", "absents"), "absents": n("absents")}


def normalize_scrutin(item: dict[str, Any]) -> dict[str, Any]:
    uid = clean_text(item.get("uid"))
    objet = item.get("objet") if isinstance(item.get("objet"), dict) else {}
    dossier = objet.get("dossierLegislatif") if isinstance(objet.get("dossierLegislatif"), dict) else {}
    sort = item.get("sort") if isinstance(item.get("sort"), dict) else {}
    type_vote = item.get("type_vote") if isinstance(item.get("type_vote"), dict) else {}
    return {
        "id": stable_id("scrutin", uid),
        "civixId": uid,
        "uid": uid,
        "legislature": item.get("legislature"),
        "number": str(item.get("numero")) if item.get("numero") is not None else None,
        "date": clean_text(item.get("date_scrutin")),
        "title": clean_text(item.get("titre")),
        "question": clean_text(item.get("question")),
        "objectLabel": clean_text(item.get("objet_libelle") or objet.get("libelle")),
        "resultCode": clean_text(item.get("sort_code") or sort.get("code")),
        "resultLabel": clean_text(sort.get("libelle")),
        "voteType": clean_text(type_vote.get("libelleTypeVote")),
        "majorityType": clean_text(type_vote.get("typeMajorite")),
        "dossierRef": clean_text(dossier.get("dossierRef")),
        "dossierTitle": clean_text(dossier.get("libelle")),
        "legislativeReference": clean_text(objet.get("referenceLegislative")),
        "tags": as_list(item.get("tags")),
        "voteResultSummary": vote_result_summary(item),
        "urls": item.get("urls") if isinstance(item.get("urls"), dict) else {"civix_api": f"https://www.civix.fr/api/v1/scrutins/{uid}" if uid else None},
        "raw": item,
    }


def normalize_vote(item: dict[str, Any], scrutin_id: str) -> dict[str, Any]:
    deputy_uid = clean_text(item.get("acteur_uid") or item.get("depute_uid") or item.get("uid_depute") or item.get("acteurRef") or item.get("deputy_uid"))
    group = clean_text(item.get("groupe_abrev") or item.get("groupe") or item.get("groupe_uid"))
    position = clean_text(item.get("position") or item.get("vote") or item.get("decision"))
    return {
        "id": stable_id("vote", f"{scrutin_id}:{deputy_uid or group or item.get('id') or position}"),
        "civixId": clean_text(item.get("id")),
        "scrutinId": scrutin_id,
        "deputyId": stable_id("deputy", deputy_uid) if deputy_uid else None,
        "deputyCivixId": deputy_uid,
        "groupId": stable_id("group", group) if group else None,
        "groupAbbreviation": group if group and not group.startswith("PO") else None,
        "groupUid": group if group and group.startswith("PO") else None,
        "position": position,
        "deputyGroupAlignment": clean_text(item.get("deputyGroupAlignment")) or "unknown",
        "voteResultSummary": item.get("voteResultSummary") if isinstance(item.get("voteResultSummary"), dict) else None,
        "groupVoteDistribution": item.get("groupVoteDistribution") if isinstance(item.get("groupVoteDistribution"), dict) else None,
        "raw": item,
    }


def normalize_vote_payload(payload: dict[str, Any], scrutin_id: str) -> list[dict[str, Any]]:
    rows = result_list(payload, "votes")
    if rows:
        return [normalize_vote(v, scrutin_id) for v in rows]
    attributes = attrs(payload)
    maybe = attributes.get("votes") or payload.get("votes")
    if isinstance(maybe, list):
        return [normalize_vote(v, scrutin_id) for v in maybe if isinstance(v, dict)]
    normalized: list[dict[str, Any]] = []
    global_counts = attributes.get("results_global")
    if isinstance(global_counts, dict):
        normalized.append(normalize_vote({
            "id": f"{scrutin_id}:global",
            "position": "summary",
            "voteResultSummary": {
                "pour": global_counts.get("pour"),
                "contre": global_counts.get("contre"),
                "abstention": global_counts.get("abstention"),
                "nonVotants": global_counts.get("non_votant"),
                "absents": None,
            },
            "raw_kind": "global_summary",
        }, scrutin_id))
    groups = attributes.get("results_by_group")
    if isinstance(groups, list):
        for group in groups:
            if not isinstance(group, dict):
                continue
            normalized.append(normalize_vote({
                "id": f"{scrutin_id}:{group.get('groupe_uid')}",
                "groupe_uid": group.get("groupe_uid"),
                "position": "group_distribution",
                "groupVoteDistribution": {
                    "pourCount": group.get("pour"),
                    "contreCount": group.get("contre"),
                    "abstentionCount": group.get("abstention"),
                    "absentCount": group.get("non_votant"),
                    "unknownCount": group.get("unknown"),
                    "totalCount": group.get("total"),
                },
                "raw_kind": "group_distribution",
                **group,
            }, scrutin_id))
    return normalized


def normalize_dossier_from_scrutin(scrutin: dict[str, Any]) -> dict[str, Any] | None:
    raw = scrutin.get("raw") if isinstance(scrutin.get("raw"), dict) else {}
    objet = raw.get("objet") if isinstance(raw.get("objet"), dict) else {}
    dossier = objet.get("dossierLegislatif") if isinstance(objet.get("dossierLegislatif"), dict) else {}
    ref = clean_text(dossier.get("dossierRef") or scrutin.get("dossierRef"))
    if not ref:
        return None
    return {
        "id": stable_id("dossier", ref),
        "civixId": ref,
        "ref": ref,
        "title": clean_text(dossier.get("libelle") or scrutin.get("dossierTitle")),
        "currentStage": clean_text(scrutin.get("voteType")),
        "stages": [],
        "textReferences": [x for x in [clean_text(scrutin.get("legislativeReference"))] if x],
        "dossierProgress": {"currentStage": clean_text(scrutin.get("voteType")), "stages": []},
        "rawSource": {"firstSeenScrutinId": scrutin.get("uid")},
    }


def normalize_stats(deputies: list[dict[str, Any]], groups: list[dict[str, Any]], scrutins: list[dict[str, Any]], votes: list[dict[str, Any]]) -> dict[str, Any]:
    by_position = {"pourCount": 0, "contreCount": 0, "abstentionCount": 0, "absentCount": 0}
    for vote in votes:
        pos = (vote.get("position") or "").lower()
        if "pour" in pos:
            by_position["pourCount"] += 1
        elif "contre" in pos:
            by_position["contreCount"] += 1
        elif "abst" in pos:
            by_position["abstentionCount"] += 1
        elif "abs" in pos or "non" in pos:
            by_position["absentCount"] += 1
    group_distribution: dict[str, dict[str, int]] = {}
    for deputy in deputies:
        abbr = deputy.get("groupAbbreviation") or "unknown"
        group_distribution.setdefault(abbr, {"deputyCount": 0})["deputyCount"] += 1
    return {
        "counts": {"deputies": len(deputies), "groups": len(groups), "scrutins": len(scrutins), "votes": len(votes)},
        "deputyVoteDistribution": by_position,
        "groupVoteDistribution": group_distribution,
        "raw": None,
    }


def page_all(client: CivixClient, report: EndpointReport, path: str, key: str, page_size: int, extra: dict[str, Any] | None = None) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any] | None]:
    items: list[dict[str, Any]] = []
    pages: list[dict[str, Any]] = []
    first_payload: dict[str, Any] | None = None
    page = 1
    while True:
        params = {"page": page, "page_size": page_size, **(extra or {})}
        payload = client.get_json(path, params)
        if first_payload is None:
            first_payload = payload
        values = result_list(payload, key)
        items.extend(values)
        pages.append(source_meta(payload, client.url(path, params)))
        report.page_count += 1
        next_page = pagination(payload).get("next_page")
        if not next_page:
            break
        page = int(next_page)
    return items, pages, first_payload


def run_endpoint(name: str, url: str, critical: bool, reports: list[EndpointReport], fn: Callable[[EndpointReport], Any]) -> Any:
    report = EndpointReport(name=name, url=url)
    reports.append(report)
    try:
        result = fn(report)
        report.finish("success")
        return result
    except Exception as error:
        report.finish("failed", error=error)
        if critical:
            raise
        return None


def document(name: str, generated_at: str, source: dict[str, Any], items_key: str, items: Any, extra: dict[str, Any] | None = None) -> dict[str, Any]:
    payload = {
        "schemaVersion": DATASET_VERSION,
        "dataset": f"citizeneye-civix-{name}",
        "generatedAt": generated_at,
        "civixFetchedAt": source.get("retrievedAt"),
        "civixApiVersion": source.get("apiVersion"),
        "source": source,
        items_key: items,
    }
    if extra:
        payload.update(extra)
    return payload


VOLATILE_KEYS = {"generatedAt", "civixFetchedAt", "retrievedAt", "lastUpdated", "startedAt", "finishedAt"}


def stable_payload(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: stable_payload(child) for key, child in value.items() if key not in VOLATILE_KEYS}
    if isinstance(value, list):
        return [stable_payload(child) for child in value]
    return value


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        try:
            existing = json.loads(path.read_text(encoding="utf-8"))
            if stable_payload(existing) == stable_payload(payload):
                return
        except json.JSONDecodeError:
            pass
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def validate_dataset(out_dir: Path) -> list[str]:
    errors: list[str] = []
    expected = {
        "deputies.json": "deputies",
        "groups.json": "groups",
        "scrutins.json": "scrutins",
        "votes.json": "votes",
        "dossiers.json": "dossiers",
        "stats.json": "stats",
        "index.json": "files",
        "ingestion-report.json": "endpoints",
    }
    for file_name, key in expected.items():
        path = out_dir / file_name
        if not path.exists():
            errors.append(f"missing {file_name}")
            continue
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            errors.append(f"{file_name}: invalid JSON: {error}")
            continue
        for required in ["schemaVersion", "generatedAt", "source"]:
            if required not in payload and file_name not in {"ingestion-report.json"}:
                errors.append(f"{file_name}: missing {required}")
        if "generatedAt" in payload and not valid_iso(payload.get("generatedAt")):
            errors.append(f"{file_name}: generatedAt is not ISO")
        if file_name != "stats.json" and key in payload and not isinstance(payload[key], list):
            errors.append(f"{file_name}: {key} must be an array")
        if file_name == "stats.json" and not isinstance(payload.get("stats"), dict):
            errors.append("stats.json: stats must be an object")
        if file_name == "ingestion-report.json":
            endpoints = payload.get("endpoints")
            if not isinstance(endpoints, list) or not endpoints:
                errors.append("ingestion-report.json: endpoints must be a non-empty array")
            elif not all(isinstance(ep, dict) and ep.get("name") and ep.get("status") in {"success", "failed", "skipped"} for ep in endpoints):
                errors.append("ingestion-report.json: every endpoint needs name/status")
        for item in payload.get(key, []) if isinstance(payload.get(key), list) else []:
            if isinstance(item, dict) and "id" in item and not isinstance(item.get("id"), str):
                errors.append(f"{file_name}: item id must be a string")
    return errors


def build_dataset(args: argparse.Namespace) -> int:
    generated_at = utc_now()
    client = CivixClient(args.base_url, timeout=args.timeout, retries=args.retries, sleep_seconds=args.retry_sleep)
    out_dir = Path(args.out)
    reports: list[EndpointReport] = []

    metadata = run_endpoint("metadata", client.url("/.well-known/civix.json"), True, reports, lambda r: client.get_json("/.well-known/civix.json"))
    api_index = run_endpoint("index", client.url("/api/v1/index"), True, reports, lambda r: client.get_json("/api/v1/index"))
    base_source = {
        "publisher": "CIVIX.fr",
        "provider": "Assemblée nationale",
        "via": "CIVIX.fr",
        "sourceUrls": ["https://www.civix.fr/api", "https://www.data.gouv.fr/dataservices/api-publique-civix", "https://www.civix.fr"],
        "civixUrls": {"site": "https://www.civix.fr", "api": client.url("/api/v1/index"), "metadata": client.url("/.well-known/civix.json")},
        "retrievedAt": generated_at,
        "lastUpdated": metadata.get("schema_version") if isinstance(metadata, dict) else None,
        "apiVersion": (api_index or {}).get("api_version") if isinstance(api_index, dict) else None,
        "schemaVersion": (metadata or {}).get("schema_version") if isinstance(metadata, dict) else None,
    }

    deputies_raw, deputies_pages, deputies_payload = run_endpoint(
        "deputies", client.url("/api/v1/deputes"), True, reports,
        lambda r: page_all(client, r, "/api/v1/deputes", "results", args.page_size, {"legislature": args.legislature})
    )
    deputies = [normalize_deputy(x) for x in deputies_raw]
    reports[-1].fetched_count = len(deputies)

    groups_raw = run_endpoint("groups", client.url("/api/v1/groupes"), True, reports, lambda r: result_list(client.get_json("/api/v1/groupes"), "groups"))
    groups = [normalize_group(x) for x in groups_raw]
    reports[-1].fetched_count = len(groups)

    scrutins_raw, scrutins_pages, scrutins_payload = run_endpoint(
        "scrutins", client.url("/api/v1/scrutins"), True, reports,
        lambda r: page_all(client, r, "/api/v1/scrutins", "results", args.page_size, {"legislature": args.legislature})
    )
    scrutins = [normalize_scrutin(x) for x in scrutins_raw]
    reports[-1].fetched_count = len(scrutins)

    votes: list[dict[str, Any]] = []
    def fetch_votes(report: EndpointReport) -> None:
        max_items = min(args.max_scrutin_votes, len(scrutins)) if args.max_scrutin_votes >= 0 else len(scrutins)
        report.notes.append(f"Votes nominaux récupérés pour {max_items} scrutin(s). Limite configurable avec --max-scrutin-votes/CIVIX_MAX_SCRUTIN_VOTES pour usage raisonnable.")
        for scrutin in scrutins[:max_items]:
            uid = scrutin.get("uid")
            if not uid:
                report.skipped_count += 1
                continue
            try:
                payload = client.get_json(f"/api/v1/scrutins/{uid}/votes")
                normalized_rows = normalize_vote_payload(payload, uid)
                votes.extend(normalized_rows)
                report.fetched_count += len(normalized_rows)
            except Exception as error:
                report.skipped_count += 1
                report.notes.append(f"{uid}: {error}")
        if max_items < len(scrutins):
            report.skipped_count += len(scrutins) - max_items
        return None
    run_endpoint("individual_votes", client.url("/api/v1/scrutins/{uid}/votes"), False, reports, fetch_votes)

    dossiers_by_ref: dict[str, dict[str, Any]] = {}
    for scrutin in scrutins:
        dossier = normalize_dossier_from_scrutin(scrutin)
        if not dossier:
            continue
        existing = dossiers_by_ref.get(dossier["ref"])
        if not existing:
            dossiers_by_ref[dossier["ref"]] = dossier
        else:
            refs = set(existing.get("textReferences", [])) | set(dossier.get("textReferences", []))
            existing["textReferences"] = sorted(refs)
    dossiers = sorted(dossiers_by_ref.values(), key=lambda x: x.get("ref") or "")
    dossier_report = EndpointReport(name="dossiers", url=client.url("/api/v1/scrutins"), status="success", fetched_count=len(dossiers), finished_at=utc_now(), notes=["CIVIX n'expose pas d'endpoint dossier dédié dans l'OpenAPI actuelle; dossiers normalisés depuis les références des scrutins quand disponibles."])
    reports.append(dossier_report)

    stats = normalize_stats(deputies, groups, scrutins, votes)
    reports.append(EndpointReport(name="stats", url=client.url("/api/v1/index"), status="success", fetched_count=1, finished_at=utc_now(), notes=["Statistiques déterministes dérivées du jeu CIVIX généré; aucun score politique inféré."]))

    endpoints = [r.as_dict() for r in reports]
    common_source = {**base_source, "endpoints": endpoints}
    deputies_source = {**common_source, **source_meta(deputies_payload or {}, client.url("/api/v1/deputes")), "apiVersion": common_source.get("apiVersion"), "pages": deputies_pages}
    scrutins_source = {**common_source, **source_meta(scrutins_payload or {}, client.url("/api/v1/scrutins")), "apiVersion": common_source.get("apiVersion"), "pages": scrutins_pages}
    files = {
        "deputies.json": document("deputies", generated_at, deputies_source, "deputies", deputies),
        "groups.json": document("groups", generated_at, common_source, "groups", groups),
        "scrutins.json": document("scrutins", generated_at, scrutins_source, "scrutins", scrutins),
        "votes.json": document("votes", generated_at, common_source, "votes", votes),
        "dossiers.json": document("dossiers", generated_at, common_source, "dossiers", dossiers),
        "stats.json": document("stats", generated_at, common_source, "stats", stats),
    }
    index_files = []
    for name, payload in files.items():
        write_json(out_dir / name, payload)
        index_files.append({"name": name.removesuffix(".json"), "url": f"data/civix/{name}", "count": len(payload.get(name.removesuffix('.json'), [])) if isinstance(payload.get(name.removesuffix('.json')), list) else None})

    index_payload = document("index", generated_at, common_source, "files", index_files, extra={"metadata": {"civix": metadata, "apiIndexSummary": (api_index or {}).get("summary") if isinstance(api_index, dict) else None}})
    report_payload = {
        "schemaVersion": DATASET_VERSION,
        "dataset": "citizeneye-civix-ingestion-report",
        "generatedAt": generated_at,
        "source": common_source,
        "status": "success" if all(r.status == "success" or r.name not in CRITICAL_ENDPOINTS for r in reports) else "partial",
        "endpoints": endpoints,
        "files": index_files,
    }
    write_json(out_dir / "index.json", index_payload)
    write_json(out_dir / "ingestion-report.json", report_payload)

    errors = validate_dataset(out_dir)
    if errors:
        for error in errors:
            print(f"validation error: {error}", file=sys.stderr)
        return 1
    print(f"CIVIX ingestion complete: deputies={len(deputies)} groups={len(groups)} scrutins={len(scrutins)} votes={len(votes)} dossiers={len(dossiers)} out={out_dir}")
    return 0


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build CitizenEye CIVIX static dataset")
    parser.add_argument("--out", default="public/data/civix", help="Output directory for generated CIVIX JSON files")
    parser.add_argument("--base-url", default=os.environ.get("CIVIX_BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--legislature", type=int, default=int(os.environ.get("CIVIX_LEGISLATURE", "17")))
    parser.add_argument("--page-size", type=int, default=int(os.environ.get("CIVIX_PAGE_SIZE", "100")))
    parser.add_argument("--max-scrutin-votes", type=int, default=int(os.environ.get("CIVIX_MAX_SCRUTIN_VOTES", "250")), help="Max scrutins for which to fetch individual votes; -1 fetches all")
    parser.add_argument("--timeout", type=int, default=45)
    parser.add_argument("--retries", type=int, default=3)
    parser.add_argument("--retry-sleep", type=float, default=0.25)
    return parser.parse_args(argv)


if __name__ == "__main__":
    raise SystemExit(build_dataset(parse_args(sys.argv[1:])))
