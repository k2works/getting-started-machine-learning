module MachineLearning.Chapter02.IrisPreprocessing

open FSharp.Data
open MachineLearning.Chapter02.Random

/// 型プロバイダが列の名前と型を知るためのサンプル。
/// 学習データはコミットできないので、同じ列を持つ架空の値で書く。
[<Literal>]
let IrisSample = "がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n0.1,0.2,,0.4,Iris-sample"

/// 空欄がありうる数値の列は float option として読む
type IrisCsv = CsvProvider<IrisSample, Schema="float option,float option,float option,float option,string">

let FeatureNames = [ "がく片長さ"; "がく片幅"; "花弁長さ"; "花弁幅" ]

[<Literal>]
let Target = "種類"

/// 特徴量（列名から値への Map）と正解ラベルの組
type IrisRow =
    {
        Features: Map<string, float option>
        Species: string
    }

let loadIris (csvFile: string) : IrisRow list =
    IrisCsv.Load(csvFile).Rows
    |> Seq.map (fun row ->
        {
            Features = Map.ofList [ "がく片長さ", row.がく片長さ; "がく片幅", row.がく片幅; "花弁長さ", row.花弁長さ; "花弁幅", row.花弁幅 ]
            Species = row.種類
        })
    |> Seq.toList

/// 行の列名の一覧（1 行目の列名を使う）
let private columnsOf (rows: Map<string, 'V> list) : string list =
    match rows with
    | [] -> []
    | first :: _ -> first |> Map.keys |> Seq.toList

/// 列ごとに値を集計して、列名から集計結果への Map を返す
let private byColumn (rows: Map<string, 'V> list) (aggregate: 'V list -> 'R) : Map<string, 'R> =
    columnsOf rows
    |> List.map (fun column -> column, rows |> List.map (fun row -> row[column]) |> aggregate)
    |> Map.ofList

let countMissing (rows: Map<string, 'V option> list) : Map<string, int> =
    byColumn rows (List.filter Option.isNone >> List.length)

let columnMeans (rows: Map<string, float option> list) : Map<string, float> =
    byColumn rows (List.choose id >> List.average)

let fillMissing (values: Map<string, float>) (rows: Map<string, float option> list) : Map<string, float> list =
    rows
    |> List.map (Map.map (fun column value -> value |> Option.defaultValue values[column]))

type TrainTestSplit<'X, 'T> =
    {
        XTrain: 'X list
        XTest: 'X list
        TTrain: 'T list
        TTest: 'T list
    }

/// シードで並べ替えた順に、テストデータの割合（切り上げ）を除いた先頭を訓練データにする
let splitTrainTest (testSize: float) (seed: int) (x: 'X list) (t: 'T list) : TrainTestSplit<'X, 'T> =
    let pairs = List.zip x t |> shuffle seed
    let nTrain = pairs.Length - int (ceil (float pairs.Length * testSize))
    let train, test = List.splitAt nTrain pairs

    {
        XTrain = train |> List.map fst
        XTest = test |> List.map fst
        TTrain = train |> List.map snd
        TTest = test |> List.map snd
    }

/// 読み込み、訓練データとテストデータに分け、訓練データの平均で欠損値を補完する
let prepareIris (csvFile: string) (testSize: float) (seed: int) : TrainTestSplit<Map<string, float>, string> =
    let rows = loadIris csvFile
    let x = rows |> List.map (fun row -> row.Features)
    let t = rows |> List.map (fun row -> row.Species)
    let split = splitTrainTest testSize seed x t
    let means = columnMeans split.XTrain

    {
        XTrain = fillMissing means split.XTrain
        XTest = fillMissing means split.XTest
        TTrain = split.TTrain
        TTest = split.TTest
    }
