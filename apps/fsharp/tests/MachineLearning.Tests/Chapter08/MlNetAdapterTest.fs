module MachineLearning.Tests.Chapter08.MlNetAdapterTest

open System.IO
open Xunit
open MachineLearning.Chapter08.WeightedTree
open MachineLearning.Chapter08.MlNetAdapter

let byFare = List.map (fun fare -> Map.ofList [ "Fare", fare ])
let x = byFare [ 1.0; 1.0; 1.0; 1.0; 2.0; 2.0; 2.0 ]
let t = [ 0; 0; 0; 0; 0; 0; 1 ]
let newX = byFare [ 1.0; 2.0 ]

[<Fact>]
let ``学習用テスト: 行の重み（ExampleWeightColumnName）でクラスの重みを表せる`` () =
    let predictWith classWeight =
        let model = trainFastTree 2 x t (classWeights classWeight t)
        predictFastTree model newX

    Assert.Equal<int list>([ 0; 0 ], predictWith Unweighted)
    Assert.Equal<int list>([ 0; 1 ], predictWith Balanced)

[<Fact>]
let ``zip に保存したモデルを読み込むと同じ予測をする`` () =
    let model = trainFastTree 2 x t (classWeights Balanced t)

    let modelFile =
        Path.Combine(Directory.CreateTempSubdirectory("model-").FullName, "model", "survived.zip")

    saveFastTree modelFile model

    Assert.Equal<int list>([ 0; 1 ], predictFastTree (loadFastTree modelFile) newX)
