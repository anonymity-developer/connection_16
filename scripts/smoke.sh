#!/usr/bin/env bash
set -Eeuo pipefail

base_url="${1:-http://localhost:8088}"
curl --fail --silent --show-error "${base_url%/}/" >/dev/null
curl --fail --silent --show-error "${base_url%/}/api/v1/meta" | python3 -c '
import json, sys
data = json.load(sys.stdin)
assert data["service"] == "TAG ME", data
assert data["stage"] == "bootstrap", data
assert [m["code"] for m in data["missions"]] == ["SHOW_ME", "SYNC_ME", "MAKE_A_WISH", "FAST_TAP", "SAY_SOMETHING"], data
assert "".join(m["letter"] for m in data["missions"]) == "SSAFY", data
print("TAG ME web → API smoke passed")
'
