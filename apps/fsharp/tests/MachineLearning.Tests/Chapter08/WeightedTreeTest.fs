module MachineLearning.Tests.Chapter08.WeightedTreeTest

open Xunit
open MachineLearning.Chapter03
open MachineLearning.Chapter08.WeightedTree

[<Fact>]
let ``重みがすべて 1 なら第 3 章のジニ不純度と同じ値になる`` () =
    let labels = [ 0; 1; 1 ]

    Assert.Equal(DecisionTree.gini labels, weightedGini labels [ 1.0; 1.0; 1.0 ])

[<Fact>]
let ``重みの大きいラベルほど多いものとして不純度を計算する`` () =
    Assert.Equal(0.375, weightedGini [ 0; 1 ] [ 1.0; 3.0 ], 12)

[<Fact>]
let ``balanced は少ないクラスほど 1 件の重みを大きくし、クラスごとの重みの合計をそろえる`` () =
    let weights = classWeights Balanced [ 0; 0; 0; 1 ]

    Assert.Equal<float list>([ 4.0 / 6.0; 4.0 / 6.0; 4.0 / 6.0; 2.0 ], weights)

[<Fact>]
let ``重み付けなしなら重みはすべて 1`` () =
    Assert.Equal<float list>([ 1.0; 1.0; 1.0 ], classWeights Unweighted [ 0; 1; 1 ])

let fares = [ 8.0; 9.0; 13.0; 20.0; 60.0; 80.0 ]
let ages = [ 30.0; 22.0; 18.0; 45.0; 25.0; 33.0 ]

let x =
    List.map2 (fun fare age -> Map.ofList [ "Fare", fare; "Age", age ]) fares ages

let t = [ 0; 0; 1; 0; 1; 1 ]

[<Fact>]
let ``重みがすべて 1 なら第 3 章の決定木と同じ木を作る`` () =
    for maxDepth in [ Some 1; Some 2; None ] do
        Assert.Equal(DecisionTree.fit maxDepth x t, fitWeighted maxDepth x t (classWeights Unweighted t))

[<Fact>]
let ``balanced にすると少ないクラスが混ざった葉でも少ないクラスを予測する`` () =
    let byFare = List.map (fun fare -> Map.ofList [ "Fare", fare ])
    let fareX = byFare [ 1.0; 1.0; 1.0; 1.0; 2.0; 2.0; 2.0 ]
    let survived = [ 0; 0; 0; 0; 0; 0; 1 ]

    let predictWith classWeight =
        let tree = fitWeighted (Some 1) fareX survived (classWeights classWeight survived)
        DecisionTree.predict tree (byFare [ 1.0; 2.0 ])

    Assert.Equal<int list>([ 0; 0 ], predictWith Unweighted)
    Assert.Equal<int list>([ 0; 1 ], predictWith Balanced)
