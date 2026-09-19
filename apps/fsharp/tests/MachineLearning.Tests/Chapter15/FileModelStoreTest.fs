module MachineLearning.Tests.Chapter15.FileModelStoreTest

open System.IO
open Xunit
open MachineLearning.Chapter07.LinearRegression
open MachineLearning.Chapter08.Pipeline
open MachineLearning.Chapter08.WeightedTree
open MachineLearning.Chapter15.Domain
open MachineLearning.Chapter15.FileModelStore
open MachineLearning.Tests.Chapter15.Stubs

/// テストごとに空の一時ディレクトリを作る
let emptyDirectory () : string =
    Directory.CreateTempSubdirectory("model-").FullName

let salesModel: LinearModel =
    {
        Intercept = 100.0
        Coefficients = Map.ofList [ "SNS1", 2.0; "SNS2", 0.5; "actor", 1.0; "original", 10.0 ]
    }

[<Fact>]
let ``保存した線形回帰を読み込み、映画の特徴量から興行収入を予測する`` () =
    let directory = emptyDirectory ()
    saveSalesModel directory salesModel

    match (fileModelStore directory).LoadSalesModel() with
    | Ok predictSales ->
        // 100 + 2 × 200 + 0.5 × 500 + 1 × 3000 + 10 × 1（原作あり）
        Assert.Equal(3760.0, predictSales movie, 9)
    | Error error -> Assert.Fail $"%A{error}"

[<Fact>]
let ``モデルのファイルが無ければ ModelNotFound を返す`` () =
    let store = fileModelStore (emptyDirectory ())

    Assert.Equal(Error(ModelNotFound "cinema"), store.LoadSalesModel() |> Result.map ignore)
    Assert.Equal(Error(ModelNotFound "survived"), store.LoadSurvivalModel() |> Result.map ignore)

[<Fact>]
let ``モデルのファイルが壊れていれば ModelUnreadable を返す`` () =
    let directory = emptyDirectory ()
    File.WriteAllText(Path.Combine(directory, SalesModelFile), "{")

    Assert.Equal(Error(ModelUnreadable "cinema"), (fileModelStore directory).LoadSalesModel() |> Result.map ignore)

[<Fact>]
let ``ドメインの乗客を第 8 章の乗客に変換する`` () =
    let converted = toChapter08Passenger passenger

    Assert.Equal(1, converted.Pclass)
    Assert.Equal("female", converted.Sex)
    Assert.Equal(None, converted.Age)
    Assert.Equal(Some "C", converted.Embarked)

[<Fact>]
let ``保存したパイプラインを読み込み、第 8 章と同じ生存の予測をする`` () =
    let passengers =
        [ Male, 30.0; Female, 20.0; Male, 60.0; Female, 40.0 ]
        |> List.map (fun (sex, age) ->
            toChapter08Passenger
                { passenger with
                    Sex = sex
                    Age = Some age
                })

    let pipeline =
        fitPipeline
            {
                MaxDepth = Some 2
                ClassWeight = Unweighted
            }
            passengers
            [ 0; 1; 0; 1 ]

    let directory = emptyDirectory ()
    saveSurvivalModel directory pipeline

    match (fileModelStore directory).LoadSurvivalModel() with
    | Ok survives ->
        for p in [ passenger; { passenger with Sex = Male } ] do
            Assert.Equal(predictPassenger pipeline (toChapter08Passenger p) = 1, survives p)
    | Error error -> Assert.Fail $"%A{error}"
