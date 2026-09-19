module MachineLearning.Chapter14.Main

open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter14.KMeans
open MachineLearning.Chapter14.MlNetKMeans
open MachineLearning.Chapter14.Spending
open MachineLearning.Chapter14.StatsKMeans

[<Literal>]
let Seed = 0

[<Literal>]
let NInit = 10

let ClusterCounts = [ 1..10 ]

[<Literal>]
let NClusters = 5

/// 2 つの結果が、同じクラスタ番号と（小数第 9 位まで）同じ SSE か
let private sameResult (a: KMeansResult) (b: KMeansResult) : bool =
    a.Labels = b.Labels && abs (a.Sse - b.Sse) < 1e-9

/// Wholesale.csv の支出額をクラスタリングし、SSE・ライブラリとの比較・クラスタごとの平均支出額を表示する
let run (print: string -> unit) : unit =
    let rows = loadSpending (Path.Combine(dataDir (), "Wholesale.csv"))
    let points = toStandardizedPoints SpendingColumns rows
    print $"データ件数: {rows.Length}（支出額 {SpendingColumns.Length} 列）"
    print $"クラスタ数ごとの SSE（初期中心 {NInit} 通りの最小値）:"
    print "クラスタ数\t自作\tML.NET（k-means++）"

    for n, sse in sseByClusterCount NInit Seed ClusterCounts points do
        print $"{n}\t{sse:F2}\t{mlNetBestSse n Seed NInit points:F2}"

    // 自作の試行と同じ初期中心を FSharp.Stats に渡し、結果が一致するかを数える
    let candidates =
        [
            for n in ClusterCounts do
                for i in 0 .. NInit - 1 -> chooseInitialCenters n (Seed + i) points
        ]

    let matched =
        candidates
        |> List.filter (fun centers -> sameResult (kmeans centers points) (kmeansWithFSharpStats centers points))
        |> List.length

    print $"同じ初期中心で FSharp.Stats と結果が一致した数: {matched} / {candidates.Length}"

    let result = kmeansWithRestarts NInit NClusters Seed points
    print ""
    print $"クラスタ数 {NClusters} のクラスタごとの件数と平均支出額:"
    print (String.concat "\t" ([ "クラスタ"; "件数" ] @ SpendingColumns))

    for summary in summarizeClusters SpendingColumns result.Labels rows do
        let means =
            SpendingColumns |> List.map (fun column -> $"{summary.Means[column]:F0}")

        print (String.concat "\t" ([ string summary.Cluster; string summary.Count ] @ means))
