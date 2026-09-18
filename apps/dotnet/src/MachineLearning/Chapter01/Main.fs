module MachineLearning.Chapter01.Main

open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter01.KinokoTakenoko

/// KvsT.csv を読み込み、ルールによる判定の正解率を表示する
let run (print: string -> unit) : unit =
    let people = loadPeople (Path.Combine(dataDir (), "KvsT.csv"))
    let features, labels = splitFeaturesAndLabels people
    let predictions = features |> List.map predictByRule
    print $"データ件数: {people.Length}"
    print $"ルールによる判定の正解率: {accuracy predictions labels:F4}"
