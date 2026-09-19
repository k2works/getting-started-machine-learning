module MachineLearning.Tests.Chapter15.Stubs

open MachineLearning.Chapter15.Domain

/// sns1 に 1000 を足すだけの興行収入のモデル
let stubSalesModel: SalesModel = fun movie -> 1000.0 + movie.Sns1

/// 女性なら生存と判定するだけのモデル
let stubSurvivalModel: SurvivalModel = fun passenger -> passenger.Sex = Female

/// 常にスタブのモデルを返す置き場
let stubModelStore: ModelStore =
    {
        LoadSalesModel = fun () -> Ok stubSalesModel
        LoadSurvivalModel = fun () -> Ok stubSurvivalModel
    }

/// モデルが 1 つも無い置き場
let emptyModelStore: ModelStore =
    {
        LoadSalesModel = fun () -> Error(ModelNotFound "cinema")
        LoadSurvivalModel = fun () -> Error(ModelNotFound "survived")
    }

let movie: Movie =
    {
        Sns1 = 200.0
        Sns2 = 500.0
        Actor = 3000.0
        Original = true
    }

let passenger: Passenger =
    {
        Pclass = First
        Sex = Female
        Age = None
        SibSp = 0
        Parch = 0
        Fare = 50.0
        Embarked = Some Cherbourg
    }
