#!/usr/bin/env bash
# なでしこ3 版の検査を、CI と同じ順（文法・整形・テスト）でまとめて行う。
#
# gonako には検査をまとめて実行する仕組みが無い。lint は 1 回に 1 ファイルしか受け付けず、
# format は整形の結果を出力するだけで検査用のモードが無い。テストもファイルごとに
# 実行して終了コードを見る。ここでそれらを束ねる（ADR 015）。
set -uo pipefail

cd "$(dirname "$0")/.."

readonly GONAKO="./bin/gonako"
if [ ! -x "${GONAKO}" ]; then
  echo "gonako がありません。先に ./tools/install-gonako.sh を実行してください" >&2
  exit 1
fi

mapfile -t sources < <(find main.nako3 src test -name '*.nako3' | sort)
mapfile -t tests < <(find test -name '*_test.nako3' | sort)

failed=0

echo "== 文法（gonako lint）"
for file in "${sources[@]}"; do
  if ! "${GONAKO}" lint "${file}" > /dev/null; then
    echo "文法エラー: ${file}" >&2
    failed=1
  fi
done

echo "== 整形（gonako format との差分）"
for file in "${sources[@]}"; do
  if ! "${GONAKO}" format "${file}" | diff -u "${file}" - ; then
    echo "整形されていません: ${file}（${GONAKO} format ${file} -f で直せます）" >&2
    failed=1
  fi
done

echo "== テスト"
for file in "${tests[@]}"; do
  echo "-- ${file}"
  if ! "${GONAKO}" "${file}"; then
    failed=1
  fi
done

if [ "${failed}" -ne 0 ]; then
  echo "検査が失敗しました" >&2
  exit 1
fi
echo "すべての検査が通りました"
