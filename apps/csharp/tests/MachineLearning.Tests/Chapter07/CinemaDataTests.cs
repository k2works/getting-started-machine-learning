namespace MachineLearning.Tests.Chapter07;

using MachineLearning.Chapter07;
using MachineLearning.Dataset;

public class CinemaDataTests
{
    private readonly string csvFile = Path.Combine(DataDir.Current(), "cinema.csv");

    [Fact(DisplayName = "実データから外れ値を 1 件取り除く")]
    public void RemovesOneOutlier()
    {
        this.RequireData();
        var table = Cinema.Load(this.csvFile);

        Assert.Equal((100, 99), (table.Rows.Count, Cinema.RemoveOutliers(table).Rows.Count));
    }

    [Fact(DisplayName = "実データの切片と係数は F# 版と一致する")]
    public void CoefficientsMatchFSharp()
    {
        this.RequireData();
        var split = Cinema.Prepare(this.csvFile, 0.2, 0);

        var model = LinearRegression.Fit(split.XTrain, split.TTrain);

        Assert.Equal(6330.97, model.Intercept, 2);
        Assert.Equal(1.1481, model.Coefficients.Value("SNS1"), 4);
        Assert.Equal(0.5122, model.Coefficients.Value("SNS2"), 4);
        Assert.Equal(0.2748, model.Coefficients.Value("actor"), 4);
        Assert.Equal(242.5101, model.Coefficients.Value("original"), 4);
    }

    [Fact(DisplayName = "実データのテストデータの評価は F# 版と一致する")]
    public void MetricsMatchFSharp()
    {
        this.RequireData();
        var split = Cinema.Prepare(this.csvFile, 0.2, 0);

        var y = LinearRegression.Predict(LinearRegression.Fit(split.XTrain, split.TTrain), split.XTest);

        Assert.Equal(0.7740, RegressionMetrics.R2Score(split.TTest, y), 4);
        Assert.Equal(320.18, RegressionMetrics.MeanAbsoluteError(split.TTest, y), 2);
        Assert.Equal(396.73, RegressionMetrics.RootMeanSquaredError(split.TTest, y), 2);
    }

    [Fact(DisplayName = "実データでも ML.NET の SDCA は自作とおおむね同じ評価になる")]
    public void SdcaAgreesOnRealData()
    {
        this.RequireData();
        var split = Cinema.Prepare(this.csvFile, 0.2, 0);

        var library = MlNetRegression.TrainSdca(split.XTrain, split.TTrain)(split.XTest);

        Assert.Equal(0.7659, RegressionMetrics.R2Score(split.TTest, library), 4);
    }

    [Fact(DisplayName = "章の実行は件数・係数・評価を表示する")]
    public void RunPrintsSummary()
    {
        this.RequireData();
        var output = new StringWriter();

        Program.Run(output);

        Assert.Equal(
            """
            データ件数: 100
            外れ値を除いた件数: 99
            訓練データ: 79 件, テストデータ: 20 件
            切片: 6330.97
            係数: SNS1=1.1481, SNS2=0.5122, actor=0.2748, original=242.5101
            テストデータの評価: R2=0.7740, MAE=320.18, RMSE=396.73
            ML.NET(SDCA) の評価: R2=0.7659, MAE=338.69, RMSE=403.77

            """.ReplaceLineEndings(),
            output.ToString());
    }

    private void RequireData() =>
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ cinema.csv が配置されていない（gulp data:setup）");
}
