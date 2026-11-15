# Codex → WanGP GPU handoff: Zodiac Episode 1

This file is a **shared-workspace handoff**, not an automatic connection between ChatGPT conversations and Codex. Open this branch in a Codex environment that has access to your GPU computer.

## Objective

Generate a genuine **15-second vertical 9:16 photorealistic live-action UGC** video for *Zodiac: The Awakening*, Episode 1. Kaien is a 13-year-old boy with medium brown skin, short black twists and a charcoal hoodie. Scorpio symbol powers awaken at age 13. A Zodiac scanner reads **UNKNOWN**, lights go out and his eyes glow violet. Preserve his identity and outfit in all three clips.

## Existing assets

- Canonical script and shot prompts: `episode_01.json` (three 5-second shots).
- Validated local FFmpeg assembler: `tools/omniroute.py`.
- Existing CI smoke test: `tests/test_omniroute.py`.
- Official WanGP: https://github.com/deepbeepmeep/WanGP
- Official WanGP Python API: https://github.com/deepbeepmeep/WanGP/blob/main/docs/API.md
- MCP guide: `WANGP_MCP_ACCESS.md`

## Codex instructions (when working on the user's authorized GPU PC)

1. **Detect, don't assume:** print OS, `nvidia-smi` (or AMD GPU detection), VRAM and available disk. Print `which python` / `where python`, `ffmpeg -version`, and installed WanGP folder. Never print API keys, cookies, secrets, or machine-specific private files.
2. If WanGP is installed, use its existing environment and configuration. Do not overwrite checkpoint folders or reinstall WanGP without checking; no subscription purchases.
3. Check WanGP MCP availability locally with the configured endpoint (usually `http://127.0.0.1:8000/mcp`). If it is not running, use the **official** WanGP Manager or startup script with MCP enabled. Bind locally unless authenticated HTTPS has been explicitly configured.
4. Use the **WanGP Showrunner** to import a new 15-second project from the prompts, or use the official `shared.api.init(root=...)`/session API, querying `list_model_metadata()`, `get_model_availability(model_type)`, `get_default_settings(model_type)` before choosing a model supported by the actual installed GPU.
5. Prefer a compatible fast low-VRAM **LTX / Wan / H3 model** already installed. Start at portrait 480p draft. Render shot `EP01-S01`, evaluate character continuity, then render `EP01-S02` and `EP01-S03`. Use image-to-video reference of Kaien wherever supported, to preserve appearance. Avoid hallucinated model IDs and unsupported resolution/length parameters.
6. Export generated clips as `01.mp4`, `02.mp4`, `03.mp4` and assemble:
   ```bash
   python3 tools/omniroute.py validate
   python3 tools/omniroute.py assemble --clips 01.mp4 02.mp4 03.mp4 --output output/episode_01.mp4
   ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1 output/episode_01.mp4
   ```
7. Produce a local downloadable result or a specifically authorized artifact destination. Report model ID, GPU, command, duration, resolution, output location, and whether **actual AI motion** was rendered. If generation could not run, report the exact blocker; don't substitute synthetic assembly footage and call it AI video.
8. Return your status to the GitHub PR [Zodiac OmniRoute](https://github.com/bernardjstander123-droid/Install/pull/2) or provide the resulting artifact. Do **not** merge the PR unless requested.

## Important

A separate Codex session is not automatically linked to this ChatGPT session. Sharing this branch and this handoff file makes the production state accessible to both. This repository is not a GPU inference host, and standard GitHub-hosted runners cannot perform WanGP model inference.
