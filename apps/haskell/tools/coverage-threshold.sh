#!/usr/bin/env bash
# HPC の結果を読み、式のカバレッジがしきい値を下回ったら失敗する。
#
# cabal には「カバレッジが N% を下回ったら失敗させる」機能が無いので、
# PHP 版の tools/coverage-threshold.php と同じく自分で判定する。
#
# 使い方: tools/coverage-threshold.sh [しきい値]
set -euo pipefail

threshold="${1:-80}"

index=$(find dist-newstyle -path '*/hpc/vanilla/html/hpc_index.html' | head -1)

if [ -z "$index" ]; then
  echo "カバレッジの結果がありません。先に cabal test --enable-coverage を実行してください" >&2
  exit 1
fi

# 総計から「式のカバレッジ」を取る。HPC の index は総計の見出しの直後に、
# 分岐・トップレベル定義・式の順で 3 組の「率」と「実数/総数」を並べる。
# 見出しと数値が別の行にあるので、改行を落としてから取り出す。
summary=$(tr -d '\n' < "$index" | grep -o 'Program Coverage Total.*' | head -1)
rate=$(echo "$summary" | grep -oE '<td align="right">[0-9]+%</td><td>[0-9]+/[0-9]+</td>' | sed -n '3p')

if [ -z "$rate" ]; then
  echo "カバレッジの結果を読めません: $index" >&2
  exit 1
fi

percent=$(echo "$rate" | grep -oE '[0-9]+%' | tr -d '%')
counts=$(echo "$rate" | grep -oE '[0-9]+/[0-9]+')

printf '式カバレッジ: %s%% (%s), しきい値: %s%%\n' "$percent" "$counts" "$threshold"

if [ "$percent" -lt "$threshold" ]; then
  printf 'カバレッジがしきい値を下回りました: %s%% < %s%%\n' "$percent" "$threshold" >&2
  exit 3
fi
