from pathlib import Path
import tempfile
import unittest
from qa_log import tail_from

class LogOffsetsTests(unittest.TestCase):
    def test_cyrillic_before_marker_does_not_hide_acknowledgement(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'client.log'
            path.write_text('Шейдеры перезагружены.\n',encoding='utf-8')
            offset=path.stat().st_size
            with path.open('a',encoding='utf-8') as stream:
                stream.write('FX QA status: active=false\n')
            self.assertEqual(tail_from(path,offset),'FX QA status: active=false\n')
