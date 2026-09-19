module MachineLearning.Chapter15.FileModelStore

open System.IO
open System.Text.Json
open MachineLearning.Chapter07.LinearRegression
open MachineLearning.Chapter08
open MachineLearning.Chapter08.Pipeline
open MachineLearning.Chapter15.Domain

[<Literal>]
let SalesModelFile = "cinema.json"

[<Literal>]
let SurvivalModelFile = "survived.json"

/// 映画を、第 7 章の線形回帰の特徴量（列名から値への Map）にする
let toCinemaFeatures (movie: Movie) : Map<string, float> =
    Map.ofList
        [
            "SNS1", movie.Sns1
            "SNS2", movie.Sns2
            "actor", movie.Actor
            "original", (if movie.Original then 1.0 else 0.0)
        ]

/// ドメインの乗客を、第 8 章のパイプラインが受け取る乗客にする
let toChapter08Passenger (passenger: Passenger) : SurvivedData.Passenger =
    {
        Pclass =
            match passenger.Pclass with
            | First -> 1
            | Second -> 2
            | Third -> 3
        Sex =
            match passenger.Sex with
            | Male -> "male"
            | Female -> "female"
        Age = passenger.Age
        SibSp = passenger.SibSp
        Parch = passenger.Parch
        Fare = passenger.Fare
        Embarked =
            passenger.Embarked
            |> Option.map (function
                | Cherbourg -> "C"
                | Queenstown -> "Q"
                | Southampton -> "S")
    }

/// 第 7 章の線形回帰を JSON で保存する。保存先のディレクトリが無ければ作る
let saveSalesModel (directory: string) (model: LinearModel) : unit =
    Directory.CreateDirectory directory |> ignore
    File.WriteAllText(Path.Combine(directory, SalesModelFile), JsonSerializer.Serialize model)

/// 第 8 章のパイプラインを JSON で保存する
let saveSurvivalModel (directory: string) (pipeline: FittedPipeline) : unit =
    ModelFile.saveModel (Path.Combine(directory, SurvivalModelFile)) pipeline

/// ファイルが無ければ ModelNotFound、読み込めなければ ModelUnreadable にする
let private loadFile (name: string) (file: string) (read: string -> Result<'M, string>) : Result<'M, PredictionError> =
    if not (File.Exists file) then
        Error(ModelNotFound name)
    else
        read file |> Result.mapError (fun _ -> ModelUnreadable name)

let private readLinearModel (file: string) : Result<LinearModel, string> =
    try
        Ok(JsonSerializer.Deserialize<LinearModel>(File.ReadAllText file))
    with :? JsonException as e ->
        Error e.Message

/// ディレクトリに保存した学習済みモデルを、呼ばれるたびに読み込む置き場
let fileModelStore (directory: string) : ModelStore =
    {
        LoadSalesModel =
            fun () ->
                loadFile "cinema" (Path.Combine(directory, SalesModelFile)) readLinearModel
                |> Result.map (fun model movie ->
                    predictLinearRegression model [ toCinemaFeatures movie ] |> List.exactlyOne)
        LoadSurvivalModel =
            fun () ->
                loadFile "survived" (Path.Combine(directory, SurvivalModelFile)) ModelFile.loadModel
                |> Result.map (fun pipeline passenger -> predictPassenger pipeline (toChapter08Passenger passenger) = 1)
    }
