# SSH Access to This Machine (via Tailscale)

**Status: done, working.** SSH is enabled on this machine (`Pavilion`) and reachable over Tailscale.
Use this as the standing way to pull files (APKs, etc.) off this machine when `SendUserFile` /
Slack can't handle the size (e.g. debug APKs are ~46MB, over `SendUserFile`'s 30MB cap and Slack
MCP has no upload tool at all).

## This machine's details

- Tailscale IP: `100.109.128.31` (hostname `pavilion`, may also work as `abhiram@pavilion` if
  MagicDNS is on for the tailnet)
- SSH user: `abhiram`
- `sshd` is enabled + running (`systemctl status ssh` to confirm). If it's ever off again:
  ```
  sudo apt update && sudo apt install -y openssh-server   # only if ssh.service unit is missing
  sudo systemctl enable --now ssh
  ```

## From a laptop/desktop

```
ssh abhiram@100.109.128.31
scp abhiram@100.109.128.31:/path/to/file .
```
Requires Tailscale connected on that device (same tailnet account) and the account password (or an
SSH key added to `~/.ssh/authorized_keys` on this machine to skip password prompts).

## From an Android phone (no terminal needed)

1. Install/connect the **Tailscale** app on the phone (same tailnet account).
2. Install **Termius** (Play Store, free tier is enough).
3. Add host: Hostname `100.109.128.31`, port `22`, user `abhiram`, password auth (or SSH key).
4. Connect, then use Termius's **SFTP tab** (not the terminal) to browse to the file and download
   it straight to phone storage — no commands needed.
5. Open the downloaded file (e.g. an APK) from the phone's Files app to install it directly.

Alternative for a real terminal on Android: **Termux** (get from F-Droid, not Play Store — Play
version is outdated), then `pkg install openssh` and use the same `ssh`/`scp` commands as the
laptop section.

## Example: pulling the debug APK

```
scp abhiram@100.109.128.31:/home/abhiram/Downloads/app-for-food/app/build/outputs/apk/debug/app-debug.apk .
```
