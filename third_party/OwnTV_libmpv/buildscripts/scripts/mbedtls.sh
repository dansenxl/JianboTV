#!/bin/bash -e

. ../../include/depinfo.sh
. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	make clean
	exit 0
else
	exit 255
fi

$0 clean # separate building not supported, always clean

# Since 3.6.6 mbedTLS seeds from /dev/random, which can block for minutes on an old TV box (its
# getrandom() path is glibc-only, so Android never takes it). FFmpeg seeds once per HTTPS
# connection, so every HLS segment would wait. Same fix as mpv-android db7df51 (issue #1297).
./scripts/config.py set MBEDTLS_PLATFORM_DEV_RANDOM '"/dev/urandom"'

# Its Makefile has "CFLAGS ?= -O2" and never reads CPPFLAGS (build.sh): keep the -O2, add hardening.
make -j$cores CFLAGS="-O2 $CPPFLAGS" no_test
make DESTDIR="$prefix_dir" install
