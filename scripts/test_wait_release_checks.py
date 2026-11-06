import unittest

from wait_release_checks import RERUN_GRACE_SECONDS, evaluate_run


def run(status, conclusion="", attempt=1):
    return {
        "status": status,
        "conclusion": conclusion,
        "url": "https://example.invalid/run",
        "attempt": attempt,
    }


class WaitReleaseChecksTest(unittest.TestCase):
    def test_failed_attempt_waits_for_re_run(self):
        failures = {}
        state, _ = evaluate_run(
            "device-checks.yml", run("completed", "failure", 1), 100.0, failures
        )
        self.assertEqual("wait", state)
        state, _ = evaluate_run(
            "device-checks.yml", run("in_progress", "", 2), 130.0, failures
        )
        self.assertEqual("wait", state)
        self.assertNotIn("device-checks.yml", failures)
        state, _ = evaluate_run(
            "device-checks.yml", run("completed", "success", 2), 160.0, failures
        )
        self.assertEqual("verified", state)

    def test_persistent_failure_blocks_after_grace(self):
        failures = {}
        evaluate_run(
            "device-checks.yml", run("completed", "failure", 1), 100.0, failures
        )
        state, message = evaluate_run(
            "device-checks.yml",
            run("completed", "failure", 1),
            100.0 + RERUN_GRACE_SECONDS,
            failures,
        )
        self.assertEqual("blocked", state)
        self.assertIn("failure", message)

    def test_missing_or_running_checks_wait(self):
        failures = {}
        state, _ = evaluate_run("android.yml", None, 0.0, failures)
        self.assertEqual("wait", state)
        state, _ = evaluate_run("android.yml", run("queued"), 0.0, failures)
        self.assertEqual("wait", state)

    def test_success_is_verified(self):
        state, message = evaluate_run(
            "android.yml", run("completed", "success"), 0.0, {}
        )
        self.assertEqual("verified", state)
        self.assertIn("Verified android.yml", message)


if __name__ == "__main__":
    unittest.main()
