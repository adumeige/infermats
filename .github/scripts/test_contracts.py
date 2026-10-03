"""Independent checks against pinned upstream verifier and documented Jev shapes."""
import json
from pathlib import Path
import unittest
from upstream.caption_verifier import CaptionVerifier

ROOT = Path(__file__).resolve().parents[2]

class ContractTests(unittest.TestCase):
    def test_ideogram_goldens_pass_upstream(self):
        for path in (ROOT / 'infermats-ideogram/src/test/resources').glob('*.json'):
            with self.subTest(path=path.name):
                self.assertEqual([], CaptionVerifier().verify_raw(path.read_text()))

    def test_jev_expected_fixtures(self):
        for path in (ROOT / 'infermats-jev/src/test/resources').glob('*.json'):
            with self.subTest(path=path.name):
                request = json.loads(path.read_text())
                self.assertEqual({'model', 'state', 'questions'}, set(request))
                self.assertIsInstance(request['model'], str)
                self.assertIsInstance(request['state'], (str, dict, list))
                for q in request['questions'].values():
                    self.assertIn(q['type'], ('choice', 'score', 'noul'))
                    self.assertIn('instructions', q)
                    self.assertTrue(q['instructions'] is None or isinstance(q['instructions'], (str, dict, list)))
                    if q['type'] == 'choice':
                        self.assertIsInstance(q['criteria'], dict)
                        self.assertLessEqual(len(q['criteria']), 255)
                        values = q['criteria'].values()
                    elif q['type'] == 'score':
                        self.assertIsInstance(q['criteria'], list)
                        self.assertTrue(2 <= len(q['criteria']) <= 10)
                        values = q['criteria']
                    else:
                        self.assertLessEqual(set(q.get('criteria', {})), {'true', 'false'})
                        values = q.get('criteria', {}).values()
                    for v in values:
                        self.assertTrue(v is None or isinstance(v, (str, dict, list)))
