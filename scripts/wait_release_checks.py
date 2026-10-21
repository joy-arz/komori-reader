#!/usr/bin/env python3
"""Require successful build and device checks for the exact release commit."""
import argparse
import json
import re
import subprocess
import time

WORKFLOWS = ("android.yml", "device-checks.yml")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--repo", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.commit):
        parser.error("Expected a full commit SHA")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repo):
        parser.error("Expected owner/repository")
    deadline = time.monotonic() + 1200
    while time.monotonic() < deadline:
        ready = True
        for workflow in WORKFLOWS:
            result = subprocess.run(
                ["gh", "run", "list", "--repo", args.repo, "--workflow", workflow,
                 "--commit", args.commit, "--limit", "1", "--json", "status,conclusion,url"],
                capture_output=True, text=True, check=True, timeout=45,
            )
            runs = json.loads(result.stdout)
            if not runs or runs[0]["status"] != "completed":
                ready = False
                print(f"Waiting for {workflow} on {args.commit}", flush=True)
                continue
            run = runs[0]
            if run["conclusion"] != "success":
                raise SystemExit(f"Publication blocked: {workflow}: {run['conclusion']} {run['url']}")
            print(f"Verified {workflow}: {run['url']}", flush=True)
        if ready:
            return
        time.sleep(30)
    raise SystemExit("Publication blocked: exact-commit checks did not finish within 20 minutes")


if __name__ == "__main__":
    main()
