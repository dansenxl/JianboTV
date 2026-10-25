#!/bin/bash -e

. ../../include/depinfo.sh
. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf _build$ndk_suffix
	exit 0
else
	exit 255
fi

mkdir -p _build$ndk_suffix
cd _build$ndk_suffix

cpu=armv7-a
[[ "$ndk_triple" == "aarch64"* ]] && cpu=armv8-a
[[ "$ndk_triple" == "x86_64"* ]] && cpu=generic
[[ "$ndk_triple" == "i686"* ]] && cpu="i686 --disable-asm"

cpuflags=
# Tuned for the cores 32-bit Android TVs actually have (Cortex-A53/A55 running an armv7 userland);
# -mcpu=cortex-a8 scheduled for a 2005 in-order core. The ISA stays armv7-a + NEON.
[[ "$ndk_triple" == "arm"* ]] && cpuflags="$cpuflags -mfpu=neon -mtune=cortex-a53"

# Filters are an allowlist, not all-or-nothing. Without any, mpv's `--deinterlace` and every `af=lavfi`
# fail silently (the option is accepted, the filter never runs). tools/inspect_aar.py checks each one
# below is really registered in the built libavfilter.
filters=(
	# graph plumbing mpv's lavfi bridge creates by name (filters/f_lavfi.c)
	buffer buffersink abuffer abuffersink
	# format converters libavfilter inserts on its own when two filters disagree
	scale aresample format aformat null anull
	# inserted by mpv itself (filters/f_auto_filters.c): deinterlace, rotation, flip
	bwdif yadif rotate transpose hflip vflip
	# sound: night mode, volume levelling, loudness, EQ, dialogue
	acompressor dynaudnorm loudnorm alimiter volume pan
	equalizer anequalizer bass treble highpass lowpass dialoguenhance
)
filter_list=$(IFS=,; echo "${filters[*]}")
# Two muxers and still no encoders: mpv's `--stream-record` / `dump-cache` can then write what it is
# already playing to disk untouched (a local timeshift option), but nothing is ever re-encoded.

# Video and image decoders an IPTV player never meets: game and FMV formats, screen and remote-desktop
# capture, editing intermediates and lossless pro codecs, still-image and text-art formats. A denylist,
# so a codec FFmpeg adds later is built in until someone decides otherwise. **No audio decoder is ever
# listed** — ExoPlayer decodes with them too (libowntvffmpeg); s302m, speex, comfortnoise, dvaudio and
# eightsvx sit among FFmpeg's video codecs and stay. tools/inspect_aar.py checks the must-haves.
no_decoders=(
	# games, consoles, FMV
	anm argo bethsoftvid bfi bink bmv_video c93 cdgraphics cdtoons cdxl cri dfa dsicinvideo dxa
	eacmv eamad eatgq eatgv eatqi escape124 escape130 flic fmvc fourxm gdv hnm4_video idcin
	interplay_video ipu jv kmvc mdec mimic mmvideo mobiclip motionpixels mvc1 mvc2 mvdv mvha
	paf_video pdv rl2 roq sanm sga simbiosis_imx smacker thp tiertexseqvideo tmv truemotion1
	truemotion2 truemotion2rt txd vb vmdvideo vqa vqc xan_wc3 xan_wc4 yop amv
	# screen capture and remote desktop
	aasc arbc cscd dxtory fic flashsv flashsv2 fraps g2m lscr msa1 mscc mss1 mss2 mts2 mwsc rasc
	rscc scpr screenpresso srgc tdsc tscc tscc2 vmnc wcmv zmbv
	# editing intermediates, lossless and capture-card codecs
	agm aic apv avrn avrp avui cfhd cllc dxv ffv1 ffvhuff hap hq_hqa hqx huffyuv lagarith loco
	magicyuv m101 media100 mszh notchlc pixlet prores_raw prosumer r10k r210 sheervideo snow
	speedhq targa_y216 utvideo v210 v210x vble vmix ylc y41p zero12v zerocodec zlib
	# old webcams, capture boards and niche vendors
	aura aura2 cljr clearvideo cpia cyuv h261 hymt imm4 imm5 kgv1 lead mxpeg qpeg rtv1 sp5x smvjpeg
	ulti vcr1 wnv1 xl frwu bitpacked
	# still images and text art (cover art keeps png, apng, mjpeg, bmp, gif, webp, tiff, jpeg2000)
	alias_pix brender_pix dds dpx exr fits gem jpegls pam pbm pcx pfm pgm pgmyuv pgx phm photocd
	pictor ppm psd ptx qdraw qoi sgi sgirle sunrast targa vbn wbmp xbm xface xpm xwd
	ansi bintext idf xbin
)
# Protocols nothing in OwnTV opens: file transfer, upload-only, distributed web, Windows Media,
# FEC and fan-out helpers. Playback keeps http(s), tls, tcp, udp, rtp/srtp, rtmp*, hls/crypto, data,
# file, fd, pipe, cache, async, concat, subfile and content://.
no_protocols=(ftp gopher gophers icecast ipfs_gateway ipns_gateway mmsh mmst prompeg tee unix md5 concatf)

../configure \
	--target-os=android --enable-cross-compile --cross-prefix=$ndk_triple- --cc=$CC \
	--arch=${ndk_triple%%-*} --cpu=$cpu --pkg-config=pkg-config --nm=llvm-nm \
	--extra-cflags="-I$prefix_dir/include $cpuflags" --extra-ldflags="-L$prefix_dir/lib" \
	--enable-{jni,mediacodec,mbedtls,libdav1d,libxml2} --disable-vulkan \
	--disable-static --enable-shared --enable-{gpl,version3} \
	--disable-{stripping,doc,programs} \
	--disable-{muxers,encoders,devices,filters,avdevice} \
	--enable-filter=$filter_list \
	--enable-muxer=mpegts,matroska \
	--disable-decoder=$(IFS=,; echo "${no_decoders[*]}") \
	--disable-protocol=$(IFS=,; echo "${no_protocols[*]}") \
	--disable-v4l2-m2m

make -j$cores
make DESTDIR="$prefix_dir" install

ln -sf "$prefix_dir"/lib/libswresample.so "$native_dir"
ln -sf "$prefix_dir"/lib/libavutil.so "$native_dir"
ln -sf "$prefix_dir"/lib/libavcodec.so "$native_dir"
ln -sf "$prefix_dir"/lib/libavformat.so "$native_dir"
ln -sf "$prefix_dir"/lib/libswscale.so "$native_dir"
ln -sf "$prefix_dir"/lib/libavfilter.so "$native_dir"
