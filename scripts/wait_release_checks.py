#!/usr/bin/env python3
"""Require successful build and device checks for the exact release commit."""
import argparse
import json
import re
import subprocess
import time

WORKFLOWS = ("android.yml", "device-checks.yml")
POLL_SECONDS = 30
DEADLINE_SECONDS = 1200
RERUN_GRACE_SECONDS = 300


def latest_run(repo, workflow, commit):
    result = subprocess.run(
        ["gh", "run", "list", "--repo", repo, "--workflow", workflow,
         "--commit", commit, "--limit", "1",
         "--json", "status,conclusion,url,attempt"],
        capture_output=True, text=True, check=True, timeout=45,
    )
    runs = json.loads(result.stdout)
    return runs[0] if runs else None


def evaluate_run(workflow, run, now, failures_since):
    """Return (state, message) for one workflow.

    A failed attempt is not final: it may be re-run, so keep waiting until the
    grace window expires. A re-run clears the failure because its status is no
    longer completed.
    """
    if run is None:
        return "wait", f"Waiting for {workflow} on the release commit"
    attempt = run.get("attempt", 1)
    if run["status"] != "completed":
        failures_since.pop(workflow, None)
        return "wait", f"Waiting for {workflow} attempt {attempt}: {run['status']}"
    if run["conclusion"] == "success":
        failures_since.pop(workflow, None)
        return "verified", f"Verified {workflow} attempt {attempt}: {run['url']}"
    failed_at = failures_since.setdefault(workflow, now)
    remaining = int(RERUN_GRACE_SECONDS - (now - failed_at))
    if remaining <= 0:
        return "blocked", (
            f"Publication blocked: {workflow} attempt {attempt}: "
            f"{run['conclusion']} {run['url']}"
        )
    return "wait", (
        f"{workflow} attempt {attempt} concluded {run['conclusion']}; "
        f"waiting {remaining}s for a re-run"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--repo", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.commit):
        parser.error("Expected a full commit SHA")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repo):
        parser.error("Expected owner/repository")
    deadline = time.monotonic() + DEADLINE_SECONDS
    failures_since = {}
    while time.monotonic() < deadline:
        ready = True
        for workflow in WORKFLOWS:
            run = latest_run(args.repo, workflow, args.commit)
            state, message = evaluate_run(workflow, run, time.monotonic(), failures_since)
            print(message, flush=True)
            if state == "blocked":
                raise SystemExit(message)
            if state != "verified":
                ready = False
        if ready:
            return
        time.sleep(POLL_SECONDS)
    raise SystemExit("Publication blocked: exact-commit checks did not finish within 20 minutes")


if __name__ == "__main__":
    main()
