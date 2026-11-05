#!/usr/bin/env python3
"""Import exactly the raster revisions accepted by the independent visual review."""
import hashlib
import argparse
import json
from pathlib import Path
import shutil
import struct

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/backgrounds"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--design", type=Path, default=Path("docs/design/background-reboot"),
                        help="Review directory relative to the repository")
    design = (ROOT / parser.parse_args().design).resolve()
    design.relative_to(ROOT)
    catalogue = set(json.loads((ASSETS / "catalog.json").read_text())["backgrounds"])
    references = json.loads((design / "references.json").read_text())["backgrounds"]
    accepted = json.loads((design / "accepted.json").read_text())["backgrounds"]
    imported = []
    for key, review in accepted.items():
        if review.get("renderer") == "native":
            continue
        assert key in catalogue and key not in {"plain", "custom"}, key
        assert review["verdict"] == "accepted", key
        source = Path(review["master"])
        assert source.name == review["revision"] + ".png" and source.parent.name == key, source
        raw = source.read_bytes()
        assert raw[:8] == b"\x89PNG\r\n\x1a\n", source
        width, height = struct.unpack(">II", raw[16:24])
        assert 256 <= width <= 2048 and 256 <= height <= 2048, source
        assert len(raw) <= 6 * 1024 * 1024, source
        refs = references[key]["references"]
        assert 1 <= len(refs) <= 10, key
        target = ASSETS / key / "art"
        target.mkdir(exist_ok=True)
        destination = target / "artwork.png"
        digest = hashlib.sha256(raw).hexdigest()
        if not destination.exists() or hashlib.sha256(destination.read_bytes()).hexdigest() != digest:
            shutil.copyfile(source, destination)
        brief = design / "briefs" / f"{key}.md"
        if not brief.is_file():
            brief = design / "brief.md"
        assert brief.is_file(), brief
        manifest = {
            "id": key, "revision": review["revision"], "file": "artwork.png",
            "sha256": digest, "width": width, "height": height,
            "generator": "image_gen.imagegen", "originalIllustration": True,
            "referenceSources": [r["sourceUrl"] for r in refs],
            "brief": str(brief.relative_to(ROOT)),
            "review": "accepted", "reviewScope": review["scope"],
        }
        (target / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
        imported.append(key)
    print(f"{len(imported)} accepted illustrations imported: {', '.join(imported)}")


if __name__ == "__main__":
    main()
