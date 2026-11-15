#!/usr/bin/env python3
"""Zodiac OmniRoute: free/self-hosted AI video prompt planning and CPU assembly.

Does not pretend GitHub Actions has a GPU. Generate clips in WanGP or ComfyUI
on a GPU, then assemble them deterministically using FFmpeg.
"""
import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'episode_01.json'


def load_manifest(path=MANIFEST):
    with open(path, encoding='utf-8') as stream:
        data = json.load(stream)
    video = data.get('video', {})
    shots = data.get('shots', [])
    if data.get('schema_version') != 1:
        raise ValueError('Unsupported manifest schema_version')
    if not shots or len(set(s['id'] for s in shots)) != len(shots):
        raise ValueError('Shots must have unique ids and cannot be empty')
    current = 0
    for shot in shots:
        if shot['start_second'] != current:
            raise ValueError('Non-contiguous shot timing')
        if not isinstance(shot['duration_seconds'], int) or shot['duration_seconds'] <= 0:
            raise ValueError('Shot duration must be positive integer seconds')
        if len(shot.get('prompt', '').strip()) < 20:
            raise ValueError('Every shot needs a descriptive prompt')
        current += shot['duration_seconds']
    if current != video.get('duration_seconds'):
        raise ValueError('Shots do not sum to the declared video duration')
    if not (video.get('width', 0) > 0 and video.get('height', 0) > 0 and video.get('fps', 0) > 0):
        raise ValueError('Video dimensions and frame rate must be positive')
    if data['canon'].get('awakening_age') != 13:
        raise ValueError('Continuity violated: awakening must be at 13')
    return data


def check_program(program):
    return shutil.which(program) is not None


def run(command):
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f'{command[0]} failed:\n{result.stderr[-3500:]}')
    return result.stdout


def has_audio(path):
    result = run(['ffprobe', '-v', 'error', '-select_streams', 'a:0', '-show_entries',
                  'stream=index', '-of', 'csv=p=0', str(path)])
    return bool(result.strip())


def normalize(path, dest, seconds, width, height, fps):
    # Audio is retained when a generated model clip contains it. Otherwise inject silence.
    source_audio = has_audio(path)
    command = ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(path)]
    if not source_audio:
        command += ['-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo']
    scale = (f'scale={width}:{height}:force_original_aspect_ratio=increase,'
             f'crop={width}:{height},setsar=1,fps={fps},'
             f'tpad=stop_mode=clone:stop_duration={seconds},'
             f'trim=duration={seconds},setpts=PTS-STARTPTS')
    command += ['-filter_complex', f'[0:v]{scale}[v]', '-map', '[v]',
                '-map', '0:a:0' if source_audio else '1:a:0',
                '-af', f'aresample=48000,apad,atrim=duration={seconds},asetpts=PTS-STARTPTS',
                '-t', str(seconds), '-c:v', 'libx264', '-preset', 'veryfast',
                '-crf', '20', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '128k',
                '-ar', '48000', '-ac', '2', '-movflags', '+faststart', str(dest)]
    run(command)


def assemble(manifest, clips, output):
    if not (check_program('ffmpeg') and check_program('ffprobe')):
        raise RuntimeError('FFmpeg + FFprobe are required')
    shots = manifest['shots']
    if len(clips) != len(shots):
        raise ValueError(f'Expected {len(shots)} clips, got {len(clips)}')
    for p in clips:
        if not Path(p).is_file():
            raise FileNotFoundError(str(p))
    vid = manifest['video']
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='zodiac-omni-') as tmp:
        paths = []
        for index, (clip, shot) in enumerate(zip(clips, shots)):
            local = Path(tmp) / f'normalized-{index:02d}.mp4'
            normalize(clip, local, shot['duration_seconds'], vid['width'],
                      vid['height'], vid['fps'])
            paths.append(local)
        args = ['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y']
        for p in paths:
            args += ['-i', str(p)]
        inputs = ''.join(f'[{i}:v:0][{i}:a:0]' for i in range(len(paths)))
        filt = f'{inputs}concat=n={len(paths)}:v=1:a=1[v][a]'
        args += ['-filter_complex', filt, '-map', '[v]', '-map', '[a]',
                 '-c:v', 'libx264', '-preset', 'medium', '-crf', '18',
                 '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '160k',
                 '-t', str(vid['duration_seconds']), '-movflags', '+faststart', str(output)]
        run(args)
    return output


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest', type=Path, default=MANIFEST)
    subs = p.add_subparsers(dest='action', required=True)
    subs.add_parser('validate', help='Check story timing and Scorpio continuity')
    subs.add_parser('prompts', help='Print ready-to-copy prompts for WanGP/ComfyUI')
    subs.add_parser('doctor', help='Check local rendering prerequisites')
    a = subs.add_parser('assemble', help='Join AI-generated shot clips into 15s vertical MP4')
    a.add_argument('--clips', nargs='+', required=True, type=Path)
    a.add_argument('--output', type=Path, default=ROOT / 'output' / 'episode_01.mp4')
    args = p.parse_args(argv)
    data = load_manifest(args.manifest)
    if args.action == 'validate':
        print(f'OK: {len(data["shots"])} shots, {data["video"]["duration_seconds"]} seconds; Scorpio canon intact')
    elif args.action == 'doctor':
        print('ffmpeg:', 'available' if check_program('ffmpeg') else 'MISSING')
        print('ffprobe:', 'available' if check_program('ffprobe') else 'MISSING')
        print('nvidia-smi:', 'available' if check_program('nvidia-smi') else 'not detected (AI model rendering not available here)')
        print('Render the AI clips separately on a GPU with WanGP/ComfyUI, then run assemble.')
    elif args.action == 'prompts':
        for shot in data['shots']:
            print(f'\n===== {shot["id"]} ({shot["duration_seconds"]}s) =====\n{shot["prompt"]}')
    elif args.action == 'assemble':
        print('Created:', assemble(data, args.clips, args.output))
    return 0

if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, RuntimeError, FileNotFoundError) as err:
        print('ERROR:', err, file=sys.stderr)
        sys.exit(1)
