---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "deps.edn による依存の宣言とロックファイルが無いこと、別名（:test・:coverage・:run）の使い分け、cljfmt による整形、clj-kondo の --fail-level warning と終了コード、cloverage によるカバレッジ計測を学ぶ。"
tags: [article,getting-start-ml,clojure]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T01:20:00Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、何をコミットし、何をコミットしないかを決め、実験を再現するにはシードと版を固定する必要があることを確かめました。この章では、その「版を固定する」仕組みと、コードの品質を機械的に確かめる道具を整えます。

Clojure 版で使う道具は次のとおりです。

| 役割 | 道具 | 設定ファイル |
|------|------|------------|
| 依存の宣言・クラスパスの組み立て | Clojure CLI | `deps.edn` |
| 依存の取得 | Maven（Clojure CLI に内蔵）・Git | （`deps.edn` の宣言から解決） |
| テストの実行 | cognitect-labs/test-runner | `deps.edn` の `:test` 別名 |
| 整形 | cljfmt | （設定ファイル無し。既定のまま） |
| 静的解析 | clj-kondo | （設定ファイル無し。既定のまま） |
| カバレッジ | cloverage | `deps.edn` の `:coverage` 別名 |

Scala 版では「整形は scalafmt、指摘はコンパイラ自身」という分担でした。Clojure は動的型付けで、コンパイラがしてくれる検査はごく少ないので、**指摘の役は clj-kondo が一手に引き受けます**。型の無い言語で、どこまでを機械に見てもらえるのか。それがこの章の見どころです。

道具は 3 つとも、`deps.edn` の依存としてではなく **コマンドとして** 使います。Nix の開発環境（`ops/nix/environments/clojure/shell.nix`）に入っているものを、そのまま呼びます。

## 5.2 deps.edn によるパッケージ管理

### deps.edn の全体

Clojure CLI の設定は `deps.edn` に書きます。設定ファイルそのものが EDN（Extensible Data Notation）というデータで、Clojure のマップ・ベクタ・キーワードの記法をそのまま使います。sbt の `build.sbt` や Gradle の `build.gradle.kts` が「設定を書くためのプログラム」だったのに対し、`deps.edn` は **ただのデータ** です。評価される式は 1 つもありません。

```clojure
{:paths ["src"]
 :deps {org.clojure/clojure {:mvn/version "1.12.0"}
        org.clojure/data.csv {:mvn/version "1.1.0"}
        org.tribuo/tribuo-classification-tree {:mvn/version "4.3.2"}}
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

読み方は次のとおりです。

| キー | 意味 |
|------|------|
| `:paths` | ソースの置き場所。ここに書いた場所がクラスパスに載る |
| `:deps` | 依存。ライブラリの名前（`グループ/成果物`）から、どこから取るかの指定へのマップ |
| `:aliases` | 別名。`-M:test` のように名前で呼ぶと、その中身が基本の設定に足される |

**Clojure 自身が `:deps` の 1 つ** であることに注目してください。Java・Kotlin・Scala では言語の版をビルドツールの設定項目（`scalaVersion` など）で指定しましたが、Clojure では言語のランタイムもライブラリの 1 つです。`org.clojure/clojure {:mvn/version "1.12.0"}` の行を書き換えれば、そのプロジェクトが使う Clojure の版が変わります。

### 依存の取り方は 2 種類ある

依存の値には、どこから取るかを書きます。本シリーズでは 2 種類が出てきます。

```clojure
org.tribuo/tribuo-classification-tree {:mvn/version "4.3.2"}
io.github.cognitect-labs/test-runner {:git/tag "v0.5.1" :git/sha "dfb30dd"}
```

| 指定 | 意味 | 置かれる場所 |
|------|------|------------|
| `:mvn/version` | Maven Central などのリポジトリから、その版の JAR を取る | `~/.m2/repository` |
| `:git/tag` と `:git/sha` | Git のリポジトリから、そのコミットのソースを取る | `~/.gitlibs` |

test-runner のようにライブラリとして公開されていないものでも、Git から直接使えるのが Clojure CLI の特徴です。そして、そのときに **コミットの SHA を書く** ことが求められます。タグは付け替えられますが、SHA は付け替えられません。つまりこの 1 行は、それ自体がロックファイルの役割をしています。

### ロックファイルが無い

Ruby の `Gemfile.lock`、Rust の `Cargo.lock`、.NET の `packages.lock.json` と、多くの言語には「依存の解決結果を丸ごと記録するファイル」があります。**Clojure CLI にはありません。** `deps.edn` を書いたら、それがすべてです。sbt と同じ事情です。

そのかわり、Clojure CLI は解決結果を `.cpcache/` にキャッシュします。ただしこれはクラスパスの計算をやり直さないためのもので、コミットするものではありません（第 4 章で除外しました）。

本シリーズでは、次の方針で版を決めています。

- `:deps` には厳密な版だけを書く（範囲や `RELEASE` のような動的な指定を書かない）
- Git から取る依存には必ず `:git/sha` を書く
- 推移的に入ってくるライブラリの版は、必要になったときに `-Stree` で確かめる

宣言した 3 つの依存が、実際には何を連れてくるかは `-Stree` で見られます。

```bash
clojure -Stree
```

```text
org.clojure/clojure 1.12.0
  . org.clojure/spec.alpha 0.5.238
  . org.clojure/core.specs.alpha 0.4.74
org.clojure/data.csv 1.1.0
org.tribuo/tribuo-classification-tree 4.3.2
  . org.tribuo/tribuo-classification-core 4.3.2
    . org.tribuo/tribuo-core 4.3.2
      . com.oracle.labs.olcut/olcut-core 5.3.1
      . org.tribuo/tribuo-util-onnx 4.3.2
        . com.google.protobuf/protobuf-java 3.25.6
      . com.oracle.labs.olcut/olcut-config-protobuf 5.3.1
        . com.oracle.labs.olcut/olcut-core 5.3.1
      . com.google.protobuf/protobuf-java 3.25.6
    . org.tribuo/tribuo-common-tree 4.3.2
    . com.oracle.labs.olcut/olcut-core 5.3.1
      . org.jline/jline-terminal 3.27.1
        . org.jline/jline-native 3.27.1
      . org.jline/jline-reader 3.27.1
        . org.jline/jline-terminal 3.27.1
      ...
  . org.tribuo/tribuo-data 4.3.2
    . org.tribuo/tribuo-core 4.3.2
    . com.oracle.labs.olcut/olcut-core 5.3.1
    . org.tribuo/tribuo-util-tokenization 4.3.2
      . com.oracle.labs.olcut/olcut-core 5.3.1
    . com.opencsv/opencsv 5.9
      . org.apache.commons/commons-text 1.11.0
        X org.apache.commons/commons-lang3 3.13.0 :excluded
      . commons-beanutils/commons-beanutils 1.9.4
      . org.apache.commons/commons-collections4 4.4
    . org.apache.commons/commons-lang3 3.12.0
  . org.tribuo/tribuo-math 4.3.2
```

3 行書いただけで、Tribuo は分類の共通部分・決定木の共通部分・設定ライブラリ（OLCUT）・protobuf・jline・opencsv を連れてきます。`X` と `:excluded` が付いている行は、別の場所でより新しい版（`commons-lang3` は 3.12.0）が選ばれたため、こちらが採用されなかったことを示します。Maven と同じ「いちばん近い宣言が勝つ」規則です。

このように、ロックファイルが無くても **解決結果はいつでも表示できます**。本シリーズの規模（直接の依存が 3 つ）では、`deps.edn` に厳密な版を書き、必要なときに `-Stree` を見る、で足りると判断しました。推移的な依存まで完全に固定したい場合は、社内のリポジトリで版を決めるか、`:exclusions` と明示的な宣言で自分の側に引き取ることになります。

### 別名（alias）— 1 つのファイルに複数の顔を持たせる

`:aliases` は、Clojure CLI の中心にある仕組みです。基本の設定（`:paths` と `:deps`）に、名前を付けた差分を重ねます。

```bash
clojure -M:test       # :test の中身を足して実行
clojure -M:coverage   # :coverage の中身を足して実行
clojure -M:run chapter01
```

3 つの別名が何を足しているかを並べます。

| 別名 | `:extra-paths` | `:extra-deps` | `:main-opts` |
|------|---------------|--------------|-------------|
| `:test` | `test` | test-runner | `-m cognitect.test-runner` |
| `:coverage` | `test` | cloverage | `-m cloverage.coverage -p src -s test --text` |
| `:run` | （無し） | （無し） | `-m getting-started-ml.main` |

- `:extra-paths` は `:paths` に足すソースの置き場所。テストのコードは本番のクラスパスに入れたくないので、`:test` と `:coverage` のときだけ `test` を足します。Gradle の `testImplementation`、sbt の `% Test` に当たる区別を、Clojure では **パスの出し入れ** で表します
- `:extra-deps` は `:deps` に足す依存。test-runner も cloverage も、本番の実行には要りません
- `:main-opts` は、`-M` で起動したときに渡される引数。「どの名前空間の `-main` を呼ぶか」をここに書いておくと、使う側は別名の名前だけ覚えれば済みます

`-M` は「`clojure.main` で起動する」という指定です。ほかに `-X`（関数を引数のマップ付きで呼ぶ）や `-T`（ツールとして呼ぶ）もあります。test-runner を `-X:test` で呼ぶには `:exec-fn` の設定が必要なので、本シリーズでは `-M` にそろえました。

### 依存を先に取っておく

CI や、初めて手元に持ってきたときに、依存だけを先に取得したいことがあります。

```bash
clojure -P -M:test
```

`-P`（prepare）は「依存を取得してクラスパスを作るところまでやって、実行はしない」という指定です。本リポジトリでは、これを Gulp のタスクの `setup` に登録しています（第 6 章）。

## 5.3 Clojure と Clojure CLI と JDK の版

版を決める場所は、3 つに分かれます。

| 対象 | 決める場所 | 実測値 |
|------|----------|-------|
| Clojure（言語） | `deps.edn` の `:deps` | 1.12.0 |
| Clojure CLI（ビルドの道具） | Nix の開発環境 | 1.12.3.1577 |
| JDK | Nix の開発環境（Clojure CLI が持つ） | 21.0.8 |

sbt は `project/build.properties` の 1 行で **ビルドツール自身の版** をプロジェクトの側で決められましたが、Clojure CLI にはその仕組みがありません。`deps.edn` に「CLI の版」を書く場所は無く、環境に入っているものが使われます。かわりに、言語の版はプロジェクトの側にあります。境界の引き方が逆になっている、と覚えると分かりやすいでしょう。

JDK の版には、少し注意が要ります。Nix の開発環境に入って `java -version` と打つと、こう出ます。

```text
openjdk version "25.0.2" 2026-01-20
```

ところが、Clojure CLI が起動した JVM の中で `(System/getProperty "java.version")` を見ると `21.0.8` です。Nix の `clojure` のパッケージが、自分用の JDK 21 を抱えているからです。テストも検査もカバレッジもすべて Clojure CLI 経由で走るので、**実際に効いているのは 21.0.8** です。Java 版・Kotlin 版・Scala 版と同じ JDK 21 の世界にいます。

「環境に入っている `java` の版」と「実行に使われる JDK の版」が違いうる、というのは、記事の数値を再現するときに引っかかりやすい落とし穴です。版を報告するときは、シェルのコマンドではなく **走っているプログラムに聞く** ほうが確実です。

## 5.4 コードスタイル — cljfmt

### 設定ファイルを置かない

[cljfmt](https://github.com/weavejester/cljfmt) は Clojure のコードを整形する道具です。本シリーズでは設定ファイル（`.cljfmt.edn`）を置かず、既定のまま使っています。

Scala 版の scalafmt は `version` を書かないと起動すらしませんでしたが、cljfmt は設定ファイルが無くても動きます。cljfmt 自体の版は Nix の開発環境（`ops/nix/environments/clojure/shell.nix`）が固定しているので、整形の結果が人によって変わることもありません。

```text
cljfmt 0.15.6
```

Clojure のコードの整形は、ほとんどが **かっこの中の字下げ** の話です。行の長さや空白の入れ方について、言語のコミュニティに強い合意があるので、設定するところがあまりありません。設定項目を減らせるなら減らす、というのは、そのまま「議論を減らす」ことにつながります。

### cljfmt の実行

```bash
# 検査する（CI 向け）
cljfmt check src test

# 整形する
cljfmt fix src test
```

`check` は、整形されていないファイルがあると失敗します。わざと字下げを崩したファイルを置いて実行すると、こうなりました。

```clojure
(ns getting-started-ml.violation
  (:require [clojure.string :as str]))

(defn show [counts]
    (let [unused (count counts)]
  (reduce + counts)))
```

```bash
cljfmt check src test
echo "EXIT=$?"
```

```text
src/getting_started_ml/violation.clj has incorrect formatting
--- a/src/getting_started_ml/violation.clj
+++ b/src/getting_started_ml/violation.clj
@@ -2,5 +2,5 @@
   (:require [clojure.string :as str]))

 (defn show [counts]
-    (let [unused (count counts)]
-  (reduce + counts)))
+  (let [unused (count counts)]
+    (reduce + counts)))
1 file(s) formatted incorrectly
EXIT=1
```

親切なことに、**どう直せばよいかを差分の形で** 見せてくれます。`cljfmt fix` を走らせれば、そのとおりに直ります。

```text
Reformatted src/getting_started_ml/violation.clj
```

直したあとにもう一度 `check` を走らせると、こうなります。

```text
All source files formatted correctly
```

第 4 章で見たとおり、cljfmt が見ないものもあります。改行コード（CRLF）は指摘しません。道具の守備範囲を測っておき、外にあるものは別の層（`.gitattributes`）で守る、という話でした。

## 5.5 静的解析 — clj-kondo

### 型の無い言語で機械に見てもらう

[clj-kondo](https://github.com/clj-kondo/clj-kondo) は Clojure の静的解析の道具です。コードを実行せずに読み、未使用の束縛・未使用の名前空間・引数の数の間違い・重複したキーなどを指摘します。

Scala 版では、こうした指摘の多くをコンパイラが出していました。Clojure は動的型付けなので、コンパイラは「引数の数が合わない」程度しか教えてくれず、しかもその名前空間を読み込むまで分かりません。clj-kondo は、その穴を埋める道具です。**型の無い言語ほど、静的解析の道具の価値が大きい** と言えます。

cljfmt と同じく、設定ファイル（`.clj-kondo/config.edn`）は置かず、既定のままにしています。キャッシュのディレクトリも作らせていないので、第 4 章で見たとおり `.gitignore` に書く対象もありません。

### 終了コードが 3 段階ある

clj-kondo は、指摘の重さで終了コードを変えます。

| 終了コード | 意味 |
|-----------|------|
| 0 | 指摘が無い（または `--fail-level` より軽い指摘だけ） |
| 2 | warning 以上の指摘があった |
| 3 | error の指摘があった |

そして `--fail-level` で「どこから失敗にするか」を選べます。本シリーズでは `--fail-level warning` を指定しています。

```bash
clj-kondo --lint src test --fail-level warning
```

先ほどの違反のファイルを置いたまま実行すると、こうなりました。

```text
src/getting_started_ml/violation.clj:2:14: warning: namespace clojure.string is required but never used
src/getting_started_ml/violation.clj:5:11: warning: unused binding unused
linting took 510ms, errors: 0, warnings: 2
EXIT=2
```

`:require` したのに使っていない名前空間と、`let` で束縛したのに使っていない名前の両方を捕まえました。Scala 版の `-Wunused:all` と同じ範囲です。

この設定が効いていることは、`--fail-level error` に変えて試すと分かります。

```text
src/getting_started_ml/violation.clj:2:14: warning: namespace clojure.string is required but never used
src/getting_started_ml/violation.clj:5:9: warning: unused binding unused
linting took 251ms, errors: 0, warnings: 2
EXIT_error_level=0
```

**同じ指摘が出ているのに、終了コードは 0 です。**（`5:9` と桁が動いているのは、この実行の前に `cljfmt fix` で字下げを直したからです） CI はこれを成功と見なすので、警告は誰にも読まれずに流れていきます。Scala 版の `-Xfatal-warnings` が要だったのと同じ理由で、Clojure 版では `--fail-level warning` が要です。「警告を出すこと」と「警告で止めること」は別の設定であり、後者を書かないと前者は意味を持ちません。

なお、実測した clj-kondo 2025.10.23 では、`--fail-level` を付けずに実行しても終了コードは 2 でした（既定が warning）。それでも明示して書いているのは、既定に頼らないためです。既定は版が変われば変わりえますし、読む人に「ここは意図して警告で止めている」と伝える働きもあります。

違反を消してもう一度走らせると、こうなります。

```text
linting took 270ms, errors: 0, warnings: 0
EXIT_clean=0
```

### わざと違反を入れて確かめる

設定を書いただけでは、本当に検査されているか分かりません。検査の仕組みを入れたら、**わざと違反を入れて落ちることを確かめる** のが確実です。上の実験は、複製したプロジェクトで行い、確かめてから消しました。

このとき測れるのは「捕まる範囲」です。たとえば clj-kondo は、次のものは指摘しません。

- 関数の引数の **型** の食い違い（`nil` を渡してはいけない関数に `nil` を渡す、など）。型の情報が無いので原理的に分かりません
- 第 2 章の `number` が返す `nil`（欠損値）を、そのまま算術に渡してしまうこと

Scala 版は、これを `Option[Double]` という型で防いでいました。Clojure 版に同じ守りはありません。だから第 2 章の実データのテストでは、欠損値を補完したかどうかを自分で確かめる必要がありました。

```clojure
      (is (every? (fn [features] (every? some? (vals features))) (:x-train split)))
```

「訓練データのすべての特徴量が `nil` でない」ことを主張するこのテストは、Scala 版では型が保証してくれたので書く必要がなかったものです。**道具が見ないものはテストで守る。** 動的型付けの言語でテストを厚くする理由は、ここにあります。

### 指摘を抑制しない

clj-kondo には、指摘を抑制する仕組み（`#_:clj-kondo/ignore` や設定ファイルでの無効化）があります。本シリーズでは使わず、すべて直しました。抑制を許すと、抑制されたコードが増えていき、検査の意味が薄れていきます。

## 5.6 コードカバレッジ — cloverage

カバレッジは、テストがコードのどこを通ったかの割合です。[cloverage](https://github.com/cloverage/cloverage) で計測します。

```bash
clojure -M:coverage
```

`:coverage` の別名に書いた `-p src -s test --text` が、それぞれ「本番のソース」「テストのソース」「テキストのレポートも出す」という指定です。

```text
Ran 21 tests containing 51 assertions.
0 failures, 0 errors.
Ran tests.
Writing text report to: .../apps/clojure/target/coverage/coverage.txt
Writing HTML report to: .../apps/clojure/target/coverage/index.html

|------------------------------+---------+---------|
|                    Namespace | % Forms | % Lines |
|------------------------------+---------+---------|
| getting-started-ml.chapter01 |   78.48 |   87.80 |
| getting-started-ml.chapter02 |   63.56 |   73.75 |
| getting-started-ml.chapter03 |   62.23 |   71.13 |
|   getting-started-ml.dataset |  100.00 |  100.00 |
|      getting-started-ml.main |   20.41 |   54.55 |
|------------------------------+---------+---------|
|                    ALL FILES |   64.72 |   75.00 |
|------------------------------+---------+---------|
```

cloverage が数えるのは、行（`% Lines`）と **フォーム（`% Forms`）** です。フォームというのは、`(inc x)` のような 1 つの式のことです。行は「その行のどれかが実行された」で数えますが、フォームは式ごとに数えるので、1 行に複数の式が並ぶ Clojure のコードでは、こちらのほうが厳しい数字になります。名前空間ごとの内訳が出るのも、どこにテストが届いていないかを探すのに便利です。

`getting-started-ml.main` の 20.41% は、章を選ぶ入口の分岐（第 6 章）が、テストからほとんど呼ばれていないためです。

### `:paths` に test を含めないと落ちる

`:coverage` の別名には `:extra-paths ["test"]` を書いてあります。cloverage は `-s test` でテストの場所を教えられていますが、それは「どの名前空間をテストとして扱うか」の指定で、**クラスパスに載せるのは別の話** です。試しに `:extra-paths` を消して実行すると、こうなりました。

```text
Instrumented getting-started-ml.main
Execution error (FileNotFoundException) at cloverage.coverage/eval4911$fn$fn$run-tests (coverage.clj:211).
Could not locate getting_started_ml/chapter01_test__init.class, getting_started_ml/chapter01_test.clj or getting_started_ml/chapter01_test.cljc on classpath. Please check that namespaces with dashes use underscores in the Clojure file name.
```

本番のコードへの計測用の埋め込み（Instrumented）までは進み、テストを読み込むところで落ちます。エラーメッセージが「名前にダッシュを使う名前空間はファイル名をアンダースコアにしてください」と助言してくるので、最初はファイル名を疑ってしまいますが、原因はクラスパスです。**メッセージがいちばんありそうな原因を挙げてくるだけで、当たっているとは限りません。** こういう引っかかりどころは、`deps.edn` にコメントとして残しておきます。

```clojure
  ;; カバレッジ: clojure -M:coverage（:paths に test を含めないと落ちる）
```

### データの有無で数字が動く

第 4 章で見たとおり、学習データが無い環境では実データのテストが本体を実行しません。同じコードでも、カバレッジの数字は変わります。

| 実行の条件 | フォーム | 行 |
|-----------|--------|-----|
| 学習データあり | 75.04% | 85.17% |
| 学習データなし | 64.72% | 75.00% |

名前空間ごとに見ると、動いたのは第 2 章（63.56% から 78.47%）と第 3 章（62.23% から 74.22%）だけでした。第 1 章にも実データのテストはあるのですが、そこで通る経路は架空の値の単体テストでもすでに通っていたので、78.48%・87.80% のまま変わりません。「実データのテストを増やせばカバレッジが上がる」わけではない、ということです。

CI には学習データを置けないので、CI で出る数字は後者です。カバレッジに下限を設けて CI を失敗させる設定もありますが、本シリーズでは入れていません。データの有無で 10 ポイント動く数字を合格ラインにすると、「データが無いと落ちる CI」になってしまうからです。カバレッジは、合格・不合格を決める指標ではなく、**テストが通っていない場所を探すための地図** として使います。

レポートの HTML（`target/coverage/index.html`）をブラウザで開くと、どのフォームが通っていないかが色で分かります。第 8 章以降でコードが増えたら、ここを見て「テストを書いたつもりで通っていない分岐」を探します。

## 5.7 検査の 4 つを 1 行にする

ここまでの 4 つを、`&&` でつないだ 1 行が「Clojure 版の品質チェック」です。

```bash
cljfmt check src test && clj-kondo --lint src test --fail-level warning && clojure -M:test && clojure -M:coverage
```

`&&` でつないでいるので、前が失敗したらそこで止まります。順番にも意味があります。

1. **cljfmt** — いちばん速く、直し方も機械的。まずここで形をそろえる
2. **clj-kondo** — コードを実行せずに読む。JVM を起動しないので速い
3. **テスト** — ここで初めて JVM を起動してコードを動かす
4. **カバレッジ** — テストが通ったあとで、どこに届いていないかを見る

Scala 版の「sbt には `check` が無い」という話と同じで、Clojure CLI にも検査をまとめる既定のタスクはありません。この並びそのものが定義であり、第 6 章で見るように、Gulp のタスクと CI の両方に同じ並びが書かれています。

整形は検査の側に入れていません（`cljfmt fix` ではなく `check`）。CI で勝手にコードを書き換えるのは避け、CI は「整形されていない」と教えるだけにします。整形するのは手元の人間の仕事です。

## 5.8 まとめ

この章では、版を固定する仕組みと、品質を機械的に確かめる道具を整えました。

1. **deps.edn によるパッケージ管理** — 設定はプログラムではなくただのデータ（EDN）。Clojure 自身も依存の 1 つなので、言語の版をプロジェクトで固定できる。Maven から取るものは `:mvn/version`、Git から取るものは `:git/sha` まで書く
2. **ロックファイルが無い** — `deps.edn` に厳密な版だけを書き、解決結果は `-Stree` で確かめる。`.cpcache/` はキャッシュであってロックではない
3. **別名で 1 つのファイルに複数の顔を持たせる** — `:test`・`:coverage`・`:run` が、それぞれ足すパス・依存・起動の引数を持つ。テストのコードを本番のクラスパスに載せない区別は、パスの出し入れで表す
4. **整形** — cljfmt を設定ファイル無しで使い、`check` で検査、`fix` で整形する。直し方を差分で見せてくれる
5. **静的解析** — clj-kondo が、型の無い言語で機械に見てもらえる範囲を受け持つ。`--fail-level warning` を明示しないと、警告が出ていても終了コードが 0 になる。型で防げないもの（`nil` の混入など）はテストで守る
6. **カバレッジ** — cloverage でフォームと行を計測する。`:extra-paths ["test"]` が無いと、紛らわしいエラーで落ちる。学習データの有無で数字が動くので、合格ラインではなく地図として使う

次の章では、これらの検査を 1 つのコマンドにまとめ、GitHub Actions で自動的に実行する仕組みを作ります。
