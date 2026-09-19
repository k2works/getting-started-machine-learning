/// ドメイン層。映画・乗客・予測・予測できなかった理由の型と、「モデル」と「モデルの置き場」の約束。ほかのどの層も知らない
module MachineLearning.Chapter15.Domain

/// 興行収入を予測する映画の特徴量
type Movie =
    {
        Sns1: float
        Sns2: float
        Actor: float
        /// 原作があるか
        Original: bool
    }

/// 客室の等級
type Pclass =
    | First
    | Second
    | Third

type Sex =
    | Male
    | Female

/// 乗船した港
type Embarked =
    | Cherbourg
    | Queenstown
    | Southampton

/// 生存を予測する乗客。年齢と乗船港は分からないことがある
type Passenger =
    {
        Pclass: Pclass
        Sex: Sex
        Age: float option
        SibSp: int
        Parch: int
        Fare: float
        Embarked: Embarked option
    }

type SalesPrediction = { Sales: float }

type SurvivalPrediction = { Survived: bool }

/// 予測できなかった理由
type PredictionError =
    /// 学習済みモデルが見つからない。持つのはモデルの名前だけで、ファイルのパスは持たない
    | ModelNotFound of model: string
    /// 学習済みモデルのファイルはあるが、読み込めない（壊れている・形が違う）
    | ModelUnreadable of model: string

/// 映画の特徴量から興行収入を予測するモデル
type SalesModel = Movie -> float

/// 乗客が生存するかを判定するモデル
type SurvivalModel = Passenger -> bool

/// 学習済みモデルの置き場。読み込めなければ Error を返す
type ModelStore =
    {
        LoadSalesModel: unit -> Result<SalesModel, PredictionError>
        LoadSurvivalModel: unit -> Result<SurvivalModel, PredictionError>
    }
