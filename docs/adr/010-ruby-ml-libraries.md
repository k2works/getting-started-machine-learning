---
type: ADR
title: "010 Ruby 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "Ruby 版のライブラリに Bundler・Minitest・RuboCop・SimpleCov・Rumale 2.2・numo-narray-alt・Sinatra を採用し、Nix の Ruby 3.3 を前提にする方針と自作する範囲を決める。"
tags: [adr,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T00:00:00Z }
---

# 010 Ruby 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定

「機械学習から始めるプログラミング入門」Ruby 版で使うライブラリを決める。

日付: 2026-09-22

## ステータス

2026-09-22 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「第 3 波の執筆計画」では、Ruby 版の機械学習ライブラリの第一候補を Rumale とし、B50 のステップ 1 で確かめてから確定するとした。Ruby 版の対比の相手は Python 版（動的型付けと scikit-learn に似た API）と、TypeScript 版・Kotlin 版（型の検査の有無）である。

B50 のステップ 1 で、使い捨ての Bundler プロジェクトを `nix develop .#ruby` の中で動かして次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| Nix 環境 | Ruby 3.3.10、Bundler 2.7.2、RubyGems 3.7.2。手元の `ruby` は macOS の 2.6.10 | `nix develop .#ruby` |
| Rumale の版とライセンス | 2.2.0（2026-07-05）、BSD-3-Clause。`rumale-tree`・`rumale-ensemble`・`rumale-linear_model` などの gem に分かれ、すべて 2.2.0 でそろう | rubygems.org の API、`bundle list` |
| Rumale にあるもの | 決定木（`Tree::DecisionTreeClassifier`、`feature_importances` つき）・ランダムフォレスト（`Ensemble::RandomForestClassifier`）・線形回帰・ロジスティック回帰・リッジ・ラッソ（`LinearModel`）・K-means（`Clustering::KMeans`）・PCA（`Decomposition::PCA`）・標準化（`Preprocessing::StandardScaler`）・混同行列（`EvaluationMeasure.confusion_matrix`）・適合率／再現率／F 値（`Precision`・`Recall`・`FScore`）・ROC AUC（`ROCAUC`）・交差検証（`ModelSelection::CrossValidation` と `KFold`） | 使い捨てのプロジェクトで全部実行 |
| Rumale に無いもの | PCA の寄与率。`PCA` が持つのは `components` と `mean` だけ | `instance_methods`・`instance_variables` |
| 行列 | Rumale 2.x は `numo-narray-alt` 0.11.2（2026-08-12）に依存する。本家の `numo-narray` は 0.9.2.1（2022-08-20）で止まっており、両方を入れると衝突の警告が出る。ネイティブ拡張は Nix の中でビルドできた | `bundle install` の警告、`Gemfile.lock` |
| 乱数の並び | `(0..9).to_a.shuffle(random: Random.new(0))` は `[2, 8, 4, 9, 1, 6, 7, 3, 0, 5]` | 使い捨てのプロジェクトで実行 |
| CSV | csv 3.3.6。`CSV.read`（ファイルを開く）は BOM を取り除くが、`CSV.parse(File.read(...))` は先頭の見出しに BOM が残る。Shift_JIS は `encoding: "Shift_JIS:UTF-8"` で読め、指定しないと `CSV::InvalidEncodingError` | ヘッダーを表示 |
| API | Sinatra 4.2.1、Puma 8.0.2、rackup | 同上 |
| 直列化 | `Marshal` で学習済みの決定木を保存・復元して同じ予測が出る | 同上 |
| 整形・静的解析 | RuboCop 1.91.0。既定で「未使用の変数」「`nil` との比較」など 6 件を検出。`NewCops: enable` を書かないと、新しいルールごとの警告が大量に出る | わざと崩したファイル |
| テスト・カバレッジ | Minitest 6.0.6（`skip` がある）、SimpleCov 1.3.0 | `bundle list` |

### 各章で確かめた結果

| 章 | 確かめたこと |
|----|------------|
| 1 | `CSV.foreach`／`CSV.read` はファイルを開くときに BOM を取り除く。`module_function` のモジュールをテストのクラスに `include` すると、章の `run` がプライベートなインスタンスメソッドとして入り、`Minitest::Test#run` を上書きして全テストが `NoMethodError` で落ちる。テストでは `include` せずにモジュールの関数として呼ぶ |
| 2 | `Array#shuffle(random: Random.new(0))` の並びは `[2, 8, 4, 9, 1, 6, 7, 3, 0, 5]`。乱数の並びを決めるのは Ruby 本体なので、固定しているのは `Gemfile.lock` ではなく Ruby の版（2.6.10 と 3.3.10 で同じ並び）。件数 105 件・45 件はほかの言語版と一致し、訓練データの平均値は一致しない |
| 3 | `tally` は出現順を保ち、`max_by` は同点のとき最初の要素を返す（Rust の `max_by_key` は最後）。`sort_by` は安定ではないので、元の位置を 2 つ目の鍵にする。Rumale の決定木は節ごとに特徴量を `Array#sample` でランダムな順に並べるので、同じ不純度の分割が並ぶと自作と違う分割を選ぶ（深さ 1〜5 は予測が一致、制限なしで 1 件分かれる）。予測時の比較は自作と同じ `<=` |
| 4〜6 | **Nix の環境で `RUBYLIB` に Solargraph の依存の gem が並び、`bundle exec` が `Gemfile.lock` より古い parser・prism・rubocop-ast を読み込んでいた**（検査は通るので気付けない。`rubocop -V` の表示で気付いた）。環境定義の `shellHook` で `unset RUBYLIB` して直した。RuboCop 1.91 は `NewCops: enable` を書かないと 163 個の cop について警告が出る。`Gemfile.lock` には `bundle lock --add-platform x86_64-linux` で CI 用の Linux を加えた |
| 7 | **`Rumale::LinearModel::LinearRegression` は `Numo::Linalg` が読み込まれていないと L-BFGS（`tol` 1e-4）で解き、`solver: "svd"` を指定しても黙って L-BFGS に落ちる。** 既定のままでは切片 0.0044（自作は 6035.99）、テストの決定係数 −1.246 になり、例外も警告も出ない。`tol: 1e-10` で自作との差は 3.5e-5 以内になる。Numo の `work[i, true]` はビューを返すので、行の入れ替えで `dup` を忘れると黙って同じ行になる |
| 8 | Rumale に欠損値の補完は無く、`OneHotEncoder` は整数のカテゴリだけを受け取り、先頭のカテゴリを落とせない。決定木にクラスの重みも無い。前処理と重み付きの決定木は自作が最終実装。`Marshal` は `Data`・配列を鍵にした `Hash`・第 3 章の木をそのまま保存できる（無名クラスは保存できない）。RuboCop の `Security/MarshalLoad` は理由を書いて 1 行だけ無効にした。`Data` は属性の差し替えを防ぐだけで、属性が指すオブジェクトは変えられる（同じパイプラインを 2 回学習すると 1 回目のモデルが上書きされた。`dup` で直した） |
| 9 | **Rumale の `StandardScaler` は n−1 で割る標本標準偏差**（Numo の `stddev`）。scikit-learn・linfa（n で割る）とは √((n−1)/n) 倍ずれる。`PolynomialFeatures` は先頭の定数項を除けば自作と一致する。csv gem は文字コード名を打ち間違えても警告を出して読み進める。Numo に連立方程式を解く関数は無い（`numo-linalg` が要る）ので、正規方程式は自作 |
| 10 | ロジスティック回帰の既定値は `reg_param: 1.0`・`tol: 1e-4` で切片も正則化する。`reg_param: 0.0`・`tol: 1e-8` で自作と予測が完全に一致する。ランダムフォレストは節ごとに `max_features`（既定は √特徴量数）の列を調べる（自作は木ごと）。**Rumale のモデルは `random_seed` を省くと既定値が `srand` になり、モデルを作るだけでプロセス全体の乱数の種が入れ替わる**ので、必ずシードを渡す。RuboCop の `Style/Sample` に従って `shuffle(random:).first(n)` を `sample(n, random:)` に直すと、同じシードでも選ばれる列が変わる |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・依存管理 | Ruby（Nix の環境）、Bundler（`Gemfile.lock` をコミットする） | 3.3、Bundler 2.7 | — | 第 1 章 |
| タスク | Rake | Ruby に同梱 | MIT | 第 1 章 |
| テスト | Minitest（`skip` で実データのテストを飛ばす） | 6.0 | MIT | 第 1 章 |
| 整形・静的解析 | RuboCop（`NewCops: enable`） | 1.91 | MIT | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | SimpleCov | 1.3 | MIT | 第 1 章（記事での解説は第 5 章） |
| データの表現 | `Hash` と `Struct`／`Data`。データフレームのライブラリは使わない | — | — | 第 1 章 |
| CSV | `csv` gem（`Gemfile` に書く） | 3.3 | Ruby / BSD-2-Clause | 第 1 章 |
| 乱数 | `Random.new(seed)` と `Array#shuffle(random:)` | Ruby と同じ | — | 第 2 章 |
| 行列 | `numo-narray-alt`（Rumale の依存として入る。本家の `numo-narray` は入れない） | 0.11 | BSD-3-Clause | 第 7 章 |
| 機械学習 | Rumale | 2.2 | BSD-3-Clause | 第 3 章 |
| 直列化 | `Marshal`（学習済みモデル）、`json`（API の入出力） | Ruby と同じ | — | 第 8 章 |
| API | Sinatra（`Sinatra::Base`）と Puma、rackup | Sinatra 4.2、Puma 8.0 | MIT / BSD-3-Clause | 第 15 章 |

- **Nix の Ruby 3.3 を前提にする。** 手元の macOS の Ruby 2.6 では動かさない。記事の環境構築の節は `nix develop .#ruby` を前提に書く
- **型注釈（RBS・Steep）は使わない。** Ruby 版の対比の軸の 1 つが「実行時に型を検査しない言語で、型の誤りをテストで捕まえる」ことなので、使わない理由を記事に書く
- **`numo-narray` を直接入れない。** Rumale 2.x が依存するのはフォークの `numo-narray-alt` で、両方を入れると衝突する。本家の保守が 2022 年で止まっていることとあわせて第 7 章で扱う
- **CSV はファイルから読む。** `CSV.read`／`CSV.foreach` は BOM を取り除くので、ほかの言語版で書いてきた BOM の除去が要らない。文字列から読むときは `encoding: "bom|utf-8"` を使う
- **各章は「自作してから Rumale と突き合わせる」構成にする。** Python 版（scikit-learn）と同じ流れ
- **Rumale に無いものだけを自作の最終実装とする。** 現時点では PCA の寄与率（第 13 章）が該当する。欠損値の補完・ダミー変数化（第 8 章）の有無は、その章で確かめる

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | `Rumale::Tree::DecisionTreeClassifier` | 自作のジニ不純度の決定木と突き合わせる。`max_depth` と特徴量重要度も比べる |
| 7 | `Rumale::LinearModel::LinearRegression` と Numo | 正規方程式を自作してから、Rumale の重回帰と突き合わせる |
| 8 | Rumale の決定木。前処理は章の中で確かめる | 欠損値の補完・ダミー変数化が Rumale に無ければ自作 |
| 9 | `Rumale::Preprocessing::StandardScaler` | 自作の標準化と突き合わせる（母標準偏差か標本標準偏差かを実測で確かめる） |
| 10 | `LinearModel::LogisticRegression`・`Ensemble::RandomForestClassifier` | ロジスティック回帰とランダムフォレストを自作と突き合わせる。正則化の既定値を確かめる |
| 11 | `EvaluationMeasure`・`ModelSelection::CrossValidation` | 自作の評価指標・ROC AUC・交差検証と突き合わせる |
| 12 | `LinearModel::Ridge`・`Lasso` | 自作のリッジ・ラッソと突き合わせる（目的関数の流儀の違いに注意する） |
| 13 | `Decomposition::PCA` | 固有ベクトルを突き合わせる。寄与率は Rumale に無いので自作 |
| 14 | `Clustering::KMeans` | 自作の K-means と突き合わせる |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| RSpec | Minitest で足り、Ruby に近い素朴な書き方がほかの言語版の xUnit 系と比べやすい |
| RBS・Steep（型注釈） | Ruby 版の対比の軸（実行時に型を検査しない）を損なう |
| `numo-narray`（本家） | 2022 年から更新が無く、Rumale 2.x の依存（`numo-narray-alt`）と衝突する |
| Daru・Polars（データフレーム） | ほかの言語版と同じく `Hash` と構造体で表す。シリーズの方針 |
| Rails・Roda（API） | 第 15 章の主題（層の分離と統合テスト）には Sinatra で足りる |
| WEBrick だけの HTTP サーバー | Ruby 3.0 から標準から外れており、Sinatra と Rack のほうが統合テストを書きやすい |

## 影響

- 良い影響: Rumale がそろっているので、Python 版と同じ「自作してからライブラリと突き合わせる」流れをほぼ全章で書ける
- 良い影響: `CSV.read` が BOM を取り除くので、ほかの言語版との差自体が記事の題材になる
- 悪い影響: 手元の macOS の Ruby では動かないので、Nix を使わない読者には Ruby 3.3 の導入を別途案内する必要がある
- 悪い影響: Rumale は作者 1 人が保守しており、行列ライブラリも作者のフォークに依存している。保守の持続性をリスクとして記事に書く
