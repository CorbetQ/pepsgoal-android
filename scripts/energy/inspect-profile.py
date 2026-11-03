#!/usr/bin/env python3
"""Record APK identity and merged profile presence; presence is not proof of app-specific coverage."""
import hashlib
import json
import sys
from pathlib import Path
from zipfile import ZipFile

apk = Path(sys.argv[1])
with ZipFile(apk) as archive:
    profiles = {n: archive.getinfo(n).file_size for n in archive.namelist() if n.startswith("assets/dexopt/")}
print(json.dumps({"apk": apk.name, "sha256": hashlib.file_digest(apk.open("rb"), "sha256").hexdigest(), "profiles": profiles}, indent=2))
