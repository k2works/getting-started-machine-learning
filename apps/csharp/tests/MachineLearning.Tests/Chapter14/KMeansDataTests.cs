namespace MachineLearning.Tests.Chapter14;

using MachineLearning.Chapter14;
using MachineLearning.Dataset;

public class KMeansDataTests
{
    private readonly string wholesaleFile = Path.Combine(DataDir.Current(), "Wholesale.csv");

    [Fact(DisplayName = "Wholesale.csv を読み込むと 440 件・支出額の 6 列になる")]
    public void LoadsWholesale()
    {
        this.SkipUnlessWholesale();

        var rows = Spending.Load(this.wholesaleFile);

        Assert.Equal(440, rows.Count);
        Assert.Equal(
            ["Fresh", "Milk", "Grocery", "Frozen", "Detergents_Paper", "Delicassen"], rows[0].Columns);
    }

    [Fact(DisplayName = "クラスタ数 1 の SSE は、件数 × 列数になる")]
    public void SseOfSingleClusterIsRowsTimesColumns()
    {
        this.SkipUnlessWholesale();

        var points = Spending.ToStandardizedPoints(Spending.Load(this.wholesaleFile));

        // 標準化した列は平均 0・分散 1（件数で割る）なので、全体の平均を中心にした SSE は 440 × 6 になる
        Assert.Equal(440.0 * 6.0, KMeans.FitWithRestarts(1, 1, 0, points).Sse, 6);
    }

    [Fact(DisplayName = "実行すると SSE の表とクラスタごとの平均支出額を表示する")]
    public void PrintsSummary()
    {
        this.SkipUnlessWholesale();

        using var output = new StringWriter();
        MachineLearning.Chapter14.Program.Run(output);

        Assert.Equal(
            string.Join(
                Environment.NewLine,
                "データ件数: 440（支出額 6 列）",
                "クラスタ数ごとの SSE（初期中心 10 通りの最小値）:",
                "クラスタ数\t自作\tML.NET（k-means++）",
                "1\t2640.00\t2640.00",
                "2\t1954.18\t1954.80",
                "3\t1610.17\t1627.76",
                "4\t1345.47\t1312.60",
                "5\t1085.27\t1060.26",
                "6\t947.20\t924.69",
                "7\t865.72\t822.82",
                "8\t752.61\t746.01",
                "9\t666.15\t666.67",
                "10\t605.62\t605.19",
                string.Empty,
                "クラスタ数 5 のクラスタごとの件数と平均支出額:",
                "クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen",
                "4\t265\t8909\t2967\t3804\t2248\t989\t962",
                "0\t96\t5509\t10556\t16478\t1420\t7199\t1659",
                "1\t65\t31117\t4260\t5374\t7225\t849\t2286",
                "2\t10\t15965\t34708\t48537\t3055\t24875\t2943",
                "3\t4\t52022\t31696\t18491\t29826\t2699\t19656") + Environment.NewLine,
            output.ToString());
    }

    private void SkipUnlessWholesale() =>
        Assert.SkipUnless(
            File.Exists(this.wholesaleFile), "学習データ Wholesale.csv が配置されていない（gulp data:setup）");
}
