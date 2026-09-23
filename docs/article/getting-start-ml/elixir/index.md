# 機械学習から始める Elixir 入門

Elixir は Erlang VM（BEAM）の上で動く関数型の言語です。不変のデータ（マップ・リスト）をパイプライン演算子 `|>` でつなぎ、パターンマッチと関数節で分岐を表します。型を宣言しないという点では Python・Ruby・Clojure と同じ動的型付けですが、引数のパターンで関数の呼び分けが決まるところが大きく違います。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、Elixir の数値計算ライブラリ Nx と機械学習ライブラリ Scholar と突き合わせながら、Elixir の書き方とエコシステムを学びます。

Elixir 版は 3 つの軸で対比します。1 つは [F# 版](../fsharp/index.md)・[Clojure 版](../clojure/index.md) で、不変のデータを関数で変換していく流儀が近いところです。2 つめは [Python 版](../python/index.md) で、Nx は NumPy に、Scholar は scikit-learn にあたります。3 つめは [Ruby 版](../ruby/index.md)・[Clojure 版](../clojure/index.md) で、型を宣言しない言語として、テストが唯一の安全網になる点が同じです。

## 特徴

- **表を素のマップとリストで表す**: データフレームのライブラリを使わず、列名をアトムにしたマップの並び（`%{身長: 170, 体重: 60, 年代: 20, 派閥: "きのこ"}` のリスト）でデータを持つ。アトムのキーには日本語をそのまま使える
- **パターンマッチと関数節で分岐する**: `if` を書かずに、引数の形が違う関数節を並べて呼び分ける。`def predict_by_rule(%{年代: 20}), do: ...` のように、値そのものをパターンに書ける
- **パイプライン演算子でつなぐ**: `path |> File.read!() |> parse()` のように、左の値を右の関数の第 1 引数に渡していく。F# 版の `|>`・Clojure 版のスレッディングマクロと同じ発想
- **型を宣言しない**: `@spec` も Dialyzer も使わない。型の誤りや綴りの間違いは、実行して初めて分かる（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）
- **失敗は例外で表す**: `raise ArgumentError, "..."` を使う。Rust 版の `Result`・Go 版の `error` とは逆の流儀で、Ruby 版・Clojure 版に近い

## ほかの版との違い

- Livebook による探索と可視化の節は設けません。グラフは [Python 版](../python/index.md) と [Kotlin 版](../kotlin/index.md) の可視化の節を参照してください
- 総合演習（付録 A）の解答例は作りません。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Elixir で取り組んでください
- **Scholar には決定木とランダムフォレストがありません。** 本シリーズの背骨である決定木が丸ごと無いので、第 3 章の決定木は自作したものがそのまま最終実装になります。ライブラリと突き合わせられるのは線形回帰・ロジスティック回帰・K-means・主成分分析・評価指標・前処理の章だけです。これはほかのどの言語版よりも狭い範囲です（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）
- **乱数は `java.util.Random` と同じ線形合同法を自作します。** Nx の `Nx.Random`（Threefry）も Erlang の `:rand` も、ほかの言語版と並びが合いません。そこで乱数生成器そのものを自作することにしました。その結果、第 2 章以降の分割は [Java 版](../java/index.md)・[Kotlin 版](../kotlin/index.md)・[Scala 版](../scala/index.md)・[Clojure 版](../clojure/index.md) と一致します
- **Nx のテンソルは既定が単精度（f32）です。** `type: :f64` を明示しないと、重回帰の係数が `0.9999998807907104` のようにずれます。NumPy が既定で倍精度なのとちょうど裏返しです
- CSV は NimbleCSV を使いますが、**BOM は取り除かれません**。先頭の列名から自分で取り除きます
- `erl` コマンドは OTP 28 ですが、**Elixir は自分が持つ OTP 27 で動きます**（`System.otp_release()` が `"27"` を返します）

## 開発環境

| ツール | 用途 |
|--------|------|
| [Elixir](https://elixir-lang.org/) 1.18・[Mix](https://hexdocs.pm/mix/Mix.html) | 言語・依存管理・タスク |
| [ExUnit](https://hexdocs.pm/ex_unit/ExUnit.html) | テスティングフレームワーク（`mix test`） |
| `mix format` | 整形（`mix format --check-formatted` で検査） |
| [Credo](https://github.com/rrrene/credo)・`mix compile --warnings-as-errors` | 静的解析（未使用の変数はコンパイラが警告する） |
| `mix test --cover` | カバレッジ（標準。しきい値は `test_coverage: [summary: [threshold: ...]]` に書く） |

Nix の環境（`nix develop .#elixir`）は Elixir 1.18.4・Mix 1.18.4・elixir-ls です。

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [NimbleCSV](https://github.com/dashbitco/nimble_csv) | CSV の読み込み（BOM は自分で取り除く） | 第 1 章 |
| 自作の線形合同法 | 乱数（訓練データとテストデータの分割） | 第 2 章 |
| [Nx](https://github.com/elixir-nx/nx) | テンソル（`type: :f64` を明示する） | 第 3 章 |
| [Scholar](https://github.com/elixir-nx/scholar) | 線形回帰・ロジスティック回帰・K-means・主成分分析・評価指標・前処理 | 第 7 章 |
| [codepagex](https://github.com/tallakt/codepagex) | Shift_JIS（CP932）の CSV | 第 9 章 |
| `:erlang.term_to_binary` | 学習済みモデルの保存 | 第 8 章 |
| [Plug](https://github.com/elixir-plug/plug)・[Bandit](https://github.com/mtrudel/bandit)・[Jason](https://github.com/michalmuskala/jason) | 予測 API | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 012](../../../adr/012-elixir-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/elixir/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
nix develop .#elixir
cd apps/elixir
mix deps.get
mix test
```

リポジトリのルートで `npx gulp apps:check:elixir` を実行すると、CI と同じ順（整形・警告・静的解析・テストとカバレッジ）でまとめて検査できます。

## 章構成

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ | 状態 |
|----|--------|------|
| [第 1 章](01-machine-learning-and-first-test.md) | 機械学習とはじめてのテスト | 公開済み |
| [第 2 章](02-data-preprocessing-and-triangulation.md) | データの前処理と三角測量 | 公開済み |
| [第 3 章](03-decision-tree-and-obvious-implementation.md) | 決定木による分類と明白な実装 | 公開済み |

### 第 2 部: 開発環境と自動化

| 章 | テーマ | 状態 |
|----|--------|------|
| [第 4 章](04-version-control-and-data-management.md) | バージョン管理とデータ管理 | 公開済み |
| [第 5 章](05-package-management-and-static-analysis.md) | パッケージ管理と静的解析 | 公開済み |
| [第 6 章](06-task-runner-and-ci-cd.md) | タスクランナーと CI/CD | 公開済み |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ | 状態 |
|----|--------|------|
| 第 7 章 | 線形回帰による数値予測 | 執筆中 |
| 第 8 章 | 実践的な分類と前処理パイプライン | 執筆中 |
| 第 9 章 | 特徴量エンジニアリング | 執筆中 |

### 第 4 部: モデルの改善と評価

| 章 | テーマ | 状態 |
|----|--------|------|
| 第 10 章 | ロジスティック回帰とアンサンブル学習 | 執筆中 |
| 第 11 章 | 評価指標と交差検証 | 執筆中 |
| 第 12 章 | 正則化とモデル選択 | 執筆中 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ | 状態 |
|----|--------|------|
| 第 13 章 | 主成分分析による次元削減 | 執筆中 |
| 第 14 章 | K-means によるクラスタリング | 執筆中 |
| 第 15 章 | 機械学習 API とモジュール設計 | 執筆中 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Elixir で取り組んでください。
