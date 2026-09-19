module MachineLearning.Tests.Chapter11.MetricsTest

open System
open Xunit
open MachineLearning.Chapter01.KinokoTakenoko
open MachineLearning.Chapter11.Metrics

[<Fact>]
let ``正例と負例の予測の当たり外れを数える`` () =
    let actual = [ 1; 1; 1; 0; 0 ]
    let predicted = [ 1; 1; 0; 1; 0 ]

    Assert.Equal({ TP = 2; FP = 1; FN = 1; TN = 1 }, confusionMatrix 1 actual predicted)

[<Fact>]
let ``どちらのラベルを正例とするかで数え方が変わる`` () =
    let actual = [ 1; 1; 1; 0; 0; 0 ]
    let predicted = [ 1; 0; 0; 0; 0; 1 ]

    Assert.Equal({ TP = 2; FP = 2; FN = 1; TN = 1 }, confusionMatrix 0 actual predicted)

[<Fact>]
let ``正解と予測の件数が違えばエラーになる`` () =
    Assert.Throws<ArgumentException>(fun () -> confusionMatrix 1 [ 1; 0; 1 ] [ 1; 0 ] |> ignore)
    |> ignore

let cm = { TP = 3; FP = 1; FN = 2; TN = 4 }

[<Fact>]
let ``適合率は正例と予測したうち本当に正例だった割合`` () = Assert.Equal(0.75, precision cm, 12)

[<Fact>]
let ``再現率は本当の正例のうち正例と予測できた割合`` () = Assert.Equal(0.6, recall cm, 12)

[<Fact>]
let ``F 値は適合率と再現率の調和平均`` () =
    Assert.Equal(2.0 * 0.75 * 0.6 / (0.75 + 0.6), f1Score cm, 12)

[<Fact>]
let ``正例を 1 件も当てられなければ適合率と再現率と F 値は 0`` () =
    let missed = { TP = 0; FP = 0; FN = 3; TN = 5 }

    Assert.Equal((0.0, 0.0, 0.0), (precision missed, recall missed, f1Score missed))

[<Fact>]
let ``誤差の 2 乗の平均と平方根と絶対値の平均を求める`` () =
    let actual = [ 3.0; 5.0; 8.0 ]
    let predicted = [ 2.0; 5.0; 10.0 ]

    Assert.Equal(5.0 / 3.0, meanSquaredError actual predicted, 12)
    Assert.Equal(sqrt (5.0 / 3.0), rootMeanSquaredError actual predicted, 12)
    Assert.Equal(1.0, meanAbsoluteError actual predicted, 12)

[<Fact>]
let ``大きく外れた予測があると RMSE は MAE より大きく増える`` () =
    let actual = [ 3.0; 5.0; 8.0; 10.0 ]
    let predicted = [ 2.0; 5.0; 10.0; 30.0 ]

    Assert.Equal(sqrt 101.25, rootMeanSquaredError actual predicted, 12)
    Assert.Equal(5.75, meanAbsoluteError actual predicted, 12)

[<Fact>]
let ``MSE も正解と予測の件数が違えばエラーになる`` () =
    Assert.Throws<ArgumentException>(fun () -> meanSquaredError [ 1.0; 2.0 ] [ 1.0 ] |> ignore)
    |> ignore

[<Fact>]
let ``第 1 章の正解率も評価関数として使える`` () =
    let metric: Metric<int> = accuracy

    Assert.Equal(0.75, metric [ 1; 0; 1; 0 ] [ 1; 1; 1; 0 ], 12)

[<Fact>]
let ``混同行列から求める指標を正解と予測から求める評価関数に変える`` () =
    let actual = [ 1; 1; 1; 0; 0 ]
    let predicted = [ 1; 0; 0; 1; 0 ]

    let precisionMetric = classificationMetric precision 1
    let recallMetric = classificationMetric recall 1

    Assert.Equal(0.5, precisionMetric actual predicted, 12)
    Assert.Equal(1.0 / 3.0, recallMetric actual predicted, 12)
