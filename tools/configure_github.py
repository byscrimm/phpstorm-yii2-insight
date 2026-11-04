#!/usr/bin/env python3
"""Preview supported repository settings; apply only with --apply and authenticated gh."""
import argparse
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parent.parent
REPO = "byscrimm/phpstorm-yii2-insight"


def api(endpoint, method="GET", body=None):
    command = ["gh", "api", "--method", method, endpoint]
    if body is not None:
        command += ["--input", "-"]
    result = subprocess.run(command, input=json.dumps(body) if body is not None else None,
                            text=True, capture_output=True, check=True)
    return json.loads(result.stdout) if result.stdout.strip() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verified-commit", required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.verified_commit):
        raise SystemExit("Provide the complete SHA of a successful CI run")
    repository = api("repos/" + REPO)
    if not repository.get("permissions", {}).get("admin"):
        raise SystemExit("Repository administration permission is required")
    runs = api(f"repos/{REPO}/commits/{args.verified_commit}/check-runs?per_page=100")["check_runs"]
    checks = []
    for required in ("Repository policy", "Build and verify"):
        matches = [run for run in runs if run["name"].split(" / ")[-1] == required
                   and run.get("app", {}).get("slug") == "github-actions"]
        if not matches:
            raise SystemExit("No GitHub Actions check found for " + required)
        latest = max(matches, key=lambda run: run["id"])
        if latest["conclusion"] != "success":
            raise SystemExit("Latest required check did not succeed: " + latest["name"])
        checks.append({"context": latest["name"], "app_id": latest["app"]["id"]})
    branch = api(f"repos/{REPO}/branches/master")
    if args.apply and branch["protected"]:
        raise SystemExit("Existing protection detected. Review and preserve its policy before updating it.")
    settings = {"description": (ROOT / ".github/about.txt").read_text().strip(),
                "has_issues": True, "allow_squash_merge": True, "allow_merge_commit": False,
                "allow_rebase_merge": False, "allow_auto_merge": False,
                "delete_branch_on_merge": True, "squash_merge_commit_title": "PR_TITLE",
                "squash_merge_commit_message": "PR_BODY"}
    protection = {"required_status_checks": {"strict": True, "checks": checks},
                  "enforce_admins": True, "restrictions": None,
                  "required_pull_request_reviews": {"required_approving_review_count": 0,
                                                    "require_code_owner_reviews": False,
                                                    "dismiss_stale_reviews": True},
                  "required_linear_history": True, "allow_force_pushes": False,
                  "allow_deletions": False, "required_conversation_resolution": True}
    print(json.dumps({"repository": REPO, "settings": settings, "protection": protection,
                      "private_vulnerability_reporting": "enable",
                      "mode": "apply" if args.apply else "preview"}, indent=2))
    if args.apply:
        api("repos/" + REPO, "PATCH", settings)
        api(f"repos/{REPO}/private-vulnerability-reporting", "PUT")
        api(f"repos/{REPO}/branches/master/protection", "PUT", protection)
        observed = api(f"repos/{REPO}/branches/master/protection")
        print(json.dumps({"observed_required_checks": observed["required_status_checks"]}, indent=2))


if __name__ == "__main__":
    main()
