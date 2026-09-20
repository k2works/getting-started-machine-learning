---
type: Article
title: "第 13 章: 主成分分析による次元削減"
description: "分散共分散行列の固有値分解（ヤコビ法）を C# で自作し、ボストンの住宅価格の 15 列を寄与率で要約する。第 7 章の Matrix を回転行列の積に使い回し、固有ベクトルの符号をそろえる規則を決めて、F# 版・Kotlin 版・Python 版と同じ主成分を得る。ML.NET の ProjectToPrincipalComponents は主成分の座標しか返さないので、自作を最終実装にして、座標だけを突き合わせる。"
tags: [article,getting-start-ml,csharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T01:41:47Z }
---

# 第 13 章: 主成分分析による次元削減

## 13.1 はじめに

列が多いデータは、そのままでは全体像がつかめません。**主成分分析**（PCA: Principal Component Analysis）は、たくさんの列を、ばらつきをできるだけ残した少ない数の軸（**主成分**）に置き換える方法です。

この章では、ボストンの地区ごとのデータ（15 列）を主成分分析し、次の 2 つを求めます。

- **寄与率**: それぞれの主成分が、全体のばらつきのうちどれだけを説明するか
- **主成分の意味**: それぞれの主成分に、どの列が強く効いているか

[Python 版の第 13 章](../python/13-principal-component-analysis.md)・[Kotlin 版の第 13 章](../kotlin/13-principal-component-analysis.md)・[F# 版の第 13 章](../fsharp/13-principal-component-analysis.md) と同じ題材です。C# 版では次の 3 点に注目してください。

- 固有値分解から **自作する**。ML.NET には主成分の座標への変換（`ProjectToPrincipalComponents`）はありますが、主成分や寄与率を取り出す API は無いので、置き換えではなく **突き合わせの相手** として使う
- 回転行列の積は、**第 7 章で作った `Matrix`** をそのまま使う。新しい行列の型は増やさない
- 固有ベクトルは符号が逆でも同じ向きを表すので、**符号をそろえる規則** を決める。これを決めておくと、まったく別の実装（NumPy の `eigh`・Tribuo の固有値分解・F# 版のヤコビ法）と向きまで一致する

## 13.2 主成分分析とは

### ばらつきが最も大きい方向を探す

散らばったデータに 1 本の軸を引くとき、データのばらつき（分散）が最も大きくなる向きを選べば、その 1 本で元のデータの情報を最もよく残せます。これが第 1 主成分です。第 2 主成分は、第 1 主成分と直交する向きのうち、ばらつきが最も大きいものです。以下、列の数だけ主成分が得られます。

### 分散共分散行列の固有ベクトル

「ばらつきが最も大きい向き」は、**分散共分散行列の固有ベクトル** として求まります。分散共分散行列は、対角成分が列ごとの分散、それ以外の成分が 2 つの列の共分散になっている対称行列です。

対称行列 `A` に対して `A v = λ v` を満たす `v` が固有ベクトル、`λ` が固有値です。固有値は、その向きのばらつき（分散）そのものになります。

### 寄与率

固有値を大きい順に並べ、固有値の合計で割ったものが **寄与率** です。先頭から足していった **累積寄与率** が 0.8 に届くところまで主成分を採れば、「元のばらつきの 8 割を説明する軸の組」が得られます。

## 13.3 題材とデータ

第 9 章と同じ `Boston.csv`（100 件）を使います。`CRIME` だけが文字のカテゴリ（`high`・`low`・`very_low`）で、ほかの 13 列は数値、いくつかの列に空欄があります。

主成分分析は距離ではなく分散を見るので、列ごとの単位の違いがそのまま結果に効きます。そこで、第 9 章の道具で前処理してから分析します。

- 欠損値を列の平均値で **補完** する
- `CRIME` を **ダミー変数** の 2 列にする
- すべての列を **標準化** する（平均 0・標準偏差 1）

教師なし学習なので、訓練データとテストデータには分けません。100 件すべてを使います。

## 13.4 TODO リストの作成

**TODO リスト**:

- [ ] 分散共分散行列を求める
- [ ] 対称行列の固有値と固有ベクトルを求める（ヤコビ法）
  - [ ] 固有値の大きい順に並べる
  - [ ] `A v = λ v` を満たすことを確かめる
- [ ] 固有値から寄与率を求める
- [ ] 固有ベクトルの符号をそろえる
- [ ] データを主成分の向きに射影する
- [ ] 累積寄与率がしきい値に届く主成分の数を求める
- [ ] 主成分への影響が大きい列を求める
- [ ] Boston.csv を補完・ダミー変数化・標準化して分析する

## 13.5 分散共分散行列を求める

### 行列は第 7 章の Matrix を使う

F# 版は行列を `float[][]`（配列の配列）の型の別名で表しました。C# 版は第 7 章で、成分で比べられる `Matrix` クラスを作ってあります。配列をそのまま公開すると中身を書き換えられてしまうので、この章でも `Matrix` を使い回します。

### Red

分散共分散行列のテストから書きます。2 列目が 1 列目のちょうど 2 倍の、架空の 4 件のデータを使います。

```csharp
// tests/MachineLearning.Tests/Chapter13/PcaTests.cs
/// <summary>2 列目が 1 列目の 2 倍の、完全に相関する架空のデータ。</summary>
private static readonly Matrix Correlated =
    Matrix.FromRows([[1.0, 2.0], [2.0, 4.0], [3.0, 6.0], [4.0, 8.0]]);

[Fact(DisplayName = "列ごとの平均を求める")]
public void ComputesColumnMeans() => Assert.Equal([2.5, 5.0], Pca.ColumnMeans(Correlated));

[Fact(DisplayName = "分散共分散行列は対角に分散、それ以外に共分散を持つ")]
public void ComputesCovarianceMatrix()
{
    var covariance = Pca.CovarianceMatrix(Correlated);

    // 1 列目の不偏分散は ((1.5)^2 + (0.5)^2) * 2 / 3
    Assert.Equal(5.0 / 3.0, covariance[0, 0], 10);
    Assert.Equal(20.0 / 3.0, covariance[1, 1], 10);
    Assert.Equal(10.0 / 3.0, covariance[0, 1], 10);
    Assert.Equal(covariance[0, 1], covariance[1, 0], 10);
}
```

`Assert.Equal(期待値, 実測値, 10)` の 3 つ目の引数は、小数第何位まで比べるかです。xUnit のアナライザーは、この桁数を 0〜15 の範囲に収めるよう求めます（範囲外にすると `error xUnit2016` でビルドが止まります）。

### Green

中心化（各行から列の平均を引く）してから、列どうしの内積を件数 - 1 で割ります。

```csharp
// src/MachineLearning/Chapter13/Pca.cs
/// <summary>列ごとの平均。x の 1 行が 1 件のデータを表す。</summary>
public static IReadOnlyList<double> ColumnMeans(Matrix x)
{
    ArgumentNullException.ThrowIfNull(x);
    return [.. Enumerable.Range(0, x.ColumnCount)
        .Select(column => Enumerable.Range(0, x.RowCount).Average(row => x[row, column]))];
}

/// <summary>各行から列の平均を引く（中心化）。</summary>
public static Matrix Center(IReadOnlyList<double> means, Matrix x)
{
    ArgumentNullException.ThrowIfNull(means);
    ArgumentNullException.ThrowIfNull(x);
    return Matrix.FromRows([.. Enumerable.Range(0, x.RowCount).Select(row =>
        x.Row(row).Select((value, column) => value - means[column]).ToArray())]);
}

/// <summary>分散共分散行列。対角成分が列ごとの分散、それ以外が 2 列の共分散になる。</summary>
public static Matrix CovarianceMatrix(Matrix x)
{
    ArgumentNullException.ThrowIfNull(x);
    var columns = Center(ColumnMeans(x), x).Transpose();
    var n = (double)x.RowCount;
    return Matrix.FromRows([.. Enumerable.Range(0, columns.RowCount).Select(i =>
        Enumerable.Range(0, columns.RowCount)
            .Select(j => Matrix.Dot(columns.Row(i), columns.Row(j)) / (n - 1.0))
            .ToArray())]);
}
```

- `Transpose()` で行と列を入れ替えると、`Row(i)` が「i 列目の値の並び」になります。列どうしの内積が書きやすくなります
- `Matrix.Dot` は第 7 章で作った内積です。第 7 章では正規方程式を解くために作りましたが、ここでも使えます
- 件数 n ではなく n - 1 で割る（不偏分散）のは、F# 版・Python 版と合わせるためです

## 13.6 固有値分解を自作する

### ヤコビ法の考え方

対称行列の固有値分解の手順はいくつかありますが、**ヤコビ法** は「回転を繰り返して対角行列に近づける」という素直な考え方で、コードも短く書けます。

1. 対角成分以外の成分 `(p, q)` を選び、その成分が 0 になるような回転行列 `J` を作る
2. `A` を `Jᵀ A J` に置き換える。これは対称行列のまま、`(p, q)` 成分が 0 になる
3. 別の成分を 0 にすると、前に 0 にした成分が少し戻る。それでも対角成分以外の 2 乗和は必ず減るので、繰り返せば対角行列に近づく
4. 対角行列になったとき、対角成分が固有値、掛け合わせた回転行列 `J₁ J₂ …` の列が固有ベクトルになる

### 回転で「対角成分以外」が減ることをテストにする

```csharp
// tests/MachineLearning.Tests/Chapter13/EigenTests.cs
[Fact(DisplayName = "回転で対角成分以外の 2 乗和が小さくなる")]
public void RotationReducesOffDiagonal()
{
    var a = Matrix.FromRows([[2.0, 1.0], [1.0, 2.0]]);
    var j = Eigen.Rotation(a, 0, 1);

    var rotated = j.Transpose().Multiply(a).Multiply(j);

    Assert.Equal(2.0, Eigen.OffDiagonal(a), 10);
    Assert.Equal(0.0, Eigen.OffDiagonal(rotated), 15);
}
```

`Matrix` には `Transpose()` と `Multiply(Matrix)` があるので、`Jᵀ A J` をそのまま式として書けます。第 7 章の型を使い回すと、この章で行列の掛け算を書き直さずに済みます。

### 対角行列から始めて三角測量する

固有値分解のテストは、答えが手で分かるものから並べます。

```csharp
[Fact(DisplayName = "対角行列の固有値は対角成分、固有ベクトルは軸の向きになる")]
public void DecomposesDiagonalMatrix()
{
    var pairs = Eigen.Symmetric(Matrix.FromRows([[3.0, 0.0], [0.0, 1.0]]));

    Assert.Equal([3.0, 1.0], pairs.Select(pair => pair.Value));
    Assert.Equal([1.0, 0.0], pairs[0].Vector);
    Assert.Equal([0.0, 1.0], pairs[1].Vector);
}

[Fact(DisplayName = "固有値を大きい順に並べる")]
public void SortsByValueDescending()
{
    var pairs = Eigen.Symmetric(Matrix.FromRows([[1.0, 0.0], [0.0, 5.0]]));

    Assert.Equal([5.0, 1.0], pairs.Select(pair => pair.Value));
}

[Fact(DisplayName = "対角成分以外が 0 でない対称行列を分解する")]
public void DecomposesSymmetricMatrix()
{
    // [[2, 1], [1, 2]] の固有値は 3 と 1、固有ベクトルは (1, 1) と (1, -1) を長さ 1 にしたもの
    var pairs = Eigen.Symmetric(Matrix.FromRows([[2.0, 1.0], [1.0, 2.0]]));
    var unit = 1.0 / Math.Sqrt(2.0);

    Assert.Equal(3.0, pairs[0].Value, 10);
    Assert.Equal(1.0, pairs[1].Value, 10);
    Assert.Equal(unit, Math.Abs(pairs[0].Vector[0]), 10);
    Assert.Equal(unit, Math.Abs(pairs[0].Vector[1]), 10);
    Assert.Equal(pairs[0].Vector[0], pairs[0].Vector[1], 10);
    Assert.Equal(-pairs[1].Vector[0], pairs[1].Vector[1], 10);
}
```

固有ベクトルの成分そのものではなく、**絶対値と成分どうしの関係** を確かめていることに注意してください。この時点では符号の向きを決めていないので、`(0.707, 0.707)` でも `(-0.707, -0.707)` でも正解です。符号は 13.7 節で決めます。

最後に、定義そのものをテストにします。

```csharp
[Fact(DisplayName = "固有ベクトルは A v = λ v を満たし、長さが 1 になる")]
public void SatisfiesEigenEquation()
{
    var a = Matrix.FromRows([[4.0, 1.0, 2.0], [1.0, 3.0, 0.5], [2.0, 0.5, 5.0]]);

    foreach (var pair in Eigen.Symmetric(a))
    {
        var left = a.Multiply(pair.Vector);
        var right = pair.Vector.Select(value => pair.Value * value).ToList();
        Assert.Equal(1.0, Math.Sqrt(Matrix.Dot(pair.Vector, pair.Vector)), 10);
        for (var i = 0; i < left.Count; i++)
        {
            Assert.Equal(right[i], left[i], 10);
        }
    }
}
```

`3 × 3` の行列の固有値を手で求めるのは大変ですが、「定義を満たすか」なら手計算なしで確かめられます。答えを書かずに性質を書くテストです。

### Green: 回転を繰り返す

```csharp
// src/MachineLearning/Chapter13/Eigen.cs
/// <summary>固有値と、それに対応する長さ 1 の固有ベクトル。</summary>
/// <param name="Value">固有値</param>
/// <param name="Vector">長さ 1 の固有ベクトル</param>
public sealed record EigenPair(double Value, IReadOnlyList<double> Vector);

/// <summary>対称行列の固有値と固有ベクトルを、ヤコビ法で求める。</summary>
public static class Eigen
{
    /// <summary>対角成分以外の 2 乗和がこれ以下になったら、対角行列になったとみなす。</summary>
    public const double Tolerance = 1e-24;

    /// <summary>回転を繰り返す回数の上限（すべての非対角成分を 1 回ずつ回すのを 1 巡とする）。</summary>
    public const int MaxSweeps = 100;

    /// <summary>(p, q) 成分と (q, p) 成分を 0 にする、p・q の 2 つの軸の平面での回転行列。</summary>
    public static Matrix Rotation(Matrix a, int p, int q)
    {
        ArgumentNullException.ThrowIfNull(a);
        var theta = (a[q, q] - a[p, p]) / (2.0 * a[p, q]);
        var t = (theta >= 0.0 ? 1.0 : -1.0) / (Math.Abs(theta) + Math.Sqrt((theta * theta) + 1.0));
        var c = 1.0 / Math.Sqrt((t * t) + 1.0);
        var s = t * c;
        return Matrix.FromRows([.. Enumerable.Range(0, a.RowCount).Select(i =>
            Enumerable.Range(0, a.RowCount).Select(j => (i, j) switch
            {
                _ when (i == p && j == p) || (i == q && j == q) => c,
                _ when i == p && j == q => s,
                _ when i == q && j == p => -s,
                _ => i == j ? 1.0 : 0.0,
            }).ToArray())]);
    }

    /// <summary>
    /// 対称行列の固有値と固有ベクトルを、固有値の大きい順に返す（ヤコビ法）。
    /// 回転で対角行列に近づけると、対角成分が固有値、回転を掛け合わせた行列の列が固有ベクトルになる。
    /// </summary>
    public static IReadOnlyList<EigenPair> Symmetric(Matrix a)
    {
        ArgumentNullException.ThrowIfNull(a);
        var n = a.RowCount;
        var diagonal = a;
        var vectors = Identity(n);
        for (var sweep = 0; sweep < MaxSweeps && OffDiagonal(diagonal) > Tolerance; sweep++)
        {
            for (var p = 0; p < n - 1; p++)
            {
                for (var q = p + 1; q < n; q++)
                {
                    if (diagonal[p, q] != 0.0)
                    {
                        var j = Rotation(diagonal, p, q);
                        diagonal = j.Transpose().Multiply(diagonal).Multiply(j);
                        vectors = vectors.Multiply(j);
                    }
                }
            }
        }

        var columns = vectors.Transpose();
        return [.. Enumerable.Range(0, n)
            .Select(i => new EigenPair(diagonal[i, i], columns.Row(i)))
            .OrderByDescending(pair => pair.Value)];
    }
}
```

F# 版との書き方の違いが出るところです。

| 観点 | F# 版 | C# 版 |
|------|-------|-------|
| 1 巡の繰り返し | `List.fold` で `(a, v)` の組を畳み込む | `for` の二重ループで、ローカル変数を置き換える |
| 収束の判定 | 再帰関数 `diagonalize` に `[<TailCall>]` を付ける | `for` の継続条件に `OffDiagonal(diagonal) > Tolerance` を書く |
| 回転行列の組み立て | `Array.mapi` で単位行列を書き換える | `switch` 式のパターンで `(i, j)` ごとに値を決める |
| 組の表現 | レコード `{ Value; Vector }` | `sealed record EigenPair(double Value, IReadOnlyList<double> Vector)` |

`switch` 式の `_ when (i == p && j == p) || ...` は「条件付きのパターン」です。`(i, j)` のタプルに対して、当てはまる最初の腕の値を返します。F# のパターンマッチと同じ読み方ができます。

並べ替えに使った `OrderByDescending` は **安定** なので、固有値が同じ主成分の順序は、回転で得られた順のまま保たれます。F# の `List.sortByDescending` も安定なので、同じ並びになります。

## 13.7 主成分を求める

### 完全に相関する 2 列

2 列目が 1 列目のちょうど 2 倍なら、データは 1 本の直線に乗っています。第 1 主成分だけで全部を説明できるはずです。

```csharp
[Fact(DisplayName = "完全に相関する 2 列は、第 1 主成分だけで説明できる")]
public void ExplainsCorrelatedColumnsWithOneComponent()
{
    var model = Pca.Fit(2, Correlated);

    Assert.Equal(1.0, model.ExplainedVarianceRatio[0], 10);
    Assert.Equal(0.0, model.ExplainedVarianceRatio[1], 10);
    Assert.Equal([2.5, 5.0], model.Mean);
}
```

### 性質をテストにする

主成分は「長さ 1 で、たがいに直交する」「寄与率は大きい順で、合計が 1」という性質を持ちます。具体的な数値を書かずに、性質をそのままテストにできます。

```csharp
[Fact(DisplayName = "主成分は長さ 1 で、たがいに直交する")]
public void ComponentsAreOrthonormal()
{
    var model = Pca.Fit(3, Matrix.FromRows(
        [[1.0, 2.0, 0.5], [2.0, 1.0, 1.5], [3.0, 5.0, 0.0], [4.0, 3.0, 2.5], [5.0, 8.0, 1.0]]));

    for (var i = 0; i < model.Components.Count; i++)
    {
        Assert.Equal(1.0, Matrix.Dot(model.Components[i], model.Components[i]), 10);
        for (var j = i + 1; j < model.Components.Count; j++)
        {
            Assert.Equal(0.0, Matrix.Dot(model.Components[i], model.Components[j]), 10);
        }
    }
}
```

### 符号をそろえる

固有ベクトル `v` と `-v` は同じ向きを表すので、実装によって符号が逆になります。そのままだと「第 1 主成分で PRICE が正に効く」のか負に効くのかが実装ごとに変わってしまうので、**絶対値が最大の成分が正になる** という規則でそろえます。

```csharp
[Fact(DisplayName = "主成分の符号は、絶対値が最大の成分が正になるようにそろえる")]
public void NormalizesSigns()
{
    var normalized = Pca.NormalizeSigns([[-0.8, 0.6], [0.6, 0.8]]);

    Assert.Equal([0.8, -0.6], normalized[0]);
    Assert.Equal([0.6, 0.8], normalized[1]);
}
```

```csharp
/// <summary>固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の成分が正になるようにそろえる。</summary>
public static IReadOnlyList<IReadOnlyList<double>> NormalizeSigns(
    IReadOnlyList<IReadOnlyList<double>> components)
{
    ArgumentNullException.ThrowIfNull(components);
    return [.. components.Select(pc =>
    {
        var sign = Math.Sign(pc.MaxBy(Math.Abs));
        return (IReadOnlyList<double>)[.. pc.Select(value => value * sign)];
    })];
}
```

`MaxBy(Math.Abs)` は「絶対値が最大の要素」そのもの（符号付き）を返します。`Math.Sign` でその符号を取り出し、全体に掛けています。

### 学習の全体

```csharp
/// <summary>学習した主成分分析のモデル。Components の 1 行が 1 つの主成分を表す。</summary>
public sealed record PcaModel(
    IReadOnlyList<double> Mean,
    IReadOnlyList<IReadOnlyList<double>> Components,
    IReadOnlyList<double> ExplainedVariance,
    IReadOnlyList<double> ExplainedVarianceRatio);

/// <summary>分散共分散行列の固有値の大きい順に、nComponents 個の主成分を求める。</summary>
public static PcaModel Fit(int nComponents, Matrix x)
{
    ArgumentNullException.ThrowIfNull(x);
    var pairs = Eigen.Symmetric(CovarianceMatrix(x));
    var total = pairs.Sum(pair => pair.Value);
    var selected = pairs.Take(nComponents).ToList();
    return new PcaModel(
        ColumnMeans(x),
        NormalizeSigns([.. selected.Select(pair => pair.Vector)]),
        [.. selected.Select(pair => pair.Value)],
        [.. selected.Select(pair => pair.Value / total)]);
}
```

寄与率の分母は、**採用した主成分の固有値の合計ではなく、すべての固有値の合計** です。`nComponents` を減らしても寄与率の意味が変わらないようにしています。

## 13.8 データを主成分の向きに射影する

学習したモデルで、元のデータを主成分の座標に変換します。平均を引いてから、各主成分との内積を取るだけです。

```csharp
[Fact(DisplayName = "変換すると、平均を引いた値を主成分の向きに射影した座標になる")]
public void TransformsToComponentCoordinates()
{
    var model = Pca.Fit(1, Correlated);

    var transformed = Pca.Transform(model, Correlated);

    Assert.Equal(4, transformed.RowCount);
    Assert.Equal(1, transformed.ColumnCount);
    // 第 1 主成分の座標は、平均の位置で 0 になり、順に等間隔で並ぶ
    Assert.Equal(0.0, transformed[0, 0] + transformed[3, 0], 10);
    Assert.Equal(transformed[1, 0] - transformed[0, 0], transformed[2, 0] - transformed[1, 0], 10);
}
```

```csharp
/// <summary>平均を引いてから、主成分ごとの座標（主成分の向きとの内積）に変換する。</summary>
public static Matrix Transform(PcaModel model, Matrix x)
{
    ArgumentNullException.ThrowIfNull(model);
    ArgumentNullException.ThrowIfNull(x);
    var centered = Center(model.Mean, x);
    return Matrix.FromRows([.. Enumerable.Range(0, centered.RowCount).Select(row =>
        model.Components.Select(pc => Matrix.Dot(centered.Row(row), pc)).ToArray())]);
}
```

`Fit` で `nComponents` を絞れば、`Transform` の結果の列数もそのぶん減ります。これが次元削減です。

## 13.9 必要な主成分の数を求める

累積寄与率がしきい値に届くまでに、いくつの主成分が要るかを求めます。

```csharp
[Theory(DisplayName = "累積寄与率がしきい値に届くまでの主成分の数を求める")]
[InlineData(0.5, 1)]
[InlineData(0.8, 2)]
[InlineData(0.95, 3)]
public void CountsComponentsNeeded(double threshold, int expected) =>
    Assert.Equal(expected, Pca.ComponentsNeeded(threshold, [0.6, 0.25, 0.1, 0.05]));
```

```csharp
/// <summary>累積寄与率がしきい値に届くまでに必要な主成分の数。</summary>
public static int ComponentsNeeded(double threshold, IReadOnlyList<double> ratios)
{
    ArgumentNullException.ThrowIfNull(ratios);
    var sum = 0.0;
    for (var i = 0; i < ratios.Count; i++)
    {
        sum += ratios[i];
        if (sum >= threshold)
        {
            return i + 1;
        }
    }

    return ratios.Count;
}
```

F# 版は `Array.scan (+) 0.0` で累積和の配列を作り、`Array.findIndex` で最初に届く位置を探しました。C# 版は素直な `for` にしています。合計しながら途中で返す形にすると、しきい値に届かないときに `ratios.Count` を返す振る舞い（`findIndex` なら例外になる）も自然に書けます。

## 13.10 主成分への影響が大きい列を求める

主成分の「意味」を読むには、係数（**負荷量**）の絶対値が大きい列を見ます。

```csharp
[Fact(DisplayName = "主成分への影響が大きい列を、係数の絶対値の大きい順に返す")]
public void ListsTopLoadings()
{
    var loadings = Pca.TopLoadings(2, ["A", "B", "C"], [0.3, -0.9, 0.5]);

    Assert.Equal(["B", "C"], loadings.Select(pair => pair.Key));
    Assert.Equal([-0.9, 0.5], loadings.Select(pair => pair.Value));
}
```

```csharp
/// <summary>主成分の係数の絶対値が大きい順に、上位 k 個の列名と係数を返す。</summary>
public static IReadOnlyList<KeyValuePair<string, double>> TopLoadings(
    int k, IReadOnlyList<string> columns, IReadOnlyList<double> pc)
{
    ArgumentNullException.ThrowIfNull(columns);
    ArgumentNullException.ThrowIfNull(pc);
    return [.. columns.Zip(pc, KeyValuePair.Create)
        .OrderByDescending(pair => Math.Abs(pair.Value))
        .Take(k)];
}
```

並べ替えるのは絶対値ですが、返すのは **符号付きの係数** です。向きが読めないと意味を解釈できないからです。

## 13.11 Boston を前処理する

第 9 章で作った道具（`Dummies`・`Preprocessing`・`Standardizer`）を組み合わせるだけで、前処理が書けます。

```csharp
// src/MachineLearning/Chapter13/BostonStandardized.cs
/// <summary>
/// CRIME をダミー変数の列に置き換え、欠損値を列の平均値で補完してから、すべての列を標準化する。
/// 第 9 章と違い、この章は分割せずに全件を使う（教師なし学習なので正解ラベルが無い）。
/// </summary>
public static StandardizedTable Standardize(Table table)
{
    ArgumentNullException.ThrowIfNull(table);
    var categories = Dummies.Categories(table.Rows.Select(row => row.Text(Boston.Category)));
    var encoded = Dummies.Encode(table, Boston.Category, categories);
    var columns = encoded.Columns;
    var filled = Preprocessing.FillMissing(
        encoded.Rows, columns, Preprocessing.ColumnMeans(encoded.Rows, columns));
    var standardized = Standardizer.Fit(filled).Transform(filled);
    return new StandardizedTable(columns, Matrix.FromRows([.. standardized.Select(x => x.Values)]));
}
```

テストは、架空の値の表で 3 つの振る舞いを確かめます。

```csharp
// tests/MachineLearning.Tests/Chapter13/BostonStandardizedTests.cs
[Fact(DisplayName = "CRIME を先頭を除いたダミー変数の列にして、末尾に並べる")]
public void EncodesCategoryColumn()
{
    var table = BostonStandardized.Standardize(Sample());

    Assert.Equal(["ZN", "PRICE", "CRIME_low", "CRIME_very_low"], table.Columns);
    Assert.Equal(4, table.X.RowCount);
}

[Fact(DisplayName = "すべての列が平均 0・標準偏差 1 になる")]
public void StandardizesEveryColumn()
{
    var x = BostonStandardized.Standardize(Sample()).X;

    for (var column = 0; column < x.ColumnCount; column++)
    {
        var values = Enumerable.Range(0, x.RowCount).Select(row => x[row, column]).ToList();
        Assert.Equal(0.0, values.Average(), 10);
        Assert.Equal(1.0, Math.Sqrt(values.Average(value => value * value)), 10);
    }
}
```

`Standardizer` は第 9 章で作ったとおり、**件数 n で割る標準偏差**（母標準偏差）を使います。一方で分散共分散行列は n - 1 で割るので、標準化した列の不偏分散は 1 ではなく `n / (n - 1)` になります。実データのテストでは、この関係も確かめています。

```csharp
[Fact(DisplayName = "標準化した列の分散の合計は列数と等しく、寄与率の合計は 1 になる")]
public void RatiosSumToOne()
{
    this.SkipUnlessBoston();

    var table = BostonStandardized.Load(this.bostonFile);
    var model = Pca.Fit(table.Columns.Count, table.X);

    Assert.Equal(1.0, model.ExplainedVarianceRatio.Sum(), 10);
    // 標準偏差を件数 n で割って求めているので、不偏分散は n / (n - 1) になる
    Assert.Equal(table.Columns.Count * 100.0 / 99.0, model.ExplainedVariance.Sum(), 8);
}
```

ダミー変数の列名は、C# 版では `CRIME_low`・`CRIME_very_low` です（F# 版は `low`・`very_low`）。第 9 章の `Dummies.Encode` が「元の列名_カテゴリ」という名前を付けるので、その規則をこの章でもそのまま使っています。

## 13.12 実データで要約する

### 表示

```csharp
// src/MachineLearning/Chapter13/Program.cs
public static void Run(TextWriter output)
{
    ArgumentNullException.ThrowIfNull(output);
    var table = BostonStandardized.Load(Path.Combine(DataDir.Current(), "Boston.csv"));
    var model = Pca.Fit(table.Columns.Count, table.X);
    var ratios = model.ExplainedVarianceRatio;
    var needed = Pca.ComponentsNeeded(Threshold, ratios);
    var shown = ratios.Take(needed).ToList();
    output.WriteLine($"データ件数: {table.X.RowCount}, 列数: {table.Columns.Count}");
    output.WriteLine(
        "寄与率: " + string.Join(", ", shown.Select((ratio, i) => $"PC{i + 1} {Format(ratio, 4)}")));
    output.WriteLine(
        $"累積寄与率が {Format(Threshold, 1)} に届く主成分の数: {needed}（累積寄与率 {Format(shown.Sum(), 4)}）");
    for (var i = 0; i < ComponentsToExplain; i++)
    {
        var loadings = Pca.TopLoadings(TopK, table.Columns, model.Components[i]);
        output.WriteLine(
            $"第 {i + 1} 主成分で影響の大きい列: "
            + string.Join(", ", loadings.Select(pair => $"{pair.Key} {Format(pair.Value, 3)}")));
    }

    var mine = Pca.Transform(Pca.Fit(ComponentsToExplain, table.X), table.X);
    var library = MlNetPca.Project(ComponentsToExplain, false, 0, table.X);
    output.WriteLine(
        $"ML.NET の射影と一致した件数（第 {ComponentsToExplain} 主成分まで、符号を除く、許容誤差 {Tolerance}）: "
        + $"{CountAgreed(mine, library)}/{mine.RowCount}");
}
```

最後の 2 行は 13.13 節で足したものです（ML.NET との突き合わせ）。

数値の書式は `CultureInfo.InvariantCulture` を指定した `ToString("F4", ...)` にそろえています。指定しないと、実行環境のロケールによって小数点の文字が変わり、表示のテストが環境ごとに壊れます。

`Program.cs` の対応表に `["chapter13"] = Chapter13.Program.Run,` を加えます。

### 結果

```bash
dotnet run --project src/MachineLearning -- chapter13
```

```text
データ件数: 100, 列数: 15
寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581
累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）
第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328
第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405
ML.NET の射影と一致した件数（第 2 主成分まで、符号を除く、許容誤差 0.001）: 100/100
```

この表示をまるごと比べるテストを、実データのテストとして残しています。第 13 章のテストは 22 件です。

### 結果を読む

- **第 1 主成分だけで 4 割を説明する**: 15 列のばらつきのうち 41.1% を、1 本の軸で説明できます
- **15 列を 6 本の軸に要約できる**: 累積寄与率 0.8 を目安にすると、6 つの主成分で全体の 84.3% を説明できます
- **第 1 主成分は「産業化・都市化」の軸と読める**: 非小売業の土地の割合（INDUS）、窒素酸化物の濃度（NOX）、固定資産税率（TAX）が同じ向きに強く効いています
- **第 2 主成分は「住環境のよさ」の軸と読める**: 住宅価格（PRICE）、犯罪率が低い地区（CRIME_low）、部屋数（RM）が同じ向きに効いています

主成分の「意味」は計算で決まるものではなく、係数を見た人間の解釈です。符号の向きも 13.7 節の規則で決めたものなので、「値が大きいほど都市化している」のかどうかは、係数の符号とあわせて読む必要があります。

### F# 版・ほかの言語版との突き合わせ

寄与率（小数第 4 位まで）・主成分の係数（小数第 3 位まで）・必要な主成分の数は、[F# 版](../fsharp/13-principal-component-analysis.md)・Kotlin 版・Python 版と **すべて一致** しました。ダミー変数の列名だけが違います（C# 版は `CRIME_low`、F# 版は `low`）。

これまでの章では、分割の乱数が言語ごとに違うために数値が一致しないことがありました。この章は分割をしないので、同じ前処理と同じ数学から、NumPy の `eigh`・Tribuo の固有値分解・F# 版と C# 版のヤコビ法という 4 つの別々の実装が、同じ主成分にたどり着いたことになります。向きまでそろったのは、符号の規則を決めたからです。

## 13.13 ML.NET の主成分分析と突き合わせる

### ML.NET にあるもの・無いもの

ML.NET には `Transforms.ProjectToPrincipalComponents` があります。ただし、これは **主成分の座標に変換する変換器** であって、主成分そのものや寄与率を取り出す API はありません。学習用テストで確かめます。

```csharp
// tests/MachineLearning.Tests/Chapter13/MlNetPcaTests.cs
[Fact(DisplayName = "変換の結果は主成分の座標だけで、主成分や寄与率を取り出す API は無い")]
public void ExposesOnlyCoordinates()
{
    var members = typeof(PrincipalComponentAnalysisTransformer)
        .GetMembers()
        .Select(member => member.Name)
        .ToList();

    Assert.DoesNotContain(members, name => name.Contains("Eigen", StringComparison.Ordinal));
    Assert.DoesNotContain(members, name => name.Contains("Variance", StringComparison.Ordinal));
}
```

| この章で作ったもの | ML.NET で置き換えられるか |
|------------------|------------------------|
| `Pca.Transform`（主成分の座標） | 置き換えられる（`ProjectToPrincipalComponents`） |
| `Pca.Fit` の主成分・固有値・寄与率 | 置き換えられない（取り出す API が無い） |
| `Pca.ComponentsNeeded`・`Pca.TopLoadings` | 寄与率と主成分が要るので、置き換えられない |

この章の目的は「15 列を何本の軸に要約できるか」「どの列が効いているか」を読むことなので、ML.NET だけでは達成できません。自作を最終実装とし、ML.NET は **座標が合っているかを確かめる相手** として使います。

### 呼び出しの橋渡し

ML.NET は列の長さが実行時に決まるベクトルを扱えないので、第 14 章と同じく `SchemaDefinition` で列の型を指定します。

```csharp
// src/MachineLearning/Chapter13/MlNetPca.cs
public static Matrix Project(int rank, bool ensureZeroMean, int seed, Matrix x)
{
    ArgumentNullException.ThrowIfNull(x);
    var context = new MLContext(seed: seed);
    var schema = SchemaDefinition.Create(typeof(FeatureRow));
    schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, x.ColumnCount);
    var rows = Enumerable.Range(0, x.RowCount)
        .Select(row => new FeatureRow { Features = [.. x.Row(row).Select(value => (float)value)] })
        .ToList();
    var data = context.Data.LoadFromEnumerable(rows, schema);
    var transformed = context.Transforms
        .ProjectToPrincipalComponents("Projected", "Features", rank: rank, ensureZeroMean: ensureZeroMean, seed: seed)
        .Fit(data)
        .Transform(data);
    return Matrix.FromRows([.. context.Data
        .CreateEnumerable<ProjectedRow>(transformed, reuseRowObject: false)
        .Select(row => row.Projected.Select(value => (double)value).ToArray())]);
}
```

F# 版の ML.NET の橋渡しは `[<CLIMutable>]` を付けたレコードが要りましたが、C# では **書き換えられるプロパティを持つ普通のクラス** を書くだけで済みます。ML.NET が C# 向けに設計された API であることが出るところです。

### 学習用テストで癖を固定する

```csharp
[Fact(DisplayName = "平均を引かない設定（既定）では、中心化せずに主成分の向きへ射影する")]
public void DoesNotCenterByDefault()
{
    var projected = MlNetPca.Project(1, false, 0, Correlated);

    // 元の点と主成分の向き (1, 2) / sqrt(5) との内積そのもの。符号は逆向きになった
    Assert.Equal(-Math.Sqrt(5.0), projected[0, 0], 5);
    Assert.Equal(-2.0 * Math.Sqrt(5.0), projected[1, 0], 5);
}

[Fact(DisplayName = "平均を引く設定では、件数の少ない完全に相関したデータで NaN になる")]
public void ReturnsNaNForDegenerateData()
{
    // ランダム化 PCA は、既定で rank + 20 本の乱数ベクトルを使う。
    // 4 件・2 列のように件数が足りず、しかも 1 本の直線に乗っているデータでは値が定まらない
    var projected = MlNetPca.Project(1, true, 0, Correlated);

    Assert.True(double.IsNaN(projected[0, 0]));
}
```

確かめた癖は 3 つです。

1. `ensureZeroMean` の既定は `false` で、**平均を引かずに** 射影する（第 9 章の `NormalizeMeanVariance` の `fixZero` と同じ発想）。この章のデータは標準化済みで平均 0 なので、`false` のままで自作と比べられます
2. 同じ設定を、件数の足りない退化したデータに使うと **NaN** が返る。ML.NET の主成分分析は **ランダム化 PCA** で、乱数のベクトルを使って近似するためです
3. 主成分の **符号は自作と逆になることがある**（第 2 主成分が逆向きでした）。13.7 節で符号の規則を決めたのは自作の中だけの約束なので、ライブラリと比べるときは絶対値で比べます

### 実データで突き合わせる

```csharp
[Fact(DisplayName = "標準化した実データでは、自作の Transform と同じ座標になる（符号を除く）")]
public void AgreesWithMineOnRealData()
{
    Assert.SkipUnless(File.Exists(this.bostonFile), "学習データ Boston.csv が配置されていない（gulp data:setup）");

    var x = BostonStandardized.Load(this.bostonFile).X;
    var mine = Pca.Transform(Pca.Fit(2, x), x);
    var library = MlNetPca.Project(2, false, 0, x);

    for (var row = 0; row < x.RowCount; row++)
    {
        for (var pc = 0; pc < 2; pc++)
        {
            // float32 で計算するので、小数第 3 位までの一致にとどめる
            Assert.Equal(Math.Abs(mine[row, pc]), Math.Abs(library[row, pc]), 3);
        }
    }
}
```

章の実行でも、一致した件数を表示しています（13.12 節の最後の行）。100 件すべてで、第 1・第 2 主成分の座標が小数第 3 位まで一致しました。近似計算のランダム化 PCA と、厳密に解くヤコビ法が同じ座標にたどり着いたことになります。

### 自作とライブラリの役割

| 検証の手段 | 何を確かめたか |
|-----------|--------------|
| 固有値分解のテスト（`EigenTests`） | 固有値を大きい順に並べること、`A v = λ v` を満たす長さ 1 の固有ベクトルを返すこと、回転で対角成分以外が減ること |
| 性質のテスト（`PcaTests`） | 主成分が長さ 1 で直交すること、寄与率が大きい順で合計 1 になること、完全に相関する 2 列が 1 本の主成分で説明できること |
| 学習用テスト（`MlNetPcaTests`） | `ProjectToPrincipalComponents` が座標しか返さないこと、既定では中心化しないこと、退化したデータで NaN になること |
| 突き合わせ（`MlNetPcaTests`・`Program.Run`） | 実データの主成分の座標が、自作と ML.NET で一致すること |
| ほかの言語版との突き合わせ（`PcaDataTests`） | 実データの寄与率と負荷量が、F# 版・Kotlin 版・Python 版と一致すること |

## 13.14 可視化

C# 版では可視化を扱いません。累積寄与率の折れ線と、第 1・第 2 主成分の散布図は、[Python 版の第 13 章](../python/13-principal-component-analysis.md) と [Kotlin 版の第 13 章](../kotlin/13-principal-component-analysis.md) を参照してください。数値はこの章の実測値と一致するので、図を見てから戻ると、「6 本の軸で 8 割」という要約の意味がつかみやすくなります。

## 13.15 まとめ

この章では、固有値分解から主成分分析を自作し、15 列のデータを 6 本の軸に要約しました。

1. **ライブラリが返さないものは自分で作る** — ML.NET の `ProjectToPrincipalComponents` は座標しか返さず、寄与率も主成分も取り出せない。ヤコビ法は「回転で対角行列に近づける」という素直な手順なので、60 行ほどで書ける
2. **前の章の型を使い回す** — 行列は第 7 章の `Matrix`、前処理は第 9 章の `Dummies`・`Standardizer` をそのまま使った。`Transpose()` と `Multiply()` があるので `Jᵀ A J` が式のまま書ける
3. **答えを書けないときは性質を書く** — `3 × 3` の固有値は手で求められないが、`A v = λ v` と「長さ 1」「直交」「寄与率の合計が 1」ならテストにできる
4. **符号の規則を決めると、別の実装と比べられる** — 固有ベクトルの向きは一意に決まらない。「絶対値が最大の成分を正にする」と決めたことで、4 つの言語版の主成分が向きまで一致した
5. **標準化の分母に注意する** — `Standardizer` は n で割る母標準偏差、分散共分散行列は n - 1 で割る不偏分散。分散の合計が列数ぴったりにならない理由はここにあり、テストにも書いた
6. **近似の実装とも突き合わせられる** — ML.NET のランダム化 PCA は近似計算だが、実データでは自作と小数第 3 位まで同じ座標になった。ただし既定では平均を引かず、退化したデータでは NaN を返す
7. **xUnit の小数比較は 0〜15 桁** — `Assert.Equal(expected, actual, 20)` は `error xUnit2016` でビルドが止まる

次の章では、同じく教師なし学習の K-means でクラスタリングします。標準化した特徴量を使うところは、この章と同じです。
