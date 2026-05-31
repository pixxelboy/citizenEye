#!/usr/bin/env python3
"""Compile official Assemblée nationale public ZIP archives into compact CitizenEye static JSON."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import os
import re
import sys
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple

ACTIVE_DEPUTIES_URL = "https://data.assemblee-nationale.fr/static/openData/repository/17/amo/deputes_actifs_mandats_actifs_organes/AMO10_deputes_actifs_mandats_actifs_organes.json.zip"
SCRUTINS_URL = "https://data.assemblee-nationale.fr/static/openData/repository/17/loi/scrutins/Scrutins.json.zip"
DOSSIERS_URL = "https://data.assemblee-nationale.fr/static/openData/repository/17/loi/dossiers_legislatifs/Dossiers_Legislatifs.json.zip"
DATASET = "citizeneye-assemblee-v17"
LEGISLATURE = "17"

REGIONS = {
    **{str(i).zfill(2): "Auvergne-Rhône-Alpes" for i in [1, 3, 7, 15, 26, 38, 42, 43, 63, 69, 73, 74]},
    **{str(i).zfill(2): "Hauts-de-France" for i in [2, 59, 60, 62, 80]},
    **{str(i).zfill(2): "Provence-Alpes-Côte d’Azur" for i in [4, 5, 6, 13, 83, 84]},
    **{str(i).zfill(2): "Grand Est" for i in [8, 10, 51, 52, 54, 55, 57, 67, 68, 88]},
    **{str(i).zfill(2): "Occitanie" for i in [9, 11, 12, 30, 31, 32, 34, 46, 48, 65, 66, 81, 82]},
    **{str(i).zfill(2): "Nouvelle-Aquitaine" for i in [16, 17, 19, 23, 24, 33, 40, 47, 64, 79, 86, 87]},
    **{str(i).zfill(2): "Normandie" for i in [14, 27, 50, 61, 76]},
    **{str(i).zfill(2): "Centre-Val de Loire" for i in [18, 28, 36, 37, 41, 45]},
    "2A": "Corse", "2B": "Corse",
    **{str(i).zfill(2): "Bourgogne-Franche-Comté" for i in [21, 25, 39, 58, 70, 71, 89, 90]},
    **{str(i).zfill(2): "Bretagne" for i in [22, 29, 35, 56]},
    **{str(i).zfill(2): "Pays de la Loire" for i in [44, 49, 53, 72, 85]},
    **{str(i).zfill(2): "Île-de-France" for i in [75, 77, 78, 91, 92, 93, 94, 95]},
    "971": "Guadeloupe", "972": "Martinique", "973": "Guyane", "974": "La Réunion", "975": "Saint-Pierre-et-Miquelon", "976": "Mayotte",
    "977": "Saint-Barthélemy et Saint-Martin", "978": "Saint-Barthélemy et Saint-Martin", "986": "Wallis-et-Futuna", "987": "Polynésie française", "988": "Nouvelle-Calédonie",
    "99": "Français établis hors de France", "099": "Français établis hors de France",
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def download(url: str) -> bytes:
    if url.startswith("file://"):
        return Path(url[7:]).read_bytes()
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "CitizenEye static data builder"}), timeout=180) as response:
        return response.read()


def as_list(value: Any) -> List[dict]:
    if isinstance(value, list):
        return [x for x in value if isinstance(x, dict)]
    if isinstance(value, dict):
        return [value]
    return []


def clean(value: Any) -> Optional[str]:
    if isinstance(value, dict):
        value = value.get("#text")
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def active(mandate: dict) -> bool:
    return not clean(mandate.get("dateFin"))


def photo_url(actor_id: str) -> Optional[str]:
    numeric_id = actor_id.removeprefix("PA")
    if not numeric_id or not numeric_id.isdigit():
        return None
    return f"https://www.assemblee-nationale.fr/dyn/static/tribun/17/photos/carre/{numeric_id}.jpg"


def profession(actor: dict) -> Optional[str]:
    prof = actor.get("profession") or {}
    current = clean(prof.get("libelleCourant"))
    if current:
        return re.sub(r"^\(\d+\)\s*-\s*", "", current).strip()
    insee = prof.get("socProcINSEE") or {}
    return clean(insee.get("catSocPro")) or clean(insee.get("famSocPro"))


def region(place: dict) -> Optional[str]:
    direct = clean(place.get("region"))
    if direct:
        return direct
    code = clean(place.get("numDepartement"))
    if not code:
        return None
    code = code.upper().lstrip("0") or code
    return REGIONS.get(code) or REGIONS.get(code.zfill(2)) or ("Français établis hors de France" if code.startswith("99") else None)


def parse_deputies(zip_bytes: bytes) -> List[dict]:
    groups: Dict[str, dict] = {}
    deputies: List[dict] = []
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
        for name in sorted(zf.namelist()):
            if name.startswith("json/organe/") and name.endswith(".json"):
                org = json.loads(zf.read(name)).get("organe") or {}
                if org.get("codeType") != "GP" and org.get("typeOrgane") != "GP":
                    continue
                uid = clean(org.get("uid"))
                if not uid:
                    continue
                full = clean(org.get("libelle"))
                abbr = clean(org.get("libelleAbrev")) or clean(org.get("libelleAbrege"))
                groups[uid] = {"full": full or abbr or "Groupe non renseigné", "abbr": abbr or full or "N/R"}
        for name in sorted(zf.namelist()):
            if not (name.startswith("json/acteur/") and name.endswith(".json")):
                continue
            actor = json.loads(zf.read(name)).get("acteur") or {}
            mandates = as_list((actor.get("mandats") or {}).get("mandat"))
            assembly = next((m for m in mandates if m.get("typeOrgane") == "ASSEMBLEE" and active(m)), None)
            if not assembly:
                continue
            place = ((assembly.get("election") or {}).get("lieu") or {})
            ident = ((actor.get("etatCivil") or {}).get("ident") or {})
            group_ref = None
            for mandate in mandates:
                if mandate.get("typeOrgane") == "GP" and active(mandate):
                    group_ref = clean((mandate.get("organes") or {}).get("organeRef"))
                    break
            group = groups.get(group_ref or "", {"full": "Groupe non renseigné", "abbr": "N/R"})
            email = None
            for address in as_list((actor.get("adresses") or {}).get("adresse")):
                val = clean(address.get("valElec"))
                if val and "@" in val:
                    email = val
                    break
            actor_id = clean(actor.get("uid")) or ""
            deputy = {
                "id": actor_id,
                "name": f"{clean(ident.get('prenom')) or ''} {clean(ident.get('nom')) or ''}".strip(),
                "group": group["abbr"],
                "departmentName": clean(place.get("departement")) or "",
                "departmentCode": clean(place.get("numDepartement")) or "",
                "constituencyNumber": clean(place.get("numCirco")) or "",
                "email": email,
                "regionName": region(place),
                "profession": profession(actor),
                "politicalGroupFullName": group["full"],
                "politicalGroupAbbreviation": group["abbr"],
                "photoUrl": photo_url(actor_id),
            }
            deputies.append(deputy)
    return sorted(deputies, key=lambda d: (d["departmentCode"], int(d["constituencyNumber"] or 999) if str(d["constituencyNumber"]).isdigit() else 999))


def nullable_int(value: Any) -> Optional[int]:
    try:
        return int(value) if value not in (None, "") else None
    except (TypeError, ValueError):
        return None


def contains_actor(nominatif: dict, category: str, actor_id: str) -> bool:
    bucket = nominatif.get(category) or {}
    return any(clean(v.get("acteurRef")) == actor_id for v in as_list(bucket.get("votant")))


def find_position(scrutin: dict, actor_id: str) -> Optional[str]:
    groupes = as_list(((((scrutin.get("ventilationVotes") or {}).get("organe") or {}).get("groupes") or {}).get("groupe")))
    for group in groupes:
        nominatif = ((group.get("vote") or {}).get("decompteNominatif") or {})
        if contains_actor(nominatif, "pours", actor_id):
            return "POUR"
        if contains_actor(nominatif, "contres", actor_id):
            return "CONTRE"
        if contains_actor(nominatif, "abstentions", actor_id):
            return "ABSTENTION"
        if contains_actor(nominatif, "nonVotants", actor_id) or contains_actor(nominatif, "nonVotantsVolontaires", actor_id):
            return "NON_VOTANT"
    return None


def group_position(scrutin: dict, actor_id: str, deputy_position: str) -> Optional[dict]:
    groupes = as_list(((((scrutin.get("ventilationVotes") or {}).get("organe") or {}).get("groupes") or {}).get("groupe")))
    for group in groupes:
        vote = group.get("vote") or {}
        nominatif = vote.get("decompteNominatif") or {}
        if not any(contains_actor(nominatif, c, actor_id) for c in ["pours", "contres", "abstentions", "nonVotants", "nonVotantsVolontaires"]):
            continue
        counts = vote.get("decompteVoix") or {}
        values = {
            "POUR": nullable_int(counts.get("pour")),
            "CONTRE": nullable_int(counts.get("contre")),
            "ABSTENTION": nullable_int(counts.get("abstentions")),
            "NON_VOTANT": nullable_int(counts.get("nonVotants")),
        }
        known = {k: v for k, v in values.items() if v is not None}
        majority = max(known, key=lambda key: known[key]) if known else None
        return {
            "groupName": clean(group.get("libelle")) or clean(group.get("organeRef")) or "Groupe parlementaire",
            "groupMajorityPosition": majority,
            "deputyVotedLikeGroup": (majority == deputy_position) if majority else None,
            "forCount": values["POUR"],
            "againstCount": values["CONTRE"],
            "abstentionCount": values["ABSTENTION"],
            "nonVotingCount": values["NON_VOTANT"],
        }
    return None


def vote_breakdown(scrutin: dict) -> Optional[dict]:
    synthese = scrutin.get("syntheseVote") or {}
    if not synthese:
        return None
    decompte = synthese.get("decompte") or {}
    return {
        "totalVoters": nullable_int(synthese.get("nombreVotants")),
        "forCount": nullable_int(decompte.get("pour")),
        "againstCount": nullable_int(decompte.get("contre")),
        "abstentionCount": nullable_int(decompte.get("abstentions")),
        "nonVotingCount": nullable_int(synthese.get("nombreNonVotants")),
        "absoluteMajority": nullable_int(synthese.get("majoriteAbsolue")),
        "resultLabel": clean((scrutin.get("sort") or {}).get("libelle")) or clean(synthese.get("annonce")),
    }


def capitalize_first(text: str) -> str:
    if not text:
        return text
    return text[0].upper() + text[1:]


def summary(scrutin: dict) -> str:
    synthese = scrutin.get("syntheseVote") or {}
    decompte = synthese.get("decompte") or {}
    numero = clean(scrutin.get("numero")) or ""
    if decompte:
        return f"Pour {decompte.get('pour', '0')}, contre {decompte.get('contre', '0')}, abstentions {decompte.get('abstentions', '0')}. Source : Assemblée nationale, scrutin n°{numero}."
    return f"Source : Assemblée nationale, scrutin n°{numero} du {clean(scrutin.get('dateScrutin')) or ''}."


def parse_votes(zip_bytes: bytes, deputy_ids: Iterable[str]) -> dict:
    votes_by_deputy: Dict[str, List[dict]] = {actor_id: [] for actor_id in deputy_ids}
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
        for name in sorted(zf.namelist()):
            if not name.endswith(".json"):
                continue
            scrutin = json.loads(zf.read(name)).get("scrutin") or {}
            for actor_id in votes_by_deputy.keys():
                position = find_position(scrutin, actor_id)
                if not position:
                    continue
                numero = clean(scrutin.get("numero")) or ""
                objet = scrutin.get("objet") or {}
                dossier = objet.get("dossierLegislatif") or {}
                votes_by_deputy[actor_id].append({
                    "id": clean(scrutin.get("uid")) or "",
                    "number": numero,
                    "date": clean(scrutin.get("dateScrutin")) or "",
                    "title": capitalize_first(clean(scrutin.get("titre")) or ""),
                    "result": clean((scrutin.get("sort") or {}).get("libelle")) or clean((scrutin.get("syntheseVote") or {}).get("annonce")) or "Résultat non renseigné",
                    "summary": summary(scrutin),
                    "deputePosition": position,
                    "sourceUrl": f"https://www.assemblee-nationale.fr/dyn/17/scrutins/{numero}",
                    "voteBreakdown": vote_breakdown(scrutin),
                    "groupPosition": group_position(scrutin, actor_id, position),
                    "objectTitle": clean(objet.get("libelle")),
                    "dossierRef": clean(dossier.get("dossierRef")),
                    "dossierTitle": clean(dossier.get("libelle")),
                    "legislativeReference": clean(objet.get("referenceLegislative")),
                    "seanceRef": clean(scrutin.get("seanceRef")),
                })
    return {k: sorted(v, key=lambda item: item.get("date") or "", reverse=True) for k, v in votes_by_deputy.items() if v}


def flatten_acts(act: dict) -> List[dict]:
    children = []
    for child in as_list(((act.get("actesLegislatifs") or {}).get("acteLegislatif"))):
        children.extend(flatten_acts(child))
    return [act] + children


def infer_parent_type(title: Optional[str]) -> str:
    text = (title or "").lower()
    if "projet de loi de finances" in text:
        return "Projet de loi de finances"
    if "financement de la sécurité sociale" in text or "financement de la securite sociale" in text:
        return "Projet de loi de financement de la sécurité sociale"
    if "projet de loi constitutionnelle" in text:
        return "Révision constitutionnelle"
    if "projet de loi" in text:
        return "Projet de loi"
    if "proposition de loi" in text:
        return "Proposition de loi"
    if "résolution" in text or "resolution" in text:
        return "Proposition de résolution"
    if any(w in text for w in ["accord", "convention", "ratification"]):
        return "Accord international"
    return "Dossier législatif"


def dossier_url(uid: str, title: Optional[str]) -> Optional[str]:
    if not uid:
        return None
    return f"https://www.assemblee-nationale.fr/dyn/17/dossiers/{uid}"


def parse_dossiers(zip_bytes: bytes) -> dict:
    dossiers = {}
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
        for name in sorted(zf.namelist()):
            if not (name.startswith("json/dossierParlementaire/") and name.endswith(".json")):
                continue
            dossier = json.loads(zf.read(name)).get("dossierParlementaire") or {}
            uid = clean(dossier.get("uid"))
            if not uid:
                continue
            title = clean((dossier.get("titreDossier") or {}).get("titre"))
            procedure = clean((dossier.get("procedureParlementaire") or {}).get("libelle"))
            acts = []
            for act in as_list(((dossier.get("actesLegislatifs") or {}).get("acteLegislatif"))):
                acts.extend(flatten_acts(act))
            latest = next((a for a in reversed(acts) if clean(((a.get("libelleActe") or {}).get("libelleCourt")))), None)
            commission = next((clean(a.get("organeRef")) for a in acts if "COM-FOND" in (clean(a.get("codeActe")) or "")), None)
            deposit_ref = next((clean(a.get("texteAssocie")) for a in acts if clean(a.get("texteAssocie"))), None)
            match = re.search(r"B(\d+)", deposit_ref or "")
            adoption = next((a for a in reversed(acts) if re.search("ADOPTION|REJET", clean(a.get("codeActe")) or "", re.I)), None)
            rapporteur_refs = {
                ref
                for a in acts
                for r in as_list((a.get("rapporteurs") or {}).get("rapporteur"))
                for ref in [clean(r.get("acteurRef"))]
                if ref
            }
            rapporteurs = sorted(rapporteur_refs)
            dossiers[uid] = {
                "title": title,
                "type": procedure or infer_parent_type(title),
                "procedureStage": clean(((latest or {}).get("libelleActe") or {}).get("libelleCourt")) or clean(((latest or {}).get("libelleActe") or {}).get("nomCanonique")),
                "legislature": clean(dossier.get("legislature")) or LEGISLATURE,
                "dossierUrl": dossier_url(uid, title),
                "commissionName": commission,
                "rapporteurs": rapporteurs,
                "depositNumber": match.group(1) if match else None,
                "adoptionStatus": clean(((adoption or {}).get("libelleActe") or {}).get("libelleCourt")),
            }
    return dossiers


def canonical_bytes(data: Any) -> bytes:
    return json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def gzip_bytes(data: bytes) -> bytes:
    out = io.BytesIO()
    with gzip.GzipFile(fileobj=out, mode="wb", mtime=0) as gz:
        gz.write(data)
    return out.getvalue()


def write_dataset(out: Path, name: str, data: Any) -> dict:
    gz = gzip_bytes(canonical_bytes(data))
    digest = sha256(gz)
    filename = f"{name}-{digest}.json.gz"
    (out / filename).write_bytes(gz)
    return {"name": name, "url": filename, "sha256": digest, "bytes": len(gz)}


def validate_file(out: Path, file_entry: dict) -> None:
    data = (out / file_entry["url"]).read_bytes()
    if sha256(data) != file_entry["sha256"] or len(data) != file_entry["bytes"]:
        raise SystemExit(f"manifest validation failed for {file_entry['name']}")


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="public")
    parser.add_argument("--active-deputies-url", default=ACTIVE_DEPUTIES_URL)
    parser.add_argument("--scrutins-url", default=SCRUTINS_URL)
    parser.add_argument("--dossiers-url", default=DOSSIERS_URL)
    parser.add_argument("--skip-unchanged-against", default=None)
    args = parser.parse_args(argv)

    active_zip = download(args.active_deputies_url)
    scrutins_zip = download(args.scrutins_url)
    dossiers_zip = download(args.dossiers_url)
    source_hashes = {"activeDeputiesSha256": sha256(active_zip), "scrutinsSha256": sha256(scrutins_zip), "dossiersSha256": sha256(dossiers_zip)}
    version = sha256((source_hashes["activeDeputiesSha256"] + source_hashes["scrutinsSha256"] + source_hashes["dossiersSha256"]).encode())

    if args.skip_unchanged_against:
        try:
            current = json.loads(download(args.skip_unchanged_against))
            if current.get("version") == version:
                print("changed=false")
                if os.environ.get("GITHUB_OUTPUT"):
                    Path(os.environ["GITHUB_OUTPUT"]).open("a").write("changed=false\n")
                return 0
        except Exception:
            pass
    if os.environ.get("GITHUB_OUTPUT"):
        Path(os.environ["GITHUB_OUTPUT"]).open("a").write("changed=true\n")

    deputies = parse_deputies(active_zip)
    votes = parse_votes(scrutins_zip, [d["id"] for d in deputies])
    dossiers = parse_dossiers(dossiers_zip)
    if not deputies:
        raise SystemExit("no deputies parsed")
    if not votes:
        raise SystemExit("no votes parsed")
    if not dossiers:
        raise SystemExit("no dossiers parsed")

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    files = [
        write_dataset(out, "deputies", {"schemaVersion": 1, "deputies": deputies}),
        write_dataset(out, "votes", {"schemaVersion": 1, "votesByDeputy": votes}),
        write_dataset(out, "dossiers", {"schemaVersion": 1, "dossiersByRef": dossiers}),
    ]
    manifest = {
        "schemaVersion": 1,
        "dataset": DATASET,
        "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "version": version,
        "source": {"legislature": LEGISLATURE, "activeDeputiesUrl": args.active_deputies_url, "scrutinsUrl": args.scrutins_url, "dossiersUrl": args.dossiers_url, **source_hashes},
        "files": files,
    }
    (out / "manifest.json").write_bytes(canonical_bytes(manifest))
    for entry in files:
        validate_file(out, entry)
    vote_count = sum(len(v) for v in votes.values())
    print(f"deputies={len(deputies)} votes={vote_count} indexedDeputies={len(votes)} dossiers={len(dossiers)} version={version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
