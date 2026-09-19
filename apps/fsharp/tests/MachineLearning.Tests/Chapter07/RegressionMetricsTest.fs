module MachineLearning.Tests.Chapter07.RegressionMetricsTest

open System
open Xunit
open MachineLearning.Chapter07.RegressionMetrics

let t = [ 3.0; 5.0; 7.0 ]

[<Fact>]
let ``誤差の絶対値の平均を求める`` () =
    Assert.Equal(1.0, meanAbsoluteError t [ 2.0; 5.0; 9.0 ], 12)

[<Fact>]
let ``予測が大きく外れるほど値が大きくなる`` () =
    Assert.Equal(5.0 / 3.0, meanAbsoluteError t [ 1.0; 8.0; 7.0 ], 12)

[<Fact>]
let ``実測値と予測値の件数が違えば ArgumentException を投げる`` () =
    Assert.Throws<ArgumentException>(fun () -> meanAbsoluteError t [ 1.0 ] |> ignore)
    |> ignore

[<Fact>]
let ``誤差の 2 乗の平均の平方根を求める`` () =
    Assert.Equal(sqrt (5.0 / 3.0), rootMeanSquaredError t [ 2.0; 5.0; 9.0 ], 12)

[<Fact>]
let ``予測がすべて正解なら決定係数は 1`` () =
    Assert.Equal(1.0, r2Score t [ 3.0; 5.0; 7.0 ], 12)

[<Fact>]
let ``平均値を予測し続けるモデルより良い分だけ決定係数は 1 に近づく`` () =
    Assert.Equal(1.0 - 5.0 / 8.0, r2Score t [ 2.0; 5.0; 9.0 ], 12)
