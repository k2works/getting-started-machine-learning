---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "mix.exs の deps と mix.lock による版の固定、mix deps.tree、mix format と .formatter.exs、Credo の --strict と終了コード 4、未使用の変数はコンパイラが見ること、@spec と Dialyzer を使わない理由、mix test --cover のしきい値を summary の中に書くこと。"
tags: [article,getting-start-ml,elixir]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:00:00Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、何をコミットし、何をコミットしないかを決め、実験を再現するにはシードと版を固定する必要があることを確かめました。この章では、その「版を固定する」仕組みと、コードの品質を機械的に確かめる道具を整えます。

Elixir 版で使う道具は次のとおりです。

| 役割 | 道具 | 設定ファイル |
|------|------|------------|
| 依存の宣言・ビルド・タスク | Mix | `mix.exs` |
| 依存の固定 | Hex（`mix deps.get`） | `mix.lock` |
| テストの実行 | ExUnit（`mix test`） | `test/test_helper.exs` |
| 整形 | `mix format` | `.formatter.exs` |
| 静的解析（書き方） | Credo（`mix credo --strict`） | （設定ファイル無し。既定のまま） |
| 静的解析（未使用・到達不能） | Elixir のコンパイラ（`mix compile --warnings-as-errors`） | `mix.exs` の `elixirc_options` |
| カバレッジ | `mix test --cover` | `mix.exs` の `test_coverage` |

ほかの言語版と比べたときの特徴が 3 つあります。

1. **道具がすべて Mix の下にある**。Clojure 版は cljfmt と clj-kondo が `deps.edn` の外にある独立したコマンドでしたが、Elixir では `mix format` が標準、Credo は `mix.exs` の依存として入れる Mix タスクです。「どこに何があるか」を探し回らずに済みます
2. **静的解析が 2 つに分かれている**。Ruby 版は RuboCop 1 つが整形も書き方も見ていました。Elixir では、未使用の変数のような機械的に決まるものは **コンパイラ** が、書き方の好みに関わるものは **Credo** が見ます。2 つのコマンドを両方走らせる必要があります
3. **型検査の道具を使わない**。Elixir には `@spec` と Dialyzer がありますが、本シリーズでは使いません。理由は 5.5 節で説明します

## 5.2 mix.exs と mix.lock によるパッケージ管理

### mix.exs の全体

Mix の設定は `mix.exs` に書きます。拡張子が `.exs`（スクリプト）であるとおり、これは **実行される Elixir のコード** です。Clojure の `deps.edn` が「ただのデータ」だったのとは対照的で、sbt の `build.sbt` や Gradle の `build.gradle.kts` に近い立ち位置です。

```elixir
defmodule GettingStartedMl.MixProject do
  use Mix.Project

  def project do
    [
      app: :getting_started_ml,
      version: "0.1.0",
      elixir: "~> 1.18",
      elixirc_options: [warnings_as_errors: true],
      start_permanent: Mix.env() == :prod,
      deps: deps(),
      # NimbleCSV.define/2 が生成するパーサーはこちらが書いたコードではないので、
      # カバレッジの集計から外す（入れると総計が 47% まで落ちる）
      test_coverage: [ignore_modules: [GettingStartedMl.Csv.Parser], summary: [threshold: 70]]
    ]
  end

  def application do
    [extra_applications: [:logger]]
  end

  defp deps do
    [
      {:nimble_csv, "~> 1.3"},
      {:codepagex, "~> 0.1"},
      {:nx, "~> 0.13"},
      {:scholar, "~> 0.4"},
      {:plug, "~> 1.20"},
      {:bandit, "~> 1.12"},
      {:jason, "~> 1.4"},
      {:credo, "~> 1.7", only: [:dev, :test], runtime: false}
    ]
  end
end
```

`project/0` がキーワードリストを返し、`deps/0` が依存のリストを返します。設定が **関数の戻り値** なので、`Mix.env() == :prod` のような式を書けます。読み方は次のとおりです。

| キー | 意味 |
|------|------|
| `app` | アプリケーションの名前（アトム） |
| `elixir` | 必要な Elixir の版の **要求** |
| `elixirc_options` | コンパイラのオプション。ここで `warnings_as_errors` を既定にしている |
| `deps` | 依存のリスト |
| `test_coverage` | カバレッジの設定（5.6 節） |

### 悲観的バージョン制約 `~>`

依存の版には `~>` を使います。Ruby の `~>` と同じ「悲観的バージョン制約」です。

| 書き方 | 満たす版 |
|--------|---------|
| `~> 1.3` | 1.3 以上 2.0 未満 |
| `~> 1.3.0` | 1.3.0 以上 1.4.0 未満 |
| `~> 0.13` | 0.13 以上 1.0 未満 |

小数点以下の桁数で「どこまで上がってよいか」が変わります。`~> 1.3` は「メジャー版が上がらなければよい」、`~> 1.3.0` は「パッチだけ上がってよい」です。セマンティックバージョニングに従うライブラリなら、`~> 1.3` で後方互換が保たれます。

本シリーズはすべて 2 桁（`~> 1.3`・`~> 0.13` など）にしています。厳密な版を書かないのは、`mix.lock` があるからです。**範囲を `mix.exs` に、結果を `mix.lock` に** という二段構えが Elixir の（そして Ruby の）やり方です。Clojure 版は `deps.edn` に厳密な版を書くしかありませんでした。ロックファイルが無いからです。

### `only:` と `runtime:`

Credo の行だけ、ほかと形が違います。

```elixir
{:credo, "~> 1.7", only: [:dev, :test], runtime: false}
```

| オプション | 意味 |
|-----------|------|
| `only: [:dev, :test]` | `MIX_ENV` が `dev` か `test` のときだけ入れる。`prod` のビルドには含めない |
| `runtime: false` | アプリケーションを起動するときに一緒に起動しない。開発の道具であって、動くプログラムの部品ではない |

Gradle の `testImplementation`、sbt の `% Test` に当たる区別です。Clojure 版は、テスト用の依存を `:test` の別名の `:extra-deps` に置くことで表していました。Elixir はオプションで表します。

`runtime: false` を付けないと、`mix run` でアプリケーションを起動したときに Credo も OTP アプリケーションとして起動しようとします。検査の道具にはそんなことは要りません。

### mix.lock

`mix deps.get` を走らせると `mix.lock` が作られます（第 4 章）。中身は Elixir のマップのリテラルです。

`mix.exs` の 8 つの依存が、実際には何を連れてくるかは `mix deps.tree` で見られます。

```bash
mix deps.tree
```

```text
getting_started_ml
├── bandit ~> 1.12 (Hex package)
│   ├── hpax ~> 1.0 (Hex package)
│   ├── plug ~> 1.18 (Hex package)
│   ├── telemetry ~> 0.4 or ~> 1.0 (Hex package)
│   ├── thousand_island ~> 1.5 (Hex package)
│   │   └── telemetry ~> 0.4 or ~> 1.0 (Hex package)
│   └── websock ~> 0.5 (Hex package)
├── codepagex ~> 0.1 (Hex package)
├── credo ~> 1.7 (Hex package)
│   ├── bunt ~> 0.2.1 or ~> 1.0 (Hex package)
│   ├── file_system ~> 0.2 or ~> 1.0 (Hex package)
│   └── jason ~> 1.0 (Hex package)
├── jason ~> 1.4 (Hex package)
├── nimble_csv ~> 1.3 (Hex package)
├── nx ~> 0.13 (Hex package)
│   ├── complex ~> 0.7 (Hex package)
│   └── telemetry ~> 0.4.0 or ~> 1.0 (Hex package)
├── plug ~> 1.20 (Hex package)
│   ├── mime ~> 1.0 or ~> 2.0 (Hex package)
│   ├── plug_crypto ~> 1.1.1 or ~> 1.2 or ~> 2.0 (Hex package)
│   └── telemetry ~> 0.4.3 or ~> 1.0 (Hex package)
└── scholar ~> 0.4 (Hex package)
    ├── nimble_options ~> 0.5.2 or ~> 1.0 (Hex package)
    ├── nx ~> 0.9 (Hex package)
    └── polaris ~> 0.1 (Hex package)
        └── nx ~> 0.5 or ~> 1.0 (Hex package)
```

直接の 8 つに対して、全部で 19 のライブラリが入ります。Clojure 版が `-Stree` で見せた Tribuo の木（分類・決定木・OLCUT・protobuf・jline・opencsv）と比べるとずいぶん浅く、深さは 3 段までです。Elixir のライブラリは小さく保たれる傾向があり、`telemetry` のような共通の部品を複数のライブラリが同じ版で共有しています。

`telemetry` が 4 回（bandit・thousand_island・nx・plug から）現れているのに注目してください。それぞれ要求する範囲が違います（`~> 0.4 or ~> 1.0`・`~> 0.4.0 or ~> 1.0`・`~> 0.4.3 or ~> 1.0`）。`mix deps.get` は、これらすべてを満たす版を 1 つ選びます。

```bash
mix deps.get
```

```text
Resolving Hex dependencies...
Resolution completed in 0.111s
Unchanged:
  bandit 1.12.5
  bunt 1.0.0
  codepagex 0.1.13
  complex 0.7.0
  credo 1.7.19
  file_system 1.1.1
  hpax 1.0.4
  jason 1.4.5
  mime 2.0.7
  nimble_csv 1.3.0
  nimble_options 1.1.1
  nx 0.13.1
  plug 1.20.3
  plug_crypto 2.2.0
  polaris 0.2.0
  scholar 0.4.2
  telemetry 1.4.2
  thousand_island 1.5.0
  websock 0.5.3
All dependencies are up to date
```

`telemetry 1.4.2` が 1 つだけ選ばれています。Maven の「いちばん近い宣言が勝つ」とは違い、Hex は **すべての要求を同時に満たす解を探す** 制約充足の解決器を持っています。解が無ければ、どの要求が衝突しているかを教えてエラーになります。「たまたま近い宣言が勝って古い版が入っていた」という事故が起きにくい設計です。

`Unchanged:` と出ているのは、`mix.lock` に記録されている版がすべての要求を満たしていたからです。ロックが効いている証拠でもあります。`mix deps.update <ライブラリ>` を打てば、そのライブラリだけを解決し直して `mix.lock` を書き換えます。

## 5.3 Elixir と OTP の版

版を決める場所は、3 つに分かれます。

| 対象 | 決める場所 | 実測値 |
|------|----------|-------|
| Elixir（言語） | `mix.exs` の `elixir:` に **要求** を書き、実体は Nix の開発環境 | 1.18.4 |
| Mix（ビルドの道具） | Elixir に付属（版は Elixir と同じ） | 1.18.4 |
| OTP（Erlang VM） | Nix の開発環境（Elixir が自分の OTP を持つ） | 27（erts 15.2.7.4） |

Clojure 版は、言語そのものが `deps.edn` の依存の 1 つだったので、プロジェクトの側で完全に固定できました。Elixir の `mix.exs` に書ける `elixir: "~> 1.18"` は要求であって固定ではありません。`~> 1.18` を満たす 1.18 と 1.19 のどちらが使われるかは、環境に入っているものが決めます。

OTP の版には、少し注意が要ります。Nix の開発環境に入ると、こう表示されます。

```text
Elixir development environment activated
  - Elixir: Elixir 1.18.4 (compiled with Erlang/OTP 27)
  - Erlang: 28
```

**表示に 27 と 28 が両方出ています。** `erl` コマンドを叩くと OTP 28 が答えますが、Elixir 自身は「OTP 27 でコンパイルされた」と言っています。実際にどちらで動いているかは、プログラムに聞くのが確実です。

```bash
elixir -e 'IO.puts("elixir sees OTP " <> System.otp_release())'
```

```text
elixir sees OTP 27
```

```text
Erlang/OTP 27 [erts-15.2.7.4] [source] [64-bit] [smp:8:8] [ds:8:8:10] [async-threads:1]

Elixir 1.18.4 (compiled with Erlang/OTP 27)
```

`elixir --version` が先に出す 1 行目は、**Elixir が実際に起動した VM** の情報です。erts 15.2.7.4、つまり OTP 27 です。Nix の Elixir のパッケージが、自分用の OTP 27 を抱えているからです。テストも検査もカバレッジもすべて `mix` 経由で走るので、**実際に効いているのは 27** です。

Clojure 版でもまったく同じ形の食い違いがありました。環境の `java -version` は 25.0.2 でしたが、Clojure CLI が起動した JVM は 21.0.8 でした。**「環境に入っているランタイムの版」と「実行に使われるランタイムの版」は別物でありうる**、というのは、記事の数値を再現するときに引っかかりやすい落とし穴です。版を報告するときは、シェルのコマンドではなく走っているプログラムに聞きます。

## 5.4 コードスタイル — `mix format`

### 設定ファイル

`mix format` は Elixir に標準で付いてくる整形の道具です。設定は `.formatter.exs` に書きます。本シリーズの設定は 3 行だけです。

```elixir
[
  inputs: ["{mix,.formatter}.exs", "{config,lib,test}/**/*.{ex,exs}"]
]
```

`inputs` は「どのファイルを整形の対象にするか」です。これしか書いていません。行の長さ（既定は 98）も、空行の入れ方も、既定のまま使います。

Clojure 版の cljfmt も設定ファイルを置かずに使っていましたが、事情が少し違います。cljfmt は「置かなくても動く」道具で、置けば設定できました。`mix format` は **言語に標準で付いてくる** ので、コミュニティ全体が同じ既定を使っています。Elixir のコードは、どのプロジェクトを開いても同じ形をしています。Go の `gofmt` に近い立ち位置です。設定するところが無いなら、議論も起きません。

`inputs` に何を書くかだけが判断の余地です。第 4 章で見たとおり、ここに書かれなかったファイル（`mix.lock`・`.gitignore`・YAML）は `mix format` の守備範囲の外になります。

### `mix format` の実行

```bash
# 検査する（CI 向け）
mix format --check-formatted

# 整形する
mix format
```

`--check-formatted` は、整形されていないファイルがあると失敗します。わざと崩したファイルを置いて実行すると、こうなりました。

```elixir
defmodule GettingStartedMl.Violation do
  def f( x ) do
      x+1
  end
end
```

```bash
mix format --check-formatted
echo "EXIT=$?"
```

```text
** (Mix) mix format failed due to --check-formatted.
The following files are not formatted:

.../apps/elixir/lib/getting_started_ml/violation.ex

1 1  |defmodule GettingStartedMl.Violation do
2   -|  def f( x ) do
3   -|      x+1
  2 +|  def f(x) do
  3 +|    x + 1
4 4  |  end
5 5  |end

EXIT=1
```

cljfmt と同じく、**どう直せばよいかを差分の形で** 見せてくれます。左に元の行番号、右に整形後の行番号が並び、変わらない行は両方の番号が出ます。`mix format` を引数無しで走らせれば、そのとおりに直ります。

```elixir
defmodule GettingStartedMl.Violation do
  def f(x) do
    x + 1
  end
end
```

引数の丸かっこの中の空白と、`+` の前後の空白の両方が直りました。第 4 章で見たとおり、CRLF も同じ仕組みで指摘されます。

## 5.5 静的解析 — コンパイラと Credo

### 2 つに分かれている

Elixir の静的解析は、2 つの道具に分かれています。

| 道具 | 見るもの | 失敗させ方 |
|------|---------|----------|
| コンパイラ | 未使用の変数・未使用の alias・未使用のモジュール属性・存在しない関数の呼び出し・到達しない節 | `mix compile --warnings-as-errors` |
| Credo | @moduledoc の有無・関数の複雑さ・命名・パイプラインの書き方・`alias` の並び順など | `mix credo --strict` |

役割分担の線は「**機械的に決まるか、好みが入るか**」です。未使用の変数は、コンパイラが構文木を見れば確実に分かります。@moduledoc を書くかどうかは、プロジェクトの方針です。Ruby 版の RuboCop が両方を 1 つで見ていたのとは違い、Elixir では前者が言語に組み込まれています。

この分担は、**2 つとも走らせないと守れない** ことを意味します。片方だけでは穴が開きます。実際に確かめてみます。

### 未使用の変数はコンパイラが捕まえる

わざと未使用の変数を入れたファイルを置きます。

```elixir
defmodule GettingStartedMl.Violation do
  @moduledoc "検査を確かめるための一時的なモジュール。"

  @doc "使わない変数を束縛する。"
  def f(x) do
    unused = x + 1
    x
  end
end
```

```bash
mix compile --warnings-as-errors
echo "EXIT=$?"
```

```text
    warning: variable "unused" is unused (if the variable is not meant to be used, prefix it with an underscore)
    │
  6 │     unused = x + 1
    │     ~~~~~~
    │
    └─ lib/getting_started_ml/violation.ex:6:5: GettingStartedMl.Violation.f/1

Compilation failed due to warnings while using the --warnings-as-errors option
EXIT=1
```

コンパイラが行と桁を示し、`~~~~~~` で該当箇所に下線を引き、「使わないつもりならアンダースコアを前に付けよ」という直し方まで教えてくれます。そして `--warnings-as-errors` によって終了コード 1 で止まります。

同じファイルに Credo をかけるとどうなるか。

```bash
mix credo --strict
echo "EXIT=$?"
```

```text
Checking 11 source files ...

Analysis took 0.2 seconds (0.06s to load, 0.2s running 69 checks on 11 files)
76 mods/funs, found no issues.

EXIT=0
```

**Credo は何も言いません。** 未使用の変数は Credo の担当ではないからです。もし `mix credo --strict` だけを CI に入れていたら、この違反は素通りします。第 4 章で第 1 章のコミットに CI を入れた話をしましたが、その CI には最初から `mix compile --warnings-as-errors` が入っています。

`--warnings-as-errors` は `mix.exs` の `elixirc_options` にも書いてあります。

```elixir
      elixirc_options: [warnings_as_errors: true],
```

こちらは「このプロジェクトのコンパイルは既定で警告を失敗にする」という宣言で、コマンドラインのフラグはそれを明示的に繰り返しているだけです。両方書いているのは、Clojure 版で clj-kondo の `--fail-level warning` を（既定と同じでも）明示したのと同じ理由です。既定は版が変われば変わりえますし、CI の定義を読む人に「ここは意図して警告で止めている」と伝える働きもあります。

### Credo は書き方を見る — 終了コードは 4

今度は、コンパイラが何も言わないが Credo が指摘するものを置きます。`@moduledoc` を書かないだけのファイルです。

```elixir
defmodule GettingStartedMl.Violation do
  def f(x) do
    cond do
      x > 0 -> "正"
      x < 0 -> "負"
      true -> "零"
    end
  end
end
```

```bash
mix credo --strict
echo "EXIT=$?"
```

```text
Checking 11 source files ...

  Code Readability

┃ [R] → Modules should have a @moduledoc tag.
┃       lib/getting_started_ml/violation.ex:1:11 #(GettingStartedMl.Violation)

Analysis took 0.2 seconds (0.04s to load, 0.1s running 69 checks on 11 files)
76 mods/funs, found 1 code readability issue.

Use `mix credo explain` to explain issues, `mix credo --help` for options.

EXIT=4
```

**終了コードが 4 です。** 0 でも 1 でもありません。Credo は、指摘の分類ごとに違うビットを立てた値を返します。

| 値 | 意味 |
|----|------|
| 1 | Consistency（一貫性） |
| 2 | Design（設計） |
| 4 | Readability（可読性） |
| 8 | Refactoring（リファクタリングの機会） |
| 16 | Warning（警告） |

複数の分類にまたがる指摘があれば、値を足したものが返ります。可読性と設計の両方なら 6 です。「終了コードが 0 でなければ失敗」と扱っている限り問題は起きませんが、**「終了コードが 1 かどうか」で判定する仕組みに入れると、Credo の失敗を見落とします**。CI や `&&` でつないだコマンド列は 0 かどうかで判断するので大丈夫ですが、自作のスクリプトを書くときは気を付けるところです。

`--strict` は、既定では報告しない低優先度の指摘まで出させるオプションです。本シリーズでは設定ファイル（`.credo.exs`）を置かず、`--strict` を付けた既定の 69 個の検査をそのまま使っています。Ruby 版では、日本語のメソッド名や 1 文字の変数名（機械学習の慣例の `x`・`t`）を許すために `.rubocop.yml` に 5 つの設定を足す必要がありました。Elixir では 1 つも足していません。テスト名は文字列なので識別子の規則に触れず、`x` や `t` のような短い名前も Credo は問題にしないからです。

### 型検査の道具を使わない

Elixir には、関数の型を書く `@spec` と、それを検査する Dialyzer があります。本シリーズでは **どちらも使いません**（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。Ruby 版が RBS・Steep を使わないと決めたのと同じ判断です。

理由は、Elixir 版の軸の 1 つが「**型を宣言しない言語で、テストが唯一の安全網になることを示す**」ことだからです。本シリーズには、型で守る言語版（Java・Kotlin・Scala・F#・Rust・Go・C#）と、型を宣言しない言語版（Python・Ruby・Clojure・Elixir）の両方があります。後者で型検査の道具を足してしまうと、比較の軸がぼやけます。

そのぶん、**型で防げるはずのものをテストで守る** 必要があります。Clojure 版の第 5 章に、まさにその例がありました。

```clojure
      (is (every? (fn [features] (every? some? (vals features))) (:x-train split)))
```

「訓練データのすべての特徴量が `nil` でない」ことを主張するテストです。Scala 版なら `Option[Double]` という型が保証してくれるので、書く必要がありませんでした。Elixir 版も事情は同じです。第 2 章の `number/2` は欠損値に `nil` を返すので、補完し忘れたまま算術に渡すと実行時に落ちます。コンパイラも Credo もこれを教えてくれません。

ただし Elixir には、型を書かなくても効く守りが 2 つあります。

1 つは **パターンマッチ** です。

```elixir
  def next_int(state, bound) when bound > 0 do
```

`when bound > 0` というガードは、`bound` が 0 以下なら「この関数節に一致しない」ことを意味します。一致する節が無ければ `FunctionClauseError` になります。「負の範囲を渡してはいけない」という契約を、コメントではなくコードに書けます。Ruby 版なら `raise ArgumentError` を自分で書くところです。

もう 1 つは **`!` の付いた関数** です。

```elixir
  def read(path) do
    path |> File.read!() |> parse()
  end
```

`File.read/1` は `{:ok, 中身}` か `{:error, 理由}` を返しますが、`File.read!/1` は失敗すると例外を投げます。名前の `!` が「これは失敗したら止まる」という印です。第 1 章の `Map.fetch!/2` も同じです。**失敗しうる場所が関数名に現れる** ので、読むときに目で追えます。型ではありませんが、型が担っていた役割の一部を名前が担っています。

それでも、`nil` が混ざる経路や、キーの綴り間違い（`:身長` を `:身丈` と書く）は実行して初めて分かります。**道具が見ないものはテストで守る。** 動的型付けの言語でテストを厚くする理由は、ここにあります。

### 指摘を抑制しない

Credo には指摘を抑制する仕組み（`# credo:disable-for-next-line` や `.credo.exs` での無効化）があります。コンパイラの警告も、変数名の頭にアンダースコアを付ければ消せます。本シリーズでは、抑制ではなく **直す** ことにしています。抑制を許すと、抑制されたコードが増えていき、検査の意味が薄れていきます。

## 5.6 コードカバレッジ — `mix test --cover`

### 標準の仕組みを使う

カバレッジは、テストがコードのどこを通ったかの割合です。Elixir では `mix test --cover` が標準で用意されています。Ruby 版の SimpleCov、Clojure 版の cloverage のような追加の依存が要りません。中身は Erlang の `:cover` モジュールで、BEAM ファイルを「カバレッジ計測用にコンパイルし直して」実行します。

```bash
mix test --cover
```

```text
Cover compiling modules ...
学習データが見つからないので :data のテストを外します（../data/sukkiri-ml）
Running ExUnit with seed: 275396, max_cases: 16
Excluding tags: [:data]

..................................................
Finished in 0.1 seconds (0.1s async, 0.00s sync)
53 tests, 0 failures, 3 excluded

Generating cover results ...

Percentage | Module
-----------|--------------------------
    66.00% | GettingStartedMl.Chapter02
    73.21% | GettingStartedMl.Chapter03
    76.92% | GettingStartedMl.Chapter01
    85.71% | GettingStartedMl.Csv
    94.74% | GettingStartedMl.Random
   100.00% | GettingStartedMl.Dataset
-----------|--------------------------
    75.46% | Total

Generated HTML coverage results in "cover" directory
```

数えているのは **行** です。cloverage の「フォーム」のような細かい単位はありません。モジュールごとの内訳が出るので、どこにテストが届いていないかを探すのに使えます。`GettingStartedMl.Random` が 94.74% と高いのは、第 2 章で乱数生成器の性質（同じシードで同じ並び・要素が増減しない・空のリスト）を 5 つのテストで囲ったからです。第 2 章と第 3 章が低いのは、`run/0`（実データを読んで表を印字する関数）が `@tag :data` で外されているためです。

HTML のレポート（`cover/` ディレクトリ）をブラウザで開くと、どの行が通っていないかが色で分かります。第 8 章以降でコードが増えたら、ここを見て「テストを書いたつもりで通っていない分岐」を探します。

### しきい値は `summary` の中に書く — 黙って無視される設定

`mix test --cover` には、カバレッジが一定を下回ったら失敗させる仕組みがあります。設定は `mix.exs` の `test_coverage` に書きます。ここに **落とし穴があります**。

本シリーズの設定はこうです。

```elixir
      test_coverage: [ignore_modules: [GettingStartedMl.Csv.Parser], summary: [threshold: 70]]
```

`threshold: 70` が `summary:` の **中** にあることに注目してください。これを外に出して、こう書いたらどうなるか。

```elixir
      test_coverage: [ignore_modules: [GettingStartedMl.Csv.Parser], threshold: 70]
```

```bash
mix test --cover
echo "EXIT=$?"
```

```text
-----------|--------------------------
    75.46% | Total

Coverage test failed, threshold not met:

    Coverage:   75.46%
    Threshold:  90.00%

Generated HTML coverage results in "cover" directory
EXIT=3
```

**Threshold が 90.00% になっています。** 書いた 70 はどこにも出てきません。`test_coverage` の直下の `threshold:` は Mix が知らないキーなので、**エラーも警告も出さずに捨てられ**、既定の 90% が使われました。そして 75.46% は 90% に届かないので、終了コード 3 で失敗します。

たちが悪いのは、この間違いが **失敗する方向に転ぶ** ことです。CI が落ちるので、「カバレッジが足りない」と思ってテストを増やそうとします。設定の書き方が違うだけだとは、まず気付きません。逆に、しきい値を 95 にしたいときに同じ間違いをすれば、90% で通ってしまい、こちらは誰も気付きません。

**設定を書いたら、それが効いていることを確かめます。** 確かめ方は簡単で、しきい値を極端な値（たとえば 99）にして落ちるか、0 にして通るかを見ればよいのです。上の出力のように、Mix は使ったしきい値を表示してくれるので、それが書いた値と一致しているかを読むだけでも足ります。

正しく書いた設定では、75.46% が 70% を上回るので通ります。

### 生成されたモジュールをカバレッジから外す

もう 1 つの設定 `ignore_modules:` にも理由があります。第 1 章の `GettingStartedMl.Csv` は、NimbleCSV にパーサーを作らせています。

```elixir
  NimbleCSV.define(GettingStartedMl.Csv.Parser, separator: ",", escape: "\"")
```

`NimbleCSV.define/2` はマクロで、その場で `GettingStartedMl.Csv.Parser` というモジュールを **生成** します。書いたのは 1 行ですが、生成されるのは CSV の全機能（引用符・エスケープ・ストリーム処理・書き出し）を持つ大きなモジュールです。本シリーズはそのうち `parse_string/2` しか使いません。

これをカバレッジの集計に入れるとどうなるか。

```text
Percentage | Module
-----------|--------------------------
    30.88% | GettingStartedMl.Csv.Parser
    66.00% | GettingStartedMl.Chapter02
    73.21% | GettingStartedMl.Chapter03
    76.92% | GettingStartedMl.Chapter01
    85.71% | GettingStartedMl.Csv
    94.74% | GettingStartedMl.Random
   100.00% | GettingStartedMl.Dataset
-----------|--------------------------
    62.34% | Total

Coverage test failed, threshold not met:

    Coverage:   62.34%
    Threshold:  70.00%
```

30.88% のモジュールが 1 つ増えただけで、総計が 75.46% から **62.34%** に落ちます。13 ポイントです。しきい値 70% を下回るので CI が落ちます。

ここでの判断は「しきい値を下げる」ではなく「**数えるものを正す**」です。`GettingStartedMl.Csv.Parser` は自分が書いたコードではありません。書いていないコードにテストが届いていないのは当たり前で、それを総計に混ぜると、自分のコードのカバレッジが読めなくなります。

```elixir
      test_coverage: [ignore_modules: [GettingStartedMl.Csv.Parser], summary: [threshold: 70]]
```

マクロがモジュールを生成する言語では、これが繰り返し起きます。Phoenix を使えばルーターが、Ecto を使えばスキーマが、同じようにコードを生成します。**カバレッジの数字を読む前に、何が数えられているかを見る。** モジュールの一覧に見覚えのない名前が並んでいたら、それが生成されたものかどうかを確かめます。

### 数字の読み方

カバレッジは、合格・不合格を決める指標ではなく、**テストが通っていない場所を探すための地図** として使うのが本筋です。それでも本シリーズがしきい値 70% を入れているのは、「下がったら気付く」ための下限としてです。70 という値は、学習データが無い CI で通る水準（75.46%）に少し余裕を持たせて決めました。

学習データを置いて走らせると、`@tag :data` のテストが実行されるので数字は上がります。CI には学習データを置けないので、CI で出るのは低いほうの数字です。**しきい値は、低いほうの条件で通る値にしておかなければなりません。** データがある環境の数字でしきい値を決めると、「データが無いと落ちる CI」になってしまいます。

## 5.7 検査の 4 つを 1 行にする

ここまでの 4 つを、`&&` でつないだ 1 行が「Elixir 版の品質チェック」です。

```bash
mix format --check-formatted && mix compile --warnings-as-errors && mix credo --strict && mix test --cover
```

`&&` でつないでいるので、前が失敗したらそこで止まります。順番にも意味があります。

1. **`mix format --check-formatted`** — いちばん速く、直し方も機械的。まずここで形をそろえる
2. **`mix compile --warnings-as-errors`** — コンパイルが通らなければ、あとの 2 つは走らせる意味が無い。未使用の変数などもここで捕まる
3. **`mix credo --strict`** — コンパイルが通ったコードに対して、書き方を見る
4. **`mix test --cover`** — テストを走らせ、カバレッジを測る

Clojure 版の並び（cljfmt → clj-kondo → テスト → カバレッジ）と形は同じですが、2 番目が違います。Clojure 版は clj-kondo という外部の道具でしたが、Elixir では **コンパイラそのもの** です。そして Clojure 版がテストとカバレッジを別のコマンドで 2 回走らせていたのに対し、Elixir は `mix test --cover` の 1 回で済みます。

整形は検査の側に入れています（`mix format` ではなく `--check-formatted`）。CI で勝手にコードを書き換えるのは避け、CI は「整形されていない」と教えるだけにします。整形するのは手元の人間の仕事です。

第 6 章で見るように、この並びは Gulp のタスクと CI の両方に同じ順で書かれています。そして 4 つが本当に落ちることを、1 つずつわざと壊して確かめます。

## 5.8 まとめ

この章では、版を固定する仕組みと、品質を機械的に確かめる道具を整えました。

1. **mix.exs と mix.lock** — `mix.exs` は実行されるコードで、依存には悲観的バージョン制約 `~>` を書く。結果は `mix.lock` に固定され、Hex の解決器がすべての要求を同時に満たす版を選ぶ。`only:` と `runtime: false` で開発の道具を本番から外す
2. **版の食い違い** — `erl` は OTP 28 だが Elixir は自分が持つ OTP 27 で動く。Clojure 版の「`java` は 25 だが Clojure は JDK 21」と同じ形。版はシェルではなく走っているプログラムに聞く
3. **整形** — `mix format` は言語に標準で付いてくるので、設定は `inputs` だけ。`--check-formatted` が差分で直し方を見せる
4. **静的解析が 2 つに分かれる** — 未使用の変数は Credo ではなく **コンパイラ** が捕まえる。`mix credo --strict` だけでは素通りする。両方を走らせる
5. **Credo の終了コードは 4** — 指摘の分類ごとにビットが立つので、可読性の指摘だけなら 4。「1 かどうか」で判定する仕組みに入れると見落とす
6. **型を使わない** — `@spec` も Dialyzer も使わないのは、動的型付けの言語でテストが唯一の安全網になることを示すため。そのぶんガード（`when`）と `!` 付きの関数名が、契約の一部をコードに残す
7. **カバレッジの設定は効いていることを確かめる** — `threshold:` を `summary:` の外に書くと黙って無視され、既定の 90% が使われる。生成されたモジュール（`GettingStartedMl.Csv.Parser`）を外さないと総計が 13 ポイント落ちる。数字を読む前に、何が数えられているかを見る

次の章では、これらの検査を 1 つのコマンドにまとめ、GitHub Actions で自動的に実行する仕組みを作ります。そして 4 つの検査が本当に効いていることを、1 つずつ壊して確かめます。
