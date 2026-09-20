namespace MachineLearning.Tests.Chapter13;

using MachineLearning.Chapter13;
using MachineLearning.Dataset;

public class PcaDataTests
{
    private readonly string bostonFile = Path.Combine(DataDir.Current(), "Boston.csv");

    [Fact(DisplayName = "Boston.csv を標準化すると 100 件・15 列になる")]
    public void StandardizesBoston()
    {
        this.SkipUnlessBoston();

        var table = BostonStandardized.Load(this.bostonFile);

        Assert.Equal(100, table.X.RowCount);
        Assert.Equal(
            [
                "ZN", "INDUS", "CHAS", "NOX", "RM", "AGE", "DIS", "RAD", "TAX", "PTRATIO", "B", "LSTAT",
                "PRICE", "CRIME_low", "CRIME_very_low",
            ],
            table.Columns);
    }

    [Fact(DisplayName = "標準化した列の分散の合計は列数と等しく、寄与率の合計は 1 になる")]
    public void RatiosSumToOne()
    {
        this.SkipUnlessBoston();

        var table = BostonStandardized.Load(this.bostonFile);
        var model = Pca.Fit(table.Columns.Count, table.X);

        Assert.Equal(1.0, model.ExplainedVarianceRatio.Sum(), 10);
        // 標準偏差を件数 n で割って求めているので、不偏分散は n / (n - 1) になる
        Assert.Equal(table.Columns.Count * 100.0 / 99.0, model.ExplainedVariance.Sum(), 8);
    }

    [Fact(DisplayName = "実行すると寄与率と主成分への影響が大きい列を表示する")]
    public void PrintsSummary()
    {
        this.SkipUnlessBoston();

        using var output = new StringWriter();
        MachineLearning.Chapter13.Program.Run(output);

        Assert.Equal(
            string.Join(
                Environment.NewLine,
                "データ件数: 100, 列数: 15",
                "寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581",
                "累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）",
                "第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328",
                "第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405") + Environment.NewLine,
            output.ToString());
    }

    private void SkipUnlessBoston() =>
        Assert.SkipUnless(File.Exists(this.bostonFile), "学習データ Boston.csv が配置されていない（gulp data:setup）");
}
