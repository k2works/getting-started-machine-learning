module MachineLearning.Tests.Chapter07.LinearRegressionTest

open Xunit
open MachineLearning.Chapter07.LinearRegression
open MachineLearning.Tests.Chapter07.MatrixTest

/// 切片と係数が小数第 9 位まで一致し、係数の列名が同じことを確かめる
let assertModel (intercept: float) (coefficients: (string * float) list) (actual: LinearModel) =
    Assert.Equal(intercept, actual.Intercept, 9)
    Assert.Equal<string list>(List.map fst coefficients, actual.Coefficients |> Map.keys |> Seq.toList)
    assertValues (List.map snd coefficients) actual.Coefficients.Values

[<Fact>]
let ``直線上の点から切片と係数を求める`` () =
    let x = [ 0.0; 1.0; 2.0; 3.0 ] |> List.map (fun value -> Map.ofList [ "x", value ])
    let t = [ 1.0; 3.0; 5.0; 7.0 ]

    let model = fitLinearRegression x t

    assertModel 1.0 [ "x", 2.0 ] model

[<Fact>]
let ``複数の特徴量から切片と係数を求める`` () =
    let a = [ 0.0; 1.0; 0.0; 2.0; 1.0 ]
    let b = [ 0.0; 0.0; 1.0; 1.0; 3.0 ]
    let x = List.map2 (fun ai bi -> Map.ofList [ "a", ai; "b", bi ]) a b
    let t = List.map2 (fun ai bi -> 3.0 * ai - 2.0 * bi + 5.0) a b

    let model = fitLinearRegression x t

    assertModel 5.0 [ "a", 3.0; "b", -2.0 ] model

let model =
    {
        Intercept = 1.0
        Coefficients = Map.ofList [ "a", 2.0; "b", -1.0 ]
    }

[<Fact>]
let ``切片と係数から予測値を計算する`` () =
    let x = [ Map.ofList [ "a", 1.0; "b", 4.0 ]; Map.ofList [ "a", 3.0; "b", 0.5 ] ]

    assertValues [ -1.0; 6.5 ] (predictLinearRegression model x)

[<Fact>]
let ``係数の無い列は予測に使わない`` () =
    let x = [ Map.ofList [ "a", 1.0; "b", 4.0; "cinema_id", 1375.0 ] ]

    assertValues [ -1.0 ] (predictLinearRegression model x)
