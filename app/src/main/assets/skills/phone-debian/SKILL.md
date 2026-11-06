---
name: Phone Debian
description: Use when working on this Android phone's native Debian 12 chroot, Termux wrappers, ReTerminal, XFCE/Termux:X11 GUI, OpenCode service, or Android/Magisk-specific problems.
metadata:
  author: local
  version: "1.0"
---

# Phone Debian (Android 11 + native Debian 12 chroot)

This is a **native chroot**, not proot/QEMU. Root inside Debian equals root on
the phone. The canonical, verified documentation is
`~/docs/android-debian-opencode.md` (inside Debian:
`/mnt/termux/docs/android-debian-opencode.md`). Read it before changing
system-level pieces.

## Environment

| Item | Value |
|---|---|
| Device | Xiaomi Mi 9T (`davinci`), Android 11 / API 30 |
| Root | Magisk, `su` at `/debug_ramdisk/su` (not `/system/bin/su`) |
| Debian root | `/data/local/debian`, suid alias `/data/local/debian-suid` |
| Debian user | `dev`, uid/gid `1000`; Termux uid `10280`; ReTerminal uid `10617` |
| Screen | 1080×2340, X at 200 dpi, XFCE default, IceWM fallback |
| RAM | 5.6 GB total, ~1.1–1.6 GB available with GUI |
| OpenCode | Debian v2.1.4 service at `127.0.0.1:49375`; separate Termux instance at `49374` |

## Commands

```bash
debian                 # interactive shell as dev
debian root            # interactive shell as root
debian -c 'cmd'        # one command as dev
oc [dir]               # OpenCode in Debian
gui                    # XFCE + Termux:X11
gui icewm              # IceWM fallback
gui stop               # stop session, keep X
gui xstop              # stop session and X
su -c /data/local/bin/rt-status
```

`debian -c` uses a unique temp file, so concurrent calls are safe. Do not
replace it with a fixed `/tmp` filename.

## Rules and traps

- **Never use `pkill -f` with a pattern that appears in the invoking command
  line.** It can kill the shell running the command. Prefer `pkill -x <comm>`,
  PIDs from `pgrep -x`, or `gui stop`.
- From Termux, `chown dev:dev` fails because Android resolves uid 1000 as
  `system`. Use `chown 1000:1000`.
- `/data` is mounted `nosuid`; `enter-debian` remounts the `-suid` alias. Do
  not chroot directly into `/data/local/debian` if `sudo` is needed.
- Do not run `xfce4-panel -r`; it hangs in this chroot. Restart with
  `gui stop && gui`.
- Keep exactly one XFCE session/panel. Duplicates cause the
  “notification area lost selection” dialog.
- If X grows beyond the screen (2336×1080 → 3032×1403), set `Scale/X` and
  `Scale/Y` to `1` and `Notify` to `0` in
  `~/.config/xfce4/xfconf/xfce-perchannel-xml/displays.xml`.
- In landscape X is 2336×1080 (libxcvt rounds to a multiple of 8); portrait is
  1080×2340. Two-pixel side bars are normal.
- `service.json` holds the OpenCode service password (mode 600). Never publish
  it or copy it to a shared directory.
- ReTerminal updates/data clears require `su -c /data/local/bin/rt-apply-init`,
  then `rt-status`.
- Avoid heavy browsers/LSP when free RAM is low; Android may kill X or Termux.
- Test GUI changes with `debian -c 'DISPLAY=:0 ...'`, screenshots via
  `screencap`, and pixels/OCR rather than assuming a window is visible.

## Useful checks

```bash
debian -c 'echo $DISPLAY; xdpyinfo | grep -E "dimensions|resolution"'
debian -c 'pgrep -c -x xfce4-session; pgrep -c -x xfce4-panel'
debian -c 'pgrep -a -x xfce4-session; pgrep -a -x xfwm4; pgrep -a -x icewm'
su -c 'netstat -ltn | grep -E "49374|49375"'
```
