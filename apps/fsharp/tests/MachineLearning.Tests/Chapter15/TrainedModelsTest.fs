module MachineLearning.Tests.Chapter15.TrainedModelsTest

open System.IO
open System.Net
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter15.FileModelStore
open MachineLearning.Chapter15.Training
open MachineLearning.Tests.Chapter15.PredictionApiTest

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(
        [ "cinema.csv"; "Survived.csv" ]
        |> List.forall (fun name -> File.Exists(Path.Combine(dataDir (), name))),
        "学習データ cinema.csv・Survived.csv が配置されていない（gulp data:setup）"
    )

[<Fact>]
let ``実データで学習して保存したモデルで API が予測する`` () =
    task {
        requireData ()
        let directory = Directory.CreateTempSubdirectory("model-").FullName
        trainAndSaveModels (dataDir ()) directory
        let store = fileModelStore directory

        let! health = get store "/health"
        let! sales = postJson store "/cinema/sales" movieJson
        let! survived = postJson store "/survived" passengerJson

        Assert.Equal((HttpStatusCode.OK, """{"status":"ok","models":{"cinema":true,"survived":true}}"""), health)
        Assert.Equal(HttpStatusCode.OK, fst sales)
        Assert.StartsWith("""{"sales":""", snd sales)
        Assert.Equal((HttpStatusCode.OK, """{"survived":true}"""), survived)
    }

[<Fact>]
let ``学習すると保存したファイルの名前を表示する`` () =
    requireData ()
    let directory = Directory.CreateTempSubdirectory("model-").FullName
    let lines = ResizeArray<string>()

    trainAndReport directory lines.Add

    Assert.Equal<string seq>(
        [
            "学習済みモデルを保存しました: cinema.json, survived.json"
            "API を起動します: http://127.0.0.1:8015"
        ],
        lines
    )

    Assert.True(File.Exists(Path.Combine(directory, SalesModelFile)))
    Assert.True(File.Exists(Path.Combine(directory, SurvivalModelFile)))
