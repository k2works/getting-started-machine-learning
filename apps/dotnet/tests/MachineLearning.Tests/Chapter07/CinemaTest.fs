module MachineLearning.Tests.Chapter07.CinemaTest

open System
open System.IO
open Xunit
open MachineLearning.Chapter07.Cinema

/// ヘッダーと rows を書いた一時ファイルのパスを返す
let writeCsv (rows: string) : string =
    let csvFile =
        Path.Combine(Directory.CreateTempSubdirectory("cinema-").FullName, "cinema.csv")

    File.WriteAllText(csvFile, "cinema_id,SNS1,SNS2,actor,original,sales\n" + rows)
    csvFile

[<Fact>]
let ``CSV を読み込み、空欄を None にする`` () =
    let csvFile = writeCsv "1,,500,9000.5,1,9500\n"

    let rows = loadCinema csvFile

    Assert.Equal<CinemaRow list>(
        [
            {
                CinemaId = 1
                Features = Map.ofList [ "SNS1", None; "SNS2", Some 500.0; "actor", Some 9000.5; "original", Some 1.0 ]
                Sales = 9500.0
            }
        ],
        rows
    )

/// SNS2 と興行収入だけを持つ映画
let movie (sns2: float) (sales: float) : CinemaRow =
    {
        CinemaId = 0
        Features = Map.ofList [ "SNS2", Some sns2 ]
        Sales = sales
    }

[<Fact>]
let ``SNS2 が 1000 を超え、興行収入が 8500 未満の行を取り除く`` () =
    let rows = [ movie 1200.0 8000.0; movie 600.0 9500.0 ]

    Assert.Equal<CinemaRow list>([ movie 600.0 9500.0 ], removeOutliers rows)

[<Fact>]
let ``条件の片方だけを満たす行は残す`` () =
    let rows = [ movie 1200.0 9800.0; movie 600.0 8000.0 ]

    Assert.Equal<CinemaRow list>(rows, removeOutliers rows)

[<Fact>]
let ``外れ値を除いて分割し、訓練データの平均で欠損値を補完する`` () =
    let csvFile =
        writeCsv (
            "1,100,300,9000.0,0,9200\n"
            + "2,,400,9500.0,1,9800\n"
            + "3,300,500,,1,10100\n"
            + "4,150,1200,8800.0,0,8100\n"
            + "5,250,700,9900.0,1,10300\n"
            + "6,120,650,9100.0,0,9400\n"
        )

    let split = prepareCinema csvFile 0.4 0

    Assert.Equal<string seq>([ "SNS1"; "SNS2"; "actor"; "original" ], split.XTrain.Head.Keys)
    Assert.Equal((3, 2), (split.XTrain.Length, split.XTest.Length))
    Assert.DoesNotContain(8100.0, split.TTrain @ split.TTest)

    Assert.All(
        split.XTrain @ split.XTest,
        (fun row -> Assert.All(row.Values, (fun value -> Assert.False(Double.IsNaN value))))
    )
