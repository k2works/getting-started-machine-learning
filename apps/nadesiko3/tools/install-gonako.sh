#!/usr/bin/env bash
# なでしこ3 の処理系 gonako（nadesiko3go）を bin/ に入れる。
#
# タグ 3.8.8 は v の付かない名前で Go のモジュールの版として読めないので、
# そのコミットの疑似版で固定する。nadesiko3go は go 1.26.0 を要求するが、
# Nix の Go は 1.25.5 で、しかも既定が GOTOOLCHAIN=local なので切り替わらない。
# ここで 1.26 系のツールチェーンを明示する（ADR 015）。
set -euo pipefail

cd "$(dirname "$0")/.."

readonly GONAKO_VERSION="v0.0.0-20260924163730-f840295acd3d" # タグ 3.8.8
readonly GO_TOOLCHAIN="go1.26.8"

GOTOOLCHAIN="${GO_TOOLCHAIN}" GOBIN="$(pwd)/bin" \
  go install "github.com/kujirahand/nadesiko3go/cmd/gonako@${GONAKO_VERSION}"

./bin/gonako version
