import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from urllib.error import HTTPError

from virustotal_release import BEGIN, END, receipt, scan, summary, svg, update_readme, verify, version


class EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.apk = Path(self.directory.name) / 'komori-0.8.5.apk'
        self.apk.write_bytes(b'APK test fixture')
        self.digest = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.manifest = dict(versionName='0.8.5', packageName='app.komori.reader', size=self.apk.stat().st_size,
                             sha256=self.digest, apkUrl='https://github.com/joy-arz/komori-reader/releases/download/v0.8.5/komori-0.8.5.apk')
        self.previous = dict(version='0.8.5', sha256=self.digest, apk_size=self.apk.stat().st_size,
                             status='screenshot', source='maintainer-screenshot', detections=0, engines=67,
                             recorded_at='2026-10-27', report_url='https://www.virustotal.com/gui/file/' + self.digest + '/detection',
                             screenshot='docs/security/virustotal/komori-0.8.5.png')

    def test_exact_apk_identity_is_required(self):
        self.assertEqual(self.digest, verify(self.apk, self.manifest, 'v0.8.5'))
        for field, value in [('sha256', '0' * 64), ('size', 1), ('packageName', 'wrong'), ('apkUrl', 'https://example.invalid/app.apk')]:
            with self.assertRaises(ValueError):
                verify(self.apk, dict(self.manifest, **{field: value}), 'v0.8.5')

    def test_same_hash_screenshot_is_retained_without_api(self):
        self.assertEqual(self.previous, receipt(self.apk, self.manifest, 'v0.8.5', self.previous))

    def test_another_hash_never_inherits_a_clean_result_or_screenshot(self):
        data = receipt(self.apk, self.manifest, 'v0.8.5', dict(self.previous, sha256='0' * 64))
        self.assertEqual('pending', data['status'])
        self.assertNotIn('screenshot', data)
        rendered = update_readme(BEGIN + '\nold scan\n' + END, data)
        self.assertNotIn('0 / 67', rendered)
        self.assertNotIn('komori-0.8.5.png', rendered)
        self.assertIn('Scan pending', rendered)

    def test_readme_replaces_only_marked_evidence_and_release_identity(self):
        text = '# Header\nrelease-0.8.4-A3E635\nKomori 0.8.4 is available\n' + BEGIN + '\nold scan\n' + END + '\nKeep this text'
        updated = update_readme(text, self.previous)
        self.assertIn('release-0.8.5-A3E635', updated)
        self.assertIn('Komori 0.8.5 is available', updated)
        self.assertIn('Original scan screenshot', updated)
        self.assertTrue(updated.endswith('Keep this text'))
        self.assertEqual(updated, update_readme(updated, self.previous))
        with self.assertRaises(ValueError):
            update_readme('Missing markers', self.previous)

    def test_unknown_apk_is_submitted_and_waits_for_completed_analysis(self):
        digest = self.digest
        class Client:
            calls = []
            def file(self, value):
                self.calls.append('file')
                if len(self.calls) == 1:
                    raise HTTPError('fixture', 404, 'missing', {}, None)
                return {'data': {'attributes': {'sha256': digest, 'last_analysis_stats': {'malicious': 0, 'suspicious': 1, 'undetected': 66}, 'last_analysis_date': 1793059200}}}
            def upload(self, apk):
                self.calls.append('upload'); return 'analysis:fixture'
            def request(self, route):
                self.calls.append(route)
                return {'data': {'attributes': {'status': 'completed'}}}
        client = Client()
        data = receipt(self.apk, self.manifest, 'v0.8.5', client=client)
        self.assertEqual('completed', data['status'])
        self.assertEqual(1, data['stats']['suspicious'])
        self.assertEqual(['file', 'upload', 'analyses/analysis%3Afixture', 'file'], client.calls)
        self.assertIn('#EF7171', svg(data))
        self.assertIn('1 suspicious', summary(data))

    def test_api_outage_remains_pending_without_fabricating_scan_results(self):
        class Client:
            def file(self, digest):
                raise HTTPError('fixture', 429, 'rate limited', {}, None)
        data = receipt(self.apk, self.manifest, 'v0.8.5', client=Client())
        self.assertEqual('pending', data['status'])
        self.assertNotIn('stats', data)
        self.assertIn('HTTP 429', data['message'])

    def test_pending_analysis_does_not_publish_a_clean_badge(self):
        class Client:
            def file(self, digest):
                raise HTTPError('fixture', 404, 'missing', {}, None)
            def upload(self, apk): return 'queued'
        with self.assertRaises(TimeoutError):
            scan(Client(), self.apk, self.digest, max_wait=0)

    def test_api_hash_mismatch_and_invalid_tags_fail_closed(self):
        class Client:
            def file(self, digest):
                return {'data': {'attributes': {'sha256': '0' * 64}}}
        with self.assertRaises(ValueError):
            receipt(self.apk, self.manifest, 'v0.8.5', client=Client())
        for tag in ('main', 'v../../tmp/x', 'v0.8.5; echo bad'):
            with self.assertRaises(ValueError): version(tag)
