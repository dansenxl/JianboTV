# Changelog

One section per release, plus a line for every monthly check (UPDATING.md, step 7).

## 2026.10.4 — 2026-10-21

Same mpv and FFmpeg as 2026.10.3.

- Fix (FFmpeg patch 0006): an H.264 catch-up archive that starts mid-GOP stays on the hardware decoder.
  New MediaCodec option `wait_keyframe` (off by default; OwnTV_Core sets it for archives only) drops the
  packets before the first keyframe; the Realtek decoder (TCL) stalled on them, and 0002 then moved the
  archive to software. TCL, same programme: 3/3 stalled → software, ~6 s to the picture; with it 3/3
  hardware, 2.5–5 s.
- Fix (mpv patch 0003): a film whose file states no frame rate reports one on mpv. On the direct
  MediaCodec path mpv kept no frame durations, so `estimated-vf-fps` stayed empty and Auto frame rate
  had nothing to switch to; it now falls back to the frames' own timestamp gaps (playback unchanged).
- Fix (FFmpeg patch 0005): an HEVC catch-up archive no longer crashes the app on mpv. A stream joined
  mid-way reached the MediaCodec decoder with no extradata, and FFmpeg's HEVC parser aborted the
  process on the empty buffer; the decoder now opens as when the extradata holds no parameter sets.
- Fix (FFmpeg patch 0004): H.264 above 30 fps plays at its full rate on mpv. The decoder is opened
  with `max-width` / `max-height`, as ExoPlayer does; without them the Realtek decoder (TCL) drew
  through the GPU at 30 fps. 1080p50 30 → 50 fps, 1080p59.94 30 → 58 fps.
- Fix (FFmpeg patch 0003): HLS with chunked playlists (Akamai MediaPackage, public Australian
  channels) opens on mpv; a complete chunked response is no longer re-fetched until the open times out.

## 2026.10.3 — 2026-10-10

Same mpv and FFmpeg as 2026.10.2.

- New `libowntvffmpeg.so`: ExoPlayer's FFmpeg audio decoder (Media3 1.11.1) on this library's own
  FFmpeg, for OwnTV_Core. Checked by the build contract.
- Fix: mbedTLS seeds from `/dev/urandom`; its default `/dev/random` can hold up HTTPS streams for
  minutes on older boxes (as mpv-android `db7df51`).
- Fix (FFmpeg patch 0002): the 3 s decoder-stall timer restarts on every frame and every flush, so
  short normal pauses can no longer add up to a false switch to software decoding.
- Hardening: every native library built with stack protector + `_FORTIFY_SOURCE=2`.
- Every `.so` has a build-id; each GitHub Release carries the unstripped libraries per ABI.
- Smaller: no Lua (mpv's built-in scripts no longer start in every player), no libavdevice, 191 game /
  screen-capture / editing / still-image video decoders and 13 unused protocols dropped (every audio
  and subtitle decoder kept), libraries linked with `--gc-sections`, `--exclude-libs` and packed
  relocations. The build contract checks the decoders, demuxers and protocols IPTV needs.
- armv7 tuned for Cortex-A53 instead of Cortex-A8; mpv built without a timestamp.
- Build: gas-preprocessor pinned to a commit; the NDK version lives only in `depinfo.sh`.

## 2026.10.2 — 2026-10-08

- Fix (FFmpeg patches): a hardware decoder that stalls after the first frames (Realtek, found on the
  TCL during catch-up) no longer locks the whole player; after 3 s mpv switches to software decoding
  and playback continues. Same mpv and FFmpeg as 2026.10.1.

## 2026.10.1 — 2026-10-07

- Fix (patch): `ao_audiotrack` no longer burns a whole CPU core during an audio underrun (found on
  the TCL). An earlier wording of this entry said it fixed a first-frame freeze; it does not — that
  freeze is handled in OwnTV_Core. Same mpv and FFmpeg as 2026.10.0.

## 2026.10.0 — 2026-10-03

- mpv `3186d369f` (git-release-373, master of 2026-10-01), was `2a4eb8067`; no interface changes.
- HarfBuzz 14.5.0, libunibreak 8.0 (Unicode 17 line breaking), Gradle 9.8.0.
- mbedTLS stays on 3.6.x: FFmpeg n9.0.2 does not build against mbedTLS 4 yet.

## 2026.09.2 — 2026-09-26

- JNI: `MPV_EVENT_END_FILE` now also reaches Java with its reason and error code —
  `EventObserver.endFile(reason, error)` (default no-op, so existing observers compile unchanged) and
  `MPVLib.MpvEndFileReason`. It is delivered just before the plain `event(MPV_EVENT_END_FILE)`, which is
  unchanged. Same mpv and FFmpeg as 2026.09.1.

## 2026.09.1 — 2026-09-25

- Revert mpv `13a4bfbc1` (patch): since it, mpv fetched HLS playlists itself and IPTV panels answered
  403 Forbidden on every segment (found on the TCL with an Xtream HLS channel that plays on ExoPlayer).
- mpv's log follows the app's `msg-level` instead of always sending verbose messages to logcat — less
  CPU and a readable log on 2 GB TVs.

## 2026.09.0 — 2026-09-25

First OwnTV build, forked from jarnedemeulemeester/libmpv-android `8687e8c` (after its 1.0.0).

- mpv `2a4eb8067` (v0.41.0-1072, master of 2026-09-23), was 0.41.0 in upstream 1.0.0.
- FFmpeg n9.0.2, was n8.1 in upstream 1.0.0.
- FFmpeg filter allowlist: mpv's deinterlacer (`bwdif`) and `af=lavfi` (night mode, levelling) now work.
- `mpegts` and `matroska` muxers; still no encoders.
- ABIs armeabi-v7a, arm64-v8a, x86_64 (32-bit x86 dropped).
- Published as `tv.own.owntv:libmpv` to OwnTV's public Maven repository (https://ahxn00.github.io/OwnTV_Core/maven,
  no login); Java package `dev.jdtech.mpv` unchanged.
