#!/usr/bin/env python3
"""Validate a signed, optimized APK; write only public metadata."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tomllib
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
parser.add_argument("--build-tools", type=Path, required=True)
parser.add_argument("--mapping", type=Path, required=True)
parser.add_argument("--report", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
versions = tomllib.loads((root / "gradle/libs.versions.toml").read_text())["versions"]
pin = (root / ".github/fenrir-release-certificate.sha256").read_text().strip().lower()
assert re.fullmatch(r"[0-9a-f]{64}", pin), "Invalid signing certificate pin"


def run(name, *command):
    return subprocess.run([str(args.build_tools / name), *map(str, command)],
                          check=True, capture_output=True, text=True).stdout


certificates = run("apksigner", "verify", "--verbose", "--print-certs", args.apk)
digests = re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", certificates)
assert [digest.lower() for digest in digests] == [pin], "Unexpected signing identity/signer count"
run("zipalign", "-c", "-P", "16", "4", args.apk)
badging = run("aapt2", "dump", "badging", args.apk)
assert "package: name='dev.ragnarok.fenrir'" in badging, "Wrong package"
assert f"versionCode='{versions['appVersionCode']}'" in badging, "Wrong versionCode"
assert f"versionName='{versions['appVersionName']}'" in badging, "Wrong versionName"
assert "'es'" in next(line for line in badging.splitlines() if line.startswith("locales:")), "Spanish missing"
assert "application-debuggable" not in badging, "Debuggable release"
abis = versions["targetAbiDevelop" if versions["developerBuild"] == "true" else "targetAbiNotDevelop"].split(",")
with zipfile.ZipFile(args.apk) as apk:
    assert apk.testzip() is None, "Corrupt ZIP"
    for abi in abis:
        assert f"lib/{abi}/libfenrir_jni.so" in apk.namelist(), f"Native library missing: {abi}"
mapping = args.mapping.read_text()
assert "# compiler: R8" in mapping, "Missing R8 mapping"
for implementation in (
    "dialog.directauth.DirectAuthIdVKPresenter", "fragment.comments.TopicCommentsPager",
    "fragment.comments.CommentsPresenter", "settings.ModernAppearance",
):
    assert f"dev.ragnarok.fenrir.{implementation} -> " in mapping, f"Missing implementation: {implementation}"
report = {
    "apk": args.apk.name, "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
    "bytes": args.apk.stat().st_size, "package": "dev.ragnarok.fenrir",
    "versionCode": versions["appVersionCode"], "versionName": versions["appVersionName"],
    "certificate_sha256": pin, "abis": abis, "spanish": True,
    "r8": True, "zip_integrity": True, "zipalign": True,
    "signature_verification": certificates.splitlines(),
}
args.report.write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
