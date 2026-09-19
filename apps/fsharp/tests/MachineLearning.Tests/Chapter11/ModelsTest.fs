module MachineLearning.Tests.Chapter11.ModelsTest

open Xunit
open MachineLearning.Chapter03
open MachineLearning.Chapter10.Classifier
open MachineLearning.Chapter11.CrossValidation
open MachineLearning.Chapter11.Models

let byFeature (values: float list) : Map<string, float> list =
    values |> List.map (fun value -> Map.ofList [ "feature", value ])

[<Fact>]
let ``第 10 章の分類器はそのまま Model として使える`` () =
    let x = byFeature [ 0.1; 0.2; 0.3; 0.6; 0.7; 0.9 ]
    let t = [ "0"; "0"; "1"; "1"; "0"; "1" ]
    let newX = byFeature [ 0.15; 0.35; 0.8 ]

    let model: Model<string> = ofModel (DecisionTree.fit (Some 1)) DecisionTree.predict

    Assert.Equal<string list>(DecisionTree.predict (DecisionTree.fit (Some 1) x t) newX, model x t newX)

[<Fact>]
let ``線形回帰は誤差の無いデータから直線を求めて予測する`` () =
    let x = byFeature [ 1.0; 2.0; 3.0; 4.0 ]
    let t = [ 3.0; 5.0; 7.0; 9.0 ]

    let predicted = linearRegression x t (byFeature [ 1.5; 5.0 ])

    Assert.Equal(4.0, predicted[0], 9)
    Assert.Equal(11.0, predicted[1], 9)
