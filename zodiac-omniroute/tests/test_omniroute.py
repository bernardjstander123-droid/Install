import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from tools.omniroute import load_manifest, assemble, check_program

ROOT = Path(__file__).resolve().parents[1]

class ManifestTests(unittest.TestCase):
    def test_valid_manifest(self):
        d = load_manifest()
        self.assertEqual(d['video']['duration_seconds'], 15)
        self.assertEqual(sum(s['duration_seconds'] for s in d['shots']), 15)
        self.assertEqual(d['canon']['awakening_age'], 13)
        self.assertIn('scorpio', d['canon'])
        self.assertIn('pisces', d['canon'])

    def test_bad_duration_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            m = json.loads((ROOT/'episode_01.json').read_text(encoding='utf-8'))
            m['shots'][0]['duration_seconds'] = 6
            f = Path(d)/'bad.json'
            f.write_text(json.dumps(m))
            with self.assertRaises(ValueError):
                load_manifest(f)

    def test_bad_age_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            m = json.loads((ROOT/'episode_01.json').read_text(encoding='utf-8'))
            m['canon']['awakening_age'] = 18
            f = Path(d)/'bad.json'
            f.write_text(json.dumps(m))
            with self.assertRaises(ValueError):
                load_manifest(f)

    @unittest.skipUnless(check_program('ffmpeg') and check_program('ffprobe'), 'requires ffmpeg')
    def test_assembly_of_synthetic_clips(self):
        # This tests composition ONLY; it is not a model-generated scene.
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            clips = []
            for index, color in enumerate(('red','green','blue')):
                clip = root / f'sample{index}.mp4'
                subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y',
                    '-f','lavfi','-i',f'color=c={color}:s=144x256:r=12:d=5',
                    '-c:v','libx264','-pix_fmt','yuv420p',str(clip)],check=True)
                clips.append(clip)
            m = load_manifest()
            m['video']['width'], m['video']['height'],m['video']['fps'] = 144,256,12
            out = root / 'result.mp4'
            assemble(m,clips,out)
            probe = subprocess.run(['ffprobe','-v','error','-show_entries','format=duration',
                    '-of','default=noprint_wrappers=1:nokey=1',str(out)],
                    text=True,capture_output=True,check=True)
            self.assertAlmostEqual(float(probe.stdout),15,delta=.15)
            self.assertGreater(out.stat().st_size,1000)

if __name__ == '__main__':
    unittest.main()
