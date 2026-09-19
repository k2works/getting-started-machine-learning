[<Xunit.Collection("標準出力を書き換えるテスト")>]
module MachineLearning.Tests.Chapter13.FSharpStatsPcaLearningTest

open System
open System.IO
open Xunit
open FSharp.Stats
open FSharp.Stats.ML.Unsupervised

/// 完全に相関する 2 列を、列の平均を引いて中心化したもの
let centered = matrix [ [ -2.0; -4.0 ]; [ 0.0; 0.0 ]; [ 2.0; 4.0 ] ]

[<Fact>]
let ``Loadings の列が主成分で、寄与率は大きい順に並ぶ`` () =
    let result = PCA.compute centered

    Assert.Equal(1.0 / sqrt 5.0, result.Loadings[0, 0], 1e-9)
    Assert.Equal(2.0 / sqrt 5.0, result.Loadings[1, 0], 1e-9)
    Assert.Equal(1.0, result.VarExplainedByComponentIndividual[0], 1e-9)
    Assert.Equal(0.0, result.VarExplainedByComponentIndividual[1], 1e-9)

[<Fact>]
let ``VarianceOfComponent は名前に反して分散ではなく標準偏差を返す`` () =
    let result = PCA.compute centered

    // 第 1 主成分の分散（n - 1 で割る）は 20。その平方根が返る
    Assert.Equal(sqrt 20.0, result.VarianceOfComponent[0], 1e-9)

[<Fact>]
let ``compute は途中の値を標準出力に書き出す`` () =
    let original = Console.Out
    use writer = new StringWriter()
    Console.SetOut writer

    try
        PCA.compute centered |> ignore
    finally
        Console.SetOut original

    Assert.NotEqual<string>("", writer.ToString())

[<Fact>]
let ``center は平均を引くだけでなく、件数 n で割った標準偏差で割る`` () =
    let centeredByLibrary = PCA.center (matrix [ [ 1.0 ]; [ 3.0 ] ])

    Assert.Equal(-1.0, centeredByLibrary[0, 0], 1e-9)
    Assert.Equal(1.0, centeredByLibrary[1, 0], 1e-9)

[<Fact>]
let ``主成分の符号はそろえられていない`` () =
    let x =
        matrix [ [ 0.0; 0.0; 0.0 ]; [ 0.0; 2.0; 0.0 ]; [ 3.0; 0.0; 1.0 ]; [ 0.0; 3.0; 3.0 ] ]

    let means = [ 0.75; 1.25; 1.0 ]
    let centered = x |> Matrix.mapi (fun _ j value -> value - means[j])

    let result = PCA.compute centered

    let largestOfEachComponent =
        [ 0..2 ]
        |> List.map (fun j -> [ 0..2 ] |> List.map (fun i -> result.Loadings[i, j]) |> List.maxBy abs)

    Assert.Contains(largestOfEachComponent, fun value -> value < 0.0)
