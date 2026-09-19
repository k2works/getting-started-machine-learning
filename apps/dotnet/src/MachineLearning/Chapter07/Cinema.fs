module MachineLearning.Chapter07.Cinema

open FSharp.Data
open MachineLearning.Chapter02.IrisPreprocessing

/// 型プロバイダが列の名前と型を知るためのサンプル。学習データの行ではなく、同じ列を持つ架空の値で書く。
[<Literal>]
let CinemaSample = "cinema_id,SNS1,SNS2,actor,original,sales\n1,,0.2,,0,0.5"

/// 特徴量の列は欠損値がありうるので float option、映画の ID は int、興行収入は float として読む
type CinemaCsv = CsvProvider<CinemaSample, Schema="int,float option,float option,float option,float option,float">

/// 映画の ID、特徴量（列名から値への Map）、興行収入
type CinemaRow =
    {
        CinemaId: int
        Features: Map<string, float option>
        Sales: float
    }

let loadCinema (csvFile: string) : CinemaRow list =
    CinemaCsv.Load(csvFile).Rows
    |> Seq.map (fun row ->
        {
            CinemaId = row.Cinema_id
            Features =
                Map.ofList
                    [
                        "SNS1", row.SNS1
                        "SNS2", row.SNS2
                        "actor", row.Actor
                        "original", row.Original
                    ]
            Sales = row.Sales
        })
    |> Seq.toList

/// SNS2 がこの値を超え、かつ興行収入が OutlierSales 未満の映画を外れ値とする
[<Literal>]
let OutlierSns2 = 1000.0

[<Literal>]
let OutlierSales = 8500.0

/// SNS2 が欠損している映画は、外れ値かどうか判断できないので外れ値としない
let private isOutlier (row: CinemaRow) : bool =
    row.Features["SNS2"] |> Option.exists (fun sns2 -> sns2 > OutlierSns2)
    && row.Sales < OutlierSales

let removeOutliers (rows: CinemaRow list) : CinemaRow list = rows |> List.filter (isOutlier >> not)

/// 読み込み、外れ値を除き、訓練データとテストデータに分け、訓練データの平均で欠損値を補完する
let prepareCinema (csvFile: string) (testSize: float) (seed: int) : TrainTestSplit<Map<string, float>, float> =
    let rows = loadCinema csvFile |> removeOutliers
    let x = rows |> List.map (fun row -> row.Features)
    let t = rows |> List.map (fun row -> row.Sales)
    let split = splitTrainTest testSize seed x t
    let means = columnMeans split.XTrain

    {
        XTrain = fillMissing means split.XTrain
        XTest = fillMissing means split.XTest
        TTrain = split.TTrain
        TTest = split.TTest
    }
