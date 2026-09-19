module MachineLearning.Tests.Chapter03.MlNetAdapterTest

open Xunit
open MachineLearning.Chapter03
open MachineLearning.Chapter03.MlNetAdapter

let byPetalWidth (values: float list) : Map<string, float> list =
    values |> List.map (fun value -> Map.ofList [ "花弁幅", value ])

let x = byPetalWidth [ 0.1; 0.2; 0.3; 0.5; 0.6; 0.9 ]

let t = [ "setosa"; "setosa"; "setosa"; "versicolor"; "versicolor"; "virginica" ]

[<Fact>]
let ``文字列の正解ラベルで学習し、文字列のラベルで予測する`` () =
    let predictWith = trainFastTree 2 x t

    Assert.Equal<string list>([ "setosa"; "versicolor"; "virginica" ], predictWith (byPetalWidth [ 0.2; 0.55; 0.95 ]))

[<Fact>]
let ``訓練データから離れた値では自作の決定木と同じ予測をする`` () =
    let newX = byPetalWidth [ 0.15; 0.55; 0.95 ]

    let predictWith = trainFastTree 2 x t

    Assert.Equal<string list>(DecisionTree.predict (DecisionTree.fit None x t) newX, predictWith newX)
