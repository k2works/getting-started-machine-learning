module MachineLearning.Chapter09.MlNetNormalization

open Microsoft.ML

/// ML.NET に渡す 1 行（1 列）
[<CLIMutable>]
type ValueRow = { Value: float32 }

/// ML.NET の NormalizeMeanVariance で 1 列の値を正規化する。fixZero が true（既定）なら平均を引かない
let normalizeMeanVariance (fixZero: bool) (values: float list) : float list =
    let context = MLContext(seed = 0)

    let data =
        context.Data.LoadFromEnumerable(values |> List.map (fun v -> { Value = float32 v }))

    let transformed =
        context.Transforms.NormalizeMeanVariance("Value", fixZero = fixZero).Fit(data).Transform(data)

    context.Data.CreateEnumerable<ValueRow>(transformed, reuseRowObject = false)
    |> Seq.map (fun row -> float row.Value)
    |> Seq.toList
