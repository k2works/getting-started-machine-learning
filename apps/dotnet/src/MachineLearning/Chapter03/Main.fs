module MachineLearning.Chapter03.Main

open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter01.KinokoTakenoko
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter03.DecisionTree
open MachineLearning.Chapter03.MlNetAdapter

[<Literal>]
let TestSize = 0.3

[<Literal>]
let Seed = 0

let MaxDepths = [ Some 1; Some 2; Some 3; Some 4; Some 5; None ]

[<Literal>]
let TreeDepthToShow = 2

let private formatDepth (maxDepth: int option) : string =
    maxDepth |> Option.map string |> Option.defaultValue "制限なし"

/// 深さ d の決定木の葉の数の上限（2 の d 乗）。制限なしのときは訓練データの件数を上限にする
let private leavesFor (maxDepth: int option) (trainCount: int) : int =
    maxDepth
    |> Option.map (fun depth -> pown 2 depth)
    |> Option.defaultValue trainCount

/// iris.csv で深さごとの正解率、ML.NET との予測の一致数、深さ 2 の決定木を表示する
let run (print: string -> unit) : unit =
    let split = prepareIris (Path.Combine(dataDir (), "iris.csv")) TestSize Seed
    print "深さ\t訓練データ\tテストデータ\tML.NET と一致"

    for maxDepth in MaxDepths do
        let tree = fit maxDepth split.XTrain split.TTrain
        let train = accuracy (predict tree split.XTrain) split.TTrain
        let mine = predict tree split.XTest
        let test = accuracy mine split.TTest

        let library =
            trainFastTree (leavesFor maxDepth split.XTrain.Length) split.XTrain split.TTrain split.XTest

        let agreed =
            List.zip mine library |> List.filter (fun (a, b) -> a = b) |> List.length

        print $"{formatDepth maxDepth}\t{train:F4}\t{test:F4}\t{agreed}/{mine.Length}"

    print ""
    print $"深さ {TreeDepthToShow} の決定木:"

    fit (Some TreeDepthToShow) split.XTrain split.TTrain
    |> formatTree
    |> List.iter print
