---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "素のマップとリストで表を自作し、nil で欠損値を表して iris データを前処理する。java.util.Random と同じ 48 ビットの線形合同法を自作して JVM の 3 言語版と並びをそろえ、タプルと put_elem による Fisher-Yates を Elixir の TDD で実装する。マップがキーの順を保たないことも実測で確かめる。"
tags: [article,getting-start-ml,elixir]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:00:00Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

第 1 章では、欠損のないきれいなデータを読み込み、人間が書いたルールで判定しました。現実のデータには、空欄（欠損値）が混ざっています。

この章では、アヤメ（iris）のデータを題材に、欠損値を補完し、データを **訓練データ** と **テストデータ** に分けるところまでを TDD で実装します。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md)・[Clojure 版の第 2 章](../clojure/02-data-preprocessing-and-triangulation.md) と同じ TODO リストで進めます。Python 版は pandas、Kotlin 版は Kotlin DataFrame を使いましたが、Elixir 版は Java 版・Scala 版・Clojure 版と同じく **データフレームのライブラリを使わず**、小さな表を自分で組み立てます。Elixir では、その表が**素のマップとリスト**になります。

そしてこの章には、シリーズを通していちばん変わった山場があります。**乱数生成器そのものを自作します。** ライブラリが無いからではありません。ほかの言語版と数値を突き合わせるためです。次の 4 点に注目してください。

- **乱数生成器を 3 行の漸化式として書く** — `Nx.Random`（Threefry）も Erlang の `:rand` も、ほかの言語版と並びが合いません。そこで `java.util.Random` と同じ 48 ビットの線形合同法を写し取ります。結果として並びが JVM の 3 言語版（Java・Scala・Clojure）と一致します（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）
- **欠損値は `nil` で表す** — Elixir には `Option` も `Maybe` もありません。Ruby 版・Python 版・Clojure 版と同じ `nil`／`None` の世界です
- **マップはキーの順を保たない** — Ruby の `Hash` とは違います。列の順が要るところでは、CSV から取った**リスト**で持ち回ります
- **タプルと `put_elem` で交換する** — リストは添字での更新が苦手なので、Fisher-Yates ではいったんタプルに変換します

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | Elixir 版での読み方 |
|----|------|------------------|
| がく片長さ | がく片の長さ | `number(row, :がく片長さ)` → `float` か `nil` |
| がく片幅 | がく片の幅 | `number(row, :がく片幅)` → `float` か `nil` |
| 花弁長さ | 花弁の長さ | `number(row, :花弁長さ)` → `float` か `nil` |
| 花弁幅 | 花弁の幅 | `number(row, :花弁幅)` → `float` か `nil` |
| 種類 | 品種（3 種類が 50 件ずつ） | `text(row, :種類)` → 文字列 |

値は 0.0〜1.0 に収まる形で配布されています。特徴量の 4 列には合わせて 7 件の欠損値があります。Elixir には「値があるかもしれない」を表す型がないので、**無いことは `nil` で表します**。

| 言語 | 欠損値の表し方 | 値を取り出す |
|------|--------------|------------|
| **Elixir** | **`nil`** | **`\|\|`・`Enum.reject(&is_nil/1)`・`is_nil/1`・パターンマッチ** |
| Clojure | `nil` | `or`・`keep`・`some?`・`when-let` |
| Scala | `Option[Double]`（`Some`／`None`） | `getOrElse`・`map`・`flatMap`・`match` |
| F# | `float option`（`Some`／`None`） | `Option.defaultValue`・`match` |
| Ruby・Python | `nil`／`None` | `\|\|`・`or`・`compact`／`is None` |

型で表せないぶん弱いのは確かです。そのかわり、`nil` は Elixir ではただの値なので、パイプラインの途中でも `Enum.reject(&is_nil/1)` の一手で落とせます。**`Option` の包みを剥がす手間が要らない**ぶん、書く量は減ります。減った手間の代償は「除き忘れてもコンパイル時に気づけないこと」で、これは第 1 章から変わらない動的型付けの取引です。

### 訓練データとテストデータ

学習に使うデータでそのまま性能を測ると、「答えを覚えただけ」のモデルを高く評価してしまいます。そこでデータを 2 つに分けます。この章では 150 件を訓練データ 105 件・テストデータ 45 件に分けます。

### なぜ乱数を自作するのか

分割の前に並べ替えます。並べ替えには乱数が要ります。Elixir には乱数の選択肢が 2 つあります。

| 選択肢 | シード 0 で `0..9` を並べ替えた結果 |
|--------|--------------------------------|
| `Nx.Random`（Threefry） | `[2, 7, 9, 6, 0, 8, 1, 3, 4, 5]` |
| Erlang の `:rand` | さらに別の並び |
| **Java 版・Scala 版・Clojure 版** | **`[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]`** |

**どちらもほかの言語版と一致しません。** 一致しなければ訓練データに入る 105 行が変わり、そこから求まる平均値も、第 3 章の決定木の境界も変わります。章をまたいだ突き合わせができなくなります。

そこで Elixir 版は、**`java.util.Random` と同じ乱数生成器を自作します**。`java.util.Random` は乱数の作り方が仕様として文書に書かれていて、48 ビットの線形合同法という、写し取れる程度に小さなアルゴリズムです。これを Elixir で書けば、JVM を持たない言語から JVM と同じ数列が出せます。

「乱数生成器は魔法ではなく 3 行の漸化式である」——この章はそれを実際に確かめる章になります。

## 2.3 開発環境の準備

第 1 章で作った `apps/elixir/` にファイルを足します。

```text
apps/elixir/
├── mix.exs
├── lib/
│   └── getting_started_ml/
│       ├── csv.ex
│       ├── dataset.ex
│       ├── chapter01.ex
│       ├── chapter02.ex    # この章
│       └── random.ex       # この章（自作の乱数）
└── test/
    └── getting_started_ml/
        ├── chapter01_test.exs
        └── chapter02_test.exs
```

依存は第 1 章のまま（NimbleCSV）で足ります。**乱数を自作するので、この章では依存が 1 つも増えません。**

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] 乱数生成器を自作する
  - [ ] シードから状態を作る
  - [ ] 範囲を指定して整数を 1 つ返す
  - [ ] Fisher-Yates で並べ替える
  - [ ] シード 0 の並びが JVM の言語版と一致する
- [ ] 表を読み込む
  - [ ] 数値の列を読む。空欄は欠損値にする
  - [ ] 数値として読めない値・列の不足を弾く
  - [ ] 列ごとに欠損値の数を数える
- [ ] 平均値で欠損値を補完する
  - [ ] 欠損値を除いて列ごとの平均値を求める
  - [ ] 欠損値を平均値で置き換える
- [ ] 特徴量と正解ラベルに分ける
- [ ] 訓練データとテストデータに分ける
  - [ ] 指定した割合で分ける
  - [ ] 特徴量と正解ラベルの対応を崩さない
  - [ ] シードを指定すれば同じ分け方になる
- [ ] 前処理をまとめる
- [ ] 実データで前処理の結果を表示する

## 2.5 乱数生成器を自作する

先に山場から片付けます。ここが決まらないと、分割のテストで何を期待値にすればいいかが決まりません。

### テストファースト: 性質から挟み撃ちにする

最初のテストは、並びの中身を書きません。**並べ替えの「性質」だけ**を確かめます。

```elixir
test "同じシードなら何度でも同じ並びになる" do
  assert Random.shuffle(Enum.to_list(1..20), 42) == Random.shuffle(Enum.to_list(1..20), 42)
end

test "シードが違えば並びが変わる" do
  refute Random.shuffle(Enum.to_list(0..9), 0) == Random.shuffle(Enum.to_list(0..9), 1)
end

test "元の要素は増えも減りもしない" do
  assert Enum.sort(Random.shuffle(Enum.to_list(0..9), 7)) == Enum.to_list(0..9)
end
```

「同じシードなら同じ、違うシードなら違う」「並べ替えても要素は変わらない」の 3 つで、並べ替えの性質を挟み撃ちにします。この 3 つは、乱数の作り方をどう変えても通り続けるテストです。

境界も先に固定します。

```elixir
test "空のリストと一要素のリストはそのまま返る" do
  assert Random.shuffle([], 0) == []
  assert Random.shuffle([:only], 0) == [:only]
end

test "二の冪の範囲でも偏りのない値を返す" do
  {value, _} = Random.next_int(Random.new(0), 8)
  assert value in 0..7
end
```

最後の 1 つが伏線です。**`java.util.Random` は、範囲が 2 の冪のときだけ別の式を使います。** その枝を通るテストを最初から置いておきます。

### Green: 48 ビットの線形合同法

`java.util.Random` の仕様をそのまま写します。

```elixir
import Bitwise

@mask 0xFFFFFFFFFFFF
@multiplier 0x5DEECE66D
@increment 0xB

@doc "シードから状態を作る。`java.util.Random` の `setSeed` と同じ。"
def new(seed), do: bxor(seed, @multiplier) &&& @mask
```

**乱数生成器の全体像は、定数 3 つと漸化式 1 本です。**

- `@mask` は 48 ビット分の 1 が並んだ値。状態は 48 ビットに切り詰めます
- `@multiplier`（`0x5DEECE66D`）が乗数、`@increment`（`0xB` = 11）が加数
- `new/1` は、シードを乗数と `bxor` で撹拌してから 48 ビットに切ります。`java.util.Random` の `setSeed` と同じ式です

`import Bitwise` で `&&&`（ビット積）・`>>>`（右シフト）・`bxor/2` が使えるようになります。Elixir はこれらを演算子として言語に組み込んでおらず、`Bitwise` モジュールから取り込む形にしています。`import` を書いた時点でどのビット演算が使えるようになるかがファイルの先頭で分かるので、ビット演算が要る場所だけに限定できます。

漸化式が本体です。

```elixir
# 上位ビットだけを使う。48 ビットの状態から欲しいビット数を取り出す。
defp next_bits(state, bits) do
  next = state * @multiplier + @increment &&& @mask
  {next >>> (48 - bits), next}
end
```

**`next = state * @multiplier + @increment &&& @mask` の 1 行が、乱数生成器のすべてです。** 「前の状態に乗数を掛けて加数を足し、48 ビットに切る」。これだけで、見た目にはでたらめな数列が出てきます。線形合同法（Linear Congruential Generator）という名前も、この「1 次式を法で割る」という形から来ています。

`next >>> (48 - bits)` で**上位ビットだけを取り出す**ところが肝心です。線形合同法は下位ビットの周期が短い（最下位ビットは 0 と 1 を交互に繰り返す）ので、質の良い上位側だけを使います。

戻り値がタプル `{値, 次の状態}` になっているのは、**Elixir に可変の状態が無い**からです。Java の `Random` はオブジェクトの中の `seed` フィールドを書き換えますが、Elixir では「新しい状態を呼び出し側に返す」しかありません。この形は F# 版・Clojure 版の純粋な乱数と同じで、**状態を持ち回るコストが型ではなく戻り値に現れる**書き方です。

### 2 の冪のときだけ別式

```elixir
def next_int(state, bound) when bound > 0 do
  if (bound &&& -bound) == bound do
    {bits, next} = next_bits(state, 31)
    {(bound * bits) >>> 31, next}
  else
    reject(state, bound)
  end
end
```

`(bound &&& -bound) == bound` が「2 の冪かどうか」の判定です。2 の補数表現では `-bound` の下位ビットが反転するので、`bound` と `&&&` を取ると**最下位の立っているビットだけ**が残ります。それが `bound` 自身と等しいなら、立っているビットは 1 つ、つまり 2 の冪です。

2 の冪のときは `(bound * bits) >>> 31` という掛けてからシフトする式を使います。これは `java.util.Random` がそうしているからで、**アルゴリズムを写す以上、理由が分からなくても写す**部分です（質の良い上位ビットを使うための工夫です）。ここを「`rem` で済むだろう」と省くと、その瞬間に JVM と並びが合わなくなります。

### 剰余の偏りを避ける棄却

2 の冪でないときは、素直に `rem` を取ると偏りが出ます。たとえば 31 ビットの乱数を 3 で割った余りにすると、`0` になる場合がほんのわずかに多くなります。`java.util.Random` はこれを**棄却**で避けます。

```elixir
# 剰余の偏りを避けるため、範囲をはみ出す値は捨てて引き直す。
defp reject(state, bound) do
  {bits, next} = next_bits(state, 31)
  value = rem(bits, bound)

  if bits - value + (bound - 1) >= 0x80000000 do
    reject(next, bound)
  else
    {value, next}
  end
end
```

条件の `bits - value + (bound - 1) >= 0x80000000` は、「この `bits` が、31 ビットの範囲を `bound` で区切った最後の中途半端な区画に入っているか」を見ています。入っていたら捨てて引き直します。Java では `int` のオーバーフローで負になることを使って `< 0` と書いていますが、**Elixir の整数は多倍長なのでオーバーフローしません**。そこで、オーバーフローの境界である `0x80000000` と直接比べる形に書き換えました。

**同じアルゴリズムを違う整数の性質の上で写すと、条件式の書き方が変わる**という例です。「Java のコードをそのまま貼れば済む」わけではないところで、ここは実際に並びを確かめて合わせました。

引き直しは再帰で書きます。`reject/2` が自分自身を末尾で呼ぶだけなので、Erlang VM の末尾呼び出し最適化が効いてスタックは伸びません。Clojure が `recur` という専用の形を必要とした（JVM に末尾呼び出し最適化が無いため）ところが、Elixir では普通の再帰で済みます。

### タプルと `put_elem` による Fisher-Yates

```elixir
@doc """
Fisher-Yates で並べ替える。

後ろから順に、まだ選んでいない範囲から 1 つ選んで交換する。
"""
def shuffle(items, seed) do
  array = List.to_tuple(items)
  last = tuple_size(array) - 1

  if last < 1 do
    items
  else
    {shuffled, _state} =
      Enum.reduce(last..1//-1, {array, new(seed)}, fn i, {acc, state} ->
        {j, next} = next_int(state, i + 1)
        {swap(acc, i, j), next}
      end)

    Tuple.to_list(shuffled)
  end
end

defp swap(tuple, i, j) do
  at_i = elem(tuple, i)
  at_j = elem(tuple, j)
  tuple |> put_elem(i, at_j) |> put_elem(j, at_i)
end
```

**リストではなくタプルを使うところが、Elixir 固有の判断です。** Fisher-Yates は「i 番目と j 番目を交換する」を繰り返すアルゴリズムなので、添字でのアクセスと更新が要ります。Elixir のリストは連結リストなので、`Enum.at(list, i)` は先頭から i 回たどります。これを n 回繰り返すと二乗の計算量になります。

タプルは要素への定数時間のアクセスができ、`put_elem/3` で「1 要素だけ差し替えた新しいタプル」を作れます。`put_elem` は新しいタプルを返すので**不変性は保たれたまま**です。Clojure 版がここで `object-array` と `aset` という本当に可変な配列を使ったのとは対照的で、**Elixir では可変にしなくてもアルゴリズムをそのまま書けます**。

| 言語 | Fisher-Yates での交換 | 可変か |
|------|--------------------|-------|
| Java・Scala | 配列の要素の代入 | 可変 |
| Clojure | `object-array` と `aset`（関数の中に閉じた可変） | 可変 |
| **Elixir** | **タプルと `put_elem/3`** | **不変（毎回新しいタプル）** |

`Enum.reduce` の畳み込みの値が `{配列, 乱数の状態}` の 2 要素タプルになっているところも読みどころです。**Elixir には「その場で書き換える変数」が無いので、繰り返しの中で変わっていくものはすべて畳み込みの値に載せます。** ここでは配列と乱数の状態の 2 つが変わるので、タプルで束ねて持ち回ります。可変の言語なら「配列を書き換えつつ `random.nextInt()` を呼ぶ」で済むところが、状態を明示的に運ぶ形になりました。

`last..1//-1` は「`last` から 1 まで 1 ずつ減らす」範囲です。Elixir 1.12 以降、逆向きの範囲には `//-1` の刻み幅を明示する必要があります（書かないと空の範囲になります）。**後ろから前へ進む**のは Fisher-Yates の定義そのままで、この向きを変えると並びが変わります。

`last < 1` の早期リターンは、要素が 0 個か 1 個のときです。`0..1//-1` のような空の範囲を畳み込んでも結果は同じですが、`tuple_size` が 0 のときに `-1..1//-1` を作ることになるので、素直に分けました。

### 山場のテスト: JVM の 3 言語版と一致する

```elixir
test "シード 0 の並びが Java 版・Scala 版・Clojure 版と一致する" do
  assert Random.shuffle(Enum.to_list(0..9), 0) == [4, 8, 9, 6, 3, 5, 2, 1, 7, 0]
end
```

**通りました。** `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]` は、Java 版・Scala 版・Clojure 版とまったく同じ並びです。

JVM を持たない言語が、JVM の乱数と同じ数列を出しました。**乱数生成器は、定数 3 つと 1 行の漸化式でできています。** 「乱数」という言葉の響きに反して、中身は完全に決定的で、写し取れます。

この 1 行のテストが、言語をまたいだ約束の見張り番になります。ここを壊せば、第 3 章の正解率も木の境界も合わなくなります。

## 2.6 不変の表で読み込む

### データの表し方を決める

次に決めるのは、**表をどう表すか**です。第 1 章では「人物」という 1 つのマップにしましたが、この章では列の並びも扱うので、表そのものが必要になります。

| 対象 | 表し方 |
|------|--------|
| 読み込んだ行 | 列名のアトムから文字列へのマップ（`%{がく片長さ: "5.1", 種類: "Iris-setosa"}`） |
| 表 | `%{columns: [アトム...], rows: [行...]}` |
| 補完した後の特徴量 | 列名のアトムから `float` へのマップ（`%{がく片長さ: 5.1, ...}`） |
| 分割の結果 | `%{x_train: ..., x_test: ..., t_train: ..., t_test: ...}` |

Scala 版は `Row`・`Table`・`Features`・`TrainTestSplit` の 4 つの `case class` を宣言しました。Elixir 版はどれも素のマップです。構造体（`defstruct`）を使う手もありますが、使いません。**新しい型を宣言しない**ことの意味は 2 つあります。

1. すべての `Enum`・`Map` の関数がそのまま使える。`Table` 用の `map` を書く必要はありません
2. 何が入っているかはコードとテストからしか分からない。`Features` と `Row` がどちらもマップなので、**「補完済みかどうか」を型で区別できません**。Scala 版が `Row` と `Features` を分けて「欠損値が残っていないこと」を戻り値の型で保証したところは、Elixir 版ではテストで保証することになります

### マップはキーの順を保たない

表を `%{columns: ..., rows: ...}` にしたのは、**列の順を保つ**ためです。ここが Ruby 版といちばん違うところです。Ruby の `Hash` は挿入の順を保つので、Ruby 版は列の順を別に持つ必要がありませんでした。**Elixir のマップは順を保ちません。**

確かめました。

```elixir
IO.inspect(Map.keys(%{k1: 1, k2: 2, k3: 3}))
m = Map.new(1..40, fn i -> {String.to_atom("k#{i}"), i} end)
IO.inspect(Enum.take(Map.keys(m), 5))
```

```text
[:k1, :k2, :k3]
[:k23, :k24, :k7, :k35, :k10]
```

**3 件のマップでは挿入した順に見えますが、40 件のマップでは崩れます。** Elixir のマップは、キーが 32 個までは並びを持つ小さな表（flatmap）で、32 個を超えるとハッシュ表（hashmap）に切り替わります。ハッシュ表になると、並びはキーのハッシュ値で決まります。上の出力が `[:k23, :k24, :k7, ...]` という無関係な順になっているのがそれです。

Clojure 版がまったく同じ落とし穴を持っていて（あちらは 9 件目から崩れます）、同じ結論に至っています。**小さいうちは挿入順に見えるので、テストデータが小さいと気づけません。** 列が 5 つの iris なら偶然通ってしまい、列が増えた瞬間に崩れます。

だから列の順は、CSV から取った**リスト**で持ち回ります。`Csv.parse_table/1` が `{columns, rows}` というタプルを返す設計になっているのは、そのためです。

```elixir
@doc """
CSV を読み、列名の並びと行のリストを返す。

Elixir のマップはキーの順を保たない（大きくなると並びが崩れる）ので、
列の順を保ちたいときはこちらを使って列名のリストを持ち回る。
"""
def read_table(path), do: path |> File.read!() |> parse_table()

@doc "文字列を「列名の並び」と「行のリスト」にする。"
def parse_table(contents) do
  [header | rows] = Parser.parse_string(contents, skip_headers: false)
  columns = header |> strip_bom() |> Enum.map(&String.to_atom/1)

  {columns, Enum.map(rows, fn row -> columns |> Enum.zip(row) |> Map.new() end)}
end

defp strip_bom([first | rest]), do: [String.replace_prefix(first, @bom, "") | rest]
```

`[header | rows] = ...` が**パターンマッチによる分解**です。リストの先頭と残りを 1 行で取り出します。`=` は代入ではなく「左右が同じ形であること」の表明なので、行が 1 つも無い CSV を渡せばここで `MatchError` になります。**失敗する場所が早い**のは、パターンマッチの副産物です。

`Enum.zip(columns, row) |> Map.new()` で、列名と値を組にしてマップにします。Clojure の `zipmap` に当たる 2 手です。

BOM（`\uFEFF`）は第 1 章と同じく NimbleCSV が取り除かないので、先頭の列名から自分で剥がします。`String.replace_prefix/3` は先頭に一致したときだけ置き換えるので、BOM の無いファイルではそのまま通ります。

### テストファースト

セルを読むところから始めます。

```elixir
defp table do
  {columns, rows} =
    GettingStartedMl.Csv.parse_table(
      "がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n5.1,3.5,1.4,0.2,setosa\n,3.0,1.4,0.2,setosa\n7.0,3.2,4.7,1.4,versicolor\n"
    )

  %{columns: columns, rows: rows}
end
```

```elixir
test "文字列の列を読む" do
  assert C.text(hd(table().rows), :種類) == "setosa"
end

test "列が無ければ読めない" do
  assert_raise ArgumentError, "列がありません: 産地", fn -> C.text(hd(table().rows), :産地) end
end

test "数値の列を読む" do
  assert C.number(hd(table().rows), :がく片長さ) == 5.1
end

test "空欄は nil になる" do
  assert C.number(Enum.at(table().rows, 1), :がく片長さ) == nil
end

test "数値として読めなければ失敗する" do
  row = %{がく片長さ: "たかい"}

  assert_raise ArgumentError, "がく片長さ を数値として読めません: たかい", fn ->
    C.number(row, :がく片長さ)
  end
end
```

**フィクスチャを CSV の文字列そのままにしました。** 行のマップを手で書く代わりに、実際のパーサーを通しています。列の順が CSV の順のまま取れることも、同じフィクスチャで確かめられるからです。

「数値として読めなければ失敗する」のテストだけ、フィクスチャではなくマップのリテラル `%{がく片長さ: "たかい"}` を直接書いています。**型を宣言していないので、この 1 行だけで行になります。** 型を宣言する言語なら、この 1 行のためにコンストラクタの呼び出しが要ります。動的型付けの代償を払っているぶんの見返りが、ここに出ています。

### Green: 明白な実装

```elixir
@doc "文字列の列を読む。列が無ければ失敗する。"
def text(row, column) do
  case Map.fetch(row, column) do
    {:ok, value} -> value
    :error -> raise ArgumentError, "列がありません: #{column}"
  end
end

@doc "セルが空欄かどうかを返す。"
def missing?(row, column), do: row |> text(column) |> String.trim() == ""

@doc """
数値の列を読む。空欄なら `nil` を返す。数値として読めなければ失敗する。

Elixir には Option が無いので、欠損値は `nil` で表す。
"""
def number(row, column) do
  cell = row |> text(column) |> String.trim()

  if cell == "" do
    nil
  else
    case Float.parse(cell) do
      {value, ""} -> value
      _ -> raise ArgumentError, "#{column} を数値として読めません: #{cell}"
    end
  end
end
```

**`Map.fetch/2` の戻り値が `{:ok, value}` か `:error` のタグ付きタプル**です。`Map.get/2` なら無いときに `nil` が返りますが、それでは「列が無い」と「セルが `nil`」が区別できません。`fetch` を `case` で受けると、**2 つの場合が並んで見え、どちらも書き漏らせません**。Elixir で「無いかもしれない」を扱う定番の形です。

`Float.parse/1` も同じ形で、`{値, 残りの文字列}` か `:error` を返します。`{value, ""}` というパターンが「全部を数値として読み切れた」の意味です。`"5.1kg"` なら `{5.1, "kg"}` が返るので、このパターンには一致せず `_` の側に落ちて例外になります。**「読めた」と「全部読めた」を、パターンの形だけで区別しています。**

`missing?/2` の末尾の `?` は「真偽値を返す」という Elixir の命名の慣習です（Ruby と同じ）。

### 行末の空欄の落とし穴

Scala 版・Java 版は、ここで `split(",", -1)` の `-1` に苦しみました。`"4,5,"` を素直に `split` すると末尾の空文字列が落ち、列の数が合わなくなるからです。

**NimbleCSV ではこの問題は起きません。** 途中の空欄も行末の空欄も、空文字列として残ります。NimbleCSV は文字列を分割するのではなく CSV として解析するので、`split` の都合に振り回されません。**「文字列操作で済ませる」か「解析器を使う」かの違い**で、後者を選んだぶんの手間がここで返ってきています。

いっぽうで、BOM は相変わらず取り除いてくれません。**取り除いてくれるものと、くれないものが同じライブラリの中に混ざる**ので、どちらなのかは実際に動かして確かめるしかありません。

### 表として読み込む

```elixir
@doc "CSV を読み込んで表にする。列の順は CSV の順のまま。"
def load_table(path) do
  {columns, rows} = Csv.read_table(path)
  %{columns: columns, rows: rows}
end
```

**行を人物に変換しません。** 列名を付けるところまでで止め、数値への変換は後の工程（`fill_missing/3`）に回しています。欠損値の補完に平均値が要り、その平均値は訓練データからしか計算できないので、**「読む」と「数値にする」を分けないと順番が組めない**からです。

### 列ごとの欠損値の数

```elixir
@doc "列ごとに欠損値の数を数える。列の順は表の列の順のまま。"
def count_missing(%{columns: columns, rows: rows}) do
  Enum.map(columns, fn column -> {column, Enum.count(rows, &missing?(&1, column))} end)
end
```

**引数の `%{columns: columns, rows: rows}` が、関数の頭でのパターンマッチです。** 渡されたマップから `:columns` と `:rows` を取り出し、同じ名前の変数に束縛します。Clojure の分配束縛（`{:keys [columns rows]}`）に当たるもので、**引数の形がそのまま関数の仕様書になる**のが Elixir らしいところです。`:columns` を持たないマップを渡せば `FunctionClauseError` になり、関数の中に入る前に止まります。

`Enum.count/2` は述語に一致した要素を数えます。`&missing?(&1, column)` はキャプチャ記法で、`fn row -> missing?(row, column) end` の短縮形です。

戻り値をマップではなくタプルのリスト（`[{:がく片長さ, 2}, {:がく片幅, 1}, ...]`）にしているのは、ここでも**順を保つため**です。マップにした瞬間に順が保証されなくなります。

```elixir
test "列ごとに欠損値を数える" do
  assert C.count_missing(table()) == [
           {:がく片長さ, 1},
           {:がく片幅, 0},
           {:花弁長さ, 0},
           {:花弁幅, 0},
           {:種類, 0}
         ]
end
```

期待値がリストとタプルでそのまま書けます。Elixir の項はすべて `==` で構造的に比べられるので、比較のための道具は何も要りません。

## 2.7 平均値で欠損値を補完する

### 平均値を求める

```elixir
@doc "欠損値を除いて、列ごとの平均値を求める。"
def column_means(rows, columns) do
  Map.new(columns, fn column ->
    values = rows |> Enum.map(&number(&1, column)) |> Enum.reject(&is_nil/1)

    if values == [] do
      raise ArgumentError, "値がすべて空欄です: #{column}"
    end

    {column, Enum.sum(values) / length(values)}
  end)
end
```

**`Enum.map(...) |> Enum.reject(&is_nil/1)` が、`Option` の `flatMap` に当たります。** Scala 版の `rows.flatMap(_.number(column))`（`None` を平らにする）、Clojure 版の `keep` と同じ働きを、2 段のパイプラインで書いています。

```elixir
rows |> Enum.map(&number(&1, column)) |> Enum.reject(&is_nil/1)   # Elixir: nil が落ちる
```

```scala
rows.flatMap(_.number(column))                                     // Scala: None が落ちる
```

Clojure の `keep` が 1 語で済ませるところが Elixir では 2 語になりますが、**何が起きているかは 2 語のほうが読めます**。「写してから `nil` を捨てる」と書いてあるとおりです。

戻り値だけはマップにしています。ここは**引く辞書としてしか使わない**ので、順が保証されなくても困りません。「順が要るのはリスト、引くだけならマップ」という使い分けが、この関数の中で同時に出ています。

```elixir
test "欠損値を除いて平均を求める" do
  means = C.column_means(table().rows, [:がく片長さ])
  assert_in_delta means.がく片長さ, 6.05, 1.0e-12
end

test "値がすべて空欄なら平均を求められない" do
  rows = [%{がく片長さ: ""}, %{がく片長さ: " "}]

  assert_raise ArgumentError, "値がすべて空欄です: がく片長さ", fn ->
    C.column_means(rows, [:がく片長さ])
  end
end
```

**`assert_in_delta/3` が、浮動小数点数の比較です。** `assert means.がく片長さ == 6.05` と書くと、`(5.1 + 7.0) / 2` が 2 進数で正確に `6.05` にならない場合に落ちます。ExUnit は許容誤差付きの表明を標準で持っていて、第 1 章の正解率と同じ理由でここでも使います。

2 つめのテストで `" "`（空白 1 つ）を混ぜているのは、`String.trim/1` を通していることの確認です。空白だけのセルも欠損値として扱われます。

### 補完して特徴量にする

```elixir
@doc "欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変えない。"
def fill_missing(rows, columns, fill_values) do
  Enum.map(rows, fn row ->
    Map.new(columns, fn column ->
      {column, number(row, column) || fill_value(fill_values, column)}
    end)
  end)
end
```

`a || b` は「`a` が `nil`（か `false`）なら `b`」です。**`number/2` が `nil` を返したときだけ補完する**、が 1 つの式で書けました。Scala 版の `.getOrElse(...)`、Ruby 版の `||`、Clojure 版の `or` に当たります。

`||` は**短絡評価**です。値があれば `fill_value/2` は呼ばれません。これが次の落とし穴の伏線になります。

### `||` の落とし穴: 補完する値が `nil` なら黙って `nil`

補完する値が無ければ失敗させたい。ここで `Map.get/2` を使うと、静かに壊れます。

```elixir
# 落ちない版（だから危ない）
{column, number(row, column) || Map.get(fill_values, column)}
```

`Map.get/2` は鍵が無ければ `nil` を返します。すると `nil || nil` が `nil` になり、**補完したつもりのセルが `nil` のまま通ります**。例外も警告も出ません。次の工程で「補完済みのはずの特徴量」に `nil` が混ざり、第 3 章の決定木の比較演算で初めて落ちます。**原因から遠い場所で落ちる**、いちばん追いにくい壊れ方です。

これは `||` の性質そのものから来ています。`||` は「`nil` なら次」であって、「次も `nil` なら失敗」ではありません。**`nil` を「無い」の表現に使う言語では、`nil` を返しうる関数を `||` の右側に置いた瞬間に、この穴が開きます。**

そこで、無いことを明示して確かめる関数に分けました。

```elixir
# || では補完する値が nil のときに落ちないので、無いことを明示して確かめる。
defp fill_value(fill_values, column) do
  case Map.fetch(fill_values, column) do
    {:ok, value} -> value
    :error -> raise ArgumentError, "補完する値がありません: #{column}"
  end
end
```

`Map.fetch/2` は「有る」と「無い」を `{:ok, value}` と `:error` で区別します。**値が `nil` であることと、鍵が無いことが、別の結果になります。** `text/2` でも同じ形を使いました。Elixir で `nil` を扱うときの定型で、「`get` で済ませたくなったら `fetch` を疑う」と覚えておく価値があります。

コメントに理由を残したのは、**同じ誤りを繰り返さないため**です。

```elixir
test "指定した値で補完する" do
  filled = C.fill_missing(table().rows, [:がく片長さ], %{がく片長さ: 6.05})
  assert Enum.map(filled, & &1.がく片長さ) == [5.1, 6.05, 7.0]
end

test "補完する値が無ければ失敗する" do
  assert_raise ArgumentError, "補完する値がありません: がく片長さ", fn ->
    C.fill_missing(table().rows, [:がく片長さ], %{})
  end
end
```

「元の行は変えない」はドキュメントに書いていますが、**Elixir では確かめるまでもありません**。マップもリストも不変なので、`fill_missing/3` が入力を書き換える書き方が存在しないからです。それでも書いたのは、ほかの言語版と読み比べたときに「ここは何も手当てしていない」ことが伝わるようにするためです。

## 2.8 特徴量と正解ラベルに分ける

```elixir
@target :種類

@doc "アヤメのデータの正解ラベルの列。"
def target, do: @target

@doc "正解ラベルの列を取り出し、残りの列を特徴量の列にする。"
def split_features_and_target(%{columns: columns, rows: rows}, target_column) do
  %{
    columns: Enum.reject(columns, &(&1 == target_column)),
    rows: rows,
    labels: Enum.map(rows, &text(&1, target_column))
  }
end
```

戻り値を 3 要素のタプルではなくマップにしました。第 1 章では `{x, t}` のタプルを返してパターンマッチで受けましたが、3 つになると位置で覚えるのが辛くなります。**マップで返せば、受ける側が `%{columns: columns, rows: rows, labels: labels}` と名前で取り出せます。** Scala 版はここでタプル `(Vector[String], Vector[Row], Vector[String])` を返していて、型で見分けがつかない同じ型が 2 つ並んでいます。名前で受けるほうが取り違えにくい場面です。

`rows` をそのまま通していることに注意してください。正解ラベルの列は**行から取り除いていません**。取り除くのは「特徴量として扱う列の一覧」からだけで、後の `fill_missing/3` が `columns` の並びだけを見て新しいマップを作るので、余った `:種類` は自然に落ちます。行を削って回るより、**使う列を指定するほうが工程が少ない**、という選択です。

`@target` はモジュール属性で、コンパイル時に値が埋め込まれる定数です。`target/0` という関数で外に公開しているのは、モジュール属性がモジュールの外から見えないためです。

```elixir
test "正解ラベルの列を取り出す" do
  result = C.split_features_and_target(table(), :種類)
  assert result.columns == [:がく片長さ, :がく片幅, :花弁長さ, :花弁幅]
  assert result.labels == ["setosa", "setosa", "versicolor"]
end
```

`result.columns` というドット記法でマップの値を引けます。**アトムをキーにしたマップに限って使える書き方**で、`result[:columns]` より短く、かつ鍵が無ければ `KeyError` になります（`[]` は `nil` を返します）。ここでも「`nil` で黙って通る」か「すぐ落ちる」かの選択があり、テストでは落ちるほうを選びます。

## 2.9 訓練データとテストデータに分ける

### 仮実装から

最初のテストは件数だけを見ます。

```elixir
test "テストデータの割合は切り上げる" do
  x = Enum.map(1..10, &%{値: &1})
  t = Enum.map(1..10, &"ラベル#{&1}")
  split = C.split_train_test(x, t, 0.3, 0)

  assert length(split.x_train) == 7
  assert length(split.x_test) == 3
  assert length(split.t_train) == 7
  assert length(split.t_test) == 3
end
```

`0.3` を掛けて切り上げるので、10 件なら 3 件がテストデータです。

### 三角測量: 対応が崩れないことを確かめる

先頭から順に分ければ件数のテストは通りますが、それでは困ります。iris.csv は品種ごとに並んでいるので、先頭 105 件を取ると訓練データに `Iris-virginica` がほとんど入りません。並べ替えが要ります。並べ替えるなら、特徴量とラベルの対応が崩れないことを確かめなければいけません。

```elixir
test "特徴量とラベルの対応が崩れない" do
  x = Enum.map(1..10, &%{値: &1})
  t = Enum.map(1..10, &"ラベル#{&1}")
  split = C.split_train_test(x, t, 0.3, 0)

  for {features, label} <- Enum.zip(split.x_train, split.t_train) do
    assert label == "ラベル#{features.値}"
  end
end
```

特徴量を `1, 2, 3...`、ラベルをその文字列版にしておくと、**どんな並べ替えをされても「ラベルは特徴量の文字列版」という関係だけは保たれる**はずです。並び順を書き下さずに対応関係だけを確かめる書き方で、`Random.shuffle/2` の実装を変えてもこのテストは通り続けます。

`for ... do ... end` は内包表記で、ここでは 10 回の `assert` を回すために使っています。

### Green: 分割の本体

```elixir
@doc "並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。"
def split_train_test(x, t, test_size, seed) do
  if length(x) != length(t) do
    raise ArgumentError, "件数が違います: #{length(x)} と #{length(t)}"
  end

  shuffled = x |> Enum.zip(t) |> Random.shuffle(seed)
  train_count = length(shuffled) - ceil(length(shuffled) * test_size)
  {train, test} = Enum.split(shuffled, train_count)

  %{
    x_train: Enum.map(train, &elem(&1, 0)),
    x_test: Enum.map(test, &elem(&1, 0)),
    t_train: Enum.map(train, &elem(&1, 1)),
    t_test: Enum.map(test, &elem(&1, 1))
  }
end
```

`Enum.zip(x, t)` が要点です。**2 つのリストを同時にたどって `{特徴量, ラベル}` のタプルを作る**ので、並べ替えても対応が崩れません。Scala 版の `x.zip(t)` と同じで、Clojure 版が `(mapv vector x t)` と書いたところです。タプルを持つ言語では、組にするのがいちばん素直です。

`ceil/1` は Elixir 1.8 以降の組み込み関数で、`Float.ceil/1` と違って整数を返します。`length(shuffled) * test_size` が浮動小数点なので、`Enum.split/2` に渡すには整数にする必要があります。

戻り値は 4 つの鍵を持つマップです。Scala 版は `TrainTestSplit[X, T]` という型引数 2 つのクラスを宣言しましたが、**動的型付けならジェネリクスは要りません**。ここに入るのが整数でも特徴量のマップでも、同じ関数が通ります。上のテストが `%{値: 1}` で済んでいるのはそのおかげです。

```elixir
test "件数が違えば分けられない" do
  assert_raise ArgumentError, "件数が違います: 2 と 1", fn ->
    C.split_train_test([%{}, %{}], ["a"], 0.3, 0)
  end
end
```

件数が違うときの表明を先に置いているのは、`Enum.zip/2` が**短いほうに合わせて黙って切り詰める**からです。表明が無ければ、105 件と 100 件を渡しても 100 件の結果が返り、エラーになりません。**「黙って通る」経路を、例外で塞いでいます。**

## 2.10 前処理をまとめる

```elixir
@doc "iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。"
def prepare_iris(path, test_size, seed) do
  %{columns: columns, rows: rows, labels: labels} =
    path |> load_table() |> split_features_and_target(@target)

  split = split_train_test(rows, labels, test_size, seed)
  means = column_means(split.x_train, columns)

  %{
    split
    | x_train: fill_missing(split.x_train, columns, means),
      x_test: fill_missing(split.x_test, columns, means)
  }
end
```

**順序が大事です。** 分割してから、**訓練データの平均値で**両方を補完しています。先に全体の平均で補完すると、テストデータの情報が訓練データの前処理に混ざります（**データリーク**）。テストデータは「まだ見ていないデータ」のつもりで扱うので、そこから計算した値を学習側に持ち込んではいけません。

`%{split | x_train: ..., x_test: ...}` が**マップの更新構文**です。「`split` の 2 つの鍵だけを差し替えた新しいマップ」を作ります。`:t_train`・`:t_test` はそのまま残るので、書き忘れる余地がありません。しかもこの構文は**既に存在する鍵しか更新できません**。`x_trian` と綴りを間違えれば `KeyError` で落ちます。`Map.put/3` なら新しい鍵が静かに増えるところで、**更新構文は「更新である」ことを実行時に強制します**。動的型付けの言語で得られる、数少ない静的に近い保護です。

`%{columns: columns, rows: rows, labels: labels} = ...` も同じくパターンマッチで、3 つの値を 1 行で取り出しています。

Scala 版は `TrainTestSplit(...)` を 4 引数で組み立て直していて、**変えない 2 つも書く**ことになっていました。素のマップで通す設計が効く場面です。

`prepare_iris/3` の戻り値には、補完済みの特徴量と補完前の行が混ざらないようになっています。ただし**型では区別できません**。`:x_train` に補完前の行が入っていても、Elixir は何も言いません。そこはテストで固定します。

## 2.11 実データで前処理の結果を表示する

### 実データのテスト

実データを使うテストは、第 1 章と同じく `@tag :data` を付けて、データが無ければ外せるようにします。

```elixir
@tag :data
test "アヤメのデータを分割して補完する" do
  path = Path.join(GettingStartedMl.Dataset.dir(), "iris.csv")
  table = C.load_table(path)
  split = C.prepare_iris(path, 0.3, 0)

  assert length(table.rows) == 150
  assert length(split.x_train) == 105
  assert length(split.x_test) == 45

  # 補完した後の訓練データの平均値は Java 版・Scala 版・Clojure 版と一致する
  values = Enum.map(split.x_train, & &1.がく片長さ)
  assert_in_delta Enum.sum(values) / length(values), 0.4215384615384616, 1.0e-15
end
```

**この章の山場のテストです。** 補完後の訓練データの「がく片長さ」の平均値が `0.4215384615384616`。これは Java 版・Scala 版・Clojure 版と、**`1.0e-15` の許容誤差で一致します**。

平均値が一致するということは、**訓練データに入った 105 行がまったく同じ**ということです。2.5 節で写し取った 48 ビットの線形合同法が、JVM とビット単位で同じ数列を出し、Fisher-Yates が同じ交換を行い、同じ行が同じ側に分かれました。

**乱数を自作するという判断が、ここで報われました。** `Nx.Random` を使っていれば、この数値は別のものになり、ほかの言語版の記事と読み比べる意味が半分無くなっていたはずです。

`1.0e-15` という許容誤差は、倍精度浮動小数点数の刻み幅にほぼ等しい値です。**「たまたま近い」では通らない**厳しさで固定しています。

### 結果を表示する

```elixir
@doc "アヤメのデータの前処理の結果を表示する。"
def run do
  path = Path.join(Dataset.dir(), "iris.csv")
  table = load_table(path)
  split = prepare_iris(path, 0.3, 0)

  IO.puts("データ件数: #{length(table.rows)}")

  IO.puts(
    "欠損値の数: " <>
      Enum.map_join(count_missing(table), ", ", fn {column, n} -> "#{column}=#{n}" end)
  )

  IO.puts("訓練データ: #{length(split.x_train)} 件, テストデータ: #{length(split.x_test)} 件")
  IO.puts("特徴量: " <> Enum.map_join(feature_columns(table), ", ", &to_string/1))
end

defp feature_columns(table), do: Enum.reject(table.columns, &(&1 == @target))
```

**特徴量の一覧を `table.columns` から作っているところが、Clojure 版との違いです。** Clojure 版は `(keys (first (:x-train split)))` と、補完済みの特徴量のマップの鍵をそのまま並べていて、「4 つなので偶然に挿入順で出ている」ことを自覚しつつ残していました。Elixir 版はその道を選びません。2.6 節で見たとおりマップの順は保証されないので、**列の順が要る場所では最初から `columns` のリストを使います**。

`Enum.map_join/3` は「写してから連結する」を 1 つにした関数です。`Enum.map(...) |> Enum.join(", ")` と書くのと同じですが、中間のリストを作りません。

実行します。

```text
$ mix run -e "GettingStartedMl.Chapter02.run()"
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
```

テストを走らせます。

```text
$ mix test --include data
49 tests, 0 failures
```

（第 1 章・第 3 章のテストも含めた数です。）

## 2.12 可視化について

Elixir 版には Livebook の節を設けません。欠損値の分布や、品種ごとの特徴量の散布図は、[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と [Kotlin 版の第 2 章](../kotlin/02-data-preprocessing-and-triangulation.md) の「Notebook で探索する」の節を参照してください。訓練データに入る行は言語ごとに違いますが（Elixir 版は Java 版・Scala 版・Clojure 版と同じです）、「`Iris-setosa` は花弁長さ・花弁幅がほかの 2 品種よりはっきり小さい」という傾向は同じで、第 3 章の決定木はこの傾向を自動で見つけます。

## 2.13 まとめ

この章では、乱数生成器を自作し、データフレームのライブラリを使わずに欠損値を含むデータを前処理して、訓練データとテストデータに分けました。Elixir に固有の論点は次のとおりです。

1. **乱数生成器は 3 行の漸化式である** — `java.util.Random` の 48 ビット線形合同法（乗数 `0x5DEECE66D`、加数 11、`bxor` によるシードの撹拌、2 の冪のときだけ別式、剰余の偏りを避ける棄却）を写し取った。結果、シード 0 の並び `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]` と、訓練データの平均値 `0.4215384615384616` が **Java 版・Scala 版・Clojure 版と一致**した。ライブラリが無いから自作したのではなく、**数値を突き合わせるために自作した**（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）
2. **Java の整数の性質は写せない** — 棄却の条件を Java は `int` のオーバーフローで書いているが、Elixir の整数は多倍長でオーバーフローしない。`0x80000000` と直接比べる形に書き換えた。**アルゴリズムを写すとは、コードを貼ることではない**
3. **状態は戻り値で持ち回る** — `next_int/2` が `{値, 次の状態}` を返し、`Enum.reduce` の畳み込みの値が `{配列, 乱数の状態}` になる。可変の変数が無い言語では、変わるものがすべて畳み込みの値に載る
4. **Fisher-Yates はタプルと `put_elem/3`** — リストは添字の更新が苦手なので、いったんタプルに変換する。`put_elem/3` は新しいタプルを返すので**不変性は保たれる**。Clojure 版が可変の配列を使ったところを、Elixir は不変のまま書ける
5. **マップはキーの順を保たない** — 3 件なら挿入順に見えるが、40 件では `[:k23, :k24, :k7, :k35, :k10]` のように崩れる（32 件を超えるとハッシュ表に切り替わる）。Ruby の `Hash` とは違う。だから列の順は CSV から取ったリストで持ち回り、`Csv.parse_table/1` は `{columns, rows}` を返す
6. **`nil` は `Option` の代わりになるが、`||` に穴がある** — `Enum.reject(&is_nil/1)` で「欠損値を除いて集計」は書ける。いっぽう `number(row, column) || Map.get(...)` は補完する値が `nil` のとき黙って `nil` を通す。`Map.fetch/2` で「有る」と「無い」を分け、無ければ例外にした
7. **`%{split | ...}` は既にある鍵しか更新できない** — 綴りを間違えれば `KeyError`。`Map.put/3` なら静かに鍵が増えるところで、**更新構文が「更新である」ことを実行時に強制する**。動的型付けの言語で得られる、数少ない静的に近い保護

**TODO リスト（この章の完了時点）**:

- [x] 乱数生成器を自作する
- [x] 表を読み込む
- [x] 平均値で欠損値を補完する
- [x] 特徴量と正解ラベルに分ける
- [x] 訓練データとテストデータに分ける
- [x] 前処理をまとめる
- [x] 実データで前処理の結果を表示する

次の章では、この訓練データから決定木を自作します。木を表す判別共用体が Elixir には無いので、葉と節をどちらもマップで表すことになります。そして——ほかの言語版と違って——**突き合わせる相手がいません**。Scholar には決定木がないので、自作した木がそのまま最終実装になります。
