module MachineLearning.Tests.Chapter08.SurvivedDataTest

open System.IO
open System.Text
open Xunit
open MachineLearning.Chapter08.SurvivedData

/// BOM 付きの UTF-8 で、ヘッダーと rows を書いた一時ファイルのパスを返す
let writeCsv (rows: string) : string =
    let csvFile =
        Path.Combine(Directory.CreateTempSubdirectory("survived-").FullName, "survived.csv")

    File.WriteAllText(
        csvFile,
        "PassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked\n"
        + rows,
        UTF8Encoding(true)
    )

    csvFile

/// 学習用テスト: Schema を指定しないときに型プロバイダが推論する型
type NoSchemaCsv = FSharp.Data.CsvProvider<SurvivedSample>

[<Fact>]
let ``学習用テスト: Schema を指定しないと、空欄だけの列は string、1 文字の列も string と推論される`` () =
    let row = NoSchemaCsv.GetSample().Rows |> Seq.head

    Assert.IsType<string>(box row.Age) |> ignore
    Assert.IsType<string>(box row.Embarked) |> ignore
    Assert.IsType<decimal>(box row.Fare) |> ignore

[<Fact>]
let ``BOM 付き CSV を読み込み、空欄を None にする`` () =
    let rows = loadSurvived (writeCsv "1,0,3,male,,0,0,X-1,8.5,,S\n")

    Assert.Equal<SurvivedRow list>(
        [
            {
                PassengerId = 1
                Survived = 0
                Ticket = "X-1"
                Cabin = None
                Passenger =
                    {
                        Pclass = 3
                        Sex = "male"
                        Age = None
                        SibSp = 0
                        Parch = 0
                        Fare = 8.5
                        Embarked = Some "S"
                    }
            }
        ],
        rows
    )

[<Fact>]
let ``値のある列は型のとおりに、空欄の港は None に読み込む`` () =
    let row =
        loadSurvived (writeCsv "2,1,1,female,38.5,1,0,PC 17599,71.2833,C85,\n")
        |> List.head

    Assert.Equal(Some "C85", row.Cabin)
    Assert.Equal(Some 38.5, row.Passenger.Age)
    Assert.Equal(None, row.Passenger.Embarked)

[<Fact>]
let ``特徴量の乗客と Survived 列に分ける`` () =
    let rows =
        loadSurvived (writeCsv "1,0,3,male,,0,0,X-1,8.5,,S\n2,1,1,female,38,1,0,X-2,71.3,C85,C\n")

    let x, t = splitFeaturesAndTarget rows

    Assert.Equal<Passenger list>(rows |> List.map (fun row -> row.Passenger), x)
    Assert.Equal<int list>([ 0; 1 ], t)
