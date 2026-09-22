---
type: Article
title: "第 8 章: 実践的な分類と前処理パイプライン"
description: "タイタニックの生存予測を題材に、グループ中央値・最頻値による補完とダミー変数化を Data とダックタイピングで自作し、第 3 章の木を使い回したクラスの重み付きの決定木とつないでパイプラインにする。学習済みモデルは Marshal で保存し、Rumale に補完・重みが無いことと、シードで決定木の結果が変わることを実測で確かめる。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:22:46Z }
---

# 第 8 章: 実践的な分類と前処理パイプライン

## 8.1 はじめに

第 3 章では、きれいに整ったアヤメのデータで決定木を作りました。この章では、**実務に近い汚れたデータ** を扱います。題材はタイタニック号の乗客データで、乗客の属性から生死を予測します。

この章で新しく出てくるのは 4 つです。

1. **欠損値の賢い補完** — 年齢は「客室等級と性別のグループごとの中央値」、乗船した港は「最頻値」で埋める
2. **カテゴリ値のダミー変数化** — `male`・`female` のような文字列を 0 と 1 の列にする
3. **クラスの重み** — 死亡者のほうが多い偏ったデータで、少数派の生存者を見つけられるようにする
4. **前処理のパイプライン** — 上の前処理とモデルを 1 つにまとめ、ファイルに保存して再利用する

[ADR 010](../../../adr/010-ruby-ml-libraries.md) では、「欠損値の補完・ダミー変数化が Rumale にあるかは、この章で確かめる」としていました。確かめた結果、**Rumale には欠損値の補完が無く、ダミー変数化も文字列のカテゴリを受け取りません**。決定木にはクラスの重みもありません。この章の前処理と重み付きの決定木は **自作が最終的な実装** です。Rumale と突き合わせられるのは、重みを付けない決定木の部分だけです。

もう 1 つの主題は **学習済みモデルの保存** です。[Rust 版](../rust/08-classification-and-preprocessing-pipeline.md) は serde で JSON にし、「trait object は保存できないので列挙型にする」という制約に設計を合わせました。Ruby 版は標準ライブラリの `Marshal` で、オブジェクトをそのままバイト列にします。制約はほとんどありません。その代わりに何を引き受けるのかを見ていきます。

## 8.2 題材とデータ

### Survived.csv

891 人分の乗客データです。この章で使う列は次のとおりです。

| 列 | 内容 | 欠損値 |
|----|------|-------|
| Survived | 生死（1 が生存、0 が死亡）。正解ラベル | なし |
| Pclass | 客室の等級（1・2・3） | なし |
| Sex | 性別（male・female） | なし |
| Age | 年齢 | 177 件 |
| SibSp | 同乗した兄弟・配偶者の数 | なし |
| Parch | 同乗した親・子の数 | なし |
| Fare | 運賃 | なし |
| Embarked | 乗船した港（C・Q・S） | 2 件 |

`PassengerId`・`Name`・`Ticket`・`Cabin` は使いません。ID と名前は乗客を区別するためのもの、`Cabin` は欠損が多すぎるからです。

このファイルは **BOM 付き** で始まります。第 1 章で確かめたとおり、`CSV.read` はファイルを開くときに BOM を取り除くので、第 2 章の `Table.load` がそのまま使えます。

### 年齢はグループごとの中央値で補完する

第 2 章では、欠損値を列全体の平均値で埋めました。この章ではもう一歩進めます。年齢は客室の等級と性別で傾向が違う（1 等客室の乗客のほうが年上）ので、**同じ等級・同じ性別のグループの中央値** で埋めます。平均ではなく中央値なのは、極端な値に引っ張られないためです。

港は文字列なので平均も中央値も取れません。いちばん多い値（最頻値）で埋めます。

## 8.3 TODO リストの作成

**TODO リスト**:

- [ ] Rumale の前処理で足りるかを確かめる
- [ ] 特徴量の列と正解ラベルを決める
- [ ] 年齢をグループごとの中央値で補完する
  - [ ] グループを指定しなければ全体の中央値で埋める
  - [ ] グループごとに違う中央値で埋める
  - [ ] 訓練データで求めた中央値を別のデータに使う
  - [ ] 訓練データに無いグループは全体の中央値で埋める
  - [ ] 元の表を書き換えない
- [ ] 乗船した港を最頻値で補完する
- [ ] カテゴリ値をダミー変数にする
  - [ ] 最初のカテゴリを除いた列を作る
  - [ ] 別のデータにも訓練データと同じ列を作る
- [ ] クラスの重みを付けた決定木を作る
  - [ ] 重み付きのジニ不純度
  - [ ] balanced の重み
- [ ] 前処理とモデルをパイプラインにつなぐ
- [ ] 学習済みのパイプラインを保存して読み込む
- [ ] 評価する（正解率と、見つけられた生存者の数）
- [ ] 実データでクラスの重みの効果を確かめる

## 8.4 Rumale の前処理で足りるかを確かめる

Rumale 2.2 の前処理（`Rumale::Preprocessing`）にあるものを一覧にしました。

```ruby
p Rumale::Preprocessing.constants.sort
```

```text
[:BinDiscretizer, :Binarizer, :KernelCalculator, :L1Normalizer, :L2Normalizer, :LabelBinarizer, :LabelEncoder, :MaxAbsScaler, :MaxNormalizer, :MinMaxScaler, :OneHotEncoder, :OrdinalEncoder, :PolynomialFeatures, :StandardScaler, :VERSION]
```

欠損値の補完（scikit-learn の `SimpleImputer` に当たるもの）はありません。そもそも Rumale は `Numo::DFloat` の行列を受け取るので、「空欄」を表す手段がありません。

ダミー変数化には `OneHotEncoder` があります。試すと、整数のカテゴリを受け取り、**すべてのカテゴリの列** を作ります。

```ruby
enc = Rumale::Preprocessing::OneHotEncoder.new
p enc.fit_transform(Numo::Int32[[0], [1], [2], [1]]).to_a
```

```text
[[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0], [0.0, 1.0, 0.0]]
```

`male`・`female` のような文字列をそのまま渡す手段はなく、`OrdinalEncoder` に文字列の配列の配列（`[["male"], ["female"]]`）を渡すと、`NoMethodError` で次のメッセージになりました。

```text
undefined method `shape' for an instance of Array
```

文字列を番号にしてから渡し、最初のカテゴリの列を落とす処理を足すなら、自作のほうが短くなります。

決定木の `DecisionTreeClassifier` が受け取るのは次のキーワード引数だけで、クラスの重みはありません。

```text
[[:key, :criterion], [:key, :max_depth], [:key, :max_leaf_nodes], [:key, :min_samples_leaf], [:key, :max_features], [:key, :random_seed]]
```

結論として、補完・ダミー変数化・クラスの重みはすべて自作します。

## 8.5 前処理をどう表すか

### Rust 版の列挙型を Ruby に移すと

Rust 版は、学習前の前処理を `enum Step`、学習済みの前処理を `enum FittedStep` で表しました。JSON に保存するために、「どの型に戻すか」をコンパイル時に決めておく必要があったからです。

Ruby 版は、前処理の種類ごとに `Data` を 2 つずつ作ります。学習前の `GroupMedian` と学習済みの `FittedGroupMedian` です。

```ruby
# 数値の列の欠損値を、同じグループ（by の列の値の組）の中央値で補完する。
GroupMedian = Data.define(:column, :by) do
  # グループごとの中央値と全体の中央値を求める。グループは値の組の配列をそのまま Hash の鍵にする。
  def fit(x)
    present = x.rows.reject { |row| row.missing?(column) }
    medians = present.group_by { |row| Chapter08.group_of(row, by) }
                     .transform_values { |rows| Chapter08.column_median(rows, column) }

    FittedGroupMedian.new(column:, by:, medians:, overall: Chapter08.column_median(present, column))
  end
end

# 学習済みの GroupMedian。訓練データに無いグループは全体の中央値で埋める。
FittedGroupMedian = Data.define(:column, :by, :medians, :overall) do
  def transform(x)
    Chapter08.fill(x, column) { |row| medians.fetch(Chapter08.group_of(row, by), overall).to_s }
  end
end
```

`MostFrequent`・`Dummy` も同じ形です。3 種類の前処理に **共通の親クラスもインターフェースもありません**。どれも「`fit` を持ち、学習済みの前処理を返す」「学習済みの前処理は `transform` を持つ」というだけで、パイプラインはそのメソッドを呼ぶだけです。これが **ダックタイピング** です。

| | 前処理の種類の表し方 | 種類を足すとき | 呼び間違いを見つけるのは |
|---|---|---|---|
| Rust 版 | 列挙型と `match` | 列挙型に列挙子を足し、`match` を全部直す（直し漏れはコンパイルエラー） | コンパイラ |
| Ruby 版 | `Data` とダックタイピング | `fit`・`transform` を持つ `Data` を足すだけ | テスト（`NoMethodError` は実行時） |

学習前と学習済みを別の型にしたのは、Rust 版と同じ理由です。「`fit` する前に `transform` する」誤りが、学習前の `GroupMedian` には `transform` が無いので、呼んだ時点の `NoMethodError` になります。Rust 版はそれをコンパイル時に止めましたが、Ruby 版では実行時です。

### グループをそのまま Hash の鍵にする

`medians` は、グループ（客室等級と性別の値の配列）を鍵にした `Hash` です。実データの訓練データで学習すると、次の中身になりました。

```text
{["3", "male"]=>25.0, ["3", "female"]=>21.0, ["1", "female"]=>33.0, ["1", "male"]=>45.0, ["2", "female"]=>28.0, ["2", "male"]=>29.0}
```

Rust 版では、JSON のオブジェクトの鍵が文字列に限られるので、グループを鍵にした対応表を「組のベクタ」に詰め替える必要がありました。**Ruby の `Hash` は配列を鍵にでき、Marshal はそれをそのまま保存できます**。`group_by` と `transform_values` の 2 行で書けるのは、この自由さのおかげです。

鍵の配列は文字列のままです（`"3"` と `"male"`）。セルを数値に変換せずに使うので、`"3"` と `"3.0"` は別のグループになります。この章のデータでは `Pclass` がいつも整数の表記なので問題になりませんが、型を書かない言語では、こうした「同じに見えて違う鍵」に気を配る必要があります。

## 8.6 年齢をグループごとの中央値で補完する

### Red: まず全体の中央値で

`test/chapter08_test.rb` に、テストから書きます。

```ruby
def test_欠損した年齢を全体の中央値で埋める
  x = passengers(%w[1 female 10], %w[1 female 20], ["1", "female", ""])

  assert_in_delta 15.0, age_step.fit(x).transform(x).rows[2].number("Age"), 1e-12
end
```

`age_step` は、グループの列を省略すると空の配列にするヘルパーです。

```ruby
def age_step(group_columns = [])
  C::GroupMedian.new(column: "Age", by: group_columns)
end
```

グループの列が空なら、すべての行が「空の配列」という 1 つのグループに入るので、全体の中央値で埋めることになります。特別扱いの分岐は要りません。

まだ `Chapter08` が無いので、読み込んだ時点で止まります。

```text
test/chapter08_test.rb:7:in `<class:Chapter08Test>': uninitialized constant GettingStartedMl::Chapter08 (NameError)
```

### Green: 中央値と、欠損値の埋め方

```ruby
# 中央値。件数が偶数なら中央の 2 つの平均。値が無ければ ArgumentError を投げる。
def median(values, column)
  raise ArgumentError, "値がすべて空欄です: #{column}" if values.empty?

  sorted = values.sort
  middle = sorted.size / 2
  sorted.size.odd? ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0
end

# 欠損値を、行ごとにブロックが返す値で埋める。元の行は変えず、埋めた行だけ新しく作る。
def fill(x, column)
  x.with(rows: x.rows.map do |row|
    row.missing?(column) ? Chapter02::Row.new(row.cells.merge(column => yield(row))) : row
  end)
end
```

`fill` は、埋める値をブロックで受け取ります。`GroupMedian` は「行のグループの中央値」、`MostFrequent` は「いつも同じ最頻値」を返すブロックを渡します。Rust 版の `fill` はクロージャ（`impl Fn(&Row) -> Result<String>`）を受け取りましたが、Ruby ではブロックと `yield` で同じことを書けます。

`Row` のセルは文字列なので、中央値は `to_s` で文字列に戻してから入れます。`15.0.to_s` は `"15.0"` で、第 2 章の `Row#number` の `Float(cell)` で読み戻せます。

`row.cells.merge(...)` は新しい `Hash` を返すので、元の行は変わりません。TODO の「元の表を書き換えない」をテストで固定しておきます。

```ruby
def test_補完しても元の表は変わらない
  x = passengers(%w[1 female 10], ["1", "female", ""])
  age_step.fit(x).transform(x)

  assert x.rows[1].missing?("Age")
end
```

### 三角測量: グループごとに違う中央値と、訓練データに無いグループ

```ruby
def test_グループごとに違う中央値で埋める
  x = passengers(%w[1 female 10], %w[1 female 20], %w[3 male 40], %w[3 male 60],
                 ["3", "male", ""], ["1", "female", ""])
  filled = age_step(%w[Pclass Sex]).fit(x).transform(x)

  assert_equal([50.0, 15.0], filled.rows.last(2).map { |row| row.number("Age") })
end

def test_訓練データに無いグループは全体の中央値で埋める
  train = passengers(%w[1 female 10], %w[1 female 30])
  test = passengers(["3", "male", ""])

  assert_in_delta 20.0, age_step(%w[Pclass Sex]).fit(train).transform(test).rows[0].number("Age"), 1e-12
end
```

訓練データに無いグループは、`medians.fetch(group, overall)` の第 2 引数（見つからなかったときの値）で全体の中央値になります。`Hash#fetch` の既定値 1 つで、Rust 版の `find(...).map_or(overall, ...)` に当たる処理が済みます。

## 8.7 乗船した港を最頻値で補完する

```ruby
# 文字列の列の欠損値を、訓練データで最も多い値（最頻値）で補完する。
MostFrequent = Data.define(:column) do
  # 同数なら先に現れた値を選ぶ。tally は現れた順を保ち、max_by は同点なら最初の要素を返す。
  def fit(x)
    values = x.rows.reject { |row| row.missing?(column) }.map { |row| row.text(column) }
    raise ArgumentError, "値がすべて空欄です: #{column}" if values.empty?

    FittedMostFrequent.new(column:, most_frequent: values.tally.max_by { |_value, count| count }.first)
  end
end
```

「同数なら先に現れた値」は、第 3 章の `majority` と同じ性質を使っています。`tally` は最初に現れた順に数え、`max_by` は同点のとき最初の要素を返します。Rust 版では `max_by_key` が逆（最後の要素）を返すので、畳み込みを自分で書く必要がありました。テストで固定します。

```ruby
def test_最頻値が同数なら先に現れた値を選ぶ
  x = table(["Embarked"], ["C"], ["S"])

  assert_equal "C", C::MostFrequent.new(column: "Embarked").fit(x).most_frequent
end
```

## 8.8 カテゴリ値をダミー変数にする

ダミー変数は、カテゴリの数から 1 を引いた本数の 0／1 の列です。性別なら `Sex_male` の 1 列で、`female` は `Sex_male=0` として表せます。最初のカテゴリを落とすのは、残りの列から分かる情報を重ねて持たないためです（pandas の `get_dummies(drop_first=True)` と同じ）。

```ruby
# カテゴリ値の列を、並べ替えて最初のカテゴリを除いた 0 と 1 の列（ダミー変数）にする。
Dummy = Data.define(:columns) do
  def fit(x)
    FittedDummy.new(dummies: columns.to_h { |column| [column, Chapter08.categories(x, column).drop(1)] })
  end
end

# 学習済みの Dummy。元の列を除き、ダミー変数の列を末尾に足す。
FittedDummy = Data.define(:dummies) do
  def transform(x)
    x.with(columns: (x.columns - dummies.keys) + Chapter08.dummy_columns(dummies),
           rows: x.rows.map { |row| Chapter02::Row.new(row.cells.merge(Chapter08.flags(row, dummies))) })
  end
end
```

`x.columns - dummies.keys` は配列の差で、元の `Sex`・`Embarked` の列を除きます。行のセルには元の列が残りますが、表の `columns` に無い列は特徴量にしないので使われません。

ダミー変数にする列は **訓練データで決めて覚えておきます**。テストデータに `S` の乗客しかいなくても、訓練データと同じ `Embarked_Q`・`Embarked_S` の 2 列を作ります。

```ruby
def test_別のデータにも訓練データと同じダミー変数の列を作る
  train = table(["Embarked"], ["C"], ["Q"], ["S"])
  encoded = C::Dummy.new(columns: ["Embarked"]).fit(train).transform(table(["Embarked"], ["S"]))

  assert_equal %w[Embarked_Q Embarked_S], encoded.columns
  assert_equal([0.0, 1.0], encoded.columns.map { |column| encoded.rows[0].number(column) })
end
```

`FittedDummy#transform` は、はじめ列名を作る処理も中に書いていましたが、`Metrics/AbcSize` が 17.03 で上限をわずかに超えたので、`dummy_columns` に切り出しました。

## 8.9 クラスの重みを付けた決定木を作る

### なぜ自作するのか

Survived.csv は、生存 342 人・死亡 549 人と偏っています。ふつうの決定木は多数派（死亡）に寄った予測をしがちです。scikit-learn の `DecisionTreeClassifier(class_weight="balanced")` は、少数派の 1 件に大きな重みを付けて、この偏りを打ち消します。8.4 節で見たとおり、Rumale の決定木にはこの引数がありません。

### 第 3 章の木をそのまま使う

重み付きの決定木を作るにあたって、Rust 版は「ラベルが整数であることと、JSON に保存するために serde の導出が要ることから、第 3 章の `Tree` を使い回さない」としました。Ruby 版は **第 3 章の `Leaf`・`Node`・`Split` と `predict_one` をそのまま使います**。

```ruby
# 第 8 章のクラスの重みを付けた決定木。第 3 章の決定木に 1 件ごとの重みを足したもの。
# 木の型（Leaf・Node・Split）と予測は第 3 章のものをそのまま使う。ラベルが整数でも型を変えずに済む。
```

第 3 章の `Leaf` は `Data.define(:label)` で、`label` の型を決めていません。アヤメの種類（文字列）でも、生死（整数）でも入ります。Marshal は名前の付いた `Data` のクラスなら何でも保存できるので、保存のために型を作り直す必要もありません。

使い回しは木の型だけではありません。分割の候補を探すときに値の順に並べる `sort_by_feature` も第 3 章のものです。第 3 章では（値, ラベル）の組を並べていましたが、ラベルの代わりに **（ラベル, 重み）の組** を渡すと、重みも一緒に並んで出てきます。

```ruby
# 1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。
# 第 3 章の sort_by_feature に（ラベル, 重み）の組を渡して、重みも一緒に並べ替える。
def candidates(x, labeled, feature)
  sorted = Chapter03.sort_by_feature(x, labeled, feature)

  (1...sorted.size).filter_map { |index| split_at(sorted, index, feature) }
end
```

`sort_by_feature` は第 3 章で「正解ラベルの配列」として書いたものですが、中身を調べずに並べ替えて返すだけなので、何を渡しても動きます。型の宣言が無いので、**書いた人の意図より広く使える** のです。逆に言えば、どこまで広く使ってよいかは、テストが守っている範囲で判断するしかありません。

### 重み付きのジニ不純度

第 3 章のジニ不純度は「ラベルの件数の割合」から求めました。重み付きでは、件数の代わりに **重みの合計の割合** を使います。

```ruby
# ラベルごとの重みの合計。Hash は先に現れたラベルの順を保つ。
def weight_sums(labels, weights)
  labels.zip(weights).each_with_object(Hash.new(0.0)) { |(label, weight), sums| sums[label] += weight }
end

# 重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。
def weighted_gini(labels, weights)
  total = weights.sum
  return 0.0 if total.zero?

  1.0 - weight_sums(labels, weights).values.sum { |weight| (weight / total)**2 }
end
```

重みがすべて 1 なら、第 3 章のジニ不純度と一致します。重みが偏ると、不純度も偏ります。

```ruby
def test_重みが等しければ普通のジニ不純度になる
  assert_in_delta 0.5, C.weighted_gini([0, 1], [1.0, 1.0]), 1e-12
end

def test_重みが偏ると不純度も偏る
  assert_in_delta 0.375, C.weighted_gini([0, 1], [3.0, 1.0]), 1e-12
end
```

`[0, 1]` に重み `[3, 1]` なら、割合は 0.75 と 0.25 で、1 − (0.5625 + 0.0625) = 0.375 です。

### balanced の重み

scikit-learn の `balanced` と同じ式で、1 件ごとの重みを「件数 ÷（クラスの数 × そのクラスの件数）」にします。

```ruby
# クラスの件数に反比例する重み（件数 ÷（クラスの数 × そのクラスの件数））を 1 件ごとに求める。
def balanced_weights(t)
  counts = t.tally
  t.map { |label| t.size.fdiv(counts.size * counts[label]) }
end

# クラスの重みの付け方から、1 件ごとの重みを求める。
def weights_of(t, class_weight)
  case class_weight
  when :none then Array.new(t.size, 1.0)
  when :balanced then balanced_weights(t)
  else raise ArgumentError, "クラスの重みの付け方が違います: #{class_weight}"
  end
end
```

重みの付け方はシンボル（`:none`・`:balanced`）で表しました。Rust 版は列挙型で、知らない値はコンパイルが通りませんでした。Ruby 版では `:heavy` のような綴り違いも渡せてしまうので、`else` で `ArgumentError` を投げ、テストで固定します。

```ruby
def test_知らない重みの付け方は使えない
  error = assert_raises(ArgumentError) { C.weights_of([0, 1], :heavy) }

  assert_equal "クラスの重みの付け方が違います: heavy", error.message
end
```

`else` を書かなければ、`case` は `nil` を返し、そのあとの `weights.sum` で `NoMethodError` になります。**失敗はするが、原因から遠い場所で失敗する** のが、型の検査が無い言語の典型的な壊れ方です。

## 8.10 前処理とモデルをパイプラインにつなぐ

```ruby
# 学習前のパイプライン。前処理を順に fit・transform してから、モデルを学習する。
Pipeline = Data.define(:steps, :model) do
  # Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。
  def self.build(max_depth:, class_weight:)
    new(steps: [GroupMedian.new(column: "Age", by: %w[Pclass Sex]),
                MostFrequent.new(column: "Embarked"),
                Dummy.new(columns: %w[Sex Embarked])],
        model: DecisionTreeClassifier.new(max_depth:, class_weight:))
  end

  # 訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで fit する。
  # Data は属性を差し替えられないだけで、中の分類器は書き換えられる。学び直しで前の結果を
  # 壊さないように、分類器を複製してから学習する。
  def fit(x, t)
    prepared = x
    fitted = steps.map do |step|
      step.fit(prepared).tap { |fitted_step| prepared = fitted_step.transform(prepared) }
    end

    FittedPipeline.new(steps: fitted, model: model.dup.fit(Chapter08.to_features(prepared), t))
  end
end

# 学習済みのパイプライン。予測するときは前処理の transform だけを使う。
FittedPipeline = Data.define(:steps, :model) do
  # 学習済みの前処理を順に適用する。
  def transform(x)
    steps.reduce(x) { |prepared, step| step.transform(prepared) }
  end
```

`fit` の中では、各前処理を **前の前処理で変換したデータで** `fit` します。ダミー変数化は、港を最頻値で埋めた後のデータで学習しないと、空欄をカテゴリの 1 つとして数えてしまうからです。`tap` はブロックを実行してから元の値（ここでは学習済みの前処理）を返すので、`map` の結果が学習済みの前処理の配列になります。

`transform` は `reduce` 1 行です。Rust 版は「クロージャは保存できないので関数合成を使わず `for` ループにした」と書きましたが、Ruby 版の `FittedPipeline` が持つのは前処理の配列で、合成した関数ではありません。どちらでも保存できますが、配列にしておくほうが、保存したものを覗いたときに中身が読めます。

### 詰まった点: `Data` の中の分類器は書き換えられる

最初の `fit` は `model.fit(...)` と書いていました。`DecisionTreeClassifier#fit` は自分に木を覚えさせて `self` を返すので、これで学習済みの分類器が手に入ります。

テストを足していて、この書き方が危ないことに気づきました。同じパイプラインで 2 回学習すると、1 回目の学習結果が書き換わってしまいます。

```ruby
def test_同じパイプラインで学び直しても前の学習済みモデルは変わらない
  rows = passengers
  pipeline = C::Pipeline.build(max_depth: 2, class_weight: :none)
  first = pipeline.fit(C::Survived.features(rows), C::Survived.target(rows))
  tree = first.model.tree
  pipeline.fit(C::Survived.features(rows.first(2)), [1, 1])

  assert_equal tree, first.model.tree
end
```

```text
  1) Failure:
Chapter08PipelineTest#test_同じパイプラインで学び直しても前の学習済みモデルは変わらない [test/chapter08_pipeline_test.rb:67]:
--- expected
+++ actual
@@ -1 +1 @@
-#<data GettingStartedMl::Chapter03::Node split=#<data GettingStartedMl::Chapter03::Split feature="Pclass", threshold=2.0, impurity=0.0>, left=#<data GettingStartedMl::Chapter03::Leaf label=1>, right=#<data GettingStartedMl::Chapter03::Leaf label=0>>
+#<data GettingStartedMl::Chapter03::Leaf label=1>
```

1 回目の学習済みパイプライン `first` の木が、2 回目の学習の木（生存の葉 1 枚）に置き換わっています。`Pipeline` も `FittedPipeline` も `Data` なので不変だと思い込んでいましたが、**`Data` が不変なのは属性の差し替えだけ** です。属性が指している分類器の中身は、その分類器のメソッドで書き換えられます。`Pipeline` の `model` と `first.model` は同じオブジェクトだったので、2 回目の学習がそのまま 1 回目の結果を上書きしました。

直し方は `model.dup.fit(...)` で、学習のたびに分類器を複製することです。Rust 版では、`fit(&self, ...)` が `&self`（書き換えない借用）を受け取り、別の型 `FittedDecisionTree` を返す設計だったので、そもそもこの誤りを書けませんでした。Ruby 版では、**書き換えの有無は型に現れない** ので、「学び直しても前の結果が変わらない」ことをテストに書いて初めて守れます。

### 欠損値が残れば特徴量にしない

```ruby
# 前処理の済んだ表を特徴量にする。欠損値が残っていれば ArgumentError を投げる。
def to_features(x)
  x.rows.map do |row|
    values = x.columns.map { |column| row.number(column) || raise(ArgumentError, "値が空欄です: #{column}") }
    Chapter02::Features.new(columns: x.columns, values:)
  end
end
```

前処理の組み合わせを間違えて欠損値が残ったら、ここで止まります。`nil` のまま決定木に渡ると、比較の `nil <= 1.5` で `NoMethodError` になり、原因から遠い場所で失敗します。

## 8.11 モデルを保存して読み込む

### Marshal でそのまま保存する

```ruby
# 学習済みのパイプラインを Marshal で保存し、読み込む。
#
# Marshal は Data・Hash・クラスのインスタンスをそのままバイト列にできるので、変換のコードが要らない。
# その代わり、読み込むとバイト列に書かれたクラスのオブジェクトが作られる。信頼できないファイルは読まない。
module ModelFile
  module_function

  # パイプライン全体（前処理で求めた値とモデル）を保存する。置き場のディレクトリが無ければ作る。
  def save(pipeline, model_file)
    FileUtils.mkdir_p(File.dirname(model_file))
    File.binwrite(model_file, Marshal.dump(pipeline))
  end

  # 保存したパイプラインを読み込む。学習済みのパイプラインでなければ TypeError を投げる。
  def load(model_file)
    pipeline = Marshal.load(File.binread(model_file)) # rubocop:disable Security/MarshalLoad
    raise TypeError, "学習済みのパイプラインではありません: #{pipeline.class}" unless pipeline.is_a?(FittedPipeline)

    pipeline
  end
end
```

`Marshal.dump` に `FittedPipeline` を渡すだけで、前処理の `Data`、配列を鍵にした `Hash`、`DecisionTreeClassifier` のインスタンス、第 3 章の木まで、まるごとバイト列になります。Rust 版で必要だった `#[derive(Serialize, Deserialize)]` も、列挙型への作り直しも要りません。実データで学習したパイプラインは 2197 バイトでした。

保存できないものもあります。**名前の無いクラス** です。`Data.define(:a)` を定数に代入せずに使うと、次のようになります。

```text
#<TypeError: can't dump anonymous class #<Class:0x000000010efa00a0>>
```

Marshal はクラスを名前で記録し、読み込むときにその名前でクラスを探します。この章の `Data` はすべて定数に代入しているので保存できます。

### Marshal.load の危うさ

`Marshal.load` には `# rubocop:disable Security/MarshalLoad` を付けました。付けないと RuboCop が次のように咎めます。

```text
W: Security/MarshalLoad: Avoid using Marshal.load.
```

Marshal はバイト列に書かれたクラスのオブジェクトを作ります。細工したファイルを読み込むと、読み込んだ側のプログラムにあるクラスを組み合わせて、意図しない処理を起こされることがあります。Rust 版の serde は「この型に戻す」と書いてあるので、知らない形のデータは型に合わずに失敗するだけでした。Java 版で必要だった「読み込むクラスの制限」に当たるものは、Marshal には標準でありません。

この章では、**自分で保存したファイルだけを読む** 前提で `Marshal.load` を使い、その判断をコメントと RuboCop の抑止の両方に残しました。読み込んだ後に `FittedPipeline` かどうかも確かめます。ただしこの確認は、読み込んだ **後** の検査です。読み込みの途中で起きることは防げません。第 15 章の API のように外から受け取るデータには、Marshal ではなく JSON を使います（[ADR 010](../../../adr/010-ruby-ml-libraries.md)）。

### 保存して読み込めることをテストする

```ruby
def test_保存して読み込むと同じパイプラインになる
  Dir.mktmpdir do |dir|
    model_file = File.join(dir, "model", "survived.dump")
    C::ModelFile.save(fitted, model_file)

    assert_equal fitted, C::ModelFile.load(model_file)
  end
end

def test_パイプラインでないものは読み込めない
  Dir.mktmpdir do |dir|
    model_file = File.join(dir, "survived.dump")
    File.binwrite(model_file, Marshal.dump([1, 2, 3]))
    error = assert_raises(TypeError) { C::ModelFile.load(model_file) }

    assert_equal "学習済みのパイプラインではありません: Array", error.message
  end
end
```

`assert_equal fitted, loaded` が通るのは、`Data` の `==` が属性を値で比べるからです。ただし、`DecisionTreeClassifier` はふつうのクラスなので、既定の `==` は「同じオブジェクトか」で比べます。読み込んだものは別のオブジェクトなので、そのままでは等しくなりません。そこで、木が等しければ等しいとする `==` を足しました。

```ruby
def ==(other)
  other.is_a?(DecisionTreeClassifier) && tree == other.tree
end
```

`Dir.mktmpdir` はブロックを抜けるときに一時ディレクトリを消します。Rust 版は `std::env::temp_dir()` の下に自分で作って `remove_dir_all` で消しました。

## 8.12 評価する

正解率だけでは、偏ったデータのモデルを評価できません。**テストデータの生存者のうち何人を生存と予測できたか**（再現率に当たる）も見ます。

```ruby
# 訓練データとテストデータの正解率と、テストデータの生存者のうち生存と予測できた人数。
Evaluation = Data.define(:train_accuracy, :test_accuracy, :found_survivors, :survivors)
```

正解率は第 1 章の `accuracy` をそのまま使います。第 1 章ではラベルが「きのこ」「たけのこ」の文字列でしたが、`==` で比べるだけなので整数のラベルでも動きます。評価指標の体系的な話（適合率・再現率・F 値・混同行列）は第 11 章で扱います。

## 8.13 実データでクラスの重みの効果を確かめる

### 実行する

```bash
bundle exec rake 'run[chapter08]'
```

```text
データ件数: 891（生存 342, 死亡 549）
訓練データ: 712 件, テストデータ: 179 件
classWeight=none: 訓練 0.847, テスト 0.816, 生存者 81 人中 61 人を発見
classWeight=balanced: 訓練 0.837, テスト 0.810, 生存者 81 人中 63 人を発見
保存したモデル: survived.dump
架空の乗客の予測: [1, 0]
```

**正解率は 0.816 から 0.810 へわずかに下がり、見つけられた生存者は 61 人から 63 人へ増えました**。これがクラスの重みの効果です。全体の正解率を少し犠牲にして、少数派を取りこぼさないようにしています。

どちらがよいかは目的によります。救命ボートの配備を考えるなら「生存できたはずの人を見逃さない」ほうが大事でしょうし、統計の報告なら全体の正解率かもしれません。**モデルの良し悪しは、指標だけでは決まりません**。

最後の「架空の乗客の予測」は、1 等客室の女性（運賃 50、C 港）と 3 等客室の男性（運賃 8、S 港）です。どちらも年齢が分かりません。保存したモデルを読み込んで予測し、`[1, 0]` — 女性は生存、男性は死亡と出ました。**年齢が空欄でも予測できる** のは、学習済みの補完（1 等客室の女性の中央値 33.0）がモデルと一緒に保存されているからです。

保存先は `apps/ruby/model/survived.dump` で、`.gitignore` の対象です。テストでは `run` の第 2 引数に一時ディレクトリの中のパスを渡し、リポジトリの中にファイルを残さないようにしています。

### 関係をテストする

数値はほかの言語版と一致しません（分割が違うため。Rust 版はテストデータの生存者が 66 人、Ruby 版は 81 人）。それでも、**関係** は固定できます。

```ruby
def test_重みを付けると見つかる生存者が増える
  data = split
  none = C.evaluate(fitted(data, :none), data)
  balanced = C.evaluate(fitted(data, :balanced), data)

  assert_equal [81, 61, 63], [none.survivors, none.found_survivors, balanced.found_survivors]
  # 見つかる生存者は増えるが、全体の正解率はわずかに下がる
  assert_operator balanced.test_accuracy, :<, none.test_accuracy
end
```

最後の 1 行が、この章で確かめたかったことそのものです。

### Rumale の決定木と突き合わせる

重みを付けない決定木なら、Rumale と比べられます。まず小さなデータで、第 3 章の `RumaleTree.predict`（ラベルを番号にして Rumale に渡し、元のラベルに戻す）がそのまま使えることを確かめます。

```ruby
def test_重みを付けなければ自作とRumaleの予測は一致する
  x = [5.0, 6.0, 70.0, 80.0].map { |value| fare(value) }
  t = [0, 0, 1, 1]
  ours = C::DecisionTreeClassifier.new(max_depth: 1).fit(x, t).predict(x)

  assert_equal t, ours
  assert_equal ours, GettingStartedMl::Chapter03::RumaleTree.predict(x, t, x, 1)
end
```

実データでは、前処理の済んだ特徴量（`Pclass`・`Age`・`SibSp`・`Parch`・`Fare`・`Sex_male`・`Embarked_Q`・`Embarked_S` の 8 列）を自作と Rumale の両方に渡し、深さごとにテストデータの正解率と、予測が分かれた件数を数えました。Rumale のシードは 0・1・2・42 の 4 通りです。

| 深さ | 自作 | Rumale（シード 0） | 自作と分かれた件数（シード 0 / 1 / 2 / 42） |
|------|------|-----------------|-------------------------------------|
| 1 | 0.7989 | 0.7989 | 0 / 0 / 0 / 0 |
| 2 | 0.7263 | 0.7263 | 0 / 0 / 0 / 0 |
| 3 | 0.8380 | 0.8324 | 1 / 1 / 1 / 1 |
| 4 | 0.8324 | 0.8324 | 0 / 0 / 1 / 0 |
| 5 | 0.8156 | 0.8324 | 15 / 15 / 16 / 15 |
| 制限なし | 0.7765 | 0.7765 | 8 / 8 / 15 / 9 |

深さ 1・2 では完全に一致し、深さ 3 以上で分かれます。そして **Rumale の結果はシードで変わります**。深さ 4 では、シード 0 と 2 で予測が 1 件違いました。第 3 章で見たとおり、Rumale は節ごとに特徴量をランダムな順で調べるので、同じ不純度の分割が並ぶと、どれを選ぶかがシードで決まります。Survived.csv はダミー変数（0 と 1 しか取らない）を含むので、アヤメより同点が起きやすいデータです。

`random_seed` を省略すると、Rumale は乱数でシードを決めます。`DecisionTreeClassifier.new.params[:random_seed]` を 2 回表示すると、81139965255499819175056882651986968158 と 1716351981569732135012744277681459469 で、インスタンスを作るたびに違いました。**シードを渡さなければ、実行のたびに結果が変わりうる** ということです。第 3 章の `RumaleTree` は `random_seed: 0` を渡しているので、同じ入力なら同じ結果になります。Rust 版の linfa-trees はシードを渡す手段が無く、Survived.csv で実行ごとに正解率が揺れたので、出力を固定するテストから外しました。Rumale はシードを固定できるので、値を固定したテストが書けます。

```ruby
# Rumale は節ごとに特徴量をランダムな順で調べるので、同じ不純度の分割が並ぶとシードで結果が変わる
def test_Rumale_の決定木はシードを変えると予測が変わることがある
  data = split
  x_train, x_test = prepared(data)
  by_seed = [0, 2].map { |seed| rumale_predict(x_train, data.t_train, x_test, seed) }

  assert_equal(1, by_seed.transpose.count { |left, right| left != right })
end
```

深さ 5 で 15 件も分かれる理由は、シードを変えてもほとんど変わらないので、特徴量を調べる順だけでは説明できません。停止条件や、葉のラベルの同点の扱いなど、ほかの違いがありそうですが、この章では原因を確かめていません。`run` の出力には Rumale の行を入れず、深さ 5 の比較は上の表に記録するだけにしました。

## 8.14 品質チェック

```bash
bundle exec rake check
```

この章の実装を終えた時点で、学習データがある状態では 116 runs・197 assertions・0 skips、行カバレッジは 544 / 557 行（97.66%）でした。学習データを外すと 116 runs・159 assertions・18 skips、491 / 557 行（88.15%）です。

| 学習データ | runs | assertions | skips | 行カバレッジ |
|-----------|------|-----------|-------|------------|
| あり | 116 | 197 | 0 | 97.66% |
| なし | 116 | 159 | 18 | 88.15% |

この章で RuboCop に言われたことは次のとおりです。

| ルール | 場所 | 直し方 |
|------|------|------|
| `Metrics/AbcSize` | `GroupMedian#fit`（22.69）・`FittedDummy#transform`（17.03）・`evaluate`（17.23） | `column_median`・`dummy_columns`・`found_survivors` に分けた |
| `Naming/MethodParameterName` | `best_split(x, t, w)`・`group_of(row, by)` | `weights`・`columns` に改名。`.rubocop.yml` の許可リストは広げない |
| `Style/Documentation` | 複数のファイルに分けた `Chapter08` | ファイルごとにモジュールの説明を書いた |
| `Security/MarshalLoad` | `ModelFile.load` | 自分で保存したファイルだけを読む前提をコメントに書き、その行だけ抑止した |

## 8.15 探索と可視化

年齢の分布や、客室等級ごとの生存率をグラフで見る手順は [Python 版](../python/08-classification-and-preprocessing-pipeline.md) と [Kotlin 版](../kotlin/08-classification-and-preprocessing-pipeline.md) の Notebook を参照してください。Ruby 版には Notebook と可視化の節を設けません。

## 8.16 まとめ

この章では、汚れたデータの前処理をパイプラインにまとめ、クラスの重みを付けた決定木とつないで、Marshal で保存しました。Ruby に固有の論点は次のとおりです。

1. **Rumale には補完もクラスの重みも無い** — 前処理は `Numo::DFloat` を前提にしていて空欄を表せず、`OneHotEncoder` は整数のカテゴリだけを受け取る。前処理と重み付きの決定木は自作が最終実装になる
2. **ダックタイピングで前処理を並べる** — `fit` と `transform` を持つ `Data` なら何でもパイプラインに入る。種類を足すのは楽だが、呼び間違いは実行時の `NoMethodError` でしか分からない
3. **型を書かないので第 3 章を使い回せる** — 木の型も `sort_by_feature` も、ラベルが整数でも（ラベル, 重み）の組でも動く。どこまで使ってよいかはテストが決める
4. **`Data` の不変は浅い** — 属性は差し替えられないが、属性が指す分類器は書き換えられる。学び直しで前の学習結果が上書きされる誤りを、テストで見つけて `dup` で直した
5. **Marshal はそのまま保存できるが、読み込みは信頼が前提** — 配列を鍵にした `Hash` も第 3 章の木も変換なしで保存できる。名前の無いクラスは保存できない。`Marshal.load` は RuboCop の `Security/MarshalLoad` が咎めるとおり、自分で保存したファイルだけに使う
6. **Rumale の決定木はシードで結果が変わる** — Survived.csv では深さ 3 以上で自作と分かれ、シードを変えると予測が変わる。シードを渡せば再現できるので、linfa-trees と違って値を固定したテストが書ける

**TODO リスト（この章の完了時点）**:

- [x] Rumale の前処理で足りるかを確かめる
- [x] 特徴量の列と正解ラベルを決める
- [x] 年齢をグループごとの中央値で補完する
- [x] 乗船した港を最頻値で補完する
- [x] カテゴリ値をダミー変数にする
- [x] クラスの重みを付けた決定木を作る
- [x] 前処理とモデルをパイプラインにつなぐ
- [x] 学習済みのパイプラインを保存して読み込む
- [x] 評価する（正解率と、見つけられた生存者の数）
- [x] 実データでクラスの重みの効果を確かめる

次の章では、既存の列から新しい特徴量を作る **特徴量エンジニアリング** に進みます。
