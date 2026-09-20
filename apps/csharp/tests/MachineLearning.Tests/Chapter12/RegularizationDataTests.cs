namespace MachineLearning.Tests.Chapter12;

using MachineLearning.Chapter07;
using MachineLearning.Chapter12;
using MachineLearning.Dataset;
using Chapter12Program = MachineLearning.Chapter12.Program;

public class RegularizationDataTests
{
    private readonly string csvFile = Path.Combine(DataDir.Current(), "Boston.csv");

    [Fact(DisplayName = "実データから外れ値を 2 件取り除く")]
    public void RemovesTwoOutliers()
    {
        this.RequireData();
        var (x, t) = Boston.Load(this.csvFile);

        var (keptX, _) = Boston.RemoveOutliers(x, t, Boston.OutlierThreshold);

        Assert.Equal((100, 98), (x.Count, keptX.Count));
    }

    [Fact(DisplayName = "実データを訓練 47 件・検証 21 件・テスト 30 件に分ける")]
    public void SplitsIntoThree()
    {
        this.RequireData();

        var dataset = Boston.Prepare(this.csvFile, Chapter12Program.TestSize, Chapter12Program.ValidationSize, Chapter12Program.Seed);

        Assert.Equal((47, 21, 30), (dataset.TTrain.Count, dataset.TValid.Count, dataset.TTest.Count));
        Assert.Equal(9, dataset.XTrain.ColumnCount);
    }

    [Fact(DisplayName = "実データの alpha ごとの決定係数は F# 版と一致する")]
    public void ExperimentsMatchFSharp()
    {
        this.RequireData();
        var dataset = Boston.Prepare(this.csvFile, Chapter12Program.TestSize, Chapter12Program.ValidationSize, Chapter12Program.Seed);

        var comparison = Chapter12Program.CompareOnTestData(dataset);

        Assert.Equal(0.8914, comparison.Experiments[0].TrainScore, 4);
        Assert.Equal(-0.4803, comparison.Experiments[0].ValidationScore, 4);
        Assert.Equal(-0.0167, comparison.Experiments[3].ValidationScore, 4);
        Assert.Equal(10.0, comparison.Best.Alpha);
        Assert.Equal(0.9040, comparison.LinearScore, 4);
        Assert.Equal(0.8827, comparison.RidgeScore, 4);
    }

    [Fact(DisplayName = "実データでも ML.NET の SDCA は自作のリッジ回帰とほぼ同じ決定係数になる")]
    public void SdcaAgreesOnRealData()
    {
        this.RequireData();
        var dataset = Boston.Prepare(this.csvFile, Chapter12Program.TestSize, Chapter12Program.ValidationSize, Chapter12Program.Seed);

        var library = MlNetRegularization.FitRidgeWithMlNet(10.0, dataset.XTrain, dataset.TTrain);

        Assert.Equal(
            0.8828, RegressionMetrics.R2Score(dataset.TTest, Regularization.Predict(library, dataset.XTest)), 4);
    }

    [Fact(DisplayName = "実データのラッソ回帰は 9 個のうち 4 個の係数を 0 にする")]
    public void LassoZeroesFourFeatures()
    {
        this.RequireData();
        var dataset = Boston.Prepare(this.csvFile, Chapter12Program.TestSize, Chapter12Program.ValidationSize, Chapter12Program.Seed);

        var lasso = Regularization.FitLasso(Chapter12Program.LassoAlpha, dataset.XTrain, dataset.TTrain);

        Assert.Equal<IReadOnlyList<string>>(
            ["RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"],
            Regularization.ZeroCoefficientNames(lasso.Coefficients, dataset.FeatureNames));
    }

    [Fact(DisplayName = "章の実行は実験・ラッソ回帰・シードごとの比較を表示する")]
    public void RunPrintsSummary()
    {
        this.RequireData();
        var output = new StringWriter();

        Chapter12Program.Run(output);

        Assert.Equal(
            """
            データ件数: 98（外れ値 2 件を除外）
            訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件
            特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2
            alpha	訓練 R²	検証 R²	係数の絶対値の合計
            0	0.8914	-0.4803	12.509
            0.1	0.8914	-0.4445	12.400
            1	0.8908	-0.2447	11.718
            10	0.8819	-0.0167	9.991
            100	0.7769	-0.1583	6.406
            検証データで選んだ alpha: 10
            テストデータの決定係数: 線形回帰 0.9040, リッジ回帰 0.8827
            ML.NET（SDCA）のリッジ回帰のテストデータの決定係数: 0.8828
            ラッソ回帰（alpha=1）で係数が 0 になった特徴量: RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2

            シード	選んだ alpha	線形回帰	リッジ回帰
            0	10	0.9040	0.8827
            1	10	0.5551	0.4040
            2	1	0.7012	0.7040
            3	0	0.7460	0.7460
            4	100	0.2219	0.4841

            """.ReplaceLineEndings(),
            output.ToString());
    }

    private void RequireData() =>
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ Boston.csv が配置されていない（gulp data:setup）");
}
