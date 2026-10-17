import copy
import unittest
from verify_alpha11_delivery import check_run,SOURCE


class DeliveryGateTest(unittest.TestCase):
    def good(self):
        names=['Static and protocol tests','Build release JAR','Minecraft GameTests','Gameplay',
               'Archive evidence and binary','Preserve incremental evidence even on failure','Draft']
        return dict(headSha=SOURCE,status='completed',conclusion='success',
                    jobs=[dict(steps=[dict(name=n,conclusion='success') for n in names])])

    def test_only_final_publication_failure_is_allowed(self):
        data=self.good();self.assertEqual(check_run(data,'Gameplay','Draft'),[])
        data['conclusion']='failure';data['jobs'][0]['steps'][-1]['conclusion']='failure'
        self.assertEqual(check_run(data,'Gameplay','Draft'),['Draft'])
        data['jobs'][0]['steps'][2]['conclusion']='failure'
        with self.assertRaises(AssertionError):check_run(data,'Gameplay','Draft')

    def test_pending_wrong_source_or_inconsistent_metadata_rejected(self):
        for field,value in [('status','in_progress'),('headSha','other'),('conclusion','failure')]:
            data=self.good();data[field]=value
            with self.assertRaises(AssertionError):check_run(data,'Gameplay','Draft')
        data=self.good();data['jobs'][0]['steps'][-1]['conclusion']='failure'
        with self.assertRaises(AssertionError):check_run(data,'Gameplay','Draft')

    def test_required_step_cannot_be_missing_or_skipped(self):
        data=self.good();data['jobs'][0]['steps'].pop(2)
        with self.assertRaises(AssertionError):check_run(data,'Gameplay','Draft')
        data=self.good();data['jobs'][0]['steps'][3]['conclusion']='skipped'
        with self.assertRaises(AssertionError):check_run(data,'Gameplay','Draft')
