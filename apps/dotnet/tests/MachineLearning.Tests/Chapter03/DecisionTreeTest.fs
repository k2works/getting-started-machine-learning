module MachineLearning.Tests.Chapter03.DecisionTreeTest

open Xunit
open MachineLearning.Chapter03.DecisionTree

/// 花弁幅だけを特徴量に持つ行のリストを作る
let byPetalWidth (values: float list) : Map<string, float> list =
    values |> List.map (fun value -> Map.ofList [ "花弁幅", value ])

[<Fact>]
let ``1 種類のラベルだけならジニ不純度は 0`` () =
    Assert.Equal(0.0, gini [ "setosa"; "setosa"; "setosa" ])

[<Fact>]
let ``2 種類のラベルが半分ずつならジニ不純度は 0.5`` () =
    Assert.Equal(0.5, gini [ "setosa"; "virginica" ])

[<Fact>]
let ``3 種類のラベルが同じ数ならジニ不純度は 3 分の 2`` () =
    Assert.Equal(2.0 / 3.0, gini [ "setosa"; "versicolor"; "virginica" ], 12)

[<Fact>]
let ``ラベルを完全に分けられる境界を見つける`` () =
    let x = byPetalWidth [ 0.1; 0.2; 0.7; 0.8 ]
    let t = [ "setosa"; "setosa"; "virginica"; "virginica" ]

    match bestSplit x t with
    | Some split ->
        Assert.Equal("花弁幅", split.Feature)
        Assert.Equal(0.45, split.Threshold, 12)
        Assert.Equal(0.0, split.Impurity)
    | None -> Assert.Fail "分割が見つからない"

[<Fact>]
let ``複数の特徴量から不純度が最も小さくなる特徴量と境界を選ぶ`` () =
    let x =
        [
            Map.ofList [ "がく片長さ", 0.1; "花弁長さ", 0.2 ]
            Map.ofList [ "がく片長さ", 0.3; "花弁長さ", 0.1 ]
            Map.ofList [ "がく片長さ", 0.2; "花弁長さ", 0.9 ]
            Map.ofList [ "がく片長さ", 0.4; "花弁長さ", 0.6 ]
        ]

    let t = [ "setosa"; "setosa"; "virginica"; "virginica" ]

    match bestSplit x t with
    | Some split ->
        Assert.Equal("花弁長さ", split.Feature)
        Assert.Equal(0.4, split.Threshold, 12)
    | None -> Assert.Fail "分割が見つからない"

[<Fact>]
let ``ラベルが 1 種類なら分割しない`` () =
    Assert.Equal(None, bestSplit (byPetalWidth [ 0.1; 0.2; 0.7 ]) [ "setosa"; "setosa"; "setosa" ])

[<Fact>]
let ``1 種類のラベルだけを学習するとそのラベルを予測する`` () =
    let tree = fit None (byPetalWidth [ 0.1; 0.2 ]) [ "setosa"; "setosa" ]

    Assert.Equal<string list>([ "setosa"; "setosa" ], predict tree (byPetalWidth [ 0.15; 0.9 ]))

[<Fact>]
let ``境界の左右で異なるラベルを予測する`` () =
    let tree =
        fit None (byPetalWidth [ 0.1; 0.2; 0.7; 0.8 ]) [ "setosa"; "setosa"; "virginica"; "virginica" ]

    Assert.Equal<string list>([ "setosa"; "virginica" ], predict tree (byPetalWidth [ 0.15; 0.75 ]))

let threeSpeciesX = byPetalWidth [ 0.1; 0.2; 0.3; 0.5; 0.6; 0.9 ]

let threeSpeciesT =
    [ "setosa"; "setosa"; "setosa"; "versicolor"; "versicolor"; "virginica" ]

[<Fact>]
let ``深さを制限しなければすべての訓練データを分け切る`` () =
    let tree = fit None threeSpeciesX threeSpeciesT

    Assert.Equal<string list>(threeSpeciesT, predict tree threeSpeciesX)

[<Fact>]
let ``深さを 1 に制限すると境界の先は多数派のラベルを予測する`` () =
    let tree = fit (Some 1) threeSpeciesX threeSpeciesT

    Assert.Equal<string list>([ "setosa"; "versicolor" ], predict tree (byPetalWidth [ 0.2; 0.95 ]))

[<Fact>]
let ``多数決が同数なら葉の中で先に現れたラベルを選ぶ`` () =
    Assert.Equal("a", majority [ "a"; "b"; "b"; "a" ])
    Assert.Equal("b", majority [ "b"; "a" ])

[<Fact>]
let ``葉だけの木はラベルを表示する`` () =
    Assert.Equal<string list>([ "setosa" ], formatTree (Leaf "setosa"))

[<Fact>]
let ``節は条件ごとに字下げして表示する`` () =
    let tree =
        Node(
            {
                Feature = "花弁幅"
                Threshold = 0.4
                Impurity = 0.0
            },
            Leaf "setosa",
            Node(
                {
                    Feature = "花弁長さ"
                    Threshold = 0.75
                    Impurity = 0.0
                },
                Leaf "versicolor",
                Leaf "virginica"
            )
        )

    Assert.Equal<string list>(
        [
            "花弁幅 <= 0.4000"
            "  setosa"
            "花弁幅 > 0.4000"
            "  花弁長さ <= 0.7500"
            "    versicolor"
            "  花弁長さ > 0.7500"
            "    virginica"
        ],
        formatTree tree
    )
