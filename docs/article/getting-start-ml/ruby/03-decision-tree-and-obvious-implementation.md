---
type: Article
title: "第 3 章: 決定木と明白な実装"
description: "ジニ不純度で分割する決定木を Ruby の Data.define と case/in のパターンマッチで実装し、Rumale の決定木と突き合わせる。網羅性が検査されないこと、tally と max_by の同点の扱い、sort_by が安定でないことを Rust 版と対比する。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:15:00Z }
---

# 第 3 章: 決定木と明白な実装

## 3.1 はじめに

この章で、はじめて**機械学習のアルゴリズム**を実装します。決定木（decision tree）です。第 1 章では「20 代ならきのこ派」というルールを人間が書きました。決定木は、この種のルールを**データから自動で作ります**。

テスト駆動開発の技法としては、**明白な実装**（obvious implementation）を扱います。仮実装と三角測量で少しずつ追い込むのではなく、書き方がはっきり見えているときは一気に書く、という選択です。

[Python 版の第 3 章](../python/03-decision-tree-and-obvious-implementation.md) と同じ題材・同じ TODO リストで進めます。対比の相手は、判別共用体と網羅性の検査を持つ [Rust 版](../rust/03-decision-tree-and-obvious-implementation.md) です。Ruby 版の見どころは次の 4 つです。

1. **葉と節を `Data.define` で作り、`case`/`in` で見分ける** — Rust の `enum` と `match` に近い見た目で書けるが、網羅しているかは検査されない
2. **`tally` は現れた順を保ち、`max_by` は同点のとき最初の要素を返す** — Rust 版が `max_by_key` を使えなかった場面で、Ruby は標準のメソッドがそのまま使える
3. **`sort_by` は安定ではない** — 元の位置を 2 つ目の鍵にして、同じ値の並びを固定する
4. **自作の決定木を Rumale と突き合わせる** — 深さ 1〜5 では予測が完全に一致し、深さを制限しないと 1 件分かれる。その理由を Rumale のソースから説明する

## 3.2 決定木の仕組み

### データを 2 つに分けることを繰り返す

決定木は、「ある特徴量がある値以下か」という質問でデータを 2 つに分け、それを繰り返して木を作ります。実データで学習した深さ 2 の木は、こうなりました。

```text
花弁幅 <= 0.2950
  Iris-setosa
花弁幅 > 0.2950
  花弁幅 <= 0.6500
    Iris-versicolor
  花弁幅 > 0.6500
    Iris-virginica
```

読み方はこうです。花弁幅が 0.2950 以下なら `Iris-setosa`。そうでなくて 0.6500 以下なら `Iris-versicolor`。それより大きければ `Iris-virginica`。

**このルールを人間は書いていません。** 4 つの特徴量から「花弁幅」を選び、境界を 0.2950 と 0.6500 に決めたのはアルゴリズムです。第 1 章で「年代が 20 ならきのこ」と人間が決めた部分が、データから自動で決まっています。

決定木の良いところは、**学習した結果を人間が読めること**です。ニューラルネットワークのように重みの行列を眺めても分からない、ということがありません。

### どこで分けるかをジニ不純度で決める

「良い分割」とは、**分けた後のグループがなるべく 1 種類に偏る**分割です。偏りの度合いを測る指標が**ジニ不純度**（Gini impurity）です。

$$
\mathrm{Gini}(S) = 1 - \sum_{k} p_k^2
$$

$p_k$ はグループ $S$ の中でラベル $k$ が占める割合です。

| グループ | ジニ不純度 | 意味 |
|---------|----------|------|
| すべて同じラベル | 0 | 完全に純粋 |
| 2 種類が半々 | 0.5 | 最も混ざっている（2 種類のとき） |
| 3 種類が均等 | 2/3 ≒ 0.667 | 最も混ざっている（3 種類のとき） |

分割の良さは、左右のジニ不純度を**件数で重み付けして平均**した値で測ります。この値が最も小さくなる（特徴量, 境界）の組を総当たりで探すのが、この章のアルゴリズムです。

## 3.3 TODO リストの作成

**TODO リスト**:

- [ ] ジニ不純度を計算する
  - [ ] 一種類だけなら 0 になる
  - [ ] 二種類が半々なら 0.5 になる
  - [ ] 三種類が均等なら 2/3 になる
- [ ] いちばん多いラベルを返す
  - [ ] 同数なら先に現れたほうを返す
- [ ] 最良の分割を探す
  - [ ] 分けられないときは分割を返さない
  - [ ] 不純度がいちばん小さくなる分割を選ぶ
- [ ] 決定木を学習して予測する
  - [ ] 深さを制限しなければ訓練データを全部当てる
  - [ ] 学習する前は予測できない
- [ ] 木の深さを制限する
- [ ] 学習した木を表示する
- [ ] Rumale の決定木と突き合わせる
- [ ] 実データで深さと正解率を表示する

この章のファイルは次のとおりです。

```text
apps/ruby/
├── lib/getting_started_ml/
│   ├── chapter03.rb                 # 章の実行（深さごとの正解率と木の表示）
│   └── chapter03/
│       ├── decision_tree.rb         # 自作の決定木
│       └── rumale_tree.rb           # Rumale の決定木との橋渡し
└── test/
    ├── chapter03_test.rb
    └── iris_tree_test.rb            # 実データを使うテスト
```

`Gemfile` には、この章から Rumale が加わります。

```ruby
gem "csv", "~> 3.3"
gem "rumale", "~> 2.2"
```

`rumale` は `rumale-tree`・`rumale-linear_model` などの gem をまとめて入れるメタ gem で、行列には `numo-narray-alt` が依存として入ります。手元で確かめた版は Rumale 2.2.0、numo-narray-alt 0.11.2 です（[ADR 010](../../../adr/010-ruby-ml-libraries.md)）。

## 3.4 ジニ不純度を計算する

### 仮実装から三角測量へ

最初のテストは「1 種類だけなら 0」です。

```ruby
class Chapter03Test < Minitest::Test
  C = GettingStartedMl::Chapter03
  Features = GettingStartedMl::Chapter02::Features

  def test_一種類だけならジニ不純度は零になる
    assert_in_delta 0.0, C.gini(%w[setosa setosa]), 1e-12
  end
end
```

`assert_equal 0.0, ...` と書かずに、**許容誤差付きの比較**にしています。ジニ不純度は割り算と引き算の結果なので、次のテスト（2/3）では誤差が出ます。最初からそろえました。

このテストは `0.0` を返す仮実装で通ります。2 つめと 3 つめのテストを足します。

```ruby
def test_二種類が半々ならジニ不純度は零点五になる
  assert_in_delta 0.5, C.gini(%w[setosa virginica]), 1e-12
end

def test_三種類が均等ならジニ不純度は三分の二になる
  assert_in_delta 2.0 / 3, C.gini(%w[setosa versicolor virginica]), 1e-12
end
```

期待値を `0.6666666666666666` ではなく `2.0 / 3` と書いています。**式のまま書けば、期待値の側に丸めた値を持ち込みません。**

### 明白な実装

ここからは**明白な実装**です。式が決まっているので、仮実装から少しずつ進める意味がありません。

```ruby
# ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。
def gini(labels)
  return 0.0 if labels.empty?

  1.0 - labels.tally.values.sum { |count| count.fdiv(labels.size)**2 }
end
```

式がほぼそのまま 1 行になりました。

- `labels.tally` は、要素ごとの出現回数を数えた `Hash` を返します。`%w[setosa setosa virginica].tally` は `{"setosa"=>2, "virginica"=>1}` です
- `.values.sum { ... }` は、ブロックの結果の合計です。`map` してから `sum` するのと同じですが、途中の配列を作りません
- `count.fdiv(labels.size)` は、第 1 章で使った「浮動小数点数として割る」メソッドです。`2 / 3` は整数の割り算で `0` になるので、ここは `fdiv` でなければなりません

Rust 版は、ラベルごとの件数を数える `counts` を 20 行ほどで自作しました。`HashMap` が反復の順を保証しないので、現れた順を別の `Vec` に持つ必要があったからです。**Ruby の `tally` が返す `Hash` は、最初に現れた順を保ちます**。数えることと順を保つことが、標準のメソッド 1 つで済みます。

`labels.empty?` で先に戻るのは、0 件で割らないためです。`0.fdiv(0)` は例外ではなく `NaN` を返すので、放っておくと木の全体に `NaN` が静かに広がります。

## 3.5 いちばん多いラベルを返す

### `max_by` は同点のとき最初の要素を返す

葉が予測するラベルは、その葉に入ったデータでいちばん多いラベルです。テストは 2 つ。

```ruby
def test_いちばん多いラベルを返す
  assert_equal "virginica", C.majority(%w[setosa virginica virginica])
end

def test_同数なら先に現れたラベルを返す
  assert_equal "virginica", C.majority(%w[virginica setosa])
end
```

2 つめが効きます。ほかの言語版はすべて「同数なら先に現れたほう」を選ぶので、そろえないと木の形が変わります。

```ruby
# いちばん多いラベルを返す。同数なら先に現れたほうを選ぶ。
# tally は最初に現れた順を保ち、max_by は同点のとき最初の要素を返す（Rust の max_by_key とは逆）。
def majority(labels)
  labels.tally.max_by { |_label, count| count }.first
end
```

`tally` で数え、`max_by` で件数が最大の組 `[ラベル, 件数]` を選び、`.first` でラベルを取り出します。**同点のとき、Ruby の `max_by` は最初の要素を返します**。`%w[b a].tally.max_by { ... }` を実際に走らせると `["b", 1]` でした。`tally` が現れた順を保つので、「最初の要素」はそのまま「先に現れたラベル」になります。

Rust 版では、ここで**テストが 2 件落ちました**。Rust の `Iterator::max_by_key` は、同点のとき**最後**の要素を返す仕様だからです。Rust 版は `max_by_key` をあきらめて、厳密な不等号で比べる 9 行のループを書きました。

| 言語 | 同点のときに返るもの |
|------|------------------|
| Ruby の `Enumerable#max_by`・`min_by` | 最初の要素 |
| Python の `max`・`min` | 最初の要素 |
| Rust の `Iterator::max_by_key` | **最後**の要素 |
| Rust の `Iterator::min_by_key` | 最初の要素 |

Ruby 版は同じ場面で標準のメソッドがそのまま使えました。ただし、**同点のときの振る舞いはメソッド名からは読めません**。Rust 版が落ちたテストで気付いたように、Ruby 版もこのテストがあるから「たまたま合っている」のではなく「確かめて合っている」と言えます。コメントに「Rust の max_by_key とは逆」と書いたのは、ほかの言語版から移植する人が同じ落とし穴を探しに来たときのためです。

## 3.6 最良の分割を探す

### 分割を表す値オブジェクト

```ruby
# 決定木の分割。特徴量の値が境界以下なら左、境界より大きければ右へ進む。
Split = Data.define(:feature, :threshold, :impurity)
```

Rust 版は `#[derive(Debug, Clone, PartialEq)] struct Split` でした。Ruby では `Data.define` の 1 行で、キーワード引数のコンストラクタ・読み取りメソッド・値による比較がそろいます。

### テスト

```ruby
def column(value)
  Features.new(columns: ["花弁幅"], values: [value])
end

# 3 種類に分かれる小さなデータ。
def three_species
  [[0.2, 0.3, 1.2, 1.4, 2.0, 2.2].map { |value| column(value) },
   %w[setosa setosa versicolor versicolor virginica virginica]]
end

def test_分けられないときは分割を返さない
  assert_nil C.best_split([column(0.2), column(0.3)], %w[setosa setosa])
end

def test_不純度がいちばん小さくなる分割を選ぶ
  split = C.best_split(*three_species)

  assert_equal "花弁幅", split.feature
  assert_in_delta 0.75, split.threshold, 1e-12
end
```

`three_species` は架空の値です。実データの行は記事にもテストにも書きません。0.2・0.3 が setosa、1.2・1.4 が versicolor、2.0・2.2 が virginica という、実データの傾向だけを真似た 6 件です。

期待する境界 0.75 は、0.3 と 1.2 の**中点**です。境界を「隣り合う 2 つの値の中点」に置くのは、決定木の実装の定石です。0.3 と 1.2 の間で切っても、1.4 と 2.0 の間で切っても、重み付き平均の不純度は同じ 1/3 になります。**同じ不純度なら先に見つけたほうを選ぶ**ので、0.75 が選ばれます。

`C.best_split(*three_species)` の `*` は、配列を展開して引数に並べる書き方です。`three_species` が `[x, t]` を返すので、`best_split(x, t)` と同じになります。

「分けられない」は失敗ではないので、例外ではなく `nil` を返します。Rust 版は `Result<Option<Split>>` の `Ok(None)` で表しました。Ruby では第 2 章の欠損値と同じく、**「無い」は `nil`、失敗は例外**です。

### 実装

```ruby
# 左右の不純度の重み付き平均が最も小さくなる分割を返す。分けられなければ nil。
# 同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。
def best_split(x, t)
  return nil if x.empty? || gini(t).zero?

  x.first.columns.flat_map { |feature| candidates(x, t, feature) }.min_by(&:impurity)
end

# 1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。
def candidates(x, t, feature)
  sorted = sort_by_feature(x, t, feature)

  (1...sorted.size).filter_map { |i| split_at(sorted, i, feature) }
end

# 並べた組の i 番目の手前で分けた分割を返す。前後の値が同じなら分けられないので nil。
def split_at(sorted, index, feature)
  before, after = sorted.values_at(index - 1, index).map(&:first)
  return nil if before == after

  labels = sorted.map(&:last)
  Split.new(feature:, threshold: (before + after) / 2.0,
            impurity: weighted_gini(labels.first(index), labels.drop(index)))
end

# 左右の不純度の重み付き平均。
def weighted_gini(left, right)
  ((left.size * gini(left)) + (right.size * gini(right))) / (left.size + right.size)
end
```

Rust 版は、二重の `for` ループの中で「今までの最良」を `Option<Split>` に持ち、見つけるたびに更新しました。Ruby 版は**候補をすべて作ってから、最小のものを選ぶ**形です。

1. `candidates` が 1 つの列について、分割の候補を配列で返す
2. `flat_map` が全列の候補を 1 本の配列につなぐ
3. `min_by(&:impurity)` が不純度の最小の候補を選ぶ

`min_by` も `max_by` と同じく、**同点のとき最初の要素**を返します。候補は列の順、同じ列の中では値の順に並んでいるので、「同じ不純度なら列の順で前の分割」がこの 1 行で決まります。Rust 版で厳密な不等号 `<` を使って書いた決まりが、Ruby 版では `min_by` の振る舞いに含まれています。

`split_at` が `nil` を返すのは、隣り合う 2 つの値が同じときです。同じ値の間に境界を引くと、同じ値のデータが左右に分かれてしまいます。`filter_map` が `nil` を捨てるので、候補には現れません。Rust 版は `f64::EPSILON` との差で「同じ値」を判定しましたが、Ruby 版は `before == after` です。どちらも同じ CSV の文字列から読んだ値どうしを比べるので、等しい値は同じ `Float` になります。

`sorted.values_at(index - 1, index)` は、2 つの位置の要素を配列で取り出します。

### 静的解析に合わせて分けたメソッド

`best_split`・`candidates`・`split_at`・`weighted_gini` の 4 つに分かれているのは、RuboCop の `Metrics/AbcSize`（既定の上限 17）に合わせたからです。1 つのメソッドに全部を書くと上限に収まらないので、手順ごとに分けてあります。

分けた結果、`candidates` は「1 つの列の候補」、`split_at` は「1 つの位置での分割」と、それぞれが 1 つのことだけをするようになりました。Rust 版の 40 行近い `best_split` と比べると、1 つのメソッドを読むのに必要な文脈が小さくなっています。**静的解析の指摘は、名前を付けるきっかけ**として使えます。

その代償もあります。`split_at` は呼ばれるたびに `sorted.map(&:last)` でラベルの配列を作り直すので、1 つの列で候補の数だけ配列を作ります。訓練データ 105 件の決定木では気にならない量ですが、件数が大きくなれば効いてきます。ここでは読みやすさを優先しました。

### `sort_by` は安定ではない

値で並べる部分に、Ruby に固有の注意があります。

```ruby
# 指定した列の値と正解ラベルの組を、値の順に並べる。同じ値なら元の順を保つ（安定な並べ替え）。
# Ruby の sort_by は安定ではないので、元の位置を 2 つ目の鍵にする。
def sort_by_feature(x, t, feature)
  x.map { |features| features.value(feature) }.zip(t).each_with_index
   .sort_by { |(value, _label), index| [value, index] }.map(&:first)
end
```

**Ruby の `sort` と `sort_by` は安定なソートではありません**。値が同じ要素の相対順が、並べ替えた後に保たれる保証が無い、ということです。Rust の `sort_by` は安定なソートなので、Rust 版では何もしなくても「同じ値なら元の順」になりました。

同じ値の並びが変わると、何が起きるのでしょうか。同じ値の間には境界を引かないので、分割の候補そのものは変わりません。しかし `split_at` が左右に分けるラベルの**並び**は、元の順に依存します。不純度は並びに依存しないので、最良の分割は変わりません。それでも、ほかの言語版と同じ入力に対して**処理の途中の状態まで同じ**にしておくと、結果が分かれたときに原因を追いやすくなります。

そこで、`each_with_index` で元の位置を付け、`[value, index]` の配列を鍵にしています。**配列どうしの比較は、先頭の要素から順に比べる**ので、値が同じなら元の位置の小さいほうが前に来ます。鍵が重複しなくなるので、並べ替えの結果は一通りに決まります。

ブロックの引数 `|(value, _label), index|` は、`each_with_index` が渡す `[[値, ラベル], 位置]` を、丸括弧で入れ子のまま分解しています。

`f64` が全順序でないために `sort` が使えなかった Rust 版の話は、Ruby 版には出てきません。Ruby の `Float` は `<=>` で比べられ、`NaN` と比べると `nil` が返ります。`sort_by` の鍵に `NaN` が混ざると、比べられずに `ArgumentError`（comparison of Array with Array failed）で止まります。**黙って壊れはしませんが、止まるのは実行したときです**。第 2 章の前処理で欠損値を補完してあるので、ここに `NaN` や `nil` は来ません。

## 3.7 決定木を学習して予測する

### 葉と節を `Data.define` で作る

決定木は「葉」か「節」のどちらかです。

```ruby
# 予測するラベルを持つ葉。
Leaf = Data.define(:label)

# 分割と左右の部分木を持つ節。Ruby には判別共用体が無いので、葉と節を別の型にして
# case/in のパターンマッチで見分ける。網羅しているかは検査されない。
Node = Data.define(:split, :left, :right)
```

Rust 版は `enum Tree { Leaf { label }, Node { split, left, right } }` と、1 つの型の 2 つの列挙子として書きました。Ruby には判別共用体（「このどれか」を 1 つの型として宣言する仕組み）が無いので、`Leaf` と `Node` は**互いに関係の無い 2 つの型**です。「木とは `Leaf` か `Node` のどちらかである」という事実は、どこにも宣言されていません。

Rust 版で要った `Box` も要りません。Rust は値をその場に置くので、自分自身を含む型の大きさが決まらず、`Box` で間接参照を入れる必要がありました。Ruby のオブジェクトは常に参照で持つので、`Node` の `left` にそのまま `Node` を入れられます。

### `case`/`in` で見分ける

予測は再帰で書きます。

```ruby
# 木をたどって 1 件のラベルを予測する。
def predict_one(tree, features)
  case tree
  in Leaf(label:) then label
  in Node(split:, left:, right:) then predict_one(goes_left?(split, features) ? left : right, features)
  end
end

# 分割の境界以下なら左へ進む。
def goes_left?(split, features)
  features.value(split.feature) <= split.threshold
end
```

`case`/`in` は Ruby 3.0 で入ったパターンマッチです。`in Leaf(label:)` は「`tree` が `Leaf` で、その `label` を変数 `label` に取り出す」という意味です。`Data.define` で作ったクラスは、パターンマッチに必要なメソッド（`deconstruct_keys`）を持っているので、そのままパターンに書けます。

見た目は Rust 版の `match` とよく似ています。

```rust
match tree {
    Tree::Leaf { label } => Ok(label.clone()),
    Tree::Node { split, left, right } => { /* ... */ }
}
```

違うのは、**網羅性の検査がない**ことです。

| 言語版 | 木の表し方 | 網羅性の検査 | 漏れたときに分かるのは |
|--------|----------|------------|------------------|
| Ruby | `Data.define` の `Leaf` と `Node` を `case`/`in` で見分ける | されない | そのパターンに当たったとき（実行時） |
| Rust | `enum Tree` + `Box` を `match` で見分ける | コンパイラが検査する | コンパイル時 |
| Java 21 | `sealed interface` + `record` を `switch` で見分ける | 検査する | コンパイル時 |
| Python・TypeScript | クラス 2 つ、または辞書 | されない（TypeScript は判別可能ユニオンで近いことができる） | 実行時 |

たとえば深さ制限で刈り込んだ節を表す `Pruned` を 3 つめの型として足したとします。Rust 版なら、`match` を書いたすべての場所がコンパイルエラーになり、直すべき場所をコンパイラが全部教えてくれます。Ruby 版では何も言われません。`predict_one` が `Pruned` を受け取ったとき、どの `in` にも当てはまらず、はじめて **`NoMatchingPatternError`** が飛びます。

それでも、`case`/`in` には `case`/`when` より良い点が 1 つあります。**どのパターンにも当てはまらないときに例外を投げる**ことです。`case`/`when` で書いて `else` を忘れると、当てはまらなかった `case` は黙って `nil` を返し、`nil` のラベルが予測として紛れ込みます。`case`/`in` なら、少なくとも実行時には止まります。Go 版が型スイッチに `default` を書いてエラーを返したのと同じことを、Ruby は言語が肩代わりしてくれる、という形です。

網羅性をコンパイラが検査しない以上、**型を足したときに漏れを見つけるのはテスト**です。`predict_one` と木を表示する `format` の両方をテストが通っているので、新しい型を足せば、どちらかのテストが `NoMatchingPatternError` で落ちます。

### 木を組み立てる

```ruby
# 深さの上限まで分割を繰り返して木を作る。max_depth が nil なら上限なし。
def build(x, t, max_depth)
  split = max_depth&.zero? ? nil : best_split(x, t)
  return Leaf.new(label: majority(t)) if split.nil?

  left, right = x.zip(t).partition { |features, _| goes_left?(split, features) }
  next_depth = max_depth&.pred

  Node.new(split:, left: build(*left.transpose, next_depth), right: build(*right.transpose, next_depth))
end
```

**止まる条件が 2 つ**あります。深さを使い切ったとき（`max_depth` が 0）と、分割が見つからないとき（`best_split` が `nil`）です。どちらも `majority(t)` を予測とする葉になります。Ruby 版では、この 2 つを「`split` が `nil` なら葉」の 1 か所にまとめました。

`max_depth&.zero?` の `&.` は**安全なナビゲーション演算子**です。`max_depth` が `nil` なら `zero?` を呼ばずに `nil` を返し、そうでなければ `max_depth.zero?` を呼びます。`max_depth` が `nil`（上限なし）のとき、`nil ? nil : best_split(x, t)` は `best_split` のほうへ進みます。

`max_depth&.pred` も同じ形です。`pred` は 1 つ前の整数を返すメソッドで、`3.pred` は `2` です。`nil` なら `nil` のまま次の段に渡るので、**「上限なし」は最後まで上限なしのまま**です。Rust 版は `Option<usize>` の `map(|depth| depth - 1)` で同じことを書き、「番兵の値（`-1` など）を使わずに済む」ことを型の効用として挙げました。Ruby は `nil` と `&.` で同じ効用を得ています。ただし、`max_depth` に `nil` が来うることは、Rust の `Option` と違って引数の型には書かれていません。

`x.zip(t).partition { ... }` は、組の配列を条件で 2 つに分けます。`partition` は条件が真の要素と偽の要素を `[真の配列, 偽の配列]` で返すので、多重代入で `left` と `right` に受けます。第 2 章と同じく、組にしてから分け、`transpose` でほどいて `build` に渡しています。Rust 版は 4 つの `Vec` を用意して `push` を 4 回書きました。

`build(*left.transpose, next_depth)` の `*` は、`[x の配列, t の配列]` を展開して引数に並べます。分割が見つかった時点で左右とも 1 件以上あることが決まっているので、`transpose` が空で `nil` を返す第 2 章の心配は、ここではありません。

### `DecisionTree` で包む

scikit-learn の `fit` / `predict` に合わせた分類器で包みます。

```ruby
# 自作の決定木の分類器。fit で学習してから predict で予測する。
class DecisionTree
  attr_reader :tree

  def initialize(max_depth: nil)
    @max_depth = max_depth
    @tree = nil
  end

  # 訓練データから木を作る。メソッドをつなげられるように自分を返す。
  def fit(x, t)
    @tree = Chapter03.build(x, t, @max_depth)
    self
  end

  # 特徴量ごとのラベルを予測する。
  def predict(x)
    raise "学習してから予測してください" if tree.nil?

    x.map { |features| Chapter03.predict_one(tree, features) }
  end
end
```

`max_depth: nil` は**既定値つきのキーワード引数**です。`DecisionTree.new` なら上限なし、`DecisionTree.new(max_depth: 2)` なら深さ 2 です。Rust 版は名前付き引数も既定値も無いので、`DecisionTree::unlimited()` と `DecisionTree::with_max_depth(2)` という 2 つの関連関数を作りました。scikit-learn の `DecisionTreeClassifier(max_depth=2)` と同じ書き方ができるのは、Python 版と Ruby 版の共通点です。

`fit` が `self` を返すので、`DecisionTree.new(max_depth: 2).fit(x, t).predict(x)` と繋げて書けます。

`raise "..."` は、例外のクラスを書かずにメッセージだけを渡す書き方で、`RuntimeError` が投げられます。値が不正（`ArgumentError`）でもキーが無い（`KeyError`）でもなく、**呼ぶ順番を間違えた**という失敗なので、専用の例外クラスを作らずに `RuntimeError` にしました。

```ruby
def test_学習する前は予測できない
  error = assert_raises(RuntimeError) { C::DecisionTree.new.predict([column(0.2)]) }

  assert_equal "学習してから予測してください", error.message
end
```

Rust 版では、「学習してから予測する」を型で強制する設計（型状態パターン）も検討したうえで、実行時の検査にしました。Ruby にはそもそも型で強制する手段が無いので、実行時の検査とテストの 2 つが唯一の守りです。

`DecisionTree` から `Chapter03.build` と `Chapter03.predict_one` をモジュール越しに呼んでいるのは、`module_function` で定義した関数をクラスの中から呼ぶためです。クラスの中で `build(...)` と書いても、`DecisionTree` のインスタンスメソッドとしては見つかりません。

## 3.8 木の深さを制限する

深さを制限しなければ、決定木は訓練データを**完全に暗記します**。

```ruby
def test_深さを制限しなければ訓練データを全部当てる
  x, t = three_species

  assert_equal t, C::DecisionTree.new.fit(x, t).predict(x)
end
```

訓練データの正解率 1.0 は嬉しい数字に見えますが、**過学習**（overfitting）の典型です。実データでも深さ制限なしの訓練データ正解率は 1.0000 になりました。

深さ 1 の木が 2 つの葉になることも確かめます。

```ruby
def test_深さ一なら二つの葉になる
  x, t = three_species

  tree = C::DecisionTree.new(max_depth: 1).fit(x, t).tree

  assert_instance_of C::Node, tree
  assert_instance_of C::Leaf, tree.left
  assert_instance_of C::Leaf, tree.right
end
```

`assert_instance_of` は、値が指定したクラスのインスタンスであることを確かめます。Rust 版は `match` で `Node` を取り出し、`matches!(**left, Tree::Leaf { .. })` で葉であることを確かめました。Ruby では `Leaf` と `Node` が別のクラスなので、クラスを直接確かめられます。**判別共用体が無いことが、ここでは確かめやすさとして効いています**。

## 3.9 学習した木を表示する

```ruby
# 木を字下げ付きの文字列にする。
def format(tree, indent = "")
  case tree
  in Leaf(label:) then "#{indent}#{label}\n"
  in Node(split: Split(feature:, threshold:), left:, right:)
    border = Kernel.format("%.4f", threshold)
    "#{indent}#{feature} <= #{border}\n#{format(left, "#{indent}  ")}" \
      "#{indent}#{feature} > #{border}\n#{format(right, "#{indent}  ")}"
  end
end
```

`in Node(split: Split(feature:, threshold:), left:, right:)` が、**入れ子のパターン**です。`Node` の `split` がさらに `Split` であることを確かめながら、その `feature` と `threshold` まで 1 行で取り出しています。Rust 版では `Tree::Node { split, left, right }` と取り出してから `split.feature` と書きました。パターンマッチで深く掘れるのは、Ruby の `case`/`in` の読みやすいところです。

`Kernel.format("%.4f", threshold)` と、`format` にわざわざ `Kernel.` を付けています。このモジュールの中では、`format` という名前が**自分で定義した木の表示のメソッド**を指すからです。`module_function` で定義した `format` は、モジュールの中から呼ぶと `Kernel#format`（文字列の書式）より先に見つかります。`Kernel.format` と明示して、標準の書式のほうを呼んでいます。

これは第 1 章で `run` が Minitest の `run` を上書きしたのと同じ種類の話です。**同じ名前のメソッドがどちらを指すかは、実行したときの探索順で決まります**。今回は書いた本人が気付いて `Kernel.` を付けたので問題になっていませんが、`format` という名前を選んだ時点で、標準のメソッドを隠していることは覚えておく必要があります。

行末の `\` は、隣り合う 2 つの文字列リテラルを 1 つにつなぐ書き方です。長い文字列を 2 行に折り返しています。

テストは出力の文字列をそのまま固定します。

```ruby
def test_木を字下げ付きの文字列にする
  x, t = three_species

  assert_equal "花弁幅 <= 0.7500\n  setosa\n花弁幅 > 0.7500\n  versicolor\n",
               C.format(C::DecisionTree.new(max_depth: 1).fit(x, t).tree)
end
```

右の葉が `versicolor` なのは、境界 0.75 より右に versicolor 2 件と virginica 2 件が同数で入り、**先に現れた versicolor** が選ばれたからです。`majority` の同点の扱いが変われば、このテストも落ちます。**木の表示は、木の形を丸ごと検査するテスト**として働きます。

## 3.10 Rumale の決定木と突き合わせる

ここまでは自作です。Ruby には Rumale という機械学習の gem があり、scikit-learn に似た API で決定木も使えます（[ADR 010](../../../adr/010-ruby-ml-libraries.md)）。**自作したものをライブラリと突き合わせる**のが、本シリーズの進め方です。

### ラベルを番号にする

```ruby
require "rumale"

module GettingStartedMl
  module Chapter03
    # Rumale の決定木で学習して予測する。自作の決定木と結果を突き合わせるために使う。
    module RumaleTree
      module_function

      # 文字列のラベルを、最初に現れた順の番号にする。番号と、番号からラベルへの対応表を返す。
      def encode(labels)
        classes = labels.uniq
        [labels.map { |label| classes.index(label) }, classes]
      end

      # 特徴量の並びを Numo の行列にする。
      def matrix(x)
        Numo::DFloat.cast(x.map(&:values))
      end

      # 訓練データで学習し、テストデータのラベルを予測する。
      def predict(x_train, t_train, x_test, max_depth)
        codes, classes = encode(t_train)
        model = Rumale::Tree::DecisionTreeClassifier.new(criterion: "gini", max_depth:, random_seed: 0)
        model.fit(matrix(x_train), Numo::Int32.cast(codes))

        model.predict(matrix(x_test)).to_a.map { |code| classes[code] }
      end
    end
  end
end
```

Rumale の分類器は、**ラベルとして整数の配列（`Numo::Int32`）を受け取ります**。scikit-learn は文字列のラベルをそのまま受け取れますが、Rumale では自分で番号にする必要があります。`encode` は `labels.uniq`（現れた順の重複なしの配列）を番号から文字列への対応表にし、各ラベルをその位置の番号に置き換えます。

```ruby
def test_Rumale_に渡すラベルは番号にする
  assert_equal [[0, 1, 0], %w[setosa virginica]], C::RumaleTree.encode(%w[setosa virginica setosa])
end
```

特徴量は `Numo::DFloat.cast(x.map(&:values))` で行列にします。`Features` の `values` の配列を並べた「配列の配列」を、そのまま 2 次元の行列に変換しています。Rust 版は全行の値を `flat_map` で 1 本のベクタに平らにしてから `from_shape_vec` で形を与えましたが、Numo は入れ子の配列から形を読み取ってくれます。

`criterion: "gini"` と `max_depth:` は自作と同じ条件にするための指定です。`random_seed: 0` の意味は、次の節で分かります。

### 深さ 5 までは完全に一致する

実データで突き合わせます。

```ruby
# 深さを制限しないと予測が 1 件分かれる。Rumale は節ごとに特徴量をランダムな順で調べるので、
# 同じ不純度の分割が並ぶと、列の順で先勝ちにする自作とは違う分割を選ぶことがある
def test_深さ五までは自作とRumaleの予測が一致する
  data = split

  [1, 2, 3, 4, 5].each do |max_depth|
    ours = C::DecisionTree.new(max_depth:).fit(data.x_train, data.t_train).predict(data.x_test)

    assert_equal ours, C::RumaleTree.predict(data.x_train, data.t_train, data.x_test, max_depth), "深さ #{max_depth}"
  end
end
```

**深さ 1 から 5 まで、45 件のテストデータの予測が 1 件も違いません。** 正解率がたまたま同じなのではなく、予測そのものが完全に一致します。自作の実装が Rumale と同じアルゴリズムを実装できている、という強い証拠です。

Rust 版では、linfa の決定木と一致したのは深さ 1・2 だけでした。linfa は既定で `min_impurity_decrease`（不純度がわずかしか下がらない分割を却下する事前の枝刈り）などの停止条件を持ち、学習時は `<=`、予測時は `<` と比較演算子まで違っていたからです。Rumale の `base_decision_tree.rb` の `grow_node` を読むと、既定のまま効く停止条件は「節の件数が `min_samples_leaf`（既定 1）になった」「深さの上限に達した」「`stop_growing?` が真（これ以上分ける必要が無い）」「分割しても不純度が下がらない（`gain` が 0）」で、不純度の減り方に閾値を設けた枝刈りはありません。予測時の比較も自作と同じ `<=` です（同じファイルの `partial_apply`）。**既定値が素直なぶん、自作との一致が深くまで続きます**。

### 深さを制限しないと 1 件分かれる理由

深さを制限しないと、45 件のうち 1 件だけ予測が分かれます。

| 深さ | 自作（テストデータ） | Rumale（テストデータ） | 予測の一致 |
|-----|-----------------|-------------------|----------|
| 1 | 0.6444 | 0.6444 | 完全に一致 |
| 2 | 0.9556 | 0.9556 | 完全に一致 |
| 3 | 0.9556 | 0.9556 | 完全に一致 |
| 4 | 0.9556 | 0.9556 | 完全に一致 |
| 5 | 0.9556 | 0.9556 | 完全に一致 |
| 制限なし | 0.9556 | 0.9333 | **1 件違う** |

分かれた 1 件は、自作が `Iris-versicolor`（正解）と予測し、Rumale が `Iris-virginica` と予測したものでした。

理由は Rumale の `base_decision_tree.rb` にあります。節を分けるときに、特徴量を調べる順をこう決めています。

```ruby
def rand_ids
  @feature_ids.sample(@params[:max_features], random: @sub_rng)
end
```

`max_features` を指定しなければ全特徴量の数になるので、`Array#sample` は**全特徴量をランダムな順に並べ替えて返します**。Rumale はこの順で各特徴量の最良の分割を求め、その中から `max_by` で不純度の減少がいちばん大きいものを選びます。`max_by` は同点のとき最初の要素を返すので、**不純度の減少が同じ分割が 2 つの特徴量に並んだとき、どちらを選ぶかはランダムな順で決まります**。

自作は、同じ不純度なら列の順で前の分割を選びます。深い節ほど件数が少なく、「どの特徴量で切っても同じだけ純粋になる」という同点が起きやすくなります。深さ 5 までは同点がたまたま結果に響かず、制限なしで初めて、同点の選び方の違いが予測 1 件の差として表に出た、ということです。

これを確かめるために、`random_seed` だけを変えて Rumale の深さ制限なしの決定木を学習し直しました。

| `random_seed` | Rumale の正解（45 件中） | 自作と予測が違う件数 |
|--------------|---------------------|-----------------|
| 0 | 42 | 1 |
| 1 | 41 | 2 |
| 2 | 42 | 1 |
| 3 | 43 | 0 |
| 4 | 43 | 0 |
| 5 | 42 | 1 |

**シードを変えるだけで、同じデータ・同じ深さの決定木の予測が変わります**。シード 3 と 4 では自作と完全に一致しました。特徴量の順が結果を左右していることの、直接の証拠です。`random_seed: 0` を固定しているのは、この揺れを止めて、実行するたびに同じ結果になるようにするためです。

Rust 版の結論は「ライブラリの既定値は仕様である」でした。Ruby 版から持ち帰れる教訓は少し違います。

1. **ランダムでないはずのアルゴリズムにも乱数が入っていることがある。** 決定木はランダムフォレストと違って乱数を使わないと思いがちだが、Rumale は同点の選び方に乱数を使っている。`random_seed` という引数があること自体が、その手がかりになる
2. **正解率が同じでも、同じことをしているとは限らない。** シード 0・2・5 はどれも 42 件の正解だが、予測を 1 件ずつ比べてはじめて、どれがどう違うかが分かる

## 3.11 実データで深さと正解率を表示する

### 深さと正解率

```ruby
module Chapter03
  # テストデータの割合。
  TEST_SIZE = 0.3
  # 分割の乱数のシード。
  SEED = 0
  # 最後に木そのものを表示する深さ。
  TREE_DEPTH_TO_SHOW = 2
  # 正解率を比べる深さ。nil は制限なし。
  MAX_DEPTHS = [1, 2, 3, 4, 5, nil].freeze

  # 深さごとの正解率と、深さ 2 の決定木を表示する。
  def self.run(out = $stdout)
    split = Chapter02.prepare_iris(File.join(Dataset.dir, "iris.csv"), test_size: TEST_SIZE, seed: SEED)

    out.puts "深さ\t訓練データ\tテストデータ\tRumale"
    MAX_DEPTHS.each { |max_depth| out.puts accuracy_row(max_depth, split) }
    out.puts
    out.puts "深さ #{TREE_DEPTH_TO_SHOW} の決定木:"
    out.print format(DecisionTree.new(max_depth: TREE_DEPTH_TO_SHOW).fit(split.x_train, split.t_train).tree)
  end

  # 自作と Rumale で学習し、正解率を 1 行にする。
  def self.accuracy_row(max_depth, split)
    [max_depth || "制限なし", *results(max_depth, split).map { |predictions, labels| score(predictions, labels) }]
      .join("\t")
  end

  # 自作の訓練データ・テストデータと、Rumale のテストデータについて、予測と正解ラベルの組を返す。
  def self.results(max_depth, split)
    model = DecisionTree.new(max_depth:).fit(split.x_train, split.t_train)

    [[model.predict(split.x_train), split.t_train],
     [model.predict(split.x_test), split.t_test],
     [RumaleTree.predict(split.x_train, split.t_train, split.x_test, max_depth), split.t_test]]
  end

  # 正解率を小数 4 桁の文字列にする。
  def self.score(predictions, labels)
    Kernel.format("%.4f", Chapter01.accuracy(predictions, labels))
  end
end
```

`MAX_DEPTHS` に `nil` を入れ、「制限なし」を 1 つの深さとして同じループで扱っています。`max_depth || "制限なし"` で、表示のときだけ `nil` を言葉に置き換えます。Rust 版は `[usize; 5]` の配列に `None` を入れられないので、深さ 1〜5 のループの後に「制限なし」の行を別に書きました。**`nil` が「上限なし」という意味を持っている**ので、Ruby では特別扱いが要りません。

`.freeze` は、定数の配列を書き換えられないようにする指定です。Ruby の定数は「再代入すると警告が出る」だけで、中身の配列は書き換えられてしまいます。`freeze` を付けると、`MAX_DEPTHS << 6` が `FrozenError` になります。RuboCop の `Style/MutableConstant` が求める書き方です。

`[max_depth || "制限なし", *results(...).map { ... }]` の `*` は、配列の中で別の配列を展開します。深さ 1 つと正解率 3 つの 4 要素の配列を作り、`join("\t")` でタブ区切りの 1 行にしています。

`run` を `accuracy_row`・`results`・`score` に分けたのも、`Metrics/AbcSize` の上限に合わせるためです。正解率の計算は第 1 章の `Chapter01.accuracy` をそのまま使っています。

実行します。

```text
$ bundle exec rake 'run[chapter03]'
深さ	訓練データ	テストデータ	Rumale
1	0.6762	0.6444	0.6444
2	0.9333	0.9556	0.9556
3	0.9524	0.9556	0.9556
4	0.9524	0.9556	0.9556
5	0.9714	0.9556	0.9556
制限なし	1.0000	0.9556	0.9333

深さ 2 の決定木:
花弁幅 <= 0.2950
  Iris-setosa
花弁幅 > 0.2950
  花弁幅 <= 0.6500
    Iris-versicolor
  花弁幅 > 0.6500
    Iris-virginica
```

読み取れることが 3 つあります。

1. **深さ 1 では足りない。** 2 つの葉しか作れないので、3 種類は分けられません。テストデータ 0.6444
2. **深さ 2 で一気に上がる。** 0.9556。花弁幅だけで 3 種類がほぼ分かれます
3. **深くしても、テストデータの正解率は上がらない。** 訓練データは 0.9333 → 1.0000 と上がり続けるのに、テストデータは 0.9556 のままです。訓練データにだけ合わせ込んでいく、**過学習**の始まりです

深さ 2 の木は、テストデータ 45 件のうち 43 件を正しく分類しました。テストにも書いています。

```ruby
def test_深さ二の決定木はテストデータの四十五件中四十三件を正しく分類する
  data = split
  predictions = C::DecisionTree.new(max_depth: 2).fit(data.x_train, data.t_train).predict(data.x_test)

  assert_in_delta 43.0 / 45, GettingStartedMl::Chapter01.accuracy(predictions, data.t_test), 1e-12
end
```

期待値を `0.9556` ではなく `43.0 / 45` と書いているのは、3.4 節の `2.0 / 3` と同じ理由です。**丸めた値をテストに書かない**、という規律です。

### ほかの言語版と数値が一致しない

この章の正解率は、**ほかの言語版と一致しません**。第 2 章で書いたとおり、訓練データとテストデータの分け方が違うからです。Rust 版は深さ 2 のテストデータの正解率が 0.9111、木の 2 つめの境界が 0.6900 でした。Ruby 版は 0.9556 と 0.6500 です。

Python 版の深さ 2 のテストデータの正解率も 0.9556 ですが、これは偶然です。Python 版は深さ 3 以上で 0.9111 に下がり、Ruby 版は 0.9556 のままなので、表全体で見れば違う数字の並びになっています。

一致するのは次の点です。

- 深さ 1 では足りず、深さ 2 で一気に上がること
- 訓練データの正解率だけが上がり続けること（過学習）
- 深さ 2 の木が「花弁幅」を 2 回使って 3 種類に分けること（Rust 版とは最初の境界 0.2950 まで同じ）

**数値ではなく、現象が一致します。**

`run` の出力を固定するテストも置いています。

```ruby
def test_実行すると深さごとの正解率と決定木を表示する
  split
  out = StringIO.new
  C.run(out)

  assert_equal <<~TEXT, out.string
    深さ\t訓練データ\tテストデータ\tRumale
    1\t0.6762\t0.6444\t0.6444
    2\t0.9333\t0.9556\t0.9556
    3\t0.9524\t0.9556\t0.9556
    4\t0.9524\t0.9556\t0.9556
    5\t0.9714\t0.9556\t0.9556
    制限なし\t1.0000\t0.9556\t0.9333

    深さ 2 の決定木:
    花弁幅 <= 0.2950
      Iris-setosa
    花弁幅 > 0.2950
      花弁幅 <= 0.6500
        Iris-versicolor
      花弁幅 > 0.6500
        Iris-virginica
  TEXT
end
```

最初の `split` は、戻り値を使わずに呼んでいます。データが無ければ `split` の中で `skip` するので、`run` まで進みません。

`run(out)` に `StringIO` を渡すと、出力を文字列として受け取れます。第 1 章で `out = $stdout` を既定値つきの引数にした設計が、ここで効きました。Rust 版は `Vec<u8>` を `std::io::Write` として渡しました。

`<<~TEXT` は**字下げを取り除くヒアドキュメント**です。いちばん浅い行の字下げを基準に、各行の先頭の空白を取り除きます。期待する出力を、字下げ付きの木の形のまま書けます。Rust 版は文字列リテラルの行末の `\` で折り返し、残したい字下げを `\x20` で書く必要がありました。

ヒアドキュメントの中の `\t` はタブに置き換わります。`<<~TEXT` は二重引用符の文字列と同じ扱いだからです。

このテストのメソッドは 20 行を超えるので、第 2 章で `test/` の中だけ `Metrics/MethodLength` を外したのはこのためです。

## 3.12 可視化について

Python 版では、この章で決定木を図として描きました。Ruby 版では[第 2 章](02-data-preprocessing-and-triangulation.md) と同じ理由で、可視化の節を作りません。

木の図がどう見えるかは、[Python 版の第 3 章](../python/03-decision-tree-and-obvious-implementation.md) の「Notebook で探索する」の節を参照してください。この章の `format` が出力する字下げ付きのテキストも、同じ木を表しています。

## 3.13 まとめ

この章では、ジニ不純度で分割する決定木を実装し、Rumale の決定木と突き合わせました。Ruby に固有の論点は次のとおりです。

1. **葉と節を `Data.define` で作り、`case`/`in` で見分ける** — Rust の `enum` と `match` に近く書け、入れ子のパターンで `Split` の中まで取り出せる。`Box` は要らない。ただし網羅性は検査されず、型を足したときの漏れは `NoMatchingPatternError` として実行時に分かる。見つけるのはテスト
2. **`tally` と `max_by`・`min_by` で同点の扱いがそろう** — `tally` は現れた順を保ち、`max_by`・`min_by` は同点のとき最初の要素を返す。Rust 版が `max_by_key` の「同点なら最後」で 2 件落ちた場面で、Ruby 版は標準のメソッドがそのまま使えた。それを確かめているのはテスト
3. **`sort_by` は安定ではない** — 元の位置を 2 つ目の鍵にして `[value, index]` で並べ、同じ値の並びを固定した
4. **同じ名前が何を指すかは実行時の探索順で決まる** — 自分の `format` が `Kernel#format` を隠すので、標準のほうは `Kernel.format` と明示して呼ぶ
5. **ランダムでないはずのアルゴリズムにも乱数が入っていることがある** — Rumale は節ごとに特徴量を `Array#sample` でランダムな順に並べるので、同点の分割の選び方がシードで変わる。深さ 1〜5 では自作と予測が完全に一致し、制限なしで 1 件分かれた。シードを変えると、分かれる件数が 0〜2 件で揺れる
6. **静的解析の上限に合わせてメソッドを分ける** — `Metrics/AbcSize`（上限 17）に合わせて `best_split` を `candidates`・`split_at`・`weighted_gini` に分け、それぞれに名前が付いた

機械学習としては、次を確かめました。

- 決定木はルールを**データから作る**。第 1 章で人間が書いた「20 代ならきのこ」に当たる分岐が、花弁幅の 0.2950 と 0.6500 として自動で決まった
- 深くするほど訓練データの正解率は上がる（1.0000 まで）が、テストデータは 0.9556 で頭打ちになる。**過学習**

**TODO リスト（この章の完了時点）**:

- [x] ジニ不純度を計算する
- [x] いちばん多いラベルを返す
- [x] 最良の分割を探す
- [x] 決定木を学習して予測する
- [x] 木の深さを制限する
- [x] 学習した木を表示する
- [x] Rumale の決定木と突き合わせる
- [x] 実データで深さと正解率を表示する

次の章からは、別のデータセットで前処理の引き出しを増やします。この章で作った `DecisionTree` と第 2 章の `Features`・`TrainTestSplit` は、以降の章でも使い続けます。
