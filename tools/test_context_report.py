import unittest
from report_context import render

class ContextReportTest(unittest.TestCase):
    def test_green_cases_do_not_hide_process_failure(self):
        text=render(dict(runOutcome=dict(status='FAIL',error='native crash'),expectedCases=1,
                         cases=[dict(id='test',status='PASS',method='fired')]))
        self.assertIn('Process outcome: FAIL',text)
        self.assertIn('native crash',text)
        self.assertIn('not full acceptance',text)

    def test_missing_outcome_is_not_success(self):
        text=render(dict(expectedCases=35,cases=[]))
        self.assertIn('outcome: UNKNOWN',text)
        self.assertIn('0/35',text)

if __name__=='__main__':unittest.main()
