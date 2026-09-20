namespace MachineLearning.Tests.Chapter08;

using MachineLearning.Chapter02;
using MachineLearning.Chapter08;

public class TransformersTests
{
    [Fact(DisplayName = "年齢の欠損値を同じグループの中央値で補完する")]
    public void FillsAgeWithGroupMedian()
    {
        var x = Passengers.ToTable(
            Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
            Passengers.Of("1", "female", "40", "0", "0", "50", "S"),
            Passengers.Of("1", "female", string.Empty, "0", "0", "50", "S"));

        var filled = new GroupMedianImputer("Age", ["Pclass", "Sex"]).Fit(x).Transform(x);

        Assert.Equal(35.0, filled.Rows[2].Number("Age"));
    }

    [Fact(DisplayName = "グループごとに異なる中央値で補完する")]
    public void FillsAgeByGroup()
    {
        var x = Passengers.ToTable(
            Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
            Passengers.Of("3", "male", "20", "0", "0", "8", "S"),
            Passengers.Of("1", "female", string.Empty, "0", "0", "50", "S"),
            Passengers.Of("3", "male", string.Empty, "0", "0", "8", "S"));

        var filled = new GroupMedianImputer("Age", ["Pclass", "Sex"]).Fit(x).Transform(x);

        Assert.Equal(30.0, filled.Rows[2].Number("Age"));
        Assert.Equal(20.0, filled.Rows[3].Number("Age"));
    }

    [Fact(DisplayName = "訓練データで求めた中央値を別のデータの補完に使う")]
    public void UsesTrainedMedianForOtherData()
    {
        var train = Passengers.ToTable(
            Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
            Passengers.Of("1", "female", "40", "0", "0", "50", "S"));
        var test = Passengers.ToTable(Passengers.Of("1", "female", string.Empty, "0", "0", "50", "S"));

        var filled = new GroupMedianImputer("Age", ["Pclass", "Sex"]).Fit(train).Transform(test);

        Assert.Equal(35.0, filled.Rows[0].Number("Age"));
    }

    [Fact(DisplayName = "訓練データに無いグループは全体の中央値で補完する")]
    public void FallsBackToOverallMedian()
    {
        var train = Passengers.ToTable(
            Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
            Passengers.Of("1", "female", "40", "0", "0", "50", "S"),
            Passengers.Of("1", "female", "50", "0", "0", "50", "S"));
        var test = Passengers.ToTable(Passengers.Of("3", "male", string.Empty, "0", "0", "8", "S"));

        var filled = new GroupMedianImputer("Age", ["Pclass", "Sex"]).Fit(train).Transform(test);

        Assert.Equal(40.0, filled.Rows[0].Number("Age"));
    }

    [Fact(DisplayName = "乗船した港の欠損値を最も多い値で補完する")]
    public void FillsEmbarkedWithMostFrequent()
    {
        var x = Passengers.ToTable(
            Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
            Passengers.Of("1", "female", "40", "0", "0", "50", "C"),
            Passengers.Of("1", "female", "50", "0", "0", "50", "S"),
            Passengers.Of("1", "female", "60", "0", "0", "50", string.Empty));

        var filled = new MostFrequentImputer("Embarked").Fit(x).Transform(x);

        Assert.Equal("S", filled.Rows[3].Text("Embarked"));
    }

    [Fact(DisplayName = "カテゴリ値を最初のカテゴリを除いた 0 と 1 の列にする")]
    public void EncodesDummies()
    {
        var x = Passengers.ToTable(
            Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
            Passengers.Of("3", "male", "20", "0", "0", "8", "C"));

        var encoded = new DummyEncoder(["Sex", "Embarked"]).Fit(x).Transform(x);

        Assert.Equal(
            ["Pclass", "Age", "SibSp", "Parch", "Fare", "Sex_male", "Embarked_S"],
            encoded.Columns);
        Assert.Equal(0.0, encoded.Rows[0].Number("Sex_male"));
        Assert.Equal(1.0, encoded.Rows[0].Number("Embarked_S"));
        Assert.Equal(1.0, encoded.Rows[1].Number("Sex_male"));
        Assert.Equal(0.0, encoded.Rows[1].Number("Embarked_S"));
    }

    [Fact(DisplayName = "別のデータにも訓練データと同じダミー変数の列を作る")]
    public void EncodesSameColumnsForOtherData()
    {
        var train = Passengers.ToTable(
            Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
            Passengers.Of("3", "male", "20", "0", "0", "8", "C"),
            Passengers.Of("2", "male", "40", "0", "0", "20", "Q"));
        var test = Passengers.ToTable(Passengers.Of("1", "female", "30", "0", "0", "50", "S"));

        var encoded = new DummyEncoder(["Sex", "Embarked"]).Fit(train).Transform(test);

        Assert.Equal(
            ["Pclass", "Age", "SibSp", "Parch", "Fare", "Sex_male", "Embarked_Q", "Embarked_S"],
            encoded.Columns);
        Assert.Equal(0.0, encoded.Rows[0].Number("Embarked_Q"));
        Assert.Equal(1.0, encoded.Rows[0].Number("Embarked_S"));
    }

    [Fact(DisplayName = "中央値は件数が偶数なら中央の 2 つの平均になる")]
    public void MedianOfEvenCount()
    {
        Assert.Equal(3.0, GroupMedianImputer.Median([1.0, 2.0, 4.0, 5.0]));
        Assert.Equal(2.0, GroupMedianImputer.Median([1.0, 2.0, 5.0]));
    }

    [Fact(DisplayName = "前処理を合成すると順に変換する")]
    public void ComposesFittedTransformers()
    {
        var x = Passengers.ToTable(
            Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
            Passengers.Of("3", "male", string.Empty, "0", "0", "8", string.Empty));

        var fitted = new GroupMedianImputer("Age", ["Pclass", "Sex"]).Fit(x)
            .AndThen(new MostFrequentImputer("Embarked").Fit(x));
        var transformed = fitted.Transform(x);

        Assert.Equal(30.0, transformed.Rows[1].Number("Age"));
        Assert.Equal("S", transformed.Rows[1].Text("Embarked"));
    }
}
