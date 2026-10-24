"""Verify that a debug APK contains both requested implementation classes."""
import sys
import zipfile

expected = {
    b'Ldev/ragnarok/fenrir/dialog/directauth/DirectAuthIdVKPresenter;',
    b'Ldev/ragnarok/fenrir/fragment/comments/TopicCommentsPager;',
    b'Ldev/ragnarok/fenrir/fragment/comments/CommentsPresenter;',
    b'fireTopicJump',
}
found = set()
with zipfile.ZipFile(sys.argv[1]) as apk:
    assert apk.testzip() is None, 'Corrupt APK entry'
    for name in apk.namelist():
        if name.startswith('classes') and name.endswith('.dex'):
            data = apk.read(name)
            found.update(descriptor for descriptor in expected if descriptor in data)
assert found == expected, 'APK missing implementations: ' + repr(expected - found)
print('APK contains authentication, topic pager and direct-navigation implementations (debug DEX check)')
