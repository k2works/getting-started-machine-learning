module MachineLearning.Chapter11.Experiments

open MachineLearning.Chapter01.KinokoTakenoko
open MachineLearning.Chapter03
open MachineLearning.Chapter10.Classifier
open MachineLearning.Chapter11.CrossValidation
open MachineLearning.Chapter11.Datasets
open MachineLearning.Chapter11.Metrics
open MachineLearning.Chapter11.Models

/// 評価関数ごとに、K 分割交差検証のスコアの平均を求めて、評価関数の名前と組にする
let evaluate
    (nSplits: int)
    (seed: int)
    (model: Model<'T>)
    (metrics: (string * Metric<'T>) list)
    (x: Map<string, float> list)
    (t: 'T list)
    : (string * float) list =
    let folds = kFold nSplits seed x.Length

    metrics
    |> List.map (fun (name, metric) -> name, crossValidate model metric folds x t |> Seq.average)

[<Literal>]
let NSplits = 5

[<Literal>]
let Seed = 0

[<Literal>]
let TreeDepth = 2

/// 生存を正例とする
[<Literal>]
let Survived = "1"

let SurvivedMetrics: (string * Metric<string>) list =
    [
        "正解率", (fun actual predicted -> accuracy predicted actual)
        "適合率", classificationMetric precision Survived
        "再現率", classificationMetric recall Survived
        "F値", classificationMetric f1Score Survived
    ]

let CinemaMetrics: (string * Metric<float>) list =
    [ "RMSE", rootMeanSquaredError; "MAE", meanAbsoluteError ]

/// 深さ 2 の決定木を第 11 章のモデルにする
let decisionTree: Model<string> =
    ofModel (DecisionTree.fit (Some TreeDepth)) DecisionTree.predict

let evaluateSurvived (csvFile: string) : (string * float) list =
    let x, t = SurvivedCsv.Load csvFile |> prepareSurvived

    evaluate NSplits Seed decisionTree SurvivedMetrics x t

let evaluateCinema (csvFile: string) : (string * float) list =
    let x, t = CinemaCsv.Load csvFile |> prepareCinema

    evaluate NSplits Seed linearRegression CinemaMetrics x t
