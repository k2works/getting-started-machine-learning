---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ソフトマックスと勾配降下法のロジスティック回帰、第 3 章の決定木を再利用したランダムフォレストと特徴量の重要度を Elixir の TDD で自作し、分類器を「学習して予測する関数を返す関数」で表して Scholar と突き合わせ、正解率の 8 個が Java 版と一致すること・重要度が 4 桁目でずれる理由・Nx が素の Elixir より遅い場面を確かめる。"
tags: [article,getting-start-ml,elixir]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:00:00Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。Java 版は `interface Classifier`、Scala 版は `trait Classifier` を用意し、第 3 章の決定木をアダプターで包みました。**Elixir 版はインターフェースを作りません。** 分類器を「訓練データを受け取り、予測する関数を返す関数」とすれば、第 3 章の決定木も自作のロジスティック回帰も Scholar のトレーナーも、同じ `score/3` で評価できます。

最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作し、Scholar のロジスティック回帰と正解率を突き合わせます。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進め、[Java 版](../java/10-logistic-regression-and-ensemble.md)・[Clojure 版](../clojure/10-logistic-regression-and-ensemble.md) と数値を対比し、分類器を関数の型で表した [F# 版](../fsharp/10-logistic-regression-and-ensemble.md) と設計を対比します。注目してほしいのは次の 4 点です。

- **Scholar にランダムフォレストがありません。** 第 3 章の決定木に続いて、この章の森も **自作がそのまま最終実装** になります。ライブラリと突き合わせられるのはロジスティック回帰だけです（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）
- **Nx を使わないほうが速い場面があります。** 勾配降下法を Nx のテンソル演算で書いたら 5000 回で約 14 秒かかり、素の Elixir のリストで書いたら **約 2 秒** でした。「テンソルのライブラリがあるから使う」は、規模によっては逆効果です
- **乱数と手順を Java 版にそろえると、正解率は一致します。** 自作の 8 個の数値は Java 版・Clojure 版と完全に一致しました
- **それでも特徴量の重要度は 4 桁目でずれました。** 原因を測定して突き止めたので、9 節に書きます

データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `prepare_iris/3` で前処理します。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 合計が 1 になる確率にする
  - [ ] 大きな値でもあふれない
- [ ] 交差エントロピーで損失を測る
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 分けられるデータを正しく予測する
  - [ ] 学習した品種が名前の順に並ぶ
  - [ ] 繰り返すほど損失が小さくなる
- [ ] ランダムフォレストを作る
  - [ ] 多数決でまとめる
  - [ ] ブートストラップ標本を作る
  - [ ] 木ごとに使う特徴量を絞る
- [ ] 特徴量の重要度を求める
  - [ ] 決定木 1 本の重要度を求める
  - [ ] 森の重要度を求める
- [ ] すべてのモデルを同じ関数で評価する
- [ ] Scholar のロジスティック回帰と突き合わせる

コードは `GettingStartedMl.Chapter10` の 1 つのモジュールに置きます。

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和」を求め、それを確率に変換します。変換に使うのが **ソフトマックス関数** です。

```text
softmax(z)ᵢ = exp(zᵢ) / Σ exp(zⱼ)
```

すべての要素が 0 より大きく、合計が 1 になるので、確率として読めます。

```elixir
    test "合計が一になる確率にする" do
      p = C.softmax([1.0, 2.0, 3.0])

      assert_in_delta Enum.sum(p), 1.0, @delta
      assert Enum.all?(p, &(&1 > 0))
      assert p == Enum.sort(p)
    end

    test "同じスコアなら同じ確率になる" do
      assert Enum.all?(C.softmax([2.0, 2.0, 2.0]), &(abs(&1 - 1.0 / 3) < @delta))
    end

    test "大きな値でもあふれない" do
      p = C.softmax([1000.0, 1001.0])

      assert_in_delta Enum.sum(p), 1.0, @delta
      assert Enum.all?(p, &is_float/1)
    end
```

`p == Enum.sort(p)` は「入力の大小の順が確率の順に保たれる」ことを確かめています。

### 大きな値でもあふれない

定義どおりに `exp` を取ると、`exp(1000)` は倍精度の上限を超えます。ここで **Erlang は JVM と違う振る舞いをします**。JVM は `Infinity` を返し、`Infinity / Infinity` が黙って `NaN` になりました。Erlang の `:math.exp/1` は **`ArithmeticError`（`:badarith`）を投げます**。Erlang の浮動小数点数には無限大も NaN も無く、演算があふれた時点で落ちるからです。

どちらにせよ正しい答えは返らないので、対策は同じです。すべての要素から最大値を引いてから `exp` を取れば、指数が 0 以下になるのであふれません。引いた分は分母と分子で打ち消し合うので、結果は変わりません。

```elixir
  def softmax(z) do
    maximum = Enum.max(z)
    exps = Enum.map(z, &:math.exp(&1 - maximum))
    total = Enum.sum(exps)

    Enum.map(exps, &(&1 / total))
  end
```

テストの最後の行が `assert Enum.all?(p, &is_float/1)` になっているのは、この違いを踏まえたものです。Java 版・Clojure 版は「`NaN` でないこと」を確かめました。Elixir では `NaN` という値が存在しないので、確かめるべきは「例外にならず、浮動小数点数が返ること」になります。**無いものはテストできない** ので、テストの形が言語の値の世界に合わせて変わります。

## 10.4 ロジスティック回帰

### 交差エントロピー

損失（予測の悪さ）は **交差エントロピー** で測ります。正解の品種に与えた確率の対数を取り、平均してマイナスを付けます。確率が 1 なら 0、小さいほど大きくなります。

```elixir
  def cross_entropy(probabilities, targets) do
    sum =
      probabilities
      |> Enum.zip(targets)
      |> Enum.map(fn {p, target} -> :math.log(Enum.at(p, target) + @epsilon) end)
      |> Enum.sum()

    -(sum / length(probabilities))
  end
```

確率が 0 のときに `:math.log(0.0)` が落ちないよう、`@epsilon`（1e-12）を足しているのは Java 版・Clojure 版と同じです。ここでも事情は少し違って、JVM の `Math.log(0)` は `-Infinity` を返すのに対し、Erlang の `:math.log(0.0)` は `ArithmeticError` になります。**避けたい理由が「値が汚れるから」ではなく「落ちるから」に変わる** だけで、書くコードは同じです。

### 学習と予測

バッチ勾配降下法では、次を繰り返します。

1. すべての行のスコアを求め、ソフトマックスで確率にする
2. 損失を記録する
3. 誤差（確率 − 正解。正解の品種だけ 1 を引く）を求める
4. 誤差から勾配を求め、学習率を掛けて重みと切片から引く

```elixir
    test "分けられるデータを正しく予測する" do
      {x, t} = two_species()
      predict = C.logistic_trainer().(x, t, columns())

      assert predict.(x) == t
    end

    test "学習した品種は名前の順に並ぶ" do
      {x, t} = two_species()

      assert C.logistic_fit(x, t, columns()).classes == ["setosa", "virginica"]
    end

    test "繰り返すほど損失が小さくなる" do
      {x, t} = two_species()
      losses = C.logistic_fit(x, t, columns(), 1.0, 50).losses

      assert length(losses) == 50
      assert List.last(losses) < hd(losses)
    end

    test "学習率が零なら重みは変わらず損失も変わらない" do
      {x, t} = two_species()
      model = C.logistic_fit(x, t, columns(), 0.0, 3)

      assert Enum.all?(List.flatten(model.weights), &(&1 == 0.0))
      assert Enum.uniq(model.losses) == [hd(model.losses)]
    end
```

`C.logistic_trainer().(x, t, columns())` の書き方が、この章の設計を表しています。`logistic_trainer()` が **分類器**（無名関数）を返し、それに訓練データを渡すと **予測する関数** が返ります。Elixir では無名関数の呼び出しに `.()` が要るので、「これは関数値である」ことが呼ぶ側にもはっきり出ます。Clojure 版の `((logistic-trainer) x t columns)` という二重のかっこに対応します。

学習の本体は、リストだけで書きました。

```elixir
  def logistic_fit(
        x,
        t,
        columns,
        learning_rate \\ @default_learning_rate,
        epochs \\ @default_epochs
      ) do
    classes = t |> Enum.uniq() |> Enum.sort()
    targets = Enum.map(t, fn label -> Enum.find_index(classes, &(&1 == label)) end)
    rows = Enum.map(x, fn features -> Enum.map(columns, &Map.fetch!(features, &1)) end)
    weights = List.duplicate(List.duplicate(0.0, length(classes)), length(columns))
    bias = List.duplicate(0.0, length(classes))

    {weights, bias, losses} =
      descend(rows, targets, weights, bias, learning_rate, length(rows), epochs, [])

    %{
      columns: columns,
      classes: classes,
      weights: weights,
      bias: bias,
      losses: Enum.reverse(losses)
    }
  end
```

繰り返しは末尾再帰です。

```elixir
  # 1 エポックぶん進める。損失は新しいものを先頭に積み、最後に並びを戻す。
  defp descend(_rows, _targets, weights, bias, _rate, _n, 0, losses),
    do: {weights, bias, losses}

  defp descend(rows, targets, weights, bias, rate, n, epochs, losses) do
    probabilities = Enum.map(rows, &softmax(row_scores(&1, weights, bias)))
    loss = cross_entropy(probabilities, targets)

    errors = Enum.zip_with(probabilities, targets, &error/2)
    weights = update_weights(weights, rows, errors, rate, n)
    bias = update_bias(bias, errors, rate, n)

    descend(rows, targets, weights, bias, rate, n, epochs - 1, [loss | losses])
  end
```

Elixir には可変の変数もループ構文もありません。「繰り返し」は **自分を呼び直すこと** で、「状態の更新」は **次の呼び出しに違う引数を渡すこと** です。`epochs` が 0 になったら 1 つ目の節に入って止まります。Clojure 版がこの部分を `dotimes` と `double-array` と `aset` で書いた（そして「Clojure らしくない見た目です」と断った）のと、正反対の姿になりました。

損失を `[loss | losses]` と **先頭に積んで** 最後に `Enum.reverse/1` するのは、リストの先頭への追加が O(1)、末尾への追加が O(n) だからです。Elixir で並びを作るときの定型です。

各段は小さな関数に切り出しました。

```elixir
  # 誤差は「確率 − 正解」。正解の品種だけ 1 を引く。
  defp error(probabilities, target) do
    Enum.with_index(probabilities, fn value, i -> if i == target, do: value - 1.0, else: value end)
  end

  # 1 行のスコア（品種ごと）。特徴量を外、品種を内にして足し込む。
  defp row_scores(row, weights, bias) do
    row
    |> Enum.zip(weights)
    |> Enum.reduce(bias, fn {value, for_feature}, acc ->
      Enum.zip_with(acc, for_feature, fn sum, weight -> sum + value * weight end)
    end)
  end
```

`row_scores/3` の畳み込みの初期値が `bias` なのがポイントです。「切片から始めて、特徴量ごとに重み付きの値を足していく」という式がそのままコードになります。**特徴量が外側、品種が内側** という順は Java 版と同じにしました。浮動小数点の足し算は順によって結果が変わるので、数値を一致させるには順まで合わせる必要があります。

勾配も同じ形です。

```elixir
  defp update_weights(weights, rows, errors, rate, n) do
    zero = Enum.map(weights, fn for_feature -> Enum.map(for_feature, fn _ -> 0.0 end) end)

    gradient =
      rows
      |> Enum.zip(errors)
      |> Enum.reduce(zero, fn {row, error}, acc -> add_outer_product(acc, row, error) end)

    Enum.zip_with(weights, gradient, fn for_feature, gradients ->
      Enum.zip_with(for_feature, gradients, fn weight, g -> weight - rate * g / n end)
    end)
  end

  # acc[特徴量][品種] に「その行の特徴量 × 誤差」を足す。
  defp add_outer_product(acc, row, error) do
    Enum.zip_with(acc, row, fn for_feature, value ->
      Enum.zip_with(for_feature, error, fn sum, e -> sum + value * e end)
    end)
  end
```

Java 版・Clojure 版が `weights[特徴量 * 品種数 + 品種]` と 1 本の配列に平らに並べたところを、Elixir では **リストのリスト** のままにしました。添字の計算が出てこないので、ずれる余地がありません。

予測は、スコアがいちばん大きい品種を選びます。

```elixir
  defp argmax(values) do
    values
    |> Enum.with_index()
    |> Enum.reduce(fn {value, i}, {best, best_i} ->
      if value > best, do: {value, i}, else: {best, best_i}
    end)
    |> elem(1)
  end
```

`>` を厳密な不等号にしているので、同点なら先に現れた品種を選びます。Java 版の `argmax` と同じ規則です。`Enum.max_by/2` を使わなかったのは、同点のときにどちらを返すかを自分で決めておきたかったからです（`Enum.max_by/2` は先に現れたほうを返すので結果は同じですが、規則をコードに書いておくほうが、ほかの版と突き合わせるときに読みやすくなります）。

そして、分類器はこれだけです。

```elixir
  def logistic_trainer(learning_rate \\ @default_learning_rate, epochs \\ @default_epochs) do
    fn x, t, columns ->
      model = logistic_fit(x, t, columns, learning_rate, epochs)

      fn x -> logistic_predict(model, x) end
    end
  end
```

Java 版・Scala 版には「学習する前に予測するとエラーになる」というテストがありました。`fit` を呼ぶ前は `model` が `null` で、`predict` が `IllegalStateException` を投げるという振る舞いです。**Elixir 版にはこのテストがありません。** 予測する関数は学習が終わってからしか作られないので、「学習前のモデル」という状態がそもそも存在しないからです。**表せない状態は、テストする必要もありません。** F# 版・Clojure 版が同じ理由で同じテストを持たないのと同じです。

### Nx を使わないほうが速かった

Elixir には Nx というテンソルのライブラリがあります。勾配降下法はまさに行列演算なので、最初は Nx で書きました。

```elixir
# 最初に書いたもの（採用しなかった）
z = Nx.dot(xs, w)
p = Nx.divide(Nx.exp(z), Nx.sum(Nx.exp(z), axes: [1], keep_axes: true))
w = Nx.subtract(w, Nx.multiply(rate / n, Nx.dot(Nx.transpose(xs), err)))
```

105 件 × 4 特徴量 × 3 品種を 5000 回まわして計測したところ、**Nx では約 14 秒、上に載せた素の Elixir のリストでは約 2 秒** でした。7 倍の差です。

理由は、ADR 012 で決めたとおり **EXLA を入れず `Nx.BinaryBackend` を使っている** ことにあります。BinaryBackend はテンソルをバイナリとして持ち、演算のたびに新しいバイナリを組み立てる Elixir の実装です。GPU も SIMD も使いません。105 × 4 のような小さな行列では、その組み立ての費用が計算そのものを上回ります。

**「テンソルのライブラリがあるから使う」は、規模によっては逆効果です。** Nx が効くのは、EXLA を入れて行列が十分大きいときです。本書の規模では、素のリストのほうが速く、しかも読みやすく、ループの順を Java 版にそろえられました。第 9 章の正規方程式（`Nx.LinAlg.solve/2`）のように、**自分で書くと面倒なアルゴリズム** では Nx を使い、単純な積和の繰り返しでは使わない、という使い分けに落ち着きました。

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、少しずつ違う決定木をたくさん作り、多数決で予測します。違いの作り方は 2 つです。

- **ブートストラップ標本**: 訓練データから、重複を許して同じ件数だけ選ぶ
- **特徴量の部分集合**: 木ごとに、使える特徴量をいくつかに絞る

**Scholar にランダムフォレストはありません。** 第 3 章の決定木に続いて、ここで作る森がそのまま最終実装になります。

### 多数決とブートストラップ標本

```elixir
    test "サンプルごとに最も多い予測を選ぶ" do
      assert C.majority_vote([["a", "b"], ["a", "c"], ["b", "b"]]) == ["a", "b"]
    end

    test "同数なら先に現れた予測を選ぶ" do
      assert C.majority_vote([["a"], ["b"]]) == ["a"]
    end

    test "行番号を重複を許して件数と同じだけ選ぶ" do
      {sample, _state} = C.bootstrap_sample(5, Random.new(0))

      assert length(sample) == 5
      assert Enum.all?(sample, &(&1 >= 0 and &1 < 5))
    end

    test "同じシードなら同じ標本になる" do
      assert C.bootstrap_sample(10, Random.new(0)) == C.bootstrap_sample(10, Random.new(0))
    end
```

```elixir
  @doc "サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。"
  def majority_vote([first | _] = votes) do
    Enum.map(0..(length(first) - 1)//1, fn sample ->
      votes |> Enum.map(&Enum.at(&1, sample)) |> Chapter03.majority()
    end)
  end
```

```elixir
  def bootstrap_sample(size, state) do
    Enum.map_reduce(1..size//1, state, fn _, state -> Random.next_int(state, size) end)
  end
```

多数決は、第 3 章の `majority/1`（いちばん多いラベル、同数なら先に現れたほう）をそのまま使えました。Java 版は `RandomForest.mostCommon` を別に書いていますが、規則は同じです。**同じ規則が 2 か所にあるなら、1 つにできないか探します。**

`bootstrap_sample/2` が **乱数の状態を受け取って返す** のが、Java 版・Clojure 版といちばん違うところです。あちらは `java.util.Random` という可変のオブジェクトを渡し、`.nextInt` を呼ぶたびにその中の状態が進みました。Elixir には可変のオブジェクトが無いので、第 2 章で自作した線形合同法の状態（ただの整数）を引数で受け取り、使い終わった状態を戻り値に含めます。

`Enum.map_reduce/3` は、まさにこのための関数です。「各要素について値を作りながら、アキュムレーターも持ち回る」ので、`{乱数の並び, 次の状態}` が一度に得られます。**可変の乱数生成器は、関数型の言語では「状態を返す関数」になる** という置き換えが、標準の関数ひとつで書けます。

なお、Clojure 版には「`repeatedly` は遅延シーケンスなので `.nextInt` が呼ばれる時点が決まらず、Java 版と並びが合わなくなる」という注意書きがありました。**Elixir にはこの落とし穴がありません。** `Enum` の関数はすべて即座に評価します（遅延させたいときは明示的に `Stream` を使います）。既定が正格評価なので、乱数の順が「いつ結果を使うか」に左右されません。

### 森を作る

木ごとに使う特徴量を選ぶところでは、Java 版の `Collections.shuffle` と同じ手順を書きます。

```elixir
  # 渡された乱数の状態で Fisher-Yates の並べ替えをする。第 2 章の shuffle/2 と
  # 同じ手順だが、シードではなく状態を受け取って続きから引ける。
  defp shuffle_with_state(items, state) do
    array = List.to_tuple(items)
    last = tuple_size(array) - 1

    {shuffled, state} =
      if last < 1 do
        {array, state}
      else
        Enum.reduce(last..1//-1, {array, state}, fn i, {acc, state} ->
          {j, next} = Random.next_int(state, i + 1)
          {swap(acc, i, j), next}
        end)
      end

    {Tuple.to_list(shuffled), state}
  end
```

第 2 章の `Random.shuffle/2` とほとんど同じですが、**シードではなく状態を受け取ります**。森を作る途中で乱数の状態を引き継ぐ必要があるからです（標本を引いた続きから並べ替える）。並べ替えの途中で添字を使うので、リストではなく **タプル** に直しています。Elixir のタプルは `elem/2` で O(1) に読め、`put_elem/3` で「その位置だけ差し替えた新しいタプル」を作れます。

```elixir
  def forest_fit(x, t, columns, n_estimators, max_features, max_depth, seed) do
    xs = List.to_tuple(x)
    ts = List.to_tuple(t)

    {trees, _state} =
      Enum.map_reduce(1..n_estimators//1, Random.new(seed), fn _, state ->
        {rows, state} = bootstrap_sample(tuple_size(xs), state)
        {shuffled, state} = shuffle_with_state(columns, state)
        chosen = MapSet.new(Enum.take(shuffled, max_features))
        # 列の順は元のまま残す
        tree_columns = Enum.filter(columns, &MapSet.member?(chosen, &1))
        sample_x = Enum.map(rows, &Map.take(elem(xs, &1), tree_columns))
        sample_t = Enum.map(rows, &elem(ts, &1))

        {%{
           columns: tree_columns,
           rows: rows,
           tree: Chapter03.fit(sample_x, sample_t, tree_columns, max_depth)
         }, state}
      end)

    %{trees: trees}
  end
```

- **第 3 章の決定木をそのまま呼ぶ。** `Chapter03.fit/4` は特徴量・正解・列・深さの上限を受け取って木（マップ）を返す純粋な関数なので、包む必要がありません。Java 版・Scala 版は `DecisionTree` が可変のオブジェクトだったので、`DecisionTreeClassifier` というアダプターを書きました
- **乱数の状態は 100 本ぶん `Enum.map_reduce/3` で回す。** 木を作る式と、状態を引き継ぐ仕組みが 1 つの畳み込みに収まります
- **`x` と `t` をタプルに直してから引く。** ブートストラップ標本は行番号のリストなので、リストのまま `Enum.at/2` で引くと 1 回が O(n) になります。105 件 × 105 回 × 100 本では効いてきます。**添字で引くならタプル** というのは、Elixir で性能を意識するときの基本です
- **1 本分は `%{columns: ..., rows: ..., tree: ...}`。** Java 版の `FittedTree` という record に当たります。重要度の計算で、その木が使った列と行番号が要ります

森全体は `%{trees: [...]}` という素のマップなので、**値として比較できます**。「同じシードなら同じ森になる」というテストが 1 行で書けるのは、木も森も不変のデータだからです。

```elixir
    test "同じシードなら同じ森になる" do
      {x, t} = two_species()

      assert C.forest_fit(x, t, columns(), 5, 1, nil, 0) ==
               C.forest_fit(x, t, columns(), 5, 1, nil, 0)
    end
```

Java 版・Scala 版では、木の構造を比べるために `equals` を定義するか、予測が同じであることで代用する必要がありました。Elixir では **すべての項に構造的な等値と全順序がある** ので、何も用意せずに比べられます。

```elixir
  def forest_predict(%{trees: trees}, x) do
    trees
    |> Enum.map(fn %{columns: columns, tree: tree} ->
      Chapter03.predict(tree, Enum.map(x, &Map.take(&1, columns)))
    end)
    |> majority_vote()
  end
```

## 10.6 特徴量の重要度

### 計算方法

決定木は、分割のたびに不純度（ジニ不純度）を下げます。「その分割で減った不純度 × その節に来た件数」を、分割に使った列ごとに足し合わせ、合計が 1 になるようにそろえると **特徴量の重要度** になります。

第 3 章の節は `%{split: %{feature: ..., threshold: ..., impurity: ...}, left: ..., right: ...}` というマップで、`:impurity` に「分割後の左右の不純度の重み付き平均」が入っています。分割前の不純度は、その節に来たラベルから `Chapter03.gini/1` で求められます。

```elixir
  # 分割ごとに減った不純度（件数で重み付け）を、{列, 減った量} の並びにする。
  # 第 3 章で決めたとおり、葉は :label、節は :split を持つ。網羅性は検査されない。
  defp impurity_decreases(%{label: _}, _x, _t), do: []

  defp impurity_decreases(%{split: split, left: left, right: right}, x, t) do
    {to_left, to_right} =
      x
      |> Enum.zip(t)
      |> Enum.split_with(fn {features, _} ->
        Map.fetch!(features, split.feature) <= split.threshold
      end)

    [{split.feature, length(t) * (Chapter03.gini(t) - split.impurity)}] ++
      impurity_decreases(left, Enum.map(to_left, &elem(&1, 0)), Enum.map(to_left, &elem(&1, 1))) ++
      impurity_decreases(
        right,
        Enum.map(to_right, &elem(&1, 0)),
        Enum.map(to_right, &elem(&1, 1))
      )
  end
```

Java 版・Scala 版は、葉か節かを `switch`（Java 21 のパターンマッチ）と `match` で分けました。Elixir には判別共用体がありませんが、**マップのパターンで関数節を分けられます**。`%{label: _}` は「`:label` というキーを持つマップ」にマッチするので、第 3 章で決めた「葉は `:label`、節は `:split`」という約束をそのまま関数の入口に書けます。Clojure 版が `leaf?` という述語で `if` を書いたのに比べると、約束がコードの形に出ています。

ただし **網羅性は検査されません。** どちらでもないマップを渡すと `FunctionClauseError` になります。第 3 章と同じく、木を作る側と読む側を同じ約束でそろえておきます。

```elixir
    test "一つの列だけで分ける木は、その列の重要度が一になる" do
      {x, t} = two_species()
      tree = Chapter03.fit(x, t, [:花弁幅], nil)

      assert C.tree_importances(tree, x, t, [:花弁幅]) == %{花弁幅: 1.0}
    end

    test "使わなかった列の重要度は零になる" do
      {x, t} = two_species()
      tree = Chapter03.fit(x, t, columns(), 1)
      importances = C.tree_importances(tree, x, t, columns())

      assert_in_delta Enum.sum(Map.values(importances)), 1.0, @delta
      assert Enum.count(Map.values(importances), &(&1 > 0)) == 1
    end
```

```elixir
  def tree_importances(tree, x, t, columns) do
    tree
    |> impurity_decreases(x, t)
    |> Enum.reduce(Map.new(columns, &{&1, 0.0}), fn {feature, amount}, totals ->
      Map.update!(totals, feature, &(&1 + amount))
    end)
    |> normalize()
  end
```

`Map.new(columns, &{&1, 0.0})` で「すべての列が 0」から始め、`Map.update!/3` で足し込みます。Java 版の `merge(feature, amount, Double::sum)` にそのまま対応します。使わなかった列が 0 のまま残るのが大事で、これが無いと森の平均を取るときに列が欠けます。

`Map.update/4` ではなく **`Map.update!/3`**（末尾に `!`）を使っているのは、キーが無ければ `KeyError` で落としたいからです。`impurity_decreases/3` が返す列は、必ず `columns` に含まれているはずです。もし含まれていなければ、それは木を作るときと重要度を求めるときで列がずれているということなので、静かに新しいキーを足すより落ちたほうが安全です。

### ランダムフォレストの重要度

木ごとの重要度を、木の数で割って足し合わせます。木ごとの重要度は、**その木が学習したデータ（ブートストラップ標本）と、その木が使った列** で計算します。

```elixir
  def forest_importances(%{trees: trees}, x, t, columns) do
    xs = List.to_tuple(x)
    ts = List.to_tuple(t)

    trees
    |> Enum.reduce(Map.new(columns, &{&1, 0.0}), fn tree, totals ->
      sample_x = Enum.map(tree.rows, &Map.take(elem(xs, &1), tree.columns))
      sample_t = Enum.map(tree.rows, &elem(ts, &1))

      tree.tree
      |> tree_importances(sample_x, sample_t, tree.columns)
      |> Enum.reduce(totals, fn {feature, value}, acc ->
        Map.update!(acc, feature, &(&1 + value / length(trees)))
      end)
    end)
    |> normalize()
  end
```

`tree.tree` が読みにくいのは、1 本分を表すマップの `:tree` というキーに木そのものが入っているからです。Clojure 版は分配束縛で `{tree-columns :columns :keys [rows tree]}` と別名を付けました。Elixir でも同じことはできますが、`tree.columns`・`tree.rows`・`tree.tree` とドット記法で書いたほうが「どのマップの何か」が読みやすいと判断しました。**ドット記法はキーが無ければ `KeyError` で落ちる** ので、綴りの間違いもその場で分かります。

Java 版は「木ごとに正規化してから平均し、最後にもう一度正規化する」という順で、Scala 版の記事には **正規化の順を間違えて値がずれた** 話が載っています。Elixir 版は先に Java 版の値を知っていたので、この間違いは踏みませんでした。ほかの版が照合先としてあることの効き目です。

## 10.7 モデル共通の約束

Java 版・Scala 版・Kotlin 版は、`fit` と `predict` を持つことを `interface`・`trait` で宣言し、第 3 章の決定木をアダプターで包みました。Elixir 版の「共通の約束」は次の 1 行です。

```text
分類器 = fn x, t, columns -> fn x -> ラベルの並び end end
```

宣言する場所がないので、モジュールのドキュメントに書きました。

```elixir
  分類器は「訓練データを受け取り、予測する関数を返す関数」で表す。
  Elixir には interface も protocol の宣言も要らず、第 3 章の決定木も
  Scholar のロジスティック回帰も、同じ形の関数に包むだけで同じ `score/3` で評価できる。

      分類器 = fn x, t, columns -> fn x -> ラベルの並び end end
```

第 3 章の決定木は、これだけで分類器になります。

```elixir
  def tree_trainer(max_depth) do
    fn x, t, columns ->
      tree = Chapter03.fit(x, t, columns, max_depth)

      fn x -> Chapter03.predict(tree, x) end
    end
  end
```

評価する関数も短くなります。

```elixir
  def score(trainer, split, columns) do
    predict = trainer.(split.x_train, split.t_train, columns)

    %{
      train: Chapter01.accuracy(predict.(split.x_train), split.t_train),
      test: Chapter01.accuracy(predict.(split.x_test), split.t_test)
    }
  end
```

6 つの言語で、この「共通の約束」の表し方が分かれます。

| 観点 | Python の `Protocol` | Java/Kotlin の `interface`・Scala の `trait` | F# の関数の型 | Clojure の関数 | Elixir の関数 |
|------|--------------------|-----------------------------------|-------------|--------------|-------------|
| 型が合う条件 | 同じ名前・型のメソッドを持つ | 継承を宣言している | 引数と戻り値の形が合う | 呼べれば合う | **アリティが合えば呼べる** |
| 既存の実装（第 3 章の決定木） | そのまま入れられる | アダプターで包む | 関数で包む | 関数で包む | 関数で包む |
| 約束の書き場所 | `Protocol` の宣言 | `interface`・`trait` の宣言 | 型注釈 | コメントとドキュメント文字列 | **`@moduledoc`** |
| 間違いに気付くとき | mypy を実行したとき | コンパイルのとき | コンパイルのとき | 実行したとき | **実行したとき（`BadArityError`）** |
| 「学習前に予測」の状態 | ある（テストが要る） | ある（テストが要る） | ない | ない | **ない** |

Elixir には `defprotocol` があり、`interface` に近いものを定義できます。この章で使わなかったのは、**分類器が持つ操作が 1 つ（学習する）しかない** からです。操作が 1 つなら、それは関数です。`defprotocol` は「同じ操作をデータの型ごとに切り替えたい」ときに使う道具で、`%{trees: ...}` と `%{label: ...}` のような素のマップには型としてのタグが無いので、そもそも切り替えられません（切り替えたければ構造体を定義することになります）。

引き換えに失うものもはっきりしています。引数の数を間違えた分類器を `models/0` に足しても、**実行してその行に来るまで分かりません**。ただし Elixir の無名関数は **アリティを持っている** ので、そのときのエラーは `BadArityError`（「3 引数で呼んだが 2 引数の関数だった」）という具体的なものになります。動的型付けでも、関数の形だけは実行時に検査されます。

この章のテストが「4 つの分類器をすべて同じ `score/3` に通す」形になっているのは、そのためです。

```elixir
    test "どの分類器も同じscoreで評価できる" do
      {x, t} = two_species()
      split = %{x_train: x, t_train: t, x_test: x, t_test: t}

      for trainer <- [
            C.tree_trainer(1),
            C.logistic_trainer(),
            C.forest_trainer(10, 1, nil, 0),
            C.scholar_logistic_trainer()
          ] do
        assert C.score(trainer, split, columns()) == %{train: 1.0, test: 1.0}
      end
    end
```

## 10.8 Scholar のロジスティック回帰を同じ形に包む

Scholar では `Scholar.Linear.LogisticRegression.fit/3` がモデルの構造体を返し、`predict/2` が予測します。これを分類器の形に包みます。

```elixir
  def scholar_logistic_trainer(opts \\ []) do
    fn x, t, columns ->
      classes = t |> Enum.uniq() |> Enum.sort()
      targets = Enum.map(t, fn label -> Enum.find_index(classes, &(&1 == label)) end)

      model =
        LogisticRegression.fit(
          to_tensor(x, columns),
          Nx.tensor(targets),
          Keyword.put(opts, :num_classes, length(classes))
        )

      fn x ->
        model
        |> LogisticRegression.predict(to_tensor(x, columns))
        |> Nx.to_flat_list()
        |> Enum.map(&Enum.at(classes, &1))
      end
    end
  end
```

```elixir
  defp to_tensor(x, columns) do
    Nx.tensor(Enum.map(x, fn features -> Enum.map(columns, &Map.fetch!(features, &1)) end),
      type: :f64
    )
  end
```

`model` は関数の中の束縛で、外から見えません。Java 版は `private Model<Label> model;` というフィールドが学習前は `null` になるので、`predict` で `IllegalStateException` を投げる必要がありました。Kotlin 版は型を `Model<Label>?` にしてコンパイラに任せました。**Elixir 版は「まだ学習していないモデル」を表せないので、その分岐ごと消えます。**

Scholar は **ラベルを 0 から始まる整数で受け取ります**。Tribuo が `Label` という型で文字列を持てたのに対し、Scholar の入出力はすべてテンソルです。そこで品種の名前を名前の順の番号に直して渡し、予測を名前に戻しています。この往復が分類器の関数の中に閉じているので、呼ぶ側からは自作のモデルと区別がつきません。**ライブラリの都合を、共通の約束の内側に押し込める** のが、分類器を関数にしたことの効き目です。

`Nx.tensor(targets)` に `type: :f64` を付けていないのは、ここが整数のラベルだからです。特徴量のほうには必ず付けます。

### Scholar の設定と自作との違い

`Scholar.Linear.LogisticRegression.fit/3` が受け取るオプションは `:num_classes`・`:max_iterations`・`:alpha`・`:tol` の 4 つです。既定は次のようになっています。

| 項目 | 自作 | Scholar 0.4.2 |
|------|------|--------------|
| 損失 | 交差エントロピー（ソフトマックス） | 同じ。加えて L2 正則化の項 |
| 最適化 | バッチ勾配降下法（学習率 1.0 固定）、5000 回 | バッチ勾配降下法（Armijo の直線探索で歩幅を決める）、`max_iterations` の既定 1000、`tol` で収束したら打ち切り |
| 正則化 | 無し | **`alpha` の既定が 1.0**（L2 正則化） |

**いちばん効くのは `alpha` です。** Scholar の勾配は `Nx.dot(x, [0], residuals, [0]) / num_samples + 2 * alpha * w` で、自作のバッチ勾配降下法に `2 × alpha × w` を足しただけの形です。つまり `alpha: 0.0` にすれば、自作とほぼ同じことをしています。9 節でこれを実測します。

## 10.9 実データで突き合わせる

### モデルを比べる

```elixir
  def models do
    [
      {"決定木（深さ #{@shallow_depth}）", tree_trainer(@shallow_depth)},
      {"ロジスティック回帰", logistic_trainer()},
      {"ランダムフォレスト（#{@n_estimators} 本）", forest_trainer(@n_estimators, @max_features, nil, @seed)},
      {"ランダムフォレスト（#{@n_estimators} 本・深さ #{@shallow_depth}）",
       forest_trainer(@n_estimators, @max_features, @shallow_depth, @seed)},
      {"Scholar ロジスティック回帰", scholar_logistic_trainer()}
    ]
  end
```

Java 版は順を保つために `LinkedHashMap` を使い、Scala 版・Kotlin 版・Clojure 版は組の並びにしました。Elixir 版も `[{名前, 分類器}, ...]` のリストです。第 9 章で書いたとおり、**順に意味がある結果はマップにしません**。

```bash
ML_DATA_DIR=<学習データの置き場> mix run -e 'GettingStartedMl.Chapter10.run()'
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9333	0.9556
ロジスティック回帰	0.9143	0.9111
ランダムフォレスト（100 本）	1.0000	0.9333
ランダムフォレスト（100 本・深さ 2）	0.9429	0.9556
Scholar ロジスティック回帰	0.6571	0.6444

ランダムフォレスト（100 本）の特徴量の重要度:
がく片長さ	0.1882
がく片幅	0.1265
花弁長さ	0.2713
花弁幅	0.4140
```

**自作のモデルの 8 個の正解率は、[Java 版の 10.10 節](../java/10-logistic-regression-and-ensemble.md)・[Clojure 版の 10.9 節](../clojure/10-logistic-regression-and-ensemble.md) と完全に一致しました。** 分割（第 2 章の自作の線形合同法 + Fisher-Yates）・ブートストラップ標本・列の並べ替え・ループの順をすべて Java 版にそろえたからです。乱数生成器そのものを自作するという ADR 012 の決定が、ここでいちばん効きました。

結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではない**。深さを制限しないランダムフォレスト（0.9333）は、第 3 章の深さ 2 の決定木（0.9556）より低くなりました。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。木の深さを 2 に制限した森は、決定木と同じ 0.9556 です
- **テストデータ 45 件では、1 件の違いが 0.0222 の差になる**。1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います
- **ランダムフォレストの重要度は、複数の特徴量に分散する**。木ごとに使える特徴量を 2 つに絞っているので、花弁幅を使えない木は別の特徴量で分割するためです

### 特徴量の重要度は 4 桁目でずれた

正解率が 8 個とも一致した一方で、**特徴量の重要度は Clojure 版・Java 版と一致しませんでした。**

| 特徴量 | Elixir 版 | Java 版・Clojure 版 |
|--------|----------|-------------------|
| がく片長さ | 0.1882 | 0.1882 |
| がく片幅 | **0.1265** | **0.1271** |
| 花弁長さ | **0.2713** | **0.2708** |
| 花弁幅 | 0.4140 | 0.4140 |

ずれているのは「がく片幅」と「花弁長さ」の 2 つだけで、しかも **合計はほぼ変わりません**（0.1265 + 0.2713 = 0.3978 に対し 0.1271 + 0.2708 = 0.3979）。片方から片方へ 0.0005 ぶんが移っただけです。100 本の木の平均なので、これは **1 本の木の中の 1 つの分割が、別の特徴量を選んだ** 量にあたります。

原因を測るために、森を作る過程で現れた分割の候補をすべて数えました。第 3 章の `best_split/3` は「不純度がいちばん小さい候補、同じなら列の順で前の候補」を選びます。そこで、**選ばれた候補と 2 番目の候補の不純度の差** を全部記録しました。

```text
分割の数: 1558
差が 1e-12 未満の分割: 269
差の小さい順: [{0.0, :がく片幅}, {0.0, :がく片幅}, {0.0, :がく片長さ}, ...]
```

**1558 回の分割のうち 269 回、最良と 2 番目の不純度が完全に同点です。** ブートストラップ標本は同じ行を重複して含むので、同じ不純度を与える分割がいくつも現れるのは当然です。この同点をどちらに倒すかが、重要度をどの列に付けるかを決めます。

では、なぜ同じ規則（先に見つけたほうを選ぶ）なのに結果が分かれるのか。ジニ不純度の計算に行き着きます。

```elixir
  def gini(labels) do
    total = length(labels)

    1.0 -
      (labels
       |> Enum.frequencies()
       |> Enum.map(fn {_label, n} -> n / total * (n / total) end)
       |> Enum.sum())
  end
```

`Enum.frequencies/1` が返すのは **マップ** です。マップを `Enum.map/2` でたどる順は、Elixir と Clojure で違います（Elixir はキーの項順、Clojure の小さなマップは挿入順）。浮動小数点の足し算は順によって最後の 1 ビットが変わることがあるので、**ある版では完全に同点だった 2 つの候補が、別の版では 1 ulp だけ差が付きます**。差が付けば「先に見つけたほう」ではなく「小さいほう」が選ばれ、別の列が分割に使われます。

正解率が変わらなかったのは、同点の分割はどちらを選んでも **そのデータを同じように分ける** からです。木の形が少し違っても、葉に届くラベルは変わりません。重要度だけが、どの列に手柄を付けるかで変わります。

これは「バグ」ではなく、**同点の扱いがライブラリではなく言語のコレクションの実装に依存している** という設計上の弱さです。直すなら、`gini/1` の和をラベルの順にそろえる（`Enum.sort/1` を挟む）か、`best_split/3` の同点を「列の順で前」だけでなく「閾値も小さいほう」まで決め切る必要があります。第 3 章の実装に手を入れることになるので、この章では **事実を測って記録するにとどめました**。重要度を版をまたいで比べたい場合は、まずここを疑うべきだという記録です。

### Scholar と突き合わせる

Scholar のロジスティック回帰は、既定の設定で **訓練 0.6571・テスト 0.6444** と、自作（0.9143・0.9111）よりずっと低い値になりました。オプションを変えて実測します。

| 設定 | 訓練データ | テストデータ |
|------|-----------|-------------|
| 既定（`alpha: 1.0`・`max_iterations: 1000`） | 0.6571 | 0.6444 |
| `max_iterations: 100` | 0.6571 | 0.6444 |
| `max_iterations: 5000` | 0.6571 | 0.6444 |
| **`alpha: 0.0`** | **0.9238** | **0.9111** |
| （参考）自作・1000 回 | 0.9238 | 0.9111 |
| （参考）自作・5000 回 | 0.9143 | 0.9111 |

読み取れることは 2 つです。

1. **`max_iterations` を変えても値が動きません。** `tol` による収束判定で、100 回より前に打ち切られているからです。Tribuo のロジスティック回帰が「5 エポックでは学習が足りない」という形で回数に敏感だったのとは対照的です
2. **`alpha` の既定 1.0 が効きすぎています。** L2 正則化の強さを 0 にすると、訓練 0.9238・テスト 0.9111 になりました。これは **自作を 1000 回まわしたときの値と完全に一致します**。105 件しかないデータに `alpha = 1.0` の罰則は強すぎて、重みがほとんど伸びないまま収束していたわけです

この一致はテストで固定しました。

```elixir
    @tag :data
    test "Scholarの正則化を外すと自作のロジスティック回帰と同じ正解率になる" do
      split =
        GettingStartedMl.Chapter02.prepare_iris(
          Path.join(GettingStartedMl.Dataset.dir(), "iris.csv"),
          0.3,
          0
        )

      columns = Chapter03.feature_columns()

      # Scholar の alpha（L2 正則化の強さ）の既定は 1.0。0.0 にすると、
      # 自作のバッチ勾配降下法を 1000 回繰り返したときと同じ正解率になる
      assert C.score(C.scholar_logistic_trainer(alpha: 0.0), split, columns) ==
               C.score(C.logistic_trainer(1.0, 1000), split, columns)
    end
```

**正解率のマップが `==` で完全に一致する** ところまで書けます。正解率は「当たった件数 ÷ 全件数」なので、同じ予測をすれば浮動小数点数としても同じ値になるからです。「近い」ではなく「同じ」と書けるテストは、それだけで強い主張になります。

自作のほうも繰り返し回数を変えて確かめました。1000 回では訓練 0.9238・テスト 0.9111、5000 回と 20000 回では訓練 0.9143・テスト 0.9111 でした。テストデータの正解率は変わらないので、既定の繰り返し回数は Python 版・Java 版と同じ 5000 回のままにしています。

**`run/0` の表では、あえて既定の設定のまま（0.6571・0.6444）を表示しています。** ライブラリを何も考えずに使ったときに何が起きるかが、この章でいちばん伝えたいことだからです。同じ「ロジスティック回帰」という名前でも、正則化を入れるかどうかで正解率は 0.29 も変わります。**ライブラリの結果と比べるときは、名前ではなく設定をそろえて比べます。**

第 12 章で扱う正則化を、ここで先取りして見てしまったことになります。第 12 章では `Scholar.Linear.RidgeRegression` で同じ話を回帰の側から扱います。

### 実データのテスト

表示は、第 1 章から続けている形のテストで固定します。値は実装を実データで動かして得たものなので、Red を経ていません。

```elixir
    @tag :data
    test "実行するとモデルごとの正解率と特徴量の重要度を表示する" do
      # 自作の 8 個の数値は Java 版・Clojure 版と一致する（分割も乱数も同じ手順のため）
      assert ExUnit.CaptureIO.capture_io(&C.run/0) == """
             モデル\t訓練データ\tテストデータ
             決定木（深さ 2）\t0.9333\t0.9556
```

`ExUnit.CaptureIO.capture_io/1` は、渡した関数の実行中だけ標準出力を横取りして文字列で返します。Scala 版は `Main.run(print: String => Unit)` と、表示する関数を引数で受け取る形にしました。Elixir では `IO.puts/1` の書き出し先がプロセスのグループリーダーであり、`capture_io/1` がそれを一時的に差し替えてくれるので、**`run/0` の側は何も用意しなくてもテストから出力を取れます**。プロセスごとに入出力の宛先を持てるという BEAM の仕組みが、そのままテストの道具になっています。

## 10.10 Livebook で探索する

Elixir 版では Livebook と可視化を扱いません。モデルごとの正解率のグラフ、ロジスティック回帰の損失の推移、モデル別の特徴量の重要度、森の大きさと正解率の関係は、[Python 版の 10.11 節](../python/10-logistic-regression-and-ensemble.md) と [Kotlin 版の 10.11 節](../kotlin/10-logistic-regression-and-ensemble.md) の可視化を参照してください。

`logistic_fit/5` が返すマップの `:losses`（繰り返しごとの損失のリスト）と `forest_importances/4`（特徴量ごとの重要度のマップ）は、ほかの版と同じ形のデータを返すので、同じ観点で読めます。

## 10.11 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、分類器を関数で表してまとめて評価しました。

| モデル | 自作したもの | 突き合わせたライブラリ |
|--------|------------|-------------------|
| ロジスティック回帰 | `softmax/1`・`cross_entropy/2`・`logistic_fit/5` | `Scholar.Linear.LogisticRegression`（`alpha: 0.0` で一致） |
| ランダムフォレスト | `bootstrap_sample/2`・`forest_fit/7`・`majority_vote/1` | **無し（Scholar に無い）** |
| 特徴量の重要度 | `tree_importances/4`・`forest_importances/4` | 無し |

Elixir 版ならではの学びは 6 つです。

1. **分類器は関数でよい** — `interface`・`trait`・`defprotocol`・アダプターを作らず、`fn x, t, columns -> fn x -> ラベル end end` という形だけを約束にした。第 3 章の決定木も Scholar のモデルも、包む関数を 1 つ書くだけで同じ `score/3` に通せる。引き換えに、約束は `@moduledoc` にしか書けず、違反は実行するまで分からない（ただし無名関数はアリティを持つので `BadArityError` という具体的な失敗になる）
2. **Nx を使わないほうが速かった** — 勾配降下法を `Nx.BinaryBackend` で書くと 5000 回で約 14 秒、素のリストで書くと約 2 秒。EXLA を入れない小さな行列では、テンソルのライブラリは費用のほうが大きい。第 9 章の `Nx.LinAlg.solve/2` のように「自分で書くと面倒なアルゴリズム」にだけ使う
3. **可変の乱数生成器は「状態を返す関数」になる** — `java.util.Random` を回す代わりに、線形合同法の状態を `Enum.map_reduce/3` で持ち回った。おまけに `Enum` が正格評価なので、Clojure 版が踏んだ「遅延シーケンスで乱数の順が狂う」落とし穴が無い
4. **無限大も NaN も無い** — `:math.exp/1` も `:math.log/1` も、あふれたら `ArithmeticError` で落ちる。JVM 版が「`NaN` にならないこと」を確かめたテストは、Elixir では「落ちずに浮動小数点数が返ること」になった
5. **値だから比べられる** — 木も森も素のマップなので、「同じシードなら同じ森になる」が `==` の 1 行で書ける。`equals` を書く必要も、予測で代用する必要もない
6. **手順をそろえても、同点の扱いまでは合わない** — 乱数・並べ替え・ループの順をそろえたので正解率は 8 個とも Java 版と一致した。しかし 1558 回の分割のうち 269 回が完全な同点で、そこを `Enum.frequencies/1` が返すマップの走査順が左右するため、特徴量の重要度は 4 桁目でずれた。**数値を版間で比べるときは、同点をどう倒すかまで決めておく必要がある**

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
