---
type: ADR
title: "009 Rust 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "Rust 版のライブラリに Cargo 標準のテスト・rustfmt・clippy・linfa 0.8.1・ndarray 0.16・axum を採用し、クレートの版をそろえる方針と自作する範囲を決める。"
tags: [adr,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T13:10:00Z }
---

# 009 Rust 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定

「機械学習から始めるプログラミング入門」Rust 版で使うライブラリを決める。

日付: 2026-09-20

## ステータス

2026-09-20 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「対象言語」では、Rust 版の機械学習ライブラリの候補を linfa・ndarray・polars としていた。第 2 波の計画では「linfa の対応範囲の確認に最も時間がかかる見込み」として Rust を最後に置き、Rust 版は Go 版・TypeScript 版と同じ「ライブラリが限られる環境」になると見込んでいた。

B44 のステップ 1 で、使い捨ての Cargo プロジェクトによって次を確かめた。**見込みは外れ、linfa は決定木からリッジ／ラッソ・交差検証まで揃っていた。**

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| linfa の版とライセンス | 0.8.1、`MIT OR Apache-2.0`。trees・linear・logistic・clustering・reduction・preprocessing・elasticnet が同じ 0.8.1 でそろう | crates.io、`Cargo.toml` の `license` |
| linfa にあるもの | 決定木（`DecisionTree`、`feature_importance()` つき）・重回帰（`LinearRegression`）・ロジスティック回帰・K-means・主成分分析（`Pca`、`explained_variance_ratio()` つき）・リッジとラッソ（`ElasticNet::ridge()`／`lasso()`）・混同行列（`confusion_matrix`）と適合率／再現率／F 値・ROC と AUC（`roc()`・`area_under_curve()`）・交差検証（`cross_validate_single`） | 使い捨てのプロジェクトで全部実行 |
| linfa に無いもの | ランダムフォレスト（linfa-trees は決定木だけ）、欠損値の補完、ダミー変数化（linfa-preprocessing にあるのは線形スケーリング・正規化・白色化・TF-IDF） | クレートのソースの `src/` と `lib.rs` |
| linfa の標準化 | `LinearScaler::standard()` は**母標準偏差**（n で割る）。scikit-learn の `StandardScaler` と同じで、gonum の `stat.StdDev`（標本標準偏差）とは違う | 1〜4 の 4 件を標準化して実測 |
| 行列 | ndarray。**linfa 0.8.1 が使うのは ndarray 0.16 で、最新は 0.17** | `cargo build` の型エラー |
| BLAS | 不要。linfa-linalg（純 Rust）で `cargo build` が通る | 同上 |
| 乱数 | **linfa は rand 0.8 系**（`linfa` が rand 0.8、`linfa-clustering` が rand_xoshiro 0.6）。rand 0.9・0.10 の RNG を `KMeans::params_with_rng` に渡すと「two types coming from two different versions of the same crate are different types」で落ちる | 同上 |
| 乱数の並び | `StdRng::seed_from_u64(0)` と Fisher-Yates で `[9, 3, 6, 4, 8, 1, 5, 2, 0, 7]`。Java（`[4 8 9 6 3 5 2 1 7 0]`）とも Go（`[6 8 2 3 7 5 9 1 0 4]`）とも違う | 使い捨てのプロジェクトで実行 |
| CSV | csv 1.4。**BOM を自動で取り除く**（`"﻿身長"` を読むと `"身長"` になる） | ヘッダーのバイト列を表示 |
| Shift_JIS | encoding_rs 0.8 の `SHIFT_JIS.decode` で読める。誤って UTF-8 として読むと `String::from_utf8` が `Err` になる | 同上 |
| API | axum 0.8.9 と tokio 1.53.1 で `Router` を作り、ポートを開けることを確認 | 同上 |
| 直列化 | serde 1.0・serde_json 1.0。`#[derive(Serialize, Deserialize)]` で構造体をそのまま JSON にできる | 同上 |
| 整形・静的解析 | `cargo fmt --check` は差分をファイル名と行番号つきで出す。clippy の既定で「unused variable」「the loop variable `i` is only used to index」「binary comparison to literal `Option::None`」を検出し、`-D warnings` で警告がエラーになる | わざと崩したファイル |
| カバレッジ | `flake.lock` の nixpkgs に cargo-llvm-cov 0.6.20 と cargo-tarpaulin 0.35.0 がある | `nix eval` |
| Nix 環境 | rustc 1.91.1、cargo 1.91.0、rustfmt 1.8.0、clippy 0.1.91、rust-analyzer。edition 2024 | `nix develop .#rust` |

### 各章で確かめた結果

| 章 | 確かめたこと |
|----|------------|
| 1 | （B44 のステップ 5 で書く） |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | Rust（edition 2024）、Cargo | 1.91 | — | 第 1 章 |
| テスト | 標準の `#[test]`（`cargo test`）と `#[cfg(test)] mod tests` | Rust と同じ | — | 第 1 章 |
| 整形 | `rustfmt`（`cargo fmt --check` で検査） | 1.8.0 | — | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | `clippy`（`cargo clippy -- -D warnings`） | 0.1.91 | — | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | `cargo-llvm-cov` | 0.6.20 | MIT OR Apache-2.0 | 第 1 章（記事での解説は第 5 章） |
| データの表現 | 構造体と `HashMap<String, String>`（セルの文字列）。polars を使わない | — | — | 第 1 章 |
| CSV | `csv` | 1.4 | Unlicense/MIT | 第 1 章 |
| 文字コード | `encoding_rs`（Shift_JIS の CSV） | 0.8 | （BSD 3 条項 / Apache-2.0 / MIT） | 第 9 章 |
| 乱数 | `rand` の `StdRng::seed_from_u64` と Fisher-Yates、`rand_xoshiro`（linfa に渡す用） | rand 0.8、rand_xoshiro 0.6 | MIT OR Apache-2.0 | 第 2 章 |
| 行列 | `ndarray` | **0.16**（0.17 にしない） | MIT OR Apache-2.0 | 第 7 章 |
| 機械学習 | linfa（trees・linear・logistic・clustering・reduction・preprocessing・elasticnet） | 0.8.1 | MIT OR Apache-2.0 | 第 3 章 |
| 直列化 | `serde`・`serde_json` | 1.0 | MIT OR Apache-2.0 | 第 8 章 |
| API | `axum` と `tokio` | axum 0.8、tokio 1 | MIT | 第 15 章 |

- **polars は使わない。** ほかの言語版と同じく、構造体と `HashMap` でデータを表す。データフレームのライブラリを使わないのはシリーズ全体の方針
- **ndarray は 0.16 に固定する。** linfa 0.8.1 が ndarray 0.16 に依存しており、0.17 を直接入れると同じ名前の別の型になって linfa に渡せない。「クレートの版が型を分ける」ことは Rust 固有の論点なので、第 7 章で明示的に扱う
- **rand は 0.8、rand_xoshiro は 0.6 に固定する。** 理由は同じ。linfa に RNG を渡す場面（K-means の初期中心）で版がそろっていないとコンパイルが通らない
- **linfa に無いものだけを自作の最終実装とする。** ランダムフォレスト（第 10 章）・欠損値の補完とダミー変数化（第 8 章）・特徴量の追加（第 9 章）が該当する
- **各章は「自作してから linfa と突き合わせる」構成にする。** Java 版（Tribuo）・C# 版（ML.NET）と同じ流れ。Go 版・TypeScript 版のような「ライブラリが無いので自作のみ」の章は少ない
- 依存は `Cargo.toml` にまとめ、使う章に入ってから追加する

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | linfa-trees の `DecisionTree` | 自作のジニ不純度の決定木と突き合わせる。`max_depth` の指定と特徴量重要度も比べる |
| 7 | ndarray と linfa-linear の `LinearRegression` | 正規方程式を ndarray で解いてから、linfa の重回帰と突き合わせる |
| 8 | linfa-trees（分類）。前処理は自作 | 欠損値の補完・ダミー変数化は linfa-preprocessing に無いので自作。分類器だけ突き合わせる |
| 9 | linfa-preprocessing の `LinearScaler::standard()` | 自作の標準化と突き合わせる（母標準偏差で一致するはず。実測で確かめる） |
| 10 | linfa-logistic の `LogisticRegression` | ロジスティック回帰は突き合わせる。ランダムフォレストは linfa に無いので自作のみ |
| 11 | linfa の `confusion_matrix`・`roc`・`cross_validate_single` | 自作の評価指標・ROC・交差検証と突き合わせる |
| 12 | linfa-elasticnet の `ElasticNet::ridge()`・`lasso()` | 自作のリッジ・ラッソと突き合わせる（目的関数の流儀の違いに注意する） |
| 13 | linfa-reduction の `Pca` | 自作の固有値分解と突き合わせる。寄与率は `explained_variance_ratio()` で取れる。符号はそろえる |
| 14 | linfa-clustering の `KMeans` | 自作の K-means と突き合わせる。シードは `rand_xoshiro` の RNG で渡す |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| polars（データフレーム） | ほかの言語版と同じく構造体と `HashMap` で表す。シリーズの方針 |
| ndarray 0.17（最新） | linfa 0.8.1 が 0.16 に依存しており、混ぜると型が合わない |
| rand 0.9・0.10（最新） | 同じ理由。linfa に RNG を渡せない |
| ndarray-linalg（BLAS バックエンド） | linfa-linalg（純 Rust）で足り、OpenBLAS の導入が要らない |
| smartcore | linfa で必要なアルゴリズムが揃っており、依存を増やす理由が無い |
| `cargo-tarpaulin` | `cargo-llvm-cov` のほうが rustc の計測機構をそのまま使う。どちらも固定の nixpkgs にある |
| actix-web・Rocket（API） | 第 15 章の主題（層の分離と統合テスト）には axum で足り、tokio との組み合わせが素直 |
| 標準ライブラリだけの HTTP サーバー | Rust の標準ライブラリに HTTP サーバーは無い。Go 版とはここが違う |

## 影響

- 良い影響: linfa が揃っているので、「自作してからライブラリと突き合わせる」という Python 版・Java 版と同じ流れをほぼ全章で書ける
- 良い影響: クレートの版が型を分けるという Rust 固有の論点（ndarray 0.16/0.17、rand 0.8/0.9）を、実際に落ちた例とともに示せる
- 良い影響: csv クレートが BOM を自動で取り除くので、ほかの言語版で毎回書いた BOM の除去が要らない。その差自体が記事の題材になる
- 悪い影響: 最新版のクレートを使えない箇所がある（ndarray・rand）。理由を第 7 章・第 2 章に明記する
- 悪い影響: 第 2 波の計画にあった「Rust 版は自作の比重が大きい」という前提が崩れたので、対比の軸を Java 版・C# 版（ライブラリが揃った静的型付け言語）に差し替える

## コンプライアンス

- `apps/rust/Cargo.toml` に記載された依存が本 ADR の表と一致している
- Rust CI（`.github/workflows/rust-ci.yml`）がグリーンである
- わざと違反を入れると `cargo fmt --check`・`cargo clippy -- -D warnings` が失敗する（B44 で確かめる）

## 備考

- 著者: claude-code/claude-opus-5
