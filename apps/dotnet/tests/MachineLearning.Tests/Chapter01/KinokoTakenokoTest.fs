module MachineLearning.Tests.Chapter01.KinokoTakenokoTest

open System.IO
open System.Text
open Xunit
open MachineLearning.Chapter01.KinokoTakenoko

/// BOM 付きの UTF-8 で、ヘッダーと rows を書いた一時ファイルのパスを返す
let writeCsv (rows: string) : string =
    let csvFile =
        Path.Combine(Directory.CreateTempSubdirectory("kvst-").FullName, "kvst.csv")

    File.WriteAllText(csvFile, "身長,体重,年代,派閥\n" + rows, UTF8Encoding(true))
    csvFile

[<Fact>]
let ``BOM 付き CSV を読み込んで人物のリストを返す`` () =
    let csvFile = writeCsv "165,58,30,きのこ\n"

    let people = loadPeople csvFile

    Assert.Equal<Person list>(
        [
            {
                Height = 165.0
                Weight = 58.0
                AgeGroup = 30
                Faction = Kinoko
            }
        ],
        people
    )

[<Fact>]
let ``複数行の CSV を読み込んで行の順に人物のリストを返す`` () =
    let csvFile = writeCsv "161,52,20,きのこ\n183,74,50,たけのこ\n"

    let people = loadPeople csvFile

    Assert.Equal<Person list>(
        [
            {
                Height = 161.0
                Weight = 52.0
                AgeGroup = 20
                Faction = Kinoko
            }
            {
                Height = 183.0
                Weight = 74.0
                AgeGroup = 50
                Faction = Takenoko
            }
        ],
        people
    )

[<Fact>]
let ``列が足りない CSV は列の名前を示すエラーになる`` () =
    let csvFile =
        Path.Combine(Directory.CreateTempSubdirectory("kvst-").FullName, "kvst.csv")

    File.WriteAllText(csvFile, "身長,体重,年代\n165,58,30\n")

    let error = Assert.Throws<exn>(fun () -> loadPeople csvFile |> ignore)

    Assert.Equal("列 派閥 が見つかりません", error.Message)

[<Fact>]
let ``派閥がきのこ・たけのこのどちらでもなければエラーになる`` () =
    let csvFile = writeCsv "165,58,30,すぎのこ\n"

    let error = Assert.Throws<exn>(fun () -> loadPeople csvFile |> ignore)

    Assert.Equal("派閥 すぎのこ は、きのこ・たけのこのどちらでもありません", error.Message)

[<Fact>]
let ``人物のリストを特徴量と正解ラベルに分ける`` () =
    let people =
        [
            {
                Height = 161.0
                Weight = 52.0
                AgeGroup = 20
                Faction = Kinoko
            }
            {
                Height = 183.0
                Weight = 74.0
                AgeGroup = 50
                Faction = Takenoko
            }
        ]

    let features, labels = splitFeaturesAndLabels people

    Assert.Equal<Features list>(
        [
            {
                Height = 161.0
                Weight = 52.0
                AgeGroup = 20
            }
            {
                Height = 183.0
                Weight = 74.0
                AgeGroup = 50
            }
        ],
        features
    )

    Assert.Equal<Faction list>([ Kinoko; Takenoko ], labels)

[<Fact>]
let ``20 代ならきのこ派と判定する`` () =
    let features =
        {
            Height = 161.0
            Weight = 52.0
            AgeGroup = 20
        }

    Assert.Equal(Kinoko, predictByRule features)

[<Fact>]
let ``20 代以外ならたけのこ派と判定する`` () =
    let features =
        {
            Height = 183.0
            Weight = 74.0
            AgeGroup = 50
        }

    Assert.Equal(Takenoko, predictByRule features)

[<Fact>]
let ``すべての予測が正解なら正解率は 1`` () =
    Assert.Equal(1.0, accuracy [ Kinoko; Takenoko ] [ Kinoko; Takenoko ])

[<Fact>]
let ``4 件中 3 件の予測が正解なら正解率は 0.75`` () =
    let predictions = [ Kinoko; Kinoko; Takenoko; Takenoko ]
    let labels = [ Kinoko; Takenoko; Takenoko; Takenoko ]

    Assert.Equal(0.75, accuracy predictions labels)

[<Fact>]
let ``予測と正解ラベルの件数が違えばエラーになる`` () =
    let error =
        Assert.Throws<exn>(fun () -> accuracy [ Kinoko ] [ Kinoko; Takenoko ] |> ignore)

    Assert.Equal("予測と正解ラベルの件数が違います", error.Message)
