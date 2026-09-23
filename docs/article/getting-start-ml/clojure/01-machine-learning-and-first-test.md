---
type: Article
title: "第 1 章: 機械学習とはじめてのテスト"
description: "機械学習とルールベースの違いを確認し、きのこ派・たけのこ派の判定を Clojure の TDD で実装して正解率を測る。表を素のマップとベクタで表す書き方と、例外による失敗の表し方を Scala 版・Java 版・Ruby 版と対比する。"
tags: [article,getting-start-ml,clojure]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:51:13Z }
---

# 第 1 章: 機械学習とはじめてのテスト

## 1.1 はじめに

この章では、機械学習とは何かを確認したうえで、テスト駆動開発（TDD）で最初のプログラムを Clojure で作ります。題材は「きのこの山派か、たけのこの里派か」を身長・体重・年代から判定する問題です。

この章ではまだ機械学習のアルゴリズムを使いません。人間が考えたルールで判定し、その正解率を測るところまで進みます。「ルールを人間が書く」とはどういうことかを先に体験しておくと、第 3 章で「ルールをデータから学ばせる」ことの意味がはっきりします。

[Python 版の第 1 章](../python/01-machine-learning-and-first-test.md) と同じ題材・同じ TODO リストで進めます。Clojure 版では次の 3 つと対比します。1 つは [Scala 版](../scala/01-machine-learning-and-first-test.md) と [Java 版](../java/01-machine-learning-and-first-test.md) で、同じ JVM・同じ JDK 21 の上で動き、第 3 章からは同じ機械学習ライブラリ Tribuo を使います（[ADR 011](../../../adr/011-clojure-ml-libraries.md)）。2 つめは [F# 版](../fsharp/01-machine-learning-and-first-test.md) で、不変のデータと関数でデータを変換する流儀が近いところです。3 つめは [Ruby 版](../ruby/01-machine-learning-and-first-test.md) と [Python 版](../python/01-machine-learning-and-first-test.md) で、型を宣言しない言語としてテストが安全網になる点が同じです。

## 1.2 機械学習とは

### ルールを書くか、データに学ばせるか

従来のプログラミングでは、人間が判定のルールをコードとして書きます。

```clojure
(defn predict-by-rule
  "人間が決めたルールで派閥を判定する。"
  [features]
  (if (= kinoko-age-group (:年代 features)) kinoko takenoko))
```

この書き方では、ルールの良し悪しは人間の観察力に依存します。特徴量が 3 つなら何とかなりますが、20 個・100 個になると人間には手に負えません。

機械学習は、この「ルール」をデータから自動で作ります。人間が与えるのは「入力（特徴量）」と「正解（ラベル）」の組で、ルールそのものはアルゴリズムが決めます。第 3 章で決定木を実装すると、「年代が 20 ならきのこ」に相当する分岐が、データから自動で決まる様子を見られます。

| | 従来のプログラミング | 機械学習 |
|---|---|---|
| 人間が書くもの | ルール | データと、学習のさせ方 |
| 出力 | 判定結果 | ルール（モデル）と、それを使った判定結果 |
| 得意なこと | 仕様がはっきりしている問題 | 仕様を言葉にしにくい問題 |
| 説明のしやすさ | コードを読めば分かる | モデルによる（決定木は読める、ニューラルネットは難しい） |

### 機械学習のワークフロー

本シリーズを通して、次の流れを繰り返します。

1. **データを集める・読み込む** — この章で CSV を読み込みます
2. **前処理する** — 欠損値を埋め、訓練データとテストデータに分けます（第 2 章）
3. **モデルを学習させる** — 決定木・回帰などを自作し、Tribuo と突き合わせます（第 3 章以降）
4. **評価する** — 正解率などの指標で測ります（この章で正解率から始めます）
5. **改善する** — 特徴量を作り直し、ハイパーパラメータを調整します（第 9 章以降）

### 分類と回帰

教師あり学習は、予測するものの型で 2 つに分かれます。

| 種類 | 予測するもの | 例 | 本シリーズで扱う章 |
|------|------------|-----|-----------------|
| 分類 | どのグループに属するか（離散値） | きのこ派／たけのこ派、アヤメの種類、生存／死亡 | 第 1・3・8・10・11 章 |
| 回帰 | 数値（連続値） | 映画の興行収入、住宅価格 | 第 7・9・12 章 |

この章は分類です。正解ラベルが「きのこ」「たけのこ」の 2 つなので、二値分類にあたります。

## 1.3 題材とデータ

### データの入手と配置

本シリーズの学習データは、書籍『スッキリわかる Python による機械学習入門』（インプレス, 2020）の配布データを使います。配布データは書籍購入者のみ利用できるため、リポジトリには含まれていません。[書籍サポートページ](https://sukkiri.jp/books/sukkiri_ml) から `sukkiri-ml-codes.zip` を入手し、リポジトリの `tmp/` に置いてから、リポジトリのルートで次を実行してください。

```bash
npx gulp data:setup
npx gulp data:check
```

`apps/data/sukkiri-ml/` に学習データが配置されます。このディレクトリは `.gitignore` の対象なので、誤ってコミットされることはありません。すべての言語版が同じデータを参照します。

### KvsT.csv

この章で使うのは `KvsT.csv` です。19 人分のデータが次の 4 列で記録されています。

| 列 | 意味 | 値 |
|----|------|-----|
| 身長 | 身長（cm） | 整数 |
| 体重 | 体重（kg） | 整数 |
| 年代 | 年代 | 10, 20, 30, 40 のいずれか |
| 派閥 | きのこの山派かたけのこの里派か | `きのこ` または `たけのこ` |

「身長」「体重」「年代」が特徴量、「派閥」が正解ラベルです。列名が日本語であることと、ファイルの先頭に BOM（バイトオーダーマーク）が付いていることに注意してください。**Clojure 版では BOM を自分で取り除きます。** 後で確かめます。

## 1.4 TODO リストの作成

仕様をそのままコードにするには大きすぎるので、まず TODO リストに分解します。

> TODO リスト
>
> 何をテストすべきだろうか——着手する前に、必要になりそうなテストをリストに書き出しておこう。
>
> — テスト駆動開発

**TODO リスト**:

- [ ] CSV を読み込む
  - [ ] BOM 付き CSV を列名で読み込む
  - [ ] 数値でない値・列の不足を弾く
- [ ] 特徴量と正解ラベルに分ける
- [ ] ルールで派閥を判定する
  - [ ] 20 代ならきのこ派と判定する
  - [ ] 20 代以外ならたけのこ派と判定する
- [ ] 正解率を計算する
- [ ] 実データで正解率を表示する

## 1.5 開発環境の準備

### プロジェクトの構成

実装は `apps/clojure/` に置きます。Clojure CLI のプロジェクトが 1 つ、その中に章ごとの名前空間を並べる構成です。

```text
apps/clojure/
├── deps.edn
├── src/
│   └── getting_started_ml/
│       ├── dataset.clj      # 学習データの場所
│       ├── chapter01.clj    # 第 1 章
│       └── main.clj         # 章を選んで実行するコマンド
└── test/
    └── getting_started_ml/
        ├── dataset_test.clj
        ├── chapter01_test.clj
        └── kvst_data_test.clj  # 実データを使うテスト
```

ここで最初に戸惑うのが、**名前空間とファイル名の対応**です。名前空間は `getting-started-ml.dataset` とダッシュで書きますが、ファイルは `getting_started_ml/dataset.clj` とアンダースコアになります。Clojure の名前空間はそのまま JVM のクラス名に写され、JVM の識別子にダッシュが使えないためです。ドットがディレクトリの区切りになるのは Java 版・Scala 版と同じですが、ダッシュとアンダースコアの読み替えは Clojure 固有です。ここを間違えると `FileNotFoundException` になります。

`deps.edn` は次のとおりです。

```clojure
{:paths ["src"]
 :deps {org.clojure/clojure {:mvn/version "1.12.0"}
        org.clojure/data.csv {:mvn/version "1.1.0"}}
 :aliases
 {;; テスト: clojure -M:test
  :test {:extra-paths ["test"]
         :extra-deps {io.github.cognitect-labs/test-runner
                      {:git/tag "v0.5.1" :git/sha "dfb30dd"}}
         :main-opts ["-m" "cognitect.test-runner"]}
  ;; カバレッジ: clojure -M:coverage（:paths に test を含めないと落ちる）
  :coverage {:extra-paths ["test"]
             :extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
             :main-opts ["-m" "cloverage.coverage" "-p" "src" "-s" "test" "--text"]}
  ;; 章を選んで実行: clojure -M:run chapter01
  :run {:main-opts ["-m" "getting-started-ml.main"]}}}
```

`deps.edn` は**設定ファイルではなくデータ**です。EDN（Extensible Data Notation）というマップとベクタとキーワードの記法で書かれていて、Clojure のプログラムからそのまま読めます。build.gradle や build.sbt のような DSL ではないので、条件分岐やタスクの定義は書けません。代わりに **`:aliases`（別名）** で依存とコマンドの組を並べ、`-M:test` のように名指しして使います。

| 別名 | コマンド | 何をするか |
|------|---------|-----------|
| `:test` | `clojure -M:test` | `test/` を読み込み、test-runner でテストを走らせる |
| `:coverage` | `clojure -M:coverage` | cloverage でカバレッジを測る |
| `:run` | `clojure -M:run chapter01` | 章を選んで実行する |

**`deps.edn` には lock ファイルに当たるものがありません。** Cargo の `Cargo.lock`、Bundler の `Gemfile.lock` に当たるものが無いので、依存の版を `{:mvn/version "1.1.0"}` と固定して書くこと自体が再現性の担保になります。Git から取る依存（test-runner）は、タグだけでなく `:git/sha` も書くことで固定しています。

Clojure の開発環境は `nix develop .#clojure` に入ると揃います。

```text
$ nix develop .#clojure
Clojure development environment activated
  - Clojure: Clojure CLI version 1.12.3.1577
  - Leiningen: Leiningen 2.11.2 on Java 21.0.8 OpenJDK 64-Bit Server VM
  - Babashka: babashka v1.12.209
  - Clojure LSP: clojure-lsp 2025.11.28-12.47.43
  - clj-kondo: clj-kondo v2025.10.23
  - cljfmt: cljfmt 0.15.6
```

ここで **JDK の版に落とし穴があります**。同じ環境の中で `java -version` を見ると 25 です。

```text
$ java -version
openjdk version "25.0.2" 2026-01-20
OpenJDK Runtime Environment (build 25.0.2+10-69)
OpenJDK 64-Bit Server VM (build 25.0.2+10-69, mixed mode, sharing)
```

しかし、Clojure の中から見た JDK は 21 です。

```text
$ clojure -M ver.clj
java.version = 21.0.8
clojure-version = 1.12.0
```

`clojure` コマンドのラッパーが自分の JDK 21 を持っているためで、上の起動メッセージにある Leiningen の行（`on Java 21.0.8`）とも一致します。**つまり Clojure 版は Java 版・Kotlin 版・Scala 版と同じ JDK 21 の世界で動きます。** 第 3 章以降で Tribuo を呼んだときに数値が一致するかどうかを比べる前提になるので、ここで確かめておきます。「シェルの `java` の版が、その言語処理系の使う JDK とは限らない」ことは、JVM 言語を Nix のような環境で動かすときに繰り返し出会う話です。

なお、起動メッセージに出ている clj-kondo と cljfmt は、もともとこの環境に入っていませんでした（起動メッセージに「clj-kondo …」の行があったのは clojure-lsp の出力の一部で、紛らわしい罠でした）。検査に使う道具が環境に無いままでは CI と手元で同じ検査ができないので、環境定義（`ops/nix/environments/clojure/shell.nix`）に足しています。Ruby 版で `RUBYLIB` を足したのと同じ扱いで、この経緯は第 5〜6 章で扱います。

### 環境確認テスト

いちばん最初に書くのは、環境が動くことを確かめるテストです。学習データの置き場を返す関数から始めます。

```clojure
(ns getting-started-ml.dataset
  "学習データのディレクトリを求める。")

(def env-name
  "実データの置き場をテストや CI から差し替えるための環境変数の名前。"
  "ML_DATA_DIR")

(def default-dir
  "既定の置き場。テストは apps/clojure で走るので、相対パスで apps/data に届く。"
  "../data/sukkiri-ml")

(defn dir
  "学習データのディレクトリを返す。環境変数はテストで差し替えられるように引数で受け取る。"
  ([] (dir (System/getenv)))
  ([env]
   (let [value (get env env-name)]
     (if (or (nil? value) (empty? value)) default-dir value))))
```

Clojure らしい点が 3 つあります。

1. **引数の数で振る舞いを変えられる（多アリティ）。** `([] ...)` と `([env] ...)` を並べると、`(dir)` と `(dir {"ML_DATA_DIR" "/tmp/data"})` の両方が書けます。引数なしの形は `(System/getenv)` を渡すだけの薄い包みで、**テストは引数ありの形を呼んで環境変数を差し替えます**。Rust 版が関数を引数で受け取り、Go 版が `getenv` を引数にしたのと同じ考え方を、多アリティで書いたものです
2. **`(System/getenv)` が Java のマップを返す。** それを `get` でそのまま引けます。Java の `Map` は Clojure のコレクション関数から読めるので、変換が要りません。Java の相互運用がこの軽さで書けるのは Clojure の大きな性質です
3. **`def` の 2 番目の文字列は docstring。** `(def env-name "説明" "ML_DATA_DIR")` は「説明つきで `"ML_DATA_DIR"` を束縛する」という意味です。3 つ並んだ文字列のどれが値なのか、最初は読みにくいところです

テストはこうなります。

```clojure
(ns getting-started-ml.dataset-test
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.dataset :as dataset]))

(deftest 学習データの置き場
  (testing "環境変数が無ければ既定の場所を使う"
    (is (= "../data/sukkiri-ml" (dataset/dir {}))))
  (testing "環境変数があればその場所を使う"
    (is (= "/tmp/data" (dataset/dir {"ML_DATA_DIR" "/tmp/data"}))))
  (testing "環境変数が空なら既定の場所を使う"
    (is (= "../data/sukkiri-ml" (dataset/dir {"ML_DATA_DIR" ""})))))
```

`deftest` がテストの単位、`testing` がその中の小見出し、`is` が表明です。**`deftest` の名前に日本語をそのまま使えます** — Rust 版が関数名を日本語にできたのと同じで、Go 版のように文字列で名前を与える必要はありません。

`is` は 1 つの式を受け取り、それが真でなければ失敗します。`assertEquals` のような専用の表明が並んでいるのではなく、`(is (= 期待値 実際の値))` と普通の式を書く形です。マクロなので、失敗したときには**式そのものと、その評価結果**を表示してくれます。

実行します。

```text
$ clojure -M:test

Running tests in #{"test"}

Testing getting-started-ml.dataset-test

Ran 1 tests containing 3 assertions.
0 failures, 0 errors.
```

`deftest` が 1 つ、その中の `is` が 3 つなので「1 tests containing 3 assertions」です。**テストの数え方が「テスト関数の数」と「表明の数」の 2 段になっている**のが `clojure.test` の特徴で、`testing` の小見出しはテストの数には数えられません。

## 1.6 ルールで派閥を判定する

TODO リストは CSV の読み込みから始まっていますが、**いちばん中心にある「判定」から**着手します。CSV の読み込みは外側の関心事で、判定の仕様とは独立だからです。

### データをどう表すか

その前に決めることがあります。**1 人分のデータをどう表すか**です。

ほかの言語版では、ここで型を宣言しました。Rust は `struct Features`、Scala は `case class`、Java は `record`、Ruby は `Data.define` です。Clojure では**素のマップ**を使います。

```clojure
{:身長 170 :体重 60 :年代 20}
```

`:身長` はキーワードです。コロンで始まる自己評価される値で、シンボル（変数名）とは別物です。キーワードの便利なところは、**それ自身が関数として働く**ことです。

```clojure
(:年代 {:身長 170 :体重 60 :年代 20})
;; => 20
```

`(:年代 features)` と書けば値を引けます。`(get features :年代)` と書いても同じですが、キーワードを先頭に置く書き方のほうが普通です。

型を宣言しないことには代償があります。`:年代` を `:年令` と書き間違えても、コンパイラは何も言いません。マップに無いキーを引くと `nil` が返るだけで、その `nil` が後ろのほうで別のエラーになります。Ruby 版・Python 版と同じく、**テストが唯一の安全網**です。

一方で得るものもあります。マップとベクタは Clojure の標準のコレクションなので、`map`・`filter`・`select-keys`・`zipmap` といった関数がそのまま全部使えます。列を 1 つ足すのに型の宣言を直す必要もありません。ADR 011 で `tech.ml.dataset`（データフレームのライブラリ）を採用しなかったのも、素のマップとベクタで十分に書けて、そのデータ操作自体が記事の題材になるからです。

### Red: まだ無いものを呼ぶ

テストを先に書きます。

```clojure
(ns getting-started-ml.chapter01-test
  (:require [clojure.test :refer [deftest is testing]]
            [getting-started-ml.chapter01 :as ch]))

(defn- features [height weight age-group]
  {:身長 height :体重 weight :年代 age-group})

(deftest ルールによる判定
  (testing "二十代はきのこ派と判定する"
    (is (= ch/kinoko (ch/predict-by-rule (features 170 60 20))))))
```

`ch/kinoko` も `ch/predict-by-rule` もまだありません。

```text
$ clojure -M:test

Running tests in #{"test"}
Syntax error compiling at (getting_started_ml/chapter01_test.clj:10:5).
No such var: ch/kinoko
```

ここが面白いところです。Clojure は型を宣言しない動的な言語ですが、**未定義の var（名前）はコンパイル時に見つかります**。名前空間の中身は読み込み時に決まるので、`ch/kinoko` という名前が存在しないことはテストが走る前に分かります。

| 言語版 | Red の出方 |
|--------|-----------|
| Rust・Java・Scala・Go・C# | コンパイルエラー（型も名前も検査される） |
| **Clojure** | **コンパイルエラー（名前は検査されるが、型は検査されない）** |
| Python・Ruby・TypeScript（実行時） | 実行時エラー（`NameError`・`NoMethodError`） |

Clojure は「動的型付けだが名前は静的」という中間にいます。綴りの間違いは早く見つかりますが、`(ch/predict-by-rule "文字列")` のような**型の誤りは実行するまで分かりません**。

`(defn- features ...)` の `defn-` は非公開の関数の定義です。テストの中でしか使わないヘルパーなので、名前空間の外には出しません。

### Green: 仮実装

いちばん単純に、定数を返します。

```clojure
(def kinoko "きのこ派の呼び名。" "きのこ")
(def takenoko "たけのこ派の呼び名。" "たけのこ")

(defn predict-by-rule
  "仮実装: まず定数を返す。"
  [features]
  kinoko)
```

これで最初のテストは通ります。

### 三角測量

仮実装を本実装に進めるために、もう 1 つテストを足します。

```clojure
(testing "三十代はたけのこ派と判定する"
  (is (= ch/takenoko (ch/predict-by-rule (features 160 50 30)))))
```

```text
$ clojure -M:test

Testing getting-started-ml.chapter01-test

FAIL in (ルールによる判定) (chapter01_test.clj:12)
三十代はたけのこ派と判定する
expected: (= ch/takenoko (ch/predict-by-rule (features 160 50 30)))
  actual: (not (= "たけのこ" "きのこ"))

Ran 1 tests containing 2 assertions.
1 failures, 0 errors.
```

失敗メッセージの読み方にくせがあります。`expected:` には**式がそのまま**出て、`actual:` には**その式を否定した形**が出ます。`(not (= "たけのこ" "きのこ"))` は「`"たけのこ"` と `"きのこ"` が等しくなかった」という意味です。`is` がマクロなので、評価する前のソースコードと評価した後の値の両方を持てるわけです。`testing` に書いた小見出し（三十代はたけのこ派と判定する）が失敗メッセージに出るのも助かります。

`FAIL in (ルールによる判定)` の括弧の中は `deftest` の名前で、その後ろがファイル名と行番号です。**行番号は `deftest` の先頭ではなく、失敗した `is` の位置**を指します。

この失敗を受けて、本実装に進みます。

```clojure
(def ^:private kinoko-age-group
  "「20 代ならきのこ派」というルールの年代。"
  20)

(defn predict-by-rule
  "人間が決めたルールで派閥を判定する。"
  [features]
  (if (= kinoko-age-group (:年代 features)) kinoko takenoko))
```

`^:private` はメタデータで、この var を名前空間の外から使えなくします。`defn-` の `def` 版です。`kinoko` と `takenoko` は公開しています。テストからも使いますし、この章の語彙として外に出す価値があるからです。

年代が 20 以外のすべてでたけのこ派になることも確かめます。

```clojure
(testing "二十代以外はたけのこ派と判定する"
  (doseq [age-group [10 30 40 50]]
    (is (= ch/takenoko (ch/predict-by-rule (features 170 60 age-group))))))
```

`doseq` は副作用のためのループです（`for` は遅延シーケンスを返すので、テストの中で使うと表明が評価されないまま捨てられることがあります。ここは `doseq` を使うところです）。Go 版の表駆動テスト、Java 版の `@ParameterizedTest`、Rust 版の配列のループにあたる部分を、Clojure では `doseq` で書きました。

## 1.7 失敗を例外で表す

CSV の読み込みに進む前に、**失敗をどう表すか**を決めます。Clojure は JVM の上で動くので、**Java の例外をそのまま使います**。

```clojure
(throw (IllegalArgumentException. (str "列がありません: " (name column))))
```

`(IllegalArgumentException. ...)` の末尾のドットが「新しいオブジェクトを作る」書き方です（`new` を前に置く形も書けます）。JDK の例外クラスを包み直さずに、そのまま投げます。

ここが言語ごとにいちばん分かれるところなので、並べておきます。

| 言語版 | 失敗の表し方 | 網羅性の検査 |
|--------|------------|------------|
| Rust | `Result<T, Error>` と `enum Error` | `match` の網羅性をコンパイラが検査する |
| F#・Scala | `Result`・`Either` と判別共用体 | 同じく検査する |
| Go | `(値, error)` の多値返却 | されない |
| Java | 検査例外 `throws` | 宣言はあるが、種類の網羅は検査されない |
| **Clojure** | **JDK の例外を `throw` する** | **されない（宣言もしない）** |
| C#・Kotlin・Python・TypeScript・Ruby | 例外 | されない |

Clojure には検査例外の宣言がありません。関数のシグネチャに `throws` を書く場所そのものが無いので、**どの関数がどの例外を投げうるかは docstring とテストでしか表せません**。Ruby 版と同じ立場です。Clojure には `ex-info` という独自の例外（データを添えられる）もありますが、この章では呼び出し側が Java の世界とも地続きであることを優先して、標準の `IllegalArgumentException` を使いました。

テストでは `thrown-with-msg?` で、例外の種類とメッセージの両方を確かめます。

```clojure
(testing "件数が違えば正解率を求められない"
  (is (thrown-with-msg? IllegalArgumentException #"予測と正解ラベルの件数が違います: 1 と 2"
                        (ch/accuracy [ch/kinoko] [ch/kinoko ch/takenoko]))))
```

`#"..."` が正規表現のリテラルです。`is` は普通の式を取るはずなのに、なぜ `thrown-with-msg?` のような「関数でないもの」を先頭に書けるのか——これも `is` がマクロだからです。`is` は受け取った式の**形を見て**、`thrown-with-msg?` で始まっていれば例外を捕まえる形に展開します。

## 1.8 CSV を読み込む

### data.csv は BOM を取り除かない

ここで、本シリーズを通しての「BOM の落とし穴」がまた出てきます。

BOM 付きの CSV を `clojure.data.csv` で素直に読むと、先頭の列名に BOM が残ります。確かめました。

```clojure
(spit "/tmp/bomdemo.csv" "\uFEFF身長,体重,年代,派閥\n170,60,20,きのこ\n")
(with-open [r (io/reader "/tmp/bomdemo.csv")]
  (let [[header] (csv/read-csv r)]
    (println "列名 =" (pr-str header))
    (println "先頭の列名のバイト列 =" (pr-str (vec (.getBytes ^String (first header) "UTF-8"))))
    (println "身長 で引けるか =" (contains? (set header) "身長"))))
```

```text
列名 = ["\uFEFF身長" "体重" "年代" "派閥"]
先頭の列名のバイト列 = [-17 -69 -65 -24 -70 -85 -23 -107 -73]
身長 で引けるか = false
```

（実際の出力では先頭の列名に見えない BOM が入っています。ここでは読めるように `\uFEFF` のエスケープで示しました。）

バイト列の先頭 3 つ `-17 -69 -65` が BOM です。JVM の `byte` は符号つきなので負の数に見えますが、符号なしに直すと `EF BB BF`、UTF-8 の BOM そのものです。そして **`"身長"` では引けません**。Python 版の `encoding='utf-8-sig'`、Java 版・C# 版の対処、Go 版の `TrimPrefix` と同じ場面に来ました。

| 言語版 | BOM の扱い |
|--------|-----------|
| Rust（csv クレート） | ライブラリが自動で取り除く |
| Ruby（`CSV.foreach`） | ファイルを開けば取り除かれる（`CSV.parse` では残る） |
| Python | `encoding='utf-8-sig'` を指定すれば取り除かれる |
| **Clojure（`clojure.data.csv`）** | **取り除かれない。自分で取り除く** |
| Clojure（`tech.ml.dataset`） | 取り除かれるが、ADR 011 で使わないと決めた |

`tech.ml.dataset` を使えば BOM の問題は消えます。それでも使わないと決めたのは、データフレームのライブラリを使わないというシリーズ全体の方針と、依存に OpenBLAS のネイティブライブラリが入ることが理由です（[ADR 011](../../../adr/011-clojure-ml-libraries.md)）。**「面倒を見てくれるライブラリを選ぶ」か「自分で面倒を見る」かは設計の選択**で、ここでは後者を選んで、その 1 行をコードに書きます。

### 読み込みの実装

```clojure
(def ^:private bom
  "UTF-8 の BOM。data.csv は取り除かないので、先頭の列名から自分で取り除く。"
  "\uFEFF")

(defn load-people
  "CSV を読み込み、列名で値を取り出して人物のリストにする。"
  [csv-file]
  (with-open [reader (io/reader csv-file)]
    (let [[header & rows] (csv/read-csv reader)
          columns (map #(keyword (str/replace-first % bom "")) header)]
      (mapv #(to-person (zipmap columns %)) rows))))
```

短いですが、Clojure の道具がいくつも出てきます。

- **`with-open`** は、束縛したものを節を抜けるときに必ず `.close` します。Java の try-with-resources、Ruby のブロック付き `File.open` にあたります。`csv/read-csv` が返すのは遅延シーケンスなので、**`with-open` の中で最後まで読み切る必要があります**。`mapv`（ベクタを返す＝即座に評価する）を使っているのはそのためで、`map` のままだと閉じた後で読もうとして落ちます。遅延評価のある言語で必ず一度は踏む落とし穴です
- **`[header & rows]`** は分配束縛（destructuring）です。シーケンスの先頭をヘッダー、残りを行に分けます。Rust のパターン、F# の `head :: tail` にあたります
- **`#(keyword (str/replace-first % bom ""))`** は無名関数の短縮記法で、`%` が引数です。BOM を取り除いてからキーワードにします。`str/replace-first` にしているのは、置換するのが先頭の 1 箇所だけでよいからです
- **`(zipmap columns %)`** が列名と値を組にしてマップを作ります。`(zipmap [:身長 :体重] ["170" "60"])` が `{:身長 "170" :体重 "60"}` になります。Python の `dict(zip(...))` と同じ働きで、**列名で引ける形に変えるのがここ 1 行で済みます**

行を人物にする部分はこうです。

```clojure
(def ^:private feature-keys
  "判定の手がかりになる列。"
  [:身長 :体重 :年代])

(defn- to-person
  "1 行の値を人物にする。列が無ければ例外を投げる。"
  [row]
  (doseq [column (conj feature-keys :派閥)]
    (when-not (contains? row column)
      (throw (IllegalArgumentException. (str "列がありません: " (name column))))))
  {:身長 (number row :身長)
   :体重 (number row :体重)
   :年代 (number row :年代)
   :派閥 (get row :派閥)})
```

まず列がそろっているかを確かめ、それからマップを組み立てます。**列を確かめる工程と値を変換する工程が、同じ関数の中で 2 つの式として並ぶ**のは Clojure らしい書き方です。Rust 版が `?` 演算子で構造体のリテラルの中に失敗の可能性を書けたのとは対照的に、こちらは「先に全部確かめる」という素直な形になりました。

`(name column)` はキーワードから文字列を取り出します。`:身長` を `"身長"` にする関数で、メッセージを組み立てるのに使います。

数値の読み取りは Java に任せます。

```clojure
(defn- number
  "列名で数値を読む。整数として読めなければ例外を投げる。"
  [row column]
  (let [cell (get row column)]
    (try
      (Long/parseLong cell)
      (catch NumberFormatException _
        (throw (IllegalArgumentException. (str (name column) " を数値として読めません: " cell)))))))
```

`(Long/parseLong cell)` は Java の静的メソッドの呼び出しです。スラッシュの前がクラス、後ろがメソッドです。`NumberFormatException` を捕まえて、**この章の語彙でのメッセージに包み直しています**。ライブラリ由来の例外をそのまま外に出すと、呼び出し側は Java の都合を知らないと読めません。

`(catch NumberFormatException _ ...)` の `_` は「捕まえた例外を使わない」という印です。使わない束縛に `_` を使うのは慣習ですが、後で見るように clj-kondo はこの慣習を知っていて、`_` には警告を出しません。

テストは一時ファイルを書いて読ませます。

```clojure
(testing "BOM 付きの CSV を列名で読み込む"
  (let [file (java.io.File/createTempFile "people" ".csv")]
    (try
      (spit file "\uFEFF身長,体重,年代,派閥\n170,60,20,きのこ\n")
      (is (= [{:身長 170 :体重 60 :年代 20 :派閥 ch/kinoko}] (ch/load-people (.getPath file))))
      (finally (io/delete-file file true)))))
```

`(java.io.File/createTempFile ...)` も `(.getPath file)` も Java の呼び出しです。前者が静的メソッド、後者がインスタンスメソッド（先頭のドット）です。**JDK をそのまま使えるので、一時ファイルの扱いに Clojure 固有の API を覚える必要がありません。**

そして `is` の中身に注目してください。**期待値がマップのベクタで、そのまま `=` で比べられます。**

```clojure
(= [{:身長 170 :体重 60 :年代 20 :派閥 "きのこ"}] 結果)
```

Clojure のマップとベクタは中身で比較されるので、Java 版・C# 版が配列の比較で苦労した部分も、Rust 版が `#[derive(PartialEq)]` で得た性質も、宣言なしで最初から使えます。**値で比べられることが、テストを書きやすくしています。**

## 1.9 特徴量と正解ラベルに分ける

```clojure
(defn split-features-and-labels
  "人物のリストを特徴量と正解ラベルに分ける。"
  [people]
  [(mapv #(select-keys % feature-keys) people)
   (mapv :派閥 people)])
```

2 行です。

- `(select-keys % feature-keys)` が、人物のマップから `:身長 :体重 :年代` だけを取り出した新しいマップを返します。**「必要な列だけ選ぶ」が関数 1 つ**で、ほかの言語版で `Features` 型を作って詰め替えていた部分にあたります
- `(mapv :派閥 people)` が正解ラベルのベクタです。**キーワードが関数なので、`map` にそのまま渡せます**。`(mapv #(:派閥 %) people)` と書く必要はありません

戻り値はベクタ 2 つのベクタです。呼ぶ側は分配束縛で受け取ります。

```clojure
(let [[x t] (ch/split-features-and-labels people)]
  ...)
```

Scala 版・F# 版のタプル、Go 版の多値返却にあたるものを、Clojure ではベクタで表します。専用の型ではなく普通のデータなので、そのまま `map` に渡すことも、そのまま比べることもできます。

**所有権の話が出てこない**のがここでの対比です。Rust 版では「読むだけなら `&[Person]` で借りる」「`clone()` をどこでするか」が設計の一部でしたが、Clojure のデータは不変なので、渡しても共有されるだけで複製は起きません。`select-keys` が返すのは新しいマップですが、元のマップは何も変わりません。**不変であることが、複製の判断そのものを消しています。** F# 版と共通する性質です。

## 1.10 正解率を計算する

```clojure
(defn accuracy
  "予測が正解ラベルと一致した割合を返す。件数が違えば例外を投げる。"
  [predictions labels]
  (when-not (= (count predictions) (count labels))
    (throw (IllegalArgumentException.
            (str "予測と正解ラベルの件数が違います: " (count predictions) " と " (count labels)))))
  (/ (double (count (filter true? (map = predictions labels)))) (count labels)))
```

最後の行が Clojure らしいところです。内側から読みます。

1. `(map = predictions labels)` — **`=` を関数として `map` に渡します**。2 つのシーケンスを同時に回して、要素ごとに `(= 予測 正解)` を評価し、`true`/`false` の並びを作ります。Python 版の `zip`、Rust 版の `zip().filter().count()` にあたる部分が、`map` に `=` を渡すだけで書けます
2. `(filter true? ...)` で真のものだけ残し、`(count ...)` で数えます
3. `(double ...)` で浮動小数点に変換してから割ります

**`(double ...)` を忘れると結果が変わります。** Clojure の `/` は整数どうしだと**有理数**を返すからです。`(/ 14 19)` は `0` でも `0.736...` でもなく、`14/19` という分数になります。Java・Go の整数除算（切り捨て）とも、Python 3 の `/`（浮動小数点）とも違う第 3 の流儀です。正解率は割合なので、ここでは明示的に `double` にしました。

テストでは `=` ではなく `==` を使っています。

```clojure
(testing "全部当たれば正解率は一になる"
  (is (== 1.0 (ch/accuracy [ch/kinoko ch/takenoko] [ch/kinoko ch/takenoko]))))
```

`=` は型もそろっていることを求めるので `(= 1 1.0)` は `false` ですが、`==` は数値として等しいかを見るので `(== 1 1.0)` は `true` です。**数値の比較には `==`** が安全です。

正解率の比較に厳密な比較を使えるのは、1.0 と 0.5 が浮動小数点で正確に表せる値だからです。実データのテストでは許容誤差つきの比較に変えています。

```clojure
(is (< (abs (- (ch/accuracy predictions t) (/ 14.0 19))) 1e-12))
```

## 1.11 実データで正解率を表示する

### データが無ければスキップする

実データを使うテストは、名前空間を分けて `kvst_data_test.clj` に置きます。

```clojure
(defn- csv-file
  "学習データのファイルを返す。無ければ nil（テストはスキップする）。
   clojure.test にはスキップが無いので、理由を標準エラーに出して早く戻る。"
  []
  (let [path (str (dataset/dir) "/KvsT.csv")]
    (if (.exists (java.io.File. path))
      path
      (binding [*out* *err*]
        (println "学習データ KvsT.csv が配置されていない（gulp data:setup）のでスキップする")
        nil))))

(deftest 実データを読み込んで正解率を求める
  (when-let [path (csv-file)]
    (let [people (ch/load-people path)
          [x t] (ch/split-features-and-labels people)
          predictions (mapv ch/predict-by-rule x)]
      (is (= 19 (count people)))
      (is (< (abs (- (ch/accuracy predictions t) (/ 14.0 19))) 1e-12)))))
```

**`clojure.test` にはテストを飛ばす仕組みがありません。** Ruby 版の Minitest には `skip` があり、呼べば「スキップした」と記録されます。Go の `t.Skip`、JUnit の `assumeTrue` も同じです。`clojure.test` にはそれに当たるものが無いので、**Rust 版と同じ形**——理由を標準エラーに出して早く戻る——にしました。

| 言語版 | スキップの仕組み | 結果の見え方 |
|--------|----------------|------------|
| Ruby（Minitest） | `skip` | スキップとして記録される |
| Go | `t.Skip` | スキップとして記録される |
| **Clojure（`clojure.test`）** | **無い。早期に戻る** | **成功として数えられる（表明の数が減る）** |
| Rust | 無い。早期に戻る | 成功として数えられる |

`(binding [*out* *err*] ...)` は、`*out*`（`println` の書き出し先）をこの節の間だけ標準エラーに差し替える書き方です。動的束縛といって、**呼び出しの階層をさかのぼって効く**のが特徴です。`println` の中で改めて書き出し先を渡す必要がありません。

`when-let` は「値が `nil` でなければ束縛して本体を評価し、そうでなければ何もしない」という形です。Rust 版の `let ... else` を裏返したものにあたります。

**成功として数えられてしまう**のが弱点なのは Rust 版と同じですが、Clojure の場合は**表明の数で見分けられます**。データがあるときとないときで、こう変わります。

```text
$ clojure -M:test
Ran 6 tests containing 18 assertions.
0 failures, 0 errors.

$ ML_DATA_DIR=/nonexistent clojure -M:test
学習データ KvsT.csv が配置されていない（gulp data:setup）のでスキップする
Ran 6 tests containing 16 assertions.
0 failures, 0 errors.
```

テストの数は 6 のままで、**表明の数が 18 から 16 に減ります**。`clojure.test` が「テストの数」と「表明の数」を分けて数えていることが、ここで役に立ちました。実データのテストが持つ `is` は 2 つなので、数が合います。

### 結果を表示する

```clojure
(defn run
  "実データでルールによる判定の正解率を表示する。"
  []
  (let [people (load-people (str (dataset/dir) "/KvsT.csv"))
        [x t] (split-features-and-labels people)
        predictions (mapv predict-by-rule x)]
    (println (str "データ件数: " (count people)))
    (println (format "ルールによる判定の正解率: %.4f" (accuracy predictions t)))))
```

`(format "%.4f" ...)` は `String/format` の包みで、Java の書式そのままです。

章を選んで実行するコマンドはこうなります。

```clojure
(ns getting-started-ml.main
  "章を選んで実行する。使い方: clojure -M:run chapter01"
  (:require [clojure.string :as str]
            [getting-started-ml.chapter01 :as chapter01]))

(def ^:private chapters
  {"chapter01" chapter01/run})

(defn -main
  "章の名前を受け取り、その章を実行する。"
  [& args]
  (if-let [run (get chapters (first args))]
    (run)
    (binding [*out* *err*]
      (println (str "使い方: clojure -M:run <章>（章: " (str/join ", " (sort (keys chapters))) "）"))
      (System/exit 1))))
```

ここも Clojure らしい形です。**章の振り分けが `case` でも `match` でもなく、ただのマップ**です。

```clojure
{"chapter01" chapter01/run}
```

キーが章の名前、値が**関数そのもの**です。`(get chapters "chapter01")` で関数が返り、`(run)` で呼べます。章が増えたらマップに 1 行足すだけで、使い方のメッセージ（`(sort (keys chapters))`）も自動で追いつきます。Rust 版が `match` の腕を増やし、使い方の文字列も別に直していたのと比べると、**データが 1 か所にまとまる**ぶん食い違いが起きません。関数が値であることの、いちばん分かりやすい使い道です。

`-main` の先頭のハイフンは、Java から呼べる静的メソッドとして公開する印です。`[& args]` の `&` は可変長引数で、残りをシーケンスで受け取ります。

実行します。

```text
$ clojure -M:run chapter01
データ件数: 19
ルールによる判定の正解率: 0.7368
```

**正解率 0.7368** は、Python 版・Kotlin 版・TypeScript 版・F# 版・Java 版・C# 版・Scala 版・Go 版・Rust 版・Ruby 版と同じ値です。19 人中 14 人を正しく判定できました。乱数を使わない処理なので、言語が違っても値は一致します（第 2 章では、乱数生成器の違いが効いてきます。ただし Clojure 版は `java.util.Random` を使うので、Java 版・Scala 版とは一致する見込みです）。

## 1.12 リファクタリング

### 整形と静的解析

```text
$ cljfmt check src test
All source files formatted correctly
$ clj-kondo --lint src test --fail-level warning
linting took 196ms, errors: 0, warnings: 0
```

どちらも何も言わなければ合格です。わざと崩すとどうなるかを見ておきます。まず字下げを崩します。

```clojure
(defn predict-by-rule
  "人間が決めたルールで派閥を判定する。"
  [features]
      (if (= kinoko-age-group (:年代 features))
    kinoko
        takenoko))
```

```text
$ cljfmt check src test
src/getting_started_ml/chapter01.clj has incorrect formatting
--- a/src/getting_started_ml/chapter01.clj
+++ b/src/getting_started_ml/chapter01.clj
@@ -57,9 +57,9 @@
 (defn predict-by-rule
   "人間が決めたルールで派閥を判定する。"
   [features]
-      (if (= kinoko-age-group (:年代 features))
+  (if (= kinoko-age-group (:年代 features))
     kinoko
-        takenoko))
+    takenoko))
1 file(s) formatted incorrectly
```

差分の形で出してくれます。LISP の整形は「行をどう折るか」ではなく「どれだけ字下げするか」がほぼすべてなので、`cljfmt` の指摘も字下げが中心です。`gofmt`・`rustfmt`・`scalafmt` が改行の位置まで決めるのに比べると、直すべき点が分かりやすい代わりに、**式の切り方は書き手に任されています**。

次に、使わない束縛を残します。

```clojure
(defn predict-by-rule
  "人間が決めたルールで派閥を判定する。"
  [features]
  (let [age-group (:年代 features)]
    (if (= kinoko-age-group (:年代 features)) kinoko takenoko)))
```

```text
$ clj-kondo --lint src test --fail-level warning
src/getting_started_ml/chapter01.clj:60:9: warning: unused binding age-group
linting took 179ms, errors: 0, warnings: 1
$ echo $?
2
```

clj-kondo は未使用の束縛を指摘します。そして `--fail-level warning` を付けているので、**警告でも終了コードが 2 になります**。既定では警告があっても終了コードは 0 なので、CI で先に進めてしまいます。Rust 版の `-D warnings`、Go 版の `golangci-lint` の設定と同じ考え方で、**警告を残したまま先へ進めない**ようにしています。

clj-kondo は「型を検査しない言語で、実行せずに何が言えるか」を追求した道具です。未使用の束縛のほかに、引数の数の間違い（`arity`）、解決できない名前（`Unresolved var`）、`:require` の書き忘れなどを見つけます。**Clojure が動的型付けであることの弱点を、静的解析が部分的に埋めている**構図で、Ruby 版の RuboCop と似た立場です。この道具の詳しい話は第 5 章で扱います。

### カバレッジ

```text
$ clojure -M:coverage

|------------------------------+---------+---------|
|                    Namespace | % Forms | % Lines |
|------------------------------+---------+---------|
| getting-started-ml.chapter01 |   78.48 |   87.80 |
|   getting-started-ml.dataset |  100.00 |  100.00 |
|      getting-started-ml.main |   13.33 |   44.44 |
|------------------------------+---------+---------|
|                    ALL FILES |   70.97 |   82.46 |
|------------------------------+---------+---------|
```

cloverage は **Forms（式）** と **Lines（行）** の 2 つを出します。ほかの言語版が行や分岐を数えるのに対し、**式を数えられるのは LISP ならでは**です。ソースコードが木構造そのものなので、「どの部分式が評価されたか」を単位にできます。1 行に複数の式が入りやすい言語では、行より式のほうが実態に近い数字になります。

`main` が低いのは、`-main` を直接テストしていないからです（`System/exit` を呼ぶので、テストから呼ぶとテストごと終わってしまいます）。

ここで Rust 版との違いが出ます。学習データを外しても、数字が変わりません。

```text
$ ML_DATA_DIR=/nonexistent clojure -M:coverage
学習データ KvsT.csv が配置されていない（gulp data:setup）のでスキップする

Ran 6 tests containing 16 assertions.
0 failures, 0 errors.
|                    ALL FILES |   70.97 |   82.46 |
```

**70.97% / 82.46% と、データがあるときと同じです。** Rust 版では実データのテストがスキップされるとカバレッジが 18 ポイント落ちました。Clojure 版で落ちないのは、実データのテストが通る経路（`load-people`・`split-features-and-labels`・`predict-by-rule`・`accuracy`）を、一時ファイルを使う単体テストがすべて覆っているからです。

これは「Clojure 版のテストのほうが良い」という話ではなく、**カバレッジはスキップの検出に使えるとは限らない**という話です。Rust 版では「カバレッジで確かめられる」と書けましたが、同じ手は Clojure 版では効きません。代わりに、前節で見た**表明の数（18 と 16）**が確かな手がかりになります。指標が何を写しているかは、実際に動かして確かめるしかありません。

`deps.edn` の `:coverage` に 1 つ注意があります。

```clojure
:coverage {:extra-paths ["test"]
           :extra-deps {cloverage/cloverage {:mvn/version "1.2.4"}}
           :main-opts ["-m" "cloverage.coverage" "-p" "src" "-s" "test" "--text"]}
```

`-s test` でテストの場所を教えているのに、**`:extra-paths ["test"]` も要ります**。前者は cloverage への指示、後者はクラスパスの設定で、役割が別だからです。書き忘れると `Could not locate ..._test.clj` で落ちます。`-p`／`-s` の指定とクラスパスは別物、という Clojure CLI の分かれ方に慣れるまでは引っかかるところです。

### まとめて検査する

リポジトリのルートで次を実行すると、CI と同じ順に検査できます。

```bash
npx gulp apps:check:clojure
```

中身は `cljfmt check src test && clj-kondo --lint src test --fail-level warning && clojure -M:test && clojure -M:coverage` です。同じ順序を `.github/workflows/clojure-ci.yml` にも書いています。

CI には 1 つ工夫があります。`deps.edn` には lock ファイルが無いので、依存のキャッシュの鍵に**依存の記述そのもの**を使っています。

```yaml
- name: Cache maven and gitlibs
  uses: actions/cache@v4
  with:
    path: |
      ~/.m2/repository
      ~/.gitlibs
    key: ${{ runner.os }}-clojure-${{ hashFiles('apps/clojure/deps.edn') }}
```

`~/.m2/repository` が Maven から取った依存、`~/.gitlibs` が Git から取った依存（test-runner）の置き場です。ほかの言語版が lock ファイルのハッシュを鍵にしているところを、`deps.edn` 自身のハッシュで代用しています。版を固定して書いているので、これで実用上は足ります。

## 1.13 まとめ

この章では、ルールによる判定と正解率を TDD で実装し、実データで 0.7368 という値を得ました。Clojure に固有の論点は次のとおりです。

1. **表は素のマップとベクタで表す** — 型を宣言せず、列名のキーワードで引く。キーワードは関数なので `(mapv :派閥 people)` と書ける。マップもベクタも中身で比較されるので、テストで `=` がそのまま使える
2. **動的型付けだが、名前は静的** — 未定義の var はコンパイル時に落ちるので、Red はコンパイルエラーになる。一方で型の誤りは実行するまで分からず、そこを clj-kondo が部分的に埋める
3. **失敗は JDK の例外で表す** — `throws` の宣言そのものが無いので、どの関数が何を投げるかは docstring とテストでしか表せない。Java の相互運用がそのまま使えるぶん、ライブラリの例外は自分の語彙に包み直す
4. **ライブラリの親切に頼らない選択もある** — `clojure.data.csv` は BOM を取り除かない。取り除いてくれる `tech.ml.dataset` を使わないと決めたので、BOM の除去を 1 行書く
5. **スキップの仕組みが無い** — 実データのテストは早期に戻る形にする。Rust 版と同じだが、カバレッジは変わらないので、**表明の数（18 と 16）**で走ったかどうかを見分ける
6. **遅延評価と `with-open` は相性が悪い** — `csv/read-csv` の結果は、閉じる前に `mapv` で読み切る

**TODO リスト（この章の完了時点）**:

- [x] CSV を読み込む
- [x] 特徴量と正解ラベルに分ける
- [x] ルールで派閥を判定する
- [x] 正解率を計算する
- [x] 実データで正解率を表示する

次の章では、アヤメのデータを読み込み、欠損値を補完して、訓練データとテストデータに分けます。乱数には `java.util.Random` を使うので、分割の結果が Java 版・Scala 版と一致するかどうかを確かめられます。
