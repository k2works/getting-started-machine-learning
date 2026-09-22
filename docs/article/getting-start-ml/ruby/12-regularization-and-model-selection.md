---
type: Article
title: "第 12 章: 正則化とモデル選択"
description: "リッジ回帰を Numo と第 7 章の連立方程式で、ラッソ回帰を座標降下法で Ruby の TDD で自作し、Rumale の Ridge・Lasso と突き合わせる。Rumale の 2 つの目的関数は件数で割るかどうかが違い、どちらも切片に罰則をかけることを実測し、中心化と reg_param = alpha / n の読み替えでリッジは 1e-5、ラッソは差 0 まで一致することを確かめる。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:55:55Z }
---

# 第 12 章: 正則化とモデル選択

## 12.1 はじめに

第 9 章では、2 乗の項や交互作用の項を足して特徴量を増やしました。特徴量を増やすと訓練データへの当てはまりはよくなりますが、**過学習**（訓練データにだけ強く、未知のデータで弱いこと）が起きやすくなります。

この章では、過学習を抑える **正則化** と、設定の候補から 1 つを選ぶ **モデル選択** を扱います。

- **リッジ回帰（L2 正則化）** — 係数の 2 乗の和に罰則を加える。閉形式（連立方程式）で解ける
- **ラッソ回帰（L1 正則化）** — 係数の絶対値の和に罰則を加える。係数がちょうど 0 になり、特徴量を選ぶ働きがある。閉形式では解けないので **座標降下法** を使う
- **検証データによるモデル選択** — 訓練・検証・テストの 3 つに分け、検証データで正則化の強さを選び、テストデータで最後に 1 度だけ測る

[Python 版の第 12 章](../python/12-regularization-and-model-selection.md) と同じ TODO リストで進め、[Rust 版](../rust/12-regularization-and-model-selection.md) と対比します。Ruby 版で拾う論点は次の 3 つです。

- **行列は Numo で、連立方程式は第 7 章の自作で解く**。Rust 版は ndarray の `Array2` を使いました。Ruby 版は Numo の `DFloat` で `transpose`・`dot`・`eye` を書き、Numo::Linalg が無いので第 7 章のガウス・ジョルダンの掃き出し法をそのまま使います
- **前の章の値をそのまま組み合わせる**。標準化と 2 次の項は第 9 章の `Standardizer` と `expand` を、分割は第 2 章の `split_train_test` を、決定係数は第 7 章の `r2_score` を使います。新しく書く変換は 3 行です
- **Rumale の 2 つのモデルは、目的関数の流儀が互いに違う**。Rumale の `Ridge` は誤差を件数で割り、`Lasso` は割りません。そして **どちらも切片に罰則をかけます**。ソースを読んで式を書き出し、実測で合わせます

ADR 010 のとおり、まず自作してから Rumale（`Rumale::LinearModel::Ridge`・`Lasso`）と突き合わせます。

## 12.2 過学習と正則化

### 係数が大きくなりすぎる

特徴量どうしが似ていると、最小二乗法は「片方に大きな正の係数、もう片方に大きな負の係数」を付けて訓練データに当てはめようとします。訓練データでは打ち消し合ってうまくいきますが、少しずれたデータでは大きく外れます。

### 係数の大きさに罰則を加える

そこで、当てはまりの悪さに **係数の大きさ** を足したものを最小にします。

| 手法 | 最小にするもの |
|------|--------------|
| 最小二乗法 | ‖t − Xw‖² |
| リッジ回帰 | ‖t − Xw‖² + α‖w‖² |
| ラッソ回帰 | ½‖t − Xw‖² + α‖w‖₁ |

α が大きいほど係数は 0 に近づき、当てはまりは悪くなります。**α をいくつにするか** を決めるのがモデル選択です。

切片には罰則をかけません。切片は「全体の水準」を表すだけで、大きくても過学習の原因にならないからです。そのために、学習の前に特徴量と正解から平均を引き（中心化）、解いたあとで平均を戻します。この「切片に罰則をかけない」が、12.10 節で Rumale と食い違う点になります。

### リッジ回帰の解き方

中心化したうえで、正規方程式の左辺に α を対角に足した連立方程式を解きます。

```text
(XᵀX + αI) w = Xᵀt
```

α を足すと対角が大きくなるので、解は小さいほうへ引き戻されます。α = 0 なら第 7 章の最小二乗法と同じ解になります。

## 12.3 題材とデータ

`Boston.csv` から `RM`（部屋数）・`PTRATIO`（生徒と教師の比）・`LSTAT`（低所得者の割合）の 3 列を使い、`PRICE` を予測します。Rust 版と同じく、過学習が起きやすい状況を作るために次の手を入れます。

1. **z スコアの絶対値が 3 を超える行を外れ値として除く**
2. **標準化してから 2 次の項（2 乗と積）を足す** — 3 列が 9 列になる
3. **訓練・検証・テストの 3 つに分ける** — 全体を訓練用とテスト用に分け、訓練用をさらに訓練データと検証データに分ける

3 つに分ける理由は、**検証データで選んだ結果をテストデータで確かめる** ためです。検証データで選んでから同じデータで測ると、選ぶ段階で使った情報が漏れ、実力より高い値が出ます。

## 12.4 TODO リストの作成

**TODO リスト**:

- [ ] 係数と切片を持つモデルを作る
  - [ ] 列名と係数の数が違えば失敗する
  - [ ] 係数の絶対値の合計、係数が 0 の列名を求める
- [ ] リッジ回帰を閉形式で解く
  - [ ] α が 0 なら最小二乗法と同じ解になる
  - [ ] α を強くすると係数が小さくなる
  - [ ] 切片には罰則をかけない
  - [ ] α が負なら失敗する
- [ ] ラッソ回帰を座標降下法で解く
  - [ ] 軟しきい値作用素を作る
  - [ ] α を強くすると係数がちょうど 0 になる
  - [ ] 正解に効かない列が先に 0 になる
- [ ] 標準化 + 2 次の項の変換を作る（第 9 章の道具を組み合わせる）
- [ ] 外れ値を除いて訓練・検証・テストの 3 つに分ける
- [ ] α ごとに実験し、検証データで最もよい α を選ぶ
- [ ] Rumale の `Ridge`・`Lasso` と突き合わせる
  - [ ] 目的関数の流儀の違いを実測して読み替える
- [ ] 実データで結果を表示する

この章のコードは `lib/getting_started_ml/chapter12/` に置きます。

| ファイル | 役割 |
|---------|------|
| `regularized_model.rb` | 列名の付いた係数と切片を持つモデル |
| `ridge.rb` | 中心化、切片の組み立て直し、リッジ回帰 |
| `lasso.rb` | 軟しきい値作用素、座標降下法、ラッソ回帰 |
| `selection.rb` | 実験結果と、検証データによる選択 |
| `boston.rb` | 外れ値の除去、標準化 + 2 次の項、3 つへの分割 |
| `rumale_regularized.rb` | Rumale の `Ridge`・`Lasso` の呼び出しと読み替え |

失敗は、これまでの章と同じく `ArgumentError` です。Rust 版は第 2 章・第 9 章の失敗を包む `enum` を作り、`From` で `?` が包み直すようにしました。Ruby 版は例外がそのまま呼び出し元まで伝わるので、章ごとに失敗の型を積み上げる必要がありません。

## 12.5 係数と切片を持つモデル

```ruby
# 列名の付いた係数と切片を持つモデル。行は値だけの配列（列の並びは学習したときと同じ）で受け取る。
RegularizedModel = Data.define(:columns, :coefficients, :intercept) do
  def initialize(columns:, coefficients:, intercept:)
    Chapter07.check_size(columns.size, coefficients.size)

    super
  end

  # 1 行分の予測値。
  def predict_one(row)
    Chapter07.check_size(coefficients.size, row.size)

    row.zip(coefficients).sum(intercept) { |value, coefficient| value * coefficient }
  end
```

第 7 章の `LinearModel` は `Features`（列名と値の組）を受け取りましたが、この章のモデルは **値だけの配列** を受け取ります。9 列の 2 次の項を行列のまま扱い、Numo に渡すからです。列名は係数の読み取りと「0 になった列」の報告にだけ使います。

```ruby
# 係数の絶対値の合計。正則化が強いほど小さくなる。
def coefficient_abs_sum
  coefficients.sum(&:abs)
end

# 係数がちょうど 0 になった列の名前を、列の順に返す。
def zero_columns
  columns.zip(coefficients).select { |_, coefficient| coefficient.zero? }.map(&:first)
end
```

`sum(intercept) { ... }` は、初期値を切片にした合計です。`sum` に初期値を渡せるので、「切片 + 係数 × 値の合計」を 1 つの式で書けます。

## 12.6 リッジ回帰を自作する

### Red: α が 0 なら最小二乗法と同じ

`t = 2a + 3b + 1` ちょうどの架空のデータに、正解と関係のない列 `c` を足して使います。

```ruby
# t = 2a + 3b + 1 ちょうどの架空のデータ。c は正解に関係しない列。
def sample
  rows = [[1.0, 2.0, 0.3], [2.0, 1.0, -0.2], [3.0, 5.0, 0.1], [4.0, 3.0, -0.3], [5.0, 4.0, 0.2], [6.0, 6.0, -0.1]]

  [%w[a b c], rows, rows.map { |a, b, _| (2.0 * a) + (3.0 * b) + 1.0 }]
end

def test_罰則が零なら最小二乗法と同じ解になる
  model = C.fit_ridge(*sample, 0.0)

  assert_in_delta 2.0, model.coefficients[0], 1e-9
  assert_in_delta 3.0, model.coefficients[1], 1e-9
  assert_in_delta 0.0, model.coefficients[2], 1e-9
  assert_in_delta 1.0, model.intercept, 1e-9
end
```

`sample` が列名・行・正解の 3 つを配列で返すので、`C.fit_ridge(*sample, 0.0)` と **分割して引数に渡せます**。Rust 版は `let (columns, rows, t) = sample();` と受けてから渡しました。

`GettingStartedMl::Chapter12` がまだ無いので、`uninitialized constant GettingStartedMl::Chapter12 (NameError)` で失敗します。

### Green: 中心化して対角に足す

中心化は `center` にまとめます。ラッソ回帰と Rumale との突き合わせでも使い回すからです。

```ruby
# 行と正解から列ごとの平均を引く。切片に罰則をかけないための下ごしらえ。
# 中心化した行列（Numo）・中心化した正解・列の平均・正解の平均を返す。
def center(rows, t)
  x = Numo::DFloat.cast(rows)
  x_means = x.mean(axis: 0)
  t_mean = t.sum / t.size

  [x - x_means, Numo::DFloat.cast(t) - t_mean, x_means.to_a, t_mean]
end

# 切片を平均から求める。中心化した解に、引いた平均を戻す。
def intercept_from(coefficients, x_means, t_mean)
  t_mean - coefficients.zip(x_means).sum { |coefficient, mean| coefficient * mean }
end
```

`x - x_means` は、行列（行数 × 列数）から列ごとの平均（列数）を引く式です。Numo は NumPy と同じく **形の違う配列の演算で小さいほうを繰り返す**（ブロードキャスト）ので、行ごとのループを書かずに済みます。Rust 版は行と列の二重の `map` で書きました。

本体も短くなります。

```ruby
# 平均を引いてから (XᵀX + alpha I) w = Xᵀ t を解き、切片を平均から求める。
# 目的関数は ‖t - Xw‖² + alpha ‖w‖² で、件数で割らない。alpha が 0 なら第 7 章の最小二乗法と同じ解になる。
def fit_ridge(columns, rows, t, alpha)
  check_inputs(rows, t, alpha)
  x, residuals, x_means, t_mean = center(rows, t)
  transposed = x.transpose
  # XᵀX の対角に alpha を足す。対角を大きくするほど解が小さいほうへ引き戻される
  normal = transposed.dot(x) + (Numo::DFloat.eye(columns.size) * alpha)
  coefficients = Chapter07.solve(normal, transposed.dot(residuals)).to_a

  RegularizedModel.new(columns:, coefficients:, intercept: intercept_from(coefficients, x_means, t_mean))
end
```

Rust 版は単位行列を作らずに `for` で対角に足しました。Ruby 版は `Numo::DFloat.eye(n) * alpha` で単位行列を作って足すので、**式が数式の `XᵀX + αI` と同じ形** になります。`solve` は第 7 章で自作した掃き出し法です。numo-narray-alt には連立方程式を解くメソッドが無く、numo-linalg-alt は LAPACK を要求するので、第 7 章の判断をそのまま引き継ぎます。

`center` が 4 つの値を配列で返し、呼ぶ側は `x, residuals, x_means, t_mean = center(rows, t)` の多重代入で受け取ります。Rust 版のタプルの分解代入と同じ形で、入れ物の型を作らずに済みます。

### 三角測量: 罰則の効き方

```ruby
def test_罰則を強くすると係数が小さくなる
  weak = C.fit_ridge(*sample, 1.0)
  strong = C.fit_ridge(*sample, 100.0)

  assert_operator strong.coefficient_abs_sum, :<, weak.coefficient_abs_sum
end

def test_罰則を強くしても切片は正解の平均に近いまま
  _, _, t = sample

  assert_in_delta t.sum / t.size, C.fit_ridge(*sample, 1e9).intercept, 1e-6
end
```

2 つ目が「切片に罰則をかけていない」ことの証拠です。α を極端に大きくすると係数はすべて 0 に潰れますが、切片だけは正解の平均のまま残ります。

### α が負なら失敗する

```ruby
# 正則化の強さと、行と正解の件数を確かめる。
def check_inputs(rows, t, alpha)
  raise ArgumentError, "正則化の強さは 0 以上にしてください: #{alpha}" if alpha.negative?

  Chapter07.check_size(rows.size, t.size)
end
```

```ruby
def test_負の罰則は受け付けない
  error = assert_raises(ArgumentError) { C.fit_ridge(*sample, -1.0) }

  assert_equal "正則化の強さは 0 以上にしてください: -1.0", error.message
end
```

Rust 版のメッセージは `-1` でしたが、Ruby の `Float#to_s` は `-1.0` と書きます。

## 12.7 ラッソ回帰を座標降下法で自作する

### なぜ閉形式で解けないのか

L1 の罰則 ‖w‖₁ は原点で折れているので、微分して 0 と置く方法が使えません。代わりに **係数を 1 つずつ順に、ほかを固定して最適化する**（座標降下法）と、1 座標ぶんは手で解けます。そこに出てくるのが **軟しきい値作用素** です。

```ruby
# 軟しきい値作用素。|value| が threshold 以下なら 0 にし、そうでなければ 0 のほうへ縮める。
def soft_threshold(value, threshold)
  return value - threshold if value > threshold
  return value + threshold if value < -threshold

  0.0
end
```

これが「係数がちょうど 0 になる」正体です。しきい値の内側に入った係数は、近づけるのではなく **0 そのもの** にされます。

```ruby
def test_軟しきい値作用素は零に寄せる
  assert_in_delta 2.0, C.soft_threshold(3.0, 1.0), 1e-12
  assert_in_delta(-2.0, C.soft_threshold(-3.0, 1.0), 1e-12)
  assert_equal 0.0, C.soft_threshold(0.5, 1.0)
end
```

3 つ目は `assert_in_delta` ではなく `assert_equal` です。軟しきい値作用素は **リテラルの `0.0` を返す** ので、浮動小数点数でも等値で確かめられます。

### 残差を差分で更新する

座標降下法は、係数を 1 つ動かすたびに残差 `r = t − Xw` を更新します。毎回ゼロから計算し直すと遅いので、動かした列のぶんだけ足し引きします。途中の状態（係数と残差）を持つので、小さなクラスにしました。

```ruby
# 座標降下法の途中の状態。係数と残差 r = t - Xw を持ち、係数を 1 つ動かすたびに残差を差分で更新する。
class CoordinateDescent
  attr_reader :weights

  def initialize(features, residuals, alpha)
    @features = features
    @residuals = residuals
    @alpha = alpha
    # 列ごとの 2 乗和。係数を決め直す割り算の分母になる
    @norms = (features**2).sum(axis: 0).to_a
    @weights = Array.new(features.shape[1], 0.0)
  end

  # 係数がどれも TOLERANCE より動かなくなるまで、すべての係数を 1 回ずつ動かす。
  def run
    MAX_ITERATIONS.times { break if sweep < TOLERANCE }
    self
  end

  # すべての係数を 1 回ずつ動かし、いちばん大きく動いた大きさを返す。
  def sweep
    weights.each_index.map { |index| update(index) }.max
  end

  # index 番目の係数を決め直し、動いた大きさを返す。
  def update(index)
    return 0.0 if @norms[index].zero?

    old = weights[index]
    weights[index] = refit(@features[true, index], old, @norms[index])

    (weights[index] - old).abs
  end

  # いったん列の寄与を残差に戻してから、残差との相関で係数を決め直し、新しい係数の寄与を残差から引く。
  def refit(column, old, norm)
    @residuals += old * column
    weight = Chapter12.soft_threshold(column.dot(@residuals), @alpha) / norm
    @residuals -= weight * column
    weight
  end
end
```

`@features[true, index]` は Numo で「すべての行の index 列」を取り出す書き方で、NumPy の `x[:, j]` に当たります。列の寄与を残差に戻す・引くのは `@residuals += old * column` の 1 行で、Rust 版の `r.iter_mut().zip(&x)` のループが要りません。

最初は、クラスにせずに `update_weight(x, r, weights, index, norm, alpha)` という 6 引数のモジュール関数で書き、残差を `r.inplace + (old * column)` で書き換えていました。RuboCop が 5 つを指摘しました。引数が多すぎる（`Metrics/ParameterLists`）、`r` という 1 文字の名前（`Naming/MethodParameterName`）、`Metrics/AbcSize`（17.29）、そして `inplace` の足し算の結果を捨てている（`Lint/Void`、2 件）です。最後の指摘は、Numo の `inplace` が「値を返す演算で、レシーバを書き換える」という **副作用のある式** であることを静的解析から見えなくしていた、という意味でもあります。状態をインスタンス変数に移して `+=` で書き直すと、5 つとも消えました。

### 正解に効かない列が先に 0 になる

```ruby
def test_正解に効かない列が先に零になる
  model = C.fit_lasso(*sample, 1.0)

  assert_equal ["c"], model.zero_columns
end

def test_罰則を強くすると係数がちょうど零になる
  columns, = sample

  assert_equal columns, C.fit_lasso(*sample, 1000.0).zero_columns
end
```

α = 1 で `c` だけが 0 になり、`a` と `b` は残りました。「どの特徴量が要らないか」を、ラッソ回帰は係数を 0 にすることで教えてくれます。

## 12.8 前処理を前の章の道具で組み立てる

標準化してから 2 次の項を足す変換は、第 9 章の `Standardizer` と `expand` をつなげるだけです。

```ruby
# 標準化と多項式特徴量をつなげた変換。訓練データで fit し、同じ平均と標準偏差で
# 訓練・検証・テストの 3 つを transform する。第 9 章の Standardizer と expand をそのまま使う。
PolynomialScaler = Data.define(:standardizer) do
  # 訓練データの平均と標準偏差を覚える。
  def self.fit(x)
    new(standardizer: Chapter09::Standardizer.fit(x))
  end

  # 変換後の列名。元の列、2 乗の列（"RM^2"）、積の列（"RM LSTAT"）の順。
  def feature_names
    standardizer.columns + Chapter09.pairs_with_replacement(standardizer.columns).map(&:name)
  end

  # 標準化してから 2 次の項を足し、値だけの配列にする。
  def transform(x)
    Chapter09.expand(standardizer.transform(x), standardizer.columns).map(&:values)
  end
end
```

Java 版は `PolynomialScaler` を 80 行ほど自前で書き、Rust 版は第 9 章のものを使い回して 50 行ほどでした。Ruby 版は **中身 3 行** です。第 9 章の `Standardizer` が `Data.define` の値で、`fit` と `transform` を持っているので、包むだけで済みます。

「訓練データの平均と標準偏差で検証データも変換する」ことをテストに固定します。ここを間違えると、検証データの情報が訓練に漏れます。

```ruby
def test_検証データは訓練データの平均と標準偏差で変換される
  # 訓練データの a の平均は 3 なので、3 は標準化すると 0 になり、2 乗の項も 0 になる
  values = C::PolynomialScaler.fit(train).transform([row(3.0, 30.0)]).first

  assert_in_delta 0.0, values[0], 1e-12
  assert_in_delta 0.0, values[2], 1e-12
  assert_equal 5, values.size
end
```

外れ値の除去は、Rust 版と同じく z スコアで行い、標準偏差は件数 n − 1 で割ります。

```ruby
# 列ごとの z スコア（標準偏差は件数 n - 1 で割る標本標準偏差）の絶対値が threshold を超える値を
# 1 つでも持つ行を除く。元の表は変えない。
def remove_outliers(table, columns, threshold)
  stats = columns.to_h do |column|
    [column, mean_and_sample_std(table.rows.map { |row| required_number(row, column) })]
  end

  table.with(rows: table.rows.reject do |row|
    stats.any? { |column, (mean, std)| ((required_number(row, column) - mean) / std).abs > threshold }
  end)
end
```

1 つの章の中で、標準偏差の割り方が 2 通り出てくることに注意してください。外れ値の判定は n − 1（Java 版・Kotlin 版・Rust 版にそろえる）、標準化は第 9 章の `Standardizer` の n（scikit-learn の `StandardScaler` と同じ）です。第 9 章で見たとおり、Rumale の `StandardScaler` は n − 1 で割ります。**「標準偏差」という名前だけでは、どちらで割るかは決まりません**。

3 つへの分け方は、第 2 章の `split_train_test` を 2 回呼ぶだけです。

```ruby
# 外れ値を除き、全体を訓練用とテスト用に、訓練用をさらに訓練データと検証データに分ける。
# 標準化の平均と標準偏差は訓練データだけから求め、3 つとも同じ値で変換する。
def prepare(csv_file, test_size:, validation_size:, seed:)
  table = Chapter02::Table.load(csv_file)
  kept = remove_outliers(table, [*FEATURES, TARGET], OUTLIER_THRESHOLD).rows
  outer = split_rows(kept, test_size, seed)
  inner = Chapter02.split_train_test(outer.x_train, outer.t_train, test_size: validation_size, seed:)

  scale(inner, outer, kept: kept.size, removed: table.rows.size - kept.size)
end

# 訓練データで標準化の平均と標準偏差を求め、訓練・検証・テストの 3 つを同じ値で変換する。
# inner は訓練データと検証データ、outer はテストデータを持つ第 2 章の分割。
def scale(inner, outer, kept:, removed:)
  scaler = PolynomialScaler.fit(inner.x_train)
  x_train, x_valid, x_test = [inner.x_train, inner.x_test, outer.x_test].map { |x| scaler.transform(x) }

  BostonSplit.new(x_train:, t_train: inner.t_train, x_valid:, t_valid: inner.t_test, x_test:, t_test: outer.t_test,
                  feature_names: scaler.feature_names, kept:, removed:)
end
```

分割の結果の入れ物は、最初 `Dataset` という名前にしました。`rake 'run[chapter12]'` を走らせると ``undefined method `dir' for class GettingStartedMl::Chapter12::Dataset`` で落ちました。`Chapter12` の中で `Dataset.dir` と書くと、学習データの置き場を返す `GettingStartedMl::Dataset` ではなく、同じ名前の `Chapter12::Dataset` が先に見つかるからです。Ruby の定数は **内側のモジュールから順に探す** ので、名前が衝突しても静かに内側が勝ちます。`BostonSplit` に改名しました。

## 12.9 実験結果を記録して選ぶ

α 1 つ分の結果を、作ったあとで変えられない値にします。

```ruby
# 正則化の強さ 1 つ分の実験結果。
Experiment = Data.define(:alpha, :train_score, :validation_score, :coefficient_abs_sum)
```

```ruby
# alpha ごとにリッジ回帰を訓練データで学習し、訓練データと検証データの決定係数を記録する。
# split は feature_names・x_train・t_train・x_valid・t_valid を持つもの（BostonSplit）。
def run_ridge_experiments(split, alphas)
  alphas.map do |alpha|
    model = fit_ridge(split.feature_names, split.x_train, split.t_train, alpha)

    Experiment.new(alpha:, train_score: Chapter07.r2_score(split.t_train, model.predict(split.x_train)),
                   validation_score: Chapter07.r2_score(split.t_valid, model.predict(split.x_valid)),
                   coefficient_abs_sum: model.coefficient_abs_sum)
  end
end
```

最初は Rust 版と同じく、列名・訓練・検証の 5 つの配列と α の並びを 6 つの引数で受け取っていました。RuboCop の `Metrics/ParameterLists`（上限 5）に咎められ、分割の結果を丸ごと受け取る形にしました。テストでは `BostonSplit.new` で小さな分割を作って渡します。

最もよい実験を選ぶところは、Rust 版と大きく違います。

```ruby
# 検証データの決定係数が最も高い実験。同じ値なら先の実験を選ぶ（max_by は先の要素を残す）。
def best_experiment(experiments)
  raise ArgumentError, "実験の結果が 1 件もありません" if experiments.empty?

  experiments.max_by(&:validation_score)
end
```

Rust の `f64` は `NaN` があるので `Ord` を実装しておらず、`max_by_key` が使えないので `reduce` で比べました。Ruby の `Float` は `<=>` を持つので、`max_by` がそのまま使えます。同点のときに先の要素を残すかどうかは実行結果を左右するので、テストで固定します。

```ruby
def test_同じ値なら先の実験を選ぶ
  assert_in_delta 0.1, C.best_experiment([experiment(0.1, 0.8), experiment(1.0, 0.8)]).alpha
end
```

## 12.10 Rumale と突き合わせる

### 2 つの目的関数を書き出す

ここがこの章のいちばんの山場です。Rumale 2.2 のソースから、`Ridge` と `Lasso` が最小にしている式を書き出します。

**`Ridge`**（Numo::Linalg が無いときの L-BFGS）の損失は、次のとおりです。

```ruby
loss = (d**2).sum.fdiv(n_samples) + a * (w * w).sum
```

**`Lasso`**（座標降下法）は、1 座標ずつ `soft_threshold(z, reg_param).fdiv(x_norms[j])` で係数を決め直します。これは ½‖t − Xw‖² + reg_param‖w‖₁ の座標降下法そのもので、自作と同じ式です。

そして **どちらも、切片を「1 の列の係数」として学習します**。`fit_bias: true`（既定）のとき、`expand_feature` が特徴量の右端に 1（`bias_scale`）の列を足し、その列の係数を最後に `bias_term` として切り出します。罰則は係数の並び全体にかかるので、**切片にも罰則がかかります**。

| | 自作 | Rumale の `Ridge` | Rumale の `Lasso` |
|---|------|-------------------|-------------------|
| 誤差の項 | ‖t − Xw‖² | ‖t − Xw − b‖² **/ n** | ½‖t − Xw − b‖² |
| 罰則の項 | α‖w‖²、α‖w‖₁ | reg_param (‖w‖² **+ b²**) | reg_param (‖w‖₁ **+ \|b\|**) |
| 切片 | 平均から組み立て直す | 1 の列の係数（罰則あり） | 1 の列の係数（罰則あり） |

`Ridge` の両辺に n を掛けると ‖t − Xw‖² + n·reg_param·‖w‖² なので、**α = n × reg_param**、つまり reg_param = α / n と読み替えます。`Lasso` は同じ尺度なので、α をそのまま渡します。**同じライブラリの 2 つのモデルで、正則化の強さの意味が n 倍違う** ことになります。

```ruby
# 自作の alpha を Rumale の Ridge の reg_param に直す。Rumale は誤差の 2 乗の和を件数で割るので、
# 両辺に n を掛けると ‖t - Xw‖² + n reg_param ‖w‖²。自作の alpha = n reg_param になる。
def rumale_ridge_reg_param(alpha, n_samples)
  alpha.fdiv(n_samples)
end
```

もう 1 つ、ソースから分かったことがあります。`Ridge` は Numo::Linalg があると特異値分解（`solver: "svd"`）で解き、そのときの式は `s / (s**2 + reg_param)` で、**誤差を n で割らない** ‖t − Xw‖² + reg_param‖w‖² の解です。この環境には Numo::Linalg が無いので、`solver: "svd"` を渡しても `params[:solver]` は `"svd"` のまま、`fit` の中で黙って L-BFGS に落ちました。つまり **Numo::Linalg を入れるかどうかで、同じ reg_param の意味が n 倍変わります**。第 7 章の `LinearRegression` が Numo::Linalg の有無で黙って解き方を変えたのと同じ構造です（こちらは Numo::Linalg を入れた環境で確かめていないので、ソースから読める範囲の話です）。

### 切片の罰則は、中心化して外す

切片に罰則がかかる違いは、読み替えでは消せません。そこで、**こちらで特徴量と正解の平均を引き、切片を学習させずに（`fit_bias: false`）渡し、切片は平均から組み立て直します**。自作の `center` と `intercept_from` をそのまま使います。

```ruby
# 平均を引いてから学習させ、係数と、平均から組み立て直した切片のモデルにする。
def fit_centered(estimator, columns, rows, t)
  x, residuals, x_means, t_mean = center(rows, t)
  coefficients = estimator.fit(x, residuals).weight_vec.to_a

  RegularizedModel.new(columns:, coefficients:, intercept: intercept_from(coefficients, x_means, t_mean))
end
```

```ruby
# Rumale のリッジ回帰。特徴量と正解の平均をこちらで引き、切片を学習させずに（fit_bias: false）渡す。
def rumale_ridge(columns, rows, t, alpha)
  fit_centered(ridge_estimator(alpha, t.size, fit_bias: false), columns, rows, t)
end

# Rumale のラッソ回帰。目的関数の尺度は自作と同じなので、alpha をそのまま渡す。
def rumale_lasso(columns, rows, t, alpha)
  fit_centered(lasso_estimator(alpha, fit_bias: false), columns, rows, t)
end
```

```ruby
# Rumale の Ridge。Numo::Linalg が無いので L-BFGS で解く（solver: "svd" を渡しても黙って L-BFGS になる）。
def ridge_estimator(alpha, n_samples, fit_bias:)
  Rumale::LinearModel::Ridge.new(reg_param: rumale_ridge_reg_param(alpha, n_samples), fit_bias:,
                                 max_iter: MAX_ITERATIONS, tol: RUMALE_TOLERANCE)
end

# Rumale の Lasso。繰り返しの上限と収束の判定は自作と同じにする。
def lasso_estimator(alpha, fit_bias:)
  Rumale::LinearModel::Lasso.new(reg_param: alpha, fit_bias:, max_iter: MAX_ITERATIONS, tol: RUMALE_TOLERANCE)
end
```

Rust 版の linfa-elasticnet は「正解の平均しか引かない」ので、同じく特徴量の平均をこちらで引いて渡しました。Rumale は「平均をまったく引かず、切片に罰則をかける」ので、やることは同じ（こちらで中心化する）でも、理由が違います。

テストは、列の平均も正解の平均も 0 でない、直線に乗らない 8 件で書きました。

```ruby
def test_件数で割ればRumaleのリッジ回帰は自作と一致する
  assert_same_model C.fit_ridge(*sample, 2.0), C.rumale_ridge(*sample, 2.0), 1e-6
end

def test_Rumaleのラッソ回帰は同じ罰則で自作と一致する
  assert_same_model C.fit_lasso(*sample, 5.0), C.rumale_lasso(*sample, 5.0), 1e-8
end

def test_中心化せずに渡すとRumaleは切片にも罰則をかける
  # 列を足して切片を学習させると、切片も罰則で 0 のほうへ引き寄せられる
  own = C.fit_lasso(*sample, 5.0)
  raw = C.rumale_lasso_raw(*sample, 5.0)

  assert_operator raw.intercept.abs, :<, own.intercept.abs
end
```

3 つ目は、Rumale を既定のまま（`fit_bias: true`、中心化なし）呼ぶと切片が 0 のほうへ縮むことを、違いとして固定するテストです。

### 収束の判定は小さくする

`Ridge` の L-BFGS も、`Lasso` の座標降下法も、既定の `tol` は `1e-4` です。実データ（訓練データ 47 件・9 列）で、自作との係数の最大の差を測りました。

| | 既定の `tol: 1e-4` | `tol: 1e-10` |
|---|------|------|
| リッジ回帰（α = 1.0） | 0.184 | 1.38e-05 |
| ラッソ回帰（α = 1.0） | 0.00278（197 回で停止） | 0（完全に一致） |

`tol` を `1e-10` にすると、ラッソ回帰は **差が 0**、つまり全係数が浮動小数点数として完全に一致しました。Rumale の座標降下法は、係数を動かす順・残差の足し引き・軟しきい値作用素・止める条件まで自作と同じ手順なので、同じ演算が同じ順で行われたからです。リッジ回帰は L-BFGS という反復法で近づくので、閉形式で解いた自作とは `1e-5` ほど残りました。

## 12.11 実データで比べる

```bash
bundle exec rake 'run[chapter12]'
```

```text
データ件数: 98（外れ値 2 件を除外）
訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件
特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2
alpha	訓練 R²	検証 R²	係数の絶対値の合計
0.0	0.8605	0.8252	14.215
0.1	0.8605	0.8264	13.946
1.0	0.8597	0.8309	12.991
10.0	0.8450	0.8097	10.409
100.0	0.6460	0.5722	6.013
検証データで選んだ alpha: 1.0
テストデータの決定係数: 線形回帰 0.3357, リッジ回帰 0.4384
リッジ回帰の係数の最大の差（自作と Rumale）: 1.38e-05
ラッソ回帰（alpha ごとに 0 になった特徴量）
  alpha=1.0: 自作 [] / Rumale []
  alpha=10.0: 自作 ["RM PTRATIO", "PTRATIO LSTAT", "LSTAT^2"] / Rumale ["RM PTRATIO", "PTRATIO LSTAT", "LSTAT^2"]
  alpha=50.0: 自作 ["RM PTRATIO", "RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"] / Rumale ["RM PTRATIO", "RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"]
  alpha=100.0: 自作 ["RM PTRATIO", "RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"] / Rumale ["RM PTRATIO", "RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"]
ラッソ回帰の係数の最大の差（自作と Rumale）: 0.00e+00
中心化せずに Rumale に渡したとき（切片にも罰則がかかる）
  リッジ回帰（alpha=1.0）の切片: 自作 22.17 / Rumale 20.61
  ラッソ回帰（alpha=10.0）で 0 になった特徴量: 自作 ["RM PTRATIO", "PTRATIO LSTAT", "LSTAT^2"] / Rumale ["RM PTRATIO", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"]
```

この表示は `test/boston_regularization_test.rb` で固定しています。読み取れることは次のとおりです。

- **α を強くすると係数の絶対値の合計は単調に減る**。14.215 → 13.946 → 12.991 → 10.409 → 6.013 と、5 つの α すべてで減りました。これはテストにも固定しています
- **検証データで選ばれた α は 1.0**。検証 R² は 0.8252 → 0.8264 → **0.8309** → 0.8097 → 0.5722 で、α = 1.0 が最大でした。訓練 R² は α = 0 がいちばん高い（0.8605）ので、**訓練データだけで選ぶと正則化しないほうが選ばれてしまいます**
- **テストデータでは差がはっきり出た**。線形回帰（α = 0）の 0.3357 に対して、リッジ回帰（α = 1.0）は 0.4384 でした。検証 R² の差は 0.0057 でしたが、テストデータでは 0.10 の差になりました
- **テストデータの R² は検証データより大幅に低い**（0.8309 → 0.4384）。テストデータ 30 件のほうが難しい行を含んでいるためで、**最後に 1 度だけ測る値こそが実力** だと分かります
- **自作と Rumale は一致した**。中心化と読み替えのあと、リッジ回帰の係数の最大の差は `1.38e-05`（L-BFGS の収束の差）、ラッソ回帰は `0.00e+00`（完全に一致）でした。0 になる特徴量の集合は、4 つの α すべてで一致しました
- **Rumale を既定のまま使うと、切片と選ばれる特徴量が変わる**。中心化せずに渡すと、リッジ回帰の切片は 22.17 から 20.61 に縮み、ラッソ回帰（α = 10）は自作では残る `PTRATIO^2` まで 0 にしました。切片に罰則がかかると最小にする式そのものが変わるので、切片だけでなく係数の並びも変わります
- **ラッソ回帰は特徴量を選ぶ**。α = 10 で `RM PTRATIO`・`PTRATIO LSTAT`・`LSTAT^2` の 3 つが 0 になり、α = 50 で `RM LSTAT`・`PTRATIO^2` も加わって 5 つになりました。残るのは元の 3 列と `RM^2` です。リッジ回帰は係数を小さくするだけで 0 にはしないので、「どの特徴量が要らないか」を教えてくれるのはラッソ回帰だけです

分割が違うので、Rust 版の数値（選んだ α は 1.0、テストデータの決定係数は線形回帰 0.4728・リッジ回帰 0.5019）とは一致しません。**「検証データで選んだ α がテストデータでも線形回帰に勝つ」「ラッソ回帰が交互作用の項を先に落とす」という傾向は Rust 版・Java 版と同じ** でした。

### 実データのテスト

```ruby
def test_正則化を強くすると係数の絶対値の合計は単調に減る
  split = data
  sums = C.run_ridge_experiments(split, C::ALPHAS).map(&:coefficient_abs_sum)

  assert_equal sums.sort.reverse, sums
end

def test_実データでもラッソ回帰はRumaleと同じ係数になる
  split = data
  own = C.fit_lasso(split.feature_names, split.x_train, split.t_train, 10.0)
  library = C.rumale_lasso(split.feature_names, split.x_train, split.t_train, 10.0)

  assert_equal ["RM PTRATIO", "PTRATIO LSTAT", "LSTAT^2"], own.zero_columns
  assert_equal own.coefficients, library.coefficients
end
```

2 つ目は、係数の配列を `assert_equal` で比べています。許容誤差を置かずに比べられるのは、12.10 節で見たとおり同じ演算が同じ順で行われるからです。学習データが無い環境では、これまでの章と同じく Minitest の `skip` でスキップします。

### 品質チェック

```bash
bundle exec rake check
```

第 12 章を足した時点で、`bundle exec rake check` は 245 件のテストがすべて通ります。データが無い環境（`ML_DATA_DIR=/nonexistent`）では 31 件がスキップになりました。自作のラッソ回帰は、実データ（47 件・9 列、α = 1.0）で 0.09 秒ほどで収束します。

RuboCop は 23 件を指摘しました。12.7 節の座標降下法の 5 件、12.9 節の `Metrics/ParameterLists` のほかは次のとおりです。

- **`Metrics/AbcSize`** — `prepare` が 25.5 でした。訓練データで標準化して 3 つを変換する部分を `scale` に切り出しました
- **`Style/FormatStringToken`** — 実験の行を `Kernel.format("%.1f\t%.4f...", ...)` と書いたところ、`%<alpha>.1f` のように名前付きの書式にするよう求められました。`Experiment#to_h` をそのまま `**` で渡せるので、かえって短くなりました
- **`Style/ParallelAssignment`・`Layout/*`** — 多重代入で 2 つのモデルを受ける書き方と、行の長さ・引数の揃え方です。`map(&:intercept)` で 1 つの配列にまとめ、残りは `rubocop -a` で直しました

## 12.12 可視化について

Ruby 版では Notebook と可視化を扱いません。α と決定係数の関係、係数の縮み方、ラッソ回帰で 0 になっていく様子（正則化パス）の可視化は、[Python 版の第 12 章](../python/12-regularization-and-model-selection.md) と [Kotlin 版の第 12 章](../kotlin/12-regularization-and-model-selection.md) の可視化の節を参照してください。Ruby 版の `run_ridge_experiments` は `Experiment`（α・訓練 R²・検証 R²・係数の絶対値の合計）の配列を返すので、そのままグラフの元データになります。

## 12.13 まとめ

この章では、正則化とモデル選択を Ruby の TDD で実装しました。

| 作ったもの | 自作 | 突き合わせたライブラリ |
|-----------|------|-------------------|
| リッジ回帰 | `fit_ridge`（中心化 + `XᵀX + αI` を第 7 章の `solve` で解く） | Rumale の `Ridge`（reg_param = α / n、中心化して `fit_bias: false`。差 `1.38e-05`） |
| ラッソ回帰 | `fit_lasso`（`CoordinateDescent` + 軟しきい値作用素） | Rumale の `Lasso`（α はそのまま、中心化して `fit_bias: false`。差 0） |
| 標準化 + 2 次の項 | `PolynomialScaler`（第 9 章の道具を包むだけ） | — |
| モデル選択 | `run_ridge_experiments`・`best_experiment` | — |

Ruby らしさが出たのは次の 4 点です。

1. **Numo の式が数式と同じ形になる** — `transposed.dot(x) + (Numo::DFloat.eye(n) * alpha)`、`x - x_means`（ブロードキャスト）、`@features[true, index]`（列の取り出し）。Rust 版の二重ループと `for` で対角に足す処理が、式 1 つずつになる
2. **前の章の値をそのまま組み合わせる** — `PolynomialScaler` は第 9 章の `Standardizer` を包むだけで中身 3 行。分割は第 2 章、決定係数と連立方程式は第 7 章。例外がそのまま伝わるので、Rust 版のように章ごとの失敗の型を積み上げる必要も無い
3. **`Float` は比べられる** — `max_by(&:validation_score)` で最もよい実験が選べる。Rust 版は `f64` が `Ord` でないので `reduce` を書いた
4. **定数は内側から探される** — `Chapter12::Dataset` を作ると、`Dataset.dir` が学習データの置き場ではなくそちらを指して落ちた。名前が衝突しても警告は出ないので、実行して初めて分かる

ライブラリとの突き合わせでは、**目的関数を式で書き出してから実測する** ことが効きました。Rumale の `Ridge` は誤差を件数で割り、`Lasso` は割らず、どちらも切片に罰則をかけます。`Ridge` は Numo::Linalg の有無で解き方も式も変わります。既定のまま使うと、切片が 22.17 から 20.61 に縮み、ラッソ回帰が選ぶ特徴量まで変わりました。中心化と `reg_param = α / n` の読み替えを入れると、リッジ回帰は `1e-5`、ラッソ回帰は差 0 まで一致しました。

検証データで選んだリッジ回帰（α = 1.0）は、テストデータの決定係数で線形回帰を 0.3357 → 0.4384 と上回りました。ラッソ回帰は、交互作用の項から順に係数を 0 にして特徴量を選びました。次の章では、特徴量そのものを作り直す **主成分分析** を学びます。
