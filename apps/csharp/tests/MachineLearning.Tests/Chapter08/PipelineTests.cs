namespace MachineLearning.Tests.Chapter08;

using MachineLearning.Chapter02;
using MachineLearning.Chapter08;

public class PipelineTests
{
    private static readonly Row[] Train =
    [
        Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
        Passengers.Of("1", "female", "40", "1", "0", "60", "C"),
        Passengers.Of("3", "male", "20", "0", "0", "8", "S"),
        Passengers.Of("3", "male", string.Empty, "0", "0", "7", string.Empty),
    ];

    private static readonly int[] Labels = [1, 1, 0, 0];

    [Fact(DisplayName = "パイプラインは補完もダミー変数化もしていないデータから学習して予測する")]
    public void FitsAndPredicts()
    {
        var pipeline = Pipeline.Build(2, ClassWeight.None).Fit(Passengers.ToTable(Train), Labels);

        Assert.Equal(Labels, pipeline.Predict(Passengers.ToTable(Train)));
    }

    [Fact(DisplayName = "パイプラインは前処理を済ませた特徴量を返す")]
    public void TransformsToFeatures()
    {
        var pipeline = Pipeline.Build(2, ClassWeight.None).Fit(Passengers.ToTable(Train), Labels);

        var features = pipeline.ToFeatures(Passengers.ToTable(Train));

        Assert.Equal(
            ["Pclass", "Age", "SibSp", "Parch", "Fare", "Sex_male", "Embarked_S"],
            features[0].Columns);
        Assert.Equal(20.0, features[3].Value("Age"));
        Assert.Equal(1.0, features[3].Value("Embarked_S"));
    }

    [Fact(DisplayName = "年齢が欠けた乗客 1 人でも保存した値で補完して予測できる")]
    public void PredictsOnePassengerWithMissingAge()
    {
        var pipeline = Pipeline.Build(2, ClassWeight.None).Fit(Passengers.ToTable(Train), Labels);

        Assert.Equal(1, pipeline.PredictOne(Passengers.Of("1", "female", string.Empty, "0", "0", "50", "C")));
    }

    [Fact(DisplayName = "補完を飛ばしたパイプラインは欠損値が残ったまま特徴量にできない")]
    public void FailsWithoutImputation()
    {
        var pipeline = new Pipeline([new DummyEncoder(["Sex", "Embarked"])], new DecisionTreeClassifier(2, ClassWeight.None));

        var error = Assert.Throws<ArgumentException>(() => pipeline.Fit(Passengers.ToTable(Train), Labels));

        Assert.Contains("欠損値が残っています: Age", error.Message, StringComparison.Ordinal);
    }

    [Fact(DisplayName = "評価は正解率と、見つけた生存者の人数を返す")]
    public void Evaluates()
    {
        var split = new TrainTestSplit<Row, int>(Train, Train, Labels, Labels);
        var pipeline = Pipeline.Build(2, ClassWeight.None).Fit(SurvivedData.ToTable(split.XTrain), split.TTrain);

        var result = Evaluation.Of(pipeline, split);

        Assert.Equal(new Evaluation(1.0, 1.0, 2, 2), result);
    }
}
