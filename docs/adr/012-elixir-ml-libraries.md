---
type: ADR
title: "012 Elixir 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "Elixir 版のライブラリに Mix・ExUnit・mix format・Credo・mix test --cover・Nx・Scholar・NimbleCSV・codepagex・Plug と Bandit を採用し、Scholar に決定木が無いため第 3〜6 章・第 12 章・第 14 章を自作の最終実装とする方針を決める。"
tags: [adr,getting-start-ml,elixir]
status: proposed
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:00:00Z }
---

# 012 Elixir 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定

「機械学習から始めるプログラミング入門」Elixir 版で使うライブラリを決める。

日付: 2026-09-23

## ステータス

2026-09-23 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「第 3 波の執筆計画」では、Elixir 版の機械学習ライブラリの第一候補を Nx・Scholar とし、Explorer の採否（Rust のネイティブ拡張が Nix で通るか）を B60 のステップ 1 で確かめてから確定するとした。Elixir 版の対比の相手は F# 版・Clojure 版（不変のデータと関数）、Python 版（テンソルと scikit-learn にあたるもの）、Ruby 版・Clojure 版（動的型付け）である。

B60 のステップ 1 で、使い捨ての Mix プロジェクトを `nix develop .#elixir` の中で動かして次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| Nix 環境 | Elixir 1.18.4、Mix 1.18.4。`elixir-ls` は環境変数を汚さない | `nix develop .#elixir` |
| OTP | **`erl` コマンドは OTP 28（erts 16.2）だが、Elixir は自分が持つ OTP 27（erts 15.2.7.4）で動く**。Clojure 版の「`java` は 25 だが Clojure は JDK 21」と同じ形 | `System.otp_release/0`、`erl -eval` |
| 機械学習 | Scholar 0.4.2（Apache-2.0）。線形回帰・リッジ・ロジスティック回帰・SVM・K-means・主成分分析（`explained_variance_ratio` を持つ）・標準化・混同行列・適合率／再現率／F 値・ROC 曲線・AUC・交差検証・欠損値の補完・ダミー変数化がある | ビーム表の一覧、使い捨てのプロジェクトで実行 |
| **Scholar に無いもの** | **決定木とランダムフォレストが無い。ラッソも無い。** 本書の背骨である決定木が丸ごと無い | `Scholar.*` のビーム表の一覧 |
| テンソル | Nx 0.13.1。既定のバックエンドは `Nx.BinaryBackend` で EXLA は要らない。**`Nx.tensor([1.0])` の既定の型は f32**。`type: :f64` を明示しないと重回帰の係数が `0.9999998807907104` になる | 使い捨てのプロジェクトで実行 |
| データフレーム | Explorer 0.12.0 は `rustler_precompiled` が既成の NIF を取ってくるので、Nix の中でも Rust のビルドは起きなかった | `mix deps.get`・`mix compile` |
| 乱数 | `Nx.Random.key(0)` の `shuffle` は `[2, 7, 9, 6, 0, 8, 1, 3, 4, 5]`（Threefry）。Erlang の `:rand` はまた別。**どの言語版とも一致しない** | 同上 |
| CSV | NimbleCSV 1.3.0 は BOM を取り除かない。**Shift_JIS は Erlang/Elixir の標準では読めない**。Explorer に読ませると例外を投げずに文字化けする | BOM つき・Shift_JIS のファイルで確認 |
| 文字コード | codepagex 0.1 で CP932 から変換できる（`config :codepagex, :encodings, ["VENDORS/MICSFT/WINDOWS/CP932"]` の設定が要る） | 同上 |
| API | Plug 1.20＋Bandit 1.12＋Jason 1.4 で JSON を返せた | `:httpc` で叩いた |
| 直列化 | `:erlang.term_to_binary`／`binary_to_term` でマップの決定木を往復できた | 同上 |
| 整形・静的解析 | `mix format --check-formatted` は終了コード 1。Credo 1.7.19 の `mix credo --strict` は指摘があると**終了コード 4**。未使用の変数は Credo ではなくコンパイラが警告するので `mix compile --warnings-as-errors` と組み合わせる | わざと崩したファイル |
| テスト・カバレッジ | ExUnit（テスト名に日本語を使える。`@tag` と `ExUnit.configure(exclude: ...)` でスキップできる）。標準の `mix test --cover` に**既定で 90% のしきい値**がある | 使い捨てのプロジェクト |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | Elixir と Mix（`mix.exs`・`mix.lock`） | Elixir 1.18 | Apache-2.0 | 第 1 章 |
| OTP | Nix の Elixir が持つ OTP 27 | 27 | — | 第 1 章 |
| テスト | ExUnit（`mix test`）。実データのテストは `@tag :data` で外せるようにする | 標準 | Apache-2.0 | 第 1 章 |
| 整形 | `mix format --check-formatted` | 標準 | Apache-2.0 | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | Credo（`mix credo --strict`）と `mix compile --warnings-as-errors` | Credo 1.7 | MIT | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | `mix test --cover`（標準） | 標準 | Apache-2.0 | 第 1 章（記事での解説は第 5 章） |
| データの表現 | 素のマップとリスト（列名のアトムをキーにしたマップの並び） | — | — | 第 1 章 |
| CSV | NimbleCSV（BOM は自分で取り除く） | 1.3 | Apache-2.0 | 第 1 章 |
| 文字コード | codepagex（CP932 の CSV） | 0.1 | BSD-2-Clause | 第 9 章 |
| テンソル | Nx（`type: :f64` を明示する） | 0.13 | Apache-2.0 | 第 3 章 |
| 乱数 | **`java.util.Random` と同じ線形合同法を自作する**（下記） | — | — | 第 2 章 |
| 機械学習 | Scholar | 0.4.2 | Apache-2.0 | 第 7 章 |
| 直列化 | `:erlang.term_to_binary`（学習済みモデル）、Jason（API の JSON） | Jason 1.4 | Apache-2.0 | 第 8 章 |
| API | Plug と Bandit。経路の振り分けはハンドラーの関数の中で書く | Plug 1.20、Bandit 1.12 | Apache-2.0 / MIT | 第 15 章 |

- **Explorer は使わない。** ネイティブ拡張は Nix の中でも取得できたが、データフレームのライブラリを使わないのはシリーズ全体の方針である。表は素のマップとリストで表し、CSV は NimbleCSV で読む
- **EXLA は使わない。** `Nx.BinaryBackend` で本書の規模の計算は足りる。EXLA を入れると Nix の中で巨大な依存（XLA のバイナリ）を抱えることになり、記事の「動かしてみる」の敷居が上がる
- **Nx のテンソルは `type: :f64` を明示する。** 既定の f32 のままではほかの言語版と数値を突き合わせられない。**この「既定の型が単精度である」ことは、Elixir 版でいちばん最初に書く見どころにする**（Python 版の NumPy は既定が f64 なので、ちょうど裏返しになる）
- **決定木とランダムフォレストは自作が最終実装になる。** Scholar に無い。**本書の背骨である決定木が丸ごと無いので、ライブラリとの突き合わせは線形回帰・ロジスティック回帰・K-means・主成分分析・評価指標・前処理に限られる。** これはほかのどの言語版よりも置き換えの範囲が狭い。記事ではこれを「ライブラリが育っていない領域では、自作がそのまま本番の実装になる」例として正面から扱う
- **乱数は `java.util.Random` と同じ線形合同法を自作する。** `Nx.Random`（Threefry）も Erlang の `:rand` もほかの言語版と並びが合わない。そこで**シリーズで初めて「乱数生成器そのものを自作する」**ことにし、48 ビットの線形合同法（乗数 `0x5DEECE66D`、加数 11）と `nextInt(bound)` の棄却、Fisher-Yates を書く。**シード 0 の並びが `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]` になり、Java 版・Kotlin 版・Scala 版・Clojure 版と一致することを実測で確かめた。** ライブラリが無いからではなく、**ほかの言語版と数値を突き合わせるために自作する**という、この版でいちばん面白い選択になる
- **型（`@spec`・Dialyzer）は使わない。** Ruby 版で RBS・Steep を使わなかったのと同じ扱いで、動的型付けの言語では「型の誤りをテストで捕まえる」ことを示す。使わない理由は第 5 章に書く
- **Phoenix は使わない。** 第 15 章の主題（層の分離と統合テスト）には Plug のハンドラーだけで足りる
- **各章は「自作してから Scholar と突き合わせる」構成にする。** 突き合わせられない章では、そのことを明記して自作の妥当性をテストで示す

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | **無し（Scholar に決定木が無い）** | 自作のジニ不純度の決定木が最終実装。ライブラリが無いことを章の中で明記する |
| 4〜6 | 無し | 道具立ての章。`mix format`・Credo・`mix test --cover`・Nix の環境定義を扱う |
| 7 | `Scholar.Linear.LinearRegression` | 正規方程式を自作してから突き合わせる。f32 と f64 の違いをここで見せる |
| 8 | 前処理は `Scholar.Impute.SimpleImputer`・`Scholar.Preprocessing.OneHotEncoder`。決定木は自作のまま | クラスの重みは Scholar に口が無いので自作 |
| 9 | `Scholar.Preprocessing.StandardScaler` | 自作の標準化と突き合わせる（n で割るか n−1 かを実測で確かめる） |
| 10 | `Scholar.Linear.LogisticRegression` | ランダムフォレストは無いので自作のまま。ロジスティック回帰だけ突き合わせる |
| 11 | `Scholar.Metrics.Classification`・`Regression`・`ModelSelection` | 自作の評価指標・ROC・交差検証と突き合わせる。**一部の指標は f64 を渡しても内部で f32 に落ちる**ことを確かめる |
| 12 | `Scholar.Linear.RidgeRegression` | ラッソは無いので自作のまま。リッジだけ突き合わせる |
| 13 | `Scholar.Decomposition.PCA` | 寄与率まで持っているので、自作の主成分分析と全面的に突き合わせられる |
| 14 | `Scholar.Cluster.KMeans` | 自作の K-means と突き合わせる |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| EXGBoost（決定木・勾配ブースティング） | 決定木の穴を埋められるが、XGBoost のネイティブライブラリを抱えることになり、本書の第 3 章が扱う「ジニ不純度の決定木」とは別物（勾配ブースティング木）になる。自作との突き合わせにならない |
| Explorer（データフレーム） | シリーズの方針でデータフレームのライブラリを使わない |
| EXLA（テンソルのバックエンド） | `Nx.BinaryBackend` で足りる。依存が巨大になる |
| Phoenix（API） | 第 15 章の主題には Plug と Bandit だけで足りる |
| Cowboy（HTTP サーバー） | Bandit は Elixir で書かれていて設定が素直。Plug の標準的な選択肢として定着している |
| excoveralls（カバレッジ） | 標準の `mix test --cover` にしきい値の仕組みがあり、依存を増やす理由が無い |
| Dialyzer・`@spec`（型） | 動的型付けの言語で「型をテストで捕まえる」ことを示すのが Elixir 版の軸の一つ。Ruby 版の RBS・Steep と同じ扱い |
| `Nx.Random`・Erlang の `:rand`（乱数） | どちらもほかの言語版と並びが合わない。`java.util.Random` と同じ線形合同法を自作すれば、並びを記事の中で完全に説明でき、JVM の言語版とも一致する |

## 影響

- 良い影響: パイプライン演算子・パターンマッチ・関数節による書き方が、F# 版・Clojure 版との対比としてそのまま記事の題材になる
- 良い影響: Nx の f32／f64 の話が、シリーズで初めて「数値の型が既定で単精度である」ことを扱う機会になる
- 良い影響: 乱数を自作するので、分割の結果を記事の中で完全に再現でき、しかも Java 版・Kotlin 版・Scala 版・Clojure 版と同じ並びになる。「乱数生成器は魔法ではなく 3 行の漸化式である」ことを第 2 章で示せる
- 悪い影響: **Scholar に決定木が無いため、ライブラリとの突き合わせがほかの言語版より大幅に少ない。** 第 3〜6 章・第 10 章の一部・第 12 章の一部・第 14 章は自作だけで完結する。これは Elixir の機械学習の生態系の現状であり、記事にそのまま書く
- 悪い影響: `erl` コマンドの OTP の版（28）と Elixir が動く版（27）が食い違うので、記事の環境構築の節に書く必要がある
- 悪い影響: Shift_JIS のために codepagex を入れ、設定ファイル（`config/config.exs`）でどの符号化表を組み込むかを指定する必要がある。設定を忘れると実行時に `{:error, "Unknown encoding ..."}` になる
