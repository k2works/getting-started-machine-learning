module MachineLearning.Tests.Chapter09.StandardizerTest

open Xunit
open MachineLearning.Chapter09.Standardizer

let rows (column: string) (values: float list) : Map<string, float> list =
    values |> List.map (fun value -> Map.ofList [ column, value ])

[<Fact>]
let ``訓練データから列ごとの平均と標準偏差を求める`` () =
    let train =
        [
            Map.ofList [ "RM", 1.0; "LSTAT", 10.0 ]
            Map.ofList [ "RM", 2.0; "LSTAT", 10.0 ]
            Map.ofList [ "RM", 3.0; "LSTAT", 40.0 ]
        ]

    let standardizer = fitStandardizer [ "RM"; "LSTAT" ] train

    Assert.Equal<Map<string, float>>(Map.ofList [ "RM", 2.0; "LSTAT", 20.0 ], standardizer.Means)
    Assert.Equal(sqrt (2.0 / 3.0), standardizer.Stds["RM"], 12)
    Assert.Equal(sqrt 200.0, standardizer.Stds["LSTAT"], 12)

[<Fact>]
let ``訓練データの平均と標準偏差で、指定した列だけを標準化する`` () =
    let train = rows "RM" [ 1.0; 2.0; 3.0 ]
    let standardizer = fitStandardizer [ "RM" ] train

    let test =
        [ Map.ofList [ "RM", 4.0; "LSTAT", 7.0 ] ] |> transformStandardized standardizer

    Assert.Equal(2.0 / sqrt (2.0 / 3.0), test.Head["RM"], 12)
    Assert.Equal(7.0, test.Head["LSTAT"])

[<Fact>]
let ``標準偏差が 0 の列は 0 にする`` () =
    let train = rows "CHAS" [ 1.0; 1.0 ]

    let standardized = train |> transformStandardized (fitStandardizer [ "CHAS" ] train)

    Assert.Equal<float list>([ 0.0; 0.0 ], standardized |> List.map (fun row -> row["CHAS"]))
