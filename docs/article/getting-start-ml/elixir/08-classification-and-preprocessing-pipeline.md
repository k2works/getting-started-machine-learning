---
type: Article
title: "第 8 章: 実践的な分類と前処理パイプライン"
description: "Survived データのグループ別中央値・最頻値の補完とダミー変数化を関数節のパターンマッチで振り分ける前処理にまとめ、クラスの重みを付けた決定木と :erlang.term_to_binary によるモデルの保存・読み込みを Elixir で TDD で実装し、Scholar の SimpleImputer・OneHotEncoder と突き合わせる。"
tags: [article,getting-start-ml,elixir]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:00:00Z }
---

# 第 8 章: 実践的な分類と前処理パイプライン

## 8.1 はじめに

これまでの章のデータは、欠損値が少なく、特徴量もすべて数値でした。現実のデータはそうはいきません。この章で扱うタイタニック号の乗客データには、次の 3 つの難しさがあります。

- **欠損値が多い** — 年齢が 2 割近く欠けている
- **カテゴリ値がある** — 性別や乗船した港が文字列で記録されている
- **クラスが偏っている** — 生存者より死亡者のほうが多い

この章では、これらを 1 つずつ小さな部品として TDD で実装し、前処理からモデルまでを 1 つの **パイプライン** につなぎます。最後にそのパイプラインをファイルに保存し、読み込んで予測できるようにします。保存したモデルは、第 15 章で作る機械学習 API から使います。

[Python 版の第 8 章](../python/08-classification-and-preprocessing-pipeline.md) は、scikit-learn の変換器・`Pipeline`・`DecisionTreeClassifier` の `class_weight` を使いました。Elixir 版には、次の 3 つの違いがあります。

- **前処理を関数節のパターンマッチで振り分ける** — 前処理を「`:type` のキーを持つマップ」にし、`fit_step/2` と `apply_step/2` を `%{type: :group_median}` のようなパターンで呼び分ける。[Clojure 版](../clojure/08-classification-and-preprocessing-pipeline.md) の `defmulti` と似ているが、**節が 1 つのモジュールに並ぶ** ので、どんな前処理があるかはファイルを読めば分かる
- **決定木は自作のまま** — Scholar に決定木が無い（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）ので、第 3 章の決定木を重み付きに書き直したものが最終実装になる。**クラスの重みは Scholar のどこにも口が無い** ので、ここは比べる相手すらいない
- **前処理だけは Scholar と比べられる** — `Scholar.Impute.SimpleImputer` と `Scholar.Preprocessing.OneHotEncoder` がある。ただし 8.12 節で見るとおり、**どちらもこの章で必要なことをしてくれません**。比べると自作が必要な理由がはっきりします
- **モデルは `:erlang.term_to_binary/1` で保存する** — 学習済みのパイプラインがマップ・リスト・アトム・文字列・数値だけでできているので、そのまま往復できる

実装は [Java 版の第 8 章](../java/08-classification-and-preprocessing-pipeline.md)・[Clojure 版の第 8 章](../clojure/08-classification-and-preprocessing-pipeline.md) と同じ乱数・同じ手順で分割するので、正解率や見つけた生存者の数も一致するはずです。8.13 節でそれを確かめます。

## 8.2 題材とデータ

### Survived.csv

`apps/data/sukkiri-ml/Survived.csv` は、タイタニック号の乗客 891 人の記録です。列は次のとおりです。

| 列 | 意味 | 型 |
|----|------|----|
| PassengerId | 乗客の番号 | 数値 |
| Survived | 生存（1）か死亡（0）か | 数値（正解ラベル） |
| Pclass | 客室のクラス（1・2・3） | 数値 |
| Sex | 性別（male・female） | 文字列 |
| Age | 年齢 | 数値（欠損あり） |
| SibSp | 同乗した兄弟・配偶者の数 | 数値 |
| Parch | 同乗した親・子の数 | 数値 |
| Ticket | 切符の番号 | 文字列 |
| Fare | 運賃 | 数値 |
| Cabin | 客室の番号 | 文字列（欠損が多い） |
| Embarked | 乗船した港（C・Q・S） | 文字列（欠損あり） |

### 使う特徴量と、使わない列

`PassengerId`・`Ticket`・`Cabin` は使いません。番号は生存と関係がなく、`Cabin` は 891 人中 687 人で欠けているからです。使うのは 7 列です。

```elixir
@feature_columns [:Pclass, :Sex, :Age, :SibSp, :Parch, :Fare, :Embarked]
```

列名はアトムです。第 2 章で決めたとおり、行は「列名のアトムから文字列へのマップ」で、表は `%{columns: [...], rows: [...]}` です。**列の順は `:columns` のリストが持ち、行のマップのキーの順には頼りません。** この章では前処理でダミー変数が増えて列が 8 つになるので、順を保たないマップに頼っていたら壊れていました。

ここで Elixir の書き方に制約が 1 つ出ます。**大文字で始まるキーは `row.Pclass` と書けません。** ドットのあとが大文字だと、Elixir はモジュールの別名として読もうとし、`invalid alias` というコンパイルエラーになります。この章の列名はすべて大文字で始まるので、値を読むときは第 2 章の `Chapter02.text/2`・`Chapter02.number/2` を通すか、`row[:Pclass]` と書きます。

### 年齢はグループごとの中央値で補完する

年齢を全体の平均や中央値で埋めると、1 等客室の女性も 3 等客室の男性も同じ年齢になってしまいます。客室のクラスと性別の組ごとに中央値を求め、その組の中央値で埋めます。平均ではなく中央値を使うのは、年齢のような偏った分布では極端な値に引かれにくいためです。

## 8.3 TODO リストの作成

**TODO リスト**:

- [ ] 年齢をグループごとの中央値で補完する
  - [ ] グループごとに中央値を求める
  - [ ] 訓練データで求めた中央値を別のデータに使う
  - [ ] 訓練データに無いグループは全体の中央値で補完する
- [ ] 乗船した港を最頻値で補完する
- [ ] カテゴリ値をダミー変数にする
  - [ ] 最初のカテゴリを除く
  - [ ] 別のデータにも同じ列を作る
- [ ] クラスの重みを付けた決定木を作る
  - [ ] 重み付きのジニ不純度
  - [ ] balanced の重み
  - [ ] 重み付けなしなら第 3 章の決定木と同じになる
- [ ] 前処理とモデルをパイプラインにつなぐ
- [ ] モデルを保存して読み込む
- [ ] 正解率と、見つけた生存者の数を求める
- [ ] Scholar の前処理と突き合わせる
- [ ] 実データでクラスの重みの効果を確かめる

ファイルは第 7 章と同じく 1 つです。

| ファイル | モジュール | 中身 |
|---------|-----------|------|
| `lib/getting_started_ml/chapter08.ex` | `GettingStartedMl.Chapter08` | 前処理・決定木・パイプライン・保存・評価・`run` |
| `test/getting_started_ml/chapter08_test.exs` | `GettingStartedMl.Chapter08Test` | 上記のテスト |

Clojure 版は `chapter08/transformers.clj`・`chapter08/tree.clj`・`chapter08.clj` の 3 つに分けました。Elixir でも 3 モジュールに分けられますが、1 つにまとめたのには理由があります。**前処理の振り分けを関数節で書くと、同じモジュールに並んでいないと 1 つの関数にならない** からです。次の節で見ます。

## 8.4 前処理をパターンマッチで振り分ける

前処理には 2 つの段階があります。訓練データから値を求める **fit** と、求めた値でデータを変換する **transform** です。[Scala 版](../scala/08-classification-and-preprocessing-pipeline.md) は `trait Transformer`（fit だけ）と `sealed trait FittedTransformer`（transform だけ）に分け、「fit する前は transform できない」ことを型で保証しました。

Elixir には型の宣言がありません。そこで、**前処理を `:type` のキーを持つマップで表し、`fit_step/2` と `apply_step/2` を引数のパターンで呼び分けます**。

```elixir
def fit_step(%{type: :group_median, column: column, by: by} = transformer, x) do
  …
end

def fit_step(%{type: :most_frequent, column: column} = transformer, x) do
  …
end

def fit_step(%{type: :dummy, columns: columns} = transformer, x) do
  …
end
```

`%{type: :group_median, column: column, by: by} = transformer` は 1 つの引数に 3 つの仕事をさせています。

1. **`:type` が `:group_median` であるかを確かめる**（合わなければこの節は呼ばれない）
2. **`:column` と `:by` の値を同名の変数に取り出す**
3. **マップ全体を `transformer` という名前でも受け取る**（`Map.put/3` で学習した値を足して返すため）

Clojure 版の `defmulti` と比べると、性質がちょうど 3 つの軸で対比できます。

| | Clojure 版（defmulti） | Elixir 版（関数節） | Scala 版（sealed trait） |
|---|---|---|---|
| どんな前処理があるか | 数え上げられない。`defmethod` はどこからでも足せる | **数え上げられないが、節は 1 つのモジュールに並ぶ** ので読めば分かる | コンパイラが数え上げ、`match` の漏れを警告する |
| 前処理を足す | どの名前空間からでも足せる（開いている） | 同じモジュールに節を足す（**閉じている**） | sealed trait のファイルに case class を足す |
| 知らない種類を渡すと | 実行時に「no method」 | 実行時に `FunctionClauseError` | コンパイルエラー |

Elixir はちょうど中間です。**開いてはいないが、閉じていることをコンパイラが使ってはくれません。** 節を足し忘れても、その種類の前処理を実際に使うまで気づきません。そのかわり、前処理の一覧が 1 画面に並ぶので、人間が読んで数え上げられます。この章の前処理は 3 つに固定なので、この形で足ります。

`fit_step/2` が学習した値を「元のマップに `Map.put/3` して返す」形にしておくと、学習前と学習後が同じ `:type` を持つ 1 つのマップになり、`apply_step/2` も同じパターンで書けます。

## 8.5 年齢をグループごとの中央値で補完する

### Red: 最初のテスト

```elixir
defp ages do
  table([:Pclass, :Sex, :Age], [
    ["1", "female", "30"],
    ["1", "female", "40"],
    ["3", "male", "10"],
    ["3", "male", "20"],
    ["3", "male", ""]
  ])
end

test "グループの中央値で欠損値を補完する" do
  fitted = C.fit_step(C.group_median_imputer(:Age, [:Pclass, :Sex]), ages())
  assert values(C.apply_step(fitted, ages()), :Age) == ["30", "40", "10", "20", "15.0"]
end
```

`table/2` と `values/2` は、テストのモジュールに置いた小さな道具です。

```elixir
defp table(columns, rows) do
  %{columns: columns, rows: Enum.map(rows, &Map.new(Enum.zip(columns, &1)))}
end

defp values(t, column), do: Enum.map(t.rows, &Map.fetch!(&1, column))
```

`Map.new(Enum.zip(columns, values))` が「列名と値を組にしてマップにする」ことをそのまま表します。Scala 版・Java 版はテスト用の `Tables`・`Passengers` というクラスを作りましたが、Elixir では 2 つの小さな関数で足ります。

### Green: 中央値と、グループごとの補完

```elixir
@doc "中央値。件数が偶数なら中央の 2 つの平均。"
def median(values) do
  sorted = Enum.sort(values)
  middle = div(length(sorted), 2)

  if rem(length(sorted), 2) == 1 do
    Enum.at(sorted, middle)
  else
    (Enum.at(sorted, middle - 1) + Enum.at(sorted, middle)) / 2
  end
end

def fit_step(%{type: :group_median, column: column, by: by} = transformer, x) do
  known = Enum.reject(x.rows, &Chapter02.missing?(&1, column))

  medians =
    known
    |> Enum.group_by(&group_of(&1, by))
    |> Map.new(fn {group, rows} ->
      {group, median(Enum.map(rows, &Chapter02.number(&1, column)))}
    end)

  transformer
  |> Map.put(:medians, medians)
  |> Map.put(:overall_median, median(Enum.map(known, &Chapter02.number(&1, column))))
end
```

- `Enum.group_by/2` は「関数の値ごとに要素を集めたマップ」を返します。グループのキーは `["1", "female"]` のようなリストです。Elixir のリストは値として等しさが決まるので、**そのままマップのキーにできます**。Java 版は `List<String>` をキーにするために `equals`・`hashCode` の振る舞いに頼り、Kotlin 版は `data class` を作りました
- `Map.new/2` は「マップを組のリストに変換しながら新しいマップを作る」関数です。Clojure 版の `update-vals` にあたるものは標準にありませんが、`Map.new/2` で同じことが書けます
- `transformer |> Map.put(…) |> Map.put(…)` は、学習した 2 つの値を足していくパイプラインです。**マップに新しいキーを足すのが自然な言語** なので、Clojure 版の `assoc` と同じ形になりました

`apply_step/2` は、欠けている行だけを差し替えます。

```elixir
def apply_step(%{type: :group_median, column: column, by: by} = fitted, x) do
  %{
    x
    | rows:
        Enum.map(x.rows, fn row ->
          if Chapter02.missing?(row, column) do
            value = Map.get(fitted.medians, group_of(row, by), fitted.overall_median)
            Map.put(row, column, to_string(value))
          else
            row
          end
        end)
  }
end
```

`Map.get(medians, グループ, overall_median)` の 3 つ目の引数が **既定値** です。訓練データに無いグループが来たら、全体の中央値で補完します。

### 訓練データで求めた値を別のデータに使う

前処理の要点は「**訓練データで求めた値を、テストデータにも使う**」ことです。テストデータから中央値を求めると、テストデータの情報が予測に漏れます。テストで固定します。

```elixir
test "訓練データで求めた中央値を別のデータに使う" do
  fitted = C.fit_step(C.group_median_imputer(:Age, [:Pclass, :Sex]), ages())
  other = table([:Pclass, :Sex, :Age], [["1", "female", ""]])
  assert values(C.apply_step(fitted, other), :Age) == ["35.0"]
end

test "訓練データに無いグループは全体の中央値で補完する" do
  fitted = C.fit_step(C.group_median_imputer(:Age, [:Pclass, :Sex]), ages())
  other = table([:Pclass, :Sex, :Age], [["2", "male", ""]])
  # 欠けていない年齢は 30, 40, 10, 20 なので全体の中央値は 25.0
  assert values(C.apply_step(fitted, other), :Age) == ["25.0"]
end
```

`fit_step/2` の結果を変数に取って別のデータに `apply_step/2` する、という書き方そのものが、この規律を形にしています。

## 8.6 乗船した港を最頻値で補完する

港は文字列なので、中央値ではなく **最頻値**（いちばん多い値）で補完します。

```elixir
def fit_step(%{type: :most_frequent, column: column} = transformer, x) do
  # 値の順に並べ、厳密な不等号で畳むので、同数なら値の順で前のものを選ぶ
  {value, _count} =
    x.rows
    |> Enum.reject(&Chapter02.missing?(&1, column))
    |> Enum.map(&Chapter02.text(&1, column))
    |> Enum.frequencies()
    |> Enum.sort_by(&elem(&1, 0))
    |> Enum.reduce(fn entry, best ->
      if elem(entry, 1) > elem(best, 1), do: entry, else: best
    end)

  Map.put(transformer, :most_frequent, value)
end
```

**同数のときにどちらを選ぶかを、テストで先に固定しました。**

```elixir
test "同数なら値の順で前のものを最頻値にする" do
  fitted = C.fit_step(C.most_frequent_imputer(:Embarked), table([:Embarked], [["S"], ["C"]]))
  assert fitted.most_frequent == "C"
end
```

Clojure 版はここで 1 度落ちました。`max-key` が同数のとき後ろを返すからです。Elixir の `Enum.max_by/2` は同数なら **先** を返すので、`Enum.sort_by/2` で値の順に並べてから `Enum.max_by(&elem(&1, 1))` と書いても同じ結果になります。それでも厳密な不等号で畳む形にしたのは、「同じなら左を残す」と **コードに書いてある** ほうが、あとで読む人に伝わるからです。言語ごとの `max` の同点の扱いを覚えていないと読めないコードは、言語をまたいで結果をそろえたいこの本には向きません。

`Enum.frequencies/1` が返すマップはキーの順を保たないので、`Enum.sort_by/2` で値の順に並べるのは必須です。これを忘れると、実行のたびに結果が変わることはありませんが、Erlang のマップの内部順に結果が左右されます。

## 8.7 カテゴリ値をダミー変数にする

決定木は数値しか扱えないので、`Sex`・`Embarked` を 0 と 1 の列に変えます。カテゴリが n 種類なら列は n - 1 本です（最初のカテゴリを除く）。全部作ると 1 本が残りから決まってしまい、列が重複するためです。

```elixir
test "最初のカテゴリを除いたダミー変数にする" do
  fitted = C.fit_step(C.dummy_encoder([:Sex, :Embarked]), sexes())
  encoded = C.apply_step(fitted, sexes())

  assert encoded.columns == [:Sex_male, :Embarked_Q, :Embarked_S]
  assert values(encoded, :Sex_male) == ["1", "0", "0"]
  assert values(encoded, :Embarked_Q) == ["0", "0", "1"]
  assert values(encoded, :Embarked_S) == ["1", "0", "0"]
end
```

学習した値は「カテゴリの並び」です。

```elixir
def fit_step(%{type: :dummy, columns: columns} = transformer, x) do
  # 学習した値は「{列名, カテゴリの並び} の組のリスト」。マップはキーの順を保たない
  Map.put(
    transformer,
    :dummies,
    Enum.map(columns, fn column -> {column, tl(categories_of(x, column))} end)
  )
end
```

`tl/1` は「先頭を除いた残りのリスト」です。カテゴリを `Enum.sort/1` で並べてあるので、`tl/1` が「最初のカテゴリを除く」をそのまま表します。第 7 章の係数と同じ判断で、**順を保ちたい対応づけはマップにしません。**

変換では、列の並びも作り替えます。

```elixir
def apply_step(%{type: :dummy, dummies: dummies}, x) do
  columns =
    Enum.reduce(dummies, x.columns, fn {column, categories}, columns ->
      Enum.reject(columns, &(&1 == column)) ++
        Enum.map(categories, &dummy_column(column, &1))
    end)

  %{columns: columns, rows: Enum.map(x.rows, &encode_row(&1, dummies))}
end

defp dummy_column(column, category), do: :"#{column}_#{category}"
```

`:"#{column}_#{category}"` は、文字列からアトムを作る書き方です。`:Sex` と `"male"` から `:Sex_male` ができます。**実行時にアトムを作るのは、ふつうは避けるべき** です。アトムはガベージコレクションされず、上限（既定 1048576 個）に達すると VM が落ちるからです。ここで許しているのは、作られるアトムの数が「カテゴリの種類の数」で頭打ちになり、外から与えられた文字列でいくらでも増やせるわけではないからです。**第 15 章の API では、外から来た文字列をそのままアトムにしてはいけません。**

列は次のように変わります。

```text
[:Pclass, :Sex, :Age, :SibSp, :Parch, :Fare, :Embarked]
  → Sex を除いて Sex_male を足す
[:Pclass, :Age, :SibSp, :Parch, :Fare, :Embarked, :Sex_male]
  → Embarked を除いて Embarked_Q と Embarked_S を足す
[:Pclass, :Age, :SibSp, :Parch, :Fare, :Sex_male, :Embarked_Q, :Embarked_S]
```

この **8 列の順が、次の節の決定木が分割の候補を試す順** になります。同じ不純度の分割が複数あるときにどれを選ぶかがここで決まるので、ほかの言語版と同じ順にそろえることが、数値を一致させるための条件です。

別のデータにも訓練データと同じ列ができることも固定します。訓練データに無かったカテゴリの列は、すべて 0 になります。

```elixir
test "別のデータにも訓練データと同じダミー変数の列を作る" do
  fitted = C.fit_step(C.dummy_encoder([:Sex, :Embarked]), sexes())
  encoded = C.apply_step(fitted, table([:Sex, :Embarked], [["female", "S"]]))

  assert encoded.columns == [:Sex_male, :Embarked_Q, :Embarked_S]
  # 大文字で始まる鍵は `row.Sex_male` と書けない（別名として解釈される）ので Map で引く
  assert Enum.map(encoded.rows, &{&1[:Sex_male], &1[:Embarked_Q], &1[:Embarked_S]}) ==
           [{"0", "0", "1"}]
end
```

## 8.8 クラスの重みを付けた決定木を作る

### なぜ自作するのか

Survived.csv は死亡 549 人・生存 342 人と偏っています。普通に学習すると、決定木は「迷ったら死亡」と予測しがちで、助かった人を見落とします。scikit-learn には `class_weight: "balanced"` がありますが、**Scholar には決定木そのものがありません**（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。第 3 章で作った決定木を「1 件ごとの重み」を通す形に書き直します。ここは Scholar に無いので、自作が最終実装になります。

### 重み付きのジニ不純度

第 3 章のジニ不純度は「ラベルの件数の割合」で計算しました。重み付きでは、件数の代わりに **重みの合計** で割合を求めます。

```elixir
@doc "重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。"
def weighted_gini(labels, weights) do
  total = Enum.sum(weights)

  1.0 -
    Enum.sum(
      Enum.map(weight_sums(labels, weights), fn {_label, weight} ->
        weight / total * (weight / total)
      end)
    )
end
```

`weight_sums/2` は「ラベルごとの重みの合計」を、**ラベルが先に現れた順のリスト** で返します。

```elixir
defp weight_sums(labels, weights) do
  sums =
    Enum.zip_reduce(labels, weights, %{}, fn label, weight, acc ->
      Map.update(acc, label, weight, &(&1 + weight))
    end)

  labels |> Enum.uniq() |> Enum.map(&{&1, sums[&1]})
end
```

集計そのものはマップで行い、**順が必要になる最後だけ `Enum.uniq/1` でリストに戻します**。`Enum.uniq/1` は「最初に現れた順で重複を除く」ので、これがそのまま「ラベルが先に現れた順」になります。Clojure 版はマップを使わずリストを線形に探しましたが、Elixir では第 3 章の `majority/1` で同じ手（集計はマップ、順は `Enum.uniq/1`）を使っているので、そろえました。

`Map.update/4` は「キーがあれば関数をかけ、無ければ既定値を入れる」関数です。`Enum.zip_reduce/4` が 2 つのリストを同時に畳むので、組のリストを作らずに済みます。

不純度そのものは順に左右されませんが、**同じ重みのときにどのラベルを葉にするか** は順で決まります。そこがほかの言語版と一致する条件です。

```elixir
test "重みがすべて一なら第三章のジニ不純度と同じになる" do
  labels = [0, 0, 1, 1, 1]

  assert_in_delta C.weighted_gini(labels, List.duplicate(1.0, 5)),
                  Chapter03.gini(labels),
                  1.0e-12
end

test "重みの合計が同じなら先に現れたラベルを選ぶ" do
  assert C.weighted_majority([0, 1], [1.0, 1.0]) == 0
  assert C.weighted_majority([1, 0], [1.0, 1.0]) == 1
end
```

1 本目は「新しい実装が古い実装を含む」ことを固定するテストです。第 3 章の `Chapter03.gini/1` をそのまま参照できるので、書き直しの安全網になります。

### balanced の重み

`balanced` は、クラスの件数に反比例する重みです。

```elixir
def balanced_weights(t) do
  counts = Enum.frequencies(t)
  Enum.map(t, &(length(t) / (map_size(counts) * counts[&1])))
end

def weights_of(t, :none), do: List.duplicate(1.0, length(t))
def weights_of(t, :balanced), do: balanced_weights(t)
def weights_of(_t, class_weight), do: raise(ArgumentError, "知らない重みの付け方です: #{class_weight}")
```

重みの付け方はアトム（`:none`・`:balanced`）で表します。Scala 版の `enum ClassWeight`、Java 版の `enum` にあたりますが、**アトムなので「知らない値」を渡せます**。3 つ目の節がそれを受けて失敗させます。節の順が意味を持つ書き方で、`:none`・`:balanced` の節を最後に置いたら、すべてが 3 つ目に吸われて動かなくなります。**受け皿の節はいちばん下に置く** のが決まりです。

```elixir
test "知らない重みの付け方は使えない" do
  assert_raise ArgumentError, fn -> C.weights_of([0, 1], :unknown) end
end
```

`Enum.frequencies/1` が返すマップは順を保ちませんが、ここでは `counts[&1]` と **キーで引くだけ** なので順は関係ありません。「順に頼っているかどうか」を場面ごとに確かめるのが、動的型付けの言語でこの本の数値をそろえるためのいちばんの勘所です。

### 重み付けなしなら第 3 章の決定木と同じ

重みをすべて 1 にしたときに第 3 章と同じ木になることを、小さなデータで固定します。

```elixir
test "重み付けなしなら第三章の決定木と同じ木になる" do
  x = [%{a: 1.0, b: 1.0}, %{a: 2.0, b: 1.0}, %{a: 3.0, b: 2.0}, %{a: 4.0, b: 2.0}]
  t = [0, 0, 1, 1]
  ours = C.fit_tree(x, t, [:a, :b], 2, :none)
  theirs = Chapter03.fit(x, Enum.map(t, &to_string/1), [:a, :b], 2)

  assert Enum.map(C.predict_tree(ours, x), &to_string/1) == Chapter03.predict(theirs, x)
end
```

第 3 章の決定木はラベルが文字列、この章は整数（0 と 1）なので、比べるときに `to_string/1` で合わせます。整数のままにしたのは、`Survived` が 0 か 1 の 2 値で、生存者の数を数えるときに整数のほうが素直だからです。

### balanced で予測が変わる

```elixir
test "balanced にすると少数派のラベルを予測しやすくなる" do
  # 1 が 2 件、0 が 5 件。深さ 1 の木では、重みを付けないとどの葉も多数派の 0 になる
  x = Enum.map(1..7, &%{a: &1 * 1.0})
  t = [0, 0, 1, 0, 1, 0, 0]

  assert C.predict_tree(C.fit_tree(x, t, [:a], 1, :none), x) == [0, 0, 0, 0, 0, 0, 0]
  assert C.predict_tree(C.fit_tree(x, t, [:a], 1, :balanced), x) == [1, 1, 1, 1, 1, 0, 0]
end
```

重みを付けないと、深さ 1 の木ではどちらの葉も多数派の 0 になり、少数派を 1 件も当てられません。`balanced` にすると少数派の重みが 2.5 倍になり、左の葉が 1 になります。

小さなデータでも「どこで分割されるか」は手で追いにくいので、**実際に動かして観察してから期待値を決め、そのうえでその値を固定する** という進め方をしています。予測ではなく観察に基づく期待値なので、TDD の Red としては弱い形ですが、木の形そのものを後から変えないための杭にはなります。

## 8.9 前処理とモデルをパイプラインにつなぐ

### 前処理の順番と fit の順番

パイプラインは、前処理の並びとモデルの設定を持つマップです。

```elixir
def build_pipeline(max_depth, class_weight) do
  %{
    transformers: [
      group_median_imputer(:Age, [:Pclass, :Sex]),
      most_frequent_imputer(:Embarked),
      dummy_encoder([:Sex, :Embarked])
    ],
    max_depth: max_depth,
    class_weight: class_weight
  }
end
```

学習では、**前の前処理で変換したデータで次の前処理を fit します**。ダミー変数化は「港を補完したあとのデータ」で fit しなければ、欠損値がカテゴリとして混ざってしまいます。

```elixir
def fit(pipeline, x, t) do
  {fitted, prepared} =
    Enum.reduce(pipeline.transformers, {[], x}, fn transformer, {fitted, prepared} ->
      step = fit_step(transformer, prepared)
      {fitted ++ [step], apply_step(step, prepared)}
    end)

  %{
    transformers: fitted,
    columns: prepared.columns,
    tree:
      fit_tree(
        to_features(prepared),
        t,
        prepared.columns,
        pipeline.max_depth,
        pipeline.class_weight
      )
  }
end
```

`Enum.reduce/3` の状態を「学習済みの前処理のリスト」と「変換済みのデータ」の組にしています。Scala 版の `foldLeft`・Clojure 版の `reduce` と同じ形です。

`fitted ++ [step]` はリストの末尾に足す書き方で、連結リストとしては効率が悪い（左辺の長さに比例する）操作です。ふつうは `[step | fitted]` で先頭に足して最後に `Enum.reverse/1` します。ここで末尾に足しているのは、**前処理が 3 つしかなく、順の意図が読んで分かるほうが大事** だからです。効率のための書き方を反射で選ばず、規模に見合うかどうかで決めます。

学習済みのパイプラインが `:columns` を持つのは Clojure 版と同じ都合です。決定木が分割の候補を試す順は列の並びで決まるので、**予測のときにも学習したときと同じ並びを使う** 必要があります。

### 予測では apply_step だけを使う

```elixir
def transform(fitted_pipeline, x) do
  Enum.reduce(fitted_pipeline.transformers, x, &apply_step/2)
end
```

`&apply_step/2` の 1 つで済みます。`Enum.reduce/3` が渡す引数の順（要素、積算値）と `apply_step(fitted, x)` の引数の順が偶然そろっているからです。**関数の引数の順を、畳み込みに乗る形にそろえておく** と、こういう場面で無名関数が消えます。Elixir の標準ライブラリが「データを第 1 引数に」という規約を持つのとは逆向きですが、ここは「学習済みの前処理を第 1 引数に」でパターンマッチを書きたいので、こちらを優先しました。

### 前処理が済んだ表を特徴量にする

```elixir
def to_features(x) do
  Enum.map(x.rows, fn row -> Map.new(x.columns, &feature_of(row, &1)) end)
end

defp feature_of(row, column) do
  case Chapter02.number(row, column) do
    nil -> raise ArgumentError, "欠損値が残っています: #{column}"
    value -> {column, value}
  end
end
```

Scala 版は「補完の結果が `Features` 型である」ことで欠損値が無いことを保証しました。Elixir では型がないので、ここで数えて確かめます。`Chapter02.number/2` が欠損値に `nil` を返す規約と、`case` の 2 つの節がそのまま噛み合います。

```elixir
test "欠損値が残っていれば特徴量にできない" do
  assert_raise ArgumentError, fn -> C.to_features(%{columns: [:Age], rows: [%{Age: ""}]}) end
end
```

### 欠損値を含むデータで学習して予測する

パイプライン全体を、年齢と港が欠けた小さなデータで確かめます。

```elixir
test "前処理の順に学習して欠損値の残らない特徴量にする" do
  fitted = C.fit(C.build_pipeline(3, :none), train_x(), train_t())

  assert fitted.columns == [:Pclass, :Age, :SibSp, :Parch, :Fare, :Sex_male, :Embarked_S]

  assert Enum.all?(C.features(fitted, train_x()), fn row ->
           Enum.all?(fitted.columns, &is_number(Map.fetch!(row, &1)))
         end)
end

test "学習済みのパイプラインで欠損値を含むデータを予測する" do
  fitted = C.fit(C.build_pipeline(3, :none), train_x(), train_t())
  assert C.predict(fitted, new_passengers()) == [1, 0]
end
```

年齢が空欄の架空の乗客 2 人（1 等客室の女性と 3 等客室の男性）でも、前処理が年齢と港を埋めてから木に渡すので、予測できます。

## 8.10 モデルを保存して読み込む

### マップとリストなら、そのまま書ける

学習済みのパイプラインは、マップ・リスト・アトム・文字列・数値だけでできています。BEAM にはこれをそのままバイト列にする関数が最初からあります。

```elixir
def save_model(fitted_pipeline, path) do
  File.mkdir_p!(Path.dirname(path))
  File.write!(path, :erlang.term_to_binary(Map.put(fitted_pipeline, :format, @format_version)))
end

def load_model(path) do
  loaded =
    try do
      :erlang.binary_to_term(File.read!(path), [:safe])
    rescue
      ArgumentError -> reraise ArgumentError, [message: "モデルとして読めません: #{path}"], __STACKTRACE__
    end

  case loaded do
    %{format: @format_version} -> Map.delete(loaded, :format)
    %{format: version} -> raise ArgumentError, "対応していない形式のモデルです: #{inspect(version)}"
    _ -> raise ArgumentError, "モデルとして読めません: #{path}"
  end
end
```

**`[:safe]` を渡すのが要点です。** 素の `:erlang.binary_to_term/1` は、ファイルに書かれたアトムを **その場で作ります**。信頼できないファイルを読ませると、アトム表を埋めて VM を落とせます。無名関数やプロセスの参照を復元させることもできます。`[:safe]` を付けると、既に存在するアトムしか作らず、危険な項を拒みます。Java 版・Kotlin 版が Java シリアライズの危険に対して `ObjectInputFilter` でクラスを絞ったのと、Clojure 版が `clojure.core/read-string` ではなく `clojure.edn/read-string` を選んだのと、同じ問題への 3 つ目の答えです。

`reraise/3` は、`rescue` で捕まえた例外を **元のスタックトレースのまま** 投げ直します。`raise` で投げ直すとスタックトレースがここから始まってしまい、どこで壊れたかが分からなくなります。

`@format_version` をパターンに書いているので、`%{format: @format_version}` の節は「版がちょうど 1 のとき」だけ通ります。版の比較が `if` ではなくパターンで書けるのは、モジュール属性がコンパイル時に値に展開されるからです。

### 保存したものは目で読める

保存する前の学習済みパイプラインは、8.9 節の小さな訓練データではこうなっています。

```elixir
%{
  tree: %{
    split: %{feature: :Pclass, threshold: 2.0, impurity: 0.0},
    left: %{label: 1},
    right: %{label: 0}
  },
  columns: [:Pclass, :Age, :SibSp, :Parch, :Fare, :Sex_male, :Embarked_S],
  transformers: [
    %{
      type: :group_median,
      column: :Age,
      by: [:Pclass, :Sex],
      medians: %{["1", "female"] => 35.0, ["3", "male"] => 20.0},
      overall_median: 27.5
    },
    %{type: :most_frequent, column: :Embarked, most_frequent: "S"},
    %{
      type: :dummy,
      columns: [:Sex, :Embarked],
      dummies: [Sex: ["male"], Embarked: ["S"]]
    }
  ]
}
```

前処理が学習した中央値も最頻値もカテゴリも、木の境界も、そのまま読めます。`dummies: [Sex: ["male"], Embarked: ["S"]]` が `[{:Sex, ["male"]}, {:Embarked, ["S"]}]` の別の書き方（キーワードリスト）なのは、`{アトム, 値}` の組のリストを Elixir がそう表示するためです。**順を保ちたいのでリストにした** という判断が、表示にも出ています。

ファイルは 562 バイトのバイナリで、先頭は `<<131, 116, 0, 0, 0, 4, …>>` です（`131` が External Term Format の版、`116` がマップ、`0, 0, 0, 4` がキーの数）。Clojure 版の EDN と違ってテキストではありませんが、**読み込めば構造がそのまま戻る** という点は同じで、Scala 版が 100 行ほど書いた書き出しと読み取りが要りません。

### 往復することをテストする

```elixir
test "保存して読み込んだパイプラインは同じ予測をする" do
  fitted = C.fit(C.build_pipeline(3, :balanced), train_x(), train_t())

  in_tmp("survived", fn path ->
    C.save_model(fitted, path)
    assert C.load_model(path) == fitted

    assert C.predict(C.load_model(path), new_passengers()) ==
             C.predict(fitted, new_passengers())
  end)
end
```

`assert C.load_model(path) == fitted` の 1 行で、**パイプライン全体が値として等しいこと** を確かめています。Scala 版・Java 版は「予測が一致すること」で間接的に確かめました。Elixir ではモデルがただのマップなので、直接比べられます。関数やプロセスの参照を含んでいたら、この比較は書けませんでした。

形式の版と、壊れたファイルもテストで固定します。

```elixir
test "形式の版が違うモデルは読み込めない" do
  in_tmp("broken", fn path ->
    File.write!(path, :erlang.term_to_binary(%{format: 999}))
    assert_raise ArgumentError, fn -> C.load_model(path) end
  end)
end

test "壊れたファイルは読み込めない" do
  in_tmp("garbage", fn path ->
    File.write!(path, "これはモデルではありません")
    assert_raise ArgumentError, fn -> C.load_model(path) end
  end)
end
```

保存先はテストごとに一時ディレクトリの別名にし、`after` で消します。

```elixir
defp in_tmp(name, fun) do
  path =
    Path.join(System.tmp_dir!(), "#{name}-#{System.unique_integer([:positive])}.model")

  try do
    fun.(path)
  after
    File.rm(path)
  end
end
```

`System.unique_integer([:positive])` は、**同じ VM の中で重複しない整数** を返します。ExUnit は既定でテストを並行に走らせる（`async: true`）ので、ファイル名が衝突しないことが要ります。時刻を使うと、同じミリ秒に 2 つのテストが走ったときに衝突します。

## 8.11 評価する

正解率だけでは、クラスの重みの効果が見えません。「テストデータの生存者のうち、何人を生存と予測できたか」も数えます。

```elixir
def evaluate(fitted_pipeline, split) do
  predictions = predict(fitted_pipeline, features_table(split.x_test))
  labels = split.t_test

  %{
    train_accuracy:
      accuracy(predict(fitted_pipeline, features_table(split.x_train)), split.t_train),
    test_accuracy: accuracy(predictions, labels),
    found_survivors:
      Enum.count(Enum.zip(predictions, labels), fn {p, l} ->
        p == @survived and l == @survived
      end),
    survivors: Enum.count(labels, &(&1 == @survived))
  }
end
```

評価の結果もマップなので、テストの表明は 1 つです。

```elixir
test "正解率と見つけた生存者の数を求める" do
  fitted = C.fit(C.build_pipeline(3, :none), train_x(), train_t())

  split = %{
    x_train: train_x().rows,
    t_train: train_t(),
    x_test: new_passengers().rows,
    t_test: [1, 1]
  }

  assert C.evaluate(fitted, split) == %{
           train_accuracy: 1.0,
           test_accuracy: 0.5,
           found_survivors: 1,
           survivors: 2
         }
end
```

Scala 版は `case class Evaluation` を、Java 版は `record` を作って同じことを書きました。Elixir ではマップリテラルがそのまま期待値になります。落ちたときは、ExUnit が **食い違うキーだけを色分けして並べて見せてくれます**。Clojure 版が「どのキーが違うかは自分で見比べることになる」と書いていたところが、道具側で解決されています。

## 8.12 Scholar の前処理と突き合わせる

Scholar には `Scholar.Impute.SimpleImputer` と `Scholar.Preprocessing.OneHotEncoder` があります。この章の前処理を置き換えられるでしょうか。**結論から言うと、どちらも置き換えられません。** ただ、比べてみることで自作が必要な理由がはっきりします。

### SimpleImputer は列ごとの 1 つの値しか持てない

```elixir
test "SimpleImputer の中央値は自作の全体の中央値と一致する" do
  fitted = C.fit_step(C.group_median_imputer(:Age, [:Pclass, :Sex]), ages())

  imputer =
    SimpleImputer.fit(
      Nx.tensor([[30.0], [40.0], [10.0], [20.0], [:nan]], type: :f64),
      strategy: :median
    )

  assert_in_delta Nx.to_number(imputer.statistics),
                  fitted.overall_median,
                  1.0e-12
end
```

`SimpleImputer` の中央値（25.0）は、自作の `overall_median`（25.0）と一致します。**「全体の中央値」の計算そのものは同じ** です。ところが、この章に必要なのはグループごとの中央値です。

```elixir
test "SimpleImputer にはグループ別の補完が無いので値が変わる" do
  …
  # 自作は 3 等客室の男性の中央値 15.0 で埋めるが、SimpleImputer は全体の中央値 25.0 で埋める
  assert Nx.to_number(imputed[4][0]) == 25.0
end
```

`SimpleImputer` は列ごとに 1 つの統計量しか持ちません。「`Pclass` と `Sex` の組ごとに別の中央値」は表せないので、8.2 節で挙げた「1 等客室の女性も 3 等客室の男性も同じ年齢になる」問題がそのまま起きます。scikit-learn にも `SimpleImputer` にはこの機能が無く、Python 版は `groupby().transform()` で自分で書いていました。**ライブラリの有無ではなく、そもそもこの前処理がライブラリの守備範囲の外** だということです。

同数のときの決め方は一致しました。

```elixir
test "SimpleImputer の最頻値は同数なら小さいほうを選ぶ" do
  imputer =
    SimpleImputer.fit(
      Nx.tensor([[0.0], [1.0], [:nan]], type: :f64),
      strategy: :mode
    )

  # 自作の most_frequent_imputer も、同数ならカテゴリの順で前のものを選ぶ
  assert Nx.to_number(imputer.statistics) == 0.0
end
```

ドキュメントに「同じ値が複数あれば最も小さいものだけを返す」と書いてあり、そのとおりでした。自作の 8.6 節の決め方（値の順で前のもの）と方針がそろっているので、カテゴリを順に整数へ符号化すれば同じ結果になります。**同点の決め方が同じかどうかを、ライブラリを採るかどうかの判断材料にできる** ことが分かりました。

### OneHotEncoder は最初のカテゴリを落とさない

```elixir
test "OneHotEncoder は最初のカテゴリを落とさない" do
  fitted = C.fit_step(C.dummy_encoder([:Sex]), sexes())
  mine = values(C.apply_step(fitted, sexes()), :Sex_male)

  # male を 1、female を 0 に符号化してから渡す
  codes = Nx.tensor([1, 0, 0])
  encoder = OneHotEncoder.fit(codes, num_categories: 2)
  theirs = OneHotEncoder.transform(encoder, codes)

  assert Nx.to_flat_list(theirs[[.., 1]]) == Enum.map(mine, &String.to_integer/1)
  assert Nx.shape(theirs) == {3, 2}
end
```

`OneHotEncoder` は 2 列（`{3, 2}`）を返し、自作は 1 列（`Sex_male`）です。**2 列目だけを取れば自作と一致します。** つまり「符号化のしかた」は同じで、違うのは「最初のカテゴリを落とすかどうか」だけです。scikit-learn の `drop="first"` にあたる選択肢が Scholar にはありません。

そのほかに 3 つの制約があります。

| 制約 | 内容 |
|------|------|
| 1 次元のテンソルしか取らない | `{4, 1}` を渡すと `expected input tensor to have shape {num_samples}` で失敗する。列ごとに呼ぶことになる |
| 整数の符号しか取らない | `"male"`・`"female"` をそのまま渡せない。先に整数に直す前処理（`OrdinalEncoder`）が要る |
| 学習時に無かった値を変換できない | 訓練データに無かったカテゴリがテストデータに来ると失敗する。自作は「全部 0」にして通す |

3 つ目は、8.7 節でテストに固定した「別のデータにも訓練データと同じ列を作る」の振る舞いと真っ向から違います。ライブラリの立場では「知らない値を黙って 0 にするのは危ない」という判断で、こちらの方針が甘いとも言えます。**どちらが正しいかではなく、方針が違うことを知って選ぶ** ところです。この本では、Java 版・Clojure 版と数値をそろえることを優先して自作を使います。

### 何が置き換えられて、何が置き換えられないのか

| 前処理 | Scholar | この章の判断 |
|--------|---------|------------|
| 全体の中央値・最頻値 | `SimpleImputer`（一致する） | **使わない**。必要なのはグループ別なので |
| グループ別の中央値 | 無い | 自作 |
| ダミー変数化 | `OneHotEncoder`（2 列目が一致する） | **使わない**。最初のカテゴリを落とせず、未知の値で失敗するので |
| 決定木 | 無い | 自作 |
| クラスの重み | 無い | 自作 |

第 7 章は「ライブラリがあるので行列を自作しない」章でした。この章は逆で、**5 つのうち 3 つはライブラリに無く、残る 2 つも要件に合わない** という結果になりました。それでも比べたことには意味があります。「無いから自作した」ではなく「**あるものを確かめたうえで、この要件には合わないから自作した**」と言えるようになり、その判断がテストとして残ったからです。ライブラリが育てば、このテストが乗り換えの判断材料になります。

## 8.13 実データでクラスの重みの効果を確かめる

### 実行する

```console
$ mix run -e 'GettingStartedMl.Chapter08.run()'
データ件数: 891（生存 342, 死亡 549）
訓練データ: 712 件, テストデータ: 179 件
classWeight=none: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見
classWeight=balanced: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見
保存したモデル: 読み込めました
架空の乗客の予測: [1, 0]
```

深さ 5 の木で、`balanced` にすると正解率はわずかに上がり（0.799 → 0.804）、見つけた生存者は 59 人から 65 人に増えました。年齢の分からない架空の乗客 2 人（1 等客室の女性と 3 等客室の男性）は、生存・死亡と予測されました。

### ほかの言語版と数値が一致するか

分割は第 2 章で `java.util.Random` と同じ線形合同法と Fisher-Yates にそろえてあるので、Java 版・Scala 版・Clojure 版とまったく同じ行が訓練データとテストデータに入ります。実測した結果は次のとおりで、**すべて一致しました**。

| 指標 | Elixir 版 | Java 版・Scala 版・Clojure 版 |
|------|----------|---------------------------|
| データ件数（生存・死亡） | 891（342・549） | 891（342・549） |
| 訓練・テストの件数 | 712・179 | 712・179 |
| 深さ 5・重み付けなしの正解率（訓練・テスト） | 0.854・0.799 | 0.854・0.799 |
| 深さ 5・balanced の正解率（訓練・テスト） | 0.848・0.804 | 0.848・0.804 |
| 深さ 5 で見つけた生存者（79 人中） | 59 人 → 65 人 | 59 人 → 65 人 |
| 深さ 2 で見つけた生存者（79 人中） | 41 人 → 68 人 | 41 人 → 68 人 |
| 架空の乗客 2 人の予測 | `[1, 0]` | 同じ |

Kotlin 版は `kotlin.random.Random` を使うので分割が違い、深さ 5 では `balanced` が見落としを減らしませんでした。同じアルゴリズムでも、1 回の分割の結果から「この設定のほうが良い」と一般化してはいけない、ということです。

一致には、次の 3 つの「同点のときの決め方」がすべてそろっている必要がありました。どれか 1 つでもずれると、深さの深い木で予測が食い違います。

| 場面 | 決め方 | Elixir での書き方 |
|------|-------|-----------------|
| 最頻値が同数 | 値の順で前のもの | `Enum.sort_by/2` してから `>` で畳む |
| 分割の不純度が同じ | 列の順で前のもの | `Enum.flat_map/2` で列の順に候補を並べ、`Enum.min_by/2` で選ぶ（同値なら先を返す） |
| 葉の重みの合計が同じ | 先に現れたラベル | 集計はマップで行い、`Enum.uniq/1` で最初に現れた順に戻す |

`Enum.min_by/2` と `Enum.max_by/2` が同値のとき **先** を返すのは、Elixir の決まりです。Clojure の `max-key` は後ろを返すので、Clojure 版はここで 1 度落ちました。**言語を移すときに真っ先に確かめるべき点** です。

### 効果は深さによって変わる

深さ 2 の木では、効果がはっきり出ます。

```elixir
@tag :data
test "深さ二では balanced にすると見つけられる生存者が四十一人から六十八人に増える" do
  split = survived_split()

  assert C.evaluate(fit_pipeline(split, 2, :none), split).found_survivors == 41
  assert C.evaluate(fit_pipeline(split, 2, :balanced), split).found_survivors == 68
end
```

浅い木は葉が大きく、多数派に引きずられやすいので、重みを変えた効果が出やすくなります。深い木では葉が小さくなり、重みを付けなくても少数のクラスだけの葉ができるので、差は小さくなります。

### 第 3 章の決定木との突き合わせ

Scholar に決定木が無いので、突き合わせる相手は **第 3 章の自作の決定木** しかいません。前処理の結果を第 3 章の決定木にも渡し、重み付けなしなら同じ予測になることを、深さ 1〜10 のすべてで確かめます。

```elixir
@tag :data
test "重み付けなしなら前処理後のテストデータで第三章の決定木と予測が一致する" do
  split = survived_split()

  for max_depth <- 1..10 do
    pipeline = fit_pipeline(split, max_depth, :none)
    x_train = C.features(pipeline, C.features_table(split.x_train))
    x_test = C.features(pipeline, C.features_table(split.x_test))

    theirs =
      Chapter03.predict(
        Chapter03.fit(
          x_train,
          Enum.map(split.t_train, &to_string/1),
          pipeline.columns,
          max_depth
        ),
        x_test
      )

    mine = Enum.map(C.predict(pipeline, C.features_table(split.x_test)), &to_string/1)
    assert mine == theirs, "深さ #{max_depth}"
  end
end
```

Clojure 版・Java 版は、ここで Tribuo の CART とも突き合わせ、「予測が違うのは深さ 5 で 2 件と深さ 9 で 1 件だけ」という 10 個の数字を杭にしていました。Elixir 版にはその相手がいません。**外部の実装と照らせないぶん、テストの支えは 2 本になります。**

1. 第 3 章の決定木と、深さ 1〜10 のすべてで予測が一致すること（この節）
2. 実データの正解率と生存者の数が、Java 版・Scala 版・Clojure 版と一致すること（前の節）

2 本目が実質的に Tribuo との突き合わせの代わりになっています。Java 版の数値が Tribuo と照らして検証されており、その数値と Elixir 版が一致するので、**間接的に外部の実装と照らしたことになる** という理屈です。言語版を横断して同じ数値を出すことに投資してきた効果が、ライブラリの無い言語でいちばん効いています。

`assert mine == theirs, "深さ #{max_depth}"` の 2 つ目の引数は、失敗したときのメッセージです。`for` の中で 10 回表明するので、どの深さで落ちたかが分かるようにしています。

### 実データのテスト

実データを使うテストは、第 3 章・第 7 章と同じく `@tag :data` を付けます。`test/test_helper.exs` がデータのディレクトリの有無を見て、無ければこのタグを除外します。

```console
$ ML_DATA_DIR=/nonexistent mix test
学習データが見つからないので :data のテストを外します（/nonexistent）
102 tests, 0 failures, 9 excluded
```

ExUnit は「9 件を除外した」と数えて表示します。Clojure 版が「表明が 0 個のテストとして成功に数えられるので、走ったかどうかを結果から区別できない」と書いていたところが、道具側の機能で解決されています。

## 8.14 品質チェック

```console
$ mix format --check-formatted && mix compile --warnings-as-errors && mix credo --strict && mix test --cover
Checking 14 source files ...
Analysis took 0.9 seconds (0.08s to load, 0.8s running 69 checks on 14 files)
168 mods/funs, found no issues.
…
102 tests, 0 failures
```

```text
Percentage | Module
-----------|--------------------------
    96.20% | GettingStartedMl.Chapter08
```

### Credo に入れ子の深さで 2 回止められた

この章で Credo が止めてくれたのは、どちらも **関数の入れ子が深すぎる**（`Credo.Check.Refactor.Nesting`、既定の上限は 2）でした。

```console
[F] → Function body is nested too deep (max depth is 2, was 4).
      lib/getting_started_ml/chapter08.ex:152:15 #(GettingStartedMl.Chapter08.apply_step)
[F] → Function body is nested too deep (max depth is 2, was 3).
      lib/getting_started_ml/chapter08.ex:246:9 #(GettingStartedMl.Chapter08.to_features)
```

1 つ目は、ダミー変数化の `apply_step/2` を `Enum.map` の中に `Enum.reduce` を 2 つ入れ子にして書いたところです。`encode_row/2` と `encode_column/4` に切り出しました。2 つ目は `to_features/1` で、`Enum.map` の中に `Map.new` を置き、さらにその中に `case` を書いていたところです。`feature_of/2` に切り出しました。

どちらも、切り出したあとのほうが読めます。**「行を符号化する」「1 つのセルを数値にする」という名前が付いた** からです。入れ子の深さは、名前を付けそこねた処理の数を数えているようなものでした。Clojure 版の cljfmt が「式が読めなくなっているのが本当の問題」を字下げの指摘として教えてくれたのと、同じ働き方をしています。

もう 1 つ、テストの `4 / (2 * 1)` という式が `Credo.Check.Warning.OperationOnSameValues` に引っかかりました。`balanced` の重みの定義（件数 ÷ (クラスの数 × そのクラスの件数)）をそのまま書いて見せたかったのですが、`* 1` は意味がないと指摘されたので `4 / 2` に直しました。定義を見せたいなら、式ではなくコメントで書くべきところでした。

## 8.15 探索と可視化

クラス分布、性別・客室クラス別の生存率、木の深さとクラスの重み、混同行列、分割に使われた特徴量の探索と可視化は、[Python 版の 8.13 節](../python/08-classification-and-preprocessing-pipeline.md) と [Kotlin 版の 8.13 節](../kotlin/08-classification-and-preprocessing-pipeline.md) を参照してください。Elixir 版では、深さごとの結果を 8.13 節の表とテストにまとめました。

## 8.16 まとめ

この章では、欠損値・カテゴリ値・クラスの偏りを含む現実的なデータで、前処理からモデルの保存までを TDD で実装しました。

1. **前処理はデータ、振り分けは関数節** — `:type` を持つマップにして `fit_step/2`・`apply_step/2` をパターンで呼び分けた。Clojure 版の `defmulti` のように開いてはいないが、コンパイラが漏れを数えてもくれない。**節が 1 画面に並ぶので人間が数え上げる** という、動的型付けの言語らしい落とし所になった
2. **1 つの引数に 3 つの仕事をさせる** — `%{type: :group_median, column: column, by: by} = transformer` が、種類の判定・値の取り出し・全体の束縛を同時に行う。Elixir のパターンマッチがいちばん効く場面
3. **データだから保存が 2 行で済む** — 学習済みのパイプラインがマップとリストだけなので、`:erlang.term_to_binary/1` と `:erlang.binary_to_term/2` で往復できた。読み込みには **必ず `[:safe]` を付ける**。アトム表を埋められる危険は、Java シリアライズの危険と同じ種類のもの
4. **モデルを値として比べられる** — 保存と読み込みのテストが `assert C.load_model(path) == fitted` の 1 行になった
5. **順を保ちたい対応づけはリストで持つ** — 列の並び・ダミーのカテゴリ・ラベルごとの重みの合計。集計はマップで行い、順が要る最後だけ `Enum.uniq/1` でリストに戻す。学習済みのパイプラインが `:columns` を持つのもこのため
6. **アトムは作りすぎない** — ダミー変数の列名は実行時に作るアトムだが、カテゴリの種類の数で頭打ちになる。外から来た文字列をアトムにしてはいけないことは、第 15 章の API で効いてくる
7. **Scholar の前処理は使えなかった** — `SimpleImputer` はグループ別の中央値を持てず、`OneHotEncoder` は最初のカテゴリを落とせず未知の値で失敗する。それでも比べたことで、「無いから自作した」ではなく「**確かめたうえで要件に合わないから自作した**」と言えるようになった
8. **突き合わせの相手がほかの言語版になった** — Scholar に決定木が無いので、Tribuo と照らす Java 版・Clojure 版のような検証ができない。そのかわり、Java 版と同じ数値（0.854・0.799・59 人・65 人・41 人・68 人）を出すことで、間接的に外部の実装と照らした

第 15 章の API は、この章の `build_pipeline/2` で学習して `save_model/2` で保存したパイプラインを `load_model/1` で読み込み、`features_table/1` で作った表を `predict/2` に渡して予測します。公開している入口は次の 5 つです。

| 入口 | 役割 |
|-----|------|
| `feature_columns/0` / `features_table/1` / `target_labels/1` | 特徴量の列の表と正解ラベルを作る |
| `build_pipeline/2` | この章の前処理とモデルの並びを作る |
| `fit/3` | 訓練データで学習する |
| `save_model/2` / `load_model/1` | 学習済みのパイプラインを保存・読み込みする |
| `predict/2` / `features/2` | 前処理をしてから予測する／前処理だけを行う |

次の章では、住宅価格のデータを使い、標準化や多項式特徴量など、特徴量そのものを作り変える方法を学びます。第 7 章で「Scholar を正しく動かすための下ごしらえ」として出てきた標準化が、そこで主役になります。
