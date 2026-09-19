module MachineLearning.Tests.Chapter10.FeatureImportanceTest

open Xunit
open MachineLearning.Chapter03
open MachineLearning.Chapter10
open MachineLearning.Chapter10.FeatureImportance

/// 2 つの特徴量の値のリストから、行のリストを作る
let rowsOf (first: string, firstValues: float list) (second: string, secondValues: float list) =
    List.map2 (fun a b -> Map.ofList [ first, a; second, b ]) firstValues secondValues

[<Fact>]
let ``分割しない木はすべての特徴量の重要度が 0`` () =
    let x = rowsOf ("がく片幅", [ 0.3; 0.5 ]) ("花弁幅", [ 0.1; 0.2 ])

    Assert.Equal<Map<string, float>>(
        Map.ofList [ "がく片幅", 0.0; "花弁幅", 0.0 ],
        treeImportances (DecisionTree.Leaf "setosa") x [ "setosa"; "setosa" ]
    )

[<Fact>]
let ``1 回だけ分割する木は分割に使った特徴量の重要度が 1`` () =
    let x = rowsOf ("がく片幅", [ 0.3; 0.5; 0.4; 0.6 ]) ("花弁幅", [ 0.1; 0.2; 0.8; 0.9 ])
    let t = [ "setosa"; "setosa"; "virginica"; "virginica" ]

    let tree = DecisionTree.fit None x t

    Assert.Equal<Map<string, float>>(Map.ofList [ "がく片幅", 0.0; "花弁幅", 1.0 ], treeImportances tree x t)

[<Fact>]
let ``分割で減った不純度を件数で重み付けして割合にする`` () =
    let x =
        rowsOf ("花弁長さ", [ 0.1; 0.2; 0.3; 0.8; 0.7; 0.9 ]) ("花弁幅", [ 0.1; 0.1; 0.2; 0.2; 0.9; 0.9 ])

    let t = [ "setosa"; "setosa"; "setosa"; "versicolor"; "virginica"; "virginica" ]

    let importances = treeImportances (DecisionTree.fit None x t) x t

    Assert.Equal(7.0 / 11.0, importances["花弁長さ"], 12)
    Assert.Equal(4.0 / 11.0, importances["花弁幅"], 12)

[<Fact>]
let ``木が 1 本なら学習に使った行でのその木の重要度と一致する`` () =
    let x =
        rowsOf
            ("がく片幅", [ 0.5; 0.3; 0.6; 0.4; 0.5; 0.3; 0.6; 0.4 ])
            ("花弁幅", [ 0.1; 0.12; 0.14; 0.16; 0.8; 0.82; 0.84; 0.86 ])

    let t = List.replicate 4 "setosa" @ List.replicate 4 "virginica"

    let forest =
        RandomForest.fit
            { RandomForest.defaults with
                NEstimators = 1
                MaxFeatures = 2
            }
            x
            t

    let fitted = List.exactlyOne forest

    let expected =
        treeImportances
            fitted.Tree
            (fitted.Rows |> List.map (fun row -> x[row]))
            (fitted.Rows |> List.map (fun row -> t[row]))

    Assert.Equal<Map<string, float>>(expected, forestImportances forest x t)
