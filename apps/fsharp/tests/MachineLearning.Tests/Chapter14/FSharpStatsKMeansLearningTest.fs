module MachineLearning.Tests.Chapter14.FSharpStatsKMeansLearningTest

open Xunit
open FSharp.Stats
open FSharp.Stats.ML.Unsupervised

let twoGroups =
    [| [| 0.0; 0.0 |]; [| 0.0; 1.0 |]; [| 10.0; 10.0 |]; [| 10.0; 11.0 |] |]

/// 初期中心をそのまま返す関数。kmeans は「データとクラスタ数から初期中心を作る関数」を受け取る
let fixedCenters (centers: float[][]) : float[][] -> int -> float[][] = fun _ _ -> centers

[<Fact>]
let ``初期中心を返す関数を渡せて、クラスタの番号は 1 から始まる`` () =
    let result =
        IterativeClustering.kmeans
            DistanceMetrics.euclidean
            (fixedCenters [| [| 0.0; 0.0 |]; [| 0.0; 1.0 |] |])
            twoGroups
            2

    Assert.Equal<(int * float[])[]>([| 1, [| 0.0; 0.5 |]; 2, [| 10.0; 10.5 |] |], result.Centroids)
    Assert.Equal<int[]>([| 1; 1; 2; 2 |], twoGroups |> Array.map (result.Classifier >> fst))

[<Fact>]
let ``ClosestDistances の番号は 0 から始まり、距離は 2 乗しないユークリッド距離`` () =
    let result =
        IterativeClustering.kmeans
            DistanceMetrics.euclidean
            (fixedCenters [| [| 0.0; 0.0 |]; [| 0.0; 1.0 |] |])
            twoGroups
            2

    Assert.Equal<(int * float)[]>([| 0, 0.5; 0, 0.5; 1, 0.5; 1, 0.5 |], result.ClosestDistances)

[<Fact>]
let ``点が 1 つも割り当てられなかったクラスタは初期中心のまま残る`` () =
    let centers = [| [| 0.0; 0.0 |]; [| 99.0; 99.0 |]; [| 10.0; 10.0 |] |]

    let result =
        IterativeClustering.kmeans DistanceMetrics.euclidean (fixedCenters centers) twoGroups 3

    Assert.Equal<float[]>([| 99.0; 99.0 |], snd result.Centroids[1])
