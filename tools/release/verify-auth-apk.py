#!/usr/bin/env python3
"""Check an APK's embedded source identity against the actual Git commit, without VK data."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--expected-commit', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
assert re.fullmatch('[0-9a-f]{40}', args.expected_commit), 'Use the full commit SHA'
with zipfile.ZipFile(args.apk) as apk:
    assert apk.testzip() is None, 'Corrupt APK'
    assert 'assets/fenrir-build.json' in apk.namelist(), 'APK predates the authentication source identity'
    identity = json.loads(apk.read('assets/fenrir-build.json'))
    assert identity['commit'] == args.expected_commit, 'APK belongs to a different commit'
    expected_paths = {
        'dialog/directauth/DirectAuthIdVKPresenter.kt', 'dialog/directauth/VkIdDiagnostics.kt',
        'api/auth/VkIdSession.kt', 'api/auth/AuthFailurePolicy.kt', 'api/impl/AuthApi.kt',
        'api/services/IAuthService.kt', 'api/rest/SimplePostHttp.kt', 'util/OkHttp3LoggingInterceptor.kt',
    }
    prefix = 'app_fenrir/src/main/kotlin/dev/ragnarok/fenrir/'
    assert set(identity['auth_sources']) == {prefix + path for path in expected_paths}
    for path, digest in identity['auth_sources'].items():
        source = subprocess.check_output(['git', 'show', args.expected_commit + ':' + path], cwd=root)
        assert hashlib.sha256(source).hexdigest() == digest, 'Source differs from commit: ' + path
    dex = [apk.read(name) for name in apk.namelist() if re.fullmatch(r'classes\d*\.dex', name)]
    for name in ('dialog/directauth/DirectAuthIdVKPresenter', 'dialog/directauth/VkIdDiagnostics',
                 'api/auth/VkIdSession', 'api/auth/AuthFailurePolicy',
                 'fragment/comments/TopicCommentsPager', 'settings/ModernAppearance'):
        descriptor = ('Ldev/ragnarok/fenrir/' + name + ';').encode()
        assert any(descriptor in data for data in dex), 'Implementation absent from DEX: ' + name
    assert any(b'fireTopicJump' in data for data in dex), 'Advanced topic navigation absent'
print(json.dumps({'commit': identity['commit'], 'auth_sources_verified': len(identity['auth_sources']),
                  'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                  'authentication_and_modern_dex': True}, indent=2))
