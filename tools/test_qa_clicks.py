import unittest
from qa_clicks import click_until


class ClickTests(unittest.TestCase):
    def setup_clock(self):
        self.now=0
        self.events=[]
        self.acks=0
    def sleep(self,seconds):self.now+=seconds
    def key(self,*args):self.events.append(args)
    def ack(self):self.acks+=1
    def run_clicks(self,test,seconds=15):
        return click_until(self.key,test,self.ack,seconds,
                           clock=lambda:self.now,sleep=self.sleep)

    def test_ready_shot_uses_one_click_and_releases(self):
        self.setup_clock()
        self.assertEqual(self.run_clicks(lambda:True),1)
        self.assertEqual(self.events,[('mousedown','1'),('mouseup','1')])
        self.assertEqual(self.acks,0)

    def test_early_failed_click_is_released_before_retry(self):
        self.setup_clock()
        self.assertEqual(self.run_clicks(lambda:self.acks>0),2)
        self.assertEqual(self.events,[('mousedown','1'),('mouseup','1'),
                                     ('mousedown','1'),('mouseup','1')])

    def test_never_fired_is_still_a_failure_and_bounded(self):
        self.setup_clock()
        with self.assertRaisesRegex(AssertionError,'timed out'):
            self.run_clicks(lambda:False,10)
        self.assertLessEqual(self.now,10)
        self.assertEqual(self.events[-1],('mouseup','1'))

    def test_observation_exception_does_not_leave_mouse_held(self):
        self.setup_clock()
        def broken():raise RuntimeError('client exited')
        with self.assertRaisesRegex(RuntimeError,'client exited'):self.run_clicks(broken)
        self.assertEqual(self.events[-1],('mouseup','1'))
