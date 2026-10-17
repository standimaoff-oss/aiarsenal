"""Real X11-window + PulseAudio monitor capture; never a rendered/mock gameplay video."""
import json
import os
from pathlib import Path
import signal
import subprocess
import time


def audio_metrics(audio):
    """Native interleaved channels: never downmix (ffmpeg's matrix can add +3 dB)."""
    import numpy as np
    samples = np.frombuffer(audio, dtype='<f4')
    assert len(samples) and np.isfinite(samples).all(), 'invalid gameplay audio'
    return {'audio_rms': float(np.sqrt(np.mean(samples * samples))),
            'audio_peak': float(np.max(np.abs(samples)))}


class Recording:
    def __init__(self, game, name, window=None):
        self.game = game
        if not hasattr(game, "AUDIO_FAILURES"):
            game.AUDIO_FAILURES = []
        self.name = name
        self.process = None
        if os.environ.get('QA_RECORD') != '1':
            return
        folder = game.OUT / 'videos'
        folder.mkdir(parents=True, exist_ok=True)
        self.path = folder / (name + '.mp4')
        self.log = (game.LOGS / ('record-' + name + '.log')).open('w')
        window = window or subprocess.check_output(['xdotool', 'search', '--name', 'Minecraft'], text=True).splitlines()[-1]
        # XGetImage(window) fails for an unmapped/occluded GLFW window after opening a second client.
        # Capture the actual visible X11 rectangle instead; bring it fully onto the virtual screen first.
        game.key('windowmap', window)
        game.key('windowmove', window, '0', '0')
        game.key('windowraise', window)
        game.key('windowfocus', '--sync', window)
        geometry = dict(line.split('=', 1) for line in subprocess.check_output(
            ['xdotool', 'getwindowgeometry', '--shell', window], text=True).splitlines())
        size = geometry['WIDTH'] + 'x' + geometry['HEIGHT']
        display = os.environ.get('DISPLAY', ':99') + '+' + geometry['X'] + ',' + geometry['Y']
        self.process = subprocess.Popen(['ffmpeg', '-nostdin', '-y', '-hide_banner',
            '-thread_queue_size', '1024', '-f', 'x11grab', '-framerate', '20',
            '-video_size', size, '-i', display,
            '-thread_queue_size', '1024', '-f', 'pulse', '-fragment_size', '4096',
            '-sample_rate', '48000', '-i', 'qa.monitor',
            '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '24', '-pix_fmt', 'yuv420p',
            '-threads', '1', '-vsync', '0', '-c:a', 'aac', '-b:a', '128k', '-movflags', '+faststart', str(self.path)],
            stdout=self.log, stderr=subprocess.STDOUT, start_new_session=True)
        game.PROCESSES.append(self.process)
        self.start = time.monotonic()
        self.reload_at = None
        time.sleep(.7)
        if self.process.poll() is not None:
            raise RuntimeError('recording failed: ' + name)

    def reload(self):
        if self.process:
            self.reload_at = time.monotonic() - self.start

    def stop(self):
        if not self.process:
            return
        os.killpg(self.process.pid, signal.SIGINT)
        code = self.process.wait(timeout=30)
        self.log.close()
        assert code in (0, 255), (self.name, code)
        probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_streams',
            '-show_format', '-of', 'json', str(self.path)], text=True))
        assert {'audio', 'video'} <= {s['codec_type'] for s in probe['streams']}
        assert float(probe['format']['duration']) > 5
        # Decode the ACTUAL game recording to verify audible, non-clipped output, not just an OGG resource.
        audio = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(self.path),
            '-vn', '-f', 'f32le', '-'])
        metrics = audio_metrics(audio)
        audio_stream = next(s for s in probe['streams'] if s['codec_type'] == 'audio')
        report = {'video': self.path.name, 'duration': float(probe['format']['duration']),
                  'reload_seconds': self.reload_at, **metrics,
                  'audio_channels': audio_stream['channels'],
                  'audio_sample_rate': int(audio_stream['sample_rate']),
                  'audio_measurement': 'native-rate decoded float PCM, max over all channels; no downmix/resampling',
                  'capture': 'Visible X11 Minecraft window rectangle + PulseAudio qa.monitor; continuous, not frame synthesis'}
        (self.game.LOGS / ('video-' + self.name + '.json')).write_text(json.dumps(report, indent=2) + '\n')
        # Save diagnostics even on failure. Never attenuate/limit the recording to pass QA.
        passed = metrics['audio_rms'] > .0001 and .002 < metrics['audio_peak'] < .99
        if not passed:
            self.game.AUDIO_FAILURES.append((self.name, metrics))
        # Defer the failure until ALL visual checks are captured; the final client gate still fails.

        if self.reload_at is not None:
            # Separate source frames for agent inspection. NOT labelled F2 screenshots.
            frames = self.game.OUT / 'video-review' / self.name
            frames.mkdir(parents=True, exist_ok=True)
            for i, offset in enumerate((.15, .6, 1.1, 1.7, 2.3, 3.2)):
                subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', str(self.reload_at + offset),
                    '-i', str(self.path), '-frames:v', '1', str(frames / f'{i+1:02}.png')], check=True)
        print('CONTINUOUS VIDEO/AUDIO ' + ('PASS' if passed else 'FAIL'), report, flush=True)
