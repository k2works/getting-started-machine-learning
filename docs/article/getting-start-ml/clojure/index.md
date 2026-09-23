# 機械学習から始める Clojure 入門

Clojure は JVM 上で動く LISP で、不変のデータ（マップ・ベクタ）と、それを変換する関数を中心に組み立てる言語です。型を宣言しないという点では Python・Ruby と同じ動的型付けですが、データの表し方と書き方の単位は S 式と関数の合成になります。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、JVM の機械学習ライブラリ Tribuo と突き合わせながら、Clojure の書き方とエコシステムを学びます。

Clojure 版は 3 つの軸で対比します。1 つは [Scala 版](../scala/index.md)・[Java 版](../java/index.md) で、**同じ JVM・同じ Tribuo・同じ JDK 21** を使います。乱数の並びまで一致するので、数値が合うかどうかを言語をまたいで確かめられます。2 つめは [F# 版](../fsharp/index.md) で、不変のデータと関数でデータを変換する流儀が近いところです。3 つめは [Ruby 版](../ruby/index.md)・[Python 版](../python/index.md) で、型を宣言しない言語として、テストが唯一の安全網になる点が同じです。

## 特徴

- **表を素のマップとベクタで表す**: データフレームのライブラリを使わず、列名をキーワードにしたマップの並び（`{:身長 170 :体重 60 :年代 20 :派閥 "きのこ"}` のベクタ）でデータを持つ。キーワードはそれ自身が関数なので、`(:身長 person)` で値を引ける
- **型を宣言しない**: 引数にも戻り値にも型を書かない。型の誤りや綴りの間違いは、実行して初めて分かる。ただし**未定義の var はコンパイル時に落ちる**ので、Red の出方は Python 版・Ruby 版と少し違う
- **失敗は Java の例外で表す**: `IllegalArgumentException` など JDK の例外をそのまま投げる。Rust 版の `Result`・Go 版の `error` とは逆の流儀で、Java 版・Ruby 版に近い
- **Java の相互運用がそのまま使える**: `(java.io.File. path)`・`(Long/parseLong cell)` のように、JDK と Tribuo の API を包み直さずに呼べる
- **名前空間とファイル名の対応**: 名前空間のダッシュはファイル名のアンダースコアになる（`getting-started-ml.dataset` → `getting_started_ml/dataset.clj`）

## ほかの版との違い

- Notebook による探索と可視化の節は設けません。グラフは [Python 版](../python/index.md) と [Kotlin 版](../kotlin/index.md) の可視化の節を参照してください
- 総合演習（付録 A）の解答例は作りません。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Clojure で取り組んでください
- 乱数は `java.util.Random` と Fisher-Yates を使うので、第 2 章以降の分割は Java 版・Scala 版と一致し、Python 版とは一致しません。記事の数値は Clojure 版の実装で実測した値を載せます
- CSV は `clojure.data.csv` を使いますが、**BOM は取り除かれません**。先頭の列名から自分で取り除きます。`tech.ml.dataset` なら取り除いてくれますが、データフレームのライブラリを使わないというシリーズの方針から採用しませんでした（[ADR 011](../../../adr/011-clojure-ml-libraries.md)）
- `clojure.test` にはテストを飛ばす仕組みがありません。実データのテストは、Rust 版と同じく早期に戻って理由を標準エラーに出します（Ruby 版の Minitest の `skip` との対比）
- `deps.edn` には lock ファイルに当たるものがありません。再現性は依存の版を固定して書くことに頼ります

## 開発環境

| ツール | 用途 |
|--------|------|
| [Clojure](https://clojure.org/) 1.12・[Clojure CLI](https://clojure.org/guides/deps_and_cli)（`deps.edn`） | 言語・依存管理・別名によるタスク |
| [clojure.test](https://clojure.github.io/clojure/clojure.test-api.html)・[test-runner](https://github.com/cognitect-labs/test-runner) | テスティングフレームワーク（`clojure -M:test`） |
| [cljfmt](https://github.com/weavejester/cljfmt) | 整形（`cljfmt check` で検査） |
| [clj-kondo](https://github.com/clj-kondo/clj-kondo) | 静的解析（`clj-kondo --lint src test --fail-level warning`） |
| [cloverage](https://github.com/cloverage/cloverage) | カバレッジ（`clojure -M:coverage`） |

Nix の環境（`nix develop .#clojure`）は Clojure CLI 1.12.3.1577・Leiningen 2.11.2・babashka 1.12.209・clojure-lsp 2025.11.28・clj-kondo 2025.10.23・cljfmt 0.15.6 です。`java` コマンドは 25.0.2 ですが、**Clojure は JDK 21.0.8 で動きます**（Java 版・Kotlin 版・Scala 版と同じ JDK）。clj-kondo と cljfmt はもともと環境に入っていなかったので、環境定義（`ops/nix/environments/clojure/shell.nix`）に足しました。その経緯は第 5〜6 章で扱います。

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [clojure.data.csv](https://github.com/clojure/data.csv) | CSV の読み込み（BOM は自分で取り除く） | 第 1 章 |
| `java.util.Random` | 乱数（訓練データとテストデータの分割） | 第 2 章 |
| [Tribuo](https://tribuo.org/) | 決定木・ランダムフォレスト・回帰・ロジスティック回帰・K-means・評価指標 | 第 3 章 |
| `java.io.InputStreamReader` | Shift_JIS の CSV | 第 9 章 |
| EDN（`pr-str`・`read-string`） | 学習済みモデルの保存 | 第 8 章 |
| [Ring](https://github.com/ring-clojure/ring)・Jetty・[Cheshire](https://github.com/dakrone/cheshire) | 予測 API | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 011](../../../adr/011-clojure-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/clojure/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
nix develop .#clojure
cd apps/clojure
clojure -M:test
clojure -M:run chapter01
```

リポジトリのルートで `npx gulp apps:check:clojure` を実行すると、CI と同じ順（整形・静的解析・テスト・カバレッジ）でまとめて検査できます。

## 章構成

第 1 章のみ公開済みです。第 2 章以降は執筆予定です。

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ | 状態 |
|----|--------|------|
| [第 1 章](01-machine-learning-and-first-test.md) | 機械学習とはじめてのテスト | 公開済み |
| 第 2 章 | データの前処理と三角測量 | 執筆予定 |
| 第 3 章 | 決定木による分類と明白な実装 | 執筆予定 |

### 第 2 部: 開発環境と自動化

| 章 | テーマ | 状態 |
|----|--------|------|
| 第 4 章 | バージョン管理とデータ管理 | 執筆予定 |
| 第 5 章 | パッケージ管理と静的解析 | 執筆予定 |
| 第 6 章 | タスクランナーと CI/CD | 執筆予定 |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ | 状態 |
|----|--------|------|
| 第 7 章 | 線形回帰による数値予測 | 執筆予定 |
| 第 8 章 | 実践的な分類と前処理パイプライン | 執筆予定 |
| 第 9 章 | 特徴量エンジニアリング | 執筆予定 |

### 第 4 部: モデルの改善と評価

| 章 | テーマ | 状態 |
|----|--------|------|
| 第 10 章 | ロジスティック回帰とアンサンブル学習 | 執筆予定 |
| 第 11 章 | 評価指標と交差検証 | 執筆予定 |
| 第 12 章 | 正則化とモデル選択 | 執筆予定 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ | 状態 |
|----|--------|------|
| 第 13 章 | 主成分分析による次元削減 | 執筆予定 |
| 第 14 章 | K-means によるクラスタリング | 執筆予定 |
| 第 15 章 | 機械学習 API とモジュール設計 | 執筆予定 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Clojure で取り組んでください。
