---
type: Article
title: "第 1 章: 機械学習とはじめてのテスト"
description: "機械学習とルールベースの違いを確認し、きのこ派・たけのこ派の判定を Elixir の TDD で実装して正解率を測る。パターンマッチと関数節で分岐を書き、パイプライン演算子でデータを流す書き方を Clojure 版・F# 版・Ruby 版と対比する。"
tags: [article,getting-start-ml,elixir]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:00:00Z }
---

# 第 1 章: 機械学習とはじめてのテスト

## 1.1 はじめに

この章では、機械学習とは何かを確認したうえで、テスト駆動開発（TDD）で最初のプログラムを Elixir で作ります。題材は「きのこの山派か、たけのこの里派か」を身長・体重・年代から判定する問題です。

この章ではまだ機械学習のアルゴリズムを使いません。人間が考えたルールで判定し、その正解率を測るところまで進みます。「ルールを人間が書く」とはどういうことかを先に体験しておくと、第 3 章で「ルールをデータから学ばせる」ことの意味がはっきりします。

[Python 版の第 1 章](../python/01-machine-learning-and-first-test.md) と同じ題材・同じ TODO リストで進めます。Elixir 版では次の 3 つと対比します。1 つは [F# 版](../fsharp/01-machine-learning-and-first-test.md) と [Clojure 版](../clojure/01-machine-learning-and-first-test.md) で、不変のデータを関数で変換していく流儀が近いところです。2 つめは [Python 版](../python/01-machine-learning-and-first-test.md) で、Nx が NumPy、Scholar が scikit-learn にあたります（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。3 つめは [Ruby 版](../ruby/01-machine-learning-and-first-test.md) と [Clojure 版](../clojure/01-machine-learning-and-first-test.md) で、型を宣言しない言語としてテストが安全網になる点が同じです。

## 1.2 機械学習とは

### ルールを書くか、データに学ばせるか

従来のプログラミングでは、人間が判定のルールをコードとして書きます。

```elixir
def predict_by_rule(%{年代: @kinoko_age_group}), do: @kinoko
def predict_by_rule(%{年代: _}), do: @takenoko
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
3. **モデルを学習させる** — 決定木・回帰などを自作し、Scholar と突き合わせます（第 3 章以降）
4. **評価する** — 正解率などの指標で測ります（この章で正解率から始めます）
5. **改善する** — 特徴量を作り直し、ハイパーパラメータを調整します（第 9 章以降）

Elixir 版には 1 つ断っておくことがあります。**Scholar には決定木とランダムフォレストがありません。** 本書の背骨である決定木が丸ごと無いので、第 3 章以降で自作した決定木は、ライブラリに置き換えられないまま最終実装になります（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。ほかの言語版が「自作してからライブラリと突き合わせる」構成をとるのに対して、Elixir 版は**突き合わせる相手がいない章が多い**という性格を持ちます。これは Elixir の機械学習の生態系の現状であり、「ライブラリが育っていない領域では、自作がそのまま本番の実装になる」例としてそのまま扱います。

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

「身長」「体重」「年代」が特徴量、「派閥」が正解ラベルです。列名が日本語であることと、ファイルの先頭に BOM（バイトオーダーマーク）が付いていることに注意してください。**Elixir 版では BOM を自分で取り除きます。** 後で確かめます。

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

実装は `apps/elixir/` に置きます。Mix のプロジェクトが 1 つ、その中に章ごとのモジュールを並べる構成です。

```text
apps/elixir/
├── mix.exs
├── mix.lock
├── .formatter.exs
├── config/
│   └── config.exs
├── lib/
│   └── getting_started_ml/
│       ├── dataset.ex      # 学習データの場所
│       ├── csv.ex          # CSV の読み込み（章をまたいで使う）
│       └── chapter01.ex    # 第 1 章
└── test/
    ├── test_helper.exs
    └── getting_started_ml/
        └── chapter01_test.exs
```

モジュール名とファイル名の対応は素直です。`GettingStartedMl.Chapter01` が `lib/getting_started_ml/chapter01.ex` で、**大文字小文字とアンダースコアの読み替えだけ**です。Clojure 版がダッシュとアンダースコアの読み替えで引っかかったのに比べると、ここは踏まない落とし穴になっています。しかも Elixir は**ファイル名とモジュール名の一致を強制しません**（慣習として合わせるだけ）。Mix が `lib/` の下を全部コンパイルするので、名前が合っていなくても動きます。

`mix.exs` は次のとおりです。

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

ここで大事なのは、**`mix.exs` が設定ファイルではなく Elixir のコードだ**ということです。`defmodule` で始まり、`project/0` がキーワードリストを返す関数です。Clojure 版の `deps.edn` が「データ」だったのとは逆で、こちらは「実行される関数」です。だから `Mix.env() == :prod` のような式をその場に書けます。build.gradle や build.sbt と同じ立場ですが、DSL ではなく素の言語の関数呼び出しである点が違います。

依存の版は `"~> 1.3"` のように範囲で書き、実際に使う版は `mix.lock` に固定されます。Cargo の `Cargo.lock`、Bundler の `Gemfile.lock` と同じ仕組みです。`deps/` に依存のソース、`_build/` にコンパイル済みの成果物が置かれます。どちらもコミットしません。

`credo` の行にある `only: [:dev, :test], runtime: false` は「開発とテストのときだけ取り込み、アプリケーションとしては起動しない」という指定です。静的解析の道具を本番の依存に混ぜないための書き方です。

設定ファイルは `config/config.exs` に 1 つだけあります。

```elixir
import Config

# codepagex は既定で一部の符号化表しか組み込まない。
# 第 9 章が読む Shift_JIS（CP932）の CSV のために、明示して組み込む。
config :codepagex, :encodings, ["VENDORS/MICSFT/WINDOWS/CP932"]
```

この章ではまだ使いませんが、**設定がコンパイル時に効く**ことだけ覚えておいてください。codepagex は指定された符号化表のぶんだけ変換関数を生成するので、この設定を書き忘れると第 9 章で実行時に `{:error, "Unknown encoding ..."}` になります。

### Nix 環境と、OTP の版の落とし穴

Elixir の開発環境は `nix develop .#elixir` に入ると揃います。

```text
$ nix develop .#elixir
Elixir development environment activated
  - Elixir: Elixir 1.18.4 (compiled with Erlang/OTP 27)
  - Erlang: 28
```

この 2 行目と 3 行目が食い違っているのが分かるでしょうか。**`erl` コマンドは OTP 28 ですが、Elixir 自身は OTP 27 でコンパイルされていて、OTP 27 の上で動きます。**

```text
$ elixir -e "IO.puts System.otp_release()"
27
$ erl -noshell -eval "io:format(\"~s~n\",[erlang:system_info(otp_release)]),halt()."
28
```

Clojure 版で見た「シェルの `java` は 25 だが、Clojure が使う JDK は 21」とまったく同じ形です。`elixir` コマンドのラッパーが自分の Erlang/OTP を抱えているので、シェルに見えている `erl` の版は当てになりません。**「その言語処理系が実際に動いている VM の版」は、処理系自身に聞く**——`System.otp_release/0` がその口です。ここを取り違えると、OTP 28 で入った関数を使おうとして「そんな関数は無い」と言われることになります。

### 最初のテストを走らせる

いちばん最初に書くのは、環境が動くことを確かめるテストです。その前に、学習データの置き場を返すモジュールを作っておきます。

```elixir
defmodule GettingStartedMl.Dataset do
  @moduledoc "学習データのディレクトリを求める。"

  @env_name "ML_DATA_DIR"
  @default_dir "../data/sukkiri-ml"

  @doc """
  学習データのディレクトリを返す。

  環境変数はテストで差し替えられるように引数で受け取る。
  """
  def dir(env \\ System.get_env()) do
    case Map.get(env, @env_name) do
      nil -> @default_dir
      "" -> @default_dir
      value -> value
    end
  end
end
```

Elixir らしい点が 3 つあります。

1. **`@moduledoc` と `@doc` が言語の機能。** コメントではなくモジュール属性で、コンパイル後もバイトコードに残り、`h GettingStartedMl.Dataset.dir` で読み出せます。Clojure の docstring と同じ立場ですが、複数行の文字列（`"""`）を素直に書けます
2. **`\\` が既定引数。** `dir(env \\ System.get_env())` と書くと、`dir()` と `dir(%{"ML_DATA_DIR" => "/tmp/data"})` の両方が書けます。Clojure 版が多アリティ（`([] ...)` と `([env] ...)`）で書いたところを、Elixir では既定引数 1 つで済ませています。**テストは引数ありの形を呼んで環境変数を差し替えます**
3. **`case` が値で分岐する。** `nil` と `""` と「それ以外」を上から順に照合します。`if` を書かずに、パターンで分けるのが Elixir の基本の形です

`@env_name` や `@default_dir` はモジュール属性です。定数のように見えますが、**コンパイル時に値が埋め込まれる**という点が違います。実行時に読み出されるのではなく、使っている場所にそのまま展開されます。だから後で見るように、パターンの中にも書けます。

テストを走らせてみます。

```text
$ mix test
Running ExUnit with seed: 428334, max_cases: 16

..........
Finished in 0.08 seconds (0.08s async, 0.00s sync)
10 tests, 0 failures
```

`max_cases: 16` は、テストを 16 並列まで走らせるという意味です。ExUnit は**モジュール単位で並行に走ります**（`use ExUnit.Case, async: true` を書いたものだけ）。BEAM の軽量プロセスがそのままテストの並列実行に使われていて、設定なしで最初から効いています。`seed:` はテストの実行順を決める乱数の種で、走らせるたびに変わります。順序に依存したテストを書くと、たまに落ちて気づけるという仕掛けです。

## 1.6 ルールで派閥を判定する

TODO リストは CSV の読み込みから始まっていますが、**いちばん中心にある「判定」から**着手します。CSV の読み込みは外側の関心事で、判定の仕様とは独立だからです。

### データをどう表すか

その前に決めることがあります。**1 人分のデータをどう表すか**です。

ほかの言語版では、ここで型を宣言しました。Rust は `struct Features`、Scala は `case class`、Java は `record`、Ruby は `Data.define` です。Elixir では**素のマップ**を使います。

```elixir
%{身長: 170, 体重: 60, 年代: 20, 派閥: "きのこ"}
```

`身長:` はアトムのキーです。アトムは自分自身が値である名前で、Clojure のキーワード、Ruby のシンボルにあたります。**アトムに日本語をそのまま使えます。** `%{身長: 170}` は `%{:身長 => 170}` の短縮形で、値を取り出すには `row.身長` または `Map.fetch!(row, :身長)` と書きます。

```elixir
iex> %{身長: 170, 体重: 60, 年代: 20}.年代
20
```

Clojure のキーワードと 1 つ違うのは、**アトムはそれ自身が関数ではない**ことです。Clojure なら `(mapv :派閥 people)` と書けましたが、Elixir では `Enum.map(people, & &1.派閥)` と、無名関数を 1 つ挟みます。`&` と `&1` は無名関数の短縮記法で、`fn row -> row.派閥 end` と同じ意味です。

もう 1 つ、Elixir 固有の注意があります。**アトムはガベージコレクションされません。** 外から来た文字列を無制限に `String.to_atom/1` でアトムにすると、アトム表が溢れて VM が落ちます。この章では CSV の列名（4 つ）をアトムにしますが、行の値はアトムにしません。列名は有限で、こちらが管理しているものだからです。

型を宣言しないことには代償があります。`:年代` を `:年令` と書き間違えても、コンパイラは何も言いません。マップに無いキーを `row.年令` で引くと `KeyError` になりますが、それは実行時です。Ruby 版・Clojure 版と同じく、**テストが唯一の安全網**です。ADR 012 で `@spec` と Dialyzer を使わないと決めたのも、この「動的型付けの言語で、型の誤りをテストで捕まえる」ことを正面から示すためです。Ruby 版が RBS・Steep を使わなかったのと同じ扱いで、詳しい理由は第 5 章で扱います。

一方で得るものもあります。マップとリストは Elixir の標準のデータなので、`Enum.map`・`Enum.filter`・`Map.take`・`Enum.zip` といった関数がそのまま全部使えます。列を 1 つ足すのに型の宣言を直す必要もありません。ADR 012 で Explorer（データフレームのライブラリ）を採用しなかったのも、素のマップとリストで十分に書けて、そのデータ操作自体が記事の題材になるからです。

### Red: まだ無いものを呼ぶ

テストを先に書きます。

```elixir
defmodule GettingStartedMl.Chapter01Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter01, as: C

  defp features(height, weight, age_group) do
    %{身長: height, 体重: weight, 年代: age_group}
  end

  describe "ルールによる判定" do
    test "二十代はきのこ派と判定する" do
      assert C.predict_by_rule(features(170, 60, 20)) == C.kinoko()
    end
  end
end
```

**テスト名に日本語をそのまま使えます。** `test "二十代はきのこ派と判定する" do ... end` の文字列がそのまま名前になり、失敗したときの見出しにもなります。Go 版のように識別子の制約に悩む必要はありません。

`describe` はテストのまとまりに見出しを付けます。Clojure 版の `testing`、RSpec の `describe` にあたります。`assert` は 1 つの式を受け取り、それが真でなければ失敗します。`assert_equal` のような専用の表明が並んでいるのではなく、**`assert 式` だけ**です。マクロなので、失敗したときには式そのものと、左辺・右辺の値を表示してくれます。

`alias GettingStartedMl.Chapter01, as: C` は長いモジュール名に短い別名を付ける書き方です。`alias` を書かなければ毎回フルネームで書くことになります。

`defp` は非公開の関数の定義です。テストの中でしか使わないヘルパーなので、モジュールの外には出しません。

この時点で `C.predict_by_rule/1` も `C.kinoko/0` もありません。走らせます。

```text
$ mix test
Compiling 2 files (.ex)
    error: undefined function predict_by_rule/1 (expected GettingStartedMl.Chapter01 to define such a function or for it to be imported, but none are available)
    │
 47 │     predictions = Enum.map(x, &predict_by_rule/1)
    │                                ^^^^^^^^^^^^^^^
    │
    └─ lib/getting_started_ml/chapter01.ex:47:32: GettingStartedMl.Chapter01.run/0


== Compilation error in file lib/getting_started_ml/chapter01.ex ==
** (CompileError) lib/getting_started_ml/chapter01.ex: cannot compile module GettingStartedMl.Chapter01 (errors have been logged)
```

ここが面白いところです。Elixir は型を宣言しない動的な言語ですが、**同じモジュールの中で未定義の関数を呼ぶとコンパイルエラーになります**。指摘されているのはテストの行ではなく、`run/0` の中の `&predict_by_rule/1` です。`&関数名/引数の数` という参照の書き方が、コンパイル時に解決されるからです。

| 言語版 | Red の出方 |
|--------|-----------|
| Rust・Java・Scala・Go・C# | コンパイルエラー（型も名前も検査される） |
| **Elixir** | **コンパイルエラー（同一モジュール内の名前は検査されるが、型は検査されない）** |
| Clojure | コンパイルエラー（名前は検査されるが、型は検査されない） |
| Python・Ruby・TypeScript（実行時） | 実行時エラー（`NameError`・`NoMethodError`） |

Elixir は Clojure と同じく「動的型付けだが名前はある程度静的」という中間にいます。ただし**別のモジュールの関数を呼ぶ場合は警告にとどまります**。モジュールは独立してコンパイルされるので、呼び先が後から現れる可能性を残しているからです。綴りの間違いは早く見つかりますが、`predict_by_rule("文字列")` のような**型の誤りは実行するまで分かりません**。

### Green: 仮実装

いちばん単純に、定数を返します。

```elixir
@kinoko "きのこ"
@takenoko "たけのこ"

@doc "きのこ派の呼び名。"
def kinoko, do: @kinoko

@doc "たけのこ派の呼び名。"
def takenoko, do: @takenoko

def predict_by_rule(_features), do: @kinoko
```

`def 名前, do: 式` は 1 行で書く形で、`def 名前 do ... end` と同じ意味です。短い関数はこの形で書きます。

`_features` の先頭のアンダースコアは「受け取るが使わない」という印です。これを付けないとコンパイラが「変数が使われていない」と警告します。後で確かめますが、この警告は `--warnings-as-errors` を付けると失敗になるので、Elixir では**アンダースコアを付けるかどうかが、書き手の意思表示として強制されます**。

`@kinoko` を値として外に出すために、`kinoko/0` という関数を用意しているところに注目してください。**モジュール属性はコンパイル時に展開されるので、モジュールの外からは見えません。** `C.kinoko()` のように関数で包まないと、テストから参照できません。Clojure 版が `(def kinoko "きのこ")` をそのまま公開できたのとは違うところです。

これで最初のテストは通ります。

### 三角測量: パターンマッチと関数節

仮実装を本実装に進めるために、もう 1 つテストを足します。

```elixir
test "二十代以外はたけのこ派と判定する" do
  for age_group <- [10, 30, 40, 50] do
    assert C.predict_by_rule(features(170, 60, age_group)) == C.takenoko()
  end
end
```

```text
$ mix test
  2) test ルールによる判定 二十代以外はたけのこ派と判定する (GettingStartedMl.Chapter01Test)
     test/getting_started_ml/chapter01_test.exs:26
     Assertion with == failed
     code:  assert C.predict_by_rule(features(170, 60, age_group)) == C.takenoko()
     left:  "きのこ"
     right: "たけのこ"
     stacktrace:
       test/getting_started_ml/chapter01_test.exs:28: anonymous fn/2 in GettingStartedMl.Chapter01Test."test ルールによる判定 二十代以外はたけのこ派と判定する"/1
       (elixir 1.18.4) lib/enum.ex:2546: Enum."-reduce/3-lists^foldl/2-0-"/3
       test/getting_started_ml/chapter01_test.exs:27: (test)
```

失敗メッセージがよくできています。**`code:` に式がそのまま出て、`left:` と `right:` に評価した値が出ます。** `assert` がマクロなので、評価する前のソースコードと評価した後の値の両方を持てるわけです。`describe` の見出し（ルールによる判定）とテスト名が連結されて 1 行になるのも読みやすいところです。

スタックトレースに `Enum."-reduce/3-lists^foldl/2-0-"/3` という奇妙な名前が混ざっています。`for` 内包表記が内部で `Enum.reduce` に展開され、その無名関数が BEAM の関数名として現れたものです。**言語の糖衣が剥がれた姿がスタックトレースに出る**のは、Elixir が Erlang の VM の上に乗っていることの現れです。

この失敗を受けて、本実装に進みます。ここが Elixir 版でいちばん見どころのあるところです。

```elixir
@kinoko_age_group 20

@doc "人間が決めたルールで派閥を判定する。"
def predict_by_rule(%{年代: @kinoko_age_group}), do: @kinoko
def predict_by_rule(%{年代: _}), do: @takenoko
```

**`if` がありません。** 同じ名前の関数を 2 つ並べ、**引数の形で呼び分けています**。これを関数節（function clause）といい、上から順に照合されて、最初に合った節が実行されます。

読み解いてみます。

- `%{年代: @kinoko_age_group}` は「マップであり、`:年代` というキーを持ち、その値が `20` である」というパターンです。`@kinoko_age_group` はコンパイル時に `20` に展開されるので、パターンの中に書けます。**定数をパターンに埋め込めるのは、モジュール属性がコンパイル時のものだからです**
- `%{年代: _}` は「マップであり、`:年代` というキーを持つ（値は何でもよい）」というパターンです。`_` は「照合するが束縛しない」という印です
- マップのパターンは**部分一致**です。`%{年代: 20}` は `%{身長: 170, 体重: 60, 年代: 20}` に合います。書いたキーだけを見て、ほかのキーは無視されます。リストやタプルのパターンが完全一致なのとは違う、マップだけの性質です

この 2 行が、ほかの言語版の分岐に相当します。

| 言語版 | 分岐の書き方 |
|--------|------------|
| Python・Ruby | `if`（Ruby 版は三項演算子） |
| Clojure | `(if (= kinoko-age-group (:年代 features)) kinoko takenoko)` |
| Rust・Scala・F# | `match`／パターンマッチ（1 つの式の中で分岐） |
| **Elixir** | **関数節を 2 つ並べる（関数の定義そのもので分岐する）** |

Rust や Scala のパターンマッチは「1 つの関数の中に `match` 式を書く」形でしたが、Elixir では**関数の定義が複数に分かれます**。分岐が増えるほど関数の行数が増えるのではなく、関数の数が増えます。これは「条件分岐を減らして、条件ごとに別の入り口を用意する」という設計を、言語が既定の書き方として押し出しているということです。

2 つめの節をなぜ `predict_by_rule(_features)` ではなく `predict_by_rule(%{年代: _})` と書いているのかも説明しておきます。**`:年代` を持たないマップが来たら、どちらの節にも合わずに落ちてほしい**からです。

```text
iex> GettingStartedMl.Chapter01.predict_by_rule(%{身長: 170})
** (FunctionClauseError) no function clause matching in GettingStartedMl.Chapter01.predict_by_rule/1
```

`FunctionClauseError` は「どの節にも合わなかった」というエラーです。`_features` と書いて何でも受けてしまうと、`:年代` の綴りを間違えたデータが黙ってたけのこ派と判定されます。**パターンを広く書きすぎないことが、型を宣言しない言語での防御**になります。Ruby 版・Clojure 版が「何も気づかずに通ってしまう」場面を、Elixir はパターンの狭さで塞げます。

## 1.7 失敗を例外で表す

CSV の読み込みに進む前に、**失敗をどう表すか**を決めます。Elixir では例外を使います。

```elixir
raise ArgumentError, "予測と正解ラベルの件数が違います: #{length(predictions)} と #{length(labels)}"
```

`raise モジュール, メッセージ` が例外を投げる形です。`#{...}` は文字列の中に式を埋め込む記法で、Ruby と同じです。`ArgumentError` は標準の例外で、「引数がおかしい」ことを表します。

ここが言語ごとにいちばん分かれるところなので、並べておきます。

| 言語版 | 失敗の表し方 | 網羅性の検査 |
|--------|------------|------------|
| Rust | `Result<T, Error>` と `enum Error` | `match` の網羅性をコンパイラが検査する |
| F#・Scala | `Result`・`Either` と判別共用体 | 同じく検査する |
| Go | `(値, error)` の多値返却 | されない |
| Java | 検査例外 `throws` | 宣言はあるが、種類の網羅は検査されない |
| Clojure | JDK の例外を `throw` する | されない（宣言もしない） |
| **Elixir** | **`raise` で例外を投げる** | **されない（宣言もしない）** |
| C#・Kotlin・Python・TypeScript・Ruby | 例外 | されない |

Elixir には「失敗を値で返す」流儀もあります。`{:ok, value}` と `{:error, reason}` のタプルを返す書き方で、標準ライブラリの多くがこの形を持っています。実際、`File.read/1` は `{:ok, contents}` か `{:error, :enoent}` を返し、`File.read!/1`（末尾に `!`）は失敗すると例外を投げます。**同じ操作に 2 つの版が用意されていて、呼ぶ側が選べる**わけです。Rust の `Result` と `unwrap()` の関係に近いのですが、Elixir では「エラーを見たいときは `{:ok, _}` の版、見なくてよいときは `!` の版」という慣習が言語全体に行き渡っています。

この章では `!` の版を使い、自前の失敗も例外で表します。読み込みに失敗したら処理を続けようがないからで、Elixir でいう「let it crash」——**回復できない失敗はその場で落とす**——の考え方に沿っています。`{:error, reason}` で丁寧に返しても、呼ぶ側にできるのは「落とす」ことだけです。

テストでは `assert_raise` で、例外の種類とメッセージの両方を確かめます。

```elixir
test "件数が違えば正解率を求められない" do
  assert_raise ArgumentError, "予測と正解ラベルの件数が違います: 1 と 2", fn ->
    C.accuracy([C.kinoko()], [C.kinoko(), C.takenoko()])
  end
end
```

`fn -> ... end` が無名関数で、`assert_raise` はこれを呼び出して例外が出るかを見ます。第 2 引数に文字列を渡すと**メッセージの完全一致**を、正規表現を渡すと部分一致を確かめます。ここでは完全一致にしています。メッセージに件数を埋め込んでいるので、数字がずれたら気づけます。

## 1.8 CSV を読み込む

### NimbleCSV は BOM を取り除かない

ここで、本シリーズを通しての「BOM の落とし穴」がまた出てきます。

BOM 付きの CSV を NimbleCSV で素直に読むと、先頭の列名に BOM が残ります。ADR 012 を書く前に、使い捨てのプロジェクトで確かめました。先頭の列名が `"\uFEFF身長"`（先頭に見えない BOM が付いた文字列）になり、`:身長` では引けません。Python 版の `encoding='utf-8-sig'`、Java 版・C# 版の対処、Go 版の `TrimPrefix`、Clojure 版の `str/replace-first` と同じ場面に来ました。

| 言語版 | BOM の扱い |
|--------|-----------|
| Rust（csv クレート） | ライブラリが自動で取り除く |
| Ruby（`CSV.foreach`） | ファイルを開けば取り除かれる（`CSV.parse` では残る） |
| Python | `encoding='utf-8-sig'` を指定すれば取り除かれる |
| Clojure（`clojure.data.csv`） | 取り除かれない。自分で取り除く |
| **Elixir（NimbleCSV）** | **取り除かれない。自分で取り除く** |
| Elixir（Explorer） | 取り除かれるが、ADR 012 で使わないと決めた |

Explorer（データフレームのライブラリ）を使えば BOM の問題は消えます。それでも使わないと決めたのは、データフレームのライブラリを使わないというシリーズ全体の方針です（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。**「面倒を見てくれるライブラリを選ぶ」か「自分で面倒を見る」かは設計の選択**で、ここでは後者を選んで、その 1 行をコードに書きます。

なお Elixir 版では、BOM のほかに**アトムのキーが混ざる**と困る事情もあります。`"\uFEFF身長"` を `String.to_atom/1` に渡すと、BOM 付きのアトムが 1 つ増えます。アトムは回収されないので、取り除くのはメモリの都合でもあります。

### NimbleCSV.define がモジュールを作る

CSV の読み込みは、章をまたいで使うので `GettingStartedMl.Csv` に切り出します。

```elixir
defmodule GettingStartedMl.Csv do
  @moduledoc """
  CSV を列名つきで読む。

  NimbleCSV は BOM を取り除かないので、先頭の列名から自分で取り除く。
  """

  NimbleCSV.define(GettingStartedMl.Csv.Parser, separator: ",", escape: "\"")

  alias GettingStartedMl.Csv.Parser

  @bom "\uFEFF"
```

いきなり変わったものが出てきました。`NimbleCSV.define/2` は**関数ではなくマクロで、呼ぶと新しいモジュールが 1 つ生成されます**。`GettingStartedMl.Csv.Parser` というモジュールが、指定した区切り文字と引用符に特化した形でコンパイル時に作られます。

ほかの言語の CSV ライブラリは、区切り文字を実行時の引数として受け取ります。NimbleCSV は**コンパイル時に設定を固定して、専用のパーサーを生成する**という設計です。実行時に設定を見に行かないぶん速くなります。名前の「Nimble（軽快な）」はそこから来ています。

この設計には代償があります。**生成されたモジュールは、こちらが書いていないのにこちらのコードとして数えられます。** 後でカバレッジのところで、これが実際に問題になります。

`@bom "\uFEFF"` が BOM の文字です。記事では見えないので `\uFEFF` のエスケープで書いていますが、コードでもエスケープのまま書いています。**BOM を実体の文字としてソースに書かないほうがよい**からです。見えない文字は、コピーや編集のときに事故のもとになります。

### 読み込みの実装

```elixir
  @doc """
  CSV を読み、1 行目を列名にしたマップのリストを返す。

  列名はアトムにする。値は文字列のまま返し、数値への変換は章ごとに行う。
  """
  def read(path) do
    path |> File.read!() |> parse()
  end

  @doc "文字列を列名つきのマップのリストにする。"
  def parse(contents), do: contents |> parse_table() |> elem(1)

  @doc "文字列を「列名の並び」と「行のリスト」にする。"
  def parse_table(contents) do
    [header | rows] = Parser.parse_string(contents, skip_headers: false)
    columns = header |> strip_bom() |> Enum.map(&String.to_atom/1)

    {columns, Enum.map(rows, fn row -> columns |> Enum.zip(row) |> Map.new() end)}
  end

  defp strip_bom([first | rest]), do: [String.replace_prefix(first, @bom, "") | rest]
end
```

短いですが、Elixir の道具がいくつも出てきます。

**パイプライン演算子 `|>`** が主役です。`path |> File.read!() |> parse()` は `parse(File.read!(path))` と同じ意味で、**左の値を右の関数の第 1 引数に渡します**。読む順序と処理の順序が一致するので、入れ子の括弧を目で解きほぐす必要がありません。

```elixir
header |> strip_bom() |> Enum.map(&String.to_atom/1)
```

これは「ヘッダーを取り、BOM を取り除き、それぞれをアトムにする」と左から右に読めます。

F# 版の `|>` とまったく同じ記号ですが、成り立ちが少し違います。F# の `|>` は**関数を値として扱う普通の演算子**（`x |> f` は `f x`）で、部分適用と組み合わせて使います。Elixir の `|>` は**マクロで、コンパイル時に呼び出しの形を書き換えます**。だから `|> Enum.map(&String.to_atom/1)` のように、第 2 引数以降をその場に書けます。F# なら `List.map` の引数の順序が「関数が先、リストが後」に設計されていることで同じことを実現していました。**Elixir の標準ライブラリが「データを第 1 引数にとる」規約で統一されている**のは、`|>` を気持ちよく使うためです。

Clojure のスレッディングマクロとも比べておきます。Clojure には `->`（第 1 引数に入れる）と `->>`（最後の引数に入れる）の 2 つがあり、ライブラリごとに引数の順序が違うので使い分けが要ります。Elixir は `|>` 1 つで、**規約のほうを揃えた**わけです。

残りの道具です。

- **`[header | rows]`** はリストのパターンです。先頭をヘッダー、残りを行に分けます。`|` がリストの「頭と尾」を分ける記号で、F# の `head :: tail` にあたります。**代入ではなくパターンマッチ**なので、空のリストが来たら `MatchError` で落ちます
- **`&String.to_atom/1`** は既存の関数を値として渡す書き方です。`fn s -> String.to_atom(s) end` と同じ意味で、`&モジュール.関数/引数の数` と書きます
- **`Enum.zip(columns, row) |> Map.new()`** が列名と値を組にしてマップを作ります。`Enum.zip([:身長, :体重], ["170", "60"])` が `[{:身長, "170"}, {:体重, "60"}]` になり、`Map.new/1` がそれを `%{身長: "170", 体重: "60"}` にします。Python の `dict(zip(...))`、Clojure の `zipmap` と同じ働きです
- **`String.replace_prefix/3`** は「先頭がその文字列なら置き換える」関数です。先頭以外の BOM には触りません。名前に `prefix` と書いてあるので、意図が読み手に伝わります

`parse_table/1` が列名の並びと行を組（タプル）で返し、`parse/1` が `elem(1)` で行だけを取り出しているのは、**Elixir のマップがキーの順を保たない**ためです。この章では列の順を使いませんが、第 2 章以降で列の並びが要る場面が出てくるので、両方の入り口を用意しています。

### 行を人物にする

読み込んだ行は値が全部文字列なので、数値に直しつつ列の不足を弾きます。

```elixir
defp to_person(row) do
  for column <- [:派閥 | @feature_keys], not is_map_key(row, column) do
    raise ArgumentError, "列がありません: #{column}"
  end

  %{
    身長: number(row, :身長),
    体重: number(row, :体重),
    年代: number(row, :年代),
    派閥: row.派閥
  }
end
```

まず列がそろっているかを確かめ、それからマップを組み立てます。**列を確かめる工程と値を変換する工程が、同じ関数の中で 2 つの式として並ぶ**のは Clojure 版と同じ形です。Rust 版が `?` 演算子で構造体のリテラルの中に失敗の可能性を書けたのとは対照的に、こちらは「先に全部確かめる」という素直な形になりました。

`for` の行に見慣れない書き方があります。

```elixir
for column <- [:派閥 | @feature_keys], not is_map_key(row, column) do
```

`for` は内包表記で、`<-` が生成、カンマの後ろがフィルターです。**「列を順に見て、マップにその列が無いものだけについて、本体を実行する」**という意味になります。列が全部そろっていれば本体は一度も実行されず、内包表記は空のリストを返して捨てられます。Python の内包表記と同じ形ですが、**副作用（例外を投げる）のために内包表記を使っている**のが変わったところです。

`[:派閥 | @feature_keys]` は「`:派閥` を先頭に足したリスト」です。パターンで使った `|` と同じ記号が、こちらでは組み立てに使われています。**分解にも組み立てにも同じ記号を使う**のが、パターンマッチのある言語に共通する性質です。

`is_map_key/2` はガードでも使える組み込み関数です。`Map.has_key?/2` でも同じことができますが、`is_` で始まる関数はガード（`when` 節）の中でも書けるという違いがあります。

数値の変換はこうです。

```elixir
defp number(row, column) do
  cell = Map.fetch!(row, column)

  case Integer.parse(cell) do
    {value, ""} -> value
    _ -> raise ArgumentError, "#{column} を数値として読めません: #{cell}"
  end
end
```

`Integer.parse/1` は **`{整数, 残りの文字列}` か `:error` を返します。** `"170"` なら `{170, ""}`、`"170cm"` なら `{170, "cm"}`、`"高い"` なら `:error` です。つまり**途中まで読めたら読めたぶんを返す**という、ゆるい関数です。

だから `{value, ""}` というパターンで受けます。「整数として読めて、しかも残りが空である」場合だけを通すという意味です。`"170cm"` は `{170, "cm"}` になり、このパターンに合わないので下の `_` に落ちて例外になります。**パターンで「残りが空」まで書けるので、`if` を足さずに厳密な検査になっています。**

`Map.fetch!/2` は、キーが無ければ `KeyError` を投げる版です。ここに来る時点で列の存在は確かめてあるので、`!` の版でよいという判断です。

`#{column}` でアトムを文字列に埋め込むと `身長` になります（コロンは付きません）。メッセージを組み立てるのに `Atom.to_string/1` を呼ぶ必要はありません。

### テスト

テストは一時ファイルを書いて読ませます。

```elixir
defp with_csv(contents, fun) do
  path = Path.join(System.tmp_dir!(), "people-#{System.unique_integer([:positive])}.csv")
  File.write!(path, contents)

  try do
    fun.(path)
  after
    File.rm(path)
  end
end
```

`try do ... after ... end` は、本体の成否によらず `after` を実行します。Java の `finally`、Clojure の `finally` と同じです。

`System.unique_integer([:positive])` でファイル名を一意にしているのは、**テストが並行に走るから**です。`use ExUnit.Case, async: true` を書いた時点で、このモジュールはほかのテストモジュールと同時に走ります。固定の名前にすると、たまたま同時に走ったテストどうしがファイルを奪い合います。並行がありがたい代わりに、共有資源には名前をずらす手当てが要る——BEAM の言語らしい注意点です。

`fun.(path)` の**ドットに注目してください**。無名関数を呼ぶときは `fun.(引数)` とドットを書きます。名前付きの関数（`Map.fetch!(row, column)`）と区別するためで、Elixir を書きはじめてしばらくは必ず忘れるところです。

```elixir
test "BOM 付きの CSV を列名で読み込む" do
  with_csv("\uFEFF身長,体重,年代,派閥\n170,60,20,きのこ\n", fn path ->
    assert C.load_people(path) == [%{身長: 170, 体重: 60, 年代: 20, 派閥: C.kinoko()}]
  end)
end

test "数値でない値があれば読み込めない" do
  with_csv("身長,体重,年代,派閥\n高い,60,20,きのこ\n", fn path ->
    assert_raise ArgumentError, "身長 を数値として読めません: 高い", fn -> C.load_people(path) end
  end)
end

test "列が無ければ読み込めない" do
  with_csv("身長,体重,年代\n170,60,20\n", fn path ->
    assert_raise ArgumentError, "列がありません: 派閥", fn -> C.load_people(path) end
  end)
end
```

`assert` の中身に注目してください。**期待値がマップのリストで、そのまま `==` で比べられます。**

```elixir
assert C.load_people(path) == [%{身長: 170, 体重: 60, 年代: 20, 派閥: "きのこ"}]
```

Elixir のマップとリストは中身で比較されるので、Java 版・C# 版が配列の比較で苦労した部分も、Rust 版が `#[derive(PartialEq)]` で得た性質も、宣言なしで最初から使えます。**値で比べられることが、テストを書きやすくしています。** Clojure 版・F# 版と共通する性質です。

読み込みの本体は 1 行です。

```elixir
@doc "CSV を読み込み、列名で値を取り出して人物のリストにする。"
def load_people(path) do
  path |> Csv.read() |> Enum.map(&to_person/1)
end
```

`alias GettingStartedMl.{Csv, Dataset}` と書いておくと、`Csv` と `Dataset` の 2 つの別名をまとめて作れます。波括弧で共通の前置きをくくる書き方です。

## 1.9 特徴量と正解ラベルに分ける

```elixir
@feature_keys [:身長, :体重, :年代]

@doc "人物のリストを特徴量と正解ラベルに分ける。"
def split_features_and_labels(people) do
  {Enum.map(people, &Map.take(&1, @feature_keys)), Enum.map(people, & &1.派閥)}
end
```

1 行です。

- `Map.take(&1, @feature_keys)` が、人物のマップから `:身長 :体重 :年代` だけを取り出した新しいマップを返します。**「必要な列だけ選ぶ」が関数 1 つ**で、ほかの言語版で `Features` 型を作って詰め替えていた部分にあたります。Clojure の `select-keys` と同じ働きです
- `& &1.派閥` が正解ラベルを取り出す無名関数です。`&` と `&1` の間の空白は必須で、詰めて書くと別の意味（`&&1`）に読まれます

戻り値は**タプル**です。`{特徴量, 正解ラベル}` の形で、呼ぶ側はパターンマッチで受け取ります。

```elixir
{x, t} = C.split_features_and_labels(people)
```

Elixir の `=` は代入ではなく**マッチ演算子**です。左辺をパターンとして右辺に照合し、合えば変数を束縛します。合わなければ `MatchError` で落ちます。だから `{x, t} = ...` は「右辺が 2 要素のタプルであることを確かめつつ、中身を取り出す」という意味になります。Scala 版・F# 版のタプルの分解、Go 版の多値返却にあたるものです。

タプルとリストの使い分けにも決まりがあります。**タプルは「異なる意味を持つ決まった数のもの」、リストは「同じ意味を持つ可変の数のもの」**です。ここでは 2 つの意味が違うものを返すのでタプルです。Clojure 版がベクタ 2 つのベクタを返していたのに対し、Elixir では型が分かれます。

**所有権の話が出てこない**のがここでの対比です。Rust 版では「読むだけなら `&[Person]` で借りる」「`clone()` をどこでするか」が設計の一部でしたが、Elixir のデータは不変なので、渡しても共有されるだけで複製は起きません。`Map.take/2` が返すのは新しいマップですが、元のマップは何も変わりません。**不変であることが、複製の判断そのものを消しています。** F# 版・Clojure 版と共通する性質です。

BEAM ではこれがもう一段効いてきます。プロセス間でデータを送るときには複製されますが（プロセスごとにヒープが分かれているため）、1 つのプロセスの中では共有されます。不変だからこそ、そうした最適化を処理系に任せられます。

## 1.10 正解率を計算する

```elixir
@doc "予測が正解ラベルと一致した割合を返す。件数が違えば例外を投げる。"
def accuracy(predictions, labels) do
  if length(predictions) != length(labels) do
    raise ArgumentError,
          "予測と正解ラベルの件数が違います: #{length(predictions)} と #{length(labels)}"
  end

  hits = predictions |> Enum.zip(labels) |> Enum.count(fn {p, t} -> p == t end)
  hits / length(labels)
end
```

最後の 2 行を読みます。

1. `Enum.zip(predictions, labels)` が `[{予測, 正解}, ...]` というタプルのリストを作ります
2. `Enum.count(fn {p, t} -> p == t end)` が、条件に合うものを数えます。**無名関数の引数の位置でタプルを分解している**ところに注目してください。`fn pair -> elem(pair, 0) == elem(pair, 1) end` と書かずに済みます。引数はパターンなので、関数の入り口で分解できます
3. `hits / length(labels)` で割ります

**`/` は必ず浮動小数点を返します。** `4 / 2` は `2` ではなく `2.0` です。整数の割り算をしたいときは `div/2` を使います。Clojure 版が `(double ...)` を忘れると有理数（`14/19`）になったのとは逆で、Elixir では明示的な変換が要りません。Java・Go の整数除算（切り捨て）、Python 3 の `/`（浮動小数点）とも並べておきます。

| 言語版 | `14 / 19` の結果 |
|--------|----------------|
| Java・Go・Rust（整数どうし） | `0`（切り捨て） |
| Clojure | `14/19`（有理数） |
| Python 3 | `0.7368421052631579` |
| **Elixir** | **`0.7368421052631579`（`/` は常に浮動小数点）** |

件数の検査だけは `if` で書いています。パターンマッチで書くこともできますが、「長さが違う」という条件は引数の形ではなく計算した値の比較なので、素直に `if` にしました。**パターンマッチで書けることと、書くべきことは別です。**

テストはこうです。

```elixir
test "全部当たれば正解率は一になる" do
  assert C.accuracy([C.kinoko(), C.takenoko()], [C.kinoko(), C.takenoko()]) == 1.0
end

test "半分当たれば正解率は零点五になる" do
  assert C.accuracy([C.kinoko(), C.kinoko()], [C.kinoko(), C.takenoko()]) == 0.5
end
```

`==` で厳密に比べています。1.0 と 0.5 は浮動小数点で正確に表せる値なので、これで安全です。

Elixir には `==` と `===` の 2 つがあり、`1 == 1.0` は `true`、`1 === 1.0` は `false` です（後者は型もそろっていることを求めます）。Clojure 版の `=` と `==` がちょうど裏返しの関係になっているので、言語をまたぐと取り違えやすいところです。

## 1.11 実データで正解率を表示する

### データが無ければテストを外す

実データを使うテストには `@tag :data` を付けます。

```elixir
@tag :data
test "実データでルールによる判定の正解率を求める" do
  people = C.load_people(Path.join(GettingStartedMl.Dataset.dir(), "KvsT.csv"))
  {x, t} = C.split_features_and_labels(people)
  assert length(people) == 19
  assert_in_delta C.accuracy(Enum.map(x, &C.predict_by_rule/1), t), 0.7368, 0.0001
end
```

`@tag` はその直後のテストに印を付けるモジュール属性です。そして `test/test_helper.exs` で、データの有無を見て除外するタグを決めます。

```elixir
# 実データ（書籍の購入者だけが使える）が無い環境では、:data のテストを外す。
exclude = if File.dir?(GettingStartedMl.Dataset.dir()), do: [], else: [:data]

unless exclude == [] do
  IO.puts("学習データが見つからないので :data のテストを外します（#{GettingStartedMl.Dataset.dir()}）")
end

ExUnit.start(exclude: exclude)
```

`test_helper.exs` は**テストを走らせる前に一度だけ評価されるスクリプト**です。拡張子が `.exs`（script）で、コンパイルされずにその場で実行されます。だからここに条件分岐を書けます。`ExUnit.start/1` に `exclude:` を渡すと、そのタグの付いたテストが最初から除外されます。

データがあるとき。

```text
$ mix test --cover
Running ExUnit with seed: 428334, max_cases: 16

..........
Finished in 0.08 seconds (0.08s async, 0.00s sync)
10 tests, 0 failures
```

データが無いとき。

```text
$ ML_DATA_DIR=/nonexistent mix test --cover
学習データが見つからないので :data のテストを外します（/nonexistent）
Running ExUnit with seed: 511308, max_cases: 16
Excluding tags: [:data]

.........
Finished in 0.07 seconds (0.07s async, 0.00s sync)
10 tests, 0 failures, 1 excluded
```

**「10 tests, 0 failures, 1 excluded」と、外した数がそのまま出ます。** ドットも 10 個から 9 個に減ります。ほかの言語版と並べておきます。

| 言語版 | スキップの仕組み | 結果の見え方 |
|--------|----------------|------------|
| **Elixir（ExUnit）** | **`@tag` と `ExUnit.start(exclude: ...)`** | **「1 excluded」と数が出る** |
| Ruby（Minitest） | `skip` | スキップとして記録される |
| Go | `t.Skip` | スキップとして記録される |
| Clojure（`clojure.test`） | 無い。早期に戻る | 成功として数えられる（表明の数が減る） |
| Rust | 無い。早期に戻る | 成功として数えられる |

Ruby 版の `skip` との違いは、**判断する場所**です。`skip` はテストの中で「入ってから引き返す」のに対し、`@tag` と `exclude` は「**そもそも呼ばない**」という形です。Clojure 版の早期リターンは「入ってから引き返す」側で、しかも記録が残りませんでした。Elixir はいちばん扱いやすい側にいます。

`assert_in_delta 実際の値, 期待値, 許容誤差` は浮動小数点の比較です。正解率は割り切れない値なので、`0.7368` との差が `0.0001` 以下であることを確かめています。試しにルールを壊すと、こう出ます。

```text
  1) test 実データでルールによる判定の正解率を求める (GettingStartedMl.Chapter01Test)
     test/getting_started_ml/chapter01_test.exs:82
     Expected the difference between 0.42105263157894735 and 0.7368 (0.31574736842105267) to be less than or equal to 0.0001
```

差そのものを出してくれるので、どれだけ外したかがすぐ分かります。

### 結果を表示する

```elixir
@doc "実データでルールによる判定の正解率を表示する。"
def run do
  people = load_people(Path.join(Dataset.dir(), "KvsT.csv"))
  {x, t} = split_features_and_labels(people)
  predictions = Enum.map(x, &predict_by_rule/1)

  IO.puts("データ件数: #{length(people)}")
  IO.puts("ルールによる判定の正解率: #{:io_lib.format(~c"~.4f", [accuracy(predictions, t)])}")
end
```

最後の行に Elixir らしいものが 2 つあります。

**`:io_lib.format/2` は Erlang の関数です。** 先頭のコロンから始まる `:io_lib` が Erlang のモジュールで、Elixir から**そのまま呼べます**。`~.4f` が「小数点以下 4 桁」を表す Erlang の書式で、C の `%.4f` にあたります。Elixir には `:erlang.float_to_binary/2` や `Float.round/2` もありますが、桁をそろえて出すなら Erlang の書式がいちばん素直です。Clojure が `String/format` で JDK をそのまま使ったのと同じ構図で、**土台の言語の標準ライブラリが全部使える**のが BEAM の言語の強みです。

**`~c"~.4f"` の `~c` は文字リスト（charlist）のリテラルです。** Erlang の関数は文字列を「文字コードのリスト」で受け取るので、Elixir の文字列（バイナリ）をそのまま渡せません。`~c"..."` がその変換を書く記法です。Elixir 1.15 より前は `'...'`（単引用符）と書けましたが、今は `~c` が推奨です。**Erlang と Elixir で文字列の表し方が違う**ことが、境界をまたぐたびに顔を出します。

実行します。

```text
$ mix run -e "GettingStartedMl.Chapter01.run()"
データ件数: 19
ルールによる判定の正解率: 0.7368
```

`mix run -e "式"` は、プロジェクトをコンパイルしてから式を 1 つ評価するコマンドです。Clojure 版が `-main` と別名を用意したのに対し、Elixir では**モジュールと関数を直接指定できる**ので、章を選ぶための入り口を作っていません。第 15 章で API を立てるときには、`mix run --no-halt` や `mix release` を使うことになります。

**正解率 0.7368** は、Python 版・Kotlin 版・TypeScript 版・F# 版・Java 版・C# 版・Scala 版・Go 版・Rust 版・Ruby 版・Clojure 版と同じ値です。19 人中 14 人を正しく判定できました。乱数を使わない処理なので、言語が違っても値は一致します。

## 1.12 リファクタリング

### 整形

```text
$ mix format --check-formatted
```

何も言わなければ合格です。設定は `.formatter.exs` に置きます。

```elixir
[
  inputs: ["{mix,.formatter}.exs", "{config,lib,test}/**/*.{ex,exs}"]
]
```

`inputs:` に対象のファイルを書くだけで、**整形の規則そのものは設定できません。** 行の長さ（既定 98 文字）くらいしか変えるところがなく、括弧を付けるか付けないか、どこで改行するかはすべて決め打ちです。gofmt・rustfmt と同じ「議論をさせない」流儀で、cljfmt が字下げだけを見ていたのとは踏み込みの深さが違います。

わざと崩すと、差分を出してくれます。

```text
$ mix format --check-formatted
       |
29 29  |
30 30  |  @doc "人間が決めたルールで派閥を判定する。"
31    -|  def predict_by_rule(   %{年代: @kinoko_age_group}   ), do:   @kinoko
   31 +|  def predict_by_rule(%{年代: @kinoko_age_group}), do: @kinoko
32 32  |  def predict_by_rule(%{年代: _}), do: @takenoko
33 33  |
```

**終了コードは 1 です。** CI で失敗させられます。

### コンパイラの警告と Credo

Elixir の検査は、**コンパイラと Credo で担当が分かれています**。

未使用の変数を残してみます。

```elixir
def predict_by_rule(%{年代: @kinoko_age_group} = features), do: @kinoko
```

```text
$ mix compile --warnings-as-errors
Compiling 5 files (.ex)
    warning: variable "features" is unused (if the variable is not meant to be used, prefix it with an underscore)
    │
 31 │   def predict_by_rule(%{年代: @kinoko_age_group} = features), do: @kinoko
    │                                                  ~~~~~~~~
    │
    └─ lib/getting_started_ml/chapter01.ex:31:50: GettingStartedMl.Chapter01.predict_by_rule/1

Compilation failed due to warnings while using the --warnings-as-errors option
```

**これはコンパイラの仕事で、Credo は何も言いません。** 終了コードは 1 です。Ruby 版の RuboCop、Clojure 版の clj-kondo が未使用の束縛を指摘したのに対し、Elixir ではコンパイラ自身が持っています。`mix.exs` に `elixirc_options: [warnings_as_errors: true]` を書いてあるので、`mix test` の中でコンパイルされるときも同じように失敗します。

`%{年代: @kinoko_age_group} = features` という書き方も説明しておきます。**`=` はパターンマッチなので、パターンの中にも書けます。** 「マップとして照合しつつ、全体を `features` にも束縛する」という意味です。Rust の `@` 束縛、Scala の `x @ Pattern` にあたります。今回は使わないので警告されました。

Credo のほうは、コンパイラが見ない「読みやすさ」「設計」を見ます。

```text
$ mix credo --strict
Checking 7 source files ...

  Software Design
┃
┃ [D] → Found a TODO tag in a comment: # TODO: 年代だけでなく身長と体重も見るようにする
┃       lib/getting_started_ml/chapter01.ex:30 #(GettingStartedMl.Chapter01.split_features_and_labels)

Analysis took 0.1 seconds (0.04s to load, 0.1s running 69 checks on 7 files)
44 mods/funs, found 1 software design suggestion.
```

ここに**大きな落とし穴があります。Credo は指摘があると終了コード 4 を返します。** 0 でも 1 でもありません。`&&` でつなぐぶんには 0 以外なら止まるので問題になりませんが、「終了コードが 1 かどうか」を見る書き方をしていると、指摘を見落とします。Credo が指摘の重さ（`[D]` は design、ほかに `[R]` readability、`[C]` consistency、`[W]` warning、`[F]` refactoring）ごとにビットを立てているためで、種類が混ざれば合計した値が返ります。

`--strict` を付けると、既定では出ない低優先度の指摘まで出ます。付けないとほとんど何も言わないので、CI では `--strict` を付けるのが実用上の既定です。

**検査の担当をまとめます。**

| 検査 | コマンド | 何を見るか | 終了コード |
|------|---------|-----------|-----------|
| 整形 | `mix format --check-formatted` | 空白・改行・括弧 | 1 |
| コンパイラ | `mix compile --warnings-as-errors` | 未使用の変数・未使用の属性・到達しない節 | 1 |
| 静的解析 | `mix credo --strict` | 読みやすさ・設計・重複 | **4** |
| テストとカバレッジ | `mix test --cover` | 振る舞いと網羅 | 1（しきい値割れは 3） |

4 つとも、わざと違反を入れて実際に落ちることを確かめてあります。**「CI に入れた検査が本当に落ちるか」は、入れた直後に一度壊して確かめるべきです。** 終了コードが 4 であることのような癖は、壊してみるまで分かりません。

### カバレッジの落とし穴 2 つ

```text
$ mix test --cover
Generating cover results ...

Percentage | Module
-----------|--------------------------
    60.00% | GettingStartedMl.Dataset
    76.92% | GettingStartedMl.Chapter01
   100.00% | GettingStartedMl.Csv
-----------|--------------------------
    77.78% | Total
```

`mix test --cover` は標準の機能で、Erlang の `cover` を使っています。追加のライブラリは要りません。

ここに至るまでに 2 つ踏みました。**この章でいちばん時間を取られたのがここです。**

**1 つめ: 生成されたモジュールが混ざる。**

`test_coverage: [ignore_modules: ...]` を書かずに走らせると、こうなります。

```text
Percentage | Module
-----------|--------------------------
    30.88% | GettingStartedMl.Csv.Parser
    60.00% | GettingStartedMl.Dataset
    76.92% | GettingStartedMl.Chapter01
   100.00% | GettingStartedMl.Csv
-----------|--------------------------
    47.12% | Total
```

**総計が 77.78% から 47.12% に落ちます。** 犯人は `GettingStartedMl.Csv.Parser`——`NimbleCSV.define/2` が生成したモジュールです。区切り文字や引用符のあらゆる組み合わせを扱う大きなコードが生成され、そのうち 30.88% しか通っていません。こちらが書いた覚えのないコードなのに、こちらのカバレッジとして数えられます。

コンパイル時にコードを生成するライブラリを使うと、こうなります。マクロで書けることの裏返しで、**「自分のコード」と「生成されたコード」の境目が測定の道具から見えない**わけです。`mix.exs` で明示的に外します。

```elixir
test_coverage: [ignore_modules: [GettingStartedMl.Csv.Parser], summary: [threshold: 70]]
```

**2 つめ: しきい値の置き場所を間違えても、黙って無視される。**

`mix test --cover` には**既定で 90% のしきい値**があり、下回ると失敗します。この章の 77.78% では通らないので、70% に下げます。ところが `test_coverage: [threshold: 70]` と書いても効きません。

```text
$ mix test --cover
    47.12% | Total

Coverage test failed, threshold not met:

    Coverage:   47.12%
    Threshold:  90.00%
```

**`Threshold: 90.00%` のままです。** 指定した 70 はどこにも出てきません。正しい置き場所は `summary:` の下です。

```elixir
test_coverage: [summary: [threshold: 70]]
```

しきい値は「集計の要約を出す機能」の設定なので、`summary:` の中にあります。`test_coverage:` は単なるキーワードリストなので、**知らないキーを書いても誰も文句を言いません。** 設定したつもりで既定値のまま動き、しかも失敗し続けるので、原因を `ignore_modules` のほうだと思い込んで時間を溶かしました。

**キーワードリストの設定は、書き間違えても黙って無視される**——これは Elixir の設定全般に言えることです。構造体なら未知のキーはコンパイルエラーになりますが、キーワードリストは素通りします。設定を書いたら、**「効いていない場合にどう見えるか」を一度確かめる**のが確実です。ここでは「しきい値割れの失敗メッセージが `Threshold:` を表示してくれる」ことに救われました。

ちなみに、しきい値割れの終了コードは 3 です。ここでも 1 ではありません。

`GettingStartedMl.Dataset` が 60% なのは、`dir/1` の `nil` と `""` の枝を単体テストで踏んでいないからです。`GettingStartedMl.Chapter01` の 76.92% は、`run/0` を直接テストしていないぶんです。

もう 1 つ、Clojure 版と同じ現象が起きます。学習データを外しても、カバレッジが変わりません。

```text
$ ML_DATA_DIR=/nonexistent mix test --cover
学習データが見つからないので :data のテストを外します（/nonexistent）
10 tests, 0 failures, 1 excluded

    77.78% | Total
```

**77.78% と、データがあるときと同じです。** Rust 版では実データのテストがスキップされるとカバレッジが大きく落ちましたが、Elixir 版では落ちません。実データのテストが通る経路（`load_people`・`split_features_and_labels`・`predict_by_rule`・`accuracy`）を、一時ファイルを使う単体テストがすべて覆っているからです。

つまり**カバレッジはスキップの検出に使えるとは限りません**。Elixir 版では「1 excluded」という表示がその役目を果たすので困りませんが、指標が何を写しているかは実際に動かして確かめるしかない、という話です。

### まとめて検査する

リポジトリのルートで次を実行すると、CI と同じ順に検査できます。

```bash
npx gulp apps:check:elixir
```

中身は `mix format --check-formatted && mix compile --warnings-as-errors && mix credo --strict && mix test --cover` です。同じ順序を `.github/workflows/elixir-ci.yml` にも書いています。

CI では依存とビルドをキャッシュします。

```yaml
- name: Cache deps and build
  uses: actions/cache@v4
  with:
    path: |
      apps/elixir/deps
      apps/elixir/_build
    key: ${{ runner.os }}-elixir-${{ hashFiles('apps/elixir/mix.lock') }}
```

`mix.lock` のハッシュを鍵にします。`deps/` だけでなく `_build/` も入れているのは、**Elixir のコンパイルが遅い**からです。依存を取り直すより、コンパイル済みの `.beam` を持ち越すほうが効きます。

もう 1 つ、CI には最初につまずく点があります。

```bash
mix local.hex --force && mix local.rebar --force && mix deps.get
```

`mix deps.get` の前に **Hex（パッケージ管理）と rebar（Erlang のビルド道具）を入れる**必要があります。まっさらな環境では「Hex を入れますか」と対話で聞かれてしまい、CI が止まります。`--force` を付けて黙って入れさせるのが定石です。

## 1.13 まとめ

この章では、ルールによる判定と正解率を TDD で実装し、実データで 0.7368 という値を得ました。Elixir に固有の論点は次のとおりです。

1. **分岐は関数節とパターンマッチで書く** — `if` を書かずに `predict_by_rule(%{年代: @kinoko_age_group})` と `predict_by_rule(%{年代: _})` の 2 つの節を並べる。パターンを狭く書いておくと、`:年代` を持たないデータは `FunctionClauseError` で落ちてくれる。型を宣言しない言語での防御になる
2. **表は素のマップとリストで表す** — 型を宣言せず、列名のアトムで引く。アトムに日本語を使えるが、回収されないので外から来た文字列を無制限にアトムにしない。マップもリストも中身で比較されるので、テストで `==` がそのまま使える
3. **`|>` でデータを流す** — 標準ライブラリが「データを第 1 引数にとる」規約で統一されているので、`|>` 1 つで足りる。Clojure が `->` と `->>` を使い分けるところを、Elixir は規約のほうを揃えた
4. **失敗は `raise` で表す** — `{:ok, _}`／`{:error, _}` を返す流儀もあるが、回復できない失敗はその場で落とす。どの関数が何を投げるかは `@doc` とテストでしか表せない
5. **ライブラリの親切に頼らない選択もある** — NimbleCSV は BOM を取り除かない。`NimbleCSV.define/2` がコンパイル時にパーサーのモジュールを生成する設計なので速い代わりに、そのモジュールがカバレッジに混ざる
6. **タグでテストを外せる** — `@tag :data` と `ExUnit.start(exclude: ...)` で、学習データが無い環境では「1 excluded」と数が出る。Clojure 版・Rust 版の早期リターンより扱いやすい
7. **検査はコンパイラと Credo で担当が分かれる** — 未使用の変数はコンパイラ、読みやすさと設計は Credo。**Credo の終了コードは 4**、カバレッジのしきい値割れは 3。終了コードの癖は壊してみるまで分からない
8. **設定の書き間違いは黙って無視される** — `test_coverage: [threshold: 70]` は効かず、`test_coverage: [summary: [threshold: 70]]` が正しい。キーワードリストは未知のキーを素通りさせる

**TODO リスト（この章の完了時点）**:

- [x] CSV を読み込む
- [x] 特徴量と正解ラベルに分ける
- [x] ルールで派閥を判定する
- [x] 正解率を計算する
- [x] 実データで正解率を表示する

次の章では、アヤメのデータを読み込み、欠損値を補完して、訓練データとテストデータに分けます。ここで Elixir 版はシリーズで初めて**乱数生成器そのものを自作します**。`Nx.Random`（Threefry）も Erlang の `:rand` も、ほかの言語版と並びが合わないためです。`java.util.Random` と同じ 48 ビットの線形合同法を 3 行の漸化式として書き、`nextInt(bound)` の棄却と Fisher-Yates まで実装すると、**分割の結果が Java 版・Scala 版・Clojure 版と一致します**。ライブラリが無いから自作するのではなく、ほかの言語版と数値を突き合わせるために自作する——という、この版でいちばん面白い選択です。

Elixir 版のほかの章は [Elixir 版のトップ](index.md) から辿れます。
