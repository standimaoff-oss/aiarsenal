"""Regression for Minecraft RCON's one-request-per-read implementation."""
import select
import socket
import threading
import unittest
import rcon


class RconTests(unittest.TestCase):
    def exchange(self, chunks):
        errors = []
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            listener.listen()

            def serve():
                try:
                    with listener.accept()[0] as peer:
                        peer.settimeout(2)
                        self.assertEqual(rcon._read(peer), (1, rcon.AUTH, 'test'))
                        peer.sendall(rcon._pkt(1, 2, ''))
                        self.assertEqual(rcon._read(peer), (2, rcon.EXEC, 'list'))
                        # Sending EXEC and a marker back-to-back can coalesce into one TCP read.
                        # Minecraft rejects that combined request. Demand a response barrier.
                        self.assertFalse(select.select([peer], [], [], .05)[0],
                                         'marker sent before the command response')
                        peer.sendall(rcon._pkt(2, rcon.RESPONSE, chunks[0]))
                        self.assertEqual(rcon._read(peer), (3, rcon.RESPONSE, ''))
                        for chunk in chunks[1:]:
                            peer.sendall(rcon._pkt(2, rcon.RESPONSE, chunk))
                        peer.sendall(rcon._pkt(3, rcon.RESPONSE, 'Unknown request 0'))
                except BaseException as error:
                    errors.append(error)

            worker = threading.Thread(target=serve, daemon=True)
            worker.start()
            try:
                answer = rcon.run('127.0.0.1', listener.getsockname()[1], 'test', 'list', 2)
                self.assertEqual(answer, ''.join(chunks))
            finally:
                worker.join(3)
                self.assertFalse(worker.is_alive())
                if errors:
                    raise errors[0]

    def test_single_response(self):
        self.exchange(['Test passed'])

    def test_multiple_response_packets(self):
        self.exchange(['first ', 'second ', 'third'])

    def test_empty_command_response(self):
        self.exchange([''])


if __name__ == '__main__':
    unittest.main()
