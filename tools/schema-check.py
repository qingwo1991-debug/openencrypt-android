#!/usr/bin/env python3
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
schema_path = ROOT / "schemas" / "config.schema.json"
example_path = ROOT / "schemas" / "config.example.json"

schema = json.loads(schema_path.read_text(encoding="utf-8"))
example = json.loads(example_path.read_text(encoding="utf-8"))

required_top = set(schema.get("required", []))
missing = sorted(required_top - set(example.keys()))
if missing:
    print("missing required top-level keys in example:", ", ".join(missing))
    sys.exit(1)

# Lightweight field constraints aligned with current schema.
checks = []
checks.append((1 <= example["openlist"]["port"] <= 65535, "openlist.port out of range"))
checks.append((1 <= example["gateway"]["port"] <= 65535, "gateway.port out of range"))
checks.append((example["openlist"]["port"] != example["gateway"]["port"], "openlist.port conflicts with gateway.port"))
checks.append((500 <= example["webdav"]["header_timeout_ms"] <= 30000, "webdav.header_timeout_ms out of range"))
checks.append((
    example["update"]["channel"] == "stable",
    "update.channel must be stable"
))

errors = [msg for ok, msg in checks if not ok]
if errors:
    for e in errors:
        print(e)
    sys.exit(1)

print("schema-check passed")
