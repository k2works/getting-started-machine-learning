module MachineLearning.Tests.Chapter07.StatsRegressionTest

open System
open Xunit
open FSharp.Stats
open FSharp.Stats.Fitting
open MachineLearning.Chapter07.LinearRegression
open MachineLearning.Chapter07.RegressionMetrics
open MachineLearning.Chapter07.StatsRegression
open MachineLearning.Tests.Chapter07.MatrixTest

[<Fact>]
let ``学習用テスト: LinearRegression.fit は行を 1 件のデータとする行列を受け取り、切片・係数の順のベクトルを返す`` () =
    let a = [ 0.0; 1.0; 0.0; 2.0; 1.0 ]
    let b = [ 0.0; 0.0; 1.0; 1.0; 3.0 ]
    let xData = matrix (List.map2 (fun ai bi -> [ ai; bi ]) a b)
    let yData = vector (List.map2 (fun ai bi -> 3.0 * ai - 2.0 * bi + 5.0) a b)

    let coefficients = LinearRegression.fit (xData, yData)

    assertValues [ 5.0; 3.0; -2.0 ] coefficients.Coefficients

[<Fact>]
let ``学習用テスト: calculateDeterminationFromValue は実測値・予測値の順に受け取る`` () =
    let t = [ 3.0; 5.0; 7.0 ]
    let y = [ 2.0; 5.0; 9.0 ]

    Assert.Equal(1.0 - 5.0 / 8.0, GoodnessOfFit.calculateDeterminationFromValue t y, 12)
    Assert.NotEqual(1.0 - 5.0 / 8.0, GoodnessOfFit.calculateDeterminationFromValue y t)

/// t = 4 + 1.5a - 0.5b + 2c にノイズを加えた 30 件
let noisyDataset () : Map<string, float> list * float list =
    let random = Random 0

    let rows =
        List.init 30 (fun _ ->
            Map.ofList
                [
                    "a", random.NextDouble() * 10.0
                    "b", random.NextDouble() * 10.0
                    "c", random.NextDouble() * 10.0
                ])

    let t =
        rows
        |> List.map (fun row ->
            let noise = random.NextDouble() - 0.5
            4.0 + 1.5 * row["a"] - 0.5 * row["b"] + 2.0 * row["c"] + noise)

    rows, t

[<Fact>]
let ``FSharp.Stats の切片と係数は自作の線形回帰と一致する`` () =
    let x, t = noisyDataset ()

    let mine = fitLinearRegression x t
    let library = fitWithFSharpStats x t

    Assert.Equal(mine.Intercept, library.Intercept, 9)
    Assert.Equal<string seq>(mine.Coefficients.Keys, library.Coefficients.Keys)
    assertValues mine.Coefficients.Values library.Coefficients.Values

[<Fact>]
let ``FSharp.Stats の決定係数は自作の決定係数と一致する`` () =
    let x, t = noisyDataset ()
    let y = predictLinearRegression (fitLinearRegression x t) x

    Assert.Equal(r2Score t y, r2WithFSharpStats t y, 12)
