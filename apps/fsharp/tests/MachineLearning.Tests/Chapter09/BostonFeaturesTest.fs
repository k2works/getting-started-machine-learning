module MachineLearning.Tests.Chapter09.BostonFeaturesTest

open System.IO
open Xunit
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter09.BostonFeatures

/// 見出しと行を書いた一時ファイルのパスを返す（値は架空）
let writeCsv (lines: string list) : string =
    let csvFile = Path.GetTempFileName()
    File.WriteAllText(csvFile, String.concat "\n" lines)
    csvFile

[<Fact>]
let ``CRIME をダミー変数にし、空欄は None として、PRICE を除いた列を CSV の順に並べる`` () =
    let csvFile =
        writeCsv
            [
                "CRIME,RM,AGE,PRICE"
                "high,6.0,,20.0"
                "low,5.0,40.0,10.0"
                "very_low,7.0,60.0,30.0"
            ]

    let names, rows = loadBostonFeatures csvFile

    Assert.Equal<string list>([ "RM"; "AGE"; "CRIME_low"; "CRIME_very_low" ], names)

    Assert.Equal<Map<string, float option>>(
        Map.ofList
            [
                "RM", Some 6.0
                "AGE", None
                "CRIME_low", Some 0.0
                "CRIME_very_low", Some 0.0
            ],
        fst rows.Head
    )

    Assert.Equal<float list>([ 20.0; 10.0; 30.0 ], rows |> List.map snd)

[<Fact>]
let ``直線の関係にあるデータなら、元の特徴量だけで訓練データとテストデータの決定係数が 1 になる`` () =
    let x = [ 1.0 .. 10.0 ] |> List.map (fun value -> Map.ofList [ "RM", value ])
    let t = [ 1.0 .. 10.0 ] |> List.map (fun value -> 3.0 * value + 1.0)
    let split = splitTrainTest 0.3 0 x t

    let train, test = scoreFeatureSet split [ "RM" ] [ "RM" ]

    Assert.Equal(1.0, train, 9)
    Assert.Equal(1.0, test, 9)
