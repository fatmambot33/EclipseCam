#!/usr/bin/env python3
"""Regression tests for verify-localization.py."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify-localization.py")
SPEC = importlib.util.spec_from_file_location("verify_localization", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def write_strings(path: Path, values: dict[str, str]) -> None:
    """Write a minimal Android strings.xml fixture."""
    body = "\n".join(f'    <string name="{name}">{value}</string>' for name, value in values.items())
    path.write_text(f"<resources>\n{body}\n</resources>\n", encoding="utf-8")


class LocalizationVerifierTest(unittest.TestCase):
    def test_matching_resources_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            english = root / "en.xml"
            french = root / "fr.xml"
            write_strings(english, {"title": "Ready", "count": "%1$d of %2$d"})
            write_strings(french, {"title": "Prêt", "count": "%1$d sur %2$d"})

            self.assertEqual([], MODULE.validate_resources(english, french))

    def test_matching_resources_across_multiple_files_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            english = root / "values"
            french = root / "values-fr"
            english.mkdir()
            french.mkdir()
            write_strings(english / "strings.xml", {"title": "Ready"})
            write_strings(english / "camera.xml", {"camera_error": "Camera unavailable"})
            write_strings(french / "strings.xml", {"title": "Prêt"})
            write_strings(french / "camera.xml", {"camera_error": "Caméra indisponible"})

            self.assertEqual([], MODULE.validate_resources(english, french))

    def test_duplicate_resource_across_files_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            english = root / "values"
            french = root / "values-fr"
            english.mkdir()
            french.mkdir()
            write_strings(english / "strings.xml", {"title": "Ready"})
            write_strings(english / "extra.xml", {"title": "Still ready"})
            write_strings(french / "strings.xml", {"title": "Prêt"})

            errors = MODULE.validate_resources(english, french)
            self.assertEqual(1, len(errors))
            self.assertIn("duplicate string resources: title", errors[0])

    def test_missing_translation_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            english = root / "en.xml"
            french = root / "fr.xml"
            write_strings(english, {"title": "Ready", "detail": "Local only"})
            write_strings(french, {"title": "Prêt"})

            self.assertIn(
                "French resources missing: detail",
                MODULE.validate_resources(english, french),
            )

    def test_blank_translation_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            english = root / "en.xml"
            french = root / "fr.xml"
            write_strings(english, {"title": "Ready", "detail": "Local only"})
            write_strings(french, {"title": "Prêt", "detail": "   "})

            self.assertIn(
                "French resources blank: detail",
                MODULE.validate_resources(english, french),
            )

    def test_blank_english_source_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            english = root / "en.xml"
            french = root / "fr.xml"
            write_strings(english, {"title": ""})
            write_strings(french, {"title": "Titre"})

            self.assertIn(
                "English resources blank: title",
                MODULE.validate_resources(english, french),
            )

    def test_formatter_mismatch_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            english = root / "en.xml"
            french = root / "fr.xml"
            write_strings(english, {"count": "%1$d selected • %2$d missing"})
            write_strings(french, {"count": "%1$d sélectionnées"})

            errors = MODULE.validate_resources(english, french)
            self.assertEqual(1, len(errors))
            self.assertTrue(errors[0].startswith("Formatter mismatch for count:"))

    def test_duplicate_resource_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            english = root / "en.xml"
            french = root / "fr.xml"
            english.write_text(
                '<resources><string name="title">A</string><string name="title">B</string></resources>',
                encoding="utf-8",
            )
            write_strings(french, {"title": "Titre"})

            errors = MODULE.validate_resources(english, french)
            self.assertEqual(1, len(errors))
            self.assertIn("duplicate string resources: title", errors[0])


if __name__ == "__main__":
    unittest.main()
