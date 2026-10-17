"""Bounded real mouse clicks for semiautomatic weapons; never mutate game state."""
import time


def click_until(key, test, acknowledge_release, seconds=75, *,
                clock=time.monotonic, sleep=time.sleep):
    deadline=clock()+seconds
    attempts=0
    try:
        while clock()<deadline:
            key('mousedown','1');attempts+=1
            hold_until=min(deadline,clock()+4)
            while clock()<hold_until:
                if test():return attempts
                sleep(.25)
            key('mouseup','1')
            if clock()>=deadline:break
            # Let the real client receive/process the release before another
            # click. A held failed semiautomatic shot cannot clear fireLatch.
            acknowledge_release()
            sleep(min(1,max(0,deadline-clock())))
        raise AssertionError('semiautomatic firing timed out')
    finally:
        key('mouseup','1')
