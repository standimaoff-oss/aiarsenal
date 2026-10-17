import unittest
import numpy as np
from qa_recording import audio_metrics


class AudioCapture(unittest.TestCase):
    def test_native_stereo_peak_not_downmix_gain(self):
        stereo = np.full((1000, 2), .8, dtype='<f4')
        self.assertAlmostEqual(audio_metrics(stereo.tobytes())['audio_peak'], .8, places=6)

    def test_channel_overload_cannot_cancel(self):
        stereo = np.tile([1.05, -1.05], (1000, 1)).astype('<f4')
        self.assertGreater(audio_metrics(stereo.tobytes())['audio_peak'], 1)

    def test_invalid_audio_rejected(self):
        for data in (b'', np.array([np.nan], dtype='<f4').tobytes()):
            with self.assertRaises(AssertionError):
                audio_metrics(data)


if __name__ == '__main__':
    unittest.main()
