module MachineLearning.Tests.Chapter14.LibraryKMeansTest

open Xunit
open MachineLearning.Chapter14.KMeans
open MachineLearning.Chapter14.StatsKMeans
open MachineLearning.Chapter14.MlNetKMeans
open MachineLearning.Chapter14.Spending

/// 3 つのかたまりに分かれた 2 次元の 9 点
let threeGroups: Point[] =
    [|
        [| 0.0; 0.0 |]
        [| 0.0; 1.0 |]
        [| 1.0; 0.0 |]
        [| 10.0; 10.0 |]
        [| 10.0; 11.0 |]
        [| 11.0; 10.0 |]
        [| 0.0; 10.0 |]
        [| 1.0; 10.0 |]
        [| 0.0; 11.0 |]
    |]

[<Fact>]
let ``同じ初期中心なら FSharp.Stats の K-means と同じクラスタ番号と中心になる`` () =
    let initialCenters = chooseInitialCenters 3 0 threeGroups

    let mine = kmeans initialCenters threeGroups
    let library = kmeansWithFSharpStats initialCenters threeGroups

    Assert.Equal<int[]>(mine.Labels, library.Labels)
    Assert.Equal<Point[]>(mine.Centers, library.Centers)
    Assert.Equal(mine.Sse, library.Sse, 9)

[<Fact>]
let ``ML.NET の K-means でかたまりを分けたときの SSE を求める`` () =
    // かたまりの中の点は中心から 1/3 と 2/3 だけ離れる。3 つのかたまりで合わせて 3 × (2/9 + 5/9 + 5/9) = 4
    Assert.Equal(4.0, mlNetBestSse 3 0 5 threeGroups, 3)

[<Fact>]
let ``クラスタごとの件数と列ごとの平均を、件数の多い順に並べる`` () =
    let rows =
        [ 1.0; 2.0; 3.0; 10.0 ] |> List.map (fun fresh -> Map.ofList [ "Fresh", fresh ])

    let summaries = summarizeClusters [ "Fresh" ] [| 1; 0; 0; 1 |] rows

    Assert.Equal<ClusterSummary list>(
        [
            {
                Cluster = 1
                Count = 2
                Means = Map.ofList [ "Fresh", 5.5 ]
            }
            {
                Cluster = 0
                Count = 2
                Means = Map.ofList [ "Fresh", 2.5 ]
            }
        ],
        summaries
    )
