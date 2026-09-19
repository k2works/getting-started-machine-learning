module MachineLearning.Tests.Chapter10.RandomForestTest

open Xunit
open MachineLearning.Chapter10.RandomForest

[<Fact>]
let ``サンプルごとに最も多い予測を選ぶ`` () =
    let votes =
        [
            [ "setosa"; "virginica" ]
            [ "setosa"; "virginica" ]
            [ "versicolor"; "setosa" ]
        ]

    Assert.Equal<string list>([ "setosa"; "virginica" ], majorityVote votes)

[<Fact>]
let ``元のデータと同じ件数の行番号を重複を許して選ぶ`` () =
    let rows = bootstrapSample 0 100

    Assert.Equal(100, rows.Length)
    Assert.True(rows |> List.forall (fun row -> 0 <= row && row < 100))
    Assert.True((List.distinct rows).Length < 100)

[<Fact>]
let ``同じシードなら同じ行を選ぶ`` () =
    Assert.Equal<int list>(bootstrapSample 42 10, bootstrapSample 42 10)

[<Fact>]
let ``指定した数の特徴量を元の列の順で選ぶ`` () =
    let features = [ "a"; "b"; "c"; "d" ]

    let chosen = chooseFeatures 0 2 features

    Assert.Equal(2, chosen.Length)
    Assert.Equal<string list>(List.sort chosen, chosen)
    Assert.Equal<string list>(chosen, chooseFeatures 0 2 features)

/// がく片幅と花弁幅を持つ、2 品種 10 件のデータ
let twoSpeciesX =
    List.map2
        (fun sepal petal -> Map.ofList [ "がく片幅", sepal; "花弁幅", petal ])
        [ 0.5; 0.3; 0.6; 0.4; 0.5; 0.3; 0.6; 0.4; 0.5; 0.3 ]
        [ 0.1; 0.12; 0.14; 0.16; 0.18; 0.8; 0.82; 0.84; 0.86; 0.88 ]

let twoSpeciesT = List.replicate 5 "setosa" @ List.replicate 5 "virginica"

[<Fact>]
let ``指定した数だけ第 3 章の決定木を学習する`` () =
    let forest =
        fit
            { defaults with
                NEstimators = 5
                MaxFeatures = 1
            }
            twoSpeciesX
            twoSpeciesT

    Assert.Equal(5, forest.Length)

[<Fact>]
let ``各決定木は指定した数の特徴量だけを使う`` () =
    let forest =
        fit
            { defaults with
                NEstimators = 5
                MaxFeatures = 1
            }
            twoSpeciesX
            twoSpeciesT

    Assert.True(forest |> List.forall (fun fitted -> fitted.Columns.Length = 1))

[<Fact>]
let ``決定木の多数決で予測する`` () =
    let forest = fit { defaults with NEstimators = 25 } twoSpeciesX twoSpeciesT

    let newX =
        [
            Map.ofList [ "がく片幅", 0.4; "花弁幅", 0.13 ]
            Map.ofList [ "がく片幅", 0.4; "花弁幅", 0.83 ]
        ]

    Assert.Equal<string list>([ "setosa"; "virginica" ], predict forest newX)

[<Fact>]
let ``同じシードなら同じ森になる`` () =
    let settings =
        { defaults with
            NEstimators = 5
            MaxFeatures = 1
            Seed = 7
        }

    Assert.Equal<FittedTree<string> list>(fit settings twoSpeciesX twoSpeciesT, fit settings twoSpeciesX twoSpeciesT)
