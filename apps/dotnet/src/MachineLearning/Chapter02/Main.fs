module MachineLearning.Chapter02.Main

open System
open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter02.IrisPreprocessing

[<Literal>]
let TestSize = 0.3

[<Literal>]
let Seed = 0

let private formatCounts (counts: (string * int) list) : string =
    counts
    |> List.map (fun (column, count) -> $"{column}={count}")
    |> String.concat ", "

/// 補完後の値は float なので None は入らない。数値として欠けている NaN の数を数える
let private totalMissing (rows: Map<string, float> list) : int =
    rows |> List.sumBy (Map.filter (fun _ value -> Double.IsNaN value) >> Map.count)

/// iris.csv を読み込み、欠損値の数と、分割・補完の結果を表示する
let run (print: string -> unit) : unit =
    let csvFile = Path.Combine(dataDir (), "iris.csv")
    let rows = loadIris csvFile
    let missing = rows |> List.map (fun row -> row.Features) |> countMissing

    let missingSpecies =
        rows |> List.filter (fun row -> row.Species = "") |> List.length

    let split = prepareIris csvFile TestSize Seed
    print $"データ件数: {rows.Length}"

    let counts =
        (FeatureNames |> List.map (fun column -> column, missing[column]))
        @ [ Target, missingSpecies ]

    print $"欠損値の数: {formatCounts counts}"
    print $"訓練データ: {split.XTrain.Length} 件, テストデータ: {split.XTest.Length} 件"
    print $"補完後の欠損値の数: 訓練データ {totalMissing split.XTrain}, テストデータ {totalMissing split.XTest}"
