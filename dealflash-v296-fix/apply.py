#!/usr/bin/env python3
"""v2.9.6 overlay. Every replaced file must match the exact v2.9.5 base (sha256) and every new file must not exist yet; it never silently no-ops."""
import hashlib, pathlib, shutil, sys
root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "dealflash_build_min")
here = pathlib.Path(__file__).parent / "files"
BASE = {
  "app/src/main/java/com/dealflash/app/BrowserScanner.java": "e728b028fda15cab1743fb6c7038e816ca2bc5e5b6af65f31ab692af52ac0759",
  "app/src/main/java/com/dealflash/app/ServerDealsParser.java": "9811927ae72060a92383816a8f75eb5cf7e36dda40c8a58c45b1d34680db89ff",
  "app/src/main/java/com/dealflash/app/HistoryStore.java": "25d27e0e2bf4896bcc1f971c2086302cf5df156ed53ea04d6a23cbd36a37f69a",
  "app/src/main/java/com/dealflash/app/FeedFilter.java": "720fa23b9cf93b53dcc1b8134820535900df7890b8054833e2ac98da0ce5b127",
  "app/src/main/java/com/dealflash/app/MainActivity.java": "9e9a039cb659d7f197a80d8f82cb57802260080e17254252560fcfc475206e39",
  "app/src/main/java/com/dealflash/app/DealsRepository.java": "792f811f1b55ef29896c286bda077061f7ec2a0b03d138e529176b04dcba59c5",
  "app/src/main/java/com/dealflash/app/ServerSync.java": "4a8fd60a21bbf1cdf98c33ebfce12f7339aa5507e3954197085a74ae61e65f0a",
  "app/src/main/java/com/dealflash/app/AppLauncher.java": "c774dc0842cb2bd0c6ace83ed0dafff2c41722e19763ddac8f78e37a816f419b",
  "app/src/main/java/com/dealflash/app/DealFlashMessagingService.java": "277c572907b2c7db8f5b8cc787682a0da9d22484c228ad3efc7bbf8e55934d0a",
  "app/src/main/java/com/dealflash/app/DataMigration.java": "4c6f33a3d5a1547f6d5aef5bd146ccaa25b3594c1cfc4b374426d87270b31276",
  "app/src/main/java/com/dealflash/app/ProductUX.java": "a78590f914d890f525bf1b81878de057ddb4eca34342e7f8c91d366f9fb5af35",
  "app/src/main/java/com/dealflash/app/ProductImages.java": "32d197c62e9e9c9494606244e1ea1b32b47d49628fe31df9990c1575ba98f8b4",
  "app/src/main/java/com/dealflash/app/ProductIdentity.java": "1363cc45304c89ed4c0a279f2538d66d491501de5331bdc34a06b0cc29b76d7c",
  "app/src/main/java/com/dealflash/app/ProductLinks.java": "de09a1a66394759bbd95a9a6e81187f4542868a4013c7e2f7327f8b0632524da",
  "app/src/main/java/com/dealflash/app/HotDealStore.java": "0f7954af1c1b2fd673929f32ff7c4724757138b0e7da00d2e13964fcf6b1a9cc",
  "app/src/main/java/com/dealflash/app/DealPipeline.java": "e38dd953b5f4509282b25c09ab50d5eee7d369d41ca8bddd990e6647b39d368a",
  "app/build.gradle": "60e901b96246dcd0641a3695f88b0b2c5bda9f9a288693e3ee2631d91d99da0e"
}
NEW = [
  "app/src/main/java/com/dealflash/app/ServerIdentity.java",
  "app/src/main/java/com/dealflash/app/ImageSampling.java",
  "app/src/main/java/com/dealflash/app/EndedSync.java",
  "app/src/main/java/com/dealflash/app/FcntGuard.java",
  "app/src/main/java/com/dealflash/app/PriceCheck.java",
  "app/src/main/java/com/dealflash/app/PriceVerdict.java",
  "app/src/main/java/com/dealflash/app/PriceJudge.java"
]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
bad = [f"{rel}: base sha {sha(root/rel)[:12]} != expected {want[:12]}" for rel, want in BASE.items() if not (root/rel).exists() or sha(root/rel) != want]
bad += [f"{rel}: already exists" for rel in NEW if (root/rel).exists()]
if bad:
    raise SystemExit("v2.9.6 overlay refused - base tree is not the v2.9.5 build:\n  " + "\n  ".join(bad))
for rel in list(BASE) + NEW:
    if rel == "app/build.gradle":
        continue
    shutil.copyfile(here/rel, root/rel)
g = root/"app/build.gradle"; t = g.read_text()
if "versionCode 46" not in t or "versionName '2.9.5'" not in t: raise SystemExit("unexpected version in build.gradle")
g.write_text(t.replace("versionCode 46", "versionCode 57").replace("versionName '2.9.5'", "versionName '2.10.5'"))
print("v2.9.6 overlay applied:", len(BASE) - 1, "files replaced,", len(NEW), "added")
