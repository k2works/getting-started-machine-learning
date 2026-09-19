module MachineLearning.Chapter14.StatsKMeans

open FSharp.Stats
open FSharp.Stats.ML.Unsupervised
open MachineLearning.Chapter14.KMeans

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
