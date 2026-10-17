from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from install_qa_client import install_server


class InstallerRetryTests(unittest.TestCase):
    def run_fixture(self, messages):
        def run(*args, **kwargs):
            message=messages.pop(0)
            kwargs['stdout'].write(message)
            return SimpleNamespace(returncode=0 if message=='OK' else 1)
        return run

    def test_download_failure_retries_and_keeps_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            log=Path(folder)/'install.log'
            with patch('install_qa_client.subprocess.run',side_effect=self.run_fixture(['These libraries failed to download','OK'])) as run, patch('install_qa_client.time.sleep'):
                install_server('installer.jar','server',log)
            self.assertEqual(run.call_count,2)
            self.assertIn('failed to download',log.read_text())
            self.assertIn('attempt 2/3',log.read_text())

    def test_non_download_failure_is_not_retried(self):
        with tempfile.TemporaryDirectory() as folder:
            with patch('install_qa_client.subprocess.run',side_effect=self.run_fixture(['Unrelated Java failure'])) as run:
                with self.assertRaises(subprocess.CalledProcessError):
                    install_server('installer.jar','server',Path(folder)/'install.log')
            self.assertEqual(run.call_count,1)

    def test_download_retry_is_bounded(self):
        with tempfile.TemporaryDirectory() as folder:
            with patch('install_qa_client.subprocess.run',side_effect=self.run_fixture(['These libraries failed to download']*3)) as run, patch('install_qa_client.time.sleep'):
                with self.assertRaises(subprocess.CalledProcessError):
                    install_server('installer.jar','server',Path(folder)/'install.log')
            self.assertEqual(run.call_count,3)
