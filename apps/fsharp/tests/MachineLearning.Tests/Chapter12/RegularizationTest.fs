module MachineLearning.Tests.Chapter12.RegularizationTest

open System
open Xunit
open MachineLearning.Chapter07.Matrix
open MachineLearning.Chapter07.LinearRegression
open MachineLearning.Chapter12.Regularization
open MachineLearning.Tests.Chapter07.MatrixTest

[<Fact>]
let ``alpha が 0 なら最小二乗法と同じ係数と切片になる`` () =
    let x = [| [| 1.0 |]; [| 2.0 |]; [| 3.0 |] |]
    let t = [ 3.0; 5.0; 7.0 ]

    let model = fitRidge 0.0 x t

    Assert.Equal(2.0, List.exactlyOne model.Coefficients, 12)
    Assert.Equal(1.0, model.Intercept, 12)

[<Fact>]
let ``特徴量が 2 つでも係数と切片を求める`` () =
    let x = [| [| 1.0; 0.0 |]; [| 0.0; 1.0 |]; [| 1.0; 1.0 |]; [| 2.0; 1.0 |] |]

    let t =
        x |> Array.map (fun row -> 3.0 * row[0] - 1.0 * row[1] + 4.0) |> Array.toList

    let model = fitRidge 0.0 x t

    assertValues [ 3.0; -1.0 ] model.Coefficients
    Assert.Equal(4.0, model.Intercept, 9)

/// 4 列の特徴量（-1〜1 の一様乱数）と、t = 1.5x0 - 2x1 + 0.5x2 + 3x3 + 2 にノイズを加えた正解の 30 件
let randomDataset () : Matrix * float list =
    let random = Random 0
    let weights = [| 1.5; -2.0; 0.5; 3.0 |]

    let x =
        Array.init 30 (fun _ -> Array.init weights.Length (fun _ -> random.NextDouble() * 2.0 - 1.0))

    let t =
        x
        |> Array.map (fun row -> dot row weights + 2.0 + 0.5 * (random.NextDouble() - 0.5))
        |> Array.toList

    x, t

[<Fact>]
let ``alpha を大きくすると係数の絶対値の合計が小さくなる`` () =
    let x, t = randomDataset ()

    let weak = fitRidge 0.1 x t
    let strong = fitRidge 100.0 x t

    Assert.True(List.sumBy abs strong.Coefficients < List.sumBy abs weak.Coefficients)

[<Fact>]
let ``alpha が 0 なら第 7 章の線形回帰と同じ係数と切片になる`` () =
    let x, t = randomDataset ()

    let rows =
        x
        |> Array.toList
        |> List.map (Array.mapi (fun i value -> $"x{i}", value) >> Map.ofArray)

    let model = fitRidge 0.0 x t
    let expected = fitLinearRegression rows t

    assertValues expected.Coefficients.Values model.Coefficients
    Assert.Equal(expected.Intercept, model.Intercept, 9)

[<Fact>]
let ``係数と切片から予測値を計算する`` () =
    let model =
        {
            Coefficients = [ 3.0; -1.0 ]
            Intercept = 4.0
        }

    assertValues [ 5.0; 4.0 ] (predictRegularized model [| [| 1.0; 2.0 |]; [| 0.0; 0.0 |] |])

/// 先頭の 20 件で学習し、残りの 10 件で検証する
let experimentsOf (alphas: float list) : Experiment list =
    let x, t = randomDataset ()
    let tTrain, tValid = List.splitAt 20 t
    runRidgeExperiments alphas x[..19] tTrain x[20..] tValid

[<Fact>]
let ``正則化の強さごとに 1 件ずつ実験結果を記録する`` () =
    let experiments = experimentsOf [ 0.1; 1.0; 10.0 ]

    Assert.Equal<float list>([ 0.1; 1.0; 10.0 ], experiments |> List.map (fun e -> e.Alpha))

[<Fact>]
let ``with で一部を変えた実験結果を作っても元の実験結果は変わらない`` () =
    let original = experimentsOf [ 1.0 ] |> List.exactlyOne

    let changed = { original with Alpha = 2.0 }

    Assert.Equal(1.0, original.Alpha)
    Assert.Equal(2.0, changed.Alpha)
    Assert.Equal(original.ValidationScore, changed.ValidationScore)

/// 検証データの決定係数だけを指定した実験結果
let experiment (alpha: float) (validationScore: float) : Experiment =
    {
        Alpha = alpha
        TrainScore = 0.9
        ValidationScore = validationScore
        CoefficientAbsSum = 1.0
    }

[<Fact>]
let ``検証データの決定係数が最も高い実験を選ぶ`` () =
    let experiments = [ experiment 0.1 0.7; experiment 1.0 0.6 ]

    Assert.Equal(0.1, (bestExperiment experiments).Alpha)

[<Fact>]
let ``最も高い実験が途中にあってもそれを選ぶ`` () =
    let experiments = [ experiment 0.1 0.5; experiment 1.0 0.8; experiment 10.0 0.6 ]

    Assert.Equal(1.0, (bestExperiment experiments).Alpha)

[<Fact>]
let ``実験結果が空なら選べない`` () =
    Assert.Throws<ArgumentException>(fun () -> bestExperiment [] |> ignore)
    |> ignore

[<Fact>]
let ``0 になった係数の特徴量名を返す`` () =
    Assert.Equal<string list>([ "RM"; "RM^2" ], zeroCoefficientNames [ 0.0; 1.5; 0.0 ] [ "RM"; "LSTAT"; "RM^2" ])

/// x0・x1 だけが正解に関係し、noise1・noise2 は関係しない 50 件
let sparseDataset () : Matrix * float list =
    let random = Random 0

    let x =
        Array.init 50 (fun _ -> Array.init 4 (fun _ -> random.NextDouble() * 2.0 - 1.0))

    let t =
        x
        |> Array.map (fun row -> 3.0 * row[0] - 2.0 * row[1] + 0.1 * (random.NextDouble() - 0.5))
        |> Array.toList

    x, t

[<Fact>]
let ``ラッソ回帰では予測に役立たない特徴量の係数がちょうど 0 になる`` () =
    let x, t = sparseDataset ()

    let model = fitLasso 0.5 x t

    Assert.Equal<string list>(
        [ "noise1"; "noise2" ],
        zeroCoefficientNames model.Coefficients [ "x0"; "x1"; "noise1"; "noise2" ]
    )

[<Fact>]
let ``ラッソ回帰も alpha が 0 なら最小二乗法と同じ係数と切片になる`` () =
    let x, t = randomDataset ()

    let model = fitLasso 0.0 x t
    let expected = fitRidge 0.0 x t

    assertValues expected.Coefficients model.Coefficients
    Assert.Equal(expected.Intercept, model.Intercept, 9)
