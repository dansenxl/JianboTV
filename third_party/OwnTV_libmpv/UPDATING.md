# Monthly update — how OwnTV stays on the newest mpv

**Once a month, early in the month.** The goal is that OwnTV's mpv is never more than about a month
behind official mpv master, and FFmpeg never misses a point release. Each step says how it is checked.
Nothing here is released without a device test on the TV and the phone.

## 0. What moves, and who notices

| Source | Pinned in `depinfo.sh` as | Noticed by |
|---|---|---|
| mpv master | `v_mpv` (commit) | **`monthly-update.yaml`**, 1st of the month, 06:00 UTC — opens a PR |
| FFmpeg releases (`n9.0.x`, later `n9.1`, `n10`) | `v_ffmpeg` | Renovate PR (the Renovate app is installed on this repo only, in Interactive mode) |
| libplacebo, libass, dav1d, mbedTLS, freetype, fribidi, harfbuzz, libunibreak, libxml2, fontconfig | `v_*` | Renovate PR |
| Gradle / AGP | `gradle/` | Renovate PR |
| NDK, SDK command-line tools, build-tools, CMake, gas-preprocessor | `v_ndk`, `v_sdk`, `v_sdk_build_tools`, `v_cmake`, `v_gas_preprocessor` | **by hand, step 2** — Renovate has no source matching `sdkmanager`'s package versions |
| the upstream wrapper (jdtech) | the `upstream` remote | step 6 |

## 1. The mpv pull request

On the 1st, **Actions → Monthly mpv update** runs by itself (or run it by hand: *Run workflow*).

- **A PR "Monthly mpv update (…)" appeared.** The pin is bumped, the full build and the contract check
  have already passed in that run, and the PR body lists every new entry in mpv's
  `DOCS/interface-changes` since the old pin. Go to step 3.
- **No PR, run green.** mpv master has not moved. Nothing to do for mpv this month.
- **No PR, run red.** The bump did not build. Open the failed *Build the bump* job: usually a new
  minimum version of a dependency (raise it in `depinfo.sh` on the update branch and push; the push
  builds it) or a meson option renamed in `buildscripts/scripts/mpv.sh`.

## 2. The other dependencies

Merge Renovate's PRs if they are green. If Renovate is ever removed, check by hand and bump in `depinfo.sh`:

- FFmpeg: newest `n9.0.x` tag at https://github.com/FFmpeg/FFmpeg/tags. A point release is always taken
  (security fixes). A new major (`n9.1`, `n10.0`) is taken only once mpv master builds against it —
  the build proves it.
- The rest: their release pages (`renovate.json` names each source).

Always by hand, since Renovate cannot see them: the newest NDK, build-tools and CMake that
`sdkmanager --list` offers, the newest command-line tools build (developer.android.com/studio,
"Command line tools only"), and gas-preprocessor's newest commit
(`gh api repos/FFmpeg/gas-preprocessor/commits/master --jq .sha`). `v_ndk` is the only NDK pin —
`libmpv/build.gradle.kts` reads it from `depinfo.sh`.

Every bump goes through a PR so `build.yaml` runs the build and the contract on it.

## 3. Review the mpv changes against what OwnTV sets

OwnTV configures mpv in exactly one file: `OwnTV_Core/player-core/src/main/java/tv/own/owntv/player/OwnTVPlayer.kt`.
List what it uses:

```sh
grep -ohE '(setOptionString|setProperty[A-Za-z]*|getProperty[A-Za-z]*|observeProperty)\("[^"]+"' OwnTVPlayer.kt | sort -u
```

For every interface-change entry in the PR body, ask: does it rename, remove, or change the default of
anything in that list? Known sensitive areas, with the setting OwnTV pins today to keep its behaviour:

| mpv area | Why it matters | OwnTV sets |
|---|---|---|
| network backend (`--curl-*`) | curl ignores FFmpeg's `reconnect_*` options and never retries a live (non-seekable) stream | `curl-enabled=no` — and this build has no libcurl anyway |
| TLS (`--tls-verify`) | master defaults to `yes`; IPTV panels often have bad certificates | `tls-verify=no` |
| editions / programs | `track-list` filtered to one HLS variant or TS program — breaks the Quality menu | `flatten-editions=yes` |
| `hwdec*`, `vd-lavc-*` | hardware decoding on Realtek / Amlogic | `hwdec-codecs` list, `vd-lavc-*` on the software rung |
| `demuxer-*`, `cache-*`, `stream-lavf-o` | live buffering and reconnects | the `PlayerBudget` values |
| `deinterlace`, `af`, `vf` | need the filter allowlist | `deinterlace=auto` off the direct path |

Also look at whether the two files OwnTV's main TV path depends on changed — they had not between 0.41.0
and `2a4eb8067`:

```sh
git -C <mpv clone> log --oneline <old>..<new> -- video/out/vo_mediacodec_embed.c audio/out/ao_audiotrack.c
```

Anything that needs a new setting in OwnTV goes into core in the same update, never into an app.

### Carried patches

`buildscripts/patches/mpv/` and `buildscripts/patches/ffmpeg/` hold changes OwnTV keeps on top of mpv
and FFmpeg. A bump whose build fails in *Apply patches* means upstream changed those lines: read the new upstream code — if mpv fixed the problem
itself, delete the patch; otherwise refresh it (`git revert --no-commit <commit>` in a clone, then
`git diff`). Each patch file starts with why it exists and how it was found.

- `0001-revert-hls-manifest-through-stream.patch` — mpv `13a4bfbc1` lets mpv fetch the HLS/DASH
  playlist itself; panels that bind segment access to that response then answer **403** on every
  segment. Test for it before dropping the patch: an Xtream HLS channel on mpv must play.
- `0002-audiotrack-no-spin-on-underrun.patch` — on an audio underrun `ao_audiotrack` wrote zero bytes
  in a loop, burning a whole CPU core until audio arrived. Drop it only once upstream `ao_thread`
  waits on a zero-sample read itself; test: during a catch-up open on the TCL, `ao/audiotrack` in
  `/proc/<pid>/task` stays in state S.
- `ffmpeg/0001-mediacodec-never-pass-a-dequeue-timeout.patch` + `ffmpeg/0002-mediacodec-fail-a-stalled-decoder.patch`
  — a Realtek MediaCodec decoder that stalls after the first frames used to lock mpv's core thread in
  FFmpeg's receive loop forever (the TCL's catch-up first-frame freeze). 0001 makes every dequeue
  return, 0002 fails the decode after 3 s without progress so mpv falls back to software. Drop them
  only together, and only once FFmpeg bounds that loop itself; test: 10+ catch-up opens of an NPO 1
  programme (Stalker, Junior) on the TCL — none may freeze, and `MediaCodec stalled` may appear.
  The 3 s count one unbroken stall: every frame out, input slot and flush restarts it.
- `mpv/0003-estimated-vf-fps-from-timestamp-gaps.patch` — on the direct MediaCodec path mpv keeps no
  frame durations, so a file with no frame rate left `estimated-vf-fps` empty and Auto frame rate idle.
  Drop it once mpv estimates the rate there itself; test: a Matroska film without DefaultDuration on
  mpv logs `measured fps: est-vf-fps=… (no container-fps)`.
- `ffmpeg/0003-http-a-finished-chunked-body-is-not-premature.patch` — with `reconnect_streamed=1` a
  complete chunked HLS playlist was re-fetched until the open timed out. Drop it once FFmpeg treats a
  final zero-size chunk as the end; test: `https://c.mjh.nz/abc-nsw.m3u8` opens on mpv.
- `ffmpeg/0004-mediacodec-open-the-decoder-adaptive.patch` — the decoder is opened with max-width /
  max-height (adaptive), as ExoPlayer does; without them the Realtek decoder showed H.264 above 30 fps
  at 30 fps. Test: an H.264 1080p50 channel on mpv presents ~50 fps on the TCL.
- `ffmpeg/0005-mediacodec-hevc-without-extradata.patch` — an HEVC stream with no extradata aborted the
  process in FFmpeg's HEVC parser. Drop it once upstream checks for the empty buffer; test: an HEVC
  catch-up archive opens on mpv without a crash.
- `ffmpeg/0006-mediacodec-wait-for-the-first-keyframe.patch` — decoder option `wait_keyframe` (off by
  default; core sets it for archives only) drops packets before the first keyframe. Test: 3 catch-up
  opens of an H.264 programme that starts mid-GOP on the TCL stay on hardware (no `MediaCodec stalled`).

### ExoPlayer's FFmpeg audio decoder (`libowntvffmpeg.so`)

`libmpv/src/main/cpp/ffmpeg_jni.cc` is Media3's `decoder_ffmpeg` JNI (tag 1.11.1), built on this
library's FFmpeg; OwnTV_Core's `:player-core` holds the Java half and binds to it by class name. An
FFmpeg bump that breaks it fails the build (it calls `avcodec`/`swresample` directly) — fix the C++
here, keeping the class names. The contract (`inspect_aar.py`) checks the library, its links and
`JNI_OnLoad`. When core bumps Media3, compare this file with that tag's copy.

## 4. Release this library

Merge the PR(s) into `main`, then tag: `git tag vYYYY.MM.0 && git push origin vYYYY.MM.0`.
`publish.yaml` builds, checks, publishes `tv.own.owntv:libmpv:YYYY.MM.0` and creates the release.
Add a section to [CHANGELOG.md](CHANGELOG.md) first: mpv commit + describe, FFmpeg version, what changed.
Update the README's "Versions compared" table too: the "ours" column always, the other two columns
whenever official mpv or the upstream library has released since.

## 5. Take it into OwnTV

The publish opened a **"Pin libmpv YYYY.MM.N" pull request on OwnTV_Core** (it changes
`libmpv = "YYYY.MM.N"` in `gradle/libs.versions.toml`, and core's CI builds it). It never merges itself.
In `OwnTV_Core`, on that branch:

1. Any new mpv option OwnTV needs (step 3) goes onto the same branch.
2. Core builds: `./gradlew :core:assembleRelease :player-core:assembleRelease`, unit tests, and both
   apps' `assembleStandardRelease` against core's source.
3. **Device test** on the TCL TV and the phone (release APKs, `adb install -r`, data kept) — the `mpv ready`
   log line shows the mpv and FFmpeg versions and the active audio filters (`af=`):
   - VOD 4K HDR (direct path) and an old Xvid / MPEG-2 file (copy rung)
   - live on mpv: an **Xtream HLS channel** (the 403 check — see Carried patches), a raw TS channel, one that
     drops (reconnect), an HTTPS panel
   - Stream info's **Interlacing** row on mpv with hardware decoding off (progressive / deinterlaced by the player)
   - catch-up, ExoPlayer ⇄ mpv handover both ways, Multiview
   - image and text subtitles, surround and stereo, Night mode on mpv (by ear)
   - Quality menu on a multi-variant HLS channel
   - ExoPlayer: `OwnTV-FfmpegAudio: available=true` in logcat, and a normal AAC/AC3 channel still on
     the device decoder (`AudioOutputPolicy: audio decoder: c2.…`), not `ffmpeg…`
4. Core changelog under the next `core-<version>`; the core release carries it into both apps through
   the usual pin-bump PRs.

**A regression found in step 3 means no core bump this month.** Keep the old version pinned in core, write
what broke in this repo's CHANGELOG under the unreleased version, and either pin mpv one commit earlier or
wait for next month. The library release itself can stay — core decides what it ships.

## 6. Upstream wrapper changes (a few times a year)

```sh
git fetch upstream
git log --oneline main..upstream/main
git merge upstream/main
```

Conflicts can only be in the files listed in the README's "Differences from upstream" table. Keep ours
for the version pins and publishing, take theirs for JNI/Kotlin fixes. Upstream's own version bumps are
ignored: `depinfo.sh` here is the source of truth.

## 7. Record it

One line per month in [CHANGELOG.md](CHANGELOG.md), even for "no change": the date, what was checked,
and what was released or why not.
