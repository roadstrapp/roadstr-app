#!/usr/bin/env python3
import argparse
import hashlib
import json
import pathlib


def compact(value) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="Build or verify a bounded Roadstr offline manifest")
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--generated-at", required=True)
    parser.add_argument("--artifact", action="append", default=[], type=pathlib.Path)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    if args.verify_only:
        manifest = json.loads(args.output.read_text(encoding="utf-8"))
    else:
        artifacts = []
        for specification in args.artifact:
            item = json.loads(specification.read_text(encoding="utf-8"))
            file = pathlib.Path(item.pop("file"))
            payload = file.read_bytes()
            item["sizeBytes"] = len(payload)
            item.setdefault("installedSizeBytes", len(payload))
            item["sha256"] = hashlib.sha256(payload).hexdigest()
            artifacts.append(item)
        manifest = {"schemaVersion": 1, "generatedAt": args.generated_at, "artifacts": artifacts}
        args.output.write_text(compact(manifest), encoding="utf-8")
    encoded = compact(manifest).encode("utf-8")
    if len(encoded) > 512 * 1024 or len(manifest.get("artifacts", [])) > 200:
        raise ValueError("manifest exceeds app bounds")
    if manifest.get("schemaVersion") != 1:
        raise ValueError("unsupported schema")
    for item in manifest.get("artifacts", []):
        if not item.get("url", "").startswith("https://"):
            raise ValueError("artifact URL must use HTTPS")
        if len(item.get("sha256", "")) != 64:
            raise ValueError("artifact checksum is invalid")


if __name__ == "__main__":
    main()
