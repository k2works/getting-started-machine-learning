---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "Numo の行列演算によるソフトマックスと勾配降下のロジスティック回帰、第 3 章の決定木を再利用したランダムフォレストと特徴量の重要度を Ruby の TDD で自作し、ダックタイピングで Rumale と並べて評価する。正則化を外して収束の判定を厳しくすると予測が完全に一致することを実測する。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:31:00Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。どのモデルも同じ関数で評価できるようにし、最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作します。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進め、[Rust 版](../rust/10-logistic-regression-and-ensemble.md) と対比します。Ruby 版で拾う論点は次の 3 つです。

- **共通の型を宣言しない**。Rust 版は `trait Classifier` を定義し、第 3 章の決定木に `impl Classifier for DecisionTree` を後から足しました。Ruby 版は何も宣言しません。`fit` と `predict` を持っていれば、評価の関数に渡せます（ダックタイピング）
- **行列の演算は Numo で書く**。Rust 版は `Vec<Vec<f64>>` と添字で勾配を求めました。Ruby 版は Rumale が依存している Numo の行列の積で、NumPy と同じ形に書きます
- **Rumale の既定値は突き合わせるまで分からない**。Rumale の `LogisticRegression` は既定で L2 正則化が入り、収束の判定もゆるめです。両方をそろえると、自作と予測が完全に一致しました

データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `prepare_iris` で前処理します。乱数に `Random.new(0)` を使うので、訓練データとテストデータに入る行はほかの言語版と違い、正解率も一致しません（第 2 章）。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 値がすべて同じなら確率は均等になる
  - [ ] 値の差が指数の比になる
  - [ ] 大きな値でもあふれない
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 2 種類・3 種類のラベルを予測する
  - [ ] 学習を繰り返すと損失が小さくなる
- [ ] ランダムフォレストを学習して予測する
  - [ ] 多数決で予測を 1 つに決める
  - [ ] ブートストラップ標本を選ぶ
  - [ ] 第 3 章の決定木を再利用する
- [ ] 特徴量の重要度を計算する
  - [ ] 決定木 1 本の重要度
  - [ ] ランダムフォレストの重要度
- [ ] どのモデルも同じ関数で評価する
- [ ] 実データで Rumale と正解率・予測・重要度を突き合わせる

この章のコードは `lib/getting_started_ml/chapter10/` に置きます。

| ファイル | 役割 |
|---------|------|
| `logistic_regression.rb` | ソフトマックス、交差エントロピー、ロジスティック回帰 |
| `random_forest.rb` | 多数決、ブートストラップ標本、ランダムフォレスト |
| `importance.rb` | 特徴量の重要度と、モデル共通の評価（`Score.evaluate`） |
| `rumale_models.rb` | Rumale のロジスティック回帰とランダムフォレストの呼び出し |

新しい失敗は「学習する前に予測した」だけで、第 3 章の決定木と同じ `RuntimeError`（「学習してから予測してください」）にそろえます。

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和 + 切片」を計算し、それを **ソフトマックス関数** で確率に変換します。

```text
z_k = w_1k * x_1 + w_2k * x_2 + ... + b_k     （品種 k のスコア）
p_k = exp(z_k) / Σ_j exp(z_j)                 （ソフトマックス）
```

### 三角測量

まず、性質を 2 つテストにします。

```ruby
def test_値がすべて同じなら確率は均等になる
  C.softmax([1.0, 1.0, 1.0]).each { |probability| assert_in_delta 1.0 / 3, probability, 1e-12 }
end

def test_値の差が指数の比になる
  probabilities = C.softmax([0.0, 1.0])

  assert_in_delta Math::E, probabilities[1] / probabilities[0], 1e-12
  assert_in_delta 1.0, probabilities.sum, 1e-12
end
```

学習ではデータ全件のスコアを行列でまとめて扱うので、ソフトマックスも「行列の行ごと」に求める `softmax_rows` を本体にし、1 行だけの `softmax` はその薄い包みにしました。定義どおりに書くと次のとおりです。

```ruby
# 行列の行ごとにソフトマックスを求める。
def softmax_rows(scores)
  exps = Numo::NMath.exp(scores)

  exps / exps.sum(axis: 1).expand_dims(1)
end
```

`exps.sum(axis: 1)` は行ごとの合計（件数の長さのベクトル）で、`expand_dims(1)` で「件数 × 1」の行列にしてから割ります。Numo は形の違う行列どうしの演算で、長さ 1 の次元を相手に合わせて広げる（ブロードキャスト）ので、行ごとに合計で割れます。NumPy の `keepdims=True` と同じ書き方です。この 2 つのテストは通ります。

### 大きな値でもあふれない

3 つめのテストで、この実装は壊れます。

```ruby
def test_大きな値でもあふれない
  # 定義どおり exp(1000) を求めると Infinity になり、Infinity / Infinity が NaN になる
  assert_predicate Math.exp(1000), :infinite?

  probabilities = C.softmax([1000.0, 1001.0])

  assert probabilities.all?(&:finite?), probabilities.inspect
  assert_in_delta 1.0, probabilities.sum, 1e-12
end
```

```text
  1) Failure:
Chapter10LogisticTest#test_大きな値でもあふれない [test/chapter10_logistic_test.rb:29]:
[NaN, NaN]
```

`Math.exp(1000)` は例外を出さずに `Infinity` を返し、`Infinity / Infinity` は `NaN` になります。Ruby は `Integer` なら桁あふれせずに多倍長整数へ広がりますが、**`Float` のあふれは黙って `Infinity` になります**。Rust 版・Java 版と同じく、境界の値のテストを自分で書くしかありません。

直し方は、スコアから行ごとの最大値を引いてから `exp` を取ることです。全部の項を同じ数で割ることになるので、確率は変わりません。

```ruby
# 行列の行ごとにソフトマックスを求める。
# 行ごとの最大値を引いてから exp を求めるので、大きな値でもあふれない。
def softmax_rows(scores)
  exps = Numo::NMath.exp(scores - scores.max(axis: 1).expand_dims(1))

  exps / exps.sum(axis: 1).expand_dims(1)
end
```

Rust 版は、`f64` が `Ord` を実装していないため最大値を `fold(f64::NEG_INFINITY, f64::max)` で求めました。Numo は `max(axis: 1)` で行ごとの最大値を返します。Rumale のロジスティック回帰も、ソースを読むと同じく `z.max(-1)` を引いてから `exp` を取っていました。

## 10.4 ロジスティック回帰

### 損失と勾配

学習の良し悪しは **交差エントロピー** で測ります。正解の品種の確率の対数の平均に、マイナスを付けたものです。

```ruby
# 交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。
def cross_entropy(probabilities, targets)
  return 0.0 if probabilities.empty?

  -probabilities.zip(targets).sum { |probability, target| Math.log(probability[target] + EPSILON) } /
    probabilities.size
end
```

確率が 0 のとき `Math.log(0)` が `-Infinity` になるので、ごく小さい `EPSILON`（1e-12）を足します。

ソフトマックスと交差エントロピーを組み合わせると、勾配は「確率 − 正解（正解の品種だけ 1）」を特徴量で重み付けした、簡単な形になります。

### バッチ勾配降下法を行列で書く

Rust 版は `Vec<Vec<f64>>` を添字で回し、品種と特徴量の二重ループで勾配を集めました。Ruby 版は Numo の行列の積で書きます。

```ruby
# 訓練データで学習する。繰り返しごとの損失を losses に残す。メソッドをつなげられるように自分を返す。
def fit(x, t)
  @classes = t.uniq.sort
  features = Numo::DFloat.cast(x.map(&:values))
  targets = t.map { |label| @classes.index(label) }
  @weights = Numo::DFloat.zeros(features.shape[1], @classes.size)
  @bias = Numo::DFloat.zeros(@classes.size)
  @losses = Array.new(@epochs) { step(features, targets, one_hot(targets)) }
  self
end
```

```ruby
# 全データの勾配を集めてから 1 回だけ重みを動かす（バッチ勾配降下法）。更新前の損失を返す。
# 勾配は「確率 − 正解（正解の品種だけ 1）」を特徴量で重み付けしたものになる。
def step(features, targets, expected)
  probabilities = Chapter10.softmax_rows(scores(features))
  errors = probabilities - expected
  count = features.shape[0]
  @weights -= @learning_rate * features.transpose.dot(errors) / count
  @bias -= @learning_rate * errors.sum(axis: 0) / count

  Chapter10.cross_entropy(probabilities.to_a, targets)
end
```

`features.transpose.dot(errors)` の 1 行が、Rust 版の「品種ごと・特徴量ごとに、全データの `特徴量 × 誤差` を足す」三重のループに当たります。`Array.new(@epochs) { step(...) }` は「ブロックを繰り返し回数だけ呼び、戻り値を並べる」書き方で、学習と損失の記録が 1 行に収まります。

`fit` は `self` を返すので、`LogisticRegression.new.fit(x, t).predict(x)` とつなげて書けます。第 3 章の `DecisionTree#fit` と同じ約束で、この約束が 10.5 節で効いてきます。

学習率 1.0・繰り返し 5000 回（Rust 版と同じ既定値）で、iris の訓練データ 105 件の学習は手元で 1 秒前後でした。

```ruby
def test_学習を繰り返すと損失が小さくなる
  x = [column(0.2), column(0.3), column(2.3), column(2.5)]
  losses = C::LogisticRegression.new.fit(x, %w[setosa setosa virginica virginica]).losses

  assert_equal 5000, losses.size
  assert_operator losses.first, :>, losses.last
end
```

Rust 版は、既定値を `Default` トレイトで表しました。Ruby 版はキーワード引数の既定値（`initialize(learning_rate: 1.0, epochs: 5000)`）で表し、`LogisticRegression.new` と `LogisticRegression.new(epochs: 20_000)` のように、変えたい値だけを名前付きで渡します。

## 10.5 どのモデルも同じ関数で評価する

### 型を宣言しない

モデルを学習させて、訓練データとテストデータの正解率を求める関数を書きます。

```ruby
# 訓練データとテストデータの正解率。
Score = Data.define(:train, :test) do
  # モデルを訓練データで学習させてから、訓練データとテストデータの正解率を求める。
  # fit と predict を持つものなら何でも受け取る（ダックタイピング）。
  def self.evaluate(model, split)
    model.fit(split.x_train, split.t_train)

    new(train: Chapter01.accuracy(model.predict(split.x_train), split.t_train),
        test: Chapter01.accuracy(model.predict(split.x_test), split.t_test))
  end
end
```

引数の `model` が何の型かは、どこにも書いていません。`fit` と `predict` に応答すれば動きます。第 3 章の `DecisionTree` も、この章の `LogisticRegression` も `RandomForest` も、同じ関数に渡せることをテストで確かめます。

```ruby
def test_どのモデルも同じ関数で評価する
  x, t = separable
  split = GettingStartedMl::Chapter02::TrainTestSplit.new(x_train: x, x_test: x.first(2), t_train: t,
                                                          t_test: t.first(2))

  [Chapter03::DecisionTree.new(max_depth: 1), C::LogisticRegression.new,
   C::RandomForest.new(n_estimators: 5, max_features: 2)].each do |model|
    assert_equal C::Score.new(train: 1.0, test: 1.0), C::Score.evaluate(model, split), model.class.name
  end
end
```

| 言語 | 既存の型を共通の型に合わせるには |
|------|----------------------------|
| Ruby | 何も書かない。`fit` と `predict` に応答すればよい（ダックタイピング） |
| Rust | トレイトが自分のクレートのものなら `impl Classifier for DecisionTree` を後から書ける |
| Java・C# | 型の宣言に `implements`／`:` が要るので、アダプターのクラスを書く |
| Go | 構造的部分型。メソッドの形が合えば、何も書かなくても満たす |

Ruby と Go はどちらも「宣言が要らない」側ですが、検査の時期が違います。Go は **コンパイル時に** メソッドの形が合っているかを検査します。Ruby は **呼んだ瞬間に** `NoMethodError` になるまで分かりません。第 3 章の `DecisionTree#fit` の戻り値は `self` で、この章の `Score.evaluate` は戻り値を使っていないので、たまたま問題になりませんでした。もし `fit` が `self` を返す約束を前提に `model.fit(...).predict(...)` と書いていたら、`self` を返さない `fit` を持つモデルは実行時に初めて壊れます。**約束がコードに書かれていない分、約束を確かめるのはテストの役目** になります。上のテストで 3 種類のモデルを並べて回しているのは、そのためです。

Rust 版は、第 3 章の `fit` の戻り値（`Result<&mut Self>`）がトレイトの形と違うので、`DecisionTree::fit(self, ...)` と `Classifier::fit(...)` を呼び分ける必要がありました。Ruby 版は名前が同じなら同じメソッドなので、呼び分けという概念自体がありません。

### モデルを並べる

表示のためにモデルを並べるときも、共通の型は要りません。`Hash` は入れた順を保つので、表示の順にそのまま書きます。

```ruby
# 名前とモデル。表示する順に並べる。どれも fit と predict を持つだけで、共通の親クラスは無い。
def self.models
  { "決定木（深さ #{SHALLOW_DEPTH}）" => Chapter03::DecisionTree.new(max_depth: SHALLOW_DEPTH),
    "ロジスティック回帰" => LogisticRegression.new,
    "ランダムフォレスト（#{N_ESTIMATORS} 本）" => forest,
    "ランダムフォレスト（#{N_ESTIMATORS} 本・深さ #{SHALLOW_DEPTH}）" => forest(max_depth: SHALLOW_DEPTH),
    "Rumale ロジスティック回帰（既定）" => RumaleLogisticRegression.new,
    "Rumale ロジスティック回帰（正則化なし）" => RumaleLogisticRegression.new(reg_param: 0.0, tol: STRICT_TOL),
    "Rumale ランダムフォレスト（#{N_ESTIMATORS} 本）" => rumale_forest }
end
```

Rust 版は、種類の違うモデルを 1 つの `Vec` に入れるために `Box<dyn Classifier>`（トレイトオブジェクト）が必要でした。Ruby の `Hash` や `Array` は、もともと何でも入ります。

## 10.6 ランダムフォレスト

### 仕組み

1. 訓練データから、重複を許して同じ件数を選ぶ（**ブートストラップ標本**）
2. 特徴量の一部だけを使って決定木を 1 本学習する
3. 1 と 2 を木の本数だけ繰り返す
4. 予測は全部の木の **多数決** で決める

### 多数決とブートストラップ標本

```ruby
def test_多数決で予測を一つに決める
  assert_equal %w[a c], C.majority_vote([%w[a b], %w[a c], %w[b c]])
end

def test_同数なら先に現れた予測を選ぶ
  assert_equal %w[b], C.majority_vote([%w[b], %w[a]])
end
```

「同数なら先に現れたほう」は、第 3 章の `majority` がすでに満たしている性質です。そのまま呼びます。

```ruby
# サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ（第 3 章の majority と同じ）。
def majority_vote(votes)
  return [] if votes.empty?

  votes.transpose.map { |labels| Chapter03.majority(labels) }
end
```

`votes` は「木ごとの予測の並び」の並びです。`transpose` で「サンプルごとの、木ごとの予測」に組み替えれば、あとはサンプルごとに多数決を取るだけです。Rust 版の「サンプルの番号で回し、木ごとの予測を集め直す」処理が、`transpose` の 1 語になります。

ブートストラップ標本と、木ごとに使う特徴量の選び方です。

```ruby
# 0 から size - 1 までの行番号を、重複を許して size 個選ぶ。
def bootstrap_sample(size, rng)
  Array.new(size) { rng.rand(size) }
end

# 重複なしで count 個の列を選ぶ。選んだ列は元の順に並べ直す。
def choose(columns, count, rng)
  columns & columns.sample(count, random: rng)
end
```

`columns & 選んだ列` は配列の積集合で、**左側の順を保ちます**。選んだ列を元の表の順に並べ直す処理が、演算子 1 つで済みます。列の順が木ごとにばらばらだと、特徴量の重要度を足し合わせるときに対応を取りにくくなるからです。

`choose` は最初、Rust 版と同じく「並べ替えて先頭から選ぶ」つもりで `columns.shuffle(random: rng).first(count)` と書きました。すると RuboCop の `Style/Sample` が「`sample(count, random: rng)` を使え」と指摘しました。直すと、**同じシード 0 でも選ばれる列が変わり、実データの出力が変わりました**。`shuffle` と `sample` は乱数の使い方が違うからです。深さ 2 に制限した森の正解率は、訓練データ 0.9333 → 0.9238、テストデータ 0.9556 → 0.9333 に動きました。静的解析の指摘に従うだけの「振る舞いを変えないはずのリファクタリング」でも、乱数を使うコードでは結果が変わることがあります。記事の数値は `sample` に直したあとの実測値です。

### 森を作る

1 本分の情報は `Data` にまとめます。特徴量の重要度を求めるときに、その木が使った列と標本の行番号が要るためです。

```ruby
# ランダムフォレストの 1 本分。使った特徴量の列、ブートストラップ標本の行番号、学習した決定木。
FittedTree = Data.define(:columns, :rows, :model)
```

```ruby
# 乱数の生成器を 1 つだけ作り、森全体で使い回す（木ごとに作り直すと全部の木が同じ標本になる）。
def fit(x, t)
  rng = Random.new(@seed)
  @trees = Array.new(@n_estimators) { plant(x, t, rng) }
  self
end

# 1 本分の標本と列を選び、決定木を学習する。
def plant(x, t, rng)
  rows = Chapter10.bootstrap_sample(x.size, rng)
  columns = Chapter10.choose(x.first.columns, @max_features, rng)
  sample_x = Chapter10.select_columns(x.values_at(*rows), columns)
  model = Chapter03::DecisionTree.new(max_depth: @max_depth).fit(sample_x, t.values_at(*rows))

  FittedTree.new(columns:, rows:, model:)
end
```

`x.values_at(*rows)` は、行番号の並びで要素を引き直す書き方です。重複した行番号があれば、同じ行が何度でも入ります。深さを制限しないことは、第 3 章と同じく `max_depth: nil` で表します。Rust 版の `Option<usize>` と同じく「無い」を値で表しますが、Ruby では `nil` を渡し忘れても、渡し間違えても（たとえば `max_depth: "2"`）、決定木の中で `zero?` を呼ぶまで気づきません。

## 10.7 特徴量の重要度

### 計算方法

決定木は、分割のたびに不純度（ジニ不純度）を下げます。「その分割でどれだけ不純度が下がったか」を、分割に使った特徴量の得点として足していきます。件数が多い分割ほど重みを大きくしたいので、その節に来たデータの件数を掛けます。

```text
その分割の得点 = 件数 × (分割前のジニ不純度 − 分割後の重み付き平均の不純度)
```

第 3 章の `Split` は `impurity` に「分割後の左右の重み付き平均」を持っているので、そのまま使えます。

### 決定木 1 本の重要度

木をたどるところは、第 3 章の `Leaf`・`Node` のパターンマッチで書きます。

```ruby
# 木をたどって、分割ごとに減った不純度（件数で重み付け）を足し込む。葉では何もしない。
def accumulate(tree, x, t, totals)
  return unless tree in Chapter03::Node(split:, left:, right:)

  totals[split.feature] += t.size * (Chapter03.gini(t) - split.impurity)
  goes_left, goes_right = x.zip(t).partition { |features, _| Chapter03.goes_left?(split, features) }
  accumulate(left, *goes_left.transpose, totals)
  accumulate(right, *goes_right.transpose, totals)
end
```

`tree in Chapter03::Node(split:, left:, right:)` は、形が合えば `true` を返し、同時に `split`・`left`・`right` を変数に取り出します。葉なら `false` なので早期に戻ります。Rust 版の `let Tree::Node { split, left, right } = tree else { return Ok(()); };` とほぼ同じ形です。

```ruby
def test_使った特徴量だけが重要度を持つ
  x, t = separable
  tree = Chapter03::DecisionTree.new(max_depth: 1).fit(x, t).tree

  assert_equal [["花弁幅", 1.0], ["花弁長さ", 0.0]], C.tree_importances(tree, x, t)
end
```

### ランダムフォレストの重要度

森の重要度は、**木ごとに正規化してから平均** します。

```ruby
def test_森の重要度の合計は一になる
  x, t = separable
  forest = C::RandomForest.new(n_estimators: 10, max_features: 2, seed: 0).fit(x, t)

  assert_in_delta 1.0, C.forest_importances(forest, x, t).sum(&:last), 1e-12
end
```

最初の実装では、このテストが失敗しました。

```text
  1) Failure:
Chapter10ForestTest#test_森の重要度の合計は一になる [test/chapter10_forest_test.rb:92]:
Expected |1.0 - 0.7999999999999999| (0.20000000000000007) to be <= 1.0e-12.
```

4 件しかないデータからブートストラップ標本を選ぶと、10 本のうち 2 本は **1 種類のラベルだけの標本** になり、木が葉 1 つで終わります。葉だけの木は重要度が全部 0 なので、木ごとの重要度を平均すると合計が 0.8 になりました。Rust 版を読み返すと、平均したあとにもう一度正規化していました。同じようにして通しました。

```ruby
# 森の重要度。木ごとに正規化してから平均する。木が使わなかった特徴量は、その木では 0 として扱う。
# 葉だけの木（標本が 1 種類のラベルだけ）は重要度が全部 0 なので、最後にもう一度合計が 1 になるように割る。
def forest_importances(forest, x, t)
  totals = x.first.columns.to_h { |column| [column, 0.0] }

  forest.trees.each do |fitted|
    fitted_importances(fitted, x, t).each { |column, value| totals[column] += value / forest.trees.size }
  end

  normalize(totals).to_a
end
```

小さなデータのテストが、実データでは起こりにくい端のケース（葉だけの木）を先に見つけてくれた例です。

## 10.8 Rumale と突き合わせる

### Rumale のモデルを同じ形で呼ぶ

Rumale のモデルは、ラベルに整数（`Numo::Int32`）を求めます。第 3 章の `RumaleTree.encode`（ラベルを番号にする）と `matrix`（特徴量を行列にする）を使い回し、`fit` と `predict` を持つ小さな包みにしました。2 つのモデルで共通の部分は、モジュールにして `include` します。

```ruby
# Rumale のモデルに文字列のラベルで fit・predict するための共通部分。
# ラベルの番号付けと行列への変換は、第 3 章の RumaleTree のものを使う。
module RumaleClassifier
  # 訓練データで学習する。メソッドをつなげられるように自分を返す。
  def fit(x, t)
    codes, @classes = Chapter03::RumaleTree.encode(t)
    @model = build.fit(Chapter03::RumaleTree.matrix(x), Numo::Int32.cast(codes))
    self
  end

  # 特徴量ごとのラベルを予測する。
  def predict(x)
    raise "学習してから予測してください" if @model.nil?

    @model.predict(Chapter03::RumaleTree.matrix(x)).to_a.map { |code| @classes[code] }
  end
end
```

`include` したクラスは、Rumale のモデルを作る `build` だけを書きます。`RumaleClassifier` は `build` を呼びますが、`build` があることはどこにも宣言していません。これもダックタイピングで、Rust ならトレイトの必須メソッドとして書くところです。

### ロジスティック回帰: 正則化と収束の判定

Rumale の `LinearModel::LogisticRegression` の既定値を `params` で確かめると、`reg_param: 1.0`（L2 正則化の強さ）、`max_iter: 1000`、`tol: 0.0001` でした。自作は正則化を入れていないので、このままでは別のモデルです。

Rumale 2.2.0 のソースを読むと、多クラスの目的関数は「交差エントロピーの **合計** + 0.5 × `reg_param` × ‖w‖²」で、切片も重みのベクトルに含めて正則化していました。これを L-BFGS-B（`Numo::Optimize.minimize`）で最小化し、収束の判定には `factr = tol / 機械イプシロン` を渡しています。既定の `tol: 1e-4` だと `factr` は約 4.5 × 10¹¹ で、**かなりゆるい判定** です。

設定を 1 つずつ変えて、テストデータ 45 件の予測を自作（5000 回）と比べました。

| 実装 | 設定 | 訓練データ | テストデータ | 自作との予測の差 |
|------|------|-----------|-------------|----------------|
| 自作 | 1000 回 | 0.8952 | 0.9333 | — |
| 自作 | 5000 回（既定） | 0.8952 | 0.9556 | — |
| 自作 | 20000 回 | 0.8952 | 0.9556 | — |
| Rumale | reg_param = 1.0・tol = 1e-4（既定） | 0.8381 | 0.8667 | 6 件 |
| Rumale | reg_param = 1.0・tol = 1e-8 | 0.8381 | 0.8667 | 6 件 |
| Rumale | reg_param = 0・tol = 1e-4 | 0.8952 | 0.9333 | 1 件 |
| Rumale | reg_param = 0・tol = 1e-8 | 0.8952 | 0.9556 | **0 件** |

読み取れることは 2 つです。

- **違いの大部分は正則化**。既定の L2（reg_param = 1.0）は、この分割では訓練データ・テストデータの両方の正解率を下げました（0.8952 → 0.8381、0.9556 → 0.8667）。Rust 版の linfa（alpha = 1.0）はテストデータの正解率を上げていたので、同じ「既定の L2」でも向きが違います。Rumale は切片まで正則化するので、原点に引っ張る力が強く出ます
- **残りの 1 件は収束の判定**。正則化を外しても、既定の `tol` では 1 件だけ予測が違いました。`tol` を 1e-8 に厳しくすると、**テストデータ 45 件の予測が 1 件も違わず一致** しました。自作も 1000 回では同じ 1 件を間違えるので、どちらも「最小値の手前で止まると、境界にある 1 件の予測が変わる」という同じ現象です

```ruby
# Rumale の既定は L2 正則化（reg_param: 1.0）があり、収束の判定（tol: 1e-4）もゆるい。
# 正則化を外し、tol を 1e-8 に厳しくすると、テストデータ 45 件の予測が自作と 1 件も違わない
def test_正則化を外して収束を厳しくすると自作とRumaleの予測が一致する
  data = split

  assert_equal predictions(C::LogisticRegression.new, data),
               predictions(C::RumaleLogisticRegression.new(reg_param: 0.0, tol: 1e-8), data)
end
```

学習の解き方は違う（自作はバッチ勾配降下法、Rumale は L-BFGS-B）のに、設定をそろえれば同じ予測にたどり着きます。Rust 版は正則化（alpha）をそろえるだけで一致しましたが、Ruby 版は **収束の判定という 2 つめの設定** もそろえる必要がありました。「同じロジスティック回帰」でも、そろえるべき設定はライブラリごとに違います。

### ランダムフォレスト: 再現性と「特徴量の選び方」

Rumale の `Ensemble::RandomForestClassifier` は、Rust の linfa には無かった部品です。自作と同じく木 100 本、`max_features: 2`、`random_seed: 0` で比べました。

ソースを読むと、自作と Rumale では **特徴量の選び方が違います**。

| | 自作（Rust 版と同じ） | Rumale |
|---|---|---|
| 特徴量の部分集合 | **木ごと** に 2 列を選び、その木はその 2 列だけで分割する | **節ごと** に `max_features` 列を調べて分割を選ぶ（scikit-learn と同じ） |
| `max_features` の既定 | —（必ず指定する） | √（特徴量の数）の整数部分 |
| 重要度 | 木ごとに正規化して平均し、最後に正規化 | 同じ（木ごとの `feature_importances` を足して正規化） |

乱数の扱いについても 2 つのことを確かめました。

- **`random_seed` を渡せば再現する**。同じシードで 2 回作ると、重要度も予測も一致しました（`test/chapter10_rumale_test.rb`）。`fit` の中で乱数の生成器を複製してから使うので、同じモデルに 2 回 `fit` しても同じ森になります
- **`random_seed` を省くと、実行のたびに結果が変わり、しかもプロセス全体の乱数を初期化し直す**。既定値が `random_seed || srand` と書かれていて、`Kernel#srand` はシードを返すと同時に **グローバルな乱数の種を新しく入れ替えます**。`srand(42)` のあとに `rand` を呼ぶ場合と、`srand(42)` のあとに `RandomForestClassifier.new` を挟んでから `rand` を呼ぶ場合で、値が変わりました。シードを渡した場合は変わりませんでした。Rumale の決定木（`DecisionTreeClassifier`）も同じ書き方です。**Rumale のモデルには必ず `random_seed` を渡す** ことにします

## 10.9 実データで突き合わせる

```bash
bundle exec rake 'run[chapter10]'
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9333	0.9556
ロジスティック回帰	0.8952	0.9556
ランダムフォレスト（100 本）	1.0000	0.9333
ランダムフォレスト（100 本・深さ 2）	0.9238	0.9333
Rumale ロジスティック回帰（既定）	0.8381	0.8667
Rumale ロジスティック回帰（正則化なし）	0.8952	0.9556
Rumale ランダムフォレスト（100 本）	1.0000	0.9333

ランダムフォレスト（100 本）の特徴量の重要度:
特徴量	自作	Rumale
がく片長さ	0.2128	0.1756
がく片幅	0.1062	0.0593
花弁長さ	0.2587	0.1971
花弁幅	0.4223	0.5681
```

この表示は `test/iris_models_test.rb` で固定しています。結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではない**。深さを制限しないランダムフォレスト（0.9333）は、第 3 章の深さ 2 の決定木（0.9556）を上回りませんでした。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。この傾向は Rust 版・Java 版・Kotlin 版と同じです。ただし、10.6 節で見たとおり「深く育てた森」と「深さ 2 の森」の優劣は `shuffle` と `sample` の違いだけで入れ替わりました。テストに残したのは、分け方に左右されにくい「深く育てた森は訓練データを全部当てるが、深さ 2 の決定木を上回らない」のほうです
- **自作と Rumale の森は、正解率が同じでも中身が違う**。100 本の森の正解率は両方とも 1.0000・0.9333 でしたが、重要度は違います。Rumale は節ごとに 4 列から 2 列を選ぶので、花弁幅を使える機会が多く、花弁幅の重要度が大きく出ます（0.5681）。自作は木ごとに 2 列に絞るので、花弁幅を使えない木が別の特徴量で分割し、重要度がほかの特徴量に分散します（0.4223）。花弁幅が最も大きいという順位は、両方とも Rust 版・Java 版と同じです
- **木の本数や深さで結果が動く**。自作の森を 10 本にするとテストデータで 0.9111、深さ 3 に制限しても 0.9111 でした。テストデータ 45 件では 1 件の違いが 0.0222 の差になるので、1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います

データが無い環境では、実データのテストは Minitest の `skip` でスキップされます。第 9 章・第 10 章を足した時点で、`bundle exec rake check` は 109 件のテストがすべて通り、データが無い環境（`ML_DATA_DIR=/nonexistent`）では 14 件がスキップになりました。

## 10.10 Notebook で探索する

Ruby 版では Notebook と可視化を扱いません。モデルごとの正解率のグラフ、ロジスティック回帰の損失の推移、モデル別の特徴量の重要度、森の大きさと正解率の関係は、[Python 版の 10.11 節](../python/10-logistic-regression-and-ensemble.md) と [Kotlin 版の 10.11 節](../kotlin/10-logistic-regression-and-ensemble.md) の可視化を参照してください。Ruby 版の `LogisticRegression#losses`（繰り返しごとの損失）と `forest_importances`（列の順に並んだ重要度）は、ほかの言語版と同じ形のデータを返すので、同じ観点で読めます。分割が違うので、値そのものは一致しません。

## 10.11 リファクタリング

TODO リストを終えてから `bundle exec rake check` をかけると、RuboCop が 6 件を指摘しました。

- **`Metrics/AbcSize`** — `forest_importances` が 22.34 で上限 17 を超えました。1 本分の重要度を求める部分を `fitted_importances` に切り出しました
- **`Naming/MethodParameterName`** — `softmax(z)` の `z` が 3 文字未満で咎められ、`scores` にしました
- **`Style/Sample`** — 10.6 節で書いたとおり、`shuffle(random: rng).first(count)` を `sample(count, random: rng)` に直しました。この 1 件だけは、直すと実データの出力が変わりました
- **`Lint/AmbiguousBlockAssociation`・`Layout/LineLength`** — テストの `assert rows.all? { ... }` のように、括弧の無い引数にブロックを付けた書き方が「ブロックがどちらのメソッドに付くか紛らわしい」と指摘されました。`rubocop -a` で括弧を補いました

## 10.12 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、Rumale と並べて評価しました。

| モデル | 自作したもの | 突き合わせたライブラリ |
|-------|------------|-------------------|
| ロジスティック回帰 | `LogisticRegression`（Numo の行列演算・ソフトマックス・バッチ勾配降下法） | Rumale の `LogisticRegression`（正則化を外し、`tol` を 1e-8 にすると予測が完全に一致） |
| ランダムフォレスト | `RandomForest`・`FittedTree`・`majority_vote` | Rumale の `RandomForestClassifier`（正解率は一致、特徴量を節ごとに選ぶので重要度は違う） |
| 特徴量の重要度 | `tree_importances`・`forest_importances` | Rumale の `feature_importances`（計算方法は同じ） |

Ruby らしさが出たのは次の 4 点です。

1. **共通の型を宣言しない** — `fit` と `predict` を持っていれば、第 3 章の決定木も Rumale の包みも同じ `Score.evaluate` に渡せる。代わりに、約束が守られていることはテストで確かめる
2. **行列の演算は Numo で書く** — 勾配の三重ループが `features.transpose.dot(errors)` の 1 行になる。Numo は Rumale の依存として入っているので、依存は増えない
3. **配列の語彙で書ける** — 多数決は `transpose`、列の順を保った選択は `&`、標本の引き直しは `values_at`。Rust 版で添字のループにしたところが、名前のついた操作になる
4. **既定値と乱数は実測で確かめる** — Rumale のロジスティック回帰は既定で切片まで L2 正則化し、収束の判定もゆるい。ランダムフォレストはシードを省くとプロセス全体の乱数を初期化し直す。`shuffle` を `sample` に直すだけで結果が変わる

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
