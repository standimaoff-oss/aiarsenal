import subprocess
import tempfile
from pathlib import Path
import unittest
from publish_evidence import BRANCH, publish


class PublishEvidenceTest(unittest.TestCase):
    def test_checkout_and_index_unchanged(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); remote=root/'remote.git';repo=root/'checkout';repo.mkdir()
            def git(*args):
                return subprocess.check_output(['git',*args],cwd=repo,text=True,stderr=subprocess.PIPE).strip()
            git('init','--bare','--initial-branch='+BRANCH,str(remote))
            git('init','--initial-branch='+BRANCH)
            git('config','user.name','Evidence Test');git('config','user.email','test@example.invalid')
            (repo/'source.txt').write_text('tested source\n')
            git('add','source.txt');git('commit','-m','fixture')
            git('remote','add','origin',str(remote));git('push','-u','origin',BRANCH)
            head=git('rev-parse','HEAD')
            (repo/'source.txt').write_text('staged change\n');git('add','source.txt')
            (repo/'source.txt').write_text('unstaged change\n')
            staged=git('diff','--cached');dirty=git('diff')
            (repo/'reports').mkdir();(repo/'reports/test.json').write_text('{"result":"FAIL"}\n')
            (repo/'shots/aiarsenal/run-test').mkdir(parents=True)
            (repo/'shots/aiarsenal/run-test/frame.png').write_bytes(b'fixture')
            first=publish(['reports/test.json','shots/aiarsenal/run-test'],repo)
            self.assertEqual(git('rev-parse','HEAD'),head)
            self.assertEqual(git('diff','--cached'),staged)
            self.assertEqual(git('diff'),dirty)
            self.assertEqual(git('--git-dir='+str(remote),'show',BRANCH+':source.txt'),'tested source')
            self.assertEqual(git('--git-dir='+str(remote),'show',BRANCH+':reports/test.json'),'{"result":"FAIL"}')
            self.assertEqual(publish(['reports/test.json'],repo),first)

    def test_only_evidence_files_allowed(self):
        with tempfile.TemporaryDirectory() as temp:
            repo=Path(temp);(repo/'source.java').write_text('source')
            for paths in [[],['source.java'],['../outside']]:
                with self.assertRaises(ValueError):publish(paths,repo)
