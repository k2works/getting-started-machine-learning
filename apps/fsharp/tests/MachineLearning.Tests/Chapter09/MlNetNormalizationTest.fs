module MachineLearning.Tests.Chapter09.MlNetNormalizationTest

open Xunit
open MachineLearning.Chapter09.MlNetNormalization
open MachineLearning.Chapter09.Standardizer

let values = [ 1.0; 2.0; 3.0; 6.0 ]

[<Fact>]
let ``学習用テスト: NormalizeMeanVariance の既定（fixZero）は平均を引かず、どの値にも同じ数を掛けるだけ`` () =
    let normalized = normalizeMeanVariance true values

    let ratios = List.map2 (/) normalized values

    Assert.NotEqual(0.0, List.average normalized, 5)
    Assert.All(ratios, (fun ratio -> Assert.Equal(ratios.Head, ratio, 5)))

[<Fact>]
let ``fixZero を外すと、自作の標準化と同じ値になる`` () =
    let rows = values |> List.map (fun value -> Map.ofList [ "x", value ])

    let mine =
        rows
        |> transformStandardized (fitStandardizer [ "x" ] rows)
        |> List.map (fun row -> row["x"])

    let library = normalizeMeanVariance false values

    List.iter2 (fun (m: float) (l: float) -> Assert.Equal(m, l, 5)) mine library
