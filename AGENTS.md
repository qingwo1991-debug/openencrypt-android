# Repository Runtime Rules

This repository inherits `/root/AI/AGENTS.md`. The following GitHub network rule is repeated here to guarantee local precedence.

## GitHub Network Policy (Mandatory)
- Any GitHub-related operation must not use sandbox network.
- For GitHub access, run with host network (request escalated execution when needed).
- Retry order is fixed:
  1. Host direct network first.
  2. If still unreachable, retry with proxy `192.168.1.15:7890`.
- Do not start with sandbox GitHub access.

## Proxy Fallback (Only When Direct Host Fails)
- `HTTP_PROXY=http://192.168.1.15:7890`
- `HTTPS_PROXY=http://192.168.1.15:7890`
- `ALL_PROXY=socks5://192.168.1.15:7890` (optional)
- `NO_PROXY=127.0.0.1,localhost`
