module MachineLearning.Tests.Chapter10.LogisticRegressionTest

open Xunit
open MachineLearning.Chapter10.LogisticRegression

[<Fact>]
let ``値がすべて同じなら確率は均等になる`` () =
    Assert.Equal<float[]>([| 0.25; 0.25; 0.25; 0.25 |], softmax [| 0.0; 0.0; 0.0; 0.0 |])

[<Fact>]
let ``値の差が指数の比になる`` () =
    let probabilities = softmax [| 0.0; log 2.0 |]

    Assert.Equal(1.0 / 3.0, probabilities[0], 12)
    Assert.Equal(2.0 / 3.0, probabilities[1], 12)

[<Fact>]
let ``大きな値でもあふれずに確率を求める`` () =
    Assert.Equal<float[]>([| 0.5; 0.5 |], softmax [| 1000.0; 1000.0 |])

/// 花弁幅だけを特徴量に持つ行のリストを作る
let byPetalWidth (values: float list) : Map<string, float> list =
    values |> List.map (fun value -> Map.ofList [ "花弁幅", value ])

[<Fact>]
let ``1 種類のラベルだけを学習するとそのラベルを予測する`` () =
    let model = fit defaults (byPetalWidth [ 0.1; 0.2 ]) [ "setosa"; "setosa" ]

    Assert.Equal<string list>([ "setosa"; "setosa" ], predict model (byPetalWidth [ 0.15; 0.9 ]))

[<Fact>]
let ``2 種類のラベルを境界の左右で予測する`` () =
    let model =
        fit defaults (byPetalWidth [ 0.1; 0.2; 0.8; 0.9 ]) [ "setosa"; "setosa"; "virginica"; "virginica" ]

    Assert.Equal<string list>([ "setosa"; "virginica" ], predict model (byPetalWidth [ 0.15; 0.85 ]))

[<Fact>]
let ``3 種類のラベルを 2 つの特徴量から予測する`` () =
    let x =
        List.map2
            (fun length width -> Map.ofList [ "花弁長さ", length; "花弁幅", width ])
            [ 0.1; 0.2; 0.5; 0.6; 0.5; 0.6 ]
            [ 0.1; 0.2; 0.1; 0.2; 0.8; 0.9 ]

    let t = [ "setosa"; "setosa"; "versicolor"; "versicolor"; "virginica"; "virginica" ]

    Assert.Equal<string list>(t, predict (fit defaults x t) x)

[<Fact>]
let ``学習を繰り返すと損失が小さくなる`` () =
    let model =
        fit
            { defaults with Epochs = 100 }
            (byPetalWidth [ 0.1; 0.2; 0.8; 0.9 ])
            [ "setosa"; "setosa"; "virginica"; "virginica" ]

    Assert.Equal(100, model.Losses.Length)
    Assert.True(List.last model.Losses < List.head model.Losses)
