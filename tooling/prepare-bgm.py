#!/usr/bin/env python3
"""Fetch checksum-pinned Spleeter ONNX weights into generated APK assets."""
import argparse
import hashlib
import json
import pathlib
import tarfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=pathlib.Path, default=ROOT / '.cache/bgm')
    args = parser.parse_args()
    lock = json.loads((ROOT / 'tooling/bgm-lock.json').read_text())
    assets = ROOT / 'app/src/main/assets/bgm'
    assets.mkdir(parents=True, exist_ok=True)
    def verified(path, digest):
        return path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == digest
    if all(verified(assets / name, sha) for name, sha in lock['models'].items()):
        print('Verified bundled BGM models', flush=True)
        return
    args.cache.mkdir(parents=True, exist_ok=True)
    archive = args.cache / 'models.tar.bz2'
    if not verified(archive, lock['sha256']):
        temporary = archive.with_suffix('.download')
        try:
            with urllib.request.urlopen(lock['url'], timeout=60) as response, temporary.open('wb') as out:
                count = 0
                while block := response.read(1024 * 1024):
                    count += len(block)
                    if count > 50_000_000:
                        raise RuntimeError('Model archive exceeds expected limit')
                    out.write(block)
            if not verified(temporary, lock['sha256']):
                raise RuntimeError('Model archive checksum mismatch')
            temporary.replace(archive)
        finally:
            temporary.unlink(missing_ok=True)
    with tarfile.open(archive) as tar:
        for name, sha in lock['models'].items():
            members = [item for item in tar.getmembers() if item.isfile() and pathlib.PurePosixPath(item.name).name == name]
            if len(members) != 1 or members[0].size > 25_000_000:
                raise RuntimeError('Unexpected model archive contents')
            payload = tar.extractfile(members[0]).read()
            if hashlib.sha256(payload).hexdigest() != sha:
                raise RuntimeError('Model checksum mismatch: ' + name)
            target = assets / name
            temporary = target.with_suffix('.tmp')
            temporary.write_bytes(payload)
            temporary.replace(target)
    print('Verified and prepared bundled BGM models', flush=True)

if __name__ == '__main__':
    main()
