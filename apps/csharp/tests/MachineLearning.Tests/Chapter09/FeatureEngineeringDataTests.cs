namespace MachineLearning.Tests.Chapter09;

using MachineLearning.Chapter02;
using MachineLearning.Chapter09;
using MachineLearning.Dataset;

public class FeatureEngineeringDataTests
{
    private readonly string bostonFile = Path.Combine(DataDir.Current(), "Boston.csv");
    private readonly string bikeFile = Path.Combine(DataDir.Current(), "bike.tsv");
    private readonly string weatherFile = Path.Combine(DataDir.Current(), "weather.csv");

    [Fact(DisplayName = "Boston.csv を 70 件と 30 件に分け、CRIME をダミー変数の 2 列にする")]
    public void PreparesBoston()
    {
        this.SkipUnlessBoston();

        var split = this.Split();

        Assert.Equal(70, split.XTrain.Count);
        Assert.Equal(30, split.XTest.Count);
        Assert.Equal(
            ["ZN", "INDUS", "CHAS", "NOX", "RM", "AGE", "DIS", "RAD", "TAX", "PTRATIO", "B", "LSTAT", "CRIME_low", "CRIME_very_low"],
            split.XTrain[0].Columns);
    }

    [Theory(DisplayName = "特徴量の組ごとの決定係数を確かめる")]
    [InlineData("元の特徴量", 0.5723, 0.6970)]
    [InlineData("2 乗の項を追加", 0.7561, 0.8351)]
    [InlineData("交互作用の項も追加", 0.7779, 0.8078)]
    public void ScoresByFeatureSet(string name, double train, double test)
    {
        this.SkipUnlessBoston();

        var split = this.Split();
        var terms = MachineLearning.Chapter09.Program.FeatureSets()
            .First(pair => string.Equals(pair.Key, name, StringComparison.Ordinal)).Value;

        var scores = Boston.ScoreFeatureSet(split, MachineLearning.Chapter09.Program.Columns, terms);

        Assert.Equal(train, scores.Train, 4);
        Assert.Equal(test, scores.Test, 4);
    }

    [Fact(DisplayName = "訓練データの PRICE の外れ値 6 件はすべて高額の側にある")]
    public void OutliersAreExpensive()
    {
        this.SkipUnlessBoston();

        var prices = this.Split().TTrain;
        var outliers = Outliers.IqrOutliers(prices);

        Assert.Equal(17.95, Outliers.Quantile(prices, 0.25), 2);
        Assert.Equal(24.8, Outliers.Quantile(prices, 0.75), 2);
        Assert.Equal(
            [39.8, 43.8, 44.8, 48.5, 50.0, 50.0],
            prices.Where((_, i) => outliers[i]).Order());
    }

    [Fact(DisplayName = "bike.tsv と weather.csv を結合すると 731 件になる")]
    public void JoinsBikeAndWeather()
    {
        this.SkipUnlessBike();

        var joined = BikeWeather.JoinWeather(
            BikeWeather.LoadBike(this.bikeFile), BikeWeather.LoadWeather(this.weatherFile));

        Assert.Equal(731, joined.Rows.Count);
        Assert.Equal(["晴れ", "曇り", "雨"], BikeWeather.MeanCountByWeather(joined).Select(pair => pair.Key));
    }

    [Fact(DisplayName = "実行すると特徴量の組ごとの決定係数と天気ごとの平均利用者数を表示する")]
    public void PrintsSummary()
    {
        this.SkipUnlessBoston();
        this.SkipUnlessBike();

        using var output = new StringWriter();
        MachineLearning.Chapter09.Program.Run(output);

        Assert.Equal(
            string.Join(
                Environment.NewLine,
                "訓練データ: 70 件, テストデータ: 30 件",
                "特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low",
                "標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00",
                "ML.NET で正規化した訓練データの RM: 平均 0.00, 標準偏差 1.00",
                "決定係数:",
                "  元の特徴量（3 列）: 訓練 0.5723, テスト 0.6970",
                "  2 乗の項を追加（6 列）: 訓練 0.7561, テスト 0.8351",
                "  交互作用の項も追加（9 列）: 訓練 0.7779, テスト 0.8078",
                "訓練データの PRICE の外れ値: 6 件",
                "  外れ値を除いて 2 乗の項を追加: 訓練 0.6351, テスト 0.7110",
                "天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3") + Environment.NewLine,
            output.ToString());
    }

    private TrainTestSplit<Features, double> Split() => Boston.Prepare(this.bostonFile, 0.3, 0);

    private void SkipUnlessBoston() =>
        Assert.SkipUnless(File.Exists(this.bostonFile), "学習データ Boston.csv が配置されていない（gulp data:setup）");

    private void SkipUnlessBike() =>
        Assert.SkipUnless(
            File.Exists(this.bikeFile) && File.Exists(this.weatherFile),
            "学習データ bike.tsv・weather.csv が配置されていない（gulp data:setup）");
}
