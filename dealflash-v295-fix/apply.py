#!/usr/bin/env python3
"""v2.9.5 overlay. Unlike the earlier str.replace patches this NEVER silently no-ops:
every replaced file must match the exact v2.9.4 base (sha256) and every new file must not exist yet."""
import hashlib, pathlib, shutil, sys
root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "dealflash_build_min")
here = pathlib.Path(__file__).parent / "files"
BASE = {
  "app/src/main/java/com/dealflash/app/DataQuality.java": "a964ddb0af12488ddcaba1ca37d3db94c03128c42619c32d28f7504c5ea19cf2",
  "app/src/main/java/com/dealflash/app/DealFlashMessagingService.java": "178261765318f5a9a9f730600dbac77ac971f289465197f832dcfca56aa3454d",
  "app/src/main/java/com/dealflash/app/HistoryStore.java": "a53f898622aebbf5e78f0c77f6bdfbc2f264a312ddf5e7d20bf7e4f95ab9cf64",
  "app/src/main/java/com/dealflash/app/HotDealStore.java": "ff487d4f1b15f9c5b1231e70c4fa30aaf32904f8df81dc9b510d13a279b90dd6",
  "app/src/main/java/com/dealflash/app/MainActivity.java": "ad0529d4e4fb79a8b0328d6b261ee9fd13e2145cae986ae410419fff9bf46cb5",
  "app/src/main/java/com/dealflash/app/ProductUX.java": "91d33eb01f33b535d8f639799ae44f61c329ffe64786b2a681b65fd8dede298f",
  "app/src/main/java/com/dealflash/app/ServerSync.java": "fe7d93a2681b8320951ca067b60da7269cdcd3806b27231917172768cfa90c13",
  "app/build.gradle": "a39412c1e79267ec0c5f8fbcbcecfa4f59c52755679574231e88fb84fd9c7e0b"
}
NEW = [
  "app/src/main/java/com/dealflash/app/DealsRepository.java",
  "app/src/main/java/com/dealflash/app/FeedFilter.java",
  "app/src/main/java/com/dealflash/app/ServerDealsParser.java",
  "app/src/main/java/com/dealflash/app/SyncDiag.java"
]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
bad = [f"{rel}: base sha {sha(root/rel)[:12]} != expected {want[:12]}" for rel, want in BASE.items() if not (root/rel).exists() or sha(root/rel) != want]
bad += [f"{rel}: already exists" for rel in NEW if (root/rel).exists()]
if bad:
    raise SystemExit("v2.9.5 overlay refused - base tree is not the v2.9.4 build:\n  " + "\n  ".join(bad))
for rel in list(BASE) + NEW:
    if rel == "app/build.gradle":
        continue
    shutil.copyfile(here/rel, root/rel)
g = root/"app/build.gradle"; t = g.read_text()
if "versionCode 45" not in t or "versionName '2.9.4'" not in t: raise SystemExit("unexpected version in build.gradle")
g.write_text(t.replace("versionCode 45", "versionCode 46").replace("versionName '2.9.4'", "versionName '2.9.5'"))
print("v2.9.5 overlay applied:", len(BASE) - 1, "files replaced,", len(NEW), "added")
