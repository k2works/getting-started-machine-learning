namespace MachineLearning.Tests.Chapter02;

using MachineLearning.Chapter02;
using MachineLearning.Dataset;

public class IrisDataTests
{
    private readonly string csvFile = Path.Combine(DataDir.Current(), "iris.csv");

    [Fact(DisplayName = "実データの列ごとの欠損値の数を数える")]
    public void CountsMissing()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        Assert.Equal(
            [("がく片長さ", 2), ("がく片幅", 1), ("花弁長さ", 2), ("花弁幅", 2), ("種類", 0)],
            Table.Load(this.csvFile).CountMissing().Select(pair => (pair.Key, pair.Value)));
    }

    [Fact(DisplayName = "実データを 105 件と 45 件に分けて欠損値を補完する")]
    public void SplitsAndFills()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        var split = Preprocessing.PrepareIris(this.csvFile, 0.3, 0);

        Assert.Equal(105, split.XTrain.Count);
        Assert.Equal(45, split.XTest.Count);
        Assert.Equal(105, split.TTrain.Count);
        Assert.Equal(45, split.TTest.Count);
    }

    [Fact(DisplayName = "訓練データの平均値は F# 版と同じになる（同じ乱数と同じ分け方）")]
    public void SameSplitAsFSharp()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        var (columns, rows, target) = Preprocessing.SplitFeaturesAndTarget(Table.Load(this.csvFile), Preprocessing.Target);
        var split = Preprocessing.SplitTrainTest(rows, target, 0.3, 0);

        var means = Preprocessing.ColumnMeans(split.XTrain, columns);

        // F# 版（apps/fsharp、System.Random と同じ Fisher-Yates）で実測した値
        Assert.Equal(0.424808, means["がく片長さ"], 6);
        Assert.Equal(0.462286, means["がく片幅"], 6);
        Assert.Equal(0.479135, means["花弁長さ"], 6);
        Assert.Equal(0.432404, means["花弁幅"], 6);
        Assert.Equal(
            ["Iris-versicolor", "Iris-setosa", "Iris-setosa", "Iris-versicolor", "Iris-versicolor"],
            split.TTest.Take(5));
    }

    [Fact(DisplayName = "実行すると前処理の結果を表示する")]
    public void PrintsSummary()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        using var output = new StringWriter();
        Program.Run(output);

        Assert.Equal(
            string.Join(
                Environment.NewLine,
                "データ件数: 150",
                "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0",
                "訓練データ: 105 件, テストデータ: 45 件",
                "特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅") + Environment.NewLine,
            output.ToString());
    }
}
