---
type: Article
title: "第 11 章: 評価指標と交差検証"
description: "混同行列・適合率・再現率・F 値・ROC 曲線・AUC と K 分割交差検証を Elixir の TDD で自作し、評価関数もモデルも「ただの関数」で表して Scholar.Metrics と Scholar.ModelSelection と数値を突き合わせる。"
tags: [article,getting-start-ml,elixir]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:00:00Z }
---

# 第 11 章: 評価指標と交差検証

## 11.1 はじめに

第 3 章から第 10 章まで、モデルの良し悪しは **正解率** で測ってきました。この章では、その測り方そのものを作り直します。

- **正解率だけでは足りない** — 見つけたいものが少ないデータでは、正解率が高くても役に立たないモデルができます。混同行列を数え、適合率・再現率・F 値で見ます
- **閾値を動かして見る** — 「正例らしさ」の連続値がある分類器では、どこで切るかで適合率と再現率が入れ替わります。**ROC 曲線** と **AUC** で、閾値によらない性能を見ます
- **1 回の分け方に頼らない** — 第 2 章の `split_train_test/4` は 1 回きりの分割です。**K 分割交差検証** は、分け方を入れ替えて何度も測り、平均を取ります

この章では、評価関数も分類器も **ただの関数** で表します。Java 版は `Metric<T>` という関数型インターフェースを、Scala 版は `type Metric[T]` という型の別名を用意しました。**Elixir 版はどちらも書きません。** 評価関数は「正解と予測を受け取って 1 つの数を返す関数」というだけで、ビヘイビアもプロトコルも要りません。

[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と同じ TODO リストで進め、[Clojure 版](../clojure/11-evaluation-metrics-and-cross-validation.md)・[Java 版](../java/11-evaluation-metrics-and-cross-validation.md)・[Ruby 版](../ruby/11-evaluation-metrics-and-cross-validation.md) と対比します。注目してほしいのは次の 3 点です。

- **`:fn` はキーには書けても、`cm.fn` とは書けない。** 混同行列の 4 つの数は `%{tp: .., fp: .., fn: .., tn: ..}` というマップにしましたが、`fn` は無名関数の予約語なので、ドット記法では読めません。パターンマッチで名前を変えて受けます
- **遅延の細かさが Clojure と違う。** Clojure 版は `map` がベクタを **32 件ずつまとめて** 実現するので「1 件取り出せば 1 回だけ学習する」が成り立ちませんでした。Elixir の `Stream.map/2` は 1 件ずつなので、成り立ちます。テストで固定します
- **Scholar の分類の指標は f64 を渡しても f32 で返る。** 第 7 章で「テンソルは `type: :f64` を明示する」と決めたのに、`Scholar.Metrics.Classification` にはそれが効きません。回帰の指標は f64 のままです。**指標の種類で精度が変わる** ことを実測して確かめます

データは第 8 章の Survived.csv（タイタニックの生存）と、第 7 章の cinema.csv（映画の興行収入）を使います。

## 11.2 正解率だけでは足りない理由

100 人のうち 5 人だけが病気の検査を考えます。「全員が健康」と答えるモデルの正解率は 0.95 です。数字は高いのに、病気の人を 1 人も見つけていません。

見つけたいほう（この例では病気）を **正例** と決めると、予測の当たり外れは 4 つに分かれます。

| | 正例と予測 | 負例と予測 |
|---|---|---|
| **実際に正例** | 真陽性（tp） | 偽陰性（fn） |
| **実際に負例** | 偽陽性（fp） | 真陰性（tn） |

この 4 つの数が **混同行列** です。ここから 3 つの指標を作ります。

| 指標 | 式 | 読み方 |
|---|---|---|
| 適合率（precision） | tp / (tp + fp) | 正例と予測したうち、本当に正例だった割合 |
| 再現率（recall） | tp / (tp + fn) | 本当の正例のうち、正例と予測できた割合 |
| F 値（F1） | 2pr / (p + r) | 適合率と再現率の調和平均 |

「全員が健康」のモデルは、tp が 0 なので適合率も再現率も 0 です。正解率 0.95 が隠していたことが見えます。

## 11.3 TODO リストの作成

**TODO リスト**:

- [ ] 混同行列を数える
  - [ ] 正解と予測を 4 つに分ける
  - [ ] 正例の決め方を変えると数え方も変わる
  - [ ] 件数が違えば失敗する
- [ ] 適合率・再現率・F 値を求める
  - [ ] 正例を 1 件も予測しなければ 0 にする（NaN にしない）
- [ ] 正解率と平均二乗誤差を求める
- [ ] 混同行列の指標を評価関数に変える
- [ ] ROC 曲線と AUC を求める
  - [ ] 同じスコアは 1 つの点にまとめる
  - [ ] 正例か負例の片方しか無ければ失敗する
- [ ] K 分割交差検証
  - [ ] テストデータは重ならず全体をおおう
  - [ ] 割り切れないときは余りを先頭の分割から 1 件ずつ配る
  - [ ] 同じシードなら同じ分け方になる
- [ ] 交差検証で分割ごとに学習して採点する
  - [ ] 評価関数を差し替えられる
  - [ ] 取り出すまで学習しない
  - [ ] 回帰でも同じ関数で評価できる
- [ ] Scholar の評価指標・`ModelSelection` と突き合わせる
- [ ] 実データ（Survived・cinema）で評価する

## 11.4 混同行列を数える

### Red: 最初のテスト

混同行列は 4 つの数です。Elixir では、そのまま 4 つのキーを持つマップにします。

```elixir
  describe "混同行列" do
    test "正解と予測を四つに数える" do
      assert C.confusion_matrix(
               ["1", "1", "1", "0", "0", "0"],
               ["1", "1", "0", "1", "0", "0"],
               "1"
             ) == %{tp: 2, fp: 1, fn: 1, tn: 2}
    end

    test "正例の決め方を変えると数え方も変わる" do
      actual = ["1", "1", "1", "0", "0", "0"]
      predicted = ["1", "0", "0", "1", "0", "0"]

      assert C.confusion_matrix(actual, predicted, "1") == %{tp: 1, fp: 1, fn: 2, tn: 2}
      assert C.confusion_matrix(actual, predicted, "0") == %{tp: 2, fp: 2, fn: 1, tn: 1}
    end

    test "三値以上でも正例以外はまとめて負例になる" do
      assert C.confusion_matrix(["a", "a", "b", "c"], ["a", "b", "a", "c"], "a") ==
               %{tp: 1, fp: 1, fn: 1, tn: 1}
    end

    test "件数が違えば失敗する" do
      assert_raise ArgumentError, fn -> C.confusion_matrix(["1"], ["1", "0"], "1") end
    end
  end
```

Java 版は `record ConfusionMatrix(int tp, int fp, int fn, int tn)` を定義し、Scala 版は `case class` にしました。**Elixir では定義するものがありません。** 素のマップなので、`==` で期待値とそのまま比べられ、`%{tp: 2, …}` というリテラルがそのままテストの読み仕様になります。

`%{fn: 1}` と書けることに注目してください。`fn` は無名関数を作る予約語ですが、**マップのキーの位置ではアトムのリテラル** として読まれるので問題ありません。読むときだけ気をつけます（11.5 節）。

### Green: 場合を並べてから数える

Java 版・Scala 版は、4 つのカウンタを 1 件ずつ増やしていきます。Elixir では「1 件ずつ 4 つのどれかに分類してから、まとめて数える」と書けます。

```elixir
  @doc "正解と予測を 1 件ずつ比べて数える。positive と等しいラベルを正例、それ以外を負例とする。"
  def confusion_matrix(actual, predicted, positive) do
    require_same_size(actual, predicted)

    counts =
      actual
      |> Enum.zip(predicted)
      |> Enum.map(fn {a, p} -> cell(a == positive, p == positive) end)
      |> Enum.frequencies()

    Map.merge(%{tp: 0, fp: 0, fn: 0, tn: 0}, counts)
  end
```

振り分けは、真偽値 2 つを取る 4 つの関数節にしました。

```elixir
  defp cell(true, true), do: :tp
  defp cell(false, true), do: :fp
  defp cell(true, false), do: :fn
  defp cell(false, false), do: :tn
```

- **4 つの場合が形として並びます。** Clojure 版はベクタに対する `case` で、Scala 版はタプルのパターンマッチで同じ形を作りました。Elixir は関数節がその役です。`if` のはしごより、抜けに気づきやすい書き方です
- `Enum.frequencies/1` は「どのアトムが何回現れたか」を数えます。1 件も現れなかったキーは入らないので、`Map.merge/2` で 0 の入った土台に重ねます。これをしないと、`cm.tp` が `KeyError` で落ちます
- `Enum.zip/2` は短いほうで止まるので、**件数の検査を先に書くことが必須** です

### 件数が違うときは黙って切り詰めない

```elixir
  @doc "正解と予測の件数が同じでなければ失敗する。短いほうに合わせて黙って切り詰めない。"
  def require_same_size(actual, predicted) do
    if length(actual) != length(predicted) do
      raise ArgumentError,
            "正解と予測の件数が違います（正解 #{length(actual)} 件、予測 #{length(predicted)} 件）"
    end

    :ok
  end
```

Elixir の `Enum.zip/2`・`Enum.zip_with/3` は、長さが違うコレクションを渡されても例外を投げず、短いほうに合わせます。便利ですが、評価指標では「予測が 1 件足りない」ことが黙って通ると、スコアが少しだけ良く見えます。**言語の寛容さを、そのままドメインの寛容さにしない** ために、入口で止めます。

`:ok` を返しているのは、`if` の偽の枝が `nil` を返すのを避けて、この関数が「検査だけをする」ことを戻り値でも示すためです。

## 11.5 適合率・再現率・F 値

### 明白な実装

式が決まっているので、そのまま書きます。

```elixir
  @doc "分母が 0 なら 0 を返す割り算。NaN にしない。"
  def ratio(_numerator, 0), do: 0.0
  def ratio(_numerator, +0.0), do: 0.0
  def ratio(numerator, denominator), do: numerator / denominator

  @doc "適合率。正例と予測したうち、本当に正例だった割合。"
  def precision(%{tp: tp, fp: fp}), do: ratio(tp, tp + fp)

  @doc "再現率。本当の正例のうち、正例と予測できた割合。"
  def recall(%{tp: tp, fn: misses}), do: ratio(tp, tp + misses)

  @doc "F 値。適合率と再現率の調和平均。"
  def f1_score(cm) do
    p = precision(cm)
    r = recall(cm)
    ratio(2 * p * r, p + r)
  end
```

### `:fn` はキーには書けるが、束縛名にはできない

`recall/1` の引数のパターンに注目してください。

```elixir
  def recall(%{tp: tp, fn: misses}), do: ratio(tp, tp + misses)
```

`%{tp: tp, fn: fn}` とは書けません。左の `fn:` はキーなのでよいのですが、右の `fn` は変数名の位置に予約語が来るので、`invalid alias` というコンパイルエラーになります。`cm.fn` も同じ理由で書けません。そこで **受ける側で `misses` と名前を変えました**。

Clojure 版は `{:keys [tp fn]}` が `fn` マクロを隠すという別の危険に出会い、同じように `{tp :tp misses :fn}` と受けています。データのキーとしては自然な名前でも、**言語の予約語と重なるところだけ、読み出す側が名前を変える** という同じ落とし所になりました。

この章にはもう 1 か所、同じ形の注意があります。第 8 章で決めた「大文字始まりのキーは `row.Sex` と書けない」という制約です。`prepare_survived/1` では `Chapter02.text(row, :Sex)` を使っていて、これは中で `Map.fetch/2` を呼んでいます。**予約語も大文字始まりも、マップのキーとしては書けるがドット記法では読めない** という、同じ規則の別の顔です。

### 分母が 0 になる場合

正例を 1 件も予測しなければ `tp + fp` が 0 になり、素直に割ると `ArithmeticError` になります。`ratio/2` の 1 つ目と 2 つ目の節がそれを受け止めます。

```elixir
    test "正例を一件も予測しなければ零になり NaN にならない" do
      cm = %{tp: 0, fp: 0, fn: 2, tn: 2}

      assert [C.precision(cm), C.recall(cm), C.f1_score(cm)] == [0.0, 0.0, 0.0]
    end
```

節が 2 つあるのは、分母が整数の 0 で来る場合（適合率・再現率・正解率）と、浮動小数の `+0.0` で来る場合（F 値の `p + r`）の両方があるからです。Elixir 1.18 では `0.0` をパターンに書くと「`+0.0` と書け」という警告が出ます。**`-0.0` と区別するため** で、素直に従いました。

## 11.6 正解率と平均二乗誤差

混同行列を通さずに、正解と予測から直接求める指標も 2 つ作ります。

```elixir
  @doc "正解率。正解と予測が一致した割合。"
  def accuracy(actual, predicted) do
    require_same_size(actual, predicted)
    ratio(Enum.count(Enum.zip(actual, predicted), fn {a, p} -> a == p end), length(actual))
  end

  @doc "平均二乗誤差（MSE）。誤差の 2 乗の平均。"
  def mean_squared_error(actual, predicted) do
    require_same_size(actual, predicted)

    actual
    |> Enum.zip_with(predicted, fn a, p -> (p - a) * (p - a) end)
    |> Enum.sum()
    |> Kernel./(length(actual))
  end
```

`|> Kernel./(length(actual))` は、パイプラインの最後で割るための書き方です。`/` は演算子ですが `Kernel./` という関数でもあるので、パイプの右に置けます。読みづらければ `Enum.sum(...) / length(actual)` と書けますが、**この章の関数はどれも「正解と予測を受け取って 1 つの数を返す」形** なので、上から下に 1 本で流れる形をそろえました。

### 学習用テスト: 外れた予測への敏感さ

MSE は 2 乗するので、大きく外れた予測に敏感です。MAE（第 7 章）と比べて性質を固定します。

```elixir
    test "平均二乗誤差は外れた予測に敏感で MAE より大きく増える" do
      t = [0.0, 0.0, 0.0, 0.0]
      spread = [1.0, 1.0, 1.0, 1.0]
      concentrated = [0.0, 0.0, 0.0, 4.0]

      # MAE はどちらも 1.0 で同じだが、MSE は外れ値のあるほうが大きい
      assert_in_delta Chapter07.mean_absolute_error(t, spread),
                      Chapter07.mean_absolute_error(t, concentrated),
                      1.0e-12

      assert C.mean_squared_error(t, spread) < C.mean_squared_error(t, concentrated)
    end
```

誤差の合計はどちらも 4 で同じなのに、MSE は 1.0 と 4.0 で 4 倍違います。11.10 節で cinema の RMSE と MAE を並べるとき、この性質がそのまま効いてきます。

第 7 章の RMSE との関係も、テストで結んでおきます。

```elixir
    test "平均二乗誤差は第 7 章の RMSE の二乗と一致する" do
      t = [1.0, 2.0, 3.0, 4.0]
      y = [1.5, 2.0, 3.0, 4.5]
      rmse = Chapter07.root_mean_squared_error(t, y)

      assert_in_delta C.mean_squared_error(t, y), rmse * rmse, 1.0e-12
    end
```

## 11.7 ROC 曲線と AUC

### 閾値を動かすとどうなるか

「正例らしさ」を 0 から 1 の数で返す分類器では、どこで切るかを自分で決められます。閾値を下げれば再現率は上がり、適合率は下がります。閾値を 1 つ決めて混同行列を数えるのは、**曲線の上の 1 点しか見ていない** ということです。

ROC 曲線は、閾値を高いほうから下げていったときの「偽陽性率（横軸）」と「真陽性率（縦軸）」の軌跡です。左上の角に近いほど良く、**曲線の下の面積（AUC）** が閾値によらない性能の目安になります。でたらめな分類器の AUC は 0.5 で、完全に分けられれば 1.0 です。

### 曲線の 1 点もただのマップ

```elixir
  def roc_curve(scores, labels) do
    require_same_size(scores, labels)
    positives = Enum.count(labels, & &1)
    negatives = length(labels) - positives

    if positives == 0 or negatives == 0 do
      raise ArgumentError, "正例と負例が両方ないと ROC 曲線を描けません"
    end

    [roc_point(:infinity, 0.0, 0.0) | roc_points(scores, labels, positives, negatives)]
  end
```

正例か負例の片方しか無いと、割り算の分母が 0 になって曲線が定義できません。`ratio/2` で 0 にごまかさず、**失敗させます**。適合率と違い、「正例が 1 件も無いデータの ROC」には意味のある値がないからです。同じ「分母が 0」でも、返すべきものがあるかどうかで扱いを変えました。

先頭には「どれも正例と予測しない」点（原点）を必ず置きます。台形則で面積を求めるとき、原点からの 1 本目の台形が抜けるのを防ぐためです。閾値は `:infinity` というアトムにしました。Elixir には `Float.max_finite/0` はあっても無限大のリテラルが無いので、**「どんなスコアより大きい」ことをアトムで表す** のがいちばん率直です。

### 同じスコアは 1 つの点にまとめる

```elixir
  # スコアの高いほうから、同じスコアのかたまりごとに正例と負例の数を足し込む。
  defp roc_points(scores, labels, positives, negatives) do
    scores
    |> Enum.zip(labels)
    |> Enum.group_by(&elem(&1, 0), &elem(&1, 1))
    |> Enum.sort_by(&elem(&1, 0), :desc)
    |> Enum.scan({nil, 0, 0}, fn {threshold, group}, {_, tp, fp} ->
      {threshold, tp + Enum.count(group, & &1), fp + Enum.count(group, &(not &1))}
    end)
    |> Enum.map(fn {threshold, tp, fp} ->
      roc_point(threshold, fp / negatives, tp / positives)
    end)
  end
```

`Enum.scan/3` は、畳み込みの途中経過を全部並べて返します。Ruby 版は `true_positive` と `false_positive` を書き換わる変数で持ち、`map` の中で足し込んでいました。**Elixir には書き換わる変数が無い** ので、累積を返す関数がそのまま必要な形になります。`Enum.reduce/3` だと最後の値しか残らず、`Enum.map_reduce/3` だと戻り値の形が二重になるので、`scan` がいちばん短く書けます。

同じスコアを `Enum.group_by/3` でまとめるのは、**分類器が同点を付けた事例のあいだに順序を持ち込まない** ためです。まとめないと、並べ替えの偶然で AUC が変わります。

```elixir
    test "同じスコアは一つの点にまとまる" do
      curve = C.roc_curve([0.5, 0.5, 0.5, 0.5], [true, true, false, false])

      assert length(curve) == 2
      assert C.auc(curve) == 0.5
    end
```

4 件すべてが同じスコアなら、点は原点と右上の 2 つだけになり、AUC はちょうど 0.5（でたらめと同じ）になります。

### 台形則で面積を求める

```elixir
  @doc "ROC 曲線の下の面積（AUC）を台形則で求める。"
  def auc(curve) do
    curve
    |> Enum.chunk_every(2, 1, :discard)
    |> Enum.map(fn [left, right] ->
      (right.false_positive_rate - left.false_positive_rate) *
        (left.true_positive_rate + right.true_positive_rate) / 2.0
    end)
    |> Enum.sum()
  end
```

`Enum.chunk_every(2, 1, :discard)` が、Ruby の `each_cons(2)`・Clojure の `partition 2 1` にあたります。第 3 引数の `:discard` が無いと、最後に 1 件だけの半端なかたまりが残ります。**既定が「残す」なので、明示しないと最後の点で落ちます。**

## 11.8 K 分割交差検証

### なぜ分け方を入れ替えるのか

1 回の分割で出たスコアは、その分け方に依存します。たまたま難しい行がテストデータに集まれば低く、簡単な行が集まれば高く出ます。K 分割交差検証は、データを K 個に分け、**それぞれを 1 回ずつテストデータにして K 回測り、平均を取ります**。全部の行が必ず 1 回はテストされます。

### 分け方もただのマップ

```elixir
  def k_fold(n_samples, n_splits, seed) do
    check_splits(n_samples, n_splits)
    folds(Random.shuffle(Enum.to_list(0..(n_samples - 1)), seed), n_splits)
  end

  @doc "並べ替えずに、先頭から順に `n_splits` 個のかたまりに分ける。余りの配り方は `k_fold/3` と同じ。"
  def k_fold_sequential(n_samples, n_splits) do
    check_splits(n_samples, n_splits)
    folds(Enum.to_list(0..(n_samples - 1)), n_splits)
  end
```

分け方は `%{train: [位置], test: [位置]}` です。Java 版は `record Fold`、Scala 版は `case class Fold` を定義しました。ここでもマップのままにします。

```elixir
  # 並べた位置を n_splits 個のかたまりに分け、かたまりごとに 1 つをテストデータ、残りを訓練データにする。
  defp folds(positions, n_splits) do
    total = length(positions)

    sizes =
      Enum.map(0..(n_splits - 1), fn index ->
        div(total, n_splits) + if(index < rem(total, n_splits), do: 1, else: 0)
      end)

    {tests, []} =
      Enum.map_reduce(sizes, positions, fn size, rest -> Enum.split(rest, size) end)

    Enum.map(tests, fn test -> %{train: positions -- test, test: test} end)
  end
```

`Enum.map_reduce/3` の戻り値を `{tests, []}` というパターンで受けているのがポイントです。**余りが 1 件でも出たらここで `MatchError` になります。** 「全部の行をどこかの分割に配り切った」という不変条件を、コメントではなくパターンで書きました（この不変条件は 11.9 節で Scholar と比べるときに効いてきます）。

`positions -- test` はリストの差です。件数が数百なら十分速く、何より「訓練データはテストデータの残り」という定義がそのまま式になります。

### 分け方の性質をテストで固定する

```elixir
    test "テストデータは重ならず全体をおおう" do
      folds = C.k_fold(10, 5, 0)
      tests = Enum.flat_map(folds, & &1.test)

      assert Enum.sort(tests) == Enum.to_list(0..9)
      assert length(tests) == 10
    end

    test "割り切れないときは余りを先頭の分割から一件ずつ配る" do
      assert Enum.map(C.k_fold(11, 4, 0), &length(&1.test)) == [3, 3, 3, 2]
      assert Enum.map(C.k_fold_sequential(7, 2), &length(&1.test)) == [4, 3]
    end

    test "同じシードなら同じ分け方になる" do
      assert C.k_fold(20, 4, 7) == C.k_fold(20, 4, 7)
      refute C.k_fold(20, 4, 7) == C.k_fold(20, 4, 8)
    end
```

並べ替えは第 2 章の `GettingStartedMl.Random.shuffle/2`（`java.util.Random` と同じ線形合同法）を使います。これで、Java 版・Scala 版・Clojure 版と **行の割り当てまで一致** します。11.10 節の 6 つの数値がそろうのは、このおかげです。

## 11.9 評価関数もモデルも、ただの関数

### 交差検証の手順を 1 つの関数にする

```elixir
  def cross_validate(trainer, x, t, folds, metric) do
    Stream.map(folds, fn %{train: train, test: test} ->
      predict = trainer.(pick(x, train), pick(t, train))
      metric.(pick(t, test), predict.(pick(x, test)))
    end)
  end
```

`trainer` は「訓練データを受け取って、予測する関数を返す関数」です。`metric` は「正解と予測を受け取って 1 つの数を返す関数」です。どちらも `.()` で呼ぶ無名関数なので、**型も名前も定義していません**。

Ruby 版は `make_model` という「呼ぶたびに新しいモデルを返す lambda」を渡していました。前の分割で学習した重みが残らないようにするためです。**Elixir にはそもそも状態を持つモデルがありません。** `trainer.(x, t)` が毎回まっさらな関数を返すので、この心配が構造的に消えています。

```elixir
  @doc "行の位置で値を選ぶ。"
  def pick(values, positions) do
    array = List.to_tuple(values)
    Enum.map(positions, &elem(array, &1))
  end
```

`Enum.at/2` をそのまま使うと、位置ごとにリストを先頭からたどるので O(n²) になります。一度タプルにしてから `elem/2` で引くと O(n) です。**不変のデータでも、添字で引きたいならタプルに移す** のが Elixir の定石です。

### 分類器を作る

```elixir
  @doc "第 3 章の決定木の分類器。"
  def tree_trainer(columns, max_depth) do
    fn x, t ->
      tree = Chapter03.fit(x, t, columns, max_depth)
      fn features -> Chapter03.predict(tree, features) end
    end
  end

  @doc "第 7 章の線形回帰の分類器（回帰なので予測は数値）。"
  def linear_trainer(columns) do
    fn x, t ->
      model = Chapter07.fit(x, t, columns)
      fn features -> Chapter07.predict(model, features) end
    end
  end
```

Java 版は `interface Model<T>` と、決定木・線形回帰のアダプター 2 つを書きました。Elixir はどちらも 5 行の関数です。**第 3 章と第 7 章に 1 行も手を入れていません。** 既存の `fit`/`predict` を関数で包むだけで、交差検証に載ります。

### 三角測量: 評価関数を差し替える、回帰も同じ関数で

```elixir
    test "評価関数を差し替えられる", %{x: x, t: t, folds: folds} do
      trainer = C.tree_trainer(columns(), 1)
      metric = C.classification_metric(&C.precision/1, "1")

      assert length(Enum.to_list(C.cross_validate(trainer, x, t, folds, metric))) == 4
    end

    test "回帰でも同じ関数で評価できる" do
      x = features(1..12)
      t = Enum.map(1..12, &(&1 * 2.0 + 1.0))
      folds = C.k_fold(12, 3, 0)

      scores =
        C.linear_trainer(columns())
        |> C.cross_validate(x, t, folds, &C.mean_squared_error/2)
        |> Enum.to_list()

      assert length(scores) == 3
      assert Enum.all?(scores, &(&1 < 1.0e-12))
    end
```

分類（ラベルの文字列）と回帰（数値）を、同じ `cross_validate/5` で採点できました。型で守っていないので、**分類のモデルに回帰の指標を渡す取り違えは実行するまで分かりません**。引き換えに、アダプターも型引数も 1 行も書いていません。

混同行列の指標を評価関数に変えるのが `classification_metric/2` です。

```elixir
  @doc "混同行列から求める指標を、正例を決めて、正解と予測から採点する評価関数に変える。"
  def classification_metric(score, positive) do
    fn actual, predicted -> score.(confusion_matrix(actual, predicted, positive)) end
  end
```

`&C.precision/1` のように関数捕捉で渡します。Ruby 版は `public_send(:precision)` とメソッド名のシンボルで動的に呼んでいました。Elixir では関数そのものを値として渡せるので、**名前を文字列やアトムに落とさずに済みます**。綴りを間違えれば、実行時ではなくコンパイル時に「そんな関数は無い」と言われます。

### 遅延の細かさは言語によって違う

Clojure 版は、ここで「1 件だけ取り出したつもりが 4 回学習されていた」という落とし穴に出会いました。`map` がベクタを 32 件ずつまとめて実現するからです。Elixir の `Stream.map/2` はどうか、テストで確かめます。

```elixir
    test "取り出すまで学習しない", %{x: x, t: t, folds: folds} do
      owner = self()

      trainer = fn train_x, train_t ->
        send(owner, :trained)
        C.tree_trainer(columns(), 1).(train_x, train_t)
      end

      scores = C.cross_validate(trainer, x, t, folds, &C.accuracy/2)
      refute_received :trained

      # ストリームから 1 つ取り出すと 1 回だけ学習する（Clojure の遅延シーケンスと違って
      # 32 件ずつまとめて実現することはない）
      assert [_first] = Enum.take(scores, 1)
      assert_received :trained
      refute_received :trained
    end
```

副作用の回数を数えるのに、カウンタではなく **プロセスのメッセージ** を使いました。`send(owner, :trained)` でテストプロセスに送り、`assert_received`/`refute_received` でメールボックスを 1 通ずつ確かめます。Elixir に書き換わる変数が無いので、「何回呼ばれたか」を素直に数える手段がこれになります。`async: true` のテストでも、メールボックスはプロセスごとに分かれているので混ざりません。

結果は期待どおりで、**Elixir のストリームは 1 件ずつ実現します**。`Enum.take(scores, 1)` で学習は 1 回だけです。Clojure 版がチャンク化の癖をテストに書いて固定したのと、目的は同じです。**「遅延だから 1 件」と思い込まず、言語ごとに実測して仕様として書く** ことが要ります。

### 指標ごとの平均を、順を保ったまま返す

```elixir
  @doc "Survived の評価指標。表示する順に並べる。マップはキーの順を保たないのでリストで持つ。"
  def survived_metrics do
    [
      {"正解率", &accuracy/2},
      {"適合率", classification_metric(&precision/1, @survived)},
      {"再現率", classification_metric(&recall/1, @survived)},
      {"F値", classification_metric(&f1_score/1, @survived)}
    ]
  end

  @doc "同じ分割で、評価指標ごとに交差検証のスコアの平均を求める。名前と平均の組を、指標の順に返す。"
  def evaluate(trainer, %{x: x, t: t}, metrics) do
    folds = k_fold(length(x), @n_splits, @seed)

    Enum.map(metrics, fn {name, metric} ->
      {name, mean(cross_validate(trainer, x, t, folds, metric))}
    end)
  end
```

Java 版は `LinkedHashMap` で指標の順を保ちました。Elixir のマップは **32 要素まではキーの順に並ぶように見えますが、それは実装の都合で、保証されていません**。第 7 章の係数、第 8 章の列の並びと同じく、**順が意味を持つ対応づけはタプルのリストで持ちます**。

分割（`folds`）は 1 回だけ作り、すべての指標で使い回します。指標によって分け方が違うと、指標どうしを比べられないからです。

## 11.10 Scholar の評価指標と突き合わせる

### 分類の指標は f32 に落ちる

第 7 章で「Nx のテンソルは `type: :f64` を明示する」と決めました。`Scholar.Metrics.Classification` にそれが効くかを、学習用テストで確かめます。

```elixir
    test "Scholar の分類の指標は f64 を渡しても f32 に落ちる" do
      y_true = Nx.tensor([1.0, 1.0, 0.0], type: :f64)
      y_pred = Nx.tensor([1.0, 0.0, 0.0], type: :f64)

      assert Nx.type(Classification.accuracy(y_true, y_pred)) == {:f, 32}
    end
```

**f64 を渡しても f32 が返ります。** `accuracy/3` は `Nx.equal/2` で真偽値のテンソル（`u8`）を作ってから平均を取るので、途中で型の情報が落ちるからです。`precision`・`recall`・`f1_score` も同じで、ラベルを整数のテンソルで渡す以上、Scholar が `to_float_type/1` で決める計算の型は f32 になります。

そのため、自作と比べるときの許容誤差は 1e-7 までしか詰められません。

```elixir
    test "正解率と適合率と再現率と F 値が一致する", %{actual: a, predicted: p, cm: cm} do
      scores = C.scholar_scores(a, p, "1")

      # Scholar の分類の指標は f32 で返るので、小数 7 桁までしか比べられない
      assert_in_delta scores.accuracy, C.accuracy(a, p), 1.0e-7
      assert_in_delta scores.precision, C.precision(cm), 1.0e-7
      assert_in_delta scores.recall, C.recall(cm), 1.0e-7
      assert_in_delta scores.f1_score, C.f1_score(cm), 1.0e-7
    end
```

一方、**回帰の指標は f64 のまま返ります**。こちらは 1 ビットも違いません。

```elixir
    test "回帰の平均二乗誤差は f64 のまま一致する" do
      t = [1.0, 2.0, 3.0, 4.0]
      y = [1.5, 2.0, 3.0, 4.5]

      assert C.scholar_mean_squared_error(t, y) == C.mean_squared_error(t, y)
    end
```

`assert_in_delta` ではなく `==` で比べていることに注目してください。**同じライブラリの中で、指標の種類によって精度が変わる** ことを、テストの書き方の違いとして残しました。

### 文字列のラベルを整数に符号化する

Scholar の分類の指標はテンソルしか受け取らないので、`"1"`・`"0"` という文字列のラベルを整数に直します。

```elixir
  @doc "ラベルを、正例なら 1、そうでなければ 0 の整数にする。"
  def label_codes(labels, positive) do
    Enum.map(labels, fn label -> if label == positive, do: 1, else: 0 end)
  end

  def scholar_scores(actual, predicted, positive) do
    y_true = Nx.tensor(label_codes(actual, positive), type: :u32)
    y_pred = Nx.tensor(label_codes(predicted, positive), type: :u32)

    %{
      accuracy: Nx.to_number(Classification.accuracy(y_true, y_pred)),
      precision: Nx.to_number(Classification.binary_precision(y_true, y_pred)),
      recall: Nx.to_number(Classification.binary_recall(y_true, y_pred)),
      f1_score: Nx.to_number(Classification.f1_score(y_true, y_pred, num_classes: 2)[1])
    }
  end
```

Scholar には `binary_precision/2`・`binary_recall/2` はあっても **`binary_f1_score/2` はありません**。そこで `f1_score/3` にクラスの数を渡し、既定の `average: :none` が返す「クラスごとの F 値」の 2 番目（正例＝符号 1）を `[1]` で取り出しています。Rumale が「正解に現れるラベルを昇順に並べて最後のものを正例にする」ルールだったのに対し、Scholar は **符号の大きいほうが 1 番目の添字** という、より素直な決まりでした。

混同行列の並びも確かめます。

```elixir
    test "混同行列は負例が先で行が正解になる", %{actual: a, predicted: p, cm: cm} do
      %{tp: tp, fp: fp, fn: misses, tn: tn} = cm

      assert C.scholar_confusion_matrix(a, p, "1") == [tn, fp, misses, tp]
    end
```

Scholar の `confusion_matrix/3` は「行が正解、列が予測、クラスの昇順」なので、平らにすると `[tn, fp, fn, tp]` の順です。自作の `%{tp: .., fp: .., fn: .., tn: ..}` とは **並びが逆さま** なので、テストで対応を固定しておきます。ここでも `%{fn: misses}` と名前を変えて受けています。

### AUC はラベルの型で精度が変わる

```elixir
  def scholar_auc(scores, labels) do
    y_true = Nx.tensor(Enum.map(labels, fn ok -> if ok, do: 1.0, else: 0.0 end), type: :f64)
    y_score = Nx.tensor(scores, type: :f64)

    Nx.to_number(
      Classification.roc_auc_score(
        y_true,
        y_score,
        Classification.distinct_value_indices(y_score)
      )
    )
  end
```

ここが分類の指標の中で唯一の例外です。`roc_auc_score/4` が使う計算の型は **正解のテンソルの型** から決まるので、正解を `1.0`・`0.0` の f64 で渡すと **f64 で返ります**。整数で渡すと f32 に落ちます。そのため AUC だけは 1e-12 で比べられます。

```elixir
    test "同じスコアが並んでも AUC が一致する" do
      scores = [0.5, 0.5, 0.2, 0.9, 0.5]
      labels = [true, false, false, true, true]

      assert_in_delta C.scholar_auc(scores, labels), C.auc(C.roc_curve(scores, labels)), 1.0e-12
    end
```

Scholar の ROC は、閾値の候補を `distinct_value_indices/1` という **別の関数** で先に作り、それを引数で渡す設計です。`defn`（Nx の数値関数）の中では「同じ値をまとめて数が減る」ような形の変わる処理が書けないので、そこだけ素の Elixir に出してあります。呼ぶ側から見ると手順が 1 つ増えますが、**自作の `Enum.group_by/3` と同じことを外でやらされている** と分かれば納得できます。

### `k_fold_split/2` は余りを捨てる

`Scholar.ModelSelection.k_fold_split/2` は、自作の `k_fold/3` と同じ「K 分割」ですが、**余りの扱いが違いました**。

```elixir
    test "k_fold_split は余りを捨てるので自作と件数が違う" do
      # 自作は余りを先頭の分割から 1 件ずつ配るが、Scholar は floor(件数 / 分割数) で切りそろえる
      assert Enum.map(C.k_fold_sequential(10, 3), &length(&1.test)) == [4, 3, 3]
      assert C.scholar_k_fold_test_sizes(10, 3) == [3, 3, 3]

      assert Enum.map(C.k_fold_sequential(11, 4), &length(&1.test)) == [3, 3, 3, 2]
      assert C.scholar_k_fold_test_sizes(11, 4) == [2, 2, 2, 2]
    end
```

10 件を 3 分割すると、Scholar のテストデータは 3 件ずつで、**1 件はどの分割のテストデータにも入りません**。11 件を 4 分割すると 3 件が捨てられます。実装は `floor(Nx.axis_size(x, 0) / k)` をすべての分割の大きさにしているだけです。

これは Tribuo の `KFoldSplitter`（Java 版・Clojure 版が比べた相手）とも、Rumale の `KFold`（Ruby 版）とも違います。どちらも余りを先頭の分割から配ります。**「K 分割交差検証」という名前が同じでも、端の扱いは実装ごとに違う** ということです。11.8 節で `{tests, []}` というパターンで「配り切った」ことを書いたのは、この違いに気づけるようにするためでもありました。

割り切れる件数なら一致するので、そのときだけ分割ごとの MSE を突き合わせます。

```elixir
    test "割り切れる件数なら分割ごとの MSE が一致する" do
      x = features(1..12)
      t = Enum.map(1..12, &(&1 * 2.0 + 1.0))
      folds = C.k_fold_sequential(12, 3)

      mine =
        C.linear_trainer(columns())
        |> C.cross_validate(x, t, folds, &C.mean_squared_error/2)
        |> Enum.to_list()

      theirs = C.scholar_cross_validate_mse(x, t, columns(), 3)

      assert length(theirs) == 3

      for {a, b} <- Enum.zip(mine, theirs) do
        assert_in_delta a, b, 1.0e-6
      end
    end
```

`Scholar.ModelSelection.cross_validate/4` は、分け方を作る関数と採点する関数を受け取る点が自作と同じ形です。

```elixir
  def scholar_cross_validate_mse(x, t, columns, n_splits) do
    folding = fn tensor -> ModelSelection.k_fold_split(tensor, n_splits) end

    ModelSelection.cross_validate(
      Nx.tensor(feature_matrix(x, columns), type: :f64),
      Nx.tensor(t, type: :f64),
      folding,
      &score_fold/2
    )
    |> Nx.to_flat_list()
  end

  defp score_fold({x_train, x_test}, {t_train, t_test}) do
    model = LinearRegression.fit(x_train, t_train)
    [Regression.mean_square_error(t_test, LinearRegression.predict(model, x_test))]
  end
```

採点する関数が **指標のリストを返す** ことに注意してください。Scholar はそれを `Nx.stack/2` で積むので、1 つでもリストにする必要があります。戻り値は「指標 × 分割」の 2 次元テンソルです。自作の `evaluate/3` が「指標ごとに交差検証を回す」のに対し、Scholar は「1 回の交差検証で複数の指標をまとめて採る」設計です。**同じ分割を使い回す** という 11.9 節の要請を、Scholar は API の形で強制しています。

突き合わせで分かった Scholar の約束事をまとめます。

| 項目 | 分かったこと |
|---|---|
| `Classification.accuracy/3` ほか | f64 を渡しても f32 で返る。自作とは 1e-7 までしか比べられない |
| `Classification.roc_auc_score/4` | 正解を f64 のテンソルにすれば f64 で返る。整数なら f32 |
| `Regression.mean_square_error/3` | f64 のまま返る。自作と 1 ビットも違わない |
| `binary_f1_score` | 無い。`f1_score/3` に `num_classes: 2` を渡して `[1]` で取り出す |
| `confusion_matrix/3` | 行が正解、列が予測、クラスの昇順。平らにすると `[tn, fp, fn, tp]` |
| `roc_curve/4` | 閾値の候補（`distinct_value_indices/1`）を呼ぶ側が先に作って渡す |
| `k_fold_split/2` | 余りの行を捨てる。テストデータは `floor(件数 / 分割数)` ですべて同じ大きさ |
| `cross_validate/4` | 採点する関数は指標のリストを返す。戻り値は「指標 × 分割」のテンソル |
| 正例を 1 件も予測しない場合 | Scholar も 0.0 を返す。NaN にはならない |

## 11.11 実データで評価する

### 簡略化した前処理

交差検証にかけるには、欠損値や文字列の列を数値にしておく必要があります。前処理パイプラインは第 8 章で扱ったので、この章ではそれを簡略化したものを使います。

```elixir
  def prepare_survived(table) do
    age_mean = Map.fetch!(Chapter02.column_means(table.rows, [:Age]), :Age)

    x =
      Enum.map(table.rows, fn row ->
        %{
          Pclass: Chapter02.number(row, :Pclass),
          Age: Chapter02.number(row, :Age) || age_mean,
          male: if(Chapter02.text(row, :Sex) == "male", do: 1.0, else: 0.0)
        }
      end)

    %{x: x, t: Enum.map(table.rows, &Chapter02.text(&1, :Survived))}
  end
```

補完に使う平均値を **分割の前に全体から** 求めているので、厳密にはテストデータの情報が訓練に漏れています（リーク）。第 8 章のパイプラインは分割のあとで補完しました。ここは他の言語版と条件をそろえるために、あえて簡略化した手順に合わせています。

`Chapter02.number(row, :Age) || age_mean` は、第 2 章の `number/2` が欠損値に `nil` を返すことを利用しています。Java 版の `Optional.orElse`、Scala 版の `getOrElse` にあたるものが、Elixir では `||` です。

### 交差検証の実験

```elixir
  def evaluate_survived(path) do
    evaluate(
      tree_trainer(@survived_columns, @tree_depth),
      prepare_survived(Chapter02.load_table(path)),
      survived_metrics()
    )
  end

  def evaluate_cinema(path) do
    evaluate(
      linear_trainer(Chapter07.feature_columns()),
      prepare_cinema(Chapter02.load_table(path)),
      cinema_metrics()
    )
  end
```

```console
$ mix run -e "GettingStartedMl.Chapter11.run()"
Survived（決定木、5 分割交差検証の平均）
  正解率: 0.7811
  適合率: 0.7759
  再現率: 0.6306
  F値: 0.6833
cinema（線形回帰、5 分割交差検証の平均）
  RMSE: 405.77
  MAE: 321.53
Scholar の k_fold_split のテストデータの件数（5 分割）
  891 件を自作: 179, 178, 178, 178, 178
  891 件を Scholar: 178, 178, 178, 178, 178
```

Survived の決定木は、正解率 0.7811 に対して再現率が 0.6306 です。**生存者のうち 4 割近くを見逃している** ことが、正解率だけでは見えませんでした。適合率 0.7759 は、「生存」と予測した人の 8 割近くが本当に生存していたことを表します。11.2 節で述べたとおり、正解率は「どちらを間違えたか」を隠します。

cinema の RMSE（405.77）は MAE（321.53）より大きく、11.6 節で見たとおり、大きく外れた予測があることを示します。第 7 章では 1 回の分割で RMSE が 376.14 でしたが、この章は外れ値を除かずに 5 回の平均を取っているので、単純には比べられません。

最後の 2 行が 11.10 節の違いを実データで見せています。891 件を 5 分割すると、自作は先頭の分割に 1 件多く配って 179 件にしますが、**Scholar は 891 番目の行をどの分割でもテストしません**。1 件なので結果はほとんど変わりませんが、「全部の行が必ず 1 回はテストされる」という交差検証の前提は崩れています。

**6 つの評価指標の数値は、Java 版・Scala 版・Clojure 版の同じ節とすべて一致します。** 分割の並べ替えを `java.util.Random` と同じ線形合同法（`GettingStartedMl.Random`）で行い、余りの配り方も訓練データの並びもそろえたためです。乱数が別実装の Kotlin 版とは一致しません（Kotlin 版の正解率は 0.7677）。

### 実データのテスト

学習データが無い環境では、`@tag :data` を付けたテストが `test_helper.exs` の `exclude` で外れます（第 1 章から続けている形です）。

```elixir
    @tag :data
    test "Survived の指標は Scholar の指標と一致する" do
      %{x: x, t: t} = survived_data()
      [fold | _] = C.k_fold(length(x), C.n_splits(), C.seed())

      predict =
        C.tree_trainer(C.survived_columns(), 2).(C.pick(x, fold.train), C.pick(t, fold.train))

      actual = C.pick(t, fold.test)
      predicted = predict.(C.pick(x, fold.test))
      cm = C.confusion_matrix(actual, predicted, C.survived())
      scores = C.scholar_scores(actual, predicted, C.survived())

      assert_in_delta scores.accuracy, C.accuracy(actual, predicted), 1.0e-7
      assert_in_delta scores.precision, C.precision(cm), 1.0e-7
      assert_in_delta scores.recall, C.recall(cm), 1.0e-7
      assert_in_delta scores.f1_score, C.f1_score(cm), 1.0e-7
    end
```

人工データで一致していても、実データ（891 件・3 列）でも一致するとは限りません。1 つ目の分割で決定木を学習し、その予測を自作と Scholar の両方で採点して、本番に近い条件で確かめます。

分割ごとの MSE と RMSE の関係も、実データで結んでおきます。

```elixir
    @tag :data
    test "cinema の分割ごとの MSE は第 7 章の RMSE の二乗と一致する" do
      %{x: x, t: t} =
        Dataset.dir() |> Path.join("cinema.csv") |> Chapter02.load_table() |> C.prepare_cinema()

      folds = C.k_fold(length(x), C.n_splits(), C.seed())
      trainer = C.linear_trainer(Chapter07.feature_columns())
      mse = Enum.to_list(C.cross_validate(trainer, x, t, folds, &C.mean_squared_error/2))

      rmse =
        Enum.to_list(C.cross_validate(trainer, x, t, folds, &Chapter07.root_mean_squared_error/2))

      for {a, b} <- Enum.zip(mse, rmse) do
        assert_in_delta a, b * b, 1.0e-6
      end
    end
```

同じ `folds` を 2 回渡して、指標だけを差し替えています。11.9 節で「分割は 1 回だけ作って使い回す」と決めたことが、そのままテストの書きやすさになりました。

表示は、第 1 章から続けている形のテストで固定します。値は実装を実データで動かして得たものなので、Red を経ていません。

```elixir
    @tag :data
    test "実行すると交差検証の平均を表示する" do
      output = ExUnit.CaptureIO.capture_io(&C.run/0)

      assert String.split(output, "\n", trim: true) == [
               "Survived（決定木、5 分割交差検証の平均）",
               "  正解率: 0.7811",
               …
               "  891 件を Scholar: 178, 178, 178, 178, 178"
             ]
    end
```

## 11.12 品質チェック

```console
$ mix format --check-formatted && mix compile --warnings-as-errors && mix credo --strict && mix test --cover
Checking 19 source files ...
Analysis took 0.6 seconds (0.05s to load, 0.5s running 69 checks on 19 files)
277 mods/funs, found no issues.

181 tests, 0 failures

Percentage | Module
-----------|--------------------------
   100.00% | GettingStartedMl.Chapter11
```

Credo が 1 件だけ止めてくれたのは、テストの中で `Scholar.Metrics.Classification.accuracy(...)` とモジュールをフルに書いたところでした（`Credo.Check.Design.AliasUsage`）。

```console
[D] ↘ Nested modules could be aliased at the top of the invoking module.
      test/getting_started_ml/chapter11_test.exs:273:22
```

`alias Scholar.Metrics.Classification` をテストの先頭に足して直しました。**本体では最初から alias していたのに、テストでだけ手を抜いていた** ところを見つけてくれた形です。第 8 章では入れ子の深さで止められましたが、どちらも「名前を付けそこねた」という同じ指摘の別の顔でした。

学習データが無い環境（`ML_DATA_DIR=/nonexistent mix test`）では、`:data` のテストが 16 件除外されて通ります。

## 11.13 可視化について

Elixir 版では Notebook と可視化を扱いません。混同行列のヒートマップ、ROC 曲線のグラフ、分割ごとのスコアのばらつきは、[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と [Kotlin 版の第 11 章](../kotlin/11-evaluation-metrics-and-cross-validation.md) の「Notebook による探索と可視化」の節を参照してください。

`cross_validate/5` が返すのは素の数のストリームなので、平均だけでなくばらつきを見たいときは `Enum.sort/1` や分位数をそのまま求められます。`roc_curve/2` が返すのもマップのリストなので、そのまま座標の並びとして扱えます。

## 11.14 まとめ

この章では、評価指標・ROC 曲線・K 分割交差検証を Elixir の TDD で自作し、Scholar と突き合わせました。

| 作ったもの | Elixir での表し方 | 他の版 |
|-----------|------------------|-------|
| 混同行列 | `%{tp: .., fp: .., fn: .., tn: ..}` のマップ | `record` / `case class` |
| 評価関数 | `fn 正解, 予測 -> 数 end` | `Metric<T>` / `type Metric[T]` |
| 分類器 | `fn x, t -> (fn x -> 予測 end) end` | `interface Model<T>` とアダプター 2 つ |
| 分け方 | `%{train: [..], test: [..]}` のマップ | `record Fold` / `case class Fold` |
| ROC の 1 点 | `%{threshold:, false_positive_rate:, true_positive_rate:}` | `Data.define` / `record` |
| 指標の並び | `[{"正解率", 関数}, …]` のタプルのリスト | `LinkedHashMap` / `ListMap` |

Elixir 版ならではの学びです。

1. **名前を付けるところが無い** — `Metric` も `Model` も `Fold` も定義しなかった。ビヘイビアもプロトコルも要らない。引き換えに、分類のモデルに回帰の指標を渡す取り違えは実行するまで分からない
2. **場合の網羅は関数節で並べる** — 4 つの数を 1 つずつ増やす代わりに、`cell/2` の 4 つの節で「どの箱に入るか」を並べてから `Enum.frequencies/1` で数えた。場合の網羅が形として読める。ただし **コンパイラは節の漏れを数えてくれない**
3. **`fn` はキーには書けても、読み出せない** — `%{fn: 0}` は作れるが `cm.fn` も `%{fn: fn}` も書けない。`%{fn: misses}` と受ける側で名前を変えた。第 8 章の「大文字始まりのキーは `row.Sex` と書けない」と同じ規則の別の顔
4. **不変条件はパターンで書く** — 分割を配り切ったことを `{tests, []} = Enum.map_reduce(...)` の左辺で表した。コメントより短く、破れたときに必ず止まる
5. **遅延の細かさを思い込まない** — `Stream.map/2` は 1 件ずつ実現する。Clojure 版の「32 件ずつまとめて」とは違った。プロセスのメッセージで回数を数え、癖をテストに書いて固定した
6. **同じライブラリでも指標の種類で精度が違う** — Scholar の分類の指標は f64 を渡しても f32 で返り、回帰の指標は f64 のまま。AUC だけは正解の型で決まる。**比べる許容誤差を指標ごとに変える** ことになった
7. **「K 分割」の端の扱いは実装ごとに違う** — Scholar の `k_fold_split/2` は余りを捨てる。Tribuo も Rumale も先頭から配る。名前が同じでも中身を確かめないと、「全部の行が 1 回はテストされる」という前提が黙って崩れる
8. **手順をそろえると数値が一致する** — 並べ替え・余りの配り方・訓練データの並びまでそろえたので、6 つの評価指標が Java 版・Scala 版・Clojure 版と完全に一致した

次の章では、正則化で過学習を抑え、検証データでモデルを選ぶ方法を実装します。Scholar にリッジ回帰はありますが **ラッソ回帰はない** ので、この章の「自作したものがそのまま最終実装になる」パターンがもう一度出てきます。
