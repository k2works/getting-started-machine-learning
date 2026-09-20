---
type: Article
title: "第 11 章: 評価指標と交差検証"
description: "混同行列から適合率・再現率・F 値を求め、評価関数とモデルを名前付きデリゲート Metric<T>・Model<T> として差し替える。K 分割交差検証のスコアを反復子（yield return）で必要な分だけ計算し、同じ正解と予測を ML.NET の評価に渡して自作の指標と突き合わせる。"
tags: [article,getting-start-ml,csharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T01:38:41Z }
---

# 第 11 章: 評価指標と交差検証

## 11.1 はじめに

ここまでの章では、分類の良し悪しを正解率で、回帰の良し悪しを決定係数などで測ってきました。この章では、評価の道具を 2 つ加えます。

- **評価指標**: 正解率だけでは見えない「正例をどれだけ拾えたか」を、混同行列・適合率・再現率・F 値で測る
- **K 分割交差検証**: 1 回だけの分割に頼らず、データを K 個に分けてテストデータを入れ替えながら評価する

[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md)・[Kotlin 版の第 11 章](../kotlin/11-evaluation-metrics-and-cross-validation.md)・[F# 版の第 11 章](../fsharp/11-evaluation-metrics-and-cross-validation.md) と同じ TODO リストで進めます。C# 版では、次の 3 点に注目してください。

- 評価関数とモデルを **名前付きデリゲート**（`Metric<T>`・`Model<T>`）で表し、引数として差し替える
- 交差検証のスコアを **反復子**（`yield return`）で書き、取り出した分だけ学習と評価を行う
- 同じ正解と予測を ML.NET の評価に渡し、自作の指標と **突き合わせる**

この章は第 3 章の決定木と、第 7 章の線形回帰・評価指標・`Matrix` をそのまま使います。

## 11.2 正解率だけでは足りない理由

タイタニック号の乗客データ（`Survived.csv`）では、乗客の約 6 割が亡くなっています。「全員死亡」と予測するだけで、正解率は約 6 割になります。けれども、このモデルは生存者を 1 人も見つけられません。

生存を **正例** として、予測の当たり外れを 4 つに分けて数えたものが **混同行列** です。

| | 生存と予測 | 死亡と予測 |
|--|-----------|-----------|
| 実際は生存 | TP（真陽性） | FN（偽陰性） |
| 実際は死亡 | FP（偽陽性） | TN（真陰性） |

混同行列から、次の指標を求めます。

- **適合率** = TP / (TP + FP): 生存と予測したうち、本当に生存していた割合
- **再現率** = TP / (TP + FN): 本当に生存していたうち、生存と予測できた割合
- **F 値**: 適合率と再現率の調和平均。どちらかが低いと大きく下がる

「全員死亡」のモデルは、TP が 0 なので再現率が 0 になります。この性質を、まずテストで確かめます。

```csharp
// tests/MachineLearning.Tests/Chapter11/ModelsTests.cs
[Fact(DisplayName = "常に同じラベルを予測するモデルは正解率が高くても再現率は 0 になる")]
public void ConstantModelHasNoRecall()
{
    IReadOnlyList<Features> x =
        [.. FiveValues.Select(value => new Features(Column, [value]))];
    IReadOnlyList<string> t = ["0", "0", "0", "0", "1"];

    var predicted = Models.Constant("0")(x, t)(x);

    Assert.Equal(0.8, MachineLearning.Chapter01.KinokoTakenoko.Accuracy(predicted, t), 12);
    Assert.Equal(0.0, Metrics.Recall(Metrics.Confusion("1", t, predicted)), 12);
}
```

5 件のうち 4 件が `"0"` なら、`"0"` と言い続けるだけで正解率は 0.8 です。それでも再現率は 0 です。テストの値はすべて架空です。

## 11.3 TODO リストの作成

**TODO リスト**:

- [ ] 混同行列を数える
- [ ] 適合率・再現率・F 値を求める
  - [ ] 分母が 0 のときは 0 にする
- [ ] 回帰の評価指標（MSE・RMSE・MAE）を求める
- [ ] K 分割交差検証の分け方を作る
- [ ] 評価関数を差し替えて交差検証する
  - [ ] 必要な分だけ学習する
- [ ] ML.NET の評価と突き合わせる
- [ ] 実データで評価する

## 11.4 混同行列を数える

```csharp
// tests/MachineLearning.Tests/Chapter11/MetricsTests.cs
[Fact(DisplayName = "正例と負例の予測の当たり外れを数える")]
public void Counts()
{
    Assert.Equal(new ConfusionMatrix(2, 1, 1, 1), Metrics.Confusion(1, [1, 1, 1, 0, 0], [1, 1, 0, 1, 0]));
}

[Fact(DisplayName = "どちらのラベルを正例とするかで数え方が変わる")]
public void PositiveMatters()
{
    Assert.Equal(
        new ConfusionMatrix(2, 2, 1, 1), Metrics.Confusion(0, [1, 1, 1, 0, 0, 0], [1, 0, 0, 0, 0, 1]));
}

[Fact(DisplayName = "正解と予測の件数が違えばエラーになる")]
public void Mismatched()
{
    Assert.Throws<ArgumentException>(() => Metrics.Confusion(1, [1, 0, 1], [1, 0]));
}
```

```csharp
// src/MachineLearning/Chapter11/Metrics.cs
/// <summary>混同行列。正例についての当たり外れの件数。</summary>
public readonly record struct ConfusionMatrix(
    int TruePositive, int FalsePositive, int FalseNegative, int TrueNegative);

/// <summary>正解と予測を「実際は正例か、正例と予測したか」の組にして、組ごとに数える。</summary>
public static ConfusionMatrix Confusion<T>(T positive, IReadOnlyList<T> actual, IReadOnlyList<T> predicted)
{
    ArgumentNullException.ThrowIfNull(actual);
    ArgumentNullException.ThrowIfNull(predicted);
    if (actual.Count != predicted.Count)
    {
        throw new ArgumentException($"正解 {actual.Count} 件と予測 {predicted.Count} 件の数が違います", nameof(predicted));
    }

    var comparer = EqualityComparer<T>.Default;
    var outcomes = actual
        .Zip(predicted, (a, p) => (Actual: comparer.Equals(a, positive), Predicted: comparer.Equals(p, positive)))
        .ToList();
    int Count(bool isActual, bool isPredicted) =>
        outcomes.Count(outcome => outcome.Actual == isActual && outcome.Predicted == isPredicted);

    return new ConfusionMatrix(Count(true, true), Count(false, true), Count(true, false), Count(false, false));
}
```

- 混同行列は `readonly record struct` にしました。4 つの `int` だけを持つ小さな値なので、参照型にする理由がありません。レコードなので `Assert.Equal` が中身で比べてくれます
- F# 版はフィールド名を `TP`・`FP`・`FN`・`TN` にしていますが、C# では `TruePositive` のように綴ります。2 文字の大文字の名前は、読み手にも静的解析にも意図が伝わりにくいためです
- ラベルの比較は `EqualityComparer<T>.Default` を使います。F# の `=` は型引数に制約 `equality` が付いた状態で構造的に比べますが、C# の `==` は型引数 `T` に対しては使えません（参照の比較になるか、そもそもコンパイルできません）。`EqualityComparer<T>.Default` なら、`string` でも `int` でもレコードでも、その型の `Equals` で比べられます
- F# 版は `List.map2` が件数の違いを例外にしてくれますが、LINQ の `Zip` は短いほうに黙って合わせます。件数の確認は自分で書く必要があります。これはテスト（`Mismatched`）で固定しました
- `int Count(...)` はメソッドの中に書いたローカル関数です。`outcomes` をそのまま見に行けるので、引数で渡し回す必要がありません

## 11.5 適合率・再現率・F 値

```csharp
private static readonly ConfusionMatrix Cm = new(3, 1, 2, 4);

[Fact(DisplayName = "適合率は正例と予測したうち本当に正例だった割合")]
public void Precision()
{
    Assert.Equal(0.75, Metrics.Precision(Cm), 12);
}

[Fact(DisplayName = "正例を 1 件も当てられなければ適合率と再現率と F 値は 0")]
public void ZeroDenominator()
{
    var missed = new ConfusionMatrix(0, 0, 3, 5);

    Assert.Equal(
        (0.0, 0.0, 0.0), (Metrics.Precision(missed), Metrics.Recall(missed), Metrics.F1Score(missed)));
}
```

```csharp
/// <summary>適合率。正例と予測したうち、本当に正例だった割合。</summary>
public static double Precision(ConfusionMatrix cm) =>
    Ratio(cm.TruePositive, cm.TruePositive + cm.FalsePositive);

/// <summary>再現率。本当の正例のうち、正例と予測できた割合。</summary>
public static double Recall(ConfusionMatrix cm) =>
    Ratio(cm.TruePositive, cm.TruePositive + cm.FalseNegative);

/// <summary>F 値。適合率と再現率の調和平均。</summary>
public static double F1Score(ConfusionMatrix cm)
{
    var precision = Precision(cm);
    var recall = Recall(cm);
    return Ratio(2.0 * precision * recall, precision + recall);
}

/// <summary>割り算。分母が 0 なら NaN にせず 0 を返す（scikit-learn の既定と同じ）。</summary>
private static double Ratio(double numerator, double denominator) =>
    denominator == 0.0 ? 0.0 : numerator / denominator;
```

正例と 1 件も予測しないと、適合率の分母（TP + FP）が 0 になります。`double` の `0.0 / 0.0` は例外にならず `NaN`（非数）になり、そのまま平均に混ざると結果がすべて `NaN` になります。`Ratio` で分母が 0 のときは 0 を返し、scikit-learn の既定と同じ扱いにします。

3 つの指標を 1 つのテストで確かめるときは、値組（タプル）にしてまとめて比べています。C# の値組は成分で比較されるので、F# 版と同じ書き方ができます。

## 11.6 回帰の評価指標

回帰では、予測と正解の差（誤差）の大きさを測ります。MAE と RMSE は第 7 章で作ったので、この章で足すのは MSE だけです。

```csharp
/// <summary>平均二乗誤差（MSE）。平方根をとった RMSE と MAE は第 7 章の RegressionMetrics にある。</summary>
public static double MeanSquaredError(IReadOnlyList<double> actual, IReadOnlyList<double> predicted)
{
    var rmse = Chapter07.RegressionMetrics.RootMeanSquaredError(actual, predicted);
    return rmse * rmse;
}
```

F# 版は第 11 章に MSE・RMSE・MAE の 3 つを並べて書きました。C# 版では、同じ式を 2 か所に持たないように、すでにある RMSE を 2 乗して MSE にしています。件数の食い違いの検査も、第 7 章の `Residuals` がそのまま効きます。

2 乗してから平均する RMSE は、大きく外れた予測を重く数えます。

```csharp
[Fact(DisplayName = "大きく外れた予測があると RMSE は MAE より大きく増える")]
public void RmsePenalizesOutliers()
{
    double[] actual = [3.0, 5.0, 8.0, 10.0];
    double[] predicted = [2.0, 5.0, 10.0, 30.0];

    Assert.Equal(Math.Sqrt(101.25), RegressionMetrics.RootMeanSquaredError(actual, predicted), 12);
    Assert.Equal(5.75, RegressionMetrics.MeanAbsoluteError(actual, predicted), 12);
}
```

1 件だけ 20 外れた予測があると、MAE は 5.75 ですが、RMSE は約 10.06 になります。外れ値に敏感な評価をしたいときは RMSE を、そうでないときは MAE を選びます。

## 11.7 K 分割交差検証

### なぜ分割を入れ替えるのか

訓練データとテストデータに 1 回だけ分けて評価すると、たまたまどの行がテストデータに入ったかで結果が揺れます。**K 分割交差検証** は、データを K 個のまとまりに分け、それぞれを 1 回ずつテストデータにして K 回評価し、その平均をとります。すべての行が、ちょうど 1 回だけテストデータになります。

```csharp
// tests/MachineLearning.Tests/Chapter11/CrossValidationTests.cs
[Fact(DisplayName = "データを k 個のテストデータにほぼ均等に分ける")]
public void SplitsEvenly()
{
    Assert.Equal<IReadOnlyList<int>>([4, 3, 3], TestSizes(CrossValidation.KFold(3, 0, 10)));
}

[Fact(DisplayName = "どの行もちょうど一度だけテストデータになる")]
public void EachRowTestedOnce()
{
    var folds = CrossValidation.KFold(3, 0, 10);

    Assert.Equal<IReadOnlyList<int>>(
        [.. Enumerable.Range(0, 10)], [.. folds.SelectMany(fold => fold.Test).Order()]);
}

[Fact(DisplayName = "各分割の訓練データはテストデータ以外のすべての行")]
public void TrainIsComplement()
{
    foreach (var fold in CrossValidation.KFold(3, 0, 10))
    {
        Assert.Empty(fold.Train.Intersect(fold.Test));
        Assert.Equal<IReadOnlyList<int>>(
            [.. Enumerable.Range(0, 10)], [.. fold.Train.Concat(fold.Test).Order()]);
    }
}
```

同じシードなら同じ分け方になること、分割数がデータの件数を超えればエラーになることもテストしています。

```csharp
// src/MachineLearning/Chapter11/CrossValidation.cs
/// <summary>1 回分の分け方。訓練データとテストデータの行番号。</summary>
public sealed record Fold(IReadOnlyList<int> Train, IReadOnlyList<int> Test);

/// <summary>行番号をシードで並べ替えてから nSplits 個に分け、それぞれをテストデータ、残りを訓練データにする。</summary>
public static IReadOnlyList<Fold> KFold(int nSplits, int seed, int nSamples)
{
    if (nSplits < 2 || nSplits > nSamples)
    {
        throw new ArgumentOutOfRangeException(nameof(nSplits), $"分割数は 2 以上 {nSamples} 以下にしてください");
    }

    var positions = Preprocessing.Shuffle([.. Enumerable.Range(0, nSamples)], seed);
    return [.. SplitInto(positions, nSplits).Select(test =>
        new Fold([.. positions.Except(test)], test))];
}

/// <summary>
/// ほぼ同じ長さの count 個に分ける。割り切れないときは先頭のまとまりから 1 件ずつ多くする
/// （F# の List.splitInto と同じ分け方）。
/// </summary>
private static IEnumerable<IReadOnlyList<int>> SplitInto(IReadOnlyList<int> positions, int count)
{
    var size = positions.Count / count;
    var remainder = positions.Count % count;
    var taken = 0;
    for (var i = 0; i < count; i++)
    {
        var length = size + (i < remainder ? 1 : 0);
        yield return [.. positions.Skip(taken).Take(length)];
        taken += length;
    }
}
```

- 並べ替えには、第 2 章の `Preprocessing.Shuffle` をそのまま使います。F# 版と同じ `System.Random(seed)` と同じ Fisher–Yates なので、同じシードなら同じ並びになります
- F# には `List.splitInto` がありますが、LINQ には「ほぼ均等な k 個に分ける」演算がありません（`Chunk` は長さを指定して分ける別の演算です）。余りを先頭のまとまりから 1 件ずつ配る `SplitInto` を自分で書き、F# と同じ分け方（10 件を 3 つなら 4・3・3）にしました
- `Except` は F# の `List.except` と同じく、元の順を保って要素を取り除きます
- 分け方は **行番号** で表します。特徴量や正解ラベルの型によらずに、同じ分け方を使い回せます

## 11.8 評価関数を関数として渡す

### モデルと評価関数をデリゲートで表す

交差検証の手順は、「分割ごとに、訓練データで学習し、テストデータを予測して採点する」です。学習の方法（モデル）と採点の方法（評価関数）を引数で受け取れば、どのモデルとどの指標の組み合わせにも同じ関数が使えます。

```csharp
// src/MachineLearning/Chapter11/Metrics.cs
/// <summary>評価関数。正解と予測を受け取ってスコアを返す。</summary>
public delegate double Metric<T>(IReadOnlyList<T> actual, IReadOnlyList<T> predicted);

// src/MachineLearning/Chapter11/CrossValidation.cs
/// <summary>モデル。訓練データ（特徴量と正解）を受け取り、予測する関数を返す関数。</summary>
public delegate Func<IReadOnlyList<Features>, IReadOnlyList<T>> Model<T>(
    IReadOnlyList<Features> x, IReadOnlyList<T> t);
```

F# 版は `type Metric<'T> = 'T list -> 'T list -> float` という **型の省略形** で関数の型に名前を付けました。C# にも `using` の別名がありますが、型引数を取る別名は書けません。代わりに **名前付きデリゲート** を宣言します。`Func<IReadOnlyList<T>, IReadOnlyList<T>, double>` と中身は同じで、呼び出し側では区別されませんが、名前と XML コメントが付く分だけ読みやすくなります。

`Model<T>` は、「学習して、予測する関数を返す関数」です。第 3 章の `Fit` と `Predict` をつなげた形です。既存の章のクラスを、この形に合わせます。

```csharp
// src/MachineLearning/Chapter11/Models.cs
/// <summary>深さの上限を決めた、第 3 章の決定木。</summary>
public static Model<string> DecisionTree(int maxDepth) =>
    (x, t) =>
    {
        var tree = Chapter03.DecisionTree.WithMaxDepth(maxDepth).Fit(x, t);
        return tree.Predict;
    };

/// <summary>第 7 章の、正規方程式で解く線形回帰。</summary>
public static Model<double> LinearRegression =>
    (x, t) =>
    {
        var model = Chapter07.LinearRegression.Fit(x, t);
        return newX => Chapter07.LinearRegression.Predict(model, newX);
    };
```

決定木は学習した状態をオブジェクトが持つので、`tree.Predict` という **メソッドグループ** をそのまま返せます。線形回帰は学習結果と予測が別（`LinearModel` と `static Predict`）なので、学習結果を捕まえるラムダ式にしています。F# 版は第 10 章の `ofModel` で 2 つの関数を組み合わせましたが、C# ではこの 2 行で足ります。

### 交差検証を反復子で書く

```csharp
/// <summary>
/// 分割ごとに、訓練データで学習し、テストデータの予測を評価関数で採点する。
/// 反復子なので、スコアは取り出すときに初めて計算する（取り出した分だけ学習する）。
/// </summary>
public static IEnumerable<double> CrossValidate<T>(
    Model<T> model,
    Metric<T> metric,
    IReadOnlyList<Fold> folds,
    IReadOnlyList<Features> x,
    IReadOnlyList<T> t)
{
    ArgumentNullException.ThrowIfNull(model);
    ArgumentNullException.ThrowIfNull(metric);
    ArgumentNullException.ThrowIfNull(folds);
    foreach (var fold in folds)
    {
        var predict = model(Pick(x, fold.Train), Pick(t, fold.Train));
        yield return metric(Pick(t, fold.Test), predict(Pick(x, fold.Test)));
    }
}

/// <summary>行番号の順に要素を取り出す。</summary>
public static IReadOnlyList<TItem> Pick<TItem>(IReadOnlyList<TItem> items, IReadOnlyList<int> rows)
{
    ArgumentNullException.ThrowIfNull(items);
    ArgumentNullException.ThrowIfNull(rows);
    return [.. rows.Select(row => items[row])];
}
```

`yield return` を含むメソッドは **反復子** になり、`IEnumerable<double>` を返します。中身は値を取り出すときに初めて実行されます（遅延評価）。F# の `seq { ... }` に当たります。

テスト用に、訓練データの正解の平均を常に予測するモデルを用意し、評価関数を差し替えて確かめます。

```csharp
[Fact(DisplayName = "分割ごとに訓練データで学習してテストデータを評価する")]
public void EvaluatesEachFold()
{
    // 訓練データ [1, 3] の平均 2 をテストデータ [5, 7] に当てると誤差は 3 と 5 で MAE は 4
    Assert.Equal<IReadOnlyList<double>>(
        [4.0, 4.0],
        [.. CrossValidation.CrossValidate(
            Models.Mean, RegressionMetrics.MeanAbsoluteError, Folds, X, T)]);
}

[Fact(DisplayName = "評価関数を差し替えると別の指標で評価する")]
public void SwapsMetric()
{
    Assert.Equal<IReadOnlyList<double>>(
        [17.0, 17.0],
        [.. CrossValidation.CrossValidate(Models.Mean, Metrics.MeanSquaredError, Folds, X, T)]);
}
```

`RegressionMetrics.MeanAbsoluteError` は `Metric<double>` として宣言されていませんが、引数と戻り値の形が合うので、そのままメソッドグループとして渡せます。C# のデリゲートは名前ではなく形で合わせます。

### 必要な分だけ学習する

遅延評価の効果を、学習した回数を数えるモデルで確かめます。

```csharp
[Fact(DisplayName = "最初の分割のスコアだけを取り出すなら学習は 1 回で済む")]
public void LazyTraining()
{
    var trained = 0;
    Model<double> counting = (x, t) =>
    {
        trained++;
        return Models.Mean(x, t);
    };

    _ = CrossValidation
        .CrossValidate(counting, RegressionMetrics.MeanAbsoluteError, Folds, X, T)
        .First();

    Assert.Equal(1, trained);
}

[Fact(DisplayName = "同じ反復子を 2 回たどると学習も 2 回ずつ行われる")]
public void RepeatedEnumerationRetrains()
{
    var trained = 0;
    Model<double> counting = (x, t) =>
    {
        trained++;
        return Models.Mean(x, t);
    };
    var scores = CrossValidation.CrossValidate(
        counting, RegressionMetrics.MeanAbsoluteError, Folds, X, T);

    _ = scores.ToList();
    _ = scores.ToList();

    Assert.Equal(4, trained);
}
```

`First()` で最初のスコアだけを取り出すと、学習は 1 回しか行われません。一方、同じ `IEnumerable<double>` を 2 回たどると、2 分割ぶんの学習が 2 回ずつ、合わせて 4 回行われます。反復子は結果を覚えていないためです。何度も使うなら `ToList()` で一度だけ実体化します（F# 版の `Seq.cache` に当たる役割です）。

`trained` はラムダ式に捕まえられたローカル変数です。F# では `let mutable` を明示しますが、C# のローカル変数は初めから書き換えられます。「数えるためだけに書き換える」意図は、テストの名前で示しています。

### 混同行列の指標を評価関数に変える

適合率などは混同行列から求めるので、形は `Func<ConfusionMatrix, double>` です。これを、正解と予測から求める `Metric<T>` に変える関数を用意します。

```csharp
/// <summary>混同行列から求める指標と正例のラベルから、正解と予測から求める評価関数を作る。</summary>
public static Metric<T> ClassificationMetric<T>(Func<ConfusionMatrix, double> score, T positive)
{
    ArgumentNullException.ThrowIfNull(score);
    return (actual, predicted) => score(Confusion(positive, actual, predicted));
}
```

`Metrics.ClassificationMetric(Metrics.Precision, "1")` は、「生存（`"1"`）を正例とした適合率」を求める評価関数になります。関数を受け取って関数を返す **高階関数** で、指標と正例の組み合わせを自由に作れます。

## 11.9 ML.NET の評価と突き合わせる

自作の指標が正しいかを、ML.NET の評価で確かめます。ML.NET の評価は、正解（`Label`）・予測（`PredictedLabel`）・得点（`Score`）の列を持つデータを受け取ります。同じ正解と予測を渡して、結果を比べます。

```csharp
// tests/MachineLearning.Tests/Chapter11/MlNetEvaluationTests.cs
private static readonly string[] Actual = ["1", "1", "1", "0", "0", "0", "0", "1"];
private static readonly string[] Predicted = ["1", "1", "0", "1", "0", "0", "0", "0"];

[Fact(DisplayName = "2 値分類の適合率・再現率・F 値が ML.NET の評価と一致する")]
public void BinaryMatches()
{
    var cm = Metrics.Confusion("1", Actual, Predicted);

    var library = MlNetEvaluation.EvaluateBinary("1", Actual, Predicted);

    Assert.Equal(Metrics.Precision(cm), library.PositivePrecision, 12);
    Assert.Equal(Metrics.Recall(cm), library.PositiveRecall, 12);
    Assert.Equal(Metrics.F1Score(cm), library.F1Score, 12);
}

[Fact(DisplayName = "回帰の MAE・RMSE・MSE が ML.NET の評価と一致する")]
public void RegressionMatches()
{
    double[] actual = [1.0, 2.0, 4.0, 8.0];
    double[] predicted = [1.5, 2.0, 3.0, 10.0];

    var library = MlNetEvaluation.EvaluateRegression(actual, predicted);

    Assert.Equal(RegressionMetrics.MeanAbsoluteError(actual, predicted), library.MeanAbsoluteError, 9);
    Assert.Equal(RegressionMetrics.RootMeanSquaredError(actual, predicted), library.RootMeanSquaredError, 9);
    Assert.Equal(Metrics.MeanSquaredError(actual, predicted), library.MeanSquaredError, 9);
}
```

```csharp
// src/MachineLearning/Chapter11/MlNetEvaluation.cs
/// <summary>2 値分類の評価に渡す 1 行。ML.NET は正解（Label）・予測（PredictedLabel）・得点（Score）の列を読む。</summary>
public sealed class BinaryOutcome
{
    public bool Label { get; set; }

    public bool PredictedLabel { get; set; }

    public float Score { get; set; }
}

/// <summary>
/// 正解と予測を ML.NET の 2 値分類の評価に渡す。
/// 自作の決定木はラベルしか返さないので、確率を使わない評価（NonCalibrated）にする。
/// </summary>
public static BinaryClassificationMetrics EvaluateBinary<T>(
    T positive, IReadOnlyList<T> actual, IReadOnlyList<T> predicted)
{
    ArgumentNullException.ThrowIfNull(actual);
    ArgumentNullException.ThrowIfNull(predicted);
    var comparer = EqualityComparer<T>.Default;
    var context = new MLContext(seed: 0);
    var rows = actual.Zip(predicted, (a, p) => new BinaryOutcome
    {
        Label = comparer.Equals(a, positive),
        PredictedLabel = comparer.Equals(p, positive),
        Score = comparer.Equals(p, positive) ? 1f : -1f,
    }).ToList();
    return context.BinaryClassification.EvaluateNonCalibrated(context.Data.LoadFromEnumerable(rows));
}
```

- 第 3・7 章と同じく、ML.NET に渡す行は、書き換えられるプロパティを持つクラスにします。F# 版は `[<CLIMutable>]` のレコードでしたが、C# では普通のクラスがその形です
- 自作の決定木はラベルしか返さず、確率（得点）を持ちません。確率を使わない評価 `EvaluateNonCalibrated` を使い、得点には予測に合わせて 1 か −1 を入れます
- 回帰の評価も同じ形です（`Regression.Evaluate`。予測は `Score` の列に入れます）。ML.NET の戻り値の型も `RegressionMetrics` という名前なので、第 7 章の自作のクラスと衝突します。片方を完全修飾名（`Microsoft.ML.Data.RegressionMetrics`）で書いて区別しました

```text
テストの実行の概要: 成功!
```

適合率・再現率・F 値は小数第 12 位まで、MAE・RMSE・MSE は小数第 9 位まで一致しました。回帰の桁が少ないのは、ML.NET が値を `float`（単精度）で受け取るためです。

ML.NET には交差検証の関数（`CrossValidate`）もありますが、行の分け方は ML.NET が自分で決めます。自作の `KFold` と同じ分け方を渡す手段が無いので、交差検証の平均そのものは突き合わせず、1 回分の予測の採点が一致することを確かめるにとどめます。

## 11.10 実データで評価する

### CSV を読み込む

F# 版は型プロバイダで CSV を読みますが、C# 版には型プロバイダがありません。第 2 章の `Table` と `Row` をそのまま使います。

```csharp
// src/MachineLearning/Chapter11/Datasets.cs
/// <summary>Survived.csv の特徴量の列。F# 版の Map のキーの順にそろえる。</summary>
public static readonly string[] SurvivedColumns = ["Age", "Pclass", "male"];

/// <summary>客室クラス・年齢・男性かどうかを特徴量にし、生存（"0" か "1"）を正解ラベルにする。</summary>
public static (IReadOnlyList<Features> X, IReadOnlyList<string> T) PrepareSurvived(string csvFile)
{
    var table = Table.Load(csvFile);
    var values = table.Rows
        .Select(row => new double?[]
        {
            row.Number("Age"),
            row.Number("Pclass"),
            string.Equals(row.Text("Sex"), "male", StringComparison.Ordinal) ? 1.0 : 0.0,
        })
        .ToList();
    return (FillWithColumnMeans(SurvivedColumns, values), [.. table.Rows.Select(row => row.Text("Survived"))]);
}
```

列の順を `Age`・`Pclass`・`male` にしているのには理由があります。F# 版の特徴量は `Map<string, float>` で、キーの順（`Age` < `Pclass` < `male`）に並びます。第 3 章の決定木は、不純度が同じ分割が複数あれば **先に見つけたほう** を選ぶので、列の順が結果を変えることがあります。F# 版と数値を突き合わせるために、列の順もそろえました。

欠損値の補完は、この章の関心（評価）に集中するために、他の版と同じく簡略化しています。交差検証の前に、データ全体の平均値で補完しています。厳密には分割ごとに訓練データの平均値で補完すべきです（第 2 章で見たデータリーク）。

### 交差検証の実験

```csharp
// src/MachineLearning/Chapter11/Experiments.cs
/// <summary>Survived を評価する指標（名前と評価関数の組）。</summary>
public static IReadOnlyList<KeyValuePair<string, Metric<string>>> SurvivedMetrics =>
[
    KeyValuePair.Create<string, Metric<string>>(
        "正解率", (actual, predicted) => Chapter01.KinokoTakenoko.Accuracy(predicted, actual)),
    KeyValuePair.Create("適合率", Metrics.ClassificationMetric(Metrics.Precision, Survived)),
    KeyValuePair.Create("再現率", Metrics.ClassificationMetric(Metrics.Recall, Survived)),
    KeyValuePair.Create("F値", Metrics.ClassificationMetric(Metrics.F1Score, Survived)),
];

/// <summary>評価関数ごとに、K 分割交差検証のスコアの平均を求めて、名前と組にする。</summary>
public static IReadOnlyList<KeyValuePair<string, double>> Evaluate<T>(
    int nSplits,
    int seed,
    Model<T> model,
    IReadOnlyList<KeyValuePair<string, Metric<T>>> metrics,
    IReadOnlyList<Features> x,
    IReadOnlyList<T> t)
{
    ArgumentNullException.ThrowIfNull(metrics);
    ArgumentNullException.ThrowIfNull(x);
    var folds = CrossValidation.KFold(nSplits, seed, x.Count);
    return [.. metrics.Select(metric => KeyValuePair.Create(
        metric.Key,
        CrossValidation.CrossValidate(model, metric.Value, folds, x, t).Average()))];
}
```

- 評価関数は「名前と評価関数」の組のリストで渡します。F# 版はタプルのリストでしたが、C# では `KeyValuePair` にすると `foreach (var (name, score) in ...)` で分解できて読みやすくなります
- 第 1 章の `Accuracy` は引数の順が（予測、正解）なので、`Metric<string>` の順に合わせるラムダ式で包みます。正解率はどちらの順でも同じ値ですが、型が合わなければ渡せません
- 分け方（`folds`）は 1 回だけ作り、すべての評価関数で同じ分け方を使います

```bash
dotnet run --project src/MachineLearning -- chapter11
```

```text
Survived（決定木、5 分割交差検証の平均）
  正解率: 0.7800
  適合率: 0.8114
  再現率: 0.5736
  F値: 0.6587
cinema（線形回帰、5 分割交差検証の平均）
  RMSE: 392.75
  MAE: 314.71
```

Survived の決定木は、正解率 0.78 に対して再現率が 0.57 です。生存と予測したときの的中率（適合率 0.81）は高いものの、生存者の 4 割あまりを見逃しています。正解率だけを見ていては気づけない性質です。

### F# 版と一致するか

6 つの値はすべて、[F# 版の第 11 章](../fsharp/11-evaluation-metrics-and-cross-validation.md) と表示の桁まで一致しました。

| 指標 | C# 版 | F# 版 |
|------|------|------|
| 正解率 | 0.7800 | 0.7800 |
| 適合率 | 0.8114 | 0.8114 |
| 再現率 | 0.5736 | 0.5736 |
| F 値 | 0.6587 | 0.6587 |
| RMSE（cinema） | 392.75 | 392.75 |
| MAE（cinema） | 314.71 | 314.71 |

一致した理由は 3 つあります。分け方が同じ（同じシャッフルと同じ `SplitInto`）であること、決定木が第 3 章で F# 版と同じ木を作ること、線形回帰が正規方程式で一度に解く方法であることです。

第 7 章では、ライブラリ（SDCA）との突き合わせだけが一致しませんでした。この章の線形回帰は自作のままなので、F# 版が使った FSharp.Stats の最小二乗法とも同じ解になります。

分割ごとの揺れも一致しました。

| 指標 | 最小 | 最大 |
|------|------|------|
| 正解率 | 0.753 | 0.798 |
| 再現率 | 0.415 | 0.746 |

正解率は分割によって 0.05 程度しか変わりませんが、再現率は 0.4 から 0.75 まで大きく揺れます。1 回だけの分割で評価すると、再現率は偶然に大きく左右されます。交差検証で平均をとる理由がここにあります。この 4 つの値もテストで固定しています。

```csharp
[Fact(DisplayName = "分割ごとの再現率は正解率よりも大きく揺れる")]
public void RecallVariesMoreThanAccuracy()
{
    RequireData(this.survivedCsv);
    var (x, t) = Datasets.PrepareSurvived(this.survivedCsv);
    var folds = CrossValidation.KFold(Experiments.NSplits, Experiments.Seed, x.Count);
    var model = Models.DecisionTree(Experiments.TreeDepth);

    var accuracy = Spread(model, (actual, predicted) =>
        MachineLearning.Chapter01.KinokoTakenoko.Accuracy(predicted, actual), folds, x, t);
    var recall = Spread(
        model, Metrics.ClassificationMetric(Metrics.Recall, Experiments.Survived), folds, x, t);

    Assert.Equal((0.753, 0.798), (Round(accuracy.Min), Round(accuracy.Max)));
    Assert.Equal((0.415, 0.746), (Round(recall.Min), Round(recall.Max)));
}
```

表示をまるごと比べるテストも、第 2・3・7 章と同じように残しています。学習データが無ければ `Assert.SkipUnless` でスキップします。

### 可視化

混同行列のヒートマップや、分割ごとのスコアの散布図は、[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md)・[Kotlin 版の第 11 章](../kotlin/11-evaluation-metrics-and-cross-validation.md) を参照してください。C# 版では、上の表のとおり、分割ごとの最小と最大をテストで固定する形にしています。

## 11.11 型に阻まれた 2 つの場面

この章で実際にビルドを止められた 2 つを記録しておきます。

1 つ目は、同じ名前の型の衝突です。

```text
error CS0104: 'Features' は、'MachineLearning.Chapter01.Features' と 'MachineLearning.Chapter02.Features' 間のあいまいな参照です
```

第 1 章にも第 2 章にも `Features` があるので、両方の名前空間を `using` すると、どちらか分からなくなります。テストの名前空間が `MachineLearning.Tests.Chapter11` であることも効いていて、`Chapter07.RegressionMetrics` と書くと `MachineLearning.Tests.Chapter07` のほうを探しに行きます。

```text
error CS0234: 型または名前空間の名前 'RegressionMetrics' が名前空間 'MachineLearning.Tests.Chapter07' に存在しません
```

C# の名前の解決は、内側の名前空間から外へ順に探します。F# は `open` した順で後勝ちになるので、この種の衝突はあまり起きません。`using` を必要なものだけに絞るか、完全修飾名で書くかで解決しました。

2 つ目は、静的解析です。

```text
error CA1861: 呼び出されるメソッドが繰り返し呼び出され、渡された配列を変更しない場合は、定数配列引数よりも 'static readonly' フィールドを優先します。
```

テストの中で `new[] { 1.0, 2.0, 8.0, 9.0 }.Select(...)` と書いたところが止まりました。定数だけの配列を式の中で作ると、呼ばれるたびに配列が作られます。`private static readonly double[] TreeValues = [1.0, 2.0, 8.0, 9.0];` のようにフィールドへ出して直しました。`TreatWarningsAsErrors` を入れていると、テストコードの書き方までそろえられます。

## 11.12 品質チェック

```bash
cd apps/csharp
dotnet format MachineLearning.sln --no-restore --verify-no-changes
dotnet build MachineLearning.sln --no-restore
dotnet test
```

学習データがある場合の結果です。

```text
テストの実行の概要: 成功!
  合計: 191
  失敗: 0
  成功: 191
  スキップ済み: 0
```

学習データが無い場合（`ML_DATA_DIR=/nonexistent dotnet test`）は、実データのテストがスキップされて成功します。

```text
テストの実行の概要: 成功!
  合計: 191
  失敗: 0
  成功: 160
  スキップ済み: 31
```

この章で追加したのは 30 件です（評価指標 10 件・交差検証 10 件・モデル 3 件・ML.NET 2 件・実データ 5 件）。

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter11/CrossValidation.cs）</summary>

```csharp
namespace MachineLearning.Chapter11;

using MachineLearning.Chapter02;

/// <summary>1 回分の分け方。訓練データとテストデータの行番号。</summary>
/// <param name="Train">訓練データにする行番号</param>
/// <param name="Test">テストデータにする行番号</param>
public sealed record Fold(IReadOnlyList<int> Train, IReadOnlyList<int> Test);

/// <summary>
/// モデル。訓練データ（特徴量と正解）を受け取り、予測する関数を返す関数。
/// 第 3 章の Fit と Predict をつないだ形で、F# の <c>Model&lt;'T&gt;</c> に当たる。
/// </summary>
/// <typeparam name="T">正解ラベルの型</typeparam>
/// <param name="x">訓練データの特徴量</param>
/// <param name="t">訓練データの正解</param>
/// <returns>特徴量から予測を返す関数</returns>
public delegate Func<IReadOnlyList<Features>, IReadOnlyList<T>> Model<T>(
    IReadOnlyList<Features> x, IReadOnlyList<T> t);

/// <summary>K 分割交差検証。</summary>
public static class CrossValidation
{
    /// <summary>行番号をシードで並べ替えてから nSplits 個に分け、それぞれをテストデータ、残りを訓練データにする。</summary>
    public static IReadOnlyList<Fold> KFold(int nSplits, int seed, int nSamples)
    {
        if (nSplits < 2 || nSplits > nSamples)
        {
            throw new ArgumentOutOfRangeException(nameof(nSplits), $"分割数は 2 以上 {nSamples} 以下にしてください");
        }

        var positions = Preprocessing.Shuffle([.. Enumerable.Range(0, nSamples)], seed);
        return [.. SplitInto(positions, nSplits).Select(test =>
            new Fold([.. positions.Except(test)], test))];
    }

    /// <summary>
    /// 分割ごとに、訓練データで学習し、テストデータの予測を評価関数で採点する。
    /// 反復子なので、スコアは取り出すときに初めて計算する（取り出した分だけ学習する）。
    /// </summary>
    public static IEnumerable<double> CrossValidate<T>(
        Model<T> model,
        Metric<T> metric,
        IReadOnlyList<Fold> folds,
        IReadOnlyList<Features> x,
        IReadOnlyList<T> t)
    {
        ArgumentNullException.ThrowIfNull(model);
        ArgumentNullException.ThrowIfNull(metric);
        ArgumentNullException.ThrowIfNull(folds);
        foreach (var fold in folds)
        {
            var predict = model(Pick(x, fold.Train), Pick(t, fold.Train));
            yield return metric(Pick(t, fold.Test), predict(Pick(x, fold.Test)));
        }
    }

    /// <summary>行番号の順に要素を取り出す。</summary>
    public static IReadOnlyList<TItem> Pick<TItem>(IReadOnlyList<TItem> items, IReadOnlyList<int> rows)
    {
        ArgumentNullException.ThrowIfNull(items);
        ArgumentNullException.ThrowIfNull(rows);
        return [.. rows.Select(row => items[row])];
    }

    /// <summary>
    /// ほぼ同じ長さの count 個に分ける。割り切れないときは先頭のまとまりから 1 件ずつ多くする
    /// （F# の List.splitInto と同じ分け方）。
    /// </summary>
    private static IEnumerable<IReadOnlyList<int>> SplitInto(IReadOnlyList<int> positions, int count)
    {
        var size = positions.Count / count;
        var remainder = positions.Count % count;
        var taken = 0;
        for (var i = 0; i < count; i++)
        {
            var length = size + (i < remainder ? 1 : 0);
            yield return [.. positions.Skip(taken).Take(length)];
            taken += length;
        }
    }
}
```

</details>

## 11.13 まとめ

この章では、正解率だけでは見えない性質を評価指標で測り、交差検証で評価を安定させました。

1. **混同行列と評価指標** — 正解と予測を `bool` の組にして数え、適合率・再現率・F 値を求めた。分母が 0 のときは `NaN` にせず 0 を返した。型引数の比較には `EqualityComparer<T>.Default` を使った
2. **名前付きデリゲート** — 型引数を取る型の別名が書けないので、`Metric<T>` と `Model<T>` をデリゲートで宣言した。`Func<...>` と形が同じなので、既存のメソッドをそのまま渡せる
3. **反復子と遅延評価** — 交差検証のスコアを `yield return` で書き、取り出した分だけ学習した。同じ反復子を 2 回たどると学習も 2 回ずつ行われることをテストで確かめた
4. **足りない演算は自分で書く** — LINQ には F# の `List.splitInto` に当たる演算が無い。余りを先頭から配る `SplitInto` を書き、F# と同じ分け方にそろえた
5. **ML.NET との突き合わせ** — 同じ正解と予測を ML.NET の評価に渡し、自作の指標が一致することを確かめた
6. **1 回の分割の危うさ** — 再現率は分割によって 0.4 から 0.75 まで揺れた。交差検証の平均で、偶然に左右されにくい評価にした

実データの 6 つの指標も、分割ごとの最小・最大も、F# 版と表示の桁まで一致しました。

次の章では、線形回帰に罰則を加える正則化で、モデルが訓練データに合わせすぎることを防ぎます。
