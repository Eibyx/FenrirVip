#!/usr/bin/env python3
"""One-time, owner-controlled release identity. Never run key generation in CI."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PIN = ROOT / ".github/fenrir-release-certificate.sha256"


def keytool():
    home = os.environ.get("JAVA_HOME")
    return str(Path(home) / "bin/keytool") if home else "keytool"


def init(directory):
    if directory.exists():
        raise SystemExit("Signing directory already exists. Reuse it with check/upload; never replace a release key.")
    if directory.is_relative_to(ROOT):
        raise SystemExit("Keep signing material outside the Git checkout.")
    directory.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".fenrir-signing-", dir=directory.parent) as temp:
        private = Path(temp)
        password = secrets.token_urlsafe(48)
        credentials = {"alias": "fenrirvip-release", "password": password}
        env = dict(os.environ, FENRIR_STORE_PASS=password)
        subprocess.run([
            keytool(), "-genkeypair", "-noprompt", "-storetype", "PKCS12",
            "-keystore", str(private / "fenrirvip-release.p12"),
            "-alias", credentials["alias"], "-keyalg", "RSA", "-keysize", "4096",
            "-sigalg", "SHA256withRSA", "-validity", "10000",
            "-dname", "CN=FenrirVip Release, O=FenrirVip",
            "-storepass:env", "FENRIR_STORE_PASS", "-keypass:env", "FENRIR_STORE_PASS",
        ], env=env, check=True, capture_output=True)
        (private / "credentials.json").write_text(json.dumps(credentials) + "\n")
        cert = export_certificate(private, credentials)
        (private / "certificate.der").write_bytes(cert)
        (private / "certificate.sha256").write_text(hashlib.sha256(cert).hexdigest() + "\n")
        for file in private.iterdir():
            file.chmod(0o600)
        # Private directory stays protected; a failed command never leaves a partial identity.
        private.rename(directory)
    print("Created ONE persistent identity. Securely back up the entire private directory before publication.")
    print("Public certificate SHA-256:", hashlib.sha256(cert).hexdigest())


def export_certificate(directory, credentials):
    return subprocess.run([
        keytool(), "-exportcert", "-keystore", str(directory / "fenrirvip-release.p12"),
        "-alias", credentials["alias"], "-storepass:env", "FENRIR_STORE_PASS",
    ], env=dict(os.environ, FENRIR_STORE_PASS=credentials["password"]),
        check=True, capture_output=True).stdout


def check(directory):
    if directory.is_relative_to(ROOT):
        raise SystemExit("Signing material must stay outside the checkout.")
    if directory.stat().st_mode & 0o077:
        raise SystemExit("Protect the signing directory with chmod 700.")
    for name in ("credentials.json", "fenrirvip-release.p12"):
        if (directory / name).stat().st_mode & 0o077:
            raise SystemExit("Protect private files with chmod 600.")
    credentials = json.loads((directory / "credentials.json").read_text())
    actual = hashlib.sha256(export_certificate(directory, credentials)).hexdigest()
    if actual != PIN.read_text().strip().lower():
        raise SystemExit("Certificate differs from the public repository pin. Do not publish.")
    print("Persistent signing certificate matches repository pin:", actual)
    return credentials


def upload(directory):
    credentials = check(directory)
    # The key endpoint is an authorization check, not a way around denied access.
    probe = subprocess.run([
        "gh", "api", "repos/Eibyx/FenrirVip/actions/secrets/public-key", "--silent",
    ], capture_output=True)
    if probe.returncode:
        raise SystemExit("GitHub denied access to Actions Secrets. No secrets were sent. Use an owner-authorized gh login on a trusted computer.")
    values = {
        "FENRIR_KEYSTORE_BASE64": base64.b64encode((directory / "fenrirvip-release.p12").read_bytes()),
        "FENRIR_KEYSTORE_PASSWORD": credentials["password"].encode(),
        "FENRIR_KEY_ALIAS": credentials["alias"].encode(),
        "FENRIR_KEY_PASSWORD": credentials["password"].encode(),
    }
    for name, value in values.items():
        result = subprocess.run([
            "gh", "secret", "set", name, "--repo", "Eibyx/FenrirVip",
        ], input=value, capture_output=True)
        if result.returncode:
            raise SystemExit(f"Could not set {name}. Check permissions and retry upload with the SAME directory.")
        print("Configured", name)


if __name__ == "__main__":
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("init", "check", "upload"))
    parser.add_argument("--directory", type=Path, required=True)
    args = parser.parse_args()
    try:
        {"init": init, "check": check, "upload": upload}[args.command](args.directory.resolve())
    except (OSError, ValueError, subprocess.CalledProcessError):
        # Never include subprocess arguments, credential contents or environment in errors.
        raise SystemExit("Signing operation failed. Private values were not printed; check paths, permissions and installed keytool/gh.")
