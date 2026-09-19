module MachineLearning.Tests.Chapter12.LibraryRegularizationTest

open System
open Xunit
open FSharp.Stats
open FSharp.Stats.Fitting
open MachineLearning.Chapter12.Regularization
open MachineLearning.Chapter12.MlNetRegularization
open MachineLearning.Tests.Chapter12.RegularizationTest

[<Fact>]
let ``学習用テスト: FSharp.Stats 0.6.0 の RidgeRegression.fit は lambda の行列の大きさによらず次元が合わない例外を投げる`` () =
    let x, t = randomDataset ()
    let xData = matrix x
    let yData = vector t

    for size in [ 4; 5 ] do
        let lambda = Matrix.identity size

        let error =
            Assert.ThrowsAny<ArgumentException>(fun () ->
                LinearRegression.OLS.Linear.RidgeRegression.fit lambda xData yData |> ignore)

        Assert.StartsWith("the two matrices do not have compatible dimensions", error.Message)

/// 要素の数が同じで、要素ごとの差が tolerance 以下であることを確かめる
let assertClose (tolerance: float) (expected: float seq) (actual: float seq) =
    Assert.Equal(Seq.length expected, Seq.length actual)
    Seq.iter2 (fun (e: float) (a: float) -> Assert.InRange(a, e - tolerance, e + tolerance)) expected actual

[<Fact>]
let ``学習用テスト: ML.NET の Sdca の L2Regularization は、自作のリッジ回帰の alpha を件数の半分で割った値に当たる`` () =
    let x, t = randomDataset ()
    let l2 = 0.1

    let library = fitSdca l2 x t
    let mine = fitRidge (float t.Length * l2 / 2.0) x t

    assertClose 1e-3 mine.Coefficients library.Coefficients
    assertClose 1e-3 [ mine.Intercept ] [ library.Intercept ]

[<Fact>]
let ``alpha を件数で換算すると ML.NET でも自作のリッジ回帰と同じ係数と切片になる`` () =
    let x, t = randomDataset ()

    let library = fitRidgeWithMlNet 10.0 x t
    let mine = fitRidge 10.0 x t

    assertClose 1e-3 mine.Coefficients library.Coefficients
    assertClose 1e-3 [ mine.Intercept ] [ library.Intercept ]
