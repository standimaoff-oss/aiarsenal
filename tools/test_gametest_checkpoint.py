import gzip
import hashlib
from pathlib import Path
import tempfile
import unittest
from gametest_checkpoint import preserve


class GameTestCheckpointTest(unittest.TestCase):
    def test_lossless_failure_with_diagnostics(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp);raw=b'run\nMIRV failed! age=105 present=false\n1 required tests failed :(\n'
            (p/'run.log').write_bytes(raw)
            summary=preserve(p/'run.log',p/'out','source-sha')
            self.assertEqual(gzip.decompress((p/'out/gametest-early.log.gz').read_bytes()),raw)
            self.assertEqual(summary['sha256'],hashlib.sha256(raw).hexdigest())
            self.assertEqual(summary['failures'],['MIRV failed! age=105 present=false'])
            self.assertEqual(summary['totals'],['1 required tests failed :('])
            self.assertEqual(summary['source'],'source-sha')

    def test_truncated_log_cannot_claim_pass(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp);(p/'run.log').write_bytes(b'starting\xff')
            summary=preserve(p/'run.log',p/'out','sha')
            self.assertEqual(summary['failures'],[])
            self.assertEqual(summary['totals'],[])
            self.assertNotIn('status',summary)

    def test_missing_log_is_not_an_empty_success(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(FileNotFoundError):
                preserve(Path(temp)/'missing',Path(temp)/'out','sha')
