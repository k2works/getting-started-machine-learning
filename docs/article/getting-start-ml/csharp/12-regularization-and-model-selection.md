---
type: Article
title: "第 12 章: 正則化とモデル選択"
description: "リッジ回帰を中心化した正規方程式で、ラッソ回帰を座標降下法で自作し、第 7 章の Matrix を拡張メソッドで補う。実験結果をレコードで記録して検証データで alpha を選び、ML.NET の SDCA の L2 と尺度をそろえて突き合わせる。"
tags: [article,getting-start-ml,csharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T01:48:36Z }
---

# 第 12 章: 正則化とモデル選択

## 12.1 はじめに

第 7 章の線形回帰は、訓練データの誤差をできるだけ小さくする係数を求めました。特徴量を増やすと、訓練データにはよく当てはまるのに、新しいデータには当たらない **過学習** が起きやすくなります。

この章では、係数の大きさに罰則を加えて過学習を抑える **正則化** を扱います。

- **リッジ回帰**: 係数の 2 乗の和に罰則を加える。係数が全体に小さくなる
- **ラッソ回帰**: 係数の絶対値の和に罰則を加える。役に立たない特徴量の係数がちょうど 0 になる

罰則の強さ `alpha` は、訓練データでは決められません。**検証データ** を別に取り分けて選びます。

[Python 版の第 12 章](../python/12-regularization-and-model-selection.md)・[Kotlin 版の第 12 章](../kotlin/12-regularization-and-model-selection.md)・[F# 版の第 12 章](../fsharp/12-regularization-and-model-selection.md) と同じ題材で進めます。C# 版では、次の 3 点に注目してください。

- 第 7 章の `Matrix` を、書き換えずに **拡張メソッド** で補う
- 実験の結果を **レコード** で記録し、`with` で一部を変えても元の記録が変わらないことを確かめる
- ML.NET の SDCA の L2 の **尺度** を学習用テストで確かめ、自作のリッジ回帰と突き合わせる

## 12.2 題材とデータ

`Boston.csv` は、ボストンの地区ごとの住宅価格（`PRICE`）と、その地区の特徴を記録したデータです。この章では、部屋数（`RM`）・生徒と教師の比率（`PTRATIO`）・低所得者の割合（`LSTAT`）の 3 列を使います。

3 列を標準化し、2 次の項（2 乗と積）を加えて 9 個の特徴量にします。特徴量を増やして過学習しやすくし、正則化の効果を見えやすくするためです。

データは、テストデータ（3 割）を先に取り分け、残りを訓練データと検証データ（残りの 3 割）に分けます。

## 12.3 TODO リストの作成

**TODO リスト**:

- [ ] リッジ回帰を求める
  - [ ] alpha が 0 なら最小二乗法と同じになる
  - [ ] alpha を大きくすると係数が小さくなる
- [ ] ラッソ回帰を求める
  - [ ] 役に立たない特徴量の係数がちょうど 0 になる
- [ ] alpha ごとの実験結果を記録し、検証データで選ぶ
- [ ] ライブラリと突き合わせる
- [ ] Boston のデータを準備する（外れ値の除去・2 次の項・標準化・3 つへの分割）
- [ ] 実データで実験する

## 12.4 リッジ回帰

### 第 7 章の行列を拡張メソッドで補う

リッジ回帰の正規方程式は、第 7 章の最小二乗法の式に `alpha × 単位行列` を足したものです。

(XᵀX + αI) w = Xᵀt

第 7 章の `Matrix` には、掛け算・転置・連立方程式の解法はありますが、足し算・定数倍・単位行列はありません。第 7 章のファイルは書き換えず、第 12 章に足します。

C# には **拡張メソッド** があるので、`this` を付けた静的メソッドとして書けば、呼び出し側では元からある操作と同じ書き方になります。F# 版は別モジュールの関数（`add a b`）として書きましたが、C# 版は `a.Add(b)` と書けます。

```csharp
// src/MachineLearning/Chapter12/MatrixOperations.cs
/// <summary>size 行 size 列の単位行列。</summary>
public static Matrix Identity(int size) =>
    Matrix.FromRows([.. Enumerable.Range(0, size).Select(i =>
        Enumerable.Range(0, size).Select(j => i == j ? 1.0 : 0.0).ToArray())]);

/// <summary>同じ形の行列を成分ごとに足す。</summary>
public static Matrix Add(this Matrix a, Matrix b)
{
    ArgumentNullException.ThrowIfNull(a);
    ArgumentNullException.ThrowIfNull(b);
    if (a.RowCount != b.RowCount || a.ColumnCount != b.ColumnCount)
    {
        throw new ArgumentException(
            $"{a.RowCount} 行 {a.ColumnCount} 列の行列と {b.RowCount} 行 {b.ColumnCount} 列の行列は足せません",
            nameof(b));
    }

    return Matrix.FromRows([.. Enumerable.Range(0, a.RowCount).Select(i =>
        Enumerable.Range(0, a.ColumnCount).Select(j => a[i, j] + b[i, j]).ToArray())]);
}
```

中心化（列ごとに平均値を引く）も、この章でしか使わないので拡張メソッドにしました。

```csharp
/// <summary>列ごとの平均値。</summary>
public static IReadOnlyList<double> ColumnMeans(this Matrix a)
{
    ArgumentNullException.ThrowIfNull(a);
    var columns = a.Transpose();
    return [.. Enumerable.Range(0, a.ColumnCount).Select(j => columns.Row(j).Average())];
}

/// <summary>列ごとに、その列の平均値を引く（中心化）。</summary>
public static Matrix Center(this Matrix a, IReadOnlyList<double> means) { /* 略 */ }
```

```csharp
// tests/MachineLearning.Tests/Chapter12/MatrixOperationsTests.cs
[Fact(DisplayName = "中心化すると列ごとの平均が 0 になる")]
public void Center()
{
    var centered = A.Center(A.ColumnMeans());

    Assert.Equal(Matrix.FromRows([[-1.0, -1.0], [1.0, 1.0]]), centered);
    Assert.Equal<IReadOnlyList<double>>([0.0, 0.0], centered.ColumnMeans());
}
```

第 7 章で `Matrix` に成分どうしを比べる `Equals` を書いておいたので、`Assert.Equal` で行列をそのまま比べられます。

### テストと実装

罰則をかけない（`alpha = 0`）リッジ回帰は、第 7 章の最小二乗法と同じ答えになるはずです。これを最初のテストにします。

```csharp
// tests/MachineLearning.Tests/Chapter12/RegularizationTests.cs
[Fact(DisplayName = "alpha が 0 のリッジ回帰は第 7 章の最小二乗法と同じ係数になる")]
public void ZeroAlphaEqualsLeastSquares()
{
    var features = Rows(X);

    var ridge = Regularization.FitRidge(0.0, X, T);
    var leastSquares = LinearRegression.Fit(features, T);

    Assert.Equal(leastSquares.Intercept, ridge.Intercept, 8);
    for (var j = 0; j < X.ColumnCount; j++)
    {
        Assert.Equal(leastSquares.Coefficients.Values[j], ridge.Coefficients[j], 8);
    }
}
```

```csharp
// src/MachineLearning/Chapter12/Regularization.cs
/// <summary>正則化した線形回帰の学習結果。係数は特徴量の列の順に並ぶ。</summary>
public sealed record RegularizedModel(IReadOnlyList<double> Coefficients, double Intercept);

/// <summary>中心化した X と t で (Xᵀ X + alpha I) w = Xᵀ t を解く。切片には罰則をかけない。</summary>
public static RegularizedModel FitRidge(double alpha, Matrix x, IReadOnlyList<double> t)
{
    ArgumentNullException.ThrowIfNull(x);
    ArgumentNullException.ThrowIfNull(t);
    var xMeans = x.ColumnMeans();
    var tMean = t.Average();
    var centered = x.Center(xMeans);
    var transposed = centered.Transpose();
    var coefficients = transposed
        .Multiply(centered)
        .Add(MatrixOperations.Identity(x.ColumnCount).Scale(alpha))
        .Solve(transposed.Multiply([.. t.Select(value => value - tMean)]));
    return new RegularizedModel(coefficients, tMean - Matrix.Dot(xMeans, coefficients));
}
```

第 7 章のように「先頭に 1 の列を足す」やり方は使いません。切片にまで罰則がかかってしまうからです。代わりに X と t を中心化して係数だけを求め、切片は `tの平均 - 各列の平均 × 係数` から戻します。

拡張メソッドにしたおかげで、`transposed.Multiply(centered).Add(...).Solve(...)` と、式を左から右へ 1 本につなげられます。

### テストデータが退化していて NaN が出た

最初に用意した架空のデータで、テストが次のように落ちました。

```text
Assert.Equal() Failure: Values are not within 6 decimal places
Expected: 1 (rounded from 1)
Actual:   NaN (rounded from NaN)
```

```text
Assert.Equal() Failure: Values are not within 8 decimal places
Expected: NaN (rounded from NaN)
Actual:   Infinity (rounded from Infinity)
```

原因は、3 列目を `0.5, -0.5, 0.5, -0.5, ...` としたことでした。この列は「1 列目 − 2 列目」の −0.5 倍とぴったり一致していて、列どうしが独立ではありません。`XᵀX` が正則でなくなり、ガウスの消去法が 0 で割って `Infinity` と `NaN` を返します。第 7 章で見たとおり、この計算は例外を投げずに静かに壊れます。

3 列目を `0.2, -0.3, 0.7, -0.1, 0.4, -0.6` に替え、正解を「3 + 2 × 1 列目 + 1 × 2 列目」ちょうどにしました。これで 3 列目の本当の係数は 0 なので、ラッソ回帰のテストにもそのまま使えます。

```csharp
/// <summary>架空のデータ。3 列目は 1 列目と 2 列目に関係しない、役に立たない特徴量。</summary>
private static readonly Matrix X = Matrix.FromRows(
[
    [1.0, 2.0, 0.2],
    [2.0, 1.0, -0.3],
    [3.0, 4.0, 0.7],
    [4.0, 3.0, -0.1],
    [5.0, 6.0, 0.4],
    [6.0, 5.0, -0.6],
]);

/// <summary>正解は 3 + 2 × 1 列目 + 1 × 2 列目。3 列目は正解に関係しない。</summary>
private static readonly double[] T = [7.0, 8.0, 13.0, 14.0, 19.0, 20.0];
```

罰則の効果も確かめます。

```csharp
[Fact(DisplayName = "alpha を大きくすると係数の絶対値の合計が小さくなる")]
public void LargerAlphaShrinksCoefficients()
{
    var weak = Regularization.FitRidge(0.1, X, T).Coefficients.Sum(Math.Abs);
    var strong = Regularization.FitRidge(100.0, X, T).Coefficients.Sum(Math.Abs);

    Assert.True(strong < weak, $"alpha=100 の {strong} は alpha=0.1 の {weak} より小さいはず");
}
```

最初は「`alpha` を 0・1・10・100 と上げると合計が単調に減る」と書きましたが、`alpha = 0` のときだけ合計 3.0000 で、`alpha = 1` の 3.1143 より小さくなり、テストが落ちました。この架空のデータでは、`alpha = 0` のとき 3 列目の係数がちょうど 0 になるためです。「罰則を強くすれば単調に小さくなる」は一般には成り立ちません。2 点の比較に直しました。

## 12.5 ラッソ回帰

ラッソ回帰の罰則は係数の絶対値の和で、`alpha` を境に微分できません。正規方程式では解けないので、**座標降下法**（係数を 1 つずつ、ほかを止めたまま最適な値に更新することを繰り返す）で解きます。

```csharp
[Fact(DisplayName = "ラッソ回帰は役に立たない特徴量の係数をちょうど 0 にする")]
public void LassoZeroesUselessFeature()
{
    var model = Regularization.FitLasso(1.0, X, T);

    Assert.Equal(0.0, model.Coefficients[2]);
    Assert.NotEqual(0.0, model.Coefficients[0]);
}
```

`Assert.Equal(0.0, ...)` に許容誤差を付けていないことに意味があります。ラッソ回帰の係数は「とても小さい」のではなく「ちょうど 0」になります。

```csharp
/// <summary>(1/2n) × 誤差の二乗和 + alpha × 係数の絶対値の和 を、座標降下法で最小化する（ラッソ回帰）。</summary>
public static RegularizedModel FitLasso(double alpha, Matrix x, IReadOnlyList<double> t)
{
    ArgumentNullException.ThrowIfNull(x);
    ArgumentNullException.ThrowIfNull(t);
    double n = x.RowCount;
    var xMeans = x.ColumnMeans();
    var tMean = t.Average();
    var centered = x.Center(xMeans).Transpose();
    var columns = Enumerable.Range(0, x.ColumnCount).Select(j => centered.Row(j).ToArray()).ToArray();
    var squaredNorms = columns.Select(column => Matrix.Dot(column, column) / n).ToArray();
    var weights = new double[columns.Length];

    // 残差（中心化した t - 現在の予測）。係数を 1 つ変えるたびに、その分だけ更新する
    var residual = t.Select(value => value - tMean).ToArray();
    for (var iteration = 0; iteration < LassoIterations; iteration++)
    {
        for (var j = 0; j < columns.Length; j++)
        {
            var column = columns[j];
            var rho = (Matrix.Dot(column, residual) / n) + (weights[j] * squaredNorms[j]);
            var updated = SoftThreshold(alpha, rho) / squaredNorms[j];
            for (var i = 0; i < residual.Length; i++)
            {
                residual[i] += column[i] * (weights[j] - updated);
            }

            weights[j] = updated;
        }
    }

    return new RegularizedModel(weights, tMean - Matrix.Dot(xMeans, weights));
}

/// <summary>軟閾値関数。z の絶対値を gamma だけ 0 に近づけ、0 を越えるならちょうど 0 にする。</summary>
private static double SoftThreshold(double gamma, double z) =>
    z > gamma ? z - gamma : z < -gamma ? z + gamma : 0.0;
```

- 係数を 0 にするのは `SoftThreshold` です。`rho` の絶対値が `alpha` 以下なら、その係数は 0 になります
- 残差は毎回すべて計算し直さず、係数が変わった分だけ足し込みます。ここだけは配列を書き換える手続き的な書き方です。C# では `double[]` がそのまま書き換えられるので、F# 版とほとんど同じコードになります
- 罰則が無い（`alpha = 0`）ときはリッジ回帰と同じ答えになることも、テストで確かめています

## 12.6 実験結果を記録して選ぶ

`alpha` を変えて試した結果を記録します。レコードにすると、`with` で一部を変えた新しい記録を作っても、元の記録は変わりません。

```csharp
/// <summary>正則化の強さ 1 つ分の実験結果。</summary>
public sealed record Experiment(double Alpha, double TrainScore, double ValidationScore, double CoefficientAbsSum);
```

```csharp
[Fact(DisplayName = "実験の記録は with で一部を変えても元の記録が変わらない")]
public void ExperimentIsImmutable()
{
    var original = new Experiment(1.0, 0.9, 0.8, 5.0);

    var changed = original with { Alpha = 2.0 };

    Assert.Equal((1.0, 2.0), (original.Alpha, changed.Alpha));
    Assert.Equal(original.ValidationScore, changed.ValidationScore);
}
```

C# のレコードは、F# のレコードと同じく `with` 式を持ち、値で比較されます。違うのは、C# のレコードは参照型（`class`）であり、`with` は新しいオブジェクトを作るという点です。書き換えられる `class` を使うと、実験の記録を後から壊してしまう余地が残ります。

```csharp
/// <summary>alpha ごとに訓練データで学習し、訓練データと検証データの決定係数、係数の絶対値の合計を記録する。</summary>
public static IReadOnlyList<Experiment> RunRidgeExperiments(
    IReadOnlyList<double> alphas,
    Matrix xTrain,
    IReadOnlyList<double> tTrain,
    Matrix xValid,
    IReadOnlyList<double> tValid)
{
    ArgumentNullException.ThrowIfNull(alphas);
    return [.. alphas.Select(alpha =>
    {
        var model = FitRidge(alpha, xTrain, tTrain);
        return new Experiment(
            alpha,
            RegressionMetrics.R2Score(tTrain, Predict(model, xTrain)),
            RegressionMetrics.R2Score(tValid, Predict(model, xValid)),
            model.Coefficients.Sum(Math.Abs));
    })];
}

/// <summary>検証データの決定係数が最も高い実験。</summary>
public static Experiment BestExperiment(IReadOnlyList<Experiment> experiments)
{
    ArgumentNullException.ThrowIfNull(experiments);
    return experiments.Count == 0
        ? throw new ArgumentException("実験が 1 つもありません", nameof(experiments))
        : experiments.MaxBy(experiment => experiment.ValidationScore)!;
}
```

`MaxBy` は空のリストに `null` を返します（F# の `List.maxBy` は例外を投げます）。`null` が返ることは呼び出し側にとって扱いにくいので、先に件数を確かめて `ArgumentException` にしました。空でないことを確かめた後なので、`!`（null 免除演算子）でコンパイラに伝えています。

## 12.7 ライブラリと突き合わせる

### ML.NET の SDCA と尺度をそろえる

ML.NET の回帰の学習器 SDCA（確率的双対座標上昇法）で L2 の罰則をかけ、自作のリッジ回帰と突き合わせます。SDCA の目的関数は「(1/n) × 誤差の二乗和 + (l2 / 2) × 係数の二乗和」で、自作の「誤差の二乗和 + alpha × 係数の二乗和」とは尺度が違います。両辺を比べると、`l2 = 2 × alpha / n` のときに同じ係数になるはずです。これを学習用テストで確かめます（[ADR 004](../../../adr/004-fsharp-ml-libraries.md) に F# 版で確かめた結果があります）。

```csharp
// tests/MachineLearning.Tests/Chapter12/LibraryRegularizationTests.cs
/// <summary>反復で解く SDCA と厳密な解の差を許す幅。</summary>
private const double Tolerance = 1e-3;

[Fact(DisplayName = "学習用テスト: ML.NET の Sdca の L2Regularization は、自作の alpha を件数の半分で割った値に当たる")]
public void SdcaL2ScaleMatchesAlpha()
{
    var (x, t) = Dataset();
    const double L2 = 0.1;

    var library = MlNetRegularization.FitSdca(L2, x, t);
    var mine = Regularization.FitRidge(t.Count * L2 / 2.0, x, t);

    for (var j = 0; j < x.ColumnCount; j++)
    {
        Assert.Equal(mine.Coefficients[j], library.Coefficients[j], Tolerance);
    }

    Assert.Equal(mine.Intercept, library.Intercept, Tolerance);
}
```

`Assert.Equal(expected, actual, tolerance)` は、`double` の許容誤差を **絶対値** で指定する形です。小数第何位まで、という指定（`Assert.Equal(expected, actual, 3)`）とは別のメソッドで、反復で解く手法と厳密な解を比べるにはこちらが合います。

```csharp
// src/MachineLearning/Chapter12/MlNetRegularization.cs
/// <summary>
/// ML.NET の SDCA で、(1/n) × 誤差の二乗和 + (l2 / 2) × 係数の二乗和 を最小化する。L1 の罰則は 0 にする。
/// 第 7 章と同じく、順番を固定して 1 スレッドで学習し、実行のたびに結果が変わらないようにする。
/// </summary>
public static RegularizedModel FitSdca(double l2, Matrix x, IReadOnlyList<double> t)
{
    ArgumentNullException.ThrowIfNull(x);
    ArgumentNullException.ThrowIfNull(t);
    var context = new MLContext(seed: 0);
    var schema = SchemaDefinition.Create(typeof(RegressionRow));
    schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, x.ColumnCount);
    var rows = Enumerable.Range(0, x.RowCount)
        .Select(i => new RegressionRow
        {
            Features = [.. x.Row(i).Select(value => (float)value)],
            Label = (float)t[i],
        })
        .ToList();
    var options = new SdcaRegressionTrainer.Options
    {
        L2Regularization = (float)l2,
        L1Regularization = 0f,
        ConvergenceTolerance = ConvergenceTolerance,
        MaximumNumberOfIterations = MaximumNumberOfIterations,
        Shuffle = false,
        NumberOfThreads = 1,
    };
    var trained = context.Regression.Trainers.Sdca(options)
        .Fit(context.Data.LoadFromEnumerable(rows, schema));
    return new RegularizedModel(
        [.. trained.Model.Weights.Select(weight => (double)weight)], trained.Model.Bias);
}

/// <summary>自作の FitRidge と同じ alpha でリッジ回帰を学習する。ML.NET の l2 は alpha × 2 / 件数 に当たる。</summary>
public static RegularizedModel FitRidgeWithMlNet(double alpha, Matrix x, IReadOnlyList<double> t)
{
    ArgumentNullException.ThrowIfNull(t);
    return FitSdca(2.0 * alpha / t.Count, x, t);
}
```

- `L2Regularization` の型は `float?`（`Nullable<float>`）です。F# では `Nullable(float32 l2)` と明示的に包みますが、C# では `float` をそのまま代入できます。オブジェクト初期化子で並べて書けるのも C# のほうが素直です
- 第 7 章で分かったとおり、SDCA は既定では複数のスレッドで進むので実行のたびに結果が変わります。`Shuffle = false` と `NumberOfThreads = 1` で順番を固定し、収束の判定を厳しく（`ConvergenceTolerance = 1e-7`）しました。「同じ結果が返ること」もテストにしています

第 7 章では、SDCA に渡す前に正規化（`NormalizeMeanVariance`）を挟む必要がありました。この章では、特徴量をすでに標準化してから渡しているので、パイプラインに正規化を入れていません。前処理で範囲をそろえておけば、学習器に余計な変換を足さずに済みます。

### 罰則が弱いと反復は収束しきらない

最初は `alpha = 1` で突き合わせようとして、テストが落ちました。

```text
Assert.Equal() Failure: Values are not within tolerance 0.001
Expected: 1.2492516098658812
Actual:   1.2690932750701904
```

30 件のデータで `alpha = 1` なら `l2 = 2 × 1 / 30 ≒ 0.067` で、罰則がとても弱くなります。罰則が弱いほど目的関数の底は平らになり、反復で解く SDCA は厳密な解の手前で止まります。`alpha = 10`（`l2 ≒ 0.67`）に変えると、小数第 3 位まで一致しました。

「ライブラリと数値が合わない」ときに、実装の誤りなのか、解法の違いなのかを切り分けるには、こうして条件を変えて確かめるのが確実です。

## 12.8 Boston のデータを準備する

### 型プロバイダの代わりに第 2 章の表を使う

F# 版は型プロバイダで Boston.csv を読み、使わない列（`RAD`）の空欄で例外になり、`AssumeMissingValues=true` を足して直しました。C# 版には型プロバイダがないので、第 2 章の `Table` で読みます。`Table` は全列を文字列のまま持ち、`Row.Number` を呼んだ列だけを数値にするので、使わない列に空欄があっても読めます。

```csharp
// src/MachineLearning/Chapter12/Boston.cs
/// <summary>Boston.csv のうち、この章で使う列だけを読み込む。</summary>
public static (IReadOnlyList<Features> X, IReadOnlyList<double> T) Load(string csvFile)
{
    var table = Table.Load(csvFile);
    var x = table.Rows
        .Select(row => new Features(FeatureNames, [.. FeatureNames.Select(column => Value(row, column))]))
        .ToList();
    return (x, [.. table.Rows.Select(row => Value(row, Target))]);
}

private static double Value(Row row, string column) =>
    row.Number(column) ?? throw new InvalidDataException($"{column} が空欄です");
```

型プロバイダは「列の名前と型をコンパイル時に知っている」という利点と引き換えに、サンプルに現れた形しか受け付けないという制約を持ちます。C# 版の `Table` はその逆で、列の名前を間違えても実行時まで分かりませんが、読むときに初めて型を決めるので、使わない列の形に影響されません。第 2 章で `Table` と `Row` を自作した判断が、ここで効いています。

### 外れ値の除去・2 次の項・標準化

```csharp
/// <summary>
/// 列ごとの z スコア（標本標準偏差で割る）の絶対値が threshold を超える値を、1 つでも持つ行を除く。
/// 正解の列も判定の対象にする。
/// </summary>
public static (IReadOnlyList<Features> X, IReadOnlyList<double> T) RemoveOutliers(
    IReadOnlyList<Features> x, IReadOnlyList<double> t, double threshold)
{
    ArgumentNullException.ThrowIfNull(x);
    ArgumentNullException.ThrowIfNull(t);
    var columns = x[0].Columns
        .Select((column, j) => x.Select(row => row.Values[j]).ToList())
        .Append([.. t])
        .Select(values => MeanAndStd(values, 1))
        .ToList();
    var kept = Enumerable.Range(0, x.Count)
        .Where(i => !IsOutlier([.. x[i].Values, t[i]], columns, threshold))
        .ToList();
    return ([.. kept.Select(i => x[i])], [.. kept.Select(i => t[i])]);
}

/// <summary>平均値と標準偏差。標準偏差は偏差の二乗和を「件数 - ddof」で割って求める。</summary>
public static (double Mean, double Std) MeanAndStd(IReadOnlyList<double> values, int ddof)
{
    var mean = values.Average();
    var squares = values.Sum(value => (value - mean) * (value - mean));
    return (mean, Math.Sqrt(squares / (values.Count - ddof)));
}
```

外れ値の判定は他の版と同じく、平均から標準偏差の 3 倍より離れた値とします。ここでの標準偏差は **標本標準偏差**（件数から 1 を引いて割る、`ddof = 1`）です。一方、標準化に使うのは **母標準偏差**（件数で割る、`ddof = 0`）です。2 つを `ddof` の引数で切り替えられるようにし、どちらの割り方なのかをテストで固定しました。

```csharp
[Fact(DisplayName = "標本標準偏差は件数から 1 を引いて割る")]
public void SampleStd()
{
    // 平均は 3、偏差の二乗和は 4 + 1 + 0 + 1 + 4 = 10
    Assert.Equal(Math.Sqrt(10.0 / 4), Boston.MeanAndStd([1.0, 2.0, 3.0, 4.0, 5.0], 1).Std, 12);
}
```

2 次の項は、標準化した値から作ります。

```csharp
/// <summary>平均値と、件数で割る標準偏差（母標準偏差）を訓練データから求め、2 次の項の組を決める。</summary>
public static PolynomialScaler Fit(IReadOnlyList<string> columns, IReadOnlyList<Features> x)
{
    ArgumentNullException.ThrowIfNull(columns);
    ArgumentNullException.ThrowIfNull(x);
    var pairs = columns
        .SelectMany((_, i) => Enumerable.Range(i, columns.Count - i).Select(j => (Left: i, Right: j)))
        .ToArray();
    /* 略 */
}

/// <summary>標準化した値と、その 2 次の項を並べた行列にする。</summary>
public Matrix Transform(IReadOnlyList<Features> x)
{
    ArgumentNullException.ThrowIfNull(x);
    return Matrix.FromRows([.. x.Select(row =>
    {
        var z = this.columns
            .Select((column, j) => (row.Value(column) - this.stats[j].Mean) / this.stats[j].Std)
            .ToArray();
        return z.Concat(this.pairs.Select(pair => z[pair.Left] * z[pair.Right])).ToArray();
    })]);
}
```

`j` を `i` から始めるので、`(0,0)`・`(0,1)`・`(0,2)`・`(1,1)`・… と、同じ組を 2 回作りません。3 列から 6 個の 2 次の項ができ、元の 3 列と合わせて 9 個の特徴量になります。名前は scikit-learn と同じ形（`RM^2`・`RM LSTAT`）にしました。

第 9 章にも多項式特徴量（`PolynomialFeatures`）がありますが、あちらは元の値から 2 次の項を作ってから標準化します。この章は F# 版と同じく、標準化してから 2 次の項を作ります。順番が違うと値が変わるので、第 9 章のクラスを使い回さず、この章の `PolynomialScaler` を別に書きました。

標準化の平均値と標準偏差は、**訓練データだけ** から求め、検証データとテストデータにも同じ値で適用します（第 2 章のデータリークと同じ理由です）。

```csharp
/// <summary>
/// 外れ値を除き、テストデータを分けてから、残りを訓練データと検証データに分ける。
/// 標準化と 2 次の項は、訓練データの平均値と標準偏差で 3 つすべてに適用する。
/// </summary>
public static BostonDataset Prepare(string csvFile, double testSize, double validationSize, int seed)
{
    var (allX, allT) = RemoveOutliers(Load(csvFile), OutlierThreshold);
    var outer = Preprocessing.SplitTrainTest(allX, allT, testSize, seed);
    var inner = Preprocessing.SplitTrainTest(outer.XTrain, outer.TTrain, validationSize, seed);
    var scaler = PolynomialScaler.Fit(FeatureNames, inner.XTrain);
    return new BostonDataset(
        scaler.Transform(inner.XTrain),
        inner.TTrain,
        scaler.Transform(inner.XTest),
        inner.TTest,
        scaler.Transform(outer.XTest),
        outer.TTest,
        scaler.FeatureNames);
}
```

分割は、第 2 章の `SplitTrainTest` を 2 回使います。1 回目でテストデータを取り分け、2 回目で残りを訓練データと検証データに分けます。

## 12.9 実データで実験する

```bash
dotnet run --project src/MachineLearning -- chapter12
```

```text
データ件数: 98（外れ値 2 件を除外）
訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件
特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2
alpha	訓練 R²	検証 R²	係数の絶対値の合計
0	0.8914	-0.4803	12.509
0.1	0.8914	-0.4445	12.400
1	0.8908	-0.2447	11.718
10	0.8819	-0.0167	9.991
100	0.7769	-0.1583	6.406
検証データで選んだ alpha: 10
テストデータの決定係数: 線形回帰 0.9040, リッジ回帰 0.8827
ML.NET（SDCA）のリッジ回帰のテストデータの決定係数: 0.8828
ラッソ回帰（alpha=1）で係数が 0 になった特徴量: RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2

シード	選んだ alpha	線形回帰	リッジ回帰
0	10	0.9040	0.8827
1	10	0.5551	0.4040
2	1	0.7012	0.7040
3	0	0.7460	0.7460
4	100	0.2219	0.4841
```

### 結果を読む

- `alpha` を大きくすると、係数の絶対値の合計は 12.5 から 6.4 まで小さくなり、訓練データの決定係数は下がっていきます。罰則が係数を抑えています
- 検証データの決定係数は、どの `alpha` でも負です。決定係数が負とは、「検証データの平均値を常に予測する」よりも外れている、という意味です。シード 0 の分け方では、21 件の検証データに訓練データと傾向の違う地区が集まっています。それでも `alpha = 10` で −0.48 から −0.02 まで改善しており、正則化が訓練データへの合わせすぎを抑えていることが分かります
- ML.NET の SDCA で同じ `alpha` を換算して学習すると、テストデータの決定係数は 0.8828 で、自作の 0.8827 とほぼ一致しました
- ラッソ回帰の `alpha = 1` では、9 個の特徴量のうち 4 個の係数がちょうど 0 になりました。残ったのは元の 3 列と `RM^2`・`RM PTRATIO` です

### 分け方で結論が変わる

シード 0 では、テストデータの決定係数はリッジ回帰（0.8827）より線形回帰（0.9040）のほうが高くなりました。シードを変えると、リッジ回帰が勝つ分け方（シード 2・4）も負ける分け方（シード 0・1）もあります。100 件ほどのデータでは、1 回の分割の結果だけで「正則化は効く・効かない」とは言えません。第 11 章の交差検証と同じ話です。

### F# 版と一致するか

表示された数値は、[F# 版の第 12 章](../fsharp/12-regularization-and-model-selection.md) と **すべて** 一致しました。件数・特徴量の名前・5 つの `alpha` の訓練と検証の決定係数・係数の絶対値の合計・選んだ `alpha`・テストデータの決定係数・ML.NET の結果・ラッソ回帰で 0 になった 4 つの特徴量・シードごとの比較の 15 個の数値まで、表示の桁まで同じです。

| 項目 | C# 版 | F# 版 |
|------|------|------|
| 外れ値を除いた件数 | 98 | 98 |
| 訓練 / 検証 / テスト | 47 / 21 / 30 | 47 / 21 / 30 |
| alpha = 0 の検証 R² | -0.4803 | -0.4803 |
| alpha = 10 の検証 R² | -0.0167 | -0.0167 |
| 選んだ alpha | 10 | 10 |
| テストの R²（線形回帰） | 0.9040 | 0.9040 |
| テストの R²（リッジ回帰） | 0.8827 | 0.8827 |
| テストの R²（ML.NET） | 0.8828 | 0.8828 |
| ラッソ回帰で 0 になった数 | 4 | 4 |

一致した理由は、第 7 章・第 11 章と同じです。分割が同じ行になること、リッジ回帰が反復ではなく一度に解く方法で、部分ピボット選択の手順まで同じであることです。ラッソ回帰は反復ですが、更新の順序と回数が同じなので、同じ値に落ち着きます。

第 7 章では ML.NET の SDCA だけが自作と食い違いました（R² 0.7659 と 0.7740）。この章で 0.8828 と 0.8827 まで近づいたのは、`ConvergenceTolerance` を厳しくし、特徴量を標準化してから渡しているためです。反復で解く手法でも、条件をそろえれば厳密な解に十分近づけられます。

これらの値はテストで固定しています。

```csharp
[Fact(DisplayName = "実データの alpha ごとの決定係数は F# 版と一致する")]
public void ExperimentsMatchFSharp()
{
    this.RequireData();
    var dataset = Boston.Prepare(
        this.csvFile, Chapter12Program.TestSize, Chapter12Program.ValidationSize, Chapter12Program.Seed);

    var comparison = Chapter12Program.CompareOnTestData(dataset);

    Assert.Equal(0.8914, comparison.Experiments[0].TrainScore, 4);
    Assert.Equal(-0.4803, comparison.Experiments[0].ValidationScore, 4);
    Assert.Equal(-0.0167, comparison.Experiments[3].ValidationScore, 4);
    Assert.Equal(10.0, comparison.Best.Alpha);
    Assert.Equal(0.9040, comparison.LinearScore, 4);
    Assert.Equal(0.8827, comparison.RidgeScore, 4);
}
```

`Chapter12Program` は `using Chapter12Program = MachineLearning.Chapter12.Program;` で付けた別名です。章ごとに `Program` クラスを置いているので、第 7 章と第 12 章の両方を `using` するとどちらか分からなくなります。

```text
error CS0104: 'Program' は、'MachineLearning.Chapter07.Program' と 'MachineLearning.Chapter12.Program' 間のあいまいな参照です
```

型引数を取らない型なら、`using` の別名で短い名前を付けられます（第 11 章で見たとおり、型引数を取る型には使えません）。

### 可視化

`alpha` と決定係数の関係を折れ線で見る図や、係数が 0 に落ちていく様子の図は、[Python 版の第 12 章](../python/12-regularization-and-model-selection.md)・[Kotlin 版の第 12 章](../kotlin/12-regularization-and-model-selection.md) を参照してください。C# 版では、上の表のとおり、`alpha` ごとの数値をテストで固定する形にしています。

## 12.10 品質チェック

```bash
cd apps/csharp
dotnet format MachineLearning.sln --no-restore --verify-no-changes
dotnet build MachineLearning.sln --no-restore
dotnet test
```

学習データがある場合の結果です。

```text
テストの実行の概要: 成功!
  合計: 223
  失敗: 0
  成功: 223
  スキップ済み: 0
```

学習データが無い場合（`ML_DATA_DIR=/nonexistent dotnet test`）は、実データのテストがスキップされて成功します。

```text
テストの実行の概要: 成功!
  合計: 223
  失敗: 0
  成功: 186
  スキップ済み: 37
```

この章で追加したのは 32 件です（行列の拡張 6 件・正則化 10 件・ライブラリ 3 件・データの準備 7 件・実データ 6 件）。

アナライザーには、この章でも 1 つ止められました。

```text
error CA1859: パフォーマンスを向上させるために、パラメーター 'stats' の型を 'IReadOnlyList<(double Mean, double Std)>' から 'List<(double Mean, double Std)>' に変更します
```

`private` のメソッドで、呼び出し側が必ず `List<T>` を渡しているなら、インターフェース越しに呼ぶ間接参照を省けるという指摘です。公開するメソッドには出ない指摘で、「外に見せる型は広く、内側は具体的に」という使い分けを促しています。指摘どおり `List<...>` に直しました。

## 12.11 まとめ

この章では、正則化で過学習を抑え、検証データで罰則の強さを選びました。

1. **拡張メソッドで既存の型を補う** — 第 7 章の `Matrix` を書き換えずに、足し算・定数倍・単位行列・中心化を足した。呼び出し側は元からある操作と同じ書き方で使える
2. **退化したデータは静かに壊れる** — 列が互いに独立でないテストデータを作ってしまい、例外ではなく `NaN` と `Infinity` が返った。第 7 章と同じ落とし穴を、今度はテストのデータ側で踏んだ
3. **思い込みはテストで壊れる** — 「`alpha` を上げれば係数の合計は単調に減る」は一般には成り立たなかった。テストが落ちてはじめて、主張が広すぎたことに気づいた
4. **レコードで実験を記録する** — `with` で一部を変えても元の記録が変わらないことを確かめた。実験の記録を壊さずに積み上げられる
5. **ライブラリの尺度を学習用テストで確かめる** — ML.NET の `L2Regularization` は `2 × alpha / 件数` に当たる。罰則が弱いと反復は収束しきらず、`alpha = 1` では小数第 3 位まで合わなかった
6. **分け方で結論が変わる** — 5 つのシードで、リッジ回帰が勝つ分け方も負ける分け方もあった。1 回の分割で「正則化は効く」とは言えない

実データの結果は、15 個の数値すべてが F# 版と一致しました。ML.NET の SDCA でさえ、条件をそろえれば自作との差は小数第 4 位で 0.0001 に収まります。

次の章では、特徴量そのものを作り直す主成分分析を扱います。
