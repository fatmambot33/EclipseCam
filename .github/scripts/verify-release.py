#!/usr/bin/env python3
"""Verify EclipseCam release inputs and credential-free AAB evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
EVIDENCE_DIR = ROOT / "build/release-verification"
EVIDENCE = EVIDENCE_DIR / "evidence.txt"
METADATA = EVIDENCE_DIR / "metadata.json"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
GRADLE = ROOT / "app/build.gradle.kts"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

FORBIDDEN_SUFFIXES = {
    ".jks",
    ".keystore",
    ".p12",
    ".pfx",
    ".pem",
    ".key",
    ".der",
}
FORBIDDEN_BASENAMES = {
    "google-services.json",
    "keystore.properties",
    "signing.properties",
}
FORBIDDEN_NAME_PATTERNS = (
    re.compile(r"service[-_]?account.*\.json$", re.IGNORECASE),
    re.compile(r"credentials.*\.json$", re.IGNORECASE),
)
SECRET_PATTERNS = {
    "private-key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "google-api-key": re.compile(r"AIza[0-9A-Za-z_-]{35}"),
    "github-token": re.compile(r"gh[pousr]_[0-9A-Za-z]{30,255}"),
    "aws-access-key": re.compile(r"AKIA[0-9A-Z]{16}"),
}


def run(*args: str) -> str:
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()


def tracked_files() -> list[pathlib.Path]:
    output = run("git", "ls-files", "-z")
    return [pathlib.Path(value) for value in output.split("\0") if value]


def forbidden_path_reason(path: pathlib.Path) -> str | None:
    lower_name = path.name.lower()
    if path.suffix.lower() in FORBIDDEN_SUFFIXES:
        return f"signing/credential extension {path.suffix.lower()}"
    if lower_name in FORBIDDEN_BASENAMES:
        return f"forbidden credential filename {path.name}"
    if any(pattern.fullmatch(path.name) for pattern in FORBIDDEN_NAME_PATTERNS):
        return f"forbidden credential filename {path.name}"
    return None


def secret_findings(text: str) -> list[str]:
    findings = [name for name, pattern in SECRET_PATTERNS.items() if pattern.search(text)]
    if (
        re.search(r'"type"\s*:\s*"service_account"', text)
        and re.search(r'"private_key"\s*:', text)
    ):
        findings.append("google-service-account-private-key")
    return findings


def scan_source() -> None:
    errors: list[str] = []
    files = tracked_files()
    for relative in files:
        reason = forbidden_path_reason(relative)
        if reason:
            errors.append(f"{relative}: {reason}")
            continue
        path = ROOT / relative
        try:
            data = path.read_bytes()
        except OSError as exc:
            errors.append(f"{relative}: cannot read tracked file: {exc}")
            continue
        if b"\x00" in data:
            continue
        text = data.decode("utf-8", errors="ignore")
        for finding in secret_findings(text):
            errors.append(f"{relative}: detected {finding}")

    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    lines = [
        "EclipseCam release verification",
        f"tracked_files_scanned={len(files)}",
        "tracked_signing_material=none",
        "tracked_secret_signatures=none",
        "verification_mode=credential-free",
    ]
    EVIDENCE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    if errors:
        for error in errors:
            print(f"release-verification: ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
    print(EVIDENCE.read_text(encoding="utf-8"), end="")


def gradle_value(text: str, key: str) -> str:
    match = re.search(rf"\b{re.escape(key)}\s*=\s*(?:\"([^\"]+)\"|(\d+))", text)
    if not match:
        raise ValueError(f"could not resolve {key} from app/build.gradle.kts")
    return match.group(1) or match.group(2)


def provider_default(text: str, variable: str) -> str:
    match = re.search(
        rf"val\s+{re.escape(variable)}\s*=.*?\.orElse\(\"([^\"]+)\"\)",
        text,
        flags=re.DOTALL,
    )
    if not match:
        raise ValueError(f"could not resolve default for {variable} from app/build.gradle.kts")
    return match.group(1)


def inspect_artifact(aab: pathlib.Path) -> None:
    if not aab.is_file():
        raise SystemExit(f"release-verification: ERROR: AAB not found: {aab}")

    gradle = GRADLE.read_text(encoding="utf-8")
    manifest_root = ET.parse(MANIFEST).getroot()
    permissions = sorted(
        node.attrib[f"{ANDROID_NS}name"]
        for node in manifest_root.findall("uses-permission")
        if f"{ANDROID_NS}name" in node.attrib
    )

    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(aab) as archive:
        names = archive.namelist()
        signature_entries = [
            name
            for name in names
            if name.upper().startswith("META-INF/")
            and name.upper().endswith((".RSA", ".DSA", ".EC", ".SF"))
        ]
        if signature_entries:
            raise SystemExit(
                "release-verification: ERROR: credential-free AAB unexpectedly contains "
                f"signature entries: {signature_entries}"
            )
        if "base/manifest/AndroidManifest.xml" not in names:
            raise SystemExit("release-verification: ERROR: AAB base manifest is missing")
        contents_file = EVIDENCE_DIR / "aab-contents.txt"
        contents_file.write_text("\n".join(sorted(names)) + "\n", encoding="utf-8")

    sha256 = hashlib.sha256(aab.read_bytes()).hexdigest()
    metadata = {
        "artifact": str(aab.relative_to(ROOT)),
        "sha256": sha256,
        "bytes": aab.stat().st_size,
        "applicationId": gradle_value(gradle, "applicationId"),
        "minSdk": int(gradle_value(gradle, "minSdk")),
        "targetSdk": int(gradle_value(gradle, "targetSdk")),
        "compileSdk": int(gradle_value(gradle, "compileSdk")),
        "defaultVersionCode": int(provider_default(gradle, "ciVersionCode")),
        "defaultVersionName": provider_default(gradle, "ciVersionName"),
        "permissions": permissions,
        "signed": False,
        "verificationMode": "credential-free unsigned release",
    }
    METADATA.write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    with EVIDENCE.open("a", encoding="utf-8") as handle:
        handle.write(f"release_aab={metadata['artifact']}\n")
        handle.write(f"release_aab_sha256={sha256}\n")
        handle.write(f"release_aab_bytes={metadata['bytes']}\n")
        handle.write("release_aab_signature=unsigned-as-required\n")
        handle.write(f"application_id={metadata['applicationId']}\n")
        handle.write(f"version_code={metadata['defaultVersionCode']}\n")
        handle.write(f"version_name={metadata['defaultVersionName']}\n")
        handle.write(f"min_sdk={metadata['minSdk']}\n")
        handle.write(f"target_sdk={metadata['targetSdk']}\n")
        handle.write(f"compile_sdk={metadata['compileSdk']}\n")
        handle.write(f"permissions={','.join(permissions)}\n")
    print(METADATA.read_text(encoding="utf-8"), end="")


def self_test() -> None:
    assert forbidden_path_reason(pathlib.Path("upload.jks")) is not None
    assert forbidden_path_reason(pathlib.Path("secrets/service_account.json")) is not None
    assert forbidden_path_reason(pathlib.Path("docs/release.md")) is None
    private_key_sample = "-----BEGIN " + "PRIVATE KEY-----"
    assert secret_findings(private_key_sample) == ["private-key"]
    assert "google-api-key" in secret_findings("AIza" + "A" * 35)
    service_account = json.dumps(
        {"type": "service_" + "account", "private_" + "key": "placeholder"}
    )
    assert "google-service-account-private-key" in secret_findings(service_account)
    assert secret_findings("PLAY_SERVICE_ACCOUNT_JSON=${{ secrets.VALUE }}") == []
    gradle_sample = 'val ciVersionCode = provider.orElse("42")\nval ciVersionName = provider.orElse("1.2.3")'
    assert provider_default(gradle_sample, "ciVersionCode") == "42"
    assert provider_default(gradle_sample, "ciVersionName") == "1.2.3"
    print("release-verification self-test: PASS")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("self-test")
    subparsers.add_parser("scan-source")
    artifact = subparsers.add_parser("inspect-artifact")
    artifact.add_argument("aab", type=pathlib.Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.command == "self-test":
        self_test()
    elif args.command == "scan-source":
        scan_source()
    else:
        inspect_artifact((ROOT / args.aab).resolve())


if __name__ == "__main__":
    main()
