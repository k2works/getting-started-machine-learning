module MachineLearning.Chapter07.RegressionMetrics

/// 残差（実測値 - 予測値）。List.map2 は件数が違うと ArgumentException を投げる
let private residuals (t: float list) (y: float list) : float list = List.map2 (-) t y

let meanAbsoluteError (t: float list) (y: float list) : float = residuals t y |> List.averageBy abs

let rootMeanSquaredError (t: float list) (y: float list) : float =
    residuals t y |> List.averageBy (fun r -> r * r) |> sqrt

/// 決定係数。1 - 誤差の 2 乗の合計 / 実測値と平均値の差の 2 乗の合計
let r2Score (t: float list) (y: float list) : float =
    let residual = residuals t y |> List.sumBy (fun r -> r * r)
    let mean = List.average t
    let total = t |> List.sumBy (fun value -> (value - mean) * (value - mean))
    1.0 - residual / total
