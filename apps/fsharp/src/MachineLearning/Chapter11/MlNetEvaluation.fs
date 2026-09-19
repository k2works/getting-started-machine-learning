module MachineLearning.Chapter11.MlNetEvaluation

open Microsoft.ML
open Microsoft.ML.Data

/// 2 値分類の評価に渡す 1 行。ML.NET は正解（Label）・予測（PredictedLabel）・得点（Score）の列を読む
[<CLIMutable>]
type BinaryOutcome =
    {
        Label: bool
        PredictedLabel: bool
        Score: float32
    }

/// 回帰の評価に渡す 1 行。予測は Score の列に入れる
[<CLIMutable>]
type RegressionOutcome = { Label: float32; Score: float32 }

/// 正解と予測を ML.NET の 2 値分類の評価に渡す。得点を持たない予測なので、確率を使わない評価（NonCalibrated）にする
let evaluateBinary (positive: 'T) (actual: 'T list) (predicted: 'T list) : BinaryClassificationMetrics =
    let context = MLContext(seed = 0)

    let rows =
        List.map2
            (fun a p ->
                {
                    Label = (a = positive)
                    PredictedLabel = (p = positive)
                    Score = if p = positive then 1.0f else -1.0f
                })
            actual
            predicted

    context.BinaryClassification.EvaluateNonCalibrated(context.Data.LoadFromEnumerable rows)

/// 正解と予測を ML.NET の回帰の評価に渡す
let evaluateRegression (actual: float list) (predicted: float list) : RegressionMetrics =
    let context = MLContext(seed = 0)

    let rows =
        List.map2
            (fun a p ->
                {
                    RegressionOutcome.Label = float32 a
                    Score = float32 p
                })
            actual
            predicted

    context.Regression.Evaluate(context.Data.LoadFromEnumerable rows)
