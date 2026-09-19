module MachineLearning.Chapter11.Metrics

/// 評価関数。正解と予測のリストを受け取り、スコアを返す
type Metric<'T> = 'T list -> 'T list -> float

/// 混同行列。正例についての当たり外れの件数
type ConfusionMatrix = { TP: int; FP: int; FN: int; TN: int }

/// 正解と予測を「実際は正例か、正例と予測したか」の組にして、組ごとに数える
let confusionMatrix (positive: 'T) (actual: 'T list) (predicted: 'T list) : ConfusionMatrix =
    let outcomes = List.map2 (fun a p -> a = positive, p = positive) actual predicted

    let count outcome =
        outcomes |> List.filter ((=) outcome) |> List.length

    {
        TP = count (true, true)
        FP = count (false, true)
        FN = count (true, false)
        TN = count (false, false)
    }

/// 割り算。分母が 0 なら 0 を返す
let private ratio (numerator: float) (denominator: float) : float =
    if denominator = 0.0 then 0.0 else numerator / denominator

/// 適合率。正例と予測したうち、本当に正例だった割合
let precision (cm: ConfusionMatrix) : float =
    ratio (float cm.TP) (float (cm.TP + cm.FP))

/// 再現率。本当の正例のうち、正例と予測できた割合
let recall (cm: ConfusionMatrix) : float =
    ratio (float cm.TP) (float (cm.TP + cm.FN))

/// F 値。適合率と再現率の調和平均
let f1Score (cm: ConfusionMatrix) : float =
    let p = precision cm
    let r = recall cm
    ratio (2.0 * p * r) (p + r)

/// 混同行列から求める指標と正例のラベルから、正解と予測から求める評価関数を作る
let classificationMetric (score: ConfusionMatrix -> float) (positive: 'T) : Metric<'T> =
    fun actual predicted -> confusionMatrix positive actual predicted |> score

/// 平均二乗誤差（MSE）
let meanSquaredError (actual: float list) (predicted: float list) : float =
    List.map2 (fun a p -> (p - a) * (p - a)) actual predicted |> List.average

/// 平均二乗誤差の平方根（RMSE）。正解と同じ単位になる
let rootMeanSquaredError (actual: float list) (predicted: float list) : float =
    meanSquaredError actual predicted |> sqrt

/// 平均絶対誤差（MAE）
let meanAbsoluteError (actual: float list) (predicted: float list) : float =
    List.map2 (fun a p -> abs (p - a)) actual predicted |> List.average
