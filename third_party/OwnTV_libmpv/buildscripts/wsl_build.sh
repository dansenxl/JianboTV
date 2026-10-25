#!/bin/bash -e
# Builds this repo's libmpv on the owner's PC, in WSL, and drops it into a local Maven folder that
# core and both apps read for a build run with -Powntv.libmpvLocalRepo=E:/Linux/Libmpv/maven (see core's
# CLAUDE.md, "Testing an mpv engine change before it is released"). Nothing is published; CI stays the only way to a
# release.
#
# Once per WSL install (as root — installs the same packages as .github/workflows/build.yaml):
#   wsl -d Debian -u root -- bash /path/to/OwnTV_libmpv/buildscripts/wsl_build.sh
# Every build (as the normal user):
#   wsl -d Debian -- bash /path/to/OwnTV_libmpv/buildscripts/wsl_build.sh
#
# The build runs in $WORK, inside the distro's own disk (the WSL distro virtual disk): building on
# /mnt/e is many times slower and would put gigabytes into the MEGA folder. The repo is copied
# there on every run, so the build always uses the files as they are on E: right now, uncommitted
# edits included. The first run downloads the SDK, NDK and every source (~1 h in total); after
# that only changed pins are downloaded again.

# Run as `bash wsl_build.sh`, the -e on the first line is ignored — so set it here.
set -eo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${WORK:-$HOME/owntv_libmpv}"
OUT="${OUT:-/mnt/e/Linux/Libmpv/maven}"
# The version core asks for when a build carries owntv.libmpvLocalRepo. Never a real YYYY.MM.N, so a local
# build can never be mistaken for a published one.
VERSION=local

if [ "$(id -u)" = 0 ]; then
	apt-get update
	apt-get install -y autoconf pkgconf libtool ninja-build python3-pip python3-jinja2 gperf nasm \
		git wget unzip rsync default-jdk-headless
	pip3 install --break-system-packages meson pyelftools
	echo "Packages installed. Now run this script again as the normal user (without -u root)."
	exit 0
fi

mkdir -p "$WORK"
# deps/sdk/prefix are the build's own downloads and output: kept between runs, never copied from E:.
rsync -a --delete \
	--exclude .git --exclude .gradle --exclude .cxx --exclude build \
	--exclude 'buildscripts/deps' --exclude 'buildscripts/sdk' --exclude 'buildscripts/prefix' \
	--exclude 'buildscripts/.downloaded-depinfo.sh' \
	--exclude 'libmpv/src/main/jniLibs' --exclude local.properties \
	"$REPO/" "$WORK/"
# Git stores these LF, but a Windows working copy can still hold CRLF (gradlew has no extension;
# files checked out before .gitattributes existed keep CRLF). The copy here is always made LF.
find "$WORK" -path "$WORK/buildscripts/deps" -prune -o -path "$WORK/buildscripts/sdk" -prune -o \
	-type f \( -name '*.sh' -o -name '*.py' -o -name '*.patch' -o -name gradlew \) -print0 |
	xargs -0 sed -i 's/\r$//'
chmod +x "$WORK"/gradlew "$WORK"/buildscripts/*.sh "$WORK"/buildscripts/include/*.sh "$WORK"/buildscripts/scripts/*.sh

cd "$WORK/buildscripts"
# download-deps.sh skips any source folder that already exists, so a changed pin would silently
# keep the old source. When depinfo.sh differs from the last download, start the sources over.
if ! cmp -s include/depinfo.sh .downloaded-depinfo.sh; then
	rm -rf deps prefix
	IN_CI=1 ./download.sh
	cp include/depinfo.sh .downloaded-depinfo.sh
fi

./patch.sh
./build.sh

cd "$WORK"
python3 tools/inspect_aar.py libmpv/build/outputs/aar/libmpv-release.aar
# build.sh sets ANDROID_HOME only for its own child processes (include/path.sh).
export ANDROID_HOME="$WORK/buildscripts/sdk/android-sdk-linux"
rm -rf build/staging-maven
./gradlew -q :libmpv:publishReleasePublicationToStagingRepository -PlibVersion=$VERSION
mkdir -p "$OUT"
rm -rf "$OUT/tv/own/owntv/libmpv/$VERSION"
cp -r build/staging-maven/. "$OUT/"
echo
echo "Done: tv.own.owntv:libmpv:$VERSION is in $OUT"
echo "Use it by adding \"-Powntv.libmpvLocalRepo=E:/Linux/Libmpv/maven\" to a core or app build command."
