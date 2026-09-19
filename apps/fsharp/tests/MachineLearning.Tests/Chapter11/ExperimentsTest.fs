module MachineLearning.Tests.Chapter11.ExperimentsTest

open Xunit
open MachineLearning.Chapter11.CrossValidation
open MachineLearning.Chapter11.Experiments

let byFeature (values: float list) : Map<string, float> list =
    values |> List.map (fun value -> Map.ofList [ "feature", value ])

/// 訓練データの正解の平均を常に予測するモデル
let meanModel: Model<float> = fun _ t -> List.map (fun _ -> List.average t)

[<Fact>]
let ``評価関数ごとに交差検証のスコアの平均を名前と組にして返す`` () =
    let x = byFeature [ 1.0; 2.0; 3.0; 4.0 ]
    let t = [ 1.0; 1.0; 1.0; 1.0 ]

    let scores =
        evaluate 2 0 meanModel [ "ゼロ", (fun _ _ -> 0.0); "件数", (fun a _ -> float (List.length a)) ] x t

    Assert.Equal<(string * float) list>([ "ゼロ", 0.0; "件数", 2.0 ], scores)
