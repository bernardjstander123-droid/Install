# Zodiac: The Awakening — Episode 1 / OmniRoute 🎬

A zero-API-credit **production pipeline**, not a claim that a free GPU exists on GitHub. This package contains a locked 15-second 9:16 script, coherent prompts for an open-source model, a deterministic FFmpeg video assembler, and tests. It does **not** contain model weights or an already-rendered photorealistic video.

## GitHub workflow

GitHub Actions runs the **CPU validation and assembly smoke test** only. Standard GitHub-hosted Actions runners do not provide the NVIDIA GPU needed for Wan/LTX AI video generation. For full AI generation use your own GPU or a third-party GPU notebook/Space with available quota (availability is not guaranteed).

## Free open-source generation: WanGP / ComfyUI

Recommended upstream: https://github.com/deepbeepmeep/WanGP (free local software; some models run on GPUs with about 6 GB VRAM, with significant offloading). See its official setup and model requirements. Alternative: https://github.com/comfyanonymous/ComfyUI plus a Wan 2.2 video workflow.

1. Run the selected open-source generator on a **compatible GPU PC/server**. Your Android phone can browse its Web UI but does not replace the GPU.
2. Generate **three 5-second clips**, 9:16, matching the prompts below, and export them as `01.mp4`, `02.mp4`, `03.mp4`. If your model cannot output 5 seconds exactly, generate 4–6 seconds and the assembler will pad/trim to 5s.
3. Keep Kaien's face, short twists, charcoal hoodie, room, lighting, and Scorpio mark consistent in all three outputs; keep the camera handheld and the visuals live action.
4. Optionally add native speech inside the generation prompts. The assembler preserves existing shot audio and fills missing audio with silence.
5. Install FFmpeg on your machine, then assemble locally:

```bash
python3 tools/omniroute.py doctor
python3 tools/omniroute.py validate
python3 tools/omniroute.py prompts
python3 tools/omniroute.py assemble --clips 01.mp4 02.mp4 03.mp4 --output output/episode_01.mp4
```

## Episode canon

Every person is assigned a Zodiac based on their date of birth. All people awaken on their **13th** birthday. Scorpio is the rarest and secretly the strongest, but its power level cannot be measured. Pisces is the second rarest with psychic abilities. Kaien's Scorpio awakening triggers an **UNKNOWN** reading, a blackout, glowing violet eyes, and the whisper: “The forbidden one has awakened.”

`episode_01.json` is the canonical manifest. To start a new episode, copy it and update shot prompts while preserving world rules. `tools/omniroute.py` currently validates a single manifest via `--manifest` and assembles any number of timed shots. No paid API keys or accounts are embedded in this repository.

## Verification

```bash
python3 -m unittest discover -s tests -v
```

The assembly test uses solid-color **synthetic clips** and verifies that an MP4 about 15 seconds long is generated. That test confirms FFmpeg assembly only, **not** AI motion quality or successful WanGP inference. GitHub Actions will run these tests on PRs.
