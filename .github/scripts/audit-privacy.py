#!/usr/bin/env python3
"""Fail CI when EclipseCam privacy-sensitive runtime behavior drifts unexpectedly."""

from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
GRADLE = ROOT / "app/build.gradle.kts"
SOURCE_ROOT = ROOT / "app/src/main/java"
EVIDENCE = ROOT / "build/privacy-audit/evidence.txt"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

EXPECTED_PERMISSIONS = {
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.CAMERA",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_CAMERA",
    "android.permission.INTERNET",
}

BANNED_DEPENDENCY_MARKERS = {
    "firebase-analytics",
    "google-analytics",
    "appsflyer",
    "adjust-android",
    "amplitude",
    "mixpanel",
    "facebook-android-sdk",
    "sentry-android",
}

BANNED_NETWORK_CLIENT_MARKERS = {
    "OkHttpClient(",
    "Retrofit.Builder(",
    "HttpURLConnection",
    "java.net.URL(",
    "ktor.client",
}

ALLOWED_URL_LITERAL_FILES = {
    pathlib.Path("app/src/main/java/com/fatmambo33/eclipsecam/map/MapArchitecture.kt"),
}


def fail(errors: list[str]) -> None:
    for error in errors:
        print(f"privacy-audit: ERROR: {error}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    errors: list[str] = []
    root = ET.parse(MANIFEST).getroot()
    permissions = {
        node.attrib[f"{ANDROID_NS}name"]
        for node in root.findall("uses-permission")
        if f"{ANDROID_NS}name" in node.attrib
    }
    unexpected = permissions - EXPECTED_PERMISSIONS
    missing = EXPECTED_PERMISSIONS - permissions
    if unexpected:
        errors.append(f"unexpected manifest permissions: {sorted(unexpected)}")
    if missing:
        errors.append(f"expected permissions disappeared without audit update: {sorted(missing)}")

    application = root.find("application")
    if application is None:
        errors.append("manifest has no application element")
    else:
        if application.attrib.get(f"{ANDROID_NS}allowBackup") != "false":
            errors.append("android:allowBackup must remain false")
        if application.attrib.get(f"{ANDROID_NS}usesCleartextTraffic") != "false":
            errors.append("android:usesCleartextTraffic must remain false")
        for component in list(application.findall("service")) + list(application.findall("provider")):
            if component.attrib.get(f"{ANDROID_NS}exported") != "false":
                errors.append(
                    f"privacy-sensitive component must not be exported: "
                    f"{component.attrib.get(f'{ANDROID_NS}name', '<unnamed>')}"
                )

    gradle = GRADLE.read_text(encoding="utf-8").lower()
    found_banned_deps = sorted(marker for marker in BANNED_DEPENDENCY_MARKERS if marker in gradle)
    if found_banned_deps:
        errors.append(f"analytics/tracking dependency markers found: {found_banned_deps}")

    url_pattern = re.compile(r"https?://")
    url_literal_files: list[str] = []
    network_client_files: list[str] = []
    for path in sorted(SOURCE_ROOT.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        relative = path.relative_to(ROOT)
        if url_pattern.search(text):
            url_literal_files.append(str(relative))
            if relative not in ALLOWED_URL_LITERAL_FILES:
                errors.append(f"unreviewed runtime URL literal in {relative}")
        if any(marker in text for marker in BANNED_NETWORK_CLIENT_MARKERS):
            network_client_files.append(str(relative))
            errors.append(f"direct network client usage requires privacy review: {relative}")

    EVIDENCE.parent.mkdir(parents=True, exist_ok=True)
    EVIDENCE.write_text(
        "\n".join(
            [
                "EclipseCam runtime privacy audit",
                f"permissions={','.join(sorted(permissions))}",
                f"url_literal_files={','.join(url_literal_files) or '<none>'}",
                f"direct_network_client_files={','.join(network_client_files) or '<none>'}",
                "analytics_tracking_dependencies=<none>",
                "allowBackup=false",
                "usesCleartextTraffic=false",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    if errors:
        fail(errors)
    print(EVIDENCE.read_text(encoding="utf-8"), end="")


if __name__ == "__main__":
    main()
