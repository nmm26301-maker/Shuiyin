#!/usr/bin/env python3
"""Exercise the production release script without contacting or writing GitHub."""
import hashlib
import json
import os
import pathlib
import subprocess
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / 'tooling/publish-apk.sh'
SHA = '1' * 40


class ReleaseTests(unittest.TestCase):
    def scenario(self, case):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        folder = pathlib.Path(temp.name)
        (folder / 'app').mkdir()
        (folder / 'app/build.gradle').write_text("versionName '0.6.15'\n")
        (folder / 'apk').mkdir()
        (folder / 'apk/app-debug.apk').write_bytes(b'publication-fixture')
        fake = folder / 'gh'
        fake.write_text('''#!/usr/bin/env python3
import json,os,sys
with open('calls.jsonl','a') as out:out.write(json.dumps(sys.argv[1:])+'\\n')
if sys.argv[1]=='api':
 case=os.environ['CASE']
 if case in ('new','unauthorized'):
  print('HTTP 404' if case=='new' else 'HTTP 403',file=sys.stderr);sys.exit(1)
 print(os.environ['GITHUB_SHA'] if case=='same' else '2'*40)
''')
        fake.chmod(0o700)
        env = dict(os.environ, PATH=str(folder) + os.pathsep + os.environ['PATH'],
                   GITHUB_SHA=SHA, GITHUB_REPOSITORY='fixture/repo', CASE=case)
        result = subprocess.run(['bash', str(SCRIPT)], cwd=folder, env=env, capture_output=True, text=True)
        calls = [json.loads(x) for x in (folder / 'calls.jsonl').read_text().splitlines()]
        return folder, result, calls

    def test_new_prerelease_has_exact_commit_and_checksum(self):
        folder, result, calls = self.scenario('new')
        self.assertEqual(result.returncode, 0, result.stderr)
        create = calls[-1]
        self.assertEqual(create[:2], ['release', 'create'])
        self.assertIn('--prerelease', create)
        self.assertEqual(create[create.index('--target') + 1], SHA)
        self.assertEqual(create[create.index('--repo') + 1], 'fixture/repo')
        digest = (folder / 'Shuiyin-v0.6.15.apk.sha256').read_text().split()[0]
        self.assertEqual(digest, hashlib.sha256(b'publication-fixture').hexdigest())

    def test_same_commit_rerun_only_updates_its_assets(self):
        _, result, calls = self.scenario('same')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[-1][:2], ['release', 'upload'])
        self.assertIn('--clobber', calls[-1])

    def test_other_commit_is_never_overwritten(self):
        _, result, calls = self.scenario('other')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(calls), 1)

    def test_authorization_failure_does_not_try_to_create(self):
        _, result, calls = self.scenario('unauthorized')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(calls), 1)


if __name__ == '__main__':
    unittest.main()
