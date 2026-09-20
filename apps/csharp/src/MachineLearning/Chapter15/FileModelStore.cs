namespace MachineLearning.Chapter15;

using System.Text.Json;
using MachineLearning.Chapter02;
using MachineLearning.Chapter07;
using MachineLearning.Chapter08;

/// <summary>インフラ層。学習済みモデルをディレクトリのファイルに保存し、読み込む。</summary>
public sealed class FileModelStore : IModelStore
{
    /// <summary>興行収入のモデルの名前</summary>
    public const string SalesModelName = "cinema";

    /// <summary>生存のモデルの名前</summary>
    public const string SurvivalModelName = "survived";

    private readonly string modelDirectory;

    public FileModelStore(string modelDirectory) => this.modelDirectory = modelDirectory;

    /// <summary>線形回帰モデルを JSON で保存する。</summary>
    public void SaveSalesModel(LinearModel model)
    {
        ArgumentNullException.ThrowIfNull(model);
        Directory.CreateDirectory(this.modelDirectory);
        var saved = new SavedLinearModel(
            model.Intercept,
            [.. model.Coefficients.Columns],
            [.. model.Coefficients.Values]);
        File.WriteAllText(this.SalesModelFile(), JsonSerializer.Serialize(saved));
    }

    /// <summary>学習済みパイプラインを保存する（第 8 章の ModelFiles）。</summary>
    public void SaveSurvivalModel(FittedPipeline pipeline)
    {
        Directory.CreateDirectory(this.modelDirectory);
        ModelFiles.Save(pipeline, this.SurvivalModelFile());
    }

    public PredictionResult<SalesModel> LoadSalesModel() =>
        Load<SalesModel>(SalesModelName, this.SalesModelFile(), file =>
        {
            var saved = JsonSerializer.Deserialize<SavedLinearModel>(File.ReadAllText(file))
                ?? throw new JsonException("中身が空です");
            var model = new LinearModel(saved.Intercept, new Features(saved.Columns, saved.Coefficients));
            return movie => LinearRegression.Predict(model, [ToFeatures(model, movie)])[0];
        });

    public PredictionResult<SurvivalModel> LoadSurvivalModel() =>
        Load<SurvivalModel>(SurvivalModelName, this.SurvivalModelFile(), file =>
        {
            var pipeline = ModelFiles.Load(file);
            return passenger => pipeline.PredictOne(ToRow(passenger)) == 1;
        });

    /// <summary>線形回帰モデルを JSON にするための形。</summary>
    private sealed record SavedLinearModel(
        double Intercept, IReadOnlyList<string> Columns, IReadOnlyList<double> Coefficients);

    /// <summary>映画の特徴量を、第 7 章のモデルが期待する列の並びにそろえる。</summary>
    private static Features ToFeatures(LinearModel model, Movie movie)
    {
        var values = new Dictionary<string, double>(StringComparer.Ordinal)
        {
            ["SNS1"] = movie.Sns1,
            ["SNS2"] = movie.Sns2,
            ["actor"] = movie.Actor,
            ["original"] = movie.Original ? 1 : 0,
        };
        return new Features(model.Coefficients.Columns, [.. model.Coefficients.Columns.Select(column => values[column])]);
    }

    /// <summary>乗客の特徴量を、第 8 章のパイプラインが受け取るセルの文字列にする。分からない値は空欄。</summary>
    private static Row ToRow(Passenger passenger) =>
        new(new Dictionary<string, string>(StringComparer.Ordinal)
        {
            ["Pclass"] = ((int)passenger.Pclass).ToString(System.Globalization.CultureInfo.InvariantCulture),
            ["Sex"] = passenger.Sex == Sex.Male ? "male" : "female",
            ["Age"] = passenger.Age?.ToString(System.Globalization.CultureInfo.InvariantCulture) ?? string.Empty,
            ["SibSp"] = passenger.SibSp.ToString(System.Globalization.CultureInfo.InvariantCulture),
            ["Parch"] = passenger.Parch.ToString(System.Globalization.CultureInfo.InvariantCulture),
            ["Fare"] = passenger.Fare.ToString(System.Globalization.CultureInfo.InvariantCulture),
            ["Embarked"] = passenger.Embarked switch
            {
                Embarked.Cherbourg => "C",
                Embarked.Queenstown => "Q",
                Embarked.Southampton => "S",
                _ => string.Empty,
            },
        });

    /// <summary>ファイルが無ければ ModelNotFound、読み込めなければ ModelUnreadable を返す。</summary>
    private static PredictionResult<T> Load<T>(string model, string file, Func<string, T> read)
    {
        if (!File.Exists(file))
        {
            return new Failure<T>(new ModelNotFound(model));
        }

        try
        {
            return new Success<T>(read(file));
        }
        catch (Exception e) when (e is JsonException or InvalidDataException or ArgumentException)
        {
            return new Failure<T>(new ModelUnreadable(model));
        }
    }

    private string SalesModelFile() => Path.Combine(this.modelDirectory, $"{SalesModelName}.json");

    private string SurvivalModelFile() => Path.Combine(this.modelDirectory, $"{SurvivalModelName}.json");
}
