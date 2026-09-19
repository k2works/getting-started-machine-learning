module MachineLearning.Chapter07.StatsRegression

open FSharp.Stats
open FSharp.Stats.Fitting
open MachineLearning.Chapter07.LinearRegression

/// FSharp.Stats の最小二乗法で学習し、自作と同じ LinearModel の形で返す
let fitWithFSharpStats (x: Map<string, float> list) (t: float list) : LinearModel =
    let features = x.Head |> Map.keys |> Seq.toList
    let fitted = LinearRegression.fit (matrix (toMatrix features x), vector t)

    {
        Intercept = fitted.Constant
        Coefficients = List.zip features (List.ofSeq fitted.Coefficients |> List.tail) |> Map.ofList
    }

/// FSharp.Stats の決定係数。実測値・予測値の順に渡す
let r2WithFSharpStats (t: float list) (y: float list) : float =
    GoodnessOfFit.calculateDeterminationFromValue t y
