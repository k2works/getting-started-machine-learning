---
type: Article
title: "第 11 章: 評価指標と交差検証"
description: "混同行列・適合率・再現率・F 値・ROC 曲線・AUC・K 分割交差検証を Ruby の TDD で自作し、Rumale の EvaluationMeasure（confusion_matrix・Precision・Recall・FScore・ROCAUC）と ModelSelection（KFold・CrossValidation）と突き合わせる。Rumale の KFold は第 2 章の shuffle と同じ並べ替えをするので、行の分け方まで一致することを実測する。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:47:06Z }
---

# 第 11 章: 評価指標と交差検証

## 11.1 はじめに

第 10 章の最後で、iris のテストデータ 45 件で測った正解率はモデルごとに数件しか違わず、「どのモデルが優れているか」を言い切れませんでした。理由は 2 つあります。

- **正解率という 1 つの数字では、間違い方の違いが見えない**。生存者を見逃したのか、死亡者を生存と言い過ぎたのかが、同じ正解率に潰れてしまいます
- **1 回の分け方で測った値は、たまたまで動く**。分け方を変えれば順位が入れ替わることがあります

この章では、その 2 つに手を入れます。前半は **混同行列** から求める適合率・再現率・F 値と、閾値を動かしながら性能を見る **ROC 曲線・AUC** です。後半は、データを k 個に分けて全部を一度ずつテストデータにする **K 分割交差検証** です。

[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と同じ TODO リストで進め、[Rust 版](../rust/11-evaluation-metrics-and-cross-validation.md) と対比します。Ruby 版で拾う論点は次の 3 つです。

- **評価関数は lambda と Method オブジェクトで渡す**。Rust 版は `Box<dyn Fn>` で「呼べるもの」の型をそろえました。Ruby 版は `call` に応えるものなら何でも評価関数になり、第 7 章の `root_mean_squared_error` は `Chapter07.method(...)` でそのまま渡せます
- **モデルの共通の型を宣言しない**。Rust 版は `trait Model<T>` を定義して第 3 章の決定木に `impl` を足しました。Ruby 版の決定木はもともと `fit` と `predict` を持っているので、何も足さずに交差検証にかけられます（第 10 章と同じダックタイピング）
- **Rumale の分け方は自作と行まで一致する**。Rumale の `KFold` は、第 2 章の `shuffle` と同じ `Array#shuffle(random: Random.new(seed))` で並べ替えます。Rust 版は linfa が並べ替えないので「並べ替えなし」でしか突き合わせられませんでしたが、Ruby 版は並べ替えありのまま分割ごとに突き合わせられました

ADR 010 のとおり、まず自作してから Rumale（`Rumale::EvaluationMeasure`・`Rumale::ModelSelection`）と突き合わせます。

## 11.2 正解率だけでは足りない理由

`Survived.csv` はタイタニック号の乗客のデータで、生存（`Survived` が 1）した人は 342 人、死亡（0）した人は 549 人です。正解ラベルの数が偏っていると、全員を「死亡」と予測するだけのモデルでも正解率はそれなりに高く出ます。生存者を 1 人も見つけられないのに、正解率だけを見ると当たっているように見えます。

そこで、予測の当たり外れを 4 つに分けて数える **混同行列** を使います。ここでは「生存」を正例（見つけたいほう）とします。

| | 正例と予測 | 負例と予測 |
|---|-----------|-----------|
| **実際は正例** | TP（真陽性） | FN（偽陰性） |
| **実際は負例** | FP（偽陽性） | TN（真陰性） |

混同行列から、目的に応じた指標を求めます。

| 指標 | 式 | 意味 |
|------|-----|------|
| 適合率（precision） | TP / (TP + FP) | 正例と予測したうち、本当に正例だった割合 |
| 再現率（recall） | TP / (TP + FN) | 本当の正例のうち、正例と予測できた割合 |
| F 値（F1） | 2 × 適合率 × 再現率 / (適合率 + 再現率) | 適合率と再現率の調和平均 |

回帰の評価指標（RMSE・MAE・R²）は第 7 章の `Chapter07` で作ったので、この章ではそのまま使い回します。

## 11.3 TODO リストの作成

**TODO リスト**:

- [ ] 混同行列を数える
  - [ ] どちらのラベルを正例にするかを指定できる
  - [ ] 正解と予測の件数が違えば失敗する
- [ ] 適合率・再現率・F 値を求める
  - [ ] 分母が 0 のときは 0 にする
- [ ] 評価関数を差し替えられるようにする
- [ ] ROC 曲線と AUC を求める
  - [ ] 完全に分けられれば 1、逆順なら 0 になる
  - [ ] 同じスコアは 1 つの点にまとめる
  - [ ] 正例か負例が片方しか無ければ失敗する
- [ ] K 分割のテストデータを作る
  - [ ] ほぼ均等な件数に分ける
  - [ ] どの行もちょうど一度だけテストデータになる
  - [ ] 並べ替えあり（シード付き）と並べ替えなしの両方を作る
- [ ] 交差検証で分割ごとのスコアを求める
- [ ] 分類と回帰を同じ交差検証にかけられるようにする
- [ ] Rumale の `confusion_matrix`・`Precision`・`Recall`・`FScore`・`ROCAUC` と突き合わせる
- [ ] Rumale の `KFold`・`CrossValidation` と突き合わせる
- [ ] 実データで交差検証の平均を表示する

この章のコードは `lib/getting_started_ml/chapter11/` に置きます。

| ファイル | 役割 |
|---------|------|
| `metrics.rb` | 混同行列、適合率・再現率・F 値、評価関数 |
| `roc.rb` | ROC 曲線と AUC |
| `cross_validation.rb` | K 分割、交差検証、第 7 章の線形回帰の包み |
| `data.rb` | Survived と cinema の特徴量と正解ラベル |
| `rumale_measures.rb` | Rumale の評価指標・KFold・CrossValidation の呼び出し |

新しい失敗は「件数が違う」「分割の数が正しくない」「正例か負例が片方しか無い」の 3 つで、どれも第 2 章からの流儀どおり `ArgumentError` にします。

## 11.4 混同行列を数える

### Red: 数え方のテスト

正解 `[1 1 1 0 0]` に対して予測 `[1 1 0 0 1]` なら、TP=2・FN=1・FP=1・TN=1 です。正例を `"0"` に入れ替えると、4 つの数も入れ替わります。

```ruby
# 正解 [1 1 1 0 0]、予測 [1 1 0 0 1] の混同行列。TP=2, FN=1, TN=1, FP=1。
def sample
  C::ConfusionMatrix.of(%w[1 1 1 0 0], %w[1 1 0 0 1], "1")
end

def counts(matrix)
  [matrix.true_positive, matrix.false_negative, matrix.false_positive, matrix.true_negative]
end

def test_混同行列は四つの数を数える
  assert_equal [2, 1, 1, 1], counts(sample)
end

def test_正例のラベルを入れ替えると四つの数も入れ替わる
  assert_equal [1, 1, 1, 2], counts(C::ConfusionMatrix.of(%w[1 1 1 0 0], %w[1 1 0 0 1], "0"))
end
```

`GettingStartedMl::Chapter11` がまだ無いので、`uninitialized constant GettingStartedMl::Chapter11 (NameError)` で失敗します。

### Green: 2 つの真偽値の組を tally で数える

混同行列は `Data.define` で作ります。第 2 章の `Features` と同じく、4 つの数を持つだけの値です。

```ruby
# 2 値分類の混同行列。正例（見つけたいほう）を決めて、予測の当たり外れを 4 つに分けて数える。
ConfusionMatrix = Data.define(:true_positive, :false_positive, :false_negative, :true_negative) do
  # 正解と予測から数える。件数が違えば ArgumentError を投げる（短いほうに合わせて黙って切り詰めない）。
  def self.of(actual, predicted, positive)
    Chapter11.require_same_size(actual, predicted)
    # 「実際が正例か」「予測が正例か」の組ごとに数える。4 通りのどれも無ければ 0 件
    counts = actual.zip(predicted).map { |truth, prediction| [truth == positive, prediction == positive] }.tally

    new(true_positive: counts.fetch([true, true], 0), false_positive: counts.fetch([false, true], 0),
        false_negative: counts.fetch([true, false], 0), true_negative: counts.fetch([false, false], 0))
  end
```

Rust 版は `(bool, bool)` の組を `match` で 4 通り書き、1 つでも書き落とすとコンパイルが通らないことを利点に挙げました。Ruby に網羅性の検査はありません。代わりに、**組を作って `tally`（出現回数の Hash）で一度に数え**、4 つの組を `fetch` で取り出します。`if` を並べないので「この `else` はどの場合か」を考える必要が無く、1 度も現れなかった組は `fetch` の既定値 0 になります。書き落としを検出する役目は、正例を入れ替えたテストのような **4 つの数がすべて違う値になる例** に持たせます。

### 件数が違うときは黙って切り詰めない

`zip` は短いほうではなく **レシーバの長さ** に合わせ、足りない分を `nil` で埋めます。件数が違っても静かに動いてしまうので、先に確かめます。

```ruby
# 件数が同じでなければ ArgumentError を投げる。
def require_same_size(actual, predicted)
  raise ArgumentError, "件数が違います: #{actual.size} と #{predicted.size}" unless actual.size == predicted.size
end
```

```ruby
def test_件数が違えば失敗する
  error = assert_raises(ArgumentError) { C::ConfusionMatrix.of(%w[1], %w[1 0], "1") }

  assert_equal "件数が違います: 1 と 2", error.message
end
```

## 11.5 適合率・再現率・F 値

### 分母が 0 になる場合

Ruby では、整数の `0 / 0` は `ZeroDivisionError`、浮動小数点数の `0.0 / 0.0` は `NaN` です。正例を 1 件も予測しなければ `TP + FP` が 0 になるので、割り算を包んでおきます。

```ruby
# 分母が 0 なら 0 を返す割り算。
def ratio(numerator, denominator)
  denominator.zero? ? 0.0 : numerator.to_f / denominator
end
```

指標はどれも 1 行です。

```ruby
# 適合率。正例と予測したうち、本当に正例だった割合。
def precision
  Chapter11.ratio(true_positive, true_positive + false_positive)
end

# 再現率。本当の正例のうち、正例と予測できた割合。
def recall
  Chapter11.ratio(true_positive, true_positive + false_negative)
end

# F 値。適合率と再現率の調和平均。
def f1_score
  Chapter11.ratio(2.0 * precision * recall, precision + recall)
end
```

Rust 版は `usize` から `f64` への変換を 1 か所に閉じ込めました。Ruby は `Integer#to_f` で変換するだけで、精度の警告もありません。件数の割り算は第 1 章の `accuracy` と同じく `fdiv` か `to_f` で浮動小数点数にします。

テストは、適合率と再現率が **食い違う例** で書きます。

```ruby
def test_適合率と再現率とF値を求める
  # 正解 [1 1 1 1 0 0]、予測 [1 1 0 0 0 1]。TP=2, FP=1, FN=2 で適合率と再現率が食い違う
  matrix = C::ConfusionMatrix.of(%w[1 1 1 1 0 0], %w[1 1 0 0 0 1], "1")

  assert_in_delta 2.0 / 3, matrix.precision, 1e-12
  assert_in_delta 0.5, matrix.recall, 1e-12
  assert_in_delta 4.0 / 7, matrix.f1_score, 1e-12
  assert_in_delta 0.5, matrix.accuracy, 1e-12
end

def test_正例と予測した件数が零なら適合率は零になる
  matrix = C::ConfusionMatrix.of(%w[1 0], %w[0 0], "1")

  assert_in_delta 0.0, matrix.precision, 1e-12
  assert_in_delta 0.0, matrix.f1_score, 1e-12
end
```

Rust 版は、最初に書いた例（正解 `[1 1 1 0 0]`・予測 `[1 1 0 0 1]`）で適合率と再現率がどちらも 2/3 だったため、ライブラリの「向き」の間違いを単体テストで見逃しました。Ruby 版はその教訓を先に取り込み、2 つが違う値になる例から書いています。

## 11.6 評価関数を lambda と Method オブジェクトで渡す

交差検証は「分割ごとに学習して、テストデータを採点する」手順です。採点の中身（正解率か、適合率か、RMSE か）は差し替えたいので、**評価関数を値として渡します**。

Rust 版は、関数ごとに型が違うので `Box<dyn Fn(&[T], &[T]) -> Result<f64>>` という型の別名を作ってそろえました。Ruby 版は型をそろえる必要がありません。**`call(正解, 予測)` に応えるもの** なら何でも評価関数です。

混同行列の指標は、正例のラベルを決めれば評価関数になります。

```ruby
# 混同行列から求める指標を、正例を決めて評価関数（正解と予測を受け取る lambda）に変える。
def classification_metric(score, positive)
  ->(actual, predicted) { ConfusionMatrix.of(actual, predicted, positive).public_send(score) }
end
```

指標は `:precision` のようなシンボルで渡し、`public_send` で呼びます。Rust 版の `fn(&ConfusionMatrix) -> f64`（関数ポインタ）に当たる役目を、メソッド名が担います。lambda は外側の `positive` を捕まえたまま生き残るので、Rust 版のように `move` で所有権を移す指定は要りません。

```ruby
def test_評価関数にすると正解と予測から直に採点できる
  metric = C.classification_metric(:precision, "1")

  assert_in_delta 2.0 / 3, metric.call(%w[1 1 1 0 0], %w[1 1 0 0 1]), 1e-12
end
```

前の章の関数は、引数の順で扱いが分かれました。

```ruby
# 正解率の評価関数。第 1 章の accuracy は（予測, 正解）の順に受け取るので、順を入れ替えて包む。
ACCURACY = ->(actual, predicted) { Chapter01.accuracy(predicted, actual) }
# 第 7 章の RMSE と MAE は（実測, 予測）の順なので、Method オブジェクトをそのまま評価関数にできる。
RMSE = Chapter07.method(:root_mean_squared_error)
MAE = Chapter07.method(:mean_absolute_error)
```

`Chapter07.method(:root_mean_squared_error)` は、モジュール関数を `Method` オブジェクトとして取り出します。`Method` も `call` に応えるので、lambda と同じ場所に置けます。Rust 版の `Box::new(root_mean_squared_error)` と同じく、**アダプターを書かずに前の章の資産をつなげられます**。

第 1 章の `accuracy(predictions, labels)` だけは引数の順が逆なので、lambda で包み直しました。正解率は順を入れ替えても値が変わりませんが、評価関数の約束（正解が先）をそろえておくほうが、あとから読む人が迷いません。

```ruby
def test_正解率と回帰の評価指標も同じ形で呼べる
  assert_in_delta 0.6, C::ACCURACY.call(%w[1 1 1 0 0], %w[1 1 0 0 1]), 1e-12
  assert_in_delta 1.0, C::RMSE.call([1.0, 2.0], [2.0, 3.0]), 1e-12
  assert_in_delta 1.0, C::MAE.call([1.0, 2.0], [2.0, 1.0]), 1e-12
end
```

## 11.7 ROC 曲線と AUC

### 閾値を動かす

適合率と再現率は「正例と予測するかどうか」を決めたあとの指標です。確率を返すモデルなら、「どこで線を引くか」（閾値）自体を動かせます。閾値を高くすれば適合率が上がって再現率が下がり、低くすればその逆になります。

**ROC 曲線** は、閾値を高いほうから下げながら、横軸に偽陽性率（FPR = FP / 負例の数）、縦軸に真陽性率（TPR = TP / 正例の数）を取った曲線です。左上に寄るほどよいモデルで、その下の面積が **AUC** です。完全に分けられれば 1、当てずっぽうなら 0.5、順序が逆なら 0 になります。

### Red: 3 つの極端な場合と同じスコア

```ruby
def test_完全に分けられればAUCは一になる
  curve = C.roc_curve([0.9, 0.8, 0.2, 0.1], [true, true, false, false])

  assert_in_delta 1.0, C.auc(curve), 1e-12
end

def test_順が逆ならAUCは零になる
  curve = C.roc_curve([0.1, 0.2, 0.8, 0.9], [true, true, false, false])

  assert_in_delta 0.0, C.auc(curve), 1e-12
end

def test_混ざっているとAUCは中間の値になる
  # 正例のスコア 0.9・0.4、負例のスコア 0.6・0.1。組は 4 つで、正例が上なのは 3 つ
  curve = C.roc_curve([0.9, 0.6, 0.4, 0.1], [true, false, true, false])

  assert_in_delta 0.75, C.auc(curve), 1e-12
end
```

3 つ目は **三角測量** です。AUC は「正例と負例を 1 つずつ選んだとき、正例のスコアのほうが高い確率」と等しいので、手で数えた 3/4 と一致するはずだ、という別の道筋から答えを出しています。

同じスコアが並んだときは、正例と負例が **同時に** 正例と予測されるので、点を 1 つにまとめなければなりません。

```ruby
def test_同じスコアは一つの点にまとめる
  # 0.5 が正例と負例で 1 件ずつ。閾値 0.5 では両方が同時に正例になる
  curve = C.roc_curve([0.9, 0.5, 0.5, 0.1], [true, true, false, false])

  assert_equal 4, curve.size
  assert_in_delta 0.875, C.auc(curve), 1e-12
end
```

まとめずに 1 件ずつ点を打つと、並び順しだいで曲線が階段状になり、AUC が 0.875 からずれます。

### Green: スコアごとにまとめて足し込む

```ruby
# ROC 曲線の 1 点。threshold 以上を正例と予測したときの偽陽性率と真陽性率。
RocPoint = Data.define(:threshold, :false_positive_rate, :true_positive_rate)
```

```ruby
# スコア（正例らしさ）と正解（正例なら true）から ROC 曲線を求める。
# スコアの高いほうから閾値を下げていき、同じスコアは 1 つの点にまとめる。
def roc_curve(scores, labels)
  require_same_size(scores, labels)
  positives = labels.count(true)
  negatives = labels.size - positives
  raise ArgumentError, "正例と負例が両方ないと ROC 曲線を描けません" if positives.zero? || negatives.zero?

  [RocPoint.new(threshold: Float::INFINITY, false_positive_rate: 0.0, true_positive_rate: 0.0),
   *roc_points(scores, labels, positives, negatives)]
end

# 閾値ごとの点。スコアの高いほうから、同じスコアのかたまりごとに正例と負例の数を足し込む。
def roc_points(scores, labels, positives, negatives)
  true_positive = 0
  false_positive = 0

  score_groups(scores, labels).map do |threshold, group|
    true_positive += group.count(true)
    false_positive += group.count(false)

    RocPoint.new(threshold:, false_positive_rate: false_positive.fdiv(negatives),
                 true_positive_rate: true_positive.fdiv(positives))
  end
end

# スコアごとにラベルをまとめ、スコアの降順に並べる。
def score_groups(scores, labels)
  scores.zip(labels).group_by(&:first).sort_by { |score, _| -score }
        .map { |score, pairs| [score, pairs.map(&:last)] }
end
```

Rust 版は、スコアの降順に 1 件ずつ回しながら「スコアが変わる直前」を検出して点を打ちました。Ruby 版は **`group_by` で同じスコアを先にまとめてしまう** ので、「変わる直前」を覚えておく変数が要りません。`group.count(true)` は、配列の中の `true` の数を数えます。

最初の版は `sort_by` のあとに `chunk_while` で隣り合う同じスコアをまとめ、ブロックの中でラベルを数えていました。RuboCop の `Metrics/AbcSize` が 19（上限 17）と指摘したので、まとめる部分を `score_groups` に切り出しました。

面積は台形則です。

```ruby
# ROC 曲線の下の面積（AUC）を台形則で求める。
def auc(curve)
  curve.each_cons(2).sum do |left, right|
    (right.false_positive_rate - left.false_positive_rate) *
      (left.true_positive_rate + right.true_positive_rate) / 2.0
  end
end
```

`each_cons(2)` は「隣り合う 2 つ」を順に渡すメソッドで、Rust 版の `windows(2)` と同じ役目です。添字で `curve[i]` と `curve[i + 1]` を書かないので、範囲外を踏む余地がありません。

## 11.8 K 分割交差検証

### 分け方は 2 つ用意する

K 分割は「行の位置を k 個のかたまりに分け、1 つをテストデータ、残りを訓練データにする」だけです。

```ruby
# 交差検証の 1 回分の分け方。行の位置（0 始まり）を訓練データとテストデータに分けて持つ。
Fold = Data.define(:train, :test)
```

Rust 版と同じく、**並べ替えあり** と **並べ替えなし** の 2 つを作ります。並べ替えは第 2 章の `shuffle`（`Array#shuffle(random: Random.new(seed))`）を位置の配列に使い回します。

```ruby
# 並べ替えずに、先頭から順に k 個のかたまりに分ける。余りは先頭の分割から 1 件ずつ配る。
def k_fold_sequential(n_samples, n_splits)
  check_splits(n_samples, n_splits)
  folds((0...n_samples).to_a, n_splits)
end

# シード付きの乱数で行を並べ替えてから k 個のかたまりに分ける。並べ替えは第 2 章の shuffle を使う。
def k_fold(n_samples, n_splits, seed)
  check_splits(n_samples, n_splits)
  folds(Chapter02.shuffle((0...n_samples).to_a, seed), n_splits)
end
```

```ruby
# 並べた位置を k 個のかたまりに分け、かたまりごとに 1 つをテストデータ、残りを訓練データにする。
def folds(positions, n_splits)
  sizes = Array.new(n_splits) { |index| (positions.size / n_splits) + (index < positions.size % n_splits ? 1 : 0) }
  tests = sizes.each_with_object([]) { |size, result| result << positions[result.sum(&:size), size] }

  tests.map { |test| Fold.new(train: positions - test, test:) }
end
```

訓練データは `positions - test`（配列の差）で求めます。Rust 版の `filter(|position| !test.contains(position))` と同じことを、集合の演算の名前で書けます。

### 性質をテストで固定する

具体的な分け方そのものより、**満たすべき性質** をテストにします。

```ruby
def test_テストデータは重ならず全体を覆う
  assert_equal (0...10).to_a, C.k_fold(10, 5, 0).flat_map(&:test).sort
end

def test_訓練データとテストデータは交わらない
  C.k_fold(10, 5, 0).each do |fold|
    assert_equal 8, fold.train.size
    assert_empty fold.train & fold.test
  end
end

def test_分割の数が少なすぎると失敗する
  error = assert_raises(ArgumentError) { C.k_fold(10, 1, 0) }

  assert_equal "分割の数は 2 以上 10 以下にしてください: 1", error.message
end
```

「交わらない」は `fold.train & fold.test`（配列の積）が空であることで確かめます。

### 交差検証の手順

```ruby
# 分割ごとに新しいモデルを作って訓練データで学習し、テストデータの予測を評価関数で採点する。
# make_model は呼ぶたびに新しいモデルを返す lambda なので、前の分割で学習した重みが残らない。
def cross_validate(make_model, x, t, folds, metric)
  folds.map do |fold|
    model = make_model.call.fit(x.values_at(*fold.train), t.values_at(*fold.train))

    metric.call(t.values_at(*fold.test), model.predict(x.values_at(*fold.test)))
  end
end
```

モデルそのものではなく **モデルを作る lambda** を受け取るのは、Rust 版の `&dyn Fn() -> Box<dyn Model<T>>`、Java 版の `Supplier` と同じ理由です。同じモデルを使い回すと、前の分割の学習結果が残るかもしれません。行の選び出しは、第 10 章のブートストラップ標本と同じ `values_at` です。

どれかの分割で例外が起きれば、そこで `map` が止まり、残りの分割は評価されません。Rust 版は `Result<Vec<f64>>` に `collect` して同じ振る舞いを型で表しましたが、Ruby 版は例外がそのまま伝わるだけです。

## 11.9 モデルは fit と predict を持っていればよい

分類（正解ラベルが文字列）と回帰（数値）を、同じ `cross_validate` にかけます。

第 3 章の決定木は、何も足さずにそのまま渡せます。`fit` が自分を返し、`predict` がラベルの配列を返すからです。

```ruby
def test_決定木も同じ交差検証にかけられる
  # 0 と 1 が交互に並ぶように並べ替えておけば、どの分割の訓練データにも両方のラベルが入る
  x = [0.1, 0.7, 0.2, 0.8, 0.3, 0.9].map { |value| column(value) }
  t = %w[0 1 0 1 0 1]
  make = -> { GettingStartedMl::Chapter03::DecisionTree.new(max_depth: 1) }

  assert_equal [1.0, 1.0, 1.0], C.cross_validate(make, x, t, C.k_fold_sequential(6, 3), C::ACCURACY)
end
```

Rust 版は `trait Model<T>` を宣言し、第 3 章の型に `impl Model<String> for DecisionTree` を足しました（孤児ルールのおかげでアダプターは要りませんでした）。Ruby 版はトレイトの宣言も `impl` も要りません。**約束が守られていることはテストで確かめる** のが、第 10 章から続く Ruby 版の流儀です。

第 7 章の線形回帰は、`fit` が「学習したモデル（`LinearModel`）を返す関数」なので、学習の前後を持つ薄いクラスで包みます。

```ruby
# 第 7 章の線形回帰を、fit と predict を持つモデルにする。
# 第 7 章の fit は「学習したモデルを返す関数」なので、学習する前は nil を持つ。
class LinearRegressionModel
  def initialize
    @model = nil
  end

  # 訓練データで学習する。メソッドをつなげられるように自分を返す。
  def fit(x, t)
    @model = Chapter07.fit(x, t)
    self
  end

  # 特徴量ごとの予測値。
  def predict(x)
    raise "学習してから予測してください" if @model.nil?

    @model.predict(x)
  end
end
```

Rust 版は「まだ無い」を `Option<LinearModel>` で表し、確かめ忘れるとコンパイルが通りませんでした。Ruby 版は `nil` を持ち、確かめ忘れていないことはテストで押さえます。失敗の文言は第 3 章の決定木にそろえました。

```ruby
def test_学習する前に予測すると失敗する
  error = assert_raises(RuntimeError) { C::LinearRegressionModel.new.predict([column(1.0)]) }

  assert_equal "学習してから予測してください", error.message
end
```

## 11.10 Rumale の評価指標と突き合わせる

### Rumale にはラベルを番号で渡す

Rumale の評価指標は Numo の配列を受け取るので、文字列のラベルを番号にします。ここで **番号の付け方** が大事になります。

```ruby
# 文字列のラベルを、昇順に並べた位置の番号（Numo::Int32）にする。
# Rumale の Precision・Recall・FScore（average: "binary"）は、正解に現れるラベルを昇順に並べて
# **最後のもの**を正例にする。つまり大きいほうのラベルが正例になる。
def encode_sorted(actual, predicted)
  classes = (actual + predicted).uniq.sort

  [actual, predicted].map { |labels| Numo::Int32.cast(labels.map { |label| classes.index(label) }) }
end
```

Rumale の `Precision#score` は、`average: "binary"`（既定）のとき `precision_each_class(y_true, y_pred).last` を返します。`precision_each_class` は `y_true.sort.to_a.uniq` の順にラベルごとの適合率を並べるので、**最後、つまり大きいほうのラベルが正例** です。linfa の「2 値のときだけ並びを逆にする」と同じ結論に、実装を読んでたどり着きました。

第 3 章の `RumaleTree.encode` は「最初に現れた順」に番号を付けるので、そのまま使うとデータの並びしだいで正例が入れ替わります。この章では昇順に付け直します。

```ruby
def test_Rumaleは大きいほうのラベルを正例にする
  # "no" と "yes" なら "yes" が正例。"no" を正例にした自作の値とは合わない
  actual = %w[yes yes yes no no]
  predicted = %w[yes no no no yes]
  scores = C.rumale_scores(actual, predicted)

  assert_in_delta C::ConfusionMatrix.of(actual, predicted, "yes").precision, scores.precision, 1e-12
  refute_in_delta C::ConfusionMatrix.of(actual, predicted, "no").precision, scores.precision, 1e-12
end
```

`Survived` は `"0"` と `"1"` なので `"1"`（生存）が正例になり、自作と同じ向きになります。

### 混同行列は「正解が行、予測が列」

`Rumale::EvaluationMeasure.confusion_matrix` は、正解のラベルを昇順に並べ、**正解を行、予測を列** にした `Numo::Int32` の行列を返します。

```ruby
def test_Rumaleの混同行列は正解を行に予測を列にしてラベルの昇順に並べる
  # ラベルの昇順は "0"・"1" なので、1 行目が負例（TN FP）、2 行目が正例（FN TP）になる
  assert_equal [[1, 1], [2, 2]], C.rumale_confusion_matrix(ACTUAL, PREDICTED)
end
```

11.4 節の表とは並びが逆（負例が先）です。Rust 版では linfa の `confusion_matrix` が「レシーバを行、引数を列」として数えるため、ドキュメントの例どおりに予測をレシーバにすると適合率と再現率が入れ替わりました。Rumale は **引数が `(y_true, y_pred)` の順に決まっている** ので、その取り違えは起きません。行と列の意味を 1 度テストで固定しておけば十分です。

### 適合率・再現率・F 値は一致する

```ruby
# Rumale の Accuracy・Precision・Recall・FScore で求めたスコア。
def rumale_scores(actual, predicted)
  truth, prediction = encode_sorted(actual, predicted)
  measures = [Rumale::EvaluationMeasure::Accuracy.new, Rumale::EvaluationMeasure::Precision.new,
              Rumale::EvaluationMeasure::Recall.new, Rumale::EvaluationMeasure::FScore.new]

  RumaleScores.new(*measures.map { |measure| measure.score(truth, prediction) })
end
```

```ruby
def test_Rumaleの適合率と再現率とF値は自作と一致する
  matrix = C::ConfusionMatrix.of(ACTUAL, PREDICTED, "1")
  scores = C.rumale_scores(ACTUAL, PREDICTED)

  assert_in_delta matrix.accuracy, scores.accuracy, 1e-12
  assert_in_delta matrix.precision, scores.precision, 1e-12
  assert_in_delta matrix.recall, scores.recall, 1e-12
  assert_in_delta matrix.f1_score, scores.f1_score, 1e-12
end
```

`ACTUAL`・`PREDICTED` は 11.5 節と同じ、適合率（2/3）と再現率（1/2）が食い違う例です。4 つの指標とも `1e-12` の許容で一致しました。linfa は `f32` で数えたので `1e-6` の許容が要りましたが、Rumale は `Float`（倍精度）で返します。

### ROCAUC は同じスコアのまとめ方まで同じ

Rumale の `ROCAUC#score` は、正解を整数、スコアを `Numo::DFloat` で受け取ります。

```ruby
# Rumale の ROCAUC で AUC を求める。正解は正例なら 1、負例なら 0 の整数にして渡す。
def rumale_auc(scores, labels)
  Rumale::EvaluationMeasure::ROCAUC.new.score(Numo::Int32.cast(labels.map { |label| label ? 1 : 0 }),
                                              Numo::DFloat.cast(scores))
end
```

Rumale の実装は、スコアを降順に並べてから `diff.ne(0)` で「スコアが変わる位置」だけを閾値にします。つまり **同じスコアは 1 つの点にまとめる**、自作と同じ方針です。同じスコアが並ぶ例でも確かめました。

```ruby
def test_同じスコアが並んでもRumaleのAUCと一致する
  scores = [0.9, 0.5, 0.5, 0.5, 0.1]
  labels = [true, true, false, true, false]

  assert_in_delta C.auc(C.roc_curve(scores, labels)), C.rumale_auc(scores, labels), 1e-12
end
```

### ROC を描くための確率

決定木はラベルしか返さないので、ROC 曲線のための「正例らしさ」がありません。この章の主題は評価指標なので、確率は Rumale のロジスティック回帰から借ります。

```ruby
# Rumale のロジスティック回帰で、正例らしさの確率を求める。ROC 曲線に使う。
# 正解を「正例なら 1、そうでなければ 0」にして学習するので、predict_proba の 2 列目が正例の確率になる。
def positive_probabilities(x_train, t_train, x_test, positive)
  codes = Numo::Int32.cast(t_train.map { |label| label == positive ? 1 : 0 })
  model = Rumale::LinearModel::LogisticRegression.new.fit(Chapter03::RumaleTree.matrix(x_train), codes)

  model.predict_proba(Chapter03::RumaleTree.matrix(x_test))[true, 1].to_a
end
```

Rust 版では linfa が **件数の多いほうのクラスを正例** にしたので、学習したモデルに向きを聞いて確率を裏返す必要がありました。Rumale の `predict_proba` は、ラベルを昇順に並べた列の順で確率を返します。正例を 1 に番号付けして渡せば 2 列目（`[true, 1]`）が正例の確率になり、裏返す処理は要りません。

## 11.11 Rumale の交差検証と突き合わせる

### KFold は行の分け方まで一致する

Rumale の `KFold#split` の実装を読むと、次のように書かれています。

- `dataset_ids.shuffle!(random: sub_rng) if @shuffle` — 並べ替えは `Array#shuffle!` に `Random.new(random_seed)`（の複製）を渡す
- `n_fold_samples += 1 if n < n_samples % @n_splits` — 余りは先頭の分割から 1 件ずつ配る

自作の `k_fold` は、第 2 章の `shuffle`（`items.shuffle(random: Random.new(seed))`）で並べ替え、余りを先頭から配ります。**同じ乱数の生成器を同じシードで作り、同じメソッドで並べ替えている** ので、分け方は行の番号まで一致するはずです。

```ruby
# Rumale の KFold。シードを渡せば行を並べ替えてから、渡さなければ先頭から順に分ける。
# 並べ替えないときも random_seed を渡す（省くと srand でプロセス全体の乱数の種が入れ替わる）。
def rumale_kfold(n_splits, seed)
  Rumale::ModelSelection::KFold.new(n_splits:, shuffle: !seed.nil?, random_seed: seed || 0)
end

# Rumale の KFold の分け方を、自作と同じ Fold の並びにする。
def rumale_folds(n_samples, n_splits, seed: nil)
  rumale_kfold(n_splits, seed).split(Numo::DFloat.zeros(n_samples, 1)).map do |train, test|
    Fold.new(train:, test:)
  end
end
```

```ruby
def test_並べ替えるKFoldは同じシードなら自作と行の分け方まで一致する
  assert_equal C.k_fold(11, 3, 7), C.rumale_folds(11, 3, seed: 7)
end

def test_並べ替えないKFoldは自作の並べ替えなしと一致する
  assert_equal C.k_fold_sequential(11, 3), C.rumale_folds(11, 3)
end
```

どちらも一致しました。実データの 891 件・5 分割でも一致します（11.12 節）。Rust 版の linfa は並べ替えない分け方しか持たないので「並べ替えなし」に合わせて突き合わせましたが、Ruby 版は **並べ替えありのまま、分割ごとのスコアを突き合わせられます**。同じ言語の標準ライブラリの乱数を、自作もライブラリも使っているからです。

`KFold` にも第 10 章で見つけた癖があります。`@random_seed ||= srand` と書かれているので、`random_seed` を省くとプロセス全体の乱数の種が入れ替わります。`srand(42)` のあとに `rand` を呼ぶ場合と、`srand(42)` のあとに `KFold.new(n_splits: 5)` を挟んでから `rand` を呼ぶ場合で、値が変わりました。並べ替えない（`shuffle: false`）ときも乱数の生成器は作られるので、**必ず `random_seed` を渡します**。

### CrossValidation は分割ごとのスコアを返す

`Rumale::ModelSelection::CrossValidation` は、推定器・分割器・評価器を受け取り、`perform` で分割ごとのスコアを `{ test_score: [...], ... }` の Hash で返します。

| 論点 | 自作 | Rumale | linfa（Rust 版） |
|------|------|--------|------------------|
| 行の並べ替え | `Random.new(seed)` で並べ替えてから分ける | 同じ（`shuffle: true` のとき） | 並べ替えない |
| 戻り値 | 分割ごとのスコア | 分割ごとのスコア（`test_score`） | モデルごとの平均だけ |
| モデル | 分割ごとに lambda で新しく作る | **同じ推定器を毎回 `fit` し直す** | 分割ごとに作る |
| 評価器 | 正解と予測を受け取る `call` | `score(y_true, y_pred)` を持つオブジェクト | 閉包 |

Rumale の評価器に RMSE は無いので、平均二乗誤差（`MeanSquaredError`）を求めてから分割ごとに平方根を取ります。

```ruby
# Rumale の CrossValidation で、分割ごとの RMSE を求める。
# Rumale の評価指標は平均二乗誤差（MSE）なので、分割ごとに平方根を取る。
def rumale_cross_validate_rmse(x, t, n_splits, seed: nil)
  validation = Rumale::ModelSelection::CrossValidation.new(
    estimator: Rumale::LinearModel::LinearRegression.new(tol: Chapter07::RumaleRegression::TOLERANCE),
    splitter: rumale_kfold(n_splits, seed), evaluator: Rumale::EvaluationMeasure::MeanSquaredError.new
  )

  validation.perform(Chapter03::RumaleTree.matrix(x), Numo::DFloat.cast(t))[:test_score].map { |mse| Math.sqrt(mse) }
end
```

推定器の `tol` には、第 7 章で決めた `1e-10` を渡します。Rumale の `LinearRegression` は Numo::Linalg が無いと L-BFGS で解くので、既定の `tol: 1e-4` では厳密な解の手前で止まります。この章でも確かめました。cinema の 5 分割で、`tol: 1e-10` なら分割ごとの RMSE の差は最大 0.000116 でしたが、**既定の `tol` では最大 685.88 ずれました**（自作の 451.26 に対して Rumale が 1082.44 など）。交差検証は同じ推定器を 5 回学習し直すので、収束の判定のゆるさも 5 回分効いてきます。

```ruby
def test_Rumaleの交差検証のRMSEは同じ分け方なら自作と一致する
  x, t = regression_data
  own = C.cross_validate(-> { C::LinearRegressionModel.new }, x, t, C.k_fold(12, 4, 0), C::RMSE)

  C.rumale_cross_validate_rmse(x, t, 4, seed: 0).zip(own).each do |library, mine|
    assert_in_delta mine, library, 1e-6
  end
end
```

`regression_data` は、直線に乗らない 12 件の自作のデータです（直線に乗るデータだと RMSE がどちらも 0 になり、突き合わせになりません）。

## 11.12 実データで評価する

### 簡略化した前処理

`Survived.csv` は、客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、`Survived` の文字列を正解ラベルにします。年齢の欠損値は **全体の** 平均で補います。

```ruby
# 客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解ラベルにする。
# 年齢の欠損値は全体の平均値で補う。特徴量と正解ラベルの組を返す。
def prepare_survived(table)
  age_mean = Chapter02.column_means(table.rows, ["Age"]).fetch("Age")
  x = table.rows.map do |row|
    Chapter02::Features.new(columns: SURVIVED_FEATURES,
                            values: [Chapter07::Cinema.number(row, "Pclass"), row.number("Age") || age_mean,
                                     row.text("Sex") == "male" ? 1.0 : 0.0])
  end

  [x, table.rows.map { |row| row.text(SURVIVED_TARGET) }]
end
```

本来は分割ごとに訓練データだけで補完すべきですが（第 2 章・第 9 章で扱ったリーク）、交差検証そのものを見せるために手順を短くしています。モジュールの先頭のコメントにその断りを書いておきます。

第 2 章の `Row#number` は空欄なら `nil` を返すので、補完は `row.number("Age") || age_mean` の 1 つの式です。Rust 版の `unwrap_or(age_mean)` と同じ形で、Rust 版の `f64::from(u8::from(...))` の 2 段の変換は、Ruby では三項演算子の `1.0 : 0.0` で済みます。

cinema は第 7 章の 4 列を特徴量にし、欠損値を列ごとの平均で補います。Rust 版と同じく、第 7 章の外れ値の除去はしません（100 件のまま）。

### 実行結果

```bash
bundle exec rake 'run[chapter11]'
```

```text
Survived（決定木・深さ 2）
  件数: 891
  正解率（5 分割の平均）: 0.7677
  適合率（5 分割の平均）: 0.8239
  再現率（5 分割の平均）: 0.5640
  F値（5 分割の平均）: 0.6384
  分け方（自作の k_fold と Rumale の KFold）: 一致
  ROC 曲線の点の数: 117
  AUC（自作）: 0.8376
  AUC（Rumale）: 0.8376
  混同行列（1 つ目の分割）: TP=52 FP=22 FN=17 TN=88
  Rumale の混同行列: [[88, 22], [17, 52]]
  適合率: 自作 0.7027 / Rumale 0.7027

cinema（線形回帰）
  件数: 100
  RMSE（5 分割の平均）: 406.49
  MAE（5 分割の平均）: 321.31
  RMSE（並べ替えあり）: 自作 406.49 / Rumale 406.49
  RMSE（並べ替えなし）: 自作 410.42 / Rumale 410.42
```

この表示は `test/survived_cross_validation_test.rb` で固定しています。読み取れることは次のとおりです。

- **正解率 0.7677 だけでは「見逃しの多さ」が見えない**。適合率 0.8239 に対して再現率 0.5640 なので、このモデルは「生存」と言い切るのに慎重で、生存者の 4 割以上を見逃しています。救命の優先順位を決める用途なら、再現率を上げる方向に調整すべきだと分かります
- **平均と 1 つの分割は違うことを言う**。1 つ目の分割だけを見ると適合率 0.7027・再現率 52 / 69 = 0.7536 で、平均とは大小が逆でした。1 回の分割で測った値だけで「このモデルは慎重だ」とは言えず、5 回の平均で初めて傾向が見えます
- **自作と Rumale は、分け方・AUC・混同行列・RMSE がすべて一致した**。Rumale の混同行列 `[[88, 22], [17, 52]]` は、負例の行（TN=88・FP=22）が先に来るだけで、自作の 4 つの数と同じです。RMSE は並べ替えありでも並べ替えなしでも一致しました
- **分け方を変えると値が動く**。cinema の RMSE は並べ替えありで 406.49、並べ替えなしで 410.42 でした。並べ替えなしの 410.42 は Rust 版の値と一致します。並べ替えない分け方は言語によらず同じになるからです。並べ替えありの値は、乱数の生成器が違うので Rust 版（409.05）とは一致しません

分割が違うので、Rust 版の値（正解率 0.7755、適合率 0.8052、再現率 0.5649）とは一致しません。**「適合率が再現率よりはっきり高い」という傾向は Rust 版・Java 版と同じ** でした。

### 実データのテスト

```ruby
def test_実データの交差検証で適合率と再現率が食い違う
  x, t = C.prepare_survived(table("Survived.csv"))
  folds = C.k_fold(x.size, 5, 0)
  make = -> { GettingStartedMl::Chapter03::DecisionTree.new(max_depth: 2) }
  precision, recall = %i[precision recall].map do |score|
    C.mean(C.cross_validate(make, x, t, folds, C.classification_metric(score, "1")))
  end

  # 深さ 2 の決定木は「生存」と言い切るのに慎重で、適合率は高いが再現率は低い
  assert_in_delta 0.8239, precision, 1e-4
  assert_in_delta 0.5640, recall, 1e-4
end

def test_実データの交差検証のRMSEは分割ごとにRumaleと一致する
  x, t = C.prepare_cinema(table("cinema.csv"))
  own = C.cross_validate(-> { C::LinearRegressionModel.new }, x, t, C.k_fold(x.size, 5, 0), C::RMSE)

  C.rumale_cross_validate_rmse(x, t, 5, seed: 0).zip(own).each do |library, mine|
    assert_in_delta mine, library, 1e-3
  end
end
```

実データの RMSE は数百の大きさなので、L-BFGS の誤差（最大 0.000116）を見込んで `1e-3` の許容にしています。学習データが無い環境では、これまでの章と同じく Minitest の `skip` でスキップします。

```ruby
def table(name)
  path = File.join(GettingStartedMl::Dataset.dir, name)
  skip "学習データ #{name} が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

  GettingStartedMl::Chapter02::Table.load(path)
end
```

### 品質チェック

```bash
bundle exec rake check
```

第 11 章を足した時点で、`bundle exec rake check` は 218 件のテストがすべて通ります。データが無い環境（`ML_DATA_DIR=/nonexistent`）では 28 件がスキップになりました。

RuboCop は 6 件を指摘しました。

- **`Metrics/AbcSize`** — `roc_points`（19）と `cinema_lines`（23.79）が上限 17 を超えました。`roc_points` は同じスコアをまとめる部分を `score_groups` に、`cinema_lines` は「自作と Rumale の RMSE を並べる 1 行」を `rmse_line` に切り出しました。テストの 1 件（23.28）も、自作のデータを作る部分を `regression_data` に切り出しました
- **`Layout/LineLength`** — `positive_probabilities` の 1 行が 123 文字でした。正解を番号にする式を変数 `codes` に取り出しました
- **`Lint/AmbiguousBlockAssociation`** — テストの `assert_equal [3, 2, 2], folds.map { ... }` のように、括弧の無い引数にブロックを付けた書き方が 2 件ありました。`rubocop -a` で括弧を補いました

## 11.13 Notebook で探索する

Ruby 版では Notebook と可視化を扱いません。混同行列のヒートマップ、ROC 曲線、分割ごとのスコアのばらつきの可視化は、[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と [Kotlin 版の第 11 章](../kotlin/11-evaluation-metrics-and-cross-validation.md) の可視化の節を参照してください。Ruby 版の `roc_curve` は `RocPoint`（閾値・偽陽性率・真陽性率）の配列を返すので、そのまま曲線として読めます。分割が違うので値そのものは一致しません。

## 11.14 まとめ

この章では、モデルを多面的に、偶然に左右されにくく評価する方法を Ruby の TDD で実装しました。

| 作ったもの | 自作 | 突き合わせたライブラリ |
|-----------|------|-------------------|
| 混同行列・適合率・再現率・F 値 | `ConfusionMatrix` | Rumale の `confusion_matrix`・`Precision`・`Recall`・`FScore`（**大きいほうのラベルが正例**。一致） |
| ROC 曲線・AUC | `roc_curve`・`auc` | Rumale の `ROCAUC`（同じスコアのまとめ方まで同じ。一致） |
| K 分割交差検証 | `k_fold`・`k_fold_sequential`・`cross_validate` | Rumale の `KFold`・`CrossValidation`（**並べ替えありでも行の分け方まで一致**） |

Ruby らしさが出たのは次の 4 点です。

1. **`call` に応えるものが評価関数になる** — 混同行列の指標は lambda、第 7 章の RMSE は `Method` オブジェクト。Rust 版の `Box<dyn Fn>` のように型をそろえる手間が無い
2. **数え方を集計のメソッドで書く** — 混同行列は `tally`、ROC 曲線の同じスコアは `group_by`、訓練データは配列の差 `-`、交わらないことは配列の積 `&`。条件分岐と添字を書かずに済む
3. **共通の型を宣言しない** — 第 3 章の決定木は何も足さずに交差検証にかけられる。第 7 章の線形回帰は `nil` を持つ薄いクラスで包むだけ
4. **同じ乱数なら同じ分け方になる** — 自作も Rumale も `Random.new(seed)` と `Array#shuffle` で並べ替えるので、並べ替えありの K 分割が行まで一致した。Rust 版の linfa では「並べ替えなし」でしか突き合わせられなかった

ライブラリとの突き合わせでは、**既定値と番号の付け方を実装で確かめる** ことが効きました。Rumale の `Precision` は大きいほうのラベルを正例にし、`KFold` は `random_seed` を省くとプロセス全体の乱数を初期化し直し、`LinearRegression` は既定の `tol` のままだと交差検証の RMSE が最大 685.88 ずれました。

Survived の決定木は、5 分割交差検証で正解率 0.7677、適合率 0.8239、再現率 0.5640 でした。正解率だけでは分からなかった「見逃しの多さ」が、再現率で見えるようになりました。次の章では、正則化によって過学習を抑え、検証データを使ってモデルの設定を選ぶ方法を学びます。
