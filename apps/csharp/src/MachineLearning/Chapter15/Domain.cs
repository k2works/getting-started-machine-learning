namespace MachineLearning.Chapter15;

/// <summary>客室の等級。</summary>
public enum Pclass
{
    First = 1,
    Second = 2,
    Third = 3,
}

/// <summary>性別。</summary>
public enum Sex
{
    Male,
    Female,
}

/// <summary>乗船した港。</summary>
public enum Embarked
{
    Cherbourg,
    Queenstown,
    Southampton,
}

/// <summary>興行収入を予測する映画の特徴量。</summary>
public sealed record Movie(double Sns1, double Sns2, double Actor, bool Original);

/// <summary>生存を予測する乗客。年齢と乗船港は分からないことがある。</summary>
public sealed record Passenger(
    Pclass Pclass,
    Sex Sex,
    double? Age,
    int SibSp,
    int Parch,
    double Fare,
    Embarked? Embarked);

/// <summary>予測した興行収入。</summary>
public sealed record SalesPrediction(double Sales);

/// <summary>生存するかどうかの予測。</summary>
public sealed record SurvivalPrediction(bool Survived);

/// <summary>
/// 予測できなかった理由。F# 版は判別共用体で表しているが、C# には無いので
/// 抽象レコードと sealed な派生で表す（第 3 章の決定木と同じ形）。
/// </summary>
public abstract record PredictionError(string Model)
{
    /// <summary>人に見せる説明。ファイルのパスは含めない。</summary>
    public abstract string Describe();
}

/// <summary>学習済みモデルが見つからない。</summary>
public sealed record ModelNotFound(string Model) : PredictionError(Model)
{
    public override string Describe() => $"学習済みモデル {this.Model} が見つかりません";
}

/// <summary>学習済みモデルのファイルはあるが、読み込めない（壊れている・形が違う）。</summary>
public sealed record ModelUnreadable(string Model) : PredictionError(Model)
{
    public override string Describe() => $"学習済みモデル {this.Model} を読み込めません";
}

/// <summary>映画の特徴量から興行収入を予測するモデル。</summary>
public delegate double SalesModel(Movie movie);

/// <summary>乗客が生存するかを判定するモデル。</summary>
public delegate bool SurvivalModel(Passenger passenger);

/// <summary>学習済みモデルの置き場。読み込めなければ理由を返す。</summary>
public interface IModelStore
{
    PredictionResult<SalesModel> LoadSalesModel();

    PredictionResult<SurvivalModel> LoadSurvivalModel();
}
