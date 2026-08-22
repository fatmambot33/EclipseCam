#!/usr/bin/env python3
"""Verify EclipseCam's English/French Android string-resource contract."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENGLISH = ROOT / "app/src/main/res/values"
FRENCH = ROOT / "app/src/main/res/values-fr"
FORMAT_TOKEN = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]")


def resource_files(path: Path) -> list[Path]:
    """Return the XML resource files represented by a file or values directory."""
    if path.is_file():
        return [path]
    return sorted(path.glob("*.xml"))


def load_strings(path: Path) -> dict[str, str]:
    """Load translatable strings across resource XML files and reject duplicates."""
    elements: list[tuple[Path, ET.Element]] = []
    files = resource_files(path)
    if not files:
        raise OSError(f"{path}: no Android resource XML files found")

    for resource_file in files:
        root = ET.parse(resource_file).getroot()
        elements.extend(
            (resource_file, element)
            for element in root
            if element.tag == "string"
        )

    names = [element.attrib.get("name", "") for _, element in elements]
    duplicates = sorted(name for name, count in Counter(names).items() if count > 1)
    if duplicates:
        raise ValueError(f"{path}: duplicate string resources: {', '.join(duplicates)}")

    return {
        element.attrib["name"]: "".join(element.itertext())
        for _, element in elements
        if element.attrib.get("translatable", "true").lower() != "false"
    }


def format_signature(value: str) -> tuple[str, ...]:
    """Return the ordered Android formatter-token signature for one string."""
    return tuple(match.group(0) for match in FORMAT_TOKEN.finditer(value) if match.group(0) != "%%")


def validate_resources(english: Path = ENGLISH, french: Path = FRENCH) -> list[str]:
    """Return localization-contract violations without mutating the repository."""
    errors: list[str] = []
    try:
        en = load_strings(english)
        fr = load_strings(french)
    except (ET.ParseError, OSError, ValueError, KeyError) as error:
        return [str(error)]

    missing_fr = sorted(set(en) - set(fr))
    extra_fr = sorted(set(fr) - set(en))
    if missing_fr:
        errors.append("French resources missing: " + ", ".join(missing_fr))
    if extra_fr:
        errors.append("French-only resources without English source: " + ", ".join(extra_fr))

    blank_en = sorted(name for name, value in en.items() if not value.strip())
    blank_fr = sorted(name for name, value in fr.items() if not value.strip())
    if blank_en:
        errors.append("English resources blank: " + ", ".join(blank_en))
    if blank_fr:
        errors.append("French resources blank: " + ", ".join(blank_fr))

    for name in sorted(set(en) & set(fr)):
        en_signature = format_signature(en[name])
        fr_signature = format_signature(fr[name])
        if en_signature != fr_signature:
            errors.append(
                f"Formatter mismatch for {name}: English {en_signature!r}, French {fr_signature!r}"
            )
    return errors


def main() -> int:
    """Run the localization contract verifier."""
    errors = validate_resources()
    if errors:
        print("Localization resource contract failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    count = len(load_strings(ENGLISH))
    print(f"Localization resource contract passed for {count} English/French strings.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
