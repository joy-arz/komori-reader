import unittest
from release_notes import render


class ReleaseNotesTest(unittest.TestCase):
    def test_missing_or_link_only_notes_cannot_ship(self):
        for body in ("# Changes", "## 0.8.5\n- https://example.invalid/one\n- [Compare](https://example.invalid/two)", "## 0.8.5\nhttps://example.invalid/compare", "## 0.8.5\n- One\n- Two\nValidation: pending"):
            with self.assertRaises(ValueError):
                render(body, "0.8.5")

    def test_full_history_is_embedded_for_the_backfill_release(self):
        changelog = "\n".join(f"## {v}\n- Fix for {v}\n- Tests for {v}\n" for v in ("0.8.5", "0.8.4", "0.8.3"))
        notes = render(changelog, "0.8.5")
        for v in ("0.8.5", "0.8.4", "0.8.3"):
            self.assertIn(f"Fix for {v}", notes)
            self.assertIn(f"Tests for {v}", notes)
        self.assertNotIn("/compare/", notes)
