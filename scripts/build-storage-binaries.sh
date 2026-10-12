#!/usr/bin/env bash
set -euo pipefail

taskRepoRoot=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
taskBuildDir=$(mktemp -d "${TMPDIR:-/tmp}/yesihave-storage.XXXXXX")
minioCommit=7aac2a2c5b7c882e68c1ce017d8256be2feea27f
mcCommit=77f82e18b5401a65958f1619df6ebb994634bd88
mkdir -p "$taskRepoRoot/storage-binaries"

build_binary() {
  local binaryName=$1 commit=$2
  git init -q "$taskBuildDir/$binaryName"
  git -C "$taskBuildDir/$binaryName" remote add origin "https://github.com/minio/$binaryName.git"
  git -C "$taskBuildDir/$binaryName" fetch --depth 1 origin "$commit"
  git -C "$taskBuildDir/$binaryName" checkout --detach FETCH_HEAD
  (
    cd "$taskBuildDir/$binaryName"
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 GOAMD64=v1 \
      go build -mod=readonly -trimpath -ldflags='-s -w' \
      -o "$taskRepoRoot/storage-binaries/$binaryName" .
  )
  cp "$taskBuildDir/$binaryName/LICENSE" "$taskRepoRoot/storage-binaries/$binaryName.LICENSE"
}

build_binary minio "$minioCommit"
build_binary mc "$mcCommit"
taskGoVersion=$(go env GOVERSION)
printf '{\n  "target": "linux/amd64",\n  "goToolchain": "%s",\n  "cgoEnabled": false,\n  "goAMD64": "v1",\n  "buildFlags": ["-mod=readonly", "-trimpath", "-ldflags=-s -w"],\n  "minio": {"source": "https://github.com/minio/minio", "commit": "%s"},\n  "mc": {"source": "https://github.com/minio/mc", "commit": "%s"}\n}\n' \
  "$taskGoVersion" "$minioCommit" "$mcCommit" > "$taskRepoRoot/storage-binaries/provenance.json"
printf 'Storage binaries ready; source checkout retained at %s\n' "$taskBuildDir"
