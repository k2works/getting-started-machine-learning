module MachineLearning.Chapter14.MlNetKMeans

open Microsoft.ML
open Microsoft.ML.Data
open Microsoft.ML.Trainers
open MachineLearning.Chapter14.KMeans

/// ML.NET に渡す 1 行
[<CLIMutable>]
type PointRow = { Features: float32[] }

/// ML.NET の予測。クラスタ番号は 1 から始まる
[<CLIMutable>]
type ClusterPrediction = { PredictedLabel: uint32 }

/// ML.NET の K-means（初期中心は k-means++ で ML.NET が選ぶ）でクラスタリングし、クラスタ番号（0 から始まる）を返す
let private clusterWithMlNet (nClusters: int) (seed: int) (points: Point[]) : int[] =
    let context = MLContext(seed = seed)
    let schema = SchemaDefinition.Create(typeof<PointRow>)
    schema["Features"].ColumnType <- VectorDataViewType(NumberDataViewType.Single, points[0].Length)

    let data =
        context.Data.LoadFromEnumerable(points |> Array.map (fun p -> { Features = Array.map float32 p }), schema)

    let options =
        KMeansTrainer.Options(
            NumberOfClusters = nClusters,
            InitializationAlgorithm = KMeansTrainer.InitializationAlgorithm.KMeansPlusPlus,
            NumberOfThreads = System.Nullable 1
        )

    let model = context.Clustering.Trainers.KMeans(options).Fit(data)

    context.Data.CreateEnumerable<ClusterPrediction>(model.Transform data, reuseRowObject = false)
    |> Seq.map (fun prediction -> int prediction.PredictedLabel - 1)
    |> Seq.toArray

/// シードを 1 ずつずらして nInit 回クラスタリングし、自作と同じ式で求めた SSE の最小値を返す。
/// 中心は、ML.NET が割り当てたクラスタごとの点の平均とする
let mlNetBestSse (nClusters: int) (seed: int) (nInit: int) (points: Point[]) : float =
    [ seed .. seed + nInit - 1 ]
    |> List.map (fun s ->
        let labels = clusterWithMlNet nClusters s points
        let centers = updateCenters points labels (Array.replicate nClusters points[0])
        sumOfSquaredErrors points labels centers)
    |> List.min
