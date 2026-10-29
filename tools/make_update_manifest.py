#!/usr/bin/env python3
"""Build a GitHub release manifest (update.json) for a signed SI-M APK.

The in-app updater reads exactly one manifest and one APK asset from the
repository's *latest* release:

    https://github.com/jokaje/SI-M/releases/latest/download/update.json
    https://github.com/jokaje/SI-M/releases/latest/download/SI-M.apk

This script only writes the manifest. It never uploads and never invents
values: size and SHA-256 are computed from the APK file you pass in, and the
version fields are read from the APK itself where possible.

Usage:
    python3 tools/make_update_manifest.py \\
        --apk path/to/SI-M-1.2.2.apk \\
        --release-notes notes.md \\
        --output dist/update.json

Verify an existing manifest against a local APK:
    python3 tools/make_update_manifest.py --apk app.apk --verify dist/update.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

PACKAGE_NAME = "com.aistudio.sim.aiorb"
APK_ASSET_URL = "https://github.com/jokaje/SI-M/releases/latest/download/SI-M.apk"
MAX_APK_BYTES = 200 * 1024 * 1024
MANIFEST_KEYS = {
    "schemaVersion",
    "packageName",
    "versionCode",
    "versionName",
    "apkUrl",
    "sha256",
    "sizeBytes",
    "minSdk",
    "releaseNotes",
}


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def read_apk_metadata(apk: Path) -> tuple[int, str, int]:
    """Read versionCode, versionName and minSdk from the APK via aapt2/apkanalyzer."""
    candidates = [
        ["aapt2", "dump", "badging", str(apk)],
        ["aapt", "dump", "badging", str(apk)],
    ]
    text = None
    for command in candidates:
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=120)
        except (FileNotFoundError, subprocess.SubprocessError):
            continue
        if result.returncode == 0 and "package:" in result.stdout:
            text = result.stdout
            break
    if text is None:
        raise SystemExit(
            "Konnte APK-Metadaten nicht lesen. aapt2 aus den Android build-tools in den PATH legen "
            "oder versionCode/versionName/minSdk explizit angeben."
        )

    package_line = next(line for line in text.splitlines() if line.startswith("package:"))
    version_code = re.search(r"versionCode='(\d+)'", package_line)
    version_name = re.search(r"versionName='([^']*)'", package_line)
    sdk_line = next((line for line in text.splitlines() if line.startswith("sdkVersion:")), "")
    min_sdk = re.search(r"sdkVersion:'(\d+)'", sdk_line)
    if not (version_code and version_name and min_sdk):
        raise SystemExit("APK-Metadaten unvollständig; versionCode/versionName/minSdk explizit angeben.")
    if f"package: name='{PACKAGE_NAME}'" not in package_line:
        raise SystemExit(f"APK gehört nicht zu {PACKAGE_NAME}: {package_line}")
    return int(version_code.group(1)), version_name.group(1), int(min_sdk.group(1))


def build_manifest(apk: Path, notes: str, overrides: dict) -> dict:
    size = apk.stat().st_size
    if size <= 0 or size > MAX_APK_BYTES:
        raise SystemExit(f"APK-Größe unplausibel: {size} Bytes")
    version_code, version_name, min_sdk = read_apk_metadata(apk)
    manifest = {
        "schemaVersion": 1,
        "packageName": PACKAGE_NAME,
        "versionCode": overrides.get("version_code") or version_code,
        "versionName": overrides.get("version_name") or version_name,
        "apkUrl": APK_ASSET_URL,
        "sha256": sha256_of(apk),
        "sizeBytes": size,
        "minSdk": overrides.get("min_sdk") or min_sdk,
        "releaseNotes": notes.strip(),
    }
    validate(manifest)
    return manifest


def validate(manifest: dict) -> None:
    if set(manifest) != MANIFEST_KEYS:
        raise SystemExit(f"Manifest-Felder falsch: {sorted(set(manifest) ^ MANIFEST_KEYS)}")
    if manifest["schemaVersion"] != 1:
        raise SystemExit("schemaVersion muss 1 sein")
    if manifest["packageName"] != PACKAGE_NAME:
        raise SystemExit("packageName falsch")
    if not isinstance(manifest["versionCode"], int) or manifest["versionCode"] <= 0:
        raise SystemExit("versionCode unplausibel")
    if not re.fullmatch(r"[a-f0-9]{64}", manifest["sha256"]):
        raise SystemExit("sha256 muss 64 Hex-Zeichen (klein) haben")
    if not isinstance(manifest["sizeBytes"], int) or not 0 < manifest["sizeBytes"] <= MAX_APK_BYTES:
        raise SystemExit("sizeBytes unplausibel")
    if not 26 <= manifest["minSdk"] <= 35:
        raise SystemExit("minSdk außerhalb des unterstützten Bereichs")
    if manifest["apkUrl"] != APK_ASSET_URL:
        raise SystemExit("apkUrl muss die feste Release-Asset-URL sein")
    if not 1 <= len(manifest["versionName"]) <= 80:
        raise SystemExit("versionName unplausibel")
    if len(manifest["releaseNotes"]) > 4096:
        raise SystemExit("releaseNotes länger als 4096 Zeichen")


def verify(apk: Path, manifest_path: Path) -> int:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    validate(manifest)
    actual = sha256_of(apk)
    size = apk.stat().st_size
    problems = []
    if actual != manifest["sha256"]:
        problems.append(f"sha256: Manifest {manifest['sha256']} != APK {actual}")
    if size != manifest["sizeBytes"]:
        problems.append(f"sizeBytes: Manifest {manifest['sizeBytes']} != APK {size}")
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print(f"OK: {manifest_path} passt zu {apk} ({size} Bytes, sha256 {actual})")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apk", required=True, type=Path, help="signierte APK")
    parser.add_argument("--output", type=Path, help="Ziel für update.json")
    parser.add_argument("--release-notes", type=Path, help="Datei mit den Änderungsnotizen")
    parser.add_argument("--verify", type=Path, help="bestehendes update.json gegen die APK prüfen")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--version-name")
    parser.add_argument("--min-sdk", type=int)
    args = parser.parse_args()

    if not args.apk.is_file():
        raise SystemExit(f"APK nicht gefunden: {args.apk}")
    if args.verify:
        return verify(args.apk, args.verify)
    if not args.output:
        raise SystemExit("--output fehlt")
    notes = args.release_notes.read_text(encoding="utf-8") if args.release_notes else ""
    manifest = build_manifest(
        args.apk,
        notes,
        {"version_code": args.version_code, "version_name": args.version_name, "min_sdk": args.min_sdk},
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"geschrieben: {args.output}")
    print(json.dumps(manifest, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
