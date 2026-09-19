module MachineLearning.Tests.Chapter10.ClassifierTest

open Xunit
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter03
open MachineLearning.Chapter10
open MachineLearning.Chapter10.Classifier

let byPetalWidth (values: float list) : Map<string, float> list =
    values |> List.map (fun value -> Map.ofList [ "花弁幅", value ])

let smallSplit: TrainTestSplit<Map<string, float>, string> =
    {
        XTrain = byPetalWidth [ 0.1; 0.2; 0.8; 0.9 ]
        XTest = byPetalWidth [ 0.15; 0.25 ]
        TTrain = [ "setosa"; "setosa"; "virginica"; "virginica" ]
        TTest = [ "setosa"; "setosa" ]
    }

/// 何を学習しても setosa と予測する分類器
let alwaysSetosa: Classifier<string> = fun _ _ -> List.map (fun _ -> "setosa")

[<Fact>]
let ``学習させてから訓練データとテストデータの正解率を求める`` () =
    Assert.Equal({ Train = 0.5; Test = 1.0 }, evaluate alwaysSetosa smallSplit)

[<Fact>]
let ``第 3 章の決定木と自作のモデルを同じ関数で評価できる`` () =
    let classifiers: Classifier<string> list =
        [
            ofModel (DecisionTree.fit (Some 1)) DecisionTree.predict
            ofModel (LogisticRegression.fit LogisticRegression.defaults) LogisticRegression.predict
            ofModel
                (RandomForest.fit
                    { RandomForest.defaults with
                        NEstimators = 5
                        MaxFeatures = 1
                    })
                RandomForest.predict
        ]

    let scores =
        classifiers |> List.map (fun classifier -> evaluate classifier smallSplit)

    Assert.Equal<Score list>(List.replicate 3 { Train = 1.0; Test = 1.0 }, scores)
