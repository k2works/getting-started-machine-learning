/// アプリケーション層。置き場からモデルを読み込んで予測するユースケースと、ヘルスチェック。ドメイン層だけを知る
module MachineLearning.Chapter15.PredictionService

open MachineLearning.Chapter15.Domain

/// モデルごとに読み込めるかどうか
type Health = { Cinema: bool; Survived: bool }

let predictSales (store: ModelStore) (movie: Movie) : Result<SalesPrediction, PredictionError> =
    store.LoadSalesModel() |> Result.map (fun model -> { Sales = model movie })

let predictSurvival (store: ModelStore) (passenger: Passenger) : Result<SurvivalPrediction, PredictionError> =
    store.LoadSurvivalModel()
    |> Result.map (fun model -> { Survived = model passenger })

let health (store: ModelStore) : Health =
    {
        Cinema = store.LoadSalesModel() |> Result.isOk
        Survived = store.LoadSurvivalModel() |> Result.isOk
    }

/// 利用者に見せるエラーの説明
let describe (error: PredictionError) : string =
    match error with
    | ModelNotFound model -> $"学習済みモデル {model} が見つかりません"
    | ModelUnreadable model -> $"学習済みモデル {model} を読み込めません"
