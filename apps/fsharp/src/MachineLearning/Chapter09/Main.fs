module MachineLearning.Chapter09.Main

open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter09.BikeWeather
open MachineLearning.Chapter09.BostonFeatures
open MachineLearning.Chapter09.MlNetNormalization
open MachineLearning.Chapter09.Outliers
open MachineLearning.Chapter09.Polynomial
open MachineLearning.Chapter09.Standardizer

[<Literal>]
let TestSize = 0.3

[<Literal>]
let Seed = 0

/// 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす。
/// ML.NET は float32（単精度）で計算するので、誤差は 1e-9 より大きくなる
[<Literal>]
let ZeroTolerance = 1e-6

let Columns = [ "RM"; "LSTAT"; "PTRATIO" ]

let Squares = Columns |> List.map (fun column -> termName column column)

/// 比べる特徴量の組み合わせ（名前と、使う項）
let FeatureSets =
    [
        "元の特徴量", Columns
        "2 乗の項を追加", Columns @ Squares
        "交互作用の項も追加",
        Columns
        @ (pairsWithReplacement Columns |> List.map (fun (l, r) -> termName l r))
    ]

let private clean (value: float) : float =
    if abs value < ZeroTolerance then 0.0 else value

let private formatScores (train: float, test: float) : string =
    $"訓練 {clean train:F4}, テスト {clean test:F4}"

/// Boston.csv の特徴量エンジニアリングの結果と、bike.tsv・weather.csv の結合の結果を表示する
let run (print: string -> unit) : unit =
    let names, split =
        prepareBoston (Path.Combine(dataDir (), "Boston.csv")) TestSize Seed

    print $"訓練データ: {split.XTrain.Length} 件, テストデータ: {split.XTest.Length} 件"
    print $"""特徴量の列: {String.concat ", " names}"""

    let rm =
        split.XTrain
        |> transformStandardized (fitStandardizer [ "RM" ] split.XTrain)
        |> fitStandardizer [ "RM" ]

    print $"""標準化した訓練データの RM: 平均 {clean rm.Means["RM"]:F2}, 標準偏差 {clean rm.Stds["RM"]:F2}"""

    let mlNet =
        split.XTrain
        |> List.map (fun row -> row["RM"])
        |> normalizeMeanVariance false
        |> List.map (fun value -> Map.ofList [ "RM", value ])
        |> fitStandardizer [ "RM" ]

    print $"""ML.NET で正規化した訓練データの RM: 平均 {clean mlNet.Means["RM"]:F2}, 標準偏差 {clean mlNet.Stds["RM"]:F2}"""

    print "決定係数:"

    for name, terms in FeatureSets do
        print $"  {name}（{terms.Length} 列）: {formatScores (scoreFeatureSet split Columns terms)}"

    let outliers = iqrOutliers split.TTrain |> List.filter id
    print $"訓練データの PRICE の外れ値: {outliers.Length} 件"

    let removed =
        scoreFeatureSet (removeTargetOutliers split) Columns (Columns @ Squares)

    print $"  外れ値を除いて 2 乗の項を追加: {formatScores removed}"

    let joined =
        loadBike (Path.Combine(dataDir (), "bike.tsv"))
        |> joinWeather (loadWeather (Path.Combine(dataDir (), "weather.csv")))

    let means =
        meanCountByWeather joined
        |> List.map (fun (weather, count) -> $"{weather}={count:F1}")
        |> String.concat ", "

    print $"天気ごとの平均利用者数: {means}"
