---
type: Article
title: "第 15 章: 機械学習 API とモジュール設計"
description: "第 7・8 章の学習済みモデルを Plug と Bandit で予測 API として公開し、モジュールで層を分ける。call/2 が「Conn を受け取って Conn を返す関数」であることを使ってサーバーを起動しない統合テストを書き、置き場の約束を behaviour と約束のテストの両方で守る。"
tags: [article,getting-start-ml,elixir]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:00:00Z }
---

# 第 15 章: 機械学習 API とモジュール設計

## 15.1 はじめに

シリーズの最後の章です。ここまでに作ったモデルは、テストと `mix run -e` の中でしか動きませんでした。この章では、第 7 章の線形回帰（映画の興行収入）と第 8 章のパイプライン（乗客の生存）を **HTTP の予測 API** として公開します。

題材は [Java 版の第 15 章](../java/15-machine-learning-api-and-module-design.md)・[Clojure 版](../clojure/15-machine-learning-api-and-module-design.md) と同じで、3 つのエンドポイントを作ります。

| メソッド | パス | 役割 |
|---------|------|------|
| `GET` | `/health` | モデルを読み込めるかどうかを返す |
| `POST` | `/cinema/sales` | 映画の特徴量から興行収入を予測する |
| `POST` | `/survived` | 乗客の特徴量から生存を予測する |

Elixir 版では [Plug](https://hexdocs.pm/plug/) 1.20 と [Bandit](https://github.com/mtrudel/bandit) 1.12、JSON は [Jason](https://github.com/michalmuskala/jason) 1.4 を使います（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。**Phoenix は使いません。** また、経路の振り分けのライブラリ（`Plug.Router`）も使いません。この章で見せたいのは、Plug の約束そのものだからです。

> Plug のハンドラーは、`init/1` と `call/2` を持つモジュールである。`call/2` は `%Plug.Conn{}` を受け取って `%Plug.Conn{}` を返す **ただの関数** である。

継承も注釈も DSL もありません。関数なので、テストはその関数を呼ぶだけで済み、サーバーを起動する必要がありません。Java 版が Javalin の仕組み、Ruby 版が rack-test を使った部分が、Elixir では `Plug.Test` の `conn/3`（要求を組み立てるだけの関数）で済みます。

この章で Elixir らしいのは次の 4 点です。

1. **`call/2` は関数、経路は関数節のパターンマッチ** — `def call(%Plug.Conn{method: "GET", request_path: "/health"} = conn, store)` と書けば、ルーターを入れずに経路が分かれる
2. **置き場の約束は behaviour と約束のテストの二段構え** — behaviour は「関数の名前と引数の数」をコンパイル時に守る。「無ければ例外を投げる」は書けないので、本物と偽物の両方に走らせる約束のテストで守る
3. **置き場は `{モジュール, 状態}` の組** — behaviour は値ではなくモジュールに対する約束なので、Clojure の `defprotocol`／`reify` のように「値そのものが約束を満たす」形にはならない。Plug 自身が `{モジュール, 初期化した値}` を持ち回るのと同じ形にする
4. **保存は第 8 章の `:erlang.term_to_binary` のまま** — 第 7 章のモデルも第 8 章のパイプラインもマップとリストなので、既存の章に手を入れずに保存でき、**往復しても浮動小数点数が 1 ビットも変わりません**

## 15.2 層を分ける

### モジュールと依存の向き

`lib/getting_started_ml/chapter15/` の中を、モジュールで層に分けます。

```text
domain.ex        ドメイン層             モデルの名前・例外・既存モデルのアダプター
model_store.ex   ドメイン層             置き場の約束（behaviour）
service.ex       アプリケーション層     置き場からモデルを読んで予測する
file_store.ex    インフラ層             ファイルへの保存と読み込み
validation.ex    プレゼンテーション層   要求の JSON の読み取りと検証
api.ex           プレゼンテーション層   Plug のハンドラー・ステータスコードへの変換
chapter15.ex     組み立て               学習・保存・サーバーの起動
```

依存の向きは Java 版・Clojure 版と同じく内側（ドメイン）へ向けます。

```text
api.ex ──→ service.ex ──→ model_store.ex ←── file_store.ex
                                ↑
                （ModelStore の約束だけを知る）
```

ただし **Elixir はこの向きを強制しません**。Clojure の `ns` は循環を `Cyclic load dependency` で弾きましたが、Elixir のモジュールは相互に呼び合っても構いません（コンパイル時に本当に必要になったときだけ依存が張られます）。層の向きは規約であって、処理系の保証ではない、というのが Clojure 版との違いです。

### 置き場の約束をどう表すか

Java 版は `interface ModelStore`、Clojure 版は `defprotocol` でした。Elixir で「約束」を表す道具は 3 つあります。

| 書き方 | 長所 | 短所 |
|-------|------|------|
| 関数を引数で渡す | 何も宣言しない。いちばん軽い | 2 つ以上の操作をまとめると引数が増える |
| behaviour（`@callback`） | 名前が付く。実装が足りなければコンパイル時に警告が出る | 約束の相手は **モジュール** なので、値に状態を持たせるには組が要る |
| protocol（`defprotocol`） | 値の型で振り分けられる | 置き場ごとに構造体を定義することになる |

ここは **behaviour** にしました。操作が 2 つあり、「置き場」という名前に意味があり、テストで偽物を差し替えたいからです。

```elixir
@typedoc "置き場。behaviour を実装したモジュールと、その状態の組。"
@type t :: {module(), term()}

@doc "映画の特徴量から興行収入を返す関数を読み込む。無ければ ModelNotFoundError を投げる。"
@callback load_sales_model(state :: term()) :: (map() -> number())

@doc "乗客の特徴量から生存するかどうかを返す関数を読み込む。無ければ ModelNotFoundError を投げる。"
@callback load_survival_model(state :: term()) :: (map() -> boolean())
```

behaviour が約束するのはモジュールであって値ではないので、置き場そのものは **`{モジュール, 状態}` の組** で表し、この組を受け取る関数で振り分けます。

```elixir
@doc "置き場から、興行収入のモデルを読み込む。"
def load_sales_model({module, state}), do: module.load_sales_model(state)

@doc "置き場から、生存予測のモデルを読み込む。"
def load_survival_model({module, state}), do: module.load_survival_model(state)
```

この形は Elixir では見慣れたものです。Plug 自身が `{Plug のモジュール, 初期化した値}` を持ち回っていて、`plug: {Api, store}` と書くのと同じ形になっています。

一方、**モデルそのものには behaviour を作りませんでした**。Java 版の `SalesModel`・`SurvivalModel` は `@FunctionalInterface` で、実質「関数 1 つ」です。Elixir の無名関数はそのまま値なので、包むモジュールを置かずに `fn movie -> 数値 end` を返します。

この使い分けの基準は「操作がいくつあるか」です。1 つなら関数、複数で名前が要るなら behaviour。

### behaviour が保証しないこと

`@callback` が決めるのは **関数の名前と引数の数** だけです。次の取り決めは書けません。

- 読み込めなければ `ModelNotFoundError` を投げる（`nil` を返すのではない）
- 返る関数は、映画のマップを受け取って数値を返す

`@callback` には `(map() -> number())` と戻り値の型を書いてありますが、**Dialyzer を走らせない本シリーズでは、これはコメントと同じ重みしかありません**（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。効くのは「`@behaviour` を書いたモジュールに実装が足りなければコンパイル時に警告が出る」ところだけです。そこで、約束そのものをテストにします（15.5 節）。

## 15.3 TODO リストの作成

**TODO リスト**:

- [ ] 要求の JSON を読み、型が合わなければ弾く
- [ ] 必須・0 以上・選択肢の検証をし、理由を並べて返す
- [ ] 映画・乗客の特徴量をマップで表す
- [ ] 置き場の約束を behaviour と約束のテストで表す
- [ ] 第 7 章の線形回帰と第 8 章のパイプラインをファイルに保存・読み込みする
- [ ] ファイルが無ければ `ModelNotFoundError` を投げる
- [ ] サービスが置き場からモデルを読んで予測し、ヘルスチェックを返す
- [ ] `POST /cinema/sales`・`POST /survived` が予測を JSON で返す
- [ ] 入力が不正なら 422、モデルが無ければ 503、それ以外は 500
- [ ] 知らないパスは 404、許していないメソッドは 405
- [ ] 第 7・8 章と同じ条件で学習し、API を起動する（`mix run --no-halt -e GettingStartedMl.Chapter15.run`）

## 15.4 要求を読んで検証する

### Jason は型を決めない

Java 版は要求を `record MovieRequest(Double sns1, …)` で受け、Jackson が読み込みと同時に型を確かめました。`{"sns1": "たくさん"}` はレコードにならず、そこで 422 にできます。

`Jason.decode/1` は、JSON の値をそのまま Elixir のマップ・数値・文字列にします。`"たくさん"` も文字列として読めてしまうので、**型の確認を自分で書きます**。列ごとの型を表にしました。

```elixir
@movie_types %{"sns1" => :number, "sns2" => :number, "actor" => :number, "original" => :integer}

@passenger_types %{
  "pclass" => :integer,
  "sex" => :string,
  "age" => :number,
  "sib_sp" => :integer,
  "parch" => :integer,
  "fare" => :number,
  "embarked" => :string
}
```

型をアトムで表し、確認は **関数節のパターンマッチ** で分けます。Clojure 版が `case` で分けたところが、Elixir では節の並びになります。

```elixir
# 値が型に合うかどうか。null と、表に無い列（型が nil）は問わない。
defp typed?(_type, nil), do: true
defp typed?(nil, _value), do: true
defp typed?(:number, value), do: is_number(value)
defp typed?(:integer, value), do: is_integer(value)
defp typed?(:string, value), do: is_binary(value)
```

節の順に意味があります。1 行目の `nil` は「値が `null`」、2 行目の `nil` は「表に無い列」で、**同じ `nil` が引数の位置によって別のことを表しています**。上から順に照合されるので、`typed?(:number, nil)` は 1 行目で真になります。パターンで書くと短くなる代わりに、順に依存するところが読み手の負担になるので、コメントで補いました。

読み取り本体は `with` で書きます。

```elixir
def read_json(body, types) do
  with {:ok, request} <- Jason.decode(body),
       true <- is_map(request),
       true <- Enum.all?(request, fn {field, value} -> typed?(Map.get(types, field), value) end) do
    {:ok, request}
  else
    _other -> :error
  end
end
```

`with` は「左のパターンに合えば次へ、合わなければ `else` へ」という式です。3 つの条件（JSON として読める・オブジェクトである・型が合う）が縦に並び、どれが崩れても `:error` になります。最初は `case` の中に `if` を入れて書いたのですが、Credo の `--strict` に「ネストが深すぎる（最大 2、実際は 3）」と指摘されて `with` に直しました。

```text
┃ [F] → Function body is nested too deep (max depth is 2, was 3).
┃       lib/getting_started_ml/chapter15/validation.ex:40:31 #(GettingStartedMl.Chapter15.Validation.read_json)
```

`original` を `:integer` にしたのは、`1.5` を 422 にするためです。Elixir の `is_integer/1` は `1` に真、`1.5` に偽を返すので、Java 版の `Integer` と同じ扱いになります。

戻り値を Clojure 版の「マップか `nil`」ではなく **`{:ok, マップ}` か `:error`** にしたのは、`with` で受けるためです。Elixir では「成功と失敗を組とアトムで表す」のが普通で、`nil` を返すと呼び出し側が `if` を書くことになります。

```elixir
test "JSON として読めないか型が合わなければ :error を返す" do
  for body <- [~s({"sns1": "たくさん"}), ~s({"original": 1.5}), "{ここは JSON ではない", "", "[1, 2]"] do
    assert Validation.read_json(body, Validation.movie_types()) == :error, body
  end
end
```

`""`（空の本文）と `[1, 2]`（JSON としては正しい配列）も弾きます。`Jason.decode("")` は `{:error, %Jason.DecodeError{}}` を返し、`[1, 2]` は `is_map/1` で落ちます。`~s(...)` は「二重引用符を含む文字列」を書くためのシギルで、JSON をテストに書くときに `\"` の連続を避けられます。

### 理由を集めて、無ければ値を作る

検証の規則は Java 版の `Checks` と同じ形です。規則は「理由の文字列か、問題なしの `nil`」を返します。

```elixir
@doc "必須の列が空なら理由を返す。"
def required(field, nil), do: "#{field} は必須です"
def required(_field, _value), do: nil

@doc "負の数なら理由を返す。"
def not_negative(field, value) when is_number(value) and value < 0,
  do: "#{field} は 0 以上にしてください"

def not_negative(_field, _value), do: nil
```

Clojure 版が `when` マクロで「条件が偽なら `nil`」を書いたところが、Elixir では **ガード付きの関数節と、受け皿の節** になります。`not_negative` の 1 つめの節は `is_number(value) and value < 0` のときだけ選ばれ、それ以外は 2 つめの節で `nil` になります。`nil` や文字列が来ても落ちません。

結果はマップで表します。

```elixir
def validate(reasons, build) do
  case Enum.reject(reasons, &is_nil/1) do
    [] -> %{value: build.(), errors: []}
    errors -> %{value: nil, errors: errors}
  end
end
```

Java 版は `sealed interface Validated<T>` に `Valid` と `Invalid` の 2 つのレコードを置き、`switch` の枝で値を取り出せないことをコンパイラが保証しました。Elixir には代数的データ型が無いので、Ruby 版・Clojure 版と同じく **値と理由の一覧を両方持つマップ** にして、`valid?/1` で見分けます。不正な要求の `value` が `nil` であることは、テストで固定するしかありません。

```elixir
test "不正な要求には値が無い" do
  assert Validation.movie(%{}).value == nil
end
```

`build` を関数で受け取るのは、**理由があるときに値を作らせないため** です。`build.()` は理由が 1 つも無い枝でしか呼ばれないので、`sns1 / 1` が `nil` に対して走ることはありません。Java 版の `Supplier<T>` と同じ狙いで、Elixir では `fn -> ... end` を渡すだけです。

```elixir
def movie(request) do
  %{"sns1" => sns1, "sns2" => sns2, "actor" => actor, "original" => original} =
    Map.merge(%{"sns1" => nil, "sns2" => nil, "actor" => nil, "original" => nil}, request)

  validate(
    [
      required("sns1", sns1),
      required("sns2", sns2),
      required("actor", actor),
      required("original", original),
      not_negative("sns1", sns1),
      not_negative("sns2", sns2),
      not_negative("actor", actor),
      one_of("original", original, @original_values)
    ],
    fn ->
      %{
        sns1: sns1 / 1,
        sns2: sns2 / 1,
        actor: actor / 1,
        original: original
      }
    end
  )
end
```

要点は **`Map.merge` で既定値を埋めてから分配束縛している** ところです。Elixir のマップのパターンマッチは「無いキーがあれば `MatchError`」なので、`%{"sns1" => sns1} = request` は列が足りない要求で落ちてしまいます。足りない列を `nil` で埋めてから照合すると、`required/2` が理由を返す形に載ります。Clojure 版の `{:strs [sns1 …]}` が「無ければ `nil`」だったところで、1 行の手当てが要りました。

`sns1 / 1` は整数を浮動小数点数にする書き方です。Elixir の `/` は必ず浮動小数点数を返すので、JSON から `100`（整数）で来ても `100.0` になります。`Kernel.to_float/1`（1 引数の変換関数）は無いので、ここは割り算を使うのが定石です。

JSON の名前（`sib_sp`）とドメインの名前（`:sib_sp`）は、Elixir ではどちらもアンダースコアなので読み替えが要りません。Clojure 版が `sib_sp` と `:sib-sp` の違いを境界で吸収した手間が、ここでは消えています。

## 15.5 置き場の約束をテストで書く

### 約束のテストを本物と偽物の両方に走らせる

behaviour が保証しない取り決めを、テストとして書きます。Ruby 版が「テストのモジュール」にしたところを、Elixir では **`ExUnit.Assertions` を `import` したただのモジュール** にします。

```elixir
@doc "置き場の約束を確かめる。with はモデルを 2 つ読み込める置き場、without はどちらも無い置き場。"
def check({module, _state} = with_models, without_models) do
  # 約束: behaviour の関数がそろっている（Clojure 版の satisfies? に当たる）
  assert function_exported?(module, :load_sales_model, 1)
  assert function_exported?(module, :load_survival_model, 1)

  # 約束: モデルがあれば予測する関数を返す
  assert is_number(ModelStore.load_sales_model(with_models).(@movie))
  assert ModelStore.load_survival_model(with_models).(@passenger) in [true, false]

  # 約束: モデルが無ければ ModelNotFoundError を投げる
  for {load, model} <- [
        {&ModelStore.load_sales_model/1, Domain.sales_model()},
        {&ModelStore.load_survival_model/1, Domain.survival_model()}
      ] do
    error = assert_raise ModelNotFoundError, fn -> load.(without_models) end
    assert error.model == model
    assert Exception.message(error) == "学習済みモデル #{model} が見つかりません"
  end
end
```

`ExUnit.Assertions` の `assert` は `test` ブロックの外でも動きます。失敗したときに例外を投げるだけなので、**約束を関数にまとめて、複数の `test` から呼べます**。Minitest のモジュールの `include` や JUnit の `@Nested` のような仕組みは要りません。

本物の置き場のテストから呼び、

```elixir
test "ファイルの置き場は置き場の約束を満たす" do
  StoreContract.check(store_with_models(), store_without_models())
end
```

偽物のテストからも呼びます。

```elixir
test "偽物の置き場も置き場の約束を満たす" do
  StoreContract.check(FakeStore.new(@ready), FakeStore.new(%{sales: false, survival: false}))
end
```

これで、偽物が例外の代わりに `nil` を返すように変わると、約束のテストが落ちます。

`assert_raise/2` は **投げられた例外の構造体を返します**。そのまま `error.model` を見られるので、「どのモデルが無いと言っているか」まで確かめられました。Clojure 版が `try`／`catch` で例外を掴んでから `ex-data` を見た 3 行が、1 行になっています。

`function_exported?(module, :load_sales_model, 1)` は、Clojure 版の `(satisfies? domain/ModelStore with)` にあたる行です。ただし **「behaviour を実装しているか」ではなく「その名前の関数があるか」しか見ていません**。Elixir には `@behaviour` を実行時に問い合わせる標準の関数が無く（モジュール属性 `behaviour_info` を自分で読むことになります）、そこまでするより「呼べること」を確かめるほうが約束に近いと考えました。

### 偽物はモジュールで書く

```elixir
defmodule GettingStartedMl.Chapter15Test.FakeStore do
  @moduledoc "第 15 章のテストで使う偽物の置き場。実データも学習も使わずに API とサービスを確かめる。"

  alias GettingStartedMl.Chapter15.{Domain, ModelNotFoundError}

  @behaviour GettingStartedMl.Chapter15.ModelStore

  @fixed_sales 4321.5

  @doc "偽物の置き場が返す興行収入。"
  def fixed_sales, do: @fixed_sales

  @doc "モデルがあるかどうかを差し替えられる置き場を作る。"
  def new(options), do: {__MODULE__, options}

  @impl true
  def load_sales_model(%{sales: true}), do: fn _movie -> @fixed_sales end
  def load_sales_model(_options), do: raise(ModelNotFoundError, model: Domain.sales_model())

  @impl true
  def load_survival_model(%{survival: true}), do: fn _passenger -> true end

  def load_survival_model(_options),
    do: raise(ModelNotFoundError, model: Domain.survival_model())
end
```

Clojure 版は `reify` で「その場で約束を満たす無名のオブジェクト」を作れましたが、**Elixir の behaviour はモジュールに対する約束なので、偽物もモジュールになります**。テストファイルの先頭に `defmodule` を並べれば済むので手間は小さいものの、「関数の中で偽物を作る」ことはできません。差し替えたいのは「モデルがあるかどうか」だけなので、それを状態（`%{sales: true, survival: false}`）に追い出して、モジュールは 1 つにしました。

`@behaviour` と `@impl true` を書いておくと、**約束の関数を書き忘れたときと、名前を間違えたときにコンパイル時の警告が出ます**。`load_sale_model` と書けば「`@impl` があるのに behaviour に無い」と言われ、書き忘れれば「`load_sales_model/1` が未実装」と言われます。Ruby 版が「偽物のメソッド名を 1 文字間違えても実行時まで気づかない」と書いた部分は、Elixir では先に分かります。ただし **警告であってエラーではありません**。この章のプロジェクトは `elixirc_options: [warnings_as_errors: true]` を設定しているので（第 5 章）、結果としてビルドが止まります。

## 15.6 ドメインとサービス

### モデルが無いことを専用の例外で表す

本シリーズでは失敗を `raise ArgumentError` で表してきました（[Elixir 版の方針](index.md)）。ここだけは専用の例外を作ります。

```elixir
defmodule GettingStartedMl.Chapter15.ModelNotFoundError do
  @moduledoc """
  学習済みモデルが無いことを表す例外。

  この章のほかの失敗は `ArgumentError` のままだが、これだけは別の例外にする。
  API が 503 に変えるために、ほかの失敗と見分けられなければならないからである。
  メッセージにファイルのパスを含めないのは、503 の応答としてそのまま外に出るため。
  """

  defexception [:model]

  @impl true
  def message(%{model: model}), do: "学習済みモデル #{model} が見つかりません"
end
```

`defexception` は、`__exception__` を持つ構造体と `Exception` の振る舞いを一度に定義します。フィールド（`:model`）を持てるので、Clojure 版が `ex-info` にデータを載せたのと同じことが、**専用の型として** できます。

ここが Clojure 版との大きな違いです。Clojure では `ex-info` が作る例外はすべて `ExceptionInfo` という 1 つのクラスで、`catch` の節で種類を選べないため、捕まえてから `model-not-found?` で振り分ける必要がありました。Elixir の `rescue` は **例外の構造体ごとに節を分けられます**。

```elixir
rescue
  e in ModelNotFoundError -> json(conn, 503, %{detail: Exception.message(e)})
  # 例外のメッセージには内部の事情が入るので、応答には出さない
  _other -> json(conn, 500, %{detail: "予測できませんでした"})
end
```

メッセージにファイルのパスを入れないのは Java 版と同じ判断です。メッセージは 503 の応答としてそのまま外に出るので、サーバーの中の事情を漏らしません。

### 既存のモデルをアダプターで約束に合わせる

第 7・8 章のモデルには手を入れません。約束（`映画 → 数値`・`乗客 → 真偽値`）に合わせる関数を書くだけです。

```elixir
@doc "第 7 章の線形回帰のモデルを、興行収入のモデルの約束（映画 → 数値）に合わせる。"
def linear_sales_model(model) do
  fn movie ->
    Chapter07.predict_one(model, %{
      SNS1: movie.sns1,
      SNS2: movie.sns2,
      actor: movie.actor,
      original: movie.original
    })
  end
end
```

Java 版の `record LinearSalesModel(LinearModel model) implements SalesModel` に当たるものが、**関数を返す関数** になりました。第 7 章の `predict_one/2` は列名をキーにしたマップを受け取るので、API の名前（`sns1`）から CSV の列名（`:SNS1`）への読み替えを、この 1 か所で行います。

生存予測のほうは、乗客を「CSV と同じセルの文字列の行」に戻します。

```elixir
# 乗客を、第 8 章のパイプラインが読む CSV と同じセルの文字列の行にする。
# 分からない値は空欄にすると、学習のときに求めた中央値・最頻値で補完される。
defp passenger_row(passenger) do
  %{
    Pclass: to_string(passenger.pclass),
    Sex: passenger.sex,
    Age: cell(passenger.age),
    SibSp: to_string(passenger.sib_sp),
    Parch: to_string(passenger.parch),
    Fare: to_string(passenger.fare),
    Embarked: cell(passenger.embarked)
  }
end

defp cell(nil), do: ""
defp cell(value), do: to_string(value)
```

年齢と乗船港が分からないときに空文字列を入れるのが要点です。第 8 章のパイプラインは欠損値として扱い、**訓練データから求めた中央値・最頻値で補完します**。前処理をモデルと一緒に保存しておくと、予測のときも学習と同じ手順が適用されるということが、ここで効いてきます。

`cell/1` を関数節で分けたのは、`to_string(nil)` が `""` を返してしまう（`nil` は空文字列に変換される）ことに頼りたくなかったからです。「分からない値は空欄にする」という判断を、コードの上で見えるようにしました。

### サービスは HTTP を知らない

```elixir
@doc "映画の特徴量から興行収入を予測する。"
def predict_sales(store, movie), do: ModelStore.load_sales_model(store).(movie)
```

置き場からモデル（＝関数）を読み込み、すぐ呼びます。`.(...)` は無名関数を呼ぶ書き方で、「関数を返す関数」を扱っていることがこの記法から分かります。

ヘルスチェックは、読み込めるかどうかだけを返します。

```elixir
def health(store) do
  %{
    Domain.sales_model() => ready?(&ModelStore.load_sales_model/1, store),
    Domain.survival_model() => ready?(&ModelStore.load_survival_model/1, store)
  }
end

# モデルを読み込めるかどうか。読み込めない理由がほかにあれば、そのまま投げる。
defp ready?(load, store) do
  load.(store)
  true
rescue
  ModelNotFoundError -> false
end
```

`rescue` を「すべての例外」にしないのが大事なところです。ファイルの権限の問題や壊れたモデルも「モデルがありません」と表示されてしまい、原因を探せなくなります。`ModelNotFoundError` でなければそのまま投げ、API が 500 にします。

`defp ready?(...) do ... rescue ... end` は、**関数本体そのものに `rescue` を付けた形** です。`try do ... end` で包まなくてよいので、1 段浅く書けます（Credo のネストの指摘を避ける意味でも効きます）。

ヘルスチェックの並びについては、Clojure 版と事情が違います。Clojure 版は `array-map` で「`cinema`・`survived` の順」を固定し、テストで JSON の文字列そのものを比べました。**Elixir のマップは並びを持ちません。** 実測すると、文字列のキーは辞書順に並び（`["cinema", "survived"]`）、アトムのキーは辞書順ではない内部の順に並びました。

```text
atom keys: [:status, :models]
{"status":"ok","models":{"cinema":true,"survived":false}}
string keys: ["cinema", "survived"]
```

結果としては Clojure 版と同じ並びの JSON が出ていますが、**これは処理系の都合であって約束ではありません**。そこでテストは、応答の文字列ではなく `Jason.decode!` した値を比べることにしました。

```elixir
assert decoded(response) == %{
         "status" => "ok",
         "models" => %{"cinema" => true, "survived" => true}
       }
```

このサービスは状態を持ちません。予測のたびに置き場からモデルを読むので、同時に複数の要求が来ても困りません。Bandit は要求ごとにプロセスを立てるので、状態を持たないことがそのまま安全につながります。

## 15.7 モデルをファイルに保存する

第 8 章で、学習済みのパイプラインが `:erlang.term_to_binary/1` と `:erlang.binary_to_term/2` の往復で戻ることを確かめてあります。第 7 章の線形回帰のモデルも `%{intercept: …, columns: […], coefficients: […]}` というマップとリストなので、同じやり方で保存できます。

```elixir
@behaviour GettingStartedMl.Chapter15.ModelStore

@doc "ディレクトリを置き場にする。ディレクトリはまだ無くてもよい。"
def new(model_dir), do: {__MODULE__, model_dir}

@impl true
def load_sales_model(model_dir) do
  model_dir
  |> read_model(Domain.sales_model(), &:erlang.binary_to_term(File.read!(&1), [:safe]))
  |> Domain.linear_sales_model()
end

@impl true
def load_survival_model(model_dir) do
  model_dir
  |> read_model(Domain.survival_model(), &Chapter08.load_model/1)
  |> Domain.pipeline_survival_model()
end
```

`[:safe]` は、ファイルに書かれた未知のアトムを作らせない指定です。第 8 章で入れた用心をそのまま使っています。

保存の関数は behaviour に入れません。

```elixir
@doc "第 7 章の線形回帰のモデルを保存する。"
def save_sales_model({__MODULE__, model_dir}, model) do
  path = model_file(model_dir, Domain.sales_model())
  File.mkdir_p!(Path.dirname(path))
  File.write!(path, :erlang.term_to_binary(model))
end
```

読み込みは API が使いますが、保存は学習のときにしか使いません。API から見える約束を小さく保つほうが、偽物を書くのも楽になります（Java 版の `FileModelStore` が `saveSalesModel` を `ModelStore` の外に置いたのと同じ判断です）。引数のパターンに `{__MODULE__, model_dir}` と書いてあるので、**ほかの置き場を渡すと `FunctionClauseError` で落ちます**。「保存はファイルの置き場にしかない」ことが、引数の形で表れています。

ファイルが無いことは、読む前に確かめて例外に変えます。

```elixir
# ファイルを読む。無ければ「モデルが無い」に変える。
defp read_model(model_dir, model, read) do
  path = model_file(model_dir, model)
  unless File.exists?(path), do: raise(ModelNotFoundError, model: model)
  read.(path)
end
```

Ruby 版は `Errno::ENOENT` を捕まえましたが、Elixir では `File.read!` が投げるのは `File.Error` で、第 8 章の `load_model/1` は形式が違えば `ArgumentError` も投げます。捕まえて振り分けるより、**先に `File.exists?` で確かめるほうが読みやすい** と判断しました。ファイルが消えるのと読むのが同時に起きる競合はありますが、そのときは 500 になるだけです。

保存と読み込みは、手で作った小さなモデルでテストします。切片 10、係数 1・2・3・4 のモデルを保存して、予測値を手で計算した値と比べました。

```elixir
test "保存した線形回帰で予測できる" do
  # 10 + 1×200 + 2×500 + 3×3000 + 4×1
  sales = ModelStore.load_sales_model(store_with_models()).(StoreContract.movie())
  assert_in_delta sales, 10_214.0, 1.0e-9
end
```

生存予測のほうは、「女性が生存し、男性が死亡する」4 件の作り物のデータでパイプラインを学習します。実データも `gulp data:setup` も要らないので、**この章のテストのほとんどは学習データが無くても走ります**。

```elixir
# 女性が生存し、男性が死亡する 4 件の作り物のデータ。
defp survival_rows do
  Enum.map(
    [
      ["1", "female", "30", "0", "0", "80", "C"],
      ["3", "male", "40", "0", "0", "8", "S"],
      ["2", "female", "20", "1", "0", "30", "S"],
      ["3", "male", "25", "0", "0", "10", "S"]
    ],
    &Map.new(Enum.zip(Chapter08.feature_columns(), &1))
  )
end
```

## 15.8 Plug のハンドラーは関数

### Conn を受け取って Conn を返す

Plug の約束は短く書けます。

- ハンドラーは `init/1` と `call/2` を持つモジュール
- `init/1` は起動時に一度だけ呼ばれ、戻り値が `call/2` の第 2 引数になる
- `call/2` は `%Plug.Conn{}` を受け取り、応答を書き込んだ `%Plug.Conn{}` を返す

これだけです。Javalin の `Context`、Sinatra の `request`／`response` に当たるものが `%Plug.Conn{}` という **1 つの構造体** で、要求も応答もここに入っています。Clojure の Ring が要求と応答を別のマップにしたのとは、ここが違います。

```elixir
@impl true
def init(store), do: store
```

応答を書くのは 1 か所にまとめました。

```elixir
defp json(conn, status, body) do
  conn
  |> put_resp_content_type("application/json")
  |> send_resp(status, Jason.encode!(body))
end
```

`put_resp_content_type/2` は `charset=utf-8` を自分で付けてくれるので、`"application/json; charset=utf-8"` と書く必要はありません。

### 経路の振り分けは関数節で書く

```elixir
@impl true
def call(%Plug.Conn{method: "GET", request_path: "/health"} = conn, store) do
  models = Service.health(store)
  status = if Enum.all?(models, fn {_model, ready} -> ready end), do: "ok", else: "degraded"
  json(conn, 200, %{status: status, models: models})
end

def call(%Plug.Conn{method: "POST", request_path: "/cinema/sales"} = conn, store) do
  predict(conn, Validation.movie_types(), &Validation.movie/1, fn movie ->
    %{sales: Service.predict_sales(store, movie)}
  end)
end

def call(%Plug.Conn{method: "POST", request_path: "/survived"} = conn, store) do
  predict(conn, Validation.passenger_types(), &Validation.passenger/1, fn passenger ->
    %{survived: Service.predict_survival(store, passenger)}
  end)
end

def call(conn, _store) do
  case Map.fetch(@allowed_methods, conn.request_path) do
    {:ok, allow} ->
      conn
      |> put_resp_header("allow", allow)
      |> json(405, %{detail: "許していないメソッドです"})

    :error ->
      json(conn, 404, %{detail: "見つかりません"})
  end
end
```

**メソッドとパスを、引数のパターンに直接書いています。** Clojure 版は `cond` の中で `[:get "/health"]` というベクタを `=` で比べましたが、Elixir では構造体のフィールドをパターンに埋め込めるので、条件式が消えて関数の見出しになります。最後の受け皿の節が 404 と 405 を引き受ける形も、上から順に照合される規則がそのまま設計になっています。

`Plug.Router` を入れれば、パスの変数（`/cinema/:id`）やミドルウェアの合成が楽になります。この章はそこまで要らないので入れませんでした（[ADR 012](../../../adr/012-elixir-ml-libraries.md) の代替案の表）。**Plug のハンドラーが関数であることを、包まずに見せたい** というのがこの章の意図です。

405 のために、パスごとに許しているメソッドの表を持ちます。

```elixir
# パスごとに許しているメソッド。知っているパスに違うメソッドが来たら 405 にする。
@allowed_methods %{"/health" => "GET", "/cinema/sales" => "POST", "/survived" => "POST"}
```

経路の振り分けを自分で書くなら 405 も自分で書きます。ルートを足したら表も直す必要がある点は、二重に書いている負担です。ここは経路の数が少ないうちに限った割り切りで、増えるならライブラリを入れる判断に変わります。

### with と rescue でステータスコードに変える

```elixir
# 本文を読んで検証し、正しければ build で予測する。失敗はステータスコードに変える。
defp predict(conn, types, validate, build) do
  {:ok, body, conn} = read_body(conn)

  with {:ok, request} <- Validation.read_json(body, types),
       %{value: value, errors: []} <- validate.(request) do
    json(conn, 200, build.(value))
  else
    :error -> json(conn, 422, %{detail: [@invalid_json]})
    %{errors: errors} -> json(conn, 422, %{detail: errors})
  end
rescue
  e in ModelNotFoundError -> json(conn, 503, %{detail: Exception.message(e)})
  # 例外のメッセージには内部の事情が入るので、応答には出さない
  _other -> json(conn, 500, %{detail: "予測できませんでした"})
end
```

`with` の 2 行目 `%{value: value, errors: []} <- validate.(request)` は、**「理由が空である」ことまでパターンで書いています**。`errors: []` に合わなければ `else` の `%{errors: errors}` に落ち、そのまま 422 になります。Clojure 版が `if (validation/valid? validated)` と書いた条件式が、パターンに溶けました。

`rescue` の節が 2 つなのは、15.6 節で見たとおり例外を構造体で見分けられるからです。`e in ModelNotFoundError` を先に書き、それ以外を `_other` で 500 にします。

`rescue` を関数本体に付けているので、**`conn` は `read_body/1` で束ね直す前のものが見えます**。応答を書いていない `conn` なので、そのまま `send_resp` できます。ここは `try` を明示していたら気づきにくかったところです。

500 のときに例外のメッセージを返さないことは、テストで固定しました。

```elixir
test "予測が失敗すれば 500 を返し、内部の事情を出さない" do
  response =
    Api.call(
      post_json(
        "/cinema/sales",
        ~s({"sns1": 100, "sns2": 2000, "actor": 300, "original": 1})
      ),
      {GettingStartedMl.Chapter15Test.BrokenStore, nil}
    )

  assert response.status == 500
  assert decoded(response) == %{"detail" => "予測できませんでした"}
end
```

約束を破る置き場は、テストファイルの末尾にモジュールで置きました。

```elixir
defmodule GettingStartedMl.Chapter15Test.BrokenStore do
  @moduledoc "約束を破る置き場。予測のときに ModelNotFoundError 以外の例外を投げる。"

  @behaviour GettingStartedMl.Chapter15.ModelStore

  @impl true
  def load_sales_model(_state), do: raise(RuntimeError, "内部の秘密")

  @impl true
  def load_survival_model(_state), do: raise(RuntimeError, "内部の秘密")
end
```

### サーバーを起動しない統合テスト

`call/2` は関数なので、テストは呼ぶだけです。要求は `Plug.Test` の `conn/3` で作ります。

```elixir
defp call(store_options, conn), do: Api.call(conn, FakeStore.new(store_options))

defp post_json(path, body), do: conn(:post, path, body)

defp decoded(conn), do: Jason.decode!(conn.resp_body)
```

`Plug.Test.conn/3` は **サーバーを起動しません**。`%Plug.Conn{}` を組み立てるだけの関数で、本文はアダプターの中に持たせてくれるので、`read_body/1` が本物と同じように読めます。Clojure 版が「`:body` を入力ストリームにして本物と同じ形にする」と気を遣ったところが、Elixir では `conn/3` が面倒を見てくれます。

`use Plug.Test` は Elixir 1.18 で非推奨になっていて、そのまま書くと警告が出ます。

```text
warning: use Plug.Test is deprecated. Please use `import Plug.Test` and `import Plug.Conn` directly instead.
```

指示どおり `import Plug.Conn` と `import Plug.Test` の 2 行に直しました。応答も `%Plug.Conn{}` なので、フィールドをそのまま見られます。

```elixir
test "ヘルスチェックはすべて読み込めれば ok を返す" do
  response = call(@ready, conn(:get, "/health"))

  assert response.status == 200

  assert get_resp_header(response, "content-type") ==
           ["application/json; charset=utf-8"]

  assert decoded(response) == %{
           "status" => "ok",
           "models" => %{"cinema" => true, "survived" => true}
         }
end
```

Ruby 版の rack-test は「1 つのテストの中で `app` を 1 度しか作らない」という癖があり、置き場を替えるにはテストを分ける必要がありました。Elixir では `Api.call(conn, store)` の第 2 引数を替えるだけなので、置き場の差し替えは引数の差し替えです。**ハンドラーが関数であることが、そのままテストの書きやすさになっています。**

## 15.9 実データで学習したモデルで動かす

### 学習と保存

第 7・8 章と同じ条件（`test_size` 0.2、シード 0、深さ 5、`balanced` の重み）で学習します。

```elixir
@doc "第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。"
def train_and_save_models(data_dir, store) do
  cinema = Chapter07.prepare_cinema(Path.join(data_dir, "cinema.csv"), @test_size, @seed)

  FileStore.save_sales_model(
    store,
    Chapter07.fit(cinema.x_train, cinema.t_train, Chapter07.feature_columns())
  )

  FileStore.save_survival_model(
    store,
    survival_pipeline(Path.join(data_dir, "Survived.csv"))
  )
end
```

### 予測値はほかの言語版と一致するか

実データで学習したモデルをつなぐテストは、既存の章と同じく `@tag :data` を付けて、学習データが無ければ外れるようにします。期待値は、最初に Clojure 版の値を書いて走らせ、実際に測った値と突き合わせました。

**結果は「ほぼ一致、完全一致ではない」でした。**

| 言語版 | 予測値（`sns1=200, sns2=500, actor=3000, original=1`） |
|-------|--------------------------------------------|
| Java・Scala・Clojure | 7730.457421687023 |
| **Elixir** | **7730.457421687016** |

差は約 7e-12 です。分割（どの行が訓練データに入るか）は `GettingStartedMl.Random` が `java.util.Random` と同じ線形合同法なので完全に一致していて、係数を求める手順（正規方程式 `(Xᵀ X) w = Xᵀ t` を解く）も同じです。違うのは **解く実装** で、Elixir 版は `Nx.LinAlg.solve/2`、Java・Scala・Clojure 版はそれぞれの行列ライブラリを使っています。ガウスの消去法のピボットの選び方や積の足し合わせの順が変われば、最後の 1〜2 桁は動きます。

これは [第 7 章](07-linear-regression.md) で予告していたことです。第 7 章では切片と係数がほかの言語版と一致しましたが、そこには「表示の桁（小数第 2 位・第 4 位）で一致していることを確かめただけで、Clojure 版が誇っていた『丸め誤差の最後の 1 ビットまで一致する』という性質は Elixir 版には無い」と書きました。**その差が、8 章分あとの予測値のかたちで顔を出した** わけです。桁の主張は、どこまでを保証するのかを書いた側が意識していないと、あとの章で足をすくわれます。

テストには両方を書きました。1e-6 の許容でほかの言語版と突き合わせ、Elixir 版の値そのものも固定します。

```elixir
# Java 版・Scala 版・Clojure 版と同じ分割・同じ手順なので、予測値もほぼ一致する。
# 完全には一致せず、Elixir 版は 7730.457421687016（差は 1e-11 未満）。
# 正規方程式を解くのが Nx.LinAlg.solve か Java の実装かの違いで、丸めの順が変わる
assert_in_delta Service.predict_sales(store, StoreContract.movie()),
                7730.457421687023,
                1.0e-6

assert Service.predict_sales(store, StoreContract.movie()) === 7730.457421687016
assert Service.predict_survival(store, StoreContract.passenger()) == true
```

一方、**保存と読み込みで桁が落ちないことは、完全な一致で確かめられます**。

```elixir
@tag :data
test "保存と読み込みを挟んでも予測値は 1 ビットも変わらない" do
  cinema =
    Chapter07.prepare_cinema(Path.join(Dataset.dir(), "cinema.csv"), 0.2, 0)

  model = Chapter07.fit(cinema.x_train, cinema.t_train, Chapter07.feature_columns())
  direct = Domain.linear_sales_model(model).(StoreContract.movie())

  store = FileStore.new(temp_dir("roundtrip"))
  FileStore.save_sales_model(store, model)

  # :erlang.term_to_binary は浮動小数点数をそのままのビット列で書くので、往復で桁が落ちない
  assert Service.predict_sales(store, StoreContract.movie()) === direct
end
```

`===` は「型も値も同じ」を見る比較です。`:erlang.term_to_binary/1` は浮動小数点数を 64 ビットの IEEE 754 のビット列としてそのまま書くので、**文字列に直す Clojure 版の EDN と違って、桁を落とす余地がそもそもありません**。Clojure 版は「`pr-str` が読み戻して同じ値になる表記を選ぶ」ことで往復を保証していましたが、Elixir は二進で保存するぶん、この点は考えなくて済みます。

Ruby 版（7272.90…）・Kotlin 版（7830.41…）・Python 版と値が大きく違うのは、`java.util.Random` の乱数列とシャッフルの手順が言語版ごとに違い、訓練データに入る行が違うからです。こちらは 7e-12 ではなく、数十から数百の差になります。

### Bandit で待ち受ける

```elixir
@doc "モデルを学習して保存し、予測 API を起動する。Ctrl-C で止まるまで戻らない。"
def run(model_dir \\ @model_dir) do
  store = FileStore.new(model_dir)
  train_and_save_models(Dataset.dir(), store)

  for model <- Domain.model_names() do
    IO.puts("モデル #{model}: #{Map.fetch!(Service.health(store), model)}")
  end

  {:ok, _pid} = Bandit.start_link(plug: {Api, store}, ip: @host, port: @port)
  IO.puts("http://127.0.0.1:#{@port} で待ち受けます")
  Process.sleep(:infinity)
end
```

`Bandit.start_link/1` に渡すのは **`{ハンドラーのモジュール, 初期化する値}` と設定だけ** です。`plug: {Api, store}` の形が、15.2 節で置き場を `{モジュール, 状態}` の組にした形とそろっているのが分かります。`ip: {127, 0, 0, 1}` にして、自分のマシンからだけ接続できるようにしています。

`start_link/1` は **待ち受けを始めたらすぐ戻ります**。Erlang VM の上ではサーバーは別のプロセスとして動き続けるので、`mix run` から起動するときは自分のプロセスが終わらないようにする必要があります。`Process.sleep(:infinity)` はそのための 1 行です（`mix run --no-halt` と組み合わせます）。Clojure 版が `:join? true` で「サーバーが止まるまで戻らない」ようにしたのと、同じ目的の別の手です。

Clojure 版が必要とした `(flush)` は要りませんでした。`IO.puts` はグループリーダーのプロセスに 1 行ずつ送るので、リダイレクトしてもバッファに溜まったままになりません。

実行するとモデルを学習・保存してから待ち受けます（学習データの置き場は環境変数 `ML_DATA_DIR` で指定できます）。

```text
$ mix run --no-halt -e GettingStartedMl.Chapter15.run
モデル cinema: true
モデル survived: true
http://127.0.0.1:8015 で待ち受けます

13:32:57.587 [info] Running GettingStartedMl.Chapter15.Api with Bandit 1.12.5 at 127.0.0.1:8015 (http)
```

最後の 1 行は Bandit が Logger に出しています。**自分の `IO.puts` より後に出ている** のは、Logger が別のプロセスで非同期に書くからです。Clojure 版で Jetty が SLF4J の警告を 3 行出したところにあたりますが、Elixir では Logger が標準に入っているので、依存を足さずに素直なログが出ます。

別の端末から呼びます。

```text
$ curl -s http://127.0.0.1:8015/health
{"status":"ok","models":{"cinema":true,"survived":true}}

$ curl -s -X POST http://127.0.0.1:8015/cinema/sales \
    -H "Content-Type: application/json" \
    -d '{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}'
{"sales":7730.457421687016}

$ curl -s -X POST http://127.0.0.1:8015/survived \
    -H "Content-Type: application/json" \
    -d '{"pclass": 1, "sex": "female", "sib_sp": 0, "parch": 0, "fare": 50.0}'
{"survived":true}

$ curl -s -X POST http://127.0.0.1:8015/survived \
    -H "Content-Type: application/json" \
    -d '{"pclass": 3, "sex": "male", "sib_sp": 0, "parch": 0, "fare": 8.0, "embarked": "S"}'
{"survived":false}
```

1 等客室の女性は生存、3 等客室の男性は死亡という予測です。**どちらも年齢を送っていません。** 空欄のまま第 8 章のパイプラインに渡り、訓練データから求めた中央値で補完されています。

不正な入力は 422 です。

```text
$ curl -s -X POST http://127.0.0.1:8015/cinema/sales \
    -H "Content-Type: application/json" \
    -d '{"sns1": -1, "sns2": 2000, "actor": 300, "original": 2}'
{"detail":["sns1 は 0 以上にしてください","original は 0、1 のどれかにしてください"]}

$ curl -s -X POST http://127.0.0.1:8015/cinema/sales \
    -H "Content-Type: application/json" \
    -d '{"sns1": "たくさん"}'
{"detail":["JSON の形式または値の型が正しくありません"]}
```

知らないパスは 404、許していないメソッドは 405 です。

```text
$ curl -s -i http://127.0.0.1:8015/unknown | head -8
HTTP/1.1 404 Not Found
date: Wed, 23 Sep 2026 04:33:11 GMT
content-length: 34
vary: accept-encoding
cache-control: max-age=0, private, must-revalidate
content-type: application/json; charset=utf-8

{"detail":"見つかりません"}

$ curl -s -i http://127.0.0.1:8015/cinema/sales | head -8
HTTP/1.1 405 Method Not Allowed
date: Wed, 23 Sep 2026 04:33:11 GMT
content-length: 49
vary: accept-encoding
cache-control: max-age=0, private, must-revalidate
allow: POST
content-type: application/json; charset=utf-8
```

ここで 2 つ気づくことがあります。

1 つめは、**ヘッダーの名前がすべて小文字で届く** ことです。Bandit は HTTP/1.1 でもヘッダー名を小文字に正規化します（HTTP/2 の規則に合わせた形です）。テストで `get_resp_header(response, "content-type")` と小文字で引いているのは、Plug がもともとヘッダー名を小文字で持つからで、ここは本物と同じでした。

2 つめは、**自分が書いていない `vary` と `cache-control` が付いている** ことです。これは Plug が既定で付けるヘッダーで、`call/2` の戻り値を見ているテストには現れません。Clojure 版では Jetty が `Content-Type` の空白を詰めて届けるという差が出ましたが、Elixir 版では `Content-Type` はそのまま（`application/json; charset=utf-8`、セミコロンの後に空白）で届き、代わりにヘッダーが増えていました。

どちらも意味は変わらないのでそのままにしましたが、**「ハンドラーの応答」と「クライアントに届く応答」は同じではない** ことは、関数として直接呼ぶテストの限界として覚えておく価値があります。ヘッダーの正規化・追加・接続の扱いは、サーバーが決めます。

## 15.10 品質チェック

```bash
mix format --check-formatted
mix compile --warnings-as-errors
mix credo --strict
mix test --cover
```

学習データがある状態では 138 tests・0 failures、学習データを外すと `:data` の 12 件（この章の 3 件と、それまでの章の 9 件）が外れて 126 件が走ります。

| 学習データ | tests | failures | 備考 |
|-----------|-------|---------|------|
| あり | 138 | 0 | — |
| なし（`ML_DATA_DIR=/nonexistent`） | 138 | 0 | `:data` の 12 件を除外 |

この章のモジュールごとのカバレッジ（学習データあり）は次のとおりです。

| モジュール | カバレッジ |
|-----------|-----------|
| `GettingStartedMl.Chapter15` | 50.00% |
| `GettingStartedMl.Chapter15.Api` | 95.24% |
| `GettingStartedMl.Chapter15.Domain` | 95.24% |
| `GettingStartedMl.Chapter15.FileStore` | 100.00% |
| `GettingStartedMl.Chapter15.ModelNotFoundError` | 100.00% |
| `GettingStartedMl.Chapter15.ModelStore` | 100.00% |
| `GettingStartedMl.Chapter15.Service` | 100.00% |
| `GettingStartedMl.Chapter15.Validation` | 100.00% |
| 全体 | 90.18% |

層のモジュールはどれも 95% 以上です。低いのは組み立ての `Chapter15` で、**サーバーを起動する `run/1` をテストしていない** からです。ここは `curl` で確かめました。Clojure 版・Ruby 版でも同じ形（層は 100% 近く、起動の関数だけ低い）になっていて、層を分けた結果として読めます。

Credo には最終的に 1 つも指摘されませんでした（`found no issues`）。途中で出た「ネストが深すぎる」は 15.4 節のとおり `with` で直しています。

## 15.11 まとめ

この章では、第 7・8 章のモデルを Plug の HTTP API にしました。Elixir に固有の論点は次のとおりです。

1. **`call/2` は関数、経路は関数節のパターンマッチ** — `def call(%Plug.Conn{method: "GET", request_path: "/health"} = conn, store)` と書けば、条件式もルーターも要らない。統合テストは `Plug.Test.conn/3` で要求を組み立てて呼ぶだけで、サーバーは起動しない。そのかわり、Bandit が小文字に正規化したヘッダーや Plug が足す `vary`・`cache-control` は関数のテストでは見えないので、`curl -i` で確かめる
2. **約束は behaviour と約束のテストの二段構え** — `@behaviour` と `@impl true` は実装漏れと名前の間違いをコンパイル時の警告にする（`warnings_as_errors` でビルドが止まる）。「無ければ例外を投げる」は書けないので、本物と偽物の両方に走らせる関数（`StoreContract.check/2`）で守る。`ExUnit.Assertions` の `assert` が `test` の外でも動くので、約束はただの関数でよい
3. **behaviour はモジュールへの約束なので、置き場は `{モジュール, 状態}` の組** — Clojure の `reify` のように「その場で約束を満たす値」は作れず、偽物もモジュールになる。差し替えたいものを状態に追い出せば、モジュールは 1 つで足りる。この形は Plug 自身の `plug: {Api, store}` と同じ
4. **例外は構造体なので `rescue` の節で選べる** — `defexception [:model]` で作った `ModelNotFoundError` を `e in ModelNotFoundError` で捕まえる。Clojure 版が `ex-info` を捕まえてから振り分けた手数が 1 つ減る
5. **`with` が検証の流れをそのまま書ける** — 「JSON として読める・オブジェクトである・型が合う」「読めた・理由が空」が縦に並び、崩れたところが `else` に落ちる。Credo の `--strict` はネストの深さを見ているので、`case` の入れ子より `with` のほうが通りやすい
6. **保存はバイナリなので往復で 1 ビットも落ちない** — `:erlang.term_to_binary` は浮動小数点数をそのまま書く。予測値は保存の前後で `===` で一致した。一方で **ほかの言語版との一致は 7e-12 の差が残った**（`Nx.LinAlg.solve` と Java の行列ライブラリで、正規方程式を解く丸めの順が違う）

**TODO リスト（この章の完了時点）**:

- [x] 要求の JSON を読み、型が合わなければ弾く
- [x] 必須・0 以上・選択肢の検証をし、理由を並べて返す
- [x] 映画・乗客の特徴量をマップで表す
- [x] 置き場の約束を behaviour と約束のテストで表す
- [x] 第 7 章の線形回帰と第 8 章のパイプラインをファイルに保存・読み込みする
- [x] ファイルが無ければ `ModelNotFoundError` を投げる
- [x] サービスが置き場からモデルを読んで予測し、ヘルスチェックを返す
- [x] `POST /cinema/sales`・`POST /survived` が予測を JSON で返す
- [x] 入力が不正なら 422、モデルが無ければ 503、それ以外は 500
- [x] 知らないパスは 404、許していないメソッドは 405
- [x] 第 7・8 章と同じ条件で学習し、API を起動する（`mix run --no-halt -e GettingStartedMl.Chapter15.run`）

これで Elixir 版は、第 1 章の「20 代ならきのこ派」という手書きのルールから、学習したモデルを HTTP で届けるところまでたどり着きました。最後まで支えになったのは、**値がただのマップとリストである** ことと、**分岐がパターンマッチである** ことです。表も、モデルも、学習済みのパイプラインも、要求も応答も、同じ道具で作り、`==` で比べ、`:erlang.term_to_binary` で保存できました。型を宣言しない言語で安全網になったのはテストですが、その安全網をここまで安く張れたのは、比べるものがどれも値だったからです。
