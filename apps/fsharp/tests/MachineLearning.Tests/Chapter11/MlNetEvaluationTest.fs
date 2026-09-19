module MachineLearning.Tests.Chapter11.MlNetEvaluationTest

open Xunit
open MachineLearning.Chapter11.Metrics
open MachineLearning.Chapter11.MlNetEvaluation

let actual = [ "1"; "1"; "1"; "0"; "0"; "0"; "0"; "1" ]
let predicted = [ "1"; "1"; "0"; "1"; "0"; "0"; "0"; "0" ]

[<Fact>]
let ``2 値分類の適合率・再現率・F 値が ML.NET の評価と一致する`` () =
    let cm = confusionMatrix "1" actual predicted

    let library = evaluateBinary "1" actual predicted

    Assert.Equal(precision cm, library.PositivePrecision, 12)
    Assert.Equal(recall cm, library.PositiveRecall, 12)
    Assert.Equal(f1Score cm, library.F1Score, 12)

[<Fact>]
let ``回帰の MAE・RMSE が ML.NET の評価と一致する`` () =
    let actual = [ 1.0; 2.0; 4.0; 8.0 ]
    let predicted = [ 1.5; 2.0; 3.0; 10.0 ]

    let library = evaluateRegression actual predicted

    Assert.Equal(meanAbsoluteError actual predicted, library.MeanAbsoluteError, 9)
    Assert.Equal(rootMeanSquaredError actual predicted, library.RootMeanSquaredError, 9)
