---
type: Article
title: "第 7 章: 線形回帰による数値予測"
description: "numo-narray-alt の行列と自作の掃き出し法で正規方程式を解いて重回帰を TDD で実装し、Rumale の LinearRegression と切片・係数を突き合わせる。既定の L-BFGS が許容誤差 1e-4 で止まり、実データでは切片をほぼ 0 のまま返すことを実測で確かめる。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:22:46Z }
---

# 第 7 章: 線形回帰による数値予測

## 7.1 はじめに

第 1〜3 章では、グループを当てる **分類** を扱いました。この章からは、数値そのものを当てる **回帰** に進みます。題材は、映画の SNS での反響や主演俳優の露出度から、興行収入を予測する問題です。

この章では、最も基本的な回帰モデルである **線形回帰** を自作します。Ruby 版は **numo-narray-alt** の `Numo::DFloat` を行列として使い、**連立方程式を解く手続きだけを自作** します。行列の積や転置は Numo にあり、連立方程式を解くメソッドは Numo に無いからです。[Rust 版の第 7 章](../rust/07-linear-regression.md) の ndarray と同じ立ち位置です。

そのあと、Rumale の `LinearModel::LinearRegression` に置き換えて結果を突き合わせます（[ADR 010](../../../adr/010-ruby-ml-libraries.md)）。[Python 版の第 7 章](../python/07-linear-regression.md) と同じく、外れ値の除去と回帰の評価指標（MAE・RMSE・決定係数 R²）も実装します。この章の評価指標は第 11〜12 章でも使います。

この章で拾う Ruby の論点は、**ライブラリの既定値が黙って結果を変える** ことです。Rumale の線形回帰は、既定では厳密な解を計算せず、反復法（L-BFGS）で近づいていきます。その止めどころの既定値のままだと、実データでは切片がほぼ 0 のまま返ってきます。例外も警告も出ません。型も引数の検査も通り抜けるこの種の誤りを捕まえるのは、自作との突き合わせのテストです。

## 7.2 線形回帰とは

### 特徴量の重み付きの和で予測する

線形回帰は、予測値を「切片」と「特徴量ごとの係数 × 特徴量」の和で表すモデルです。この章のデータに当てはめると次の式になります。

```text
予測値 = 切片 + 係数1 × SNS1 + 係数2 × SNS2 + 係数3 × actor + 係数4 × original
```

学習とは、訓練データに対して予測値と実測値のずれが最も小さくなるように、切片と係数を決めることです。ずれの大きさには「誤差の 2 乗の合計」を使います。これを最小にする方法を **最小二乗法** と呼びます。

### 正規方程式

最小二乗法の解は、行列を使うと 1 つの式で求められます。特徴量を行列 `X`、実測値をベクトル `t`、切片と係数をまとめたベクトルを `w` とすると、誤差の 2 乗の合計を最小にする `w` は次の **正規方程式** を満たします。

```text
(Xᵀ X) w = Xᵀ t
```

`X` の先頭には、すべての値が 1 の列を足しておきます（**計画行列**）。この列にかかる係数が切片になります。

やることは 3 つです。行列の積、転置、そして連立方程式を解くこと。前の 2 つは Numo が持っています。3 つめは持っていないので、自分で書きます。

## 7.3 題材とデータ

この章で使うのは `cinema.csv` です。100 本の映画について、次の 6 列が記録されています。

| 列 | 内容 | 欠損値 |
|----|------|-------|
| cinema_id | 映画の ID | なし |
| SNS1 | SNS での反響の数（1 つ目の指標） | 1 件 |
| SNS2 | SNS での反響の数（2 つ目の指標） | なし |
| actor | 主演俳優のメディア露出の指標 | 1 件 |
| original | 原作の有無（0 または 1） | なし |
| sales | 興行収入 | なし |

「SNS1」「SNS2」「actor」「original」が特徴量、「sales」が正解ラベル（予測したい数値）です。`cinema_id` は映画を区別するための番号なので、特徴量には使いません。

このデータには、SNS2 の値が大きいのに興行収入が低い、傾向から外れた映画が 1 本含まれています。このような **外れ値** は、最小二乗法の結果を大きく引っ張ります。誤差を 2 乗して足し合わせるので、大きく外れた 1 点の影響が大きくなるためです。

散布図で外れ値を確かめる手順は、[Python 版](../python/07-linear-regression.md) と [Kotlin 版の Notebook](../kotlin/07-linear-regression.md) を参照してください。Ruby 版には Notebook と可視化の節を設けません。

## 7.4 TODO リストの作成

**TODO リスト**:

- [ ] 連立方程式を解く
  - [ ] 2 元 1 次の連立方程式を解く
  - [ ] 先頭のピボットが 0 でも解く
  - [ ] 解が定まらなければ例外を投げる
  - [ ] 元の行列と右辺を書き換えない
- [ ] 評価指標を計算する（MAE・RMSE・R²）
- [ ] 正規方程式で線形回帰を学習する
  - [ ] 計画行列を作る
  - [ ] 直線上の点から切片と係数を求める
  - [ ] 複数の特徴量から切片と係数を求める
- [ ] 学習したモデルで予測する
- [ ] 外れ値を取り除く
  - [ ] SNS2 が 1000 を超え、興行収入が 8500 未満の行を取り除く
  - [ ] 条件の片方だけを満たす行は残す
- [ ] 外れ値の除去・分割・補完をまとめる
- [ ] Rumale と結果が一致することを確かめる
- [ ] 実データで学習・評価して表示する

外れ値の条件「SNS2 が 1000 を超え、興行収入が 8500 未満」は、書籍『スッキリわかる Python による機械学習入門』の同じデータでの分析手順に合わせたものです。「元の行列と右辺を書き換えない」は Ruby 版で足した項目です。理由は 7.6 節で述べます。

## 7.5 行列のライブラリを選ぶ

### numo-narray-alt を直接は入れない

この章から Numo を本格的に使います。ただし、`Gemfile` には Numo を書きません。

```ruby
gem "csv", "~> 3.3"
gem "rumale", "~> 2.2"
```

Rumale 2.x が依存しているのは、本家の `numo-narray`（0.9.2.1、2022 年 8 月から更新が無い）ではなく、Rumale の作者によるフォークの **`numo-narray-alt`**（0.11.2）です。Rumale を入れれば `numo-narray-alt` も入ります。ここに本家の `numo-narray` を足すと、どちらも同じ `numo/narray` というファイル名と `Numo::NArray` という名前を持つので、衝突の警告が出ます（[ADR 010](../../../adr/010-ruby-ml-libraries.md) の確認）。

コードでは `require "numo/narray"` と書きます。この 1 行で読み込まれるのが本家かフォークかは、`Gemfile.lock` が決めます。Rust 版では「ndarray の版が違えば別の型になり、コンパイルが止まる」ことが論点でした。Ruby 版で両方を入れたときに出るのは **警告** で、止まりはしません。依存を 1 つに絞る判断は人がします。

### Numo に無いもの

`Numo::DFloat` は行列の積（`dot`）と転置（`transpose`）を持っていますが、連立方程式を解くメソッドは持っていません。解く機能は `Numo::Linalg`（`numo-linalg-alt`）にありますが、これは LAPACK へのバインディングで、Rumale の依存には入っていません。試しに参照すると、定数そのものがありません。

```text
#<NameError: uninitialized constant Numo::Linalg>
```

第 7 章で必要なのは「5×5 の正方行列を 1 回解く」ことだけなので、そのぶんだけを自作します。

| | 行列 | 連立方程式 |
|---|---|---|
| Python 版 | NumPy の `ndarray` | scikit-learn に任せる |
| Rust 版 | ndarray の `Array2<f64>` | 自作（掃き出し法） |
| Ruby 版 | numo-narray-alt の `Numo::DFloat` | 自作（掃き出し法） |

## 7.6 連立方程式を解く

### Red: 解けることを先に書く

`test/chapter07_test.rb` に、テストから書きます。

```ruby
class Chapter07Test < Minitest::Test
  C = GettingStartedMl::Chapter07

  def test_二元一次の連立方程式を解く
    w = C.solve(Numo::DFloat[[2, 1], [1, 3]], Numo::DFloat[5, 10])

    assert_in_delta 1.0, w[0], 1e-12
    assert_in_delta 3.0, w[1], 1e-12
  end
```

`2x + y = 5`、`x + 3y = 10` の解は `x = 1`、`y = 3` です。まだ `Chapter07` が無いので、テストファイルを読み込んだ時点で止まります。

```text
test/chapter07_test.rb:6:in `<class:Chapter07Test>': uninitialized constant GettingStartedMl::Chapter07 (NameError)
```

第 1 章と同じく、Red はコンパイルエラーではなく実行時の `NameError` です。

### Green: 部分ピボット選択つきの掃き出し法

```ruby
# 部分ピボット選択つきのガウス・ジョルダンの掃き出し法で matrix w = rhs を解く。元の行列と右辺は変えない。
# numo-narray-alt には連立方程式を解くメソッドが無い（numo-linalg-alt は LAPACK を要求する）ので自作する。
def solve(matrix, rhs)
  size = matrix.shape[0]
  check_size(size, matrix.shape[1])
  check_size(size, rhs.size)

  # 係数行列と右辺を並べた拡大係数行列にしてから掃き出す。hstack は新しい行列を作る
  work = Numo::DFloat.hstack([matrix, rhs.reshape(size, 1)])
  size.times { |pivot| eliminate(work, pivot) }
  work[true, size].dup
end
```

`work[true, size]` は「すべての行の、`size` 番目の列」です。Numo では添字に `true` を書くと「その軸の全部」を表します。NumPy の `work[:, size]` に当たります。

引数の名前が `a`・`b` でないのは RuboCop の `Naming/MethodParameterName` が 3 文字未満の名前を咎めるからです。`.rubocop.yml` では機械学習の慣例の `x`・`t`・`y` だけを許しているので、ここは `matrix`・`rhs`（right-hand side、右辺）にしました。

掃き出しの本体は、ピボットの行を選んで入れ替え、ほかの行のピボット列を 0 にします。

```ruby
# ピボットの行を選んで入れ替え、ほかの行のピボット列を 0 にする。
def eliminate(work, pivot)
  row = pivot_row(work, pivot)
  raise ArgumentError, "解けない連立方程式です" if work[row, pivot].abs < SINGULAR

  swap_rows(work, pivot, row)
  sweep(work, pivot)
end

# 2 つの行を入れ替える。添字の配列で 2 行をまとめて取り出し、逆の順で書き戻す。
# 右辺はビューなので、dup で写してから書き戻さないと、読みながら書き換えてしまう。
def swap_rows(work, left, right)
  work[[left, right], true] = work[[right, left], true].dup
end

# ピボットの行を 1 に正規化してから、ほかの行のピボット列を 0 にする。
def sweep(work, pivot)
  work[pivot, true] /= work[pivot, pivot]
  pivot_line = work[pivot, true].dup

  work.shape[0].times do |index|
    work[index, true] -= work[index, pivot] * pivot_line unless index == pivot
  end
end

# ピボットの列で絶対値が最大の行を、対角より下から選ぶ。
def pivot_row(work, pivot)
  pivot + work[pivot..-1, pivot].abs.max_index
end
```

最初は `eliminate` 1 つに全部を書いていましたが、RuboCop の `Metrics/AbcSize`（代入・呼び出し・条件の数から測る複雑さ）が 19.21 で上限の 17 を超えたので、`swap_rows` と `sweep` に分けました。上限は緩めません。

### 詰まりやすい点: Numo の添字はビューを返す

`work[pivot, true]` のような添字は、行列の **一部を指すビュー** を返します。複製ではありません。`dup` を 2 か所に付けているのはこのためです。

- `sweep` の `pivot_line` — ピボットの行をビューのまま持つと、ループの中でピボットの行を読みながら、ほかの行を書き換えることになります。ピボットの行自体は書き換えないので結果は変わりませんが、「読んでいるものが書き換わらない」ことを読み手が確かめなくて済むように複製しました
- `swap_rows` の右辺 — `work[[right, left], true]` はビューなので、そのまま `work[[left, right], true]` に代入すると、1 行目を書いた時点で 2 行目の読み出し元が変わってしまいます

`dup` を外すとどうなるかを、2×2 の行列で確かめました。

```ruby
w = Numo::DFloat[[1, 2], [3, 4]]
w[[0, 1], true] = w[[1, 0], true]
p w.to_a
```

```text
[[3.0, 4.0], [3.0, 4.0]]
```

入れ替わらず、2 行目が 1 行目に写っただけになりました。例外も警告もありません。

Rust 版では同じ場面で「可変で借りているあいだは読めない」と借用検査が止めてくれ、`to_owned()` で複製することを強いられました。Ruby と Numo では **何も止めてくれません**。ビューか複製かはドキュメントを読んで知り、テストで確かめるしかありません。

そこで、TODO リストに「元の行列と右辺を書き換えない」を足しました。

```ruby
def test_解いても元の行列と右辺は変わらない
  a = Numo::DFloat[[0, 1], [1, 0]]
  b = Numo::DFloat[2, 3]
  C.solve(a, b)

  assert_equal [[0.0, 1.0], [1.0, 0.0]], a.to_a
  assert_equal [2.0, 3.0], b.to_a
end
```

`Numo::DFloat.hstack` は新しい行列を作るので、`work` をどれだけ書き換えても `a` と `b` は変わりません。このテストは、将来だれかが「`hstack` を省いて `matrix` を直接掃き出そう」と書き換えたときに落ちます。

### 三角測量: ピボットの入れ替えと、解けない場合

```ruby
def test_先頭のピボットが零でも入れ替えて解ける
  w = C.solve(Numo::DFloat[[0, 1], [1, 0]], Numo::DFloat[2, 3])

  assert_in_delta 3.0, w[0], 1e-12
  assert_in_delta 2.0, w[1], 1e-12
end

def test_解が定まらなければ失敗する
  error = assert_raises(ArgumentError) { C.solve(Numo::DFloat[[1, 2], [2, 4]], Numo::DFloat[3, 6]) }

  assert_equal "解けない連立方程式です", error.message
end
```

2 行目が 1 行目の 2 倍になっている行列は、解が 1 つに定まりません。掃き出しの途中でピボットが 0 になるので、そこで `ArgumentError` を投げます。失敗の表し方は第 1 章からの方針どおり、標準の例外です。Rust 版は第 2 章の `Error` 列挙型に `?` で載せましたが、Ruby 版は投げるだけで呼び出し元まで伝わります。

件数の検査は、この章のあちこちで使うので `check_size` に切り出しました。

```ruby
# 件数が違えば ArgumentError を投げる。
def check_size(expected, actual)
  raise ArgumentError, "件数が違います: #{expected} と #{actual}" unless expected == actual
end
```

## 7.7 評価指標を計算する

回帰の良し悪しは、分類の正解率と同じようには測れません。3 つの指標を実装します。

- **MAE（平均絶対誤差）** — 誤差の絶対値の平均。単位が元の値と同じで読みやすい
- **RMSE（二乗平均平方根誤差）** — 誤差を 2 乗してから平均し、平方根を取る。大きな誤差を重く見る
- **R²（決定係数）** — 1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。1 に近いほどよい

テストから書きます。値の性質がそのままテストの名前になります。

```ruby
def test_平均絶対誤差は誤差の絶対値の平均になる
  assert_in_delta 1.5, C.mean_absolute_error([1.0, 2.0], [2.0, 4.0]), 1e-12
end

def test_完全に当たれば決定係数は一になる
  assert_in_delta 1.0, C.r2_score([1.0, 2.0, 3.0], [1.0, 2.0, 3.0]), 1e-12
end

def test_平均を答え続けると決定係数は零になる
  assert_in_delta 0.0, C.r2_score([1.0, 2.0, 3.0], [2.0, 2.0, 2.0]), 1e-12
end
```

「平均を答え続けると R² は 0 になる」は、R² の意味そのものです。**平均を答えるだけのモデルより、どれだけましか** を測る指標なので、それに並ぶと 0 になります。平均より悪ければ負になります（7.10 節で実物が出てきます）。

実装では、3 つの指標が共通して使う「残差」を切り出します。

```ruby
# 実測値と予測値の差を返す。件数が違えば ArgumentError を投げる。
def residuals(t, y)
  raise ArgumentError, "件数が違います: #{t.size} と #{y.size}" unless t.size == y.size

  t.zip(y).map { |actual, predicted| actual - predicted }
end

# 決定係数（R²）。1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。
def r2_score(t, y)
  residual = sum_of_squares(residuals(t, y))
  mean = t.sum / t.size

  1.0 - (residual / sum_of_squares(t.map { |value| value - mean }))
end
```

評価指標は Numo を使わず、Ruby の配列のまま計算します。`t.sum / t.size` は `t` が浮動小数点数の配列なので浮動小数点数の割り算になりますが、整数の配列を渡すと整数の割り算になって黙って切り捨てられます。この章の呼び出し元はいつも `Float` の配列を渡すので、ここでは型を検査せず、テストで `Float` を渡す使い方を固定しています。

## 7.8 正規方程式で線形回帰を学習する

### 計画行列を作る

特徴量の先頭に 1 の列を足します。

```ruby
# 先頭に 1 の列を足した特徴量の行列（計画行列）を作る。1 の列の係数が切片になる。
def design_matrix(x)
  Numo::DFloat.cast(x.map { |features| [1.0, *features.values] })
end
```

`Numo::DFloat.cast` は Ruby の配列の配列から行列を作ります。`[1.0, *features.values]` の `*` は配列の展開です。Rust 版は 1 本の `Vec` と形（行数・列数）から `Array2` を作りましたが、Ruby 版は「行の配列」をそのまま渡せます。行ごとに長さが違うと `cast` の段階で例外になります。

### 係数に列名を付ける

学習の結果は「切片」と「列名つきの係数」です。第 2 章の `Features` と同じく、**列名と値を同じ順の配列で持って対応させます**。

```ruby
# 学習した線形回帰のモデル。切片と、列名と同じ順に並んだ係数を持つ。
# Hash でも列の順は保てるが、第 2 章の Features と同じく列名と値を別々の配列で持つ。
LinearModel = Data.define(:intercept, :columns, :coefficients) do
  def initialize(intercept:, columns:, coefficients:)
    Chapter07.check_size(columns.size, coefficients.size)

    super
  end

  # 列名で係数を読む。列が無ければ KeyError を投げる。
  def coefficient(column)
    index = columns.index(column)
    raise KeyError, "列がありません: #{column}" if index.nil?

    coefficients[index]
  end

  # 1 行分の特徴量の予測値。係数は列名で対応させるので、特徴量の列の並び順は問わない。
  def predict_one(features)
    columns.zip(coefficients).sum(intercept) { |column, weight| weight * features.value(column) }
  end
```

`Data.define` のブロックで `initialize` を上書きし、`super` の前に件数を確かめます。数が合わない `LinearModel` は作れません。第 2 章の `Features` と同じ書き方です。

`sum(intercept) { ... }` は、`intercept` を初期値にしてブロックの値を足し合わせます。予測は列名で対応させるので、渡す特徴量の並び順は問いません。テストで確かめます。

```ruby
def test_列の並びが違っても同じ予測になる
  model = C::LinearModel.new(intercept: 1.0, columns: %w[a b], coefficients: [2.0, -1.0])

  assert_in_delta 2.0, model.predict_one(features(%w[b a], [1.0, 1.0])), 1e-9
end
```

### 学習

正規方程式をそのまま書き下します。

```ruby
# (Xᵀ X) w = Xᵀ t を解いて、切片と係数を求める。列名は先頭の行の特徴量から取る。
def fit(x, t)
  check_size(x.size, t.size)
  raise ArgumentError, "特徴量がありません" if x.empty?

  weights = normal_equation(design_matrix(x), Numo::DFloat.cast(t))

  LinearModel.new(intercept: weights[0], columns: x.first.columns, coefficients: weights[1..].to_a)
end

# 計画行列と実測値から、正規方程式 (Xᵀ X) w = Xᵀ t の解 w を求める。
def normal_equation(design, t)
  transposed = design.transpose

  solve(transposed.dot(design), transposed.dot(t))
end
```

`normal_equation` は、はじめ `fit` の中に書いていましたが、`Metrics/AbcSize` が 17.29 で上限を超えたので切り出しました。数式 1 行ぶんが 1 メソッドになり、名前が式の意味を説明するようになりました。

空のデータを先に弾いているのは、`x.first` が `nil` を返し、`nil.columns` の `NoMethodError` になるのを避けるためです。Rust 版では `x.first()` が `Option` を返すので、空の場合を書かないとコンパイルが通りませんでした。Ruby 版は書き忘れても動いてしまうので、テストで空の場合を固定します。

```ruby
def test_特徴量が無ければ学習できない
  error = assert_raises(ArgumentError) { C.fit([], []) }

  assert_equal "特徴量がありません", error.message
end
```

テストは、答えが分かっている直線と平面から始めます。

```ruby
def test_直線上の点から切片と係数を求める
  # y = 1 + 2x
  x = [0.0, 1.0, 2.0].map { |value| features(["SNS1"], [value]) }
  model = C.fit(x, [1.0, 3.0, 5.0])

  assert_in_delta 1.0, model.intercept, 1e-9
  assert_in_delta 2.0, model.coefficient("SNS1"), 1e-9
end

def test_二つの特徴量でも係数を求める
  # y = 3 + 2a - b
  x = [[0.0, 0.0], [1.0, 0.0], [0.0, 1.0], [1.0, 1.0]].map { |values| features(%w[a b], values) }
  model = C.fit(x, [3.0, 5.0, 2.0, 4.0])

  assert_in_delta 3.0, model.intercept, 1e-9
  assert_in_delta 2.0, model.coefficient("a"), 1e-9
  assert_in_delta(-1.0, model.coefficient("b"), 1e-9)
end
```

三角測量です。1 つの特徴量で通ったあと、2 つの特徴量で `y = 3 + 2a - b` を当てさせます。最後の行だけ括弧が付いているのは、`assert_in_delta -1.0, ...` と書くと、`-` が二項演算子か単項演算子かが紛らわしいため、`ruby -w` で「warning: ambiguous first argument; put parentheses or a space even after `-' operator」という警告が出るからです。

## 7.9 外れ値の除去・分割・補完をまとめる

### 外れ値を取り除く

```ruby
# SNS2 がこの値を超え、かつ興行収入が OUTLIER_SALES 未満の映画を外れ値とする。
OUTLIER_SNS2 = 1000.0
OUTLIER_SALES = 8500.0

module_function

# 欠損値を許さずに数値の列を読む。空欄なら ArgumentError を投げる。
def number(row, column)
  row.number(column) || raise(ArgumentError, "値が空欄です: #{column}")
end

# 外れ値かどうかを判定する。
def outlier?(row)
  number(row, "SNS2") > OUTLIER_SNS2 && number(row, TARGET) < OUTLIER_SALES
end

# 外れ値の行を除いた表を返す。元の表は変えない。
def remove_outliers(table)
  table.with(rows: table.rows.reject { |row| outlier?(row) })
end
```

第 2 章の `Row#number` は、空欄なら `nil` を返します。外れ値の判定に使う列が空欄のまま比べると、`nil > 1000.0` の `NoMethodError` になります。そこで `||` で `nil` のときだけ例外を投げる `number` をかぶせました。`raise` に括弧が要るのは、`||` の右辺に書くためです。

`table.with(rows: ...)` は、`Data` の一部の属性だけを差し替えた新しい値を作ります。元の `table` は変わりません。

境界の扱いをテストで固定します。「超える」「未満」なので、ちょうど 1000 や 8500 の行は残ります。

```ruby
def test_話題になったのに売れなかった映画を外れ値として除く
  cleaned = C::Cinema.remove_outliers(table([%w[1200 8000], %w[1200 9000], %w[800 8000]]))

  assert_equal(%w[9000 8000], cleaned.rows.map { |row| row.text("sales") })
end

def test_境界の値は外れ値にしない
  assert_equal 2, C::Cinema.remove_outliers(table([%w[1000 8000], %w[1200 8500]])).rows.size
end
```

学習データの実際の行はテストに書きません。架空の値で境界だけを確かめます。

### 前処理を 1 つのメソッドにする

第 2 章の `prepare_iris` と同じ形で、映画のデータ用の `prepare` を書きます。順番が大事です。**外れ値を除く → 分割する → 訓練データの平均値で両方を補完する**。テストデータの値を平均に混ぜると、本番では知り得ない情報が訓練に漏れます（リーク）。

```ruby
# 外れ値を除き、分割してから訓練データの平均値で両方の欠損値を補完する。
def prepare(csv_file, test_size:, seed:)
  rows = remove_outliers(Chapter02::Table.load(csv_file)).rows
  t = rows.map { |row| number(row, TARGET) }
  split = Chapter02.split_train_test(rows, t, test_size:, seed:)
  means = Chapter02.column_means(split.x_train, FEATURES)

  split.with(x_train: Chapter02.fill_missing(split.x_train, FEATURES, means),
             x_test: Chapter02.fill_missing(split.x_test, FEATURES, means))
end
```

第 2 章の `split_train_test`・`column_means`・`fill_missing` がそのまま使えます。第 2 章では正解ラベルが文字列（アヤメの種類）でしたが、ここでは `Float`（興行収入）です。Rust 版は `TrainTestSplit<X, T>` の型引数で両方を受けましたが、Ruby 版は型を書かないので、**何も変えずに** 使い回せます。その代わり、ラベルの型の取り違えは実行するまで分かりません。

## 7.10 Rumale に置き換える

### 素直に置き換えると

Rumale の `LinearModel::LinearRegression` で学習し、自作と同じ `LinearModel` に詰め替えます。

```ruby
model = Rumale::LinearModel::LinearRegression.new
model.fit(Numo::DFloat.cast(x.map(&:values)), Numo::DFloat.cast(t))
```

注意点は、**計画行列を渡さない** ことです。Rumale は切片を自分で扱う（`fit_bias: true` が既定）ので、1 の列を足すと「同じ列が 2 本ある」状態になります。切片は `bias_term`、係数は `weight_vec` で読めます。

小さなデータ（4 行・2 列）で自作と比べるテストを書いたところ、落ちました。自作の切片 3.06 に対し、Rumale は 3.059696531807006 を返しました。差は約 3e-4 です。

### 既定の solver は L-BFGS

既定のパラメータを表示してみます。

```ruby
p Rumale::LinearModel::LinearRegression.new.params
```

```text
{:fit_bias=>true, :bias_scale=>1.0, :max_iter=>1000, :tol=>0.0001, :verbose=>false, :solver=>"lbfgs"}
```

`solver` が `"lbfgs"` です。Rumale のソースを見ると、既定の `solver: 'auto'` は「`Numo::Linalg` が読み込まれていれば `'svd'`（特異値分解で厳密に解く）、そうでなければ `'lbfgs'`」を選びます。この章の環境には `Numo::Linalg` が無いので、**誤差の 2 乗の平均を反復法で小さくしていく L-BFGS** になります。そして反復を止める許容誤差 `tol` の既定は 1e-4 です。

`solver: "svd"` と明示すると、`params[:solver]` は `"svd"` と表示されます。ところが `fit` の中で `Numo::Linalg` が無いと分かると、**黙って L-BFGS に切り替えて** 学習します。結果は既定のときと 1 桁も違わない 3.059696531807006 でした。`solver: "sgd"` のように知らない名前を渡しても、例外にならず `"lbfgs"` になります。

### 実データでは切片がほぼ 0 のまま止まる

小さなデータなら 3e-4 の差で済みます。実データでは話が変わります。訓練データ 79 件で、自作と既定の Rumale を比べました。

| | 切片 | SNS1 | SNS2 | actor | original |
|---|---|---|---|---|---|
| 自作（正規方程式） | 6035.99 | 1.1733 | 0.3771 | 0.3097 | 272.5036 |
| Rumale（既定、`tol` 1e-4） | 0.0044 | 0.3293 | 0.0680 | 0.9772 | 0.0019 |

**切片が 6035.99 のはずが 0.0044** です。切片をほとんど動かさないまま、許容誤差の判定で反復が止まっています。代わりに actor の係数が 3 倍ほどに膨らみ、切片のぶんを肩代わりしようとしています。このモデルでテストデータを予測すると、**R² は -1.246** でした。平均を答え続けるより悪い予測です。

例外も警告もありません。`fit` は `self` を返し、`predict` はそれらしい数値を返します。型も引数の形も正しいので、どの検査にも引っかかりません。自作の正規方程式と突き合わせたから気づけました。

### 許容誤差を指定する

`tol` を小さくすると、厳密な解に近づきます。

```ruby
module RumaleRegression
  # 最適化を止める許容誤差。既定の 1e-4 では厳密な解の手前で止まる。
  TOLERANCE = 1e-10

  module_function

  # Rumale で学習し、自作と同じ LinearModel にして返す。
  # Rumale は切片を自分で足す（fit_bias: true）ので、計画行列ではなく特徴量をそのまま渡す。
  def fit(x, t)
    raise ArgumentError, "特徴量がありません" if x.empty?

    model = Rumale::LinearModel::LinearRegression.new(tol: TOLERANCE)
    model.fit(Numo::DFloat.cast(x.map(&:values)), Numo::DFloat.cast(t))

    LinearModel.new(intercept: model.bias_term, columns: x.first.columns, coefficients: model.weight_vec.to_a)
  end
end
```

`tol: 1e-10` にすると、実データでの差は切片で 3.5e-5、係数で最大 1.8e-5 に縮みました。それでも 0 にはならないので、突き合わせのテストの許容誤差は、小さなデータで 1e-5、実データで 1e-3 にしています。第 3 章の決定木は「同点のときの選び方」で自作と分かれましたが、線形回帰は解が一意に決まります。残る差は **反復法の止めどころ** から来るもので、許容誤差の大きさはそれに合わせて決めます。

既定のままでは厳密な解に届かないことも、テストに残しておきます。Rumale の版を上げて既定値が変わったら、このテストが教えてくれます。

```ruby
# 既定の tol（1e-4）のままだと、L-BFGS が厳密な解の手前で止まる
def test_Rumale_の既定の許容誤差では厳密な解に届かない
  x = Numo::DFloat[[0, 0], [1, 0], [0, 1], [1, 2]]
  model = Rumale::LinearModel::LinearRegression.new.fit(x, Numo::DFloat[3, 5, 2.2, 3.1])

  assert_equal "lbfgs", model.params[:solver]
  assert_operator (model.bias_term - 3.06).abs, :>, 1e-4
end
```

| | 既定のアルゴリズム | 自作との一致 |
|---|---|---|
| Python 版（scikit-learn） | 最小二乗の厳密解 | そのまま一致 |
| Rust 版（linfa-linear） | 最小二乗の厳密解 | 1e-6 で一致 |
| Ruby 版（Rumale、`Numo::Linalg` なし） | L-BFGS、`tol` 1e-4 | 既定では一致しない。`tol: 1e-10` で 1e-3 以内 |

## 7.11 実データで学習・評価する

### 結果を表示する

`run` で、件数・外れ値・係数・評価指標を表示します。

```ruby
# 映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。
def self.run(out = $stdout)
  csv_file = File.join(Dataset.dir, "cinema.csv")
  table = Chapter02::Table.load(csv_file)
  split = Cinema.prepare(csv_file, test_size: TEST_SIZE, seed: SEED)

  out.puts summary(table, split)
  out.puts report(fit(split.x_train, split.t_train), RumaleRegression.fit(split.x_train, split.t_train), split)
end
```

`out.puts` に配列を渡すと、要素を 1 行ずつ書き出します。`summary` と `report` は表示する行の配列を返すだけのメソッドにしたので、`run` は短いまま、`Metrics/AbcSize` の上限にも収まりました。

評価指標の行は、書式の指定子に名前を付けています。

```ruby
Kernel.format("テストデータの評価: R2=%<r2>.4f, MAE=%<mae>.2f, RMSE=%<rmse>.2f",
              r2: r2_score(split.t_test, y), mae: mean_absolute_error(split.t_test, y),
              rmse: root_mean_squared_error(split.t_test, y))
```

RuboCop の `Style/FormatStringToken` は、名前の無い指定子（`%.4f`）が 2 つ以上並ぶと咎めます。`%<r2>.4f` と名前を付けると、引数の順を取り違える誤りが起きなくなります。

実行します。

```bash
bundle exec rake 'run[chapter07]'
```

```text
データ件数: 100
外れ値を除いた件数: 99
訓練データ: 79 件, テストデータ: 20 件
切片: 6035.99
係数: SNS1=1.1733, SNS2=0.3771, actor=0.3097, original=272.5036
Rumale の切片: 6035.99, 係数: SNS1=1.1733, SNS2=0.3771, actor=0.3097, original=272.5036
テストデータの評価: R2=0.7140, MAE=339.21, RMSE=413.57
```

### 係数を読む

原作のある映画（`original` が 1）は、そうでない映画より興行収入が 273 ほど高い、と読めます。SNS1 は 1 単位あたり 1.17、SNS2 は 0.38。SNS1 のほうが興行収入への効き目が大きい指標です。

ただし、**係数の大きさをそのまま「重要さ」と読むことはできません**。特徴量の単位が違うからです。単位をそろえて比べる方法（標準化）は第 12 章で扱います。

### 評価指標を読む

R² が 0.7140 なので、興行収入のばらつきのうち 7 割ほどを説明できています。MAE は 339、RMSE は 414。RMSE が MAE より大きいのは、大きく外した映画が少数あることを意味します（RMSE は大きな誤差を重く見るため）。

### ほかの言語版と数値が一致しない

Rust 版の第 7 章も同じデータ・同じテストデータの割合 0.2・同じシード 0 ですが、係数も評価指標も一致しません（Rust 版は R²=0.8068）。**訓練データとテストデータの分け方が違う** からです。第 2 章で見たとおり、Ruby の `Random.new(0)` は Rust の `StdRng` とも Java の `java.util.Random` とも別の乱数列を作ります。件数（79 件と 20 件）だけは一致します。

同じことを確かめるには、数値そのものではなく **関係** をテストします。

```ruby
def test_自作とRumaleの係数は実データでも一致する
  data = split
  ours = C.fit(data.x_train, data.t_train)
  theirs = C::RumaleRegression.fit(data.x_train, data.t_train)

  assert_in_delta ours.intercept, theirs.intercept, 1e-3
  C::Cinema::FEATURES.each do |column|
    assert_in_delta ours.coefficient(column), theirs.coefficient(column), 1e-3, column
  end
end
```

「自作と Rumale が一致すること」は言語をまたいでも成り立つ性質です。いっぽう「R² が 0.7140 であること」は、この言語・このシードでの実測値です。後者も固定しますが、ほかの言語版と比べるものではないとコメントに書いておきます。

実データのテストは、第 1 章と同じく **データが無ければ `skip`** します。

```ruby
def csv_file
  path = File.join(GettingStartedMl::Dataset.dir, "cinema.csv")
  skip "学習データ cinema.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

  path
end
```

## 7.12 品質チェック

```bash
bundle exec rake check
```

この章の実装を終えた時点で、学習データがある状態では 80 runs・142 assertions・0 skips、行カバレッジは 344 / 362 行（95.02%）でした。学習データを外す（`ML_DATA_DIR=/nonexist bundle exec rake test`）と 80 runs・112 assertions・13 skips、305 / 362 行（84.25%）です。

| 学習データ | runs | assertions | skips | 行カバレッジ |
|-----------|------|-----------|-------|------------|
| あり | 80 | 142 | 0 | 95.02% |
| なし | 80 | 112 | 13 | 84.25% |

この章で RuboCop に言われたことは次のとおりです。どれも上限を緩めず、書き方を変えて直しました。

| ルール | 場所 | 直し方 |
|------|------|------|
| `Metrics/AbcSize` | `eliminate`（19.21）・`fit`（17.29）・`run`（24.19） | `swap_rows`・`sweep`・`normal_equation`・`summary` に分けた |
| `Naming/MethodParameterName` | `solve(a, b)` | `matrix`・`rhs` に改名 |
| `Style/FormatStringToken` | 評価指標の書式 | `%<r2>.4f` のように名前を付けた |
| `Metrics/ClassLength` | テストクラス（127 行） | 連立方程式・評価指標のテストと、回帰・外れ値・Rumale のテストの 2 ファイルに分けた |

## 7.13 まとめ

この章では、正規方程式による線形回帰を TDD で実装し、実データで R²=0.7140 を得ました。Ruby に固有の論点は次のとおりです。

1. **行列はライブラリ、解く手続きは自作** — numo-narray-alt に積と転置はあるが、連立方程式を解くメソッドは無い。`Numo::Linalg` は Rumale の依存に入らないので、掃き出し法を自作する
2. **ビューか複製かは誰も検査しない** — Numo の添字はビューを返す。Rust 版の借用検査のような歯止めが無いので、`dup` を書き、「元の行列を書き換えない」ことをテストで固定する
3. **ライブラリの既定値が黙って結果を変える** — Rumale の線形回帰は `Numo::Linalg` が無いと L-BFGS（`tol` 1e-4）で解き、実データでは切片 0.0044、R² -1.246 のモデルを例外も警告も無く返す。`solver: "svd"` を指定しても黙って L-BFGS に切り替わる。自作との突き合わせで初めて気づけた
4. **型を書かないので使い回せる、だから取り違えは実行時まで分からない** — 第 2 章の分割・補完を、ラベルが文字列でも `Float` でもそのまま使える
5. **RuboCop の複雑さの上限が設計を促す** — `Metrics/AbcSize` に従ってメソッドを分けると、`normal_equation` のように数式に名前が付く

**TODO リスト（この章の完了時点）**:

- [x] 連立方程式を解く
- [x] 評価指標を計算する（MAE・RMSE・R²）
- [x] 正規方程式で線形回帰を学習する
- [x] 学習したモデルで予測する
- [x] 外れ値を取り除く
- [x] 外れ値の除去・分割・補完をまとめる
- [x] Rumale と結果が一致することを確かめる
- [x] 実データで学習・評価して表示する

次の章では、分類に戻ってタイタニックの生存予測に取り組みます。欠損値の補完とダミー変数化を **前処理のパイプライン** にまとめ、学習済みのモデルをファイルに保存します。Rumale にどこまで前処理があるかを確かめ、無いものを自作します。
