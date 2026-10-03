"""Validate retained signed bundle and add its byte hashes to release provenance."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
from publish_github_packages import unpack

if __name__ == '__main__':
    bundle, manifest_path = map(Path, sys.argv[1:])
    manifest = json.loads(manifest_path.read_text())
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        artifacts = unpack(bundle, root, manifest['groupId'], manifest['version'])
        manifest['payloadSha256'] = {
            file.relative_to(root).as_posix(): hashlib.sha256(file.read_bytes()).hexdigest()
            for _, _, _, payloads in artifacts for file, _, _ in payloads
        }
    manifest['bundleSha256'] = hashlib.sha256(bundle.read_bytes()).hexdigest()
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
