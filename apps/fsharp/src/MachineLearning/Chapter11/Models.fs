module MachineLearning.Chapter11.Models

open FSharp.Stats
open FSharp.Stats.Fitting
open MachineLearning.Chapter11.CrossValidation

/// 行の Map を、列名の順（Map のキーの順）に並べた値のリストにする
let private valuesOf (row: Map<string, float>) : float list = row |> Map.values |> Seq.toList

/// 最小二乗法の線形回帰（FSharp.Stats の LinearRegression.fit）
let linearRegression: Model<float> =
    fun x t ->
        let coefficients = LinearRegression.fit (matrix (x |> List.map valuesOf), vector t)

        List.map (valuesOf >> vector >> coefficients.Predict)
