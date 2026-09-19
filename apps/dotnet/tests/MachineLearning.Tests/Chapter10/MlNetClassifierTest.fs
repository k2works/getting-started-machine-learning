module MachineLearning.Tests.Chapter10.MlNetClassifierTest

open Xunit
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter10.Classifier
open MachineLearning.Chapter10.MlNetClassifier
open MachineLearning.Tests.Chapter10.RandomForestTest

let twoSpeciesSplit: TrainTestSplit<Map<string, float>, string> =
    {
        XTrain = twoSpeciesX
        XTest =
            [
                Map.ofList [ "がく片幅", 0.4; "花弁幅", 0.13 ]
                Map.ofList [ "がく片幅", 0.4; "花弁幅", 0.83 ]
            ]
        TTrain = twoSpeciesT
        TTest = [ "setosa"; "virginica" ]
    }

[<Fact>]
let ``ML.NET のロジスティック回帰とランダムフォレストも同じ関数で評価できる`` () =
    let classifiers = [ lbfgsMaximumEntropy 1.0f 1.0f; fastForest 10 1 ]

    let scores =
        classifiers |> List.map (fun classifier -> evaluate classifier twoSpeciesSplit)

    Assert.Equal<Score list>(List.replicate 2 { Train = 1.0; Test = 1.0 }, scores)
