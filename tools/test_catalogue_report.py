import json
from pathlib import Path
import tempfile
import unittest
from report_catalogue import render


class LedgerTests(unittest.TestCase):
    def fixture(self, folder, rows):
        (folder/'catalogue-inventory.json').write_text(json.dumps({'items':[
            {'id':'aiarsenal:a','class':'example.Item'}, {'id':'aiarsenal:b','class':'example.Item'}],
            'freeFallBombs':['air:TEST']}))
        (folder/'catalogue-gameplay.json').write_text(json.dumps({'items':rows,'bombs':[]}))

    def test_missing_cases_and_smoke_are_not_functional_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            self.fixture(folder,[{'id':'aiarsenal:a','smoke':'PASS'}])
            text=render(folder)
            self.assertIn('PASS | NOT_VERIFIED',text)
            self.assertIn('NOT_RUN | NOT_VERIFIED',text)
            self.assertIn('`air:TEST` | NOT_RUN',text)
            self.assertNotIn('100%',text)

    def test_duplicate_records_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            self.fixture(folder,[{'id':'aiarsenal:a'},{'id':'aiarsenal:a'}])
            with self.assertRaises(AssertionError):
                render(folder)

    def test_global_failure_is_not_hidden_by_green_items(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp)
            self.fixture(folder,[{'id':'aiarsenal:a','smoke':'PASS'},{'id':'aiarsenal:b','smoke':'PASS'}])
            path=folder/'catalogue-gameplay.json';result=json.loads(path.read_text())
            result['runOutcome']={'status':'FAIL','error':'Failed to store chunk'}
            path.write_text(json.dumps(result))
            text=render(folder)
            self.assertIn('PASS=2',text)
            self.assertIn('"status": "FAIL"',text)
            self.assertIn('Failed to store chunk',text)
