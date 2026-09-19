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
