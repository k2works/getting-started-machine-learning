---
type: Article
title: "第 14 章: K-means によるクラスタリング"
description: "初期中心を引数で受け取る K-means を C# で自作し、卸売業者の顧客 440 件をエルボー法でクラスタ数を選んでクラスタリングする。第 9 章の Standardizer で支出額をそろえ、ML.NET の K-means（k-means++）とは SSE を比べる。KMeansTrainer.Options に初期中心を渡す設定が無いことを学習用テストで固定する。"
tags: [article,getting-start-ml,csharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T01:41:47Z }
---

# 第 14 章: K-means によるクラスタリング

## 14.1 はじめに

前の章の主成分分析と同じく、**クラスタリング** も正解ラベルを使わない教師なし学習です。似たデータどうしを自動的にグループ（クラスタ）に分けます。

この章では、卸売業者の顧客 440 件の支出額を **K-means** でクラスタリングし、次の 2 つを求めます。

- **クラスタ数をいくつにするか**（エルボー法）
- **それぞれのクラスタがどんな顧客なのか**（クラスタごとの平均支出額）

[Python 版の第 14 章](../python/14-k-means-clustering.md)・[Kotlin 版の第 14 章](../kotlin/14-k-means-clustering.md)・[F# 版の第 14 章](../fsharp/14-k-means-clustering.md) と同じ題材です。C# 版では次の 3 点に注目してください。

- 初期中心を **引数で受け取る** 設計にして、乱数を外に追い出す。同じ初期中心なら結果が必ず同じになるので、テストが書きやすくなる
- 標準化は第 9 章の `Standardizer`（`Fit` / `Transform`）を **そのまま使う**
- ML.NET の K-means には **初期中心を渡す設定が無い**（[ADR 004](../../../adr/004-fsharp-ml-libraries.md) で F# 版が確かめた癖を、C# でも確かめます）。そのため割り当てそのものではなく、**SSE** を比べます

## 14.2 K-means の仕組み

K-means は、クラスタ数 `k` を先に決めて、次の 2 つを交互に繰り返します。

1. **割り当て**: それぞれの点を、最も近い中心のクラスタに入れる
2. **更新**: クラスタごとに、入っている点の平均を新しい中心にする

中心が動かなくなったら終わりです。結果の良さは **SSE**（各点と、その点が属するクラスタの中心との距離の 2 乗の合計）で測ります。SSE が小さいほど、クラスタの中がまとまっています。

K-means は初期中心の選び方によって、あまり良くない解（局所解）で止まることがあります。そこで、初期中心を何通りか試して、SSE が最小の結果を採ります。

## 14.3 TODO リストの作成

**TODO リスト**:

- [ ] 2 点間の距離（の 2 乗）を求める
- [ ] 各点を最も近い中心に割り当てる
- [ ] クラスタごとの平均を新しい中心にする
  - [ ] 点が 1 つも入らなかったクラスタは、前の中心を残す
- [ ] 中心が動かなくなるまで繰り返す
- [ ] SSE を求める
- [ ] 初期中心をシードで選び、何通りか試して最小の SSE を採る
- [ ] クラスタ数ごとの SSE を並べる（エルボー法）
- [ ] ML.NET の K-means と SSE を比べる
- [ ] Wholesale.csv の支出額を標準化してクラスタリングし、クラスタごとに集計する

## 14.4 割り当てと更新

### 点の表し方

点は「列の順に値を並べたもの」なので、`IReadOnlyList<double>` で表します。毎回この長い型名を書くのは読みにくいので、**using の別名** を使います。

```csharp
// src/MachineLearning/Chapter14/KMeans.cs
namespace MachineLearning.Chapter14;

using MachineLearning.Chapter02;
using Point = System.Collections.Generic.IReadOnlyList<double>;
```

F# の `type Point = float[]` に当たる書き方です。C# 12 からは、こうした構築された型にも別名を付けられます（それ以前は名前空間と名前付きの型だけでした）。別名はファイルの中だけで効くので、ほかのファイルからは `IReadOnlyList<double>` として見えます。

### Red

答えが目で見て分かる、架空の 4 点を使います。左下に 2 点、右上に 2 点です。

```csharp
// tests/MachineLearning.Tests/Chapter14/KMeansTests.cs
/// <summary>左下と右上に 2 つずつ、はっきり離れた架空の点。</summary>
private static readonly IReadOnlyList<Point> TwoGroups =
    [[0.0, 0.0], [0.0, 1.0], [10.0, 0.0], [10.0, 1.0]];

[Fact(DisplayName = "2 点間のユークリッド距離の 2 乗を求める")]
public void ComputesSquaredDistance() =>
    Assert.Equal(25.0, KMeans.SquaredDistance([0.0, 0.0], [3.0, 4.0]));

[Fact(DisplayName = "各点を最も近い中心に割り当てる")]
public void AssignsToNearestCenter() =>
    Assert.Equal([0, 0, 1, 1], KMeans.AssignClusters([[0.0, 0.0], [10.0, 0.0]], TwoGroups));

[Fact(DisplayName = "距離が同じなら、番号の小さい中心に割り当てる")]
public void PrefersSmallerIndexOnTie() =>
    Assert.Equal([0], KMeans.AssignClusters([[0.0], [2.0]], [[1.0]]));
```

3 つ目は、距離が同じときの振る舞いを決めるテストです。決めておかないと、同じデータでもクラスタ番号が入れ替わることがあります。

### Green

```csharp
/// <summary>2 点間のユークリッド距離の 2 乗。大小を比べるだけなので平方根は取らない。</summary>
public static double SquaredDistance(Point a, Point b)
{
    ArgumentNullException.ThrowIfNull(a);
    ArgumentNullException.ThrowIfNull(b);
    var sum = 0.0;
    for (var i = 0; i < a.Count; i++)
    {
        sum += (a[i] - b[i]) * (a[i] - b[i]);
    }

    return sum;
}

/// <summary>各点を、最も近い中心の番号（クラスタ番号）に割り当てる。</summary>
public static IReadOnlyList<int> AssignClusters(IReadOnlyList<Point> centers, IReadOnlyList<Point> points)
{
    ArgumentNullException.ThrowIfNull(centers);
    ArgumentNullException.ThrowIfNull(points);
    return [.. points.Select(point => Enumerable.Range(0, centers.Count)
        .MinBy(k => SquaredDistance(point, centers[k])))];
}
```

`MinBy` は、最小のものが複数あるときに **最初の要素** を返します。これで「距離が同じなら番号の小さい中心」という振る舞いになります（F# の `Array.minBy` と同じ）。

### 中心の更新

点が 1 つも入らなかったクラスタを、どう扱うかを決めておきます。

```csharp
[Fact(DisplayName = "点が 1 つも割り当てられなかったクラスタは、前の中心を残す")]
public void KeepsCenterOfEmptyCluster()
{
    var centers = KMeans.UpdateCenters(TwoGroups, [0, 0, 0, 0], [[0.0, 0.0], [99.0, 99.0]]);

    Assert.Equal([99.0, 99.0], centers[1]);
}
```

```csharp
/// <summary>クラスタごとに、割り当てられた点の平均を新しい中心にする。点が無ければ前の中心を残す。</summary>
public static IReadOnlyList<Point> UpdateCenters(
    IReadOnlyList<Point> points, IReadOnlyList<int> labels, IReadOnlyList<Point> previousCenters)
{
    ArgumentNullException.ThrowIfNull(points);
    ArgumentNullException.ThrowIfNull(labels);
    ArgumentNullException.ThrowIfNull(previousCenters);
    return [.. previousCenters.Select((previous, k) =>
    {
        var members = points.Where((_, i) => labels[i] == k).ToList();
        if (members.Count == 0)
        {
            return previous;
        }

        // コレクション式の前には型変換を書けないので、型を書いた変数に受けてから返す
        Point center = [.. Enumerable.Range(0, previous.Count).Select(j => members.Average(point => point[j]))];
        return center;
    })];
}
```

ここで C# の文法に引っかかりました。最初は三項演算子で

```csharp
return members.Count == 0 ? previous : (Point)[.. ...];
```

と書きましたが、コレクション式の前に型変換は書けません。

```text
error CS0119: 'IReadOnlyList<double>' は 種類 です。これは特定のコンテンツでは無効になります
error CS0029: 型 'System.Collections.Generic.IEnumerable<double>' を 'System.Index' に暗黙的に変換できません
```

`(Point)[...]` が「`Point` という変数の、添字アクセス」として読まれてしまい、`[..x]` が範囲演算子だと解釈されたためです。第 9 章の `new double[] { .. }` の落とし穴と同じ種類の誤解で、**コレクション式の `..` は `[...]` の中でしか使えない** ことが分かります。型を書いた変数に受けてから返すと解決します。

## 14.5 収束まで繰り返す

```csharp
[Fact(DisplayName = "離れた 2 つの集まりを、中心が動かなくなるまで繰り返して分ける")]
public void SeparatesTwoGroups()
{
    var result = KMeans.Fit([[0.0, 0.0], [9.0, 9.0]], TwoGroups);

    Assert.Equal([0, 0, 1, 1], result.Labels);
    Assert.Equal([0.0, 0.5], result.Centers[0]);
    Assert.Equal([10.0, 0.5], result.Centers[1]);
    Assert.Equal(1.0, result.Sse);
}

[Fact(DisplayName = "同じ初期中心なら、何度実行しても同じ結果になる")]
public void IsDeterministic()
{
    var first = KMeans.Fit([[0.0, 0.0], [9.0, 9.0]], TwoGroups);
    var second = KMeans.Fit([[0.0, 0.0], [9.0, 9.0]], TwoGroups);

    Assert.Equal(first.Labels, second.Labels);
    Assert.Equal(first.Sse, second.Sse);
}
```

```csharp
/// <summary>クラスタリングの結果。</summary>
/// <param name="Labels">点ごとのクラスタ番号</param>
/// <param name="Centers">クラスタごとの中心</param>
/// <param name="Sse">各点と、その点が属するクラスタの中心との距離の 2 乗の合計</param>
public sealed record KMeansResult(IReadOnlyList<int> Labels, IReadOnlyList<Point> Centers, double Sse);

/// <summary>
/// 中心が変わらなくなるか、反復回数が上限に達するまで、割り当てと中心の更新を繰り返す。
/// 初期中心を引数で受け取るので、同じ初期中心を渡せば何度でも同じ結果になる。
/// </summary>
public static KMeansResult Fit(int maxIterations, IReadOnlyList<Point> initialCenters, IReadOnlyList<Point> points)
{
    ArgumentNullException.ThrowIfNull(initialCenters);
    ArgumentNullException.ThrowIfNull(points);
    var centers = initialCenters;
    for (var i = 0; i < maxIterations; i++)
    {
        var next = UpdateCenters(points, AssignClusters(centers, points), centers);
        var settled = SameCenters(centers, next);
        centers = next;
        if (settled)
        {
            break;
        }
    }

    var labels = AssignClusters(centers, points);
    return new KMeansResult(labels, centers, SumOfSquaredErrors(points, labels, centers));
}
```

F# 版は再帰関数（`[<TailCall>]` 付き）で書きましたが、C# 版は `for` で書いています。反復回数の上限を指定しない呼び出しのために、既定値 300 を渡すオーバーロードを別に置きました。省略可能な引数（`int maxIterations = 300`）ではなくオーバーロードにしたのは、既定値が呼び出し側にコンパイル時に埋め込まれるのを避けるためです。

反復回数で打ち切る振る舞いも、テストで固定しておきます。

```csharp
[Fact(DisplayName = "反復回数の上限で打ち切ると、収束する前の中心になる")]
public void StopsAtMaxIterations()
{
    // 1 回目の更新では、右側の 2 点しか中心 (9, 9) に割り当てられない
    var result = KMeans.Fit(1, [[0.0, 0.0], [9.0, 9.0]], TwoGroups);

    Assert.Equal([0.0, 0.5], result.Centers[0]);
    Assert.Equal([10.0, 0.5], result.Centers[1]);
}
```

## 14.6 初期中心を変えてやり直す

初期中心の選び方が悪いと、SSE の大きい結果で止まります。それを実際に確かめるテストです。

```csharp
[Fact(DisplayName = "初期中心の候補のうち、SSE が最小の結果を選ぶ")]
public void ChoosesBestCandidate()
{
    // 同じ側の 2 点を初期中心にすると、右の集まりが 1 つのクラスタにまとまらない
    var bad = KMeans.Fit([[0.0, 0.0], [0.0, 1.0]], TwoGroups);

    var best = KMeans.Best([[[0.0, 0.0], [0.0, 1.0]], [[0.0, 0.0], [10.0, 0.0]]], TwoGroups);

    Assert.True(bad.Sse > best.Sse);
    Assert.Equal(1.0, best.Sse);
}
```

初期中心は、第 2 章の `Preprocessing.Shuffle`（シード付きの Fisher–Yates）で点の番号を並べ替えて、先頭から採ります。乱数を使うのはここだけで、`Fit` そのものは決定的です。

```csharp
/// <summary>点の番号をシード付きで並べ替え、先頭の nClusters 個の点を初期中心にする。</summary>
public static IReadOnlyList<Point> ChooseInitialCenters(int nClusters, int seed, IReadOnlyList<Point> points)
{
    ArgumentNullException.ThrowIfNull(points);
    return [.. Preprocessing.Shuffle([.. Enumerable.Range(0, points.Count)], seed)
        .Take(nClusters)
        .Select(i => points[i])];
}

/// <summary>シードを 1 ずつずらして初期中心を nInit 通り選び、SSE が最小の結果を返す。</summary>
public static KMeansResult FitWithRestarts(int nInit, int nClusters, int seed, IReadOnlyList<Point> points) =>
    Best(
        [.. Enumerable.Range(0, nInit).Select(i => ChooseInitialCenters(nClusters, seed + i, points))],
        points);
```

第 2 章の分割で使った `Shuffle` をそのまま使っているので、初期中心に選ばれる点は F# 版と同じになります。この章の SSE が F# 版と一致するのは、そのためです。

## 14.7 エルボー法でクラスタ数を選ぶ

クラスタ数を増やせば SSE は必ず下がります。極端には、点の数だけクラスタを作れば SSE は 0 です。そこで、クラスタ数を 1 から順に増やしながら SSE を並べ、下がり方が緩やかになる「ひじ（elbow）」のあたりを選びます。

```csharp
[Fact(DisplayName = "クラスタ数を増やすと SSE は下がる")]
public void SseDecreasesWithMoreClusters()
{
    var sse = KMeans.SseByClusterCount(5, 0, [1, 2, 3], TwoGroups);

    Assert.Equal([1, 2, 3], sse.Select(pair => pair.Key));
    Assert.True(sse[0].Value > sse[1].Value);
    Assert.True(sse[1].Value >= sse[2].Value);
}

[Fact(DisplayName = "クラスタ数 1 の SSE は、全体の平均からの距離の 2 乗の合計になる")]
public void SseOfSingleCluster() =>
    Assert.Equal(
        KMeans.SumOfSquaredErrors(TwoGroups, [0, 0, 0, 0], [[5.0, 0.5]]),
        KMeans.FitWithRestarts(3, 1, 0, TwoGroups).Sse,
        10);
```

```csharp
/// <summary>クラスタ数ごとに、初期中心を nInit 通り試した最小の SSE を求める（エルボー法）。</summary>
public static IReadOnlyList<KeyValuePair<int, double>> SseByClusterCount(
    int nInit, int seed, IReadOnlyList<int> clusterCounts, IReadOnlyList<Point> points)
{
    ArgumentNullException.ThrowIfNull(clusterCounts);
    return [.. clusterCounts.Select(n =>
        KeyValuePair.Create(n, FitWithRestarts(nInit, n, seed, points).Sse))];
}
```

F# 版は `(int * float) list`（タプルのリスト）を返しました。C# にもタプルはありますが、`foreach` で名前付きに分解できる `KeyValuePair<int, double>` のほうが、第 9 章までの書き方（`MeanCountByWeather` など）とそろいます。

## 14.8 ML.NET の K-means と比べる

### 初期中心は渡せない

F# 版は FSharp.Stats の K-means に **同じ初期中心を渡して** 結果が完全に一致することを確かめました。ML.NET にそれができるかを、学習用テストで確かめます。

```csharp
// tests/MachineLearning.Tests/Chapter14/MlNetKMeansTests.cs
[Fact(DisplayName = "KMeansTrainer.Options には初期中心を渡す設定が無い")]
public void HasNoInitialCentroidsOption()
{
    var names = typeof(KMeansTrainer.Options).GetFields().Select(field => field.Name).ToList();

    Assert.Contains("InitializationAlgorithm", names);
    Assert.DoesNotContain(names, name => name.Contains("Centroid", StringComparison.Ordinal));
}
```

選べるのは初期化の **アルゴリズム**（`InitializationAlgorithm`。この章では k-means++ を使います）だけで、中心そのものは渡せません。したがって、自作と同じ初期中心での突き合わせはできません。比べられるのは **結果の良さ（SSE）** です。

### 呼び出しの橋渡し

```csharp
// src/MachineLearning/Chapter14/MlNetKMeans.cs
/// <summary>ML.NET に渡す 1 行。列の長さは実行時に決まるので、SchemaDefinition で型を指定する。</summary>
public sealed class PointRow
{
    public float[] Features { get; set; } = [];
}

/// <summary>ML.NET の予測。クラスタ番号は 1 から始まる。</summary>
public sealed class ClusterPrediction
{
    public uint PredictedLabel { get; set; }
}

/// <summary>クラスタリングして、0 から始まるクラスタ番号を返す。</summary>
public static IReadOnlyList<int> Cluster(int nClusters, int seed, IReadOnlyList<Point> points)
{
    ArgumentNullException.ThrowIfNull(points);
    var context = new MLContext(seed: seed);
    var schema = SchemaDefinition.Create(typeof(PointRow));
    schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, points[0].Count);
    var data = context.Data.LoadFromEnumerable(
        points.Select(point => new PointRow { Features = [.. point.Select(value => (float)value)] }).ToList(),
        schema);
    var options = new KMeansTrainer.Options
    {
        NumberOfClusters = nClusters,
        InitializationAlgorithm = KMeansTrainer.InitializationAlgorithm.KMeansPlusPlus,
        NumberOfThreads = 1,
    };
    var model = context.Clustering.Trainers.KMeans(options).Fit(data);
    return [.. context.Data
        .CreateEnumerable<ClusterPrediction>(model.Transform(data), reuseRowObject: false)
        .Select(prediction => (int)prediction.PredictedLabel - 1)];
}
```

- 列の長さ（支出額の 6 列）は実行時に決まるので、`[VectorType(6)]` の属性では書けません。`SchemaDefinition.Create` してから `ColumnType` を差し替えます
- ML.NET のクラスタ番号は **1 から** 始まるので、1 を引いて自作とそろえます
- `NumberOfThreads = 1` にしているのは、スレッド数によって結果が変わらないようにするためです
- F# 版はここで `[<CLIMutable>]` を付けたレコードが要りましたが、C# では書き換えられるプロパティを持つ普通のクラスで済みます

振る舞いも学習用テストで固定します。

```csharp
[Fact(DisplayName = "クラスタ番号は 0 から始まる番号に直してある")]
public void ShiftsClusterNumbersToZeroBased() =>
    Assert.Equal([0, 1], MlNetKMeans.Cluster(2, 0, TwoGroups).Distinct().Order());

[Fact(DisplayName = "離れた 2 つの集まりを、自作と同じ分け方でクラスタリングする")]
public void SeparatesTwoGroups()
{
    var labels = MlNetKMeans.Cluster(2, 0, TwoGroups);

    // クラスタ番号の付き方は自作と違いうるので、同じ集まりが同じ番号になることだけを確かめる
    Assert.Equal(labels[0], labels[1]);
    Assert.Equal(labels[2], labels[3]);
    Assert.NotEqual(labels[0], labels[2]);
}
```

### SSE をそろえて比べる

ML.NET は SSE を直接返さないので、**自作と同じ式** で計算します。中心は、ML.NET が割り当てたクラスタごとの点の平均とします。

```csharp
/// <summary>
/// シードを 1 ずつずらして nInit 回クラスタリングし、自作と同じ式で求めた SSE の最小値を返す。
/// 中心は、ML.NET が割り当てたクラスタごとの点の平均とする。
/// </summary>
public static double BestSse(int nClusters, int seed, int nInit, IReadOnlyList<Point> points)
{
    ArgumentNullException.ThrowIfNull(points);
    return Enumerable.Range(seed, nInit).Min(s =>
    {
        var labels = Cluster(nClusters, s, points);
        var centers = KMeans.UpdateCenters(points, labels, [.. Enumerable.Repeat(points[0], nClusters)]);
        return KMeans.SumOfSquaredErrors(points, labels, centers);
    });
}
```

`UpdateCenters` の 3 つ目の引数（前の中心）には、点が 1 つも入らないクラスタが出たときのための置き場所として、先頭の点を並べたものを渡しています。

## 14.9 実データでクラスタリングする

### データの準備

`Wholesale.csv` は、卸売業者の顧客 440 件の、品目ごとの年間支出額です。`Channel`（販売チャネル）と `Region`（地域）は区分の番号で、数の大小に意味が無いので使いません。

支出額は品目によって桁が違うので、そろえないと支出額の大きい品目だけで距離が決まってしまいます。第 9 章の `Standardizer` で列ごとに平均 0・標準偏差 1 にします。

```csharp
// src/MachineLearning/Chapter14/Spending.cs
/// <summary>支出額の列。Channel と Region は区分の番号で大小に意味が無いので使わない。</summary>
public static readonly string[] Columns =
    ["Fresh", "Milk", "Grocery", "Frozen", "Detergents_Paper", "Delicassen"];

/// <summary>顧客ごとの支出額を、支出額の 6 列だけの特徴量として読み込む。</summary>
public static IReadOnlyList<Features> Load(string csvFile)
{
    var rows = Table.Load(csvFile).Rows;
    return Preprocessing.FillMissing(rows, Columns, Preprocessing.ColumnMeans(rows, Columns));
}

/// <summary>第 9 章の Standardizer で列ごとに標準化し、1 件を 1 つの点にする。</summary>
public static IReadOnlyList<Point> ToStandardizedPoints(IReadOnlyList<Features> rows)
{
    ArgumentNullException.ThrowIfNull(rows);
    return [.. Standardizer.Fit(rows, Columns).Transform(rows).Select(features => features.Values)];
}
```

第 9 章では、訓練データで `Fit` してテストデータにも `Transform` する、という使い方をしました。この章は分割しないので、全件で `Fit` して全件を `Transform` します。同じ型を、別の場面でそのまま使えています。

### クラスタごとの集計

```csharp
/// <summary>クラスタごとの件数と列ごとの平均を、件数の多い順に並べる。</summary>
public static IReadOnlyList<ClusterSummary> SummarizeClusters(
    IReadOnlyList<int> labels, IReadOnlyList<Features> rows)
{
    ArgumentNullException.ThrowIfNull(labels);
    ArgumentNullException.ThrowIfNull(rows);
    return [.. rows
        .Select((features, i) => (Cluster: labels[i], Features: features))
        .GroupBy(pair => pair.Cluster)
        .Select(group => new ClusterSummary(
            group.Key,
            group.Count(),
            Columns.ToDictionary(
                column => column,
                column => group.Average(pair => pair.Features.Value(column)),
                StringComparer.Ordinal)))
        .OrderByDescending(summary => summary.Count)];
}
```

平均は、標準化する **前** の支出額（元の単位）で求めます。標準化した値の平均では、金額として読めないからです。

### 結果

`Program.cs` の対応表に `["chapter14"] = Chapter14.Program.Run,` を加えて実行します。

```bash
dotnet run --project src/MachineLearning -- chapter14
```

```text
データ件数: 440（支出額 6 列）
クラスタ数ごとの SSE（初期中心 10 通りの最小値）:
クラスタ数	自作	ML.NET（k-means++）
1	2640.00	2640.00
2	1954.18	1954.80
3	1610.17	1627.76
4	1345.47	1312.60
5	1085.27	1060.26
6	947.20	924.69
7	865.72	822.82
8	752.61	746.01
9	666.15	666.67
10	605.62	605.19

クラスタ数 5 のクラスタごとの件数と平均支出額:
クラスタ	件数	Fresh	Milk	Grocery	Frozen	Detergents_Paper	Delicassen
4	265	8909	2967	3804	2248	989	962
0	96	5509	10556	16478	1420	7199	1659
1	65	31117	4260	5374	7225	849	2286
2	10	15965	34708	48537	3055	24875	2943
3	4	52022	31696	18491	29826	2699	19656
```

読み取れることは次のとおりです。

- **クラスタ数 1 の SSE は 2640**: 標準化した 6 列は平均 0・分散 1（件数で割る）なので、全体の平均を中心にしたときの SSE は「件数 × 列数」（440 × 6）にちょうどなります。これも実データのテストに残しています
- **ML.NET との差は小さい**: クラスタ数 4〜8 では ML.NET のほうが SSE が小さくなりました。自作の初期中心はランダムに選んだ点で、ML.NET は k-means++ で離れた点を選ぶので、10 通りのやり直しでも ML.NET のほうが良い局所解にたどり着いています。逆にクラスタ数 2・3・9 では自作のほうが小さく、どちらかが常に勝つわけではありません
- **ひじははっきりしない**: 2 から 5 までは大きく下がり、そこから緩やかになります。クラスタ数 5 前後が 1 つの目安です
- **クラスタ数 5 の顔ぶれ**: 件数の多い順に「全体に支出の少ない顧客（265 件）」「食料品・日用品（Grocery・Detergents_Paper）が多い顧客（96 件）」「生鮮品（Fresh）が多い顧客（65 件）」に分かれました。10 件・4 件の小さなクラスタは、特定の品目に大口の支出がある顧客です

### F# 版との突き合わせ

SSE の表（自作・ML.NET とも）、クラスタごとの件数と平均支出額は、[F# 版](../fsharp/14-k-means-clustering.md) と **すべて一致** しました。初期中心を選ぶシャッフルを第 2 章で F# 版とそろえたので、10 通りの初期中心が同じ点になり、同じ局所解にたどり着いたからです。ML.NET の側も、同じシードで同じ実装を呼んでいるので一致します。

一方、Python 版・Kotlin 版とは乱数生成器が違うので、SSE とクラスタの分かれ方は一致しません。

### 実データのテスト

表示をまるごと比べるテストと、クラスタ数 1 の SSE のテストを残しています。第 14 章のテストは 23 件です。

## 14.10 可視化

C# 版では可視化を扱いません。クラスタ数と SSE の折れ線（エルボー）と、クラスタごとの平均支出額の棒グラフは、[Python 版の第 14 章](../python/14-k-means-clustering.md) と [Kotlin 版の第 14 章](../kotlin/14-k-means-clustering.md) を参照してください。折れ線の形は 14.9 節の表の数値そのものなので、図と見比べると「ひじ」の読み方がつかめます。

## 14.11 まとめ

この章では、K-means を自作して 440 件の顧客を 5 つのクラスタに分けました。

1. **乱数を外に出すと、テストが書きやすくなる** — `Fit` は初期中心を引数で受け取るだけにして、乱数を使うのは `ChooseInitialCenters` に閉じ込めた。おかげで「同じ初期中心なら同じ結果」をそのままテストにできる
2. **前の章の道具をそのまま使う** — 標準化は第 9 章の `Standardizer`、初期中心のシャッフルは第 2 章の `Preprocessing.Shuffle`。新しく書いたのは K-means の本体だけ
3. **ライブラリの制約は学習用テストで固定する** — ML.NET の `KMeansTrainer.Options` に初期中心を渡す設定が無いことを、リフレクションのテストにした。だから「割り当てが同じか」ではなく「SSE がどちらが小さいか」を比べる、という判断の理由が残る
4. **比べるものをそろえる** — ML.NET は SSE を返さないので、自作と同じ式で計算した。数値を比べるときは、計算の仕方までそろえないと意味がない
5. **初期中心の選び方で結果が変わる** — k-means++ はランダムな選び方より良い解に届きやすいが、常に勝つわけではない（クラスタ数 2・3・9 では自作のほうが小さかった）
6. **コレクション式の `..` は `[...]` の中だけ** — `(Point)[.. values]` は添字アクセスと読まれて `CS0119`・`CS0029` になる。型を書いた変数に受けてから返す
7. **分割と同じ乱数をそろえた効果** — 第 2 章で F# 版と同じシャッフルにしておいたので、この章の SSE とクラスタの中身は F# 版と完全に一致した

これで、教師あり学習（第 3〜12 章）と教師なし学習（第 13〜14 章）がひととおりそろいました。次の章では、学習したモデルを Web API として公開し、モジュールとして設計します。
