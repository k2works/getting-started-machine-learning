---
type: ADR
title: "001 Python 版の機械学習・可視化・API ライブラリの選定"
description: "Python 版の機械学習・可視化・API ライブラリに pandas・scikit-learn・FastAPI・matplotlib・seaborn・JupyterLab を採用する。"
tags: [adr,getting-start-ml,python]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T02:29:00Z }
---

# 001 Python 版の機械学習・可視化・API ライブラリの選定

「機械学習から始めるプログラミング入門」Python 版で使うライブラリを決める。

日付: 2026-09-17

## ステータス

2026-09-17 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) では、各アルゴリズムを TDD で自作したあと、その言語の機械学習ライブラリに置き換えて結果を突き合わせる方針を定めた。また、データの探索と可視化は Python と Kotlin の Notebook だけで扱う。Python 版は本シリーズの参照実装なので、ほかの言語から結果を突き合わせる基準になる。

必要な機能は次のとおり。

- データフレーム（CSV・TSV の読み込み、欠損値補完、ダミー変数化、結合）
- 行列演算（正規方程式、固有値分解、勾配降下）
- 決定木・線形回帰・リッジ回帰・ラッソ回帰・ロジスティック回帰・ランダムフォレスト・PCA・K-means・交差検証・評価指標
- Notebook での可視化と、コミット前の出力セルの削除
- 学習済みモデルを返す HTTP API と、その統合テスト

## 決定

| 用途 | 採用 | バージョン（uv.lock） | 区分 |
|------|------|---------------------|------|
| データフレーム | pandas | 3.0.5 | 本番依存 |
| 行列演算 | NumPy | 2.5.3 | 本番依存 |
| 機械学習 | scikit-learn | 1.9.1 | 本番依存 |
| モデルの保存 | joblib | 1.6.0 | 本番依存 |
| HTTP API | FastAPI | 0.141.1 | 本番依存 |
| ASGI サーバー | uvicorn | 0.53.0 | 本番依存 |
| API の統合テスト | httpx（FastAPI の `TestClient` が利用） | 0.28.1 | 開発依存 |
| pandas の型スタブ | pandas-stubs | 3.0.5.260914 | 開発依存 |
| 可視化 | matplotlib, seaborn | 3.11.2, 0.13.2 | 開発依存 |
| Notebook | JupyterLab, ipykernel, nbconvert | 4.6.3, 7.3.0, 7.17.1 | 開発依存 |
| Notebook の出力削除 | nbstripout | 0.9.1 | 開発依存 |

- Wiki 記事「テスト駆動開発から始める機械学習入門」と同じ組み合わせ（pandas・scikit-learn・FastAPI）にし、参照元との対応を取りやすくする。
- 可視化と Notebook は記事の探索の節でしか使わないため、開発依存にする。
- Notebook は `tools/notebooks.py` で実行（`tox -e notebook`）・出力削除（`tox -e format`）・検査（`tox -e lint`）する。CI では出力が残っていないことだけを検査し、学習データが無いため実行はしない。
- scikit-learn・joblib・seaborn は型情報を持たないため、mypy の `ignore_missing_imports` を対象モジュールに限定して設定する。

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| Polars（データフレーム） | 高速だが、scikit-learn との受け渡しと Wiki 記事との対応を考えると pandas の方が読者の参照資料が多い |
| statsmodels（回帰） | 係数の検定などは本シリーズの範囲外。scikit-learn に統一する |
| Plotly（可視化） | 対話的なグラフは Notebook の出力を消す運用と相性が悪い |
| Flask（API） | 入力バリデーションと OpenAPI を型ヒントから得られる FastAPI の方が、第 15 章の型とバリデーションの主題に合う |
| pickle（モデルの保存） | 標準ライブラリだが、NumPy 配列を含むモデルは joblib が scikit-learn の推奨 |

## 影響

- 良い影響: 参照元の Wiki 記事と同じライブラリなので、読者が参照元と見比べやすい。scikit-learn の結果を、ほかの言語の実装の突き合わせ基準にできる
- 悪い影響: 依存関係が増え、`uv sync` の時間とディスク使用量が増える。JupyterLab 一式は開発依存でも大きい
- 悪い影響: pandas 3 系はコピーオンライトなど 2 系と挙動が異なる箇所があり、2 系前提のネット上の資料がそのまま動かない場合がある。記事ではコードをすべて実行して確認する

## コンプライアンス

- `apps/python/pyproject.toml` と `uv.lock` に上記のライブラリだけが本番依存として記載されている
- `uv run tox -e all` が通り、`tools/notebooks.py verify` が Notebook の出力セルの残存を検出する
- Python CI（`.github/workflows/python-ci.yml`）がグリーンである

## 備考

- 著者: claude-code/claude-opus-5
- Kotlin 版・TypeScript 版のライブラリは、それぞれの Bolt の着手前に別の ADR で決める
