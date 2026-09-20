---
type: ADR
title: "009 Rust 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "Rust 版のライブラリに Cargo 標準のテスト・rustfmt・clippy・linfa 0.8.1・ndarray 0.16・axum を採用し、クレートの版をそろえる方針と自作する範囲を決める。"
tags: [adr,getting-start-ml,rust]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T13:10:00Z }
---

# 009 Rust 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定

「機械学習から始めるプログラミング入門」Rust 版で使うライブラリを決める。

日付: 2026-09-20

## ステータス

2026-09-20 提案されました

2026-09-21 承認されました（第 1〜15 章の執筆で確かめました）

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
| CSV | csv 1.4。**BOM を自動で取り除く**（`"\uFEFF身長"` を読むと `"身長"` になる） | ヘッダーのバイト列を表示 |
| Shift_JIS | encoding_rs 0.8 の `SHIFT_JIS.decode` で読める。誤って UTF-8 として読むと `String::from_utf8` が `Err` になる | 同上 |
| API | axum 0.8.9 と tokio 1.53.1 で `Router` を作り、ポートを開けることを確認 | 同上 |
| 直列化 | serde 1.0・serde_json 1.0。`#[derive(Serialize, Deserialize)]` で構造体をそのまま JSON にできる | 同上 |
| 整形・静的解析 | `cargo fmt --check` は差分をファイル名と行番号つきで出す。clippy の既定で「unused variable」「the loop variable `i` is only used to index」「binary comparison to literal `Option::None`」を検出し、`-D warnings` で警告がエラーになる | わざと崩したファイル |
| カバレッジ | `flake.lock` の nixpkgs に cargo-llvm-cov 0.6.20 と cargo-tarpaulin 0.35.0 がある | `nix eval` |
| Nix 環境 | rustc 1.91.1、cargo 1.91.0、rustfmt 1.8.0、clippy 0.1.91、rust-analyzer。edition 2024 | `nix develop .#rust` |

### 各章で確かめた結果

| 章 | 確かめたこと |
|----|------------|
| 1 | csv クレートが BOM を自動で取り除くので、ほかの言語版で書いてきた BOM の除去が要らない。`#[cfg(test)] mod tests` で実装と同じファイルにテストを置ける。関数名に日本語を使えるが、**識別子に空白は入れられない**（`fn linfa の…` はコンパイルエラー）。標準のテストにスキップが無いので、実データのテストは早期に戻る形にし、走ったかはカバレッジの差（データあり 70.09%、なし 51.96%）で見る。Cargo のテストはパッケージのルートで走るので相対パスの `../data/sukkiri-ml` が届く |
| 2 | 欠損値は `Result<Option<f64>>` で表せる（Go 版の「値と ok とエラー」の 3 値より素直）。`f64` を持つ型には `Eq` が付かないので `PartialEq` だけを derive する。`rand` は linfa に合わせて 0.8 系に固定する。`StdRng::seed_from_u64(0)` の Fisher-Yates は `[9, 3, 6, 4, 8, 1, 5, 2, 0, 7]` で、Java 版・Go 版と分かれる行も訓練データの平均値も一致しない（件数 105 件・45 件は一致する） |
| 3 | 決定木は `enum Tree { Leaf, Node }` と `Box` で表せ、`match` の網羅性をコンパイラが検査する（Go 版の既定の分岐が要らない）。**`max_by_key` は同点のとき最後の要素を返す**ので、「同数なら先に現れたほう」にするには厳密な不等号で書く（実際に 2 件のテストが落ちて気づいた）。`f64` は `Ord` ではないので `sort_by` に `partial_cmp` を使う。linfa にラベルを渡すには整数への符号化が要る |
| 4〜6 | **`rand` 0.8.8 の `StdRng` は「再現可能と考えるべきではない」とドキュメントが明言している**（実体は ChaCha12。再現が要るなら `rand_chacha` を直接使えとある）。Rust 版の数値の再現性は `Cargo.lock` をコミットしていることに依存する。`rustfmt` は CRLF を指摘しない（`newline_style = Auto`。Go 版の `gofmt` は指摘する）ので `.gitattributes` に `apps/rust/** text=auto eol=lf` を足した。カバレッジのリージョン数は rustc の版で変わる（1.91.1 で 1758、1.97.1 で 1714）ので、CI と手元の数字を比べない。テストは 47 件で、データなしでも全部 `ok` と出る（スキップの表示が無い）ため、`--nocapture` の `eprintln!` かカバレッジの差（データあり 85.67%、なし 73.66%）で判別する。clippy の実測の指摘は `needless_bool`・`ptr_arg`・`needless_range_loop`・`partialeq_to_none` など |
| 7 | ndarray には連立方程式を解く関数が無い（`ndarray-linalg` は LAPACK を要求する）ので、正規方程式は掃き出し法を自作した。**linfa-linear の `LinearRegression::default()` は素の最小二乗**で、自作の切片・係数と実データでも 1e-6 以内で一致した（Tribuo のようにトレーナーを選ぶ手間が無い） |
| 8 | **linfa-trees は非決定的なことがある。** Survived.csv では同じデータ・同じ深さでも実行ごとにテストの正解率が 0.788〜0.810 で揺れる（ダミー変数で同じ不純度の分割候補が並ぶため。iris では揺れない）。出力を固定するテストの対象にできないので、`run` の出力から linfa の行を外し、範囲で確かめるテストにした。**自作のほうが再現性が高いという逆転が起きている。** serde は trait object を保存できない（`Deserialize` が実装されていないというコンパイルエラーになる）ので、学習済みの前処理は `enum` で表す |
| 9 | **linfa-preprocessing の `LinearScaler::standard()` は母標準偏差（n で割る）で確定した。** 架空の値でも実データ（Boston の RM 列 30 件）でも自作と 1e-12 以内で一致する。scikit-learn と同じで、Tribuo・gonum（標本標準偏差）とは違う。encoding_rs の `decode` は 3 つ組（`Cow<str>`・使った符号化・`had_errors`）を返し、Shift_JIS を UTF-8 として読むと `had_errors == true`、`String::from_utf8` は `Err` になる。linfa に 1 列だけ渡す API は無いので、1 列の `Array2<f64>` と `Array1<()>` で `DatasetBase::from` を使う |
| 10 | **linfa-logistic の `MultiLogisticRegression` は既定で L2 の強さ `alpha` が 1.0。** 既定のままだと自作（正則化なし）と予測が 4 件ずれる。`alpha` を 1e-8 にするとテストデータ 45 件の予測が完全に一致する。繰り返しは既定の 100 回（L-BFGS）で収束しており、Tribuo の既定 5 エポックのような不足は起きない。**`String` のラベルをそのまま渡せる**（第 3 章の決定木のような番号付けが要らない）。学習済みモデルのメソッド名は `classes()`（`labels()` ではない）。ランダムフォレストは linfa 0.8.1 に無いので自作が最終実装 |
| 11 | linfa の `confusion_matrix`・`roc`・`area_under_curve`・`cross_validate_single` を自作と突き合わせられる。ROC には `linfa::dataset::Pr` 型の確率と `&[bool]` の正解を渡す |
| 12 | linfa-elasticnet の `ElasticNet::ridge()`・`lasso()` と突き合わせられる。目的関数の流儀（平均で割るかどうか）が違うので、alpha の値はそのまま比べられない |
| 13 | linfa-reduction の `Pca` は `explained_variance_ratio()` で寄与率、`components()` で固有ベクトルを返す。符号の向きはそろわないので、自作と比べるときはそろえる。乱数を使わないので、寄与率はほかの言語版と一致する |
| 14 | **linfa-clustering に渡す RNG は rand 0.8 系（`rand_xoshiro` 0.6 の `Xoshiro256Plus`）でなければならない。** rand 0.9 以降の `SmallRng` を `KMeans::params_with_rng` に渡すと「two types coming from two different versions of the same crate」で落ちる。linfa は k-means++ で初期中心を選ぶので、クラスタ数が多いと自作（ランダムな初期化）より SSE が小さいことが多いが、絶対ではない（実データのクラスタ数 9 では自作のほうが小さかった）。SSE は linfa が返さないので、中心を受け取って自作と同じ式で測り直す |
| 15 | axum 0.8 の `Json` 抽出器は JSON として読めないと既定で 400 を返すので、422 に統一するには生の `Request` から自分で `extract` する。ハンドラーで共有する状態は `Arc` で包み、置き場の trait に `+ Send + Sync` を課す（満たさない型はコンパイルが通らない）。`(StatusCode, Json<T>)` のタプルがそのまま応答になる。各章の `run` は同期の関数なので、`tokio::runtime::Runtime::new()` と `block_on` で非同期の世界に入る |

### linfa の決定木（`linfa-trees` 0.8.1）の既定値と自作との違い

第 3 章で自作と突き合わせた結果、深さ 1・2 では予測が完全に一致し、深さ 3 では正解率が同じ（0.9111）でも予測が 2 件分かれ、深さ 4 以上は正解率も分かれた（自作 0.9111、linfa 0.8444）。ソース（`decision_trees/hyperparams.rs`・`algorithm.rs`）を読んで理由を確かめた。

| 項目 | linfa | 自作 |
|------|-------|------|
| 既定の停止条件 | `min_weight_split: 2.0`、`min_weight_leaf: 1.0`、**`min_impurity_decrease: 1e-5`** | 深さの上限だけ |
| 枝刈りを切れるか | **切れない。** `min_impurity_decrease` に `f64::EPSILON` 未満（0 を含む）を渡すと `ParamGuard::check_ref` が `Err` を返す | — |
| 学習と予測の比較 | **学習は `<= split_value`、予測は `< split_value`** で、境界とちょうど等しい値の扱いが逆になる | どちらも `<=` |
| 同じ値とみなす差 | 固定の `1e-5` | `f64::EPSILON` |
| 分割の同点 | `score < best_score` で先勝ち（自作と同じ） | 先勝ち |
| 葉のラベルの同点 | `find_modal_class` が `HashMap` を走査するので順に依存しうる | 先に現れたほう |

深さ 3 以上で分かれる主因は `min_impurity_decrease: 1e-5` の事前枝刈りである。

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
| 乱数 | `rand` の `StdRng::seed_from_u64` と Fisher-Yates、`rand_xoshiro`（linfa に渡す用） | rand 0.8、rand_xoshiro 0.6（`Cargo.lock` で固定） | MIT OR Apache-2.0 | 第 2 章 |
| 行列 | `ndarray` | **0.16**（0.17 にしない） | MIT OR Apache-2.0 | 第 7 章 |
| 機械学習 | linfa（trees・linear・logistic・clustering・reduction・preprocessing・elasticnet） | 0.8.1 | MIT OR Apache-2.0 | 第 3 章 |
| 直列化 | `serde`・`serde_json`（trait object は保存できないので、学習済みの前処理は `enum` で表す） | 1.0 | MIT OR Apache-2.0 | 第 8 章 |
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
| 8 | linfa-trees（分類）。前処理は自作 | 欠損値の補完・ダミー変数化は linfa-preprocessing に無いので自作。分類器の突き合わせは、linfa 側が非決定的なので範囲で確かめる |
| 9 | linfa-preprocessing の `LinearScaler::standard()` | 自作の標準化と突き合わせる（母標準偏差で一致することを実測で確かめた） |
| 10 | linfa-logistic の `MultiLogisticRegression`（`alpha` を 1e-8 にする） | ロジスティック回帰は突き合わせる。既定の `alpha` は 1.0 なので、正則化なしの自作と比べるなら下げる。ランダムフォレストは linfa に無いので自作のみ |
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
