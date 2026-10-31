#!/usr/bin/env bash
# =============================================================
# ci/install-kubectl.sh — put a checksum-verified kubectl into
# $WORKSPACE/.tools (cached on the agent node in /cache/bin).
# Version comes from KUBECTL_VERSION (Jenkinsfile); keep it within
# one minor version of the K3s server (versions.yml: k3s_version).
# =============================================================
set -euo pipefail

VER="${KUBECTL_VERSION:?KUBECTL_VERSION not set, e.g. v1.36.4}"
case "$(uname -m)" in
  x86_64)  ARCH=amd64 ;;
  aarch64) ARCH=arm64 ;;
  *) echo "Unsupported arch $(uname -m)" >&2; exit 1 ;;
esac

CACHE_DIR="${KUBECTL_CACHE_DIR:-/cache/bin}"
BIN="$CACHE_DIR/kubectl-$VER-$ARCH"
OUT="${WORKSPACE:-$PWD}/.tools"
mkdir -p "$CACHE_DIR" "$OUT"

fetch() {  # fetch <url> <file>
  if command -v curl >/dev/null; then curl -fsSL --retry 3 -o "$2" "$1"
  else wget -q -O "$2" "$1"; fi
}

if [[ ! -x "$BIN" ]]; then
  URL="https://dl.k8s.io/release/$VER/bin/linux/$ARCH/kubectl"
  echo "Downloading kubectl $VER ($ARCH)"
  TMP="$(mktemp)"
  fetch "$URL" "$TMP"
  fetch "$URL.sha256" "$TMP.sha256"
  echo "$(cat "$TMP.sha256")  $TMP" | sha256sum -c - >/dev/null
  chmod +x "$TMP" && mv "$TMP" "$BIN" && rm -f "$TMP.sha256"
fi

ln -sf "$BIN" "$OUT/kubectl"
"$OUT/kubectl" version --client
