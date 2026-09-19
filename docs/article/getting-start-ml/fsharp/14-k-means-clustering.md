---
type: Article
title: "第 14 章: K-means によるクラスタリング"
description: "初期中心を引数で受け取る K-means を TDD で自作し、シードをずらしたやり直しで局所解を避け、エルボー法でクラスタ数を選ぶ。同じ初期中心を FSharp.Stats に渡して結果が一致することを確かめ、初期中心を渡せない ML.NET の K-means とは SSE を比べる。"
tags: [article,getting-start-ml,fsharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T08:26:09Z }
---

# 第 14 章: K-means によるクラスタリング

## 14.1 はじめに

ここまでの章は、正解ラベルのあるデータから学ぶ **教師あり学習** でした。この章では、正解ラベルの無いデータを、似たもの同士のまとまり（**クラスタ**）に分ける **教師なし学習** の代表、**K-means** を扱います。

題材は、卸売業者の顧客 440 件の、品目ごとの年間の支出額（`Wholesale.csv`）です。支出の傾向が似た顧客をまとめると、「食料品を大量に買う小売店」「生鮮品が中心の飲食店」のような顧客の種類が見えてきます。

[Python 版の第 14 章](../python/14-k-means-clustering.md)・[Kotlin 版の第 14 章](../kotlin/14-k-means-clustering.md)・[TypeScript 版の第 14 章](../typescript/14-k-means-clustering.md) と同じ題材で進めます。F# 版では、次の 3 点に注目してください。

- **初期中心を引数で受け取る** 設計にして、乱数を使わずにテストできるようにする
- 収束までの繰り返しを **末尾再帰** の関数で書き、`[<TailCall>]` でコンパイラに確かめさせる
- 同じ初期中心を渡せる FSharp.Stats とは結果を **突き合わせ**、渡せない ML.NET とは **比べるだけ** にする

## 14.2 K-means の仕組み

K-means は、クラスタの数 K を決めてから、次の 2 つを繰り返します。

1. **割り当て**: 各点を、最も近い中心のクラスタに割り当てる
2. **更新**: クラスタごとに、割り当てられた点の平均を新しい中心にする

割り当てが変わらなくなったら終わりです。まとまりの良さは、各点とその点が属するクラスタの中心との距離の 2 乗の合計、**SSE**（誤差平方和）で測ります。

最初の中心（**初期中心**）の選び方で、結果が変わります。悪い初期中心から始めると、それ以上良くならない中途半端な分け方（**局所解**）で止まることがあります。

## 14.3 TODO リストの作成

**TODO リスト**:

- [ ] 各点を最も近い中心に割り当てる
- [ ] クラスタごとの平均を新しい中心にする
  - [ ] 点が 1 つも無いクラスタは中心を変えない
- [ ] SSE を求める
- [ ] 割り当てが変わらなくなるまで繰り返す
  - [ ] 反復の上限で打ち切る
- [ ] 初期中心をシード付きで選ぶ
- [ ] 初期中心を変えてやり直し、SSE が最小の結果を使う
- [ ] クラスタ数ごとの SSE を求める（エルボー法）
- [ ] ライブラリと突き合わせる
- [ ] 実データでクラスタリングする

## 14.4 割り当てと更新

```fsharp
// tests/MachineLearning.Tests/Chapter14/KMeansTest.fs
[<Fact>]
let ``各点を最も近い中心のクラスタに割り当てる`` () =
    let points = [| [| 0.0 |]; [| 1.0 |]; [| 9.0 |]; [| 10.0 |] |]
    let centers = [| [| 0.0 |]; [| 10.0 |] |]

    Assert.Equal<int[]>([| 0; 0; 1; 1 |], assignClusters centers points)

[<Fact>]
let ``点が 1 つも割り当てられなかったクラスタは中心を変えない`` () =
    let points = [| [| 0.0; 0.0 |]; [| 2.0; 4.0 |] |]
    let previous = [| [| 0.0; 0.0 |]; [| 99.0; 99.0 |] |]

    Assert.Equal<Point[]>([| [| 1.0; 2.0 |]; [| 99.0; 99.0 |] |], updateCenters points [| 0; 0 |] previous)
```

```fsharp
// src/MachineLearning/Chapter14/KMeans.fs
/// 1 つの点。列の順に値を並べた配列
type Point = float[]

/// 2 点間のユークリッド距離の 2 乗
let squaredDistance (a: Point) (b: Point) : float =
    Array.map2 (fun x y -> (x - y) * (x - y)) a b |> Array.sum

/// 各点を、最も近い中心の番号（クラスタ番号）に割り当てる
let assignClusters (centers: Point[]) (points: Point[]) : int[] =
    points
    |> Array.map (fun point ->
        [| 0 .. centers.Length - 1 |]
        |> Array.minBy (fun k -> squaredDistance point centers[k]))

/// クラスタごとに、割り当てられた点の平均を新しい中心にする
let updateCenters (points: Point[]) (labels: int[]) (previousCenters: Point[]) : Point[] =
    previousCenters
    |> Array.mapi (fun k previous ->
        let members =
            Array.zip points labels
            |> Array.filter (fun (_, label) -> label = k)
            |> Array.map fst

        match members with
        | [||] -> previous
        | _ -> members |> Array.transpose |> Array.map Array.average)
```

- 点は `float[]`（配列）で表し、`type Point = float[]` と名前を付けます。第 2 章以降の `Map` ではなく配列にしたのは、K-means では列名を使わず、距離と平均の計算を繰り返すためです
- 距離の比較には、平方根をとらない「距離の 2 乗」を使います。大小関係は変わらず、計算が減ります
- `Array.minBy` は、最小の値を返す要素のうち最初のものを返します。2 つの中心から同じ距離の点は、番号の小さいクラスタに入ります
- `match members with | [||] -> previous` の `[||]` は、空の配列のパターンです。点が 1 つも無いクラスタの中心は、平均がとれないので元のまま残します

## 14.5 収束まで繰り返す

```fsharp
let twoGroups: Point[] =
    [| [| 0.0; 0.0 |]; [| 0.0; 1.0 |]; [| 10.0; 10.0 |]; [| 10.0; 11.0 |] |]

[<Fact>]
let ``割り当てが変わらなくなるまで割り当てと中心の更新を繰り返す`` () =
    let result = kmeans [| [| 0.0; 0.0 |]; [| 0.0; 1.0 |] |] twoGroups

    Assert.Equal(
        {
            Labels = [| 0; 0; 1; 1 |]
            Centers = [| [| 0.0; 0.5 |]; [| 10.0; 10.5 |] |]
            Sse = 1.0
        },
        result
    )
```

`kmeans` は、**初期中心を引数で受け取ります**。テストでは初期中心を直接書けるので、乱数に左右されずに、「どの初期中心から始めると、どこに落ち着くか」を確かめられます。

```fsharp
/// 中心が変わらなくなるか、残りの反復回数が 0 になるまで、割り当てと中心の更新を繰り返す
[<TailCall>]
let rec private iterate (iterationsLeft: int) (points: Point[]) (centers: Point[]) : Point[] =
    let next = updateCenters points (assignClusters centers points) centers

    if next = centers || iterationsLeft = 1 then
        next
    else
        iterate (iterationsLeft - 1) points next

/// 反復回数の上限を指定して、初期中心から K-means でクラスタリングする
let kmeansWith (maxIterations: int) (initialCenters: Point[]) (points: Point[]) : KMeansResult =
    let centers = iterate maxIterations points initialCenters
    let labels = assignClusters centers points

    {
        Labels = labels
        Centers = centers
        Sse = sumOfSquaredErrors points labels centers
    }

/// 反復回数の上限を既定値にした K-means
let kmeans: Point[] -> Point[] -> KMeansResult = kmeansWith MaxIterations
```

- 繰り返しを、ループではなく再帰関数 `iterate` で書いています。自分自身の呼び出しが関数の最後の処理（**末尾呼び出し**）なので、F# のコンパイラはこれをループに変換し、何回繰り返してもスタックを使い切りません
- `[<TailCall>]` は、「この関数は末尾再帰のはずだ」とコンパイラに伝える属性です。末尾呼び出しになっていなければ、コンパイラが警告（FS3569）を出します。第 5 章で有効にした FSharpLint の FL0085 が、`let rec` にこの属性を求めます
- `next = centers` は、配列の配列を `=` で比べています。F# の `=` は配列の中身まで比べる **構造的な比較** なので、中心の座標がすべて同じかを 1 行で判定できます
- `let kmeans = kmeansWith MaxIterations` は、`kmeansWith` に最初の引数だけを渡して、残りの 2 つを受け取る関数を作っています（**部分適用**）

## 14.6 初期中心を変えてやり直す

悪い初期中心から始めると、局所解に陥ります。テストで確かめます。

```fsharp
let threePairs: Point[] =
    [| 0.0; 1.0; 10.0; 11.0; 20.0; 21.0 |] |> Array.map Array.singleton

[<Fact>]
let ``初期中心によっては局所解に陥る`` () =
    let stuck = kmeans [| [| 0.0 |]; [| 1.0 |]; [| 10.0 |] |] threePairs

    Assert.Equal(101.0, stuck.Sse)

[<Fact>]
let ``複数の初期中心の候補のうち SSE が最小の結果を返す`` () =
    let candidates =
        [
            [| [| 0.0 |]; [| 1.0 |]; [| 10.0 |] |]
            [| [| 0.0 |]; [| 10.0 |]; [| 20.0 |] |]
        ]

    let result = bestKMeans candidates threePairs

    Assert.Equal(1.5, result.Sse)
    Assert.Equal<Point[]>([| [| 0.5 |]; [| 10.5 |]; [| 20.5 |] |], result.Centers)
```

0・1、10・11、20・21 の 3 組の点に、0・1・10 を初期中心として与えると、0 と 1 が別々のクラスタに分かれたまま止まり、SSE は 101 になります。0・10・20 から始めれば、3 組がきれいに分かれて SSE は 1.5 です。

```fsharp
/// 点の番号をシード付きで並べ替え、先頭の nClusters 個の点を初期中心にする
let chooseInitialCenters (nClusters: int) (seed: int) (points: Point[]) : Point[] =
    [ 0 .. points.Length - 1 ]
    |> shuffle seed
    |> List.truncate nClusters
    |> List.map (fun i -> points[i])
    |> List.toArray

/// 初期中心の候補ごとにクラスタリングし、SSE が最小の結果を返す
let bestKMeans (candidates: Point[] list) (points: Point[]) : KMeansResult =
    candidates
    |> List.map (fun initialCenters -> kmeans initialCenters points)
    |> List.minBy (fun result -> result.Sse)

/// シードを 1 ずつずらして初期中心を nInit 通り選び、SSE が最小の結果を返す
let kmeansWithRestarts (nInit: int) (nClusters: int) (seed: int) (points: Point[]) : KMeansResult =
    let candidates =
        [ for i in 0 .. nInit - 1 -> chooseInitialCenters nClusters (seed + i) points ]

    bestKMeans candidates points
```

- 初期中心は、第 2 章の `shuffle` で点の番号を並べ替え、先頭の K 個の点を選びます。データの中の点を選ぶので、重複しなければ空のクラスタは最初はできません
- 「乱数で初期中心を選ぶ」ことと「初期中心からクラスタリングする」ことを別の関数に分けたので、`bestKMeans` のテストでは初期中心の候補を直接書けます

## 14.7 エルボー法でクラスタ数を選ぶ

クラスタ数を増やすほど、各点は中心に近くなり、SSE は小さくなります。クラスタ数を点の数と同じにすれば SSE は 0 ですが、それでは分けた意味がありません。クラスタ数を 1 から増やしていき、SSE の減り方が緩やかになる「ひじ（エルボー）」のところを選ぶのが **エルボー法** です。

```fsharp
/// クラスタ数ごとに、初期中心を nInit 通り試した最小の SSE を求める（エルボー法）
let sseByClusterCount (nInit: int) (seed: int) (clusterCounts: int list) (points: Point[]) : (int * float) list =
    clusterCounts
    |> List.map (fun n -> n, (kmeansWithRestarts nInit n seed points).Sse)
```

```fsharp
[<Fact>]
let ``クラスタ数ごとにクラスタリングしたときの SSE を求める`` () =
    Assert.Equal<(int * float) list>([ 1, 201.0; 2, 1.0 ], sseByClusterCount 10 0 [ 1; 2 ] twoGroups)
```

## 14.8 ライブラリと突き合わせる

### FSharp.Stats の K-means を学習用テストで確かめる

FSharp.Stats の `IterativeClustering.kmeans` は、初期中心を作る関数を引数で受け取ります。自作と同じ初期中心を返す関数を渡せば、同じ条件で比べられます。使う前に、学習用テストで振る舞いを確かめました。

```fsharp
// tests/MachineLearning.Tests/Chapter14/FSharpStatsKMeansLearningTest.fs
/// 初期中心をそのまま返す関数。kmeans は「データとクラスタ数から初期中心を作る関数」を受け取る
let fixedCenters (centers: float[][]) : float[][] -> int -> float[][] = fun _ _ -> centers

[<Fact>]
let ``初期中心を返す関数を渡せて、クラスタの番号は 1 から始まる`` () =
    let result =
        IterativeClustering.kmeans DistanceMetrics.euclidean (fixedCenters [| [| 0.0; 0.0 |]; [| 0.0; 1.0 |] |]) twoGroups 2

    Assert.Equal<(int * float[])[]>([| 1, [| 0.0; 0.5 |]; 2, [| 10.0; 10.5 |] |], result.Centroids)
    Assert.Equal<int[]>([| 1; 1; 2; 2 |], twoGroups |> Array.map (result.Classifier >> fst))
```

分かったことは次の 3 つです。

- クラスタの番号は、自作（0 から）と違って **1 から** 始まる
- 別の結果 `ClosestDistances` の番号は 0 から始まり、距離は 2 乗しないユークリッド距離だった（番号の始まりが 1 つの結果の中でそろっていない）
- 点が 1 つも割り当てられなかったクラスタは、初期中心のまま残る（自作と同じ）

### 同じ初期中心なら結果が一致する

FSharp.Stats の結果を、番号を 0 から始まるように直して、自作と同じ形（`KMeansResult`）にします。

```fsharp
// src/MachineLearning/Chapter14/StatsKMeans.fs
/// FSharp.Stats の K-means に同じ初期中心を渡してクラスタリングし、自作と同じ形の結果にする。
/// FSharp.Stats のクラスタ番号は 1 から始まるので、0 から始まる番号に直す
let kmeansWithFSharpStats (initialCenters: Point[]) (points: Point[]) : KMeansResult =
    let result =
        IterativeClustering.kmeans DistanceMetrics.euclidean (fun _ _ -> initialCenters) points initialCenters.Length

    let labels = points |> Array.map (fun point -> fst (result.Classifier point) - 1)
    let centers = result.Centroids |> Array.sortBy fst |> Array.map snd

    {
        Labels = labels
        Centers = centers
        Sse = sumOfSquaredErrors points labels centers
    }
```

最初は `open FSharp.Stats.ML` の `DistanceMetrics` を使っていました。すると次のコンパイルエラーになりました。

```text
error FS0044: このコンストラクトは使用されなくなりました。Use FSharp.Stats.DistanceMetrics.euclidean instead
```

FSharp.Stats 0.6.0 では距離の関数の置き場が `FSharp.Stats.DistanceMetrics` に移り、古い場所は非推奨（`Obsolete`）になっています。非推奨の警告も、`TreatWarningsAsErrors` によってエラーになります。メッセージの案内どおりに `open FSharp.Stats` に直しました。

### ML.NET とは SSE を比べる

ML.NET の K-means は、初期中心を k-means++（離れた点を優先して選ぶ方法）などから選ぶだけで、初期中心そのものは渡せません（[ADR 004](../../../adr/004-fsharp-ml-libraries.md)）。そこで、ML.NET にクラスタリングさせた割り当てから、自作と同じ式で SSE を求めて比べます。

```fsharp
// src/MachineLearning/Chapter14/MlNetKMeans.fs
/// シードを 1 ずつずらして nInit 回クラスタリングし、自作と同じ式で求めた SSE の最小値を返す。
/// 中心は、ML.NET が割り当てたクラスタごとの点の平均とする
let mlNetBestSse (nClusters: int) (seed: int) (nInit: int) (points: Point[]) : float =
    [ seed .. seed + nInit - 1 ]
    |> List.map (fun s ->
        let labels = clusterWithMlNet nClusters s points
        let centers = updateCenters points labels (Array.replicate nClusters points[0])
        sumOfSquaredErrors points labels centers)
    |> List.min
```

- ML.NET のクラスタ番号も 1 から始まる（`uint32`）ので、`clusterWithMlNet` の中で 0 から始まる番号に直しています
- 3 つのかたまりに分かれた 9 点のテストでは、ML.NET の SSE は、手で計算した値（4）と一致しました

## 14.9 実データでクラスタリングする

### データの準備

`Channel`（販売チャネル）と `Region`（地域）は区分の番号で、数の大小に意味が無いので使いません。支出額の 6 列を、第 13 章の `standardize` で平均 0・標準偏差 1 にそろえます。支出額は品目によって桁が違うので、そろえないと支出額の大きい品目だけで距離が決まってしまいます。

### クラスタごとの集計

```fsharp
// src/MachineLearning/Chapter14/Spending.fs
/// クラスタごとの件数と列ごとの平均を、件数の多い順に並べる
let summarizeClusters (columns: string list) (labels: int[]) (rows: Map<string, float> list) : ClusterSummary list =
    List.zip (List.ofArray labels) rows
    |> List.groupBy fst
    |> List.map (fun (cluster, members) ->
        let memberRows = members |> List.map snd

        {
            Cluster = cluster
            Count = memberRows.Length
            Means =
                columns
                |> List.map (fun column -> column, memberRows |> List.averageBy (fun row -> row[column]))
                |> Map.ofList
        })
    |> List.sortByDescending (fun summary -> summary.Count)
```

平均は、標準化する前の支出額（元の単位）で求めます。

### 結果

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
同じ初期中心で FSharp.Stats と結果が一致した数: 100 / 100

クラスタ数 5 のクラスタごとの件数と平均支出額:
クラスタ	件数	Fresh	Milk	Grocery	Frozen	Detergents_Paper	Delicassen
4	265	8909	2967	3804	2248	989	962
0	96	5509	10556	16478	1420	7199	1659
1	65	31117	4260	5374	7225	849	2286
2	10	15965	34708	48537	3055	24875	2943
3	4	52022	31696	18491	29826	2699	19656
```

- クラスタ数 1 の SSE は 2640 です。標準化した 6 列は平均 0・分散 1 なので、全体の平均を中心にしたときの SSE は「件数 × 列数」（440 × 6）になります。これも実データのテストに残しています
- 同じ初期中心を渡した 100 通り（クラスタ数 1〜10 × 初期中心 10 通り）すべてで、FSharp.Stats と割り当てと SSE が一致しました
- ML.NET との SSE の差は、クラスタ数 4〜8 で自作のほうが大きくなりました。自作の初期中心はランダムに選んだ点で、ML.NET は k-means++ で離れた点を選ぶので、10 通りのやり直しでも ML.NET のほうが良い局所解にたどり着いています
- クラスタ数 5 では、件数の多い順に「全体に支出の少ない顧客（265 件）」「食料品・日用品（Grocery・Detergents_Paper）が多い顧客（96 件）」「生鮮品（Fresh）が多い顧客（65 件）」などに分かれました。件数が 10 件・4 件の小さなクラスタは、特定の品目に大口の支出がある顧客です

乱数生成器が他の版と違うので、SSE とクラスタの分かれ方は他の版と一致しません。

### 実データのテスト

表示をまるごと比べるテストと、クラスタ数 1 の SSE のテストを残しています。第 14 章のテストは 25 件です。

## 14.10 Notebook で探索する

Notebook は `apps/fsharp/notebooks/chapter14_kmeans_exploration.ipynb` にあります。Polyglot Notebooks が廃止されていることは、[第 2 章の 2.10 節](02-data-preprocessing-and-triangulation.md) を参照してください。

```fsharp
let counts = [ 1..10 ]
let mine = sseByClusterCount 10 0 counts points |> List.map snd
let library = counts |> List.map (fun n -> mlNetBestSse n 0 10 points)

[
    Chart.Line(x = counts, y = mine, Name = "自作（初期中心 10 通り）")
    Chart.Line(x = counts, y = library, Name = "ML.NET（k-means++）")
]
|> Chart.combine
|> Chart.withTitle "クラスタ数と SSE"
|> Chart.withXAxisStyle "クラスタ数"
|> Chart.withYAxisStyle "SSE"
```

クラスタ数と SSE の折れ線は、クラスタ数 2〜5 までは大きく下がり、それ以降は下がり方が緩やかになります。はっきりした「ひじ」は見えにくいものの、クラスタ数 5 前後が 1 つの目安になります。自作と ML.NET の 2 本の線はほぼ重なり、差はクラスタ数 4〜8 で数十程度です。

クラスタ数 5 の平均支出額を品目ごとの棒グラフにすると、クラスタごとに支出の多い品目がはっきり違うことが分かります。数値は 14.9 節の表のとおりです。

グラフの画像は、第 2 章と同じ理由で記事に載せていません。

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter14/KMeans.fs）</summary>

```fsharp
module MachineLearning.Chapter14.KMeans

open MachineLearning.Chapter02.Random

/// 1 つの点。列の順に値を並べた配列
type Point = float[]

/// 2 点間のユークリッド距離の 2 乗
let squaredDistance (a: Point) (b: Point) : float =
    Array.map2 (fun x y -> (x - y) * (x - y)) a b |> Array.sum

/// 各点を、最も近い中心の番号（クラスタ番号）に割り当てる
let assignClusters (centers: Point[]) (points: Point[]) : int[] =
    points
    |> Array.map (fun point ->
        [| 0 .. centers.Length - 1 |]
        |> Array.minBy (fun k -> squaredDistance point centers[k]))

/// クラスタごとに、割り当てられた点の平均を新しい中心にする
let updateCenters (points: Point[]) (labels: int[]) (previousCenters: Point[]) : Point[] =
    previousCenters
    |> Array.mapi (fun k previous ->
        let members =
            Array.zip points labels
            |> Array.filter (fun (_, label) -> label = k)
            |> Array.map fst

        match members with
        | [||] -> previous
        | _ -> members |> Array.transpose |> Array.map Array.average)

/// クラスタリングの結果。Labels は点ごとのクラスタ番号
type KMeansResult =
    {
        Labels: int[]
        Centers: Point[]
        Sse: float
    }

/// SSE。各点と、その点が属するクラスタの中心との距離の 2 乗の合計
let sumOfSquaredErrors (points: Point[]) (labels: int[]) (centers: Point[]) : float =
    Array.map2 (fun point label -> squaredDistance point centers[label]) points labels
    |> Array.sum

/// 反復回数の上限の既定値
[<Literal>]
let MaxIterations = 300

/// 中心が変わらなくなるか、残りの反復回数が 0 になるまで、割り当てと中心の更新を繰り返す
[<TailCall>]
let rec private iterate (iterationsLeft: int) (points: Point[]) (centers: Point[]) : Point[] =
    let next = updateCenters points (assignClusters centers points) centers

    if next = centers || iterationsLeft = 1 then
        next
    else
        iterate (iterationsLeft - 1) points next

/// 反復回数の上限を指定して、初期中心から K-means でクラスタリングする
let kmeansWith (maxIterations: int) (initialCenters: Point[]) (points: Point[]) : KMeansResult =
    let centers = iterate maxIterations points initialCenters
    let labels = assignClusters centers points

    {
        Labels = labels
        Centers = centers
        Sse = sumOfSquaredErrors points labels centers
    }

/// 反復回数の上限を既定値にした K-means
let kmeans: Point[] -> Point[] -> KMeansResult = kmeansWith MaxIterations

/// 点の番号をシード付きで並べ替え、先頭の nClusters 個の点を初期中心にする
let chooseInitialCenters (nClusters: int) (seed: int) (points: Point[]) : Point[] =
    [ 0 .. points.Length - 1 ]
    |> shuffle seed
    |> List.truncate nClusters
    |> List.map (fun i -> points[i])
    |> List.toArray

/// 初期中心の候補ごとにクラスタリングし、SSE が最小の結果を返す
let bestKMeans (candidates: Point[] list) (points: Point[]) : KMeansResult =
    candidates
    |> List.map (fun initialCenters -> kmeans initialCenters points)
    |> List.minBy (fun result -> result.Sse)

/// シードを 1 ずつずらして初期中心を nInit 通り選び、SSE が最小の結果を返す
let kmeansWithRestarts (nInit: int) (nClusters: int) (seed: int) (points: Point[]) : KMeansResult =
    let candidates =
        [ for i in 0 .. nInit - 1 -> chooseInitialCenters nClusters (seed + i) points ]

    bestKMeans candidates points

/// クラスタ数ごとに、初期中心を nInit 通り試した最小の SSE を求める（エルボー法）
let sseByClusterCount (nInit: int) (seed: int) (clusterCounts: int list) (points: Point[]) : (int * float) list =
    clusterCounts
    |> List.map (fun n -> n, (kmeansWithRestarts nInit n seed points).Sse)
```

</details>

## 14.11 まとめ

この章では、正解ラベルの無いデータを K-means でクラスタに分けました。

1. **初期中心を引数で受け取る** — 乱数で初期中心を選ぶことと、初期中心からクラスタリングすることを別の関数に分け、局所解に陥る例もテストで再現した
2. **末尾再帰と構造的な比較** — 収束までの繰り返しを末尾再帰の関数で書き、`[<TailCall>]` で確かめた。中心が変わったかは、配列の配列を `=` で比べて判定した
3. **やり直しとエルボー法** — 初期中心を変えて SSE が最小の結果を使い、クラスタ数ごとの SSE の減り方でクラスタ数を選んだ
4. **渡せるものは突き合わせ、渡せないものは比べる** — FSharp.Stats とは同じ初期中心で 100 通りすべて一致した。初期中心を渡せない ML.NET とは SSE を比べ、k-means++ の初期中心のほうが良い局所解に届くことがあると分かった
5. **非推奨の警告もエラーにする** — FSharp.Stats の古い名前空間の `DistanceMetrics` は FS0044 になり、案内どおりに新しい場所に直した

次の章では、これまでに作ったモデルを API として公開し、モジュールの設計を扱います。
