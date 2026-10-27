#!/usr/bin/env python3
"""Verify the published APK and refresh version-specific VirusTotal evidence."""
import argparse
from datetime import datetime, timezone
import hashlib
from html import escape
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen

REPO = 'joy-arz/komori-reader'
BEGIN = '<!-- VIRUSTOTAL:START -->'
END = '<!-- VIRUSTOTAL:END -->'
STATS = ('malicious', 'suspicious', 'undetected', 'harmless', 'timeout', 'confirmed-timeout', 'failure', 'type-unsupported')


def version(tag):
    if not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', tag):
        raise ValueError('Expected a version tag such as v0.8.5')
    return tag[1:]


def gh(*args):
    return subprocess.check_output(['gh', *args, '--repo', REPO], text=True, timeout=60)


def verify(apk, manifest, tag):
    name = version(tag)
    with apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    expected = f'https://github.com/{REPO}/releases/download/{tag}/komori-{name}.apk'
    if manifest.get('packageName') != 'app.komori.reader' or manifest.get('versionName') != name or manifest.get('apkUrl') != expected:
        raise ValueError('Release identity does not match the updater manifest')
    if not 0 < apk.stat().st_size <= 80 * 1024 * 1024 or manifest.get('size') != apk.stat().st_size or manifest.get('sha256') != digest:
        raise ValueError('Published APK checksum/size mismatch')
    return digest


class VirusTotal:
    def __init__(self, key):
        self.key = key
        self.last = None

    def request(self, route, body=None, content_type=None):
        if self.last is not None:
            time.sleep(max(0, 16 - (time.monotonic() - self.last)))
        self.last = time.monotonic()
        headers = {'x-apikey': self.key, 'Accept': 'application/json'}
        if content_type:
            headers['Content-Type'] = content_type
        url = route if route.startswith('https://') else 'https://www.virustotal.com/api/v3/' + route
        parsed = urlsplit(url)
        if parsed.scheme != 'https' or parsed.netloc != 'www.virustotal.com':
            raise ValueError('Unexpected VirusTotal request host')
        request = Request(url, data=body, headers=headers)
        with urlopen(request, timeout=45) as response:
            return json.load(response)

    def file(self, digest):
        return self.request('files/' + digest)

    def upload(self, apk):
        data = apk.read_bytes()
        if len(data) > 32 * 1024 * 1024:
            result = self.request('files/upload_url')
            upload_url = result['data']
            if not upload_url.startswith('https://www.virustotal.com/'):
                raise ValueError('Unexpected VirusTotal upload host')
            route = upload_url
        else:
            route = 'files'
        boundary = 'KomoriRelease' + hashlib.sha256(data).hexdigest()
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{apk.name}"\r\n'
                'Content-Type: application/vnd.android.package-archive\r\n\r\n').encode() + data + f'\r\n--{boundary}--\r\n'.encode()
        return self.request(route, body, 'multipart/form-data; boundary=' + boundary)['data']['id']


def scan(client, apk, digest, max_wait=300):
    try:
        report = client.file(digest)
    except HTTPError as error:
        if error.code != 404:
            raise
        error.close()
        analysis_id = client.upload(apk)
        deadline = time.monotonic() + max_wait
        while time.monotonic() < deadline:
            analysis = client.request('analyses/' + quote(analysis_id, safe=''))
            if analysis['data']['attributes']['status'] == 'completed':
                report = client.file(digest)
                break
        else:
            raise TimeoutError('VirusTotal analysis is still pending')
    attributes = report['data']['attributes']
    if attributes.get('sha256') != digest:
        raise ValueError('VirusTotal returned another file hash')
    stats = attributes['last_analysis_stats']
    if not stats or any(type(value) is not int or value < 0 for value in stats.values()):
        raise ValueError('Invalid VirusTotal analysis statistics')
    date = datetime.fromtimestamp(attributes['last_analysis_date'], timezone.utc).isoformat()
    return {key: stats.get(key, 0) for key in STATS}, date


def receipt(apk, manifest, tag, previous=None, client=None):
    digest = verify(apk, manifest, tag)
    result = dict(version=version(tag), sha256=digest, apk_size=apk.stat().st_size,
                  report_url=f'https://www.virustotal.com/gui/file/{digest}/detection',
                  recorded_at=datetime.now(timezone.utc).isoformat(), status='pending', source='not-scanned',
                  message='Automatic scanning needs the VIRUSTOTAL_API_KEY repository secret.')
    if previous and previous.get('sha256') == digest and previous.get('version') == result['version']:
        result = dict(previous)
    if client:
        try:
            stats, date = scan(client, apk, digest)
            result.update(status='completed', source='virustotal-api', stats=stats, recorded_at=date)
            result.pop('message', None)
        except (HTTPError, URLError, TimeoutError) as error:
            reason = f'HTTP {error.code}' if isinstance(error, HTTPError) else type(error).__name__
            if isinstance(error, HTTPError):
                error.close()
            result['message'] = f'Fresh API check unavailable ({reason}); existing same-hash evidence is retained, otherwise scan is pending.'
    return result


def summary(data):
    if data['status'] == 'screenshot':
        return f"{data['detections']} / {data['engines']} detections · maintainer screenshot"
    if data['status'] == 'completed':
        stats = data['stats']
        return f"{stats['malicious']} malicious · {stats['suspicious']} suspicious · {stats['undetected']} undetected"
    return 'Scan pending · no result claimed for this APK'


def svg(data):
    flagged = data.get('detections', 0) + data.get('stats', {}).get('malicious', 0) + data.get('stats', {}).get('suspicious', 0)
    color = '#EF7171' if flagged else '#D5FA39' if data['status'] != 'pending' else '#F5C969'
    text = [f"VirusTotal · Komori {data['version']}", summary(data), 'SHA-256: ' + data['sha256'], 'Recorded: ' + data['recorded_at']]
    lines = ''.join(f'<text x="28" y="{40 + index * 33}" fill="{color if index == 1 else "#F2F6EF"}" font-size="{20 if index < 2 else 13}">{escape(line)}</text>' for index, line in enumerate(text))
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="960" height="172" viewBox="0 0 960 172"><rect width="960" height="172" rx="14" fill="#0B100D"/><g font-family="monospace">{lines}</g></svg>\n'


def update_readme(text, data):
    if text.count(BEGIN) != 1 or text.count(END) != 1:
        raise ValueError('README requires one VirusTotal generated block')
    name = data['version']
    image = f'docs/security/virustotal/{name}.svg'
    body = f'''{BEGIN}
### VirusTotal scan · {name}

[![VirusTotal result for Komori {name}]({image})]({data['report_url']})

**{summary(data)}.** [Open this APK’s VirusTotal report]({data['report_url']}).

APK: `komori-{name}.apk` · SHA-256: `{data['sha256']}`.

This is a point-in-time result for this exact APK, separate from Google Play Protect’s installation checks.
'''
    if data.get('message'):
        body += '\n' + data['message'] + '\n'
    if data.get('screenshot'):
        body += f'''\n<details>
<summary>Original scan screenshot · {name} · {data.get('screenshot_recorded_at', data['recorded_at'])}</summary>

![Original VirusTotal scan screenshot for Komori {name}]({data['screenshot']})

</details>
'''
    body += END
    start, end = text.index(BEGIN), text.index(END) + len(END)
    text = text[:start] + body + text[end:]
    text = re.sub(r'release-[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?-A3E635', f'release-{name}-A3E635', text)
    text = re.sub(r'Komori [0-9]+\.[0-9]+\.[0-9]+ is available', f'Komori {name} is available', text)
    text = re.sub(r'### \[Download komori-[^\]]+\.apk\]\(https://github.com/joy-arz/komori-reader/releases/download/[^)]+\)',
                  f'### [Download komori-{name}.apk](https://github.com/{REPO}/releases/download/v{name}/komori-{name}.apk)', text)
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True)
    args = parser.parse_args()
    name = version(args.tag)
    folder = Path('docs/security/virustotal'); folder.mkdir(parents=True, exist_ok=True)
    record = folder / f'{name}.json'
    previous = json.loads(record.read_text()) if record.exists() else None
    with tempfile.TemporaryDirectory(prefix='komori-virustotal-') as directory:
        gh('release', 'download', args.tag, '--pattern', f'komori-{name}.apk', '--pattern', 'update.json', '--dir', directory)
        apk = Path(directory) / f'komori-{name}.apk'
        manifest = json.loads((Path(directory) / 'update.json').read_text())
        key = os.environ.get('VIRUSTOTAL_API_KEY')
        data = receipt(apk, manifest, args.tag, previous, VirusTotal(key) if key else None)
    record.write_text(json.dumps(data, indent=2) + '\n')
    (folder / f'{name}.svg').write_text(svg(data))
    latest = json.loads(gh('release', 'view', '--json', 'tagName,isPrerelease'))
    if latest['tagName'] == args.tag and not latest['isPrerelease']:
        readme = Path('README.md')
        readme.write_text(update_readme(readme.read_text(), data))
    print(f"Komori {name}: {summary(data)}")
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
            output.write(f"## VirusTotal · {name}\n\n{summary(data)}\n\n{data.get('message', '')}\n\n[Report]({data['report_url']})\n")


if __name__ == '__main__':
    main()
