# WanGP MCP access for Zodiac: The Awakening

Official project: https://github.com/deepbeepmeep/WanGP

WanGP's MCP server is **built in**. This guide prepares a self-hosted GPU renderer that can consume Episode 1 shot prompts from [episode_01.json](episode_01.json).

## Windows (easiest)

1. Download the official WanGP Manager: https://github.com/deepbeepmeep/WanGP/releases/latest/download/WanGPManager.exe
2. Run Manager → **Install / Add** → install WanGP on a compatible GPU computer.
3. Launch WanGP. In its web UI use **Configuration → Agents → Enable MCP with Web UI** and set **MCP Server Port = 8000**. Save and restart.
4. On the same computer, a local MCP client can use `http://127.0.0.1:8000/mcp`.
5. Follow the model selector and download a GPU-compatible Wan/LTX model; use `python3 tools/omniroute.py prompts` to list the three Kaien Scorpio shots.

## Script-installed WanGP (Windows/Linux)

Clone https://github.com/deepbeepmeep/WanGP.git and run the official `scripts/install.bat` or `scripts/install.sh`. To enable local MCP alongside the UI, place the following line in `scripts/args.txt` without removing your existing flags:

```text
--mcp-webui --mcp-host 127.0.0.1 --mcp-port 8000
```

Start via the official `scripts/run.bat` or `scripts/run.sh`. The same local MCP URL applies. Note: Manager-managed launches may ignore this args file; use Configuration → Agents instead.

## Remote ChatGPT access needs authenticated HTTPS

`127.0.0.1` is **not** reachable by a cloud-hosted ChatGPT connector. See the official [MCP authentication/HTTPS guide](https://github.com/deepbeepmeep/WanGP/blob/main/docs/AUTHENTICATION.md) for a trusted reverse proxy or VPN and WanGP MCP OAuth (`--mcp-auth` and `--mcp-auth-url`). Configure the public endpoint as `https://your-domain.example/mcp` in an MCP-capable client only after authentication, HTTPS, and authorization discovery work. Never expose unauthenticated MCP on a public address; MCP agents can access the queue and galleries. Do not enable arbitrary server filesystem reads.

For compatible clients, WanGP's default MCP API is **v2.1** with Streamable HTTP. The web-UI MCP server runs concurrently with the UI and uses the same models and queue. See the official [API documentation](https://github.com/deepbeepmeep/WanGP/blob/main/docs/API.md).

## Limits and status

- This repository provides **prompt manifests and assembly tooling**, not GPU hosting.
- GitHub Actions can test the assembler but cannot render the Wan/LTX video models on standard CPU runners.
- A Samsung Galaxy A07 can access a WanGP web UI remotely; its 8GB **system RAM** is not interchangeable with 8GB dedicated GPU VRAM.
- No remote computer is currently connected here, so this guide is **not** proof of successful installation, MCP handshake, or video generation.
