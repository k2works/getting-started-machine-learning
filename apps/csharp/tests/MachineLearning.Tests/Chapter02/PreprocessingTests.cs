namespace MachineLearning.Tests.Chapter02;

using MachineLearning.Chapter02;

public class FeaturesTests
{
    [Fact(DisplayName = "値の配列を写して持ち、渡した配列を後から変えても影響を受けない")]
    public void CopiesValues()
    {
        double[] values = [0.1, 0.2];
        var features = new Features(["a", "b"], values);

        values[0] = 9.9;

        Assert.Equal(0.1, features.Value("a"));
    }

    [Fact(DisplayName = "列名と値が同じなら等しい")]
    public void EqualByValue()
    {
        var one = new Features(["a"], [0.1]);
        var other = new Features(["a"], [0.1]);

        Assert.Equal(one, other);
        Assert.Equal(one.GetHashCode(), other.GetHashCode());
    }

    [Fact(DisplayName = "列名と値の数が違えばエラーになる")]
    public void SizeMismatch() =>
        Assert.Throws<ArgumentException>(() => new Features(["a", "b"], [0.1]));
}

public class ColumnMeansTests
{
    [Fact(DisplayName = "欠損値を除いて列ごとの平均値を求める")]
    public void IgnoresMissing()
    {
        List<Row> rows = [Sample("0.1", "0.2"), Sample(string.Empty, "0.4"), Sample("0.3", "0.9")];

        var means = Preprocessing.ColumnMeans(rows, ["がく片長さ", "がく片幅"]);

        Assert.Equal(0.2, means["がく片長さ"], 12);
        Assert.Equal(0.5, means["がく片幅"], 12);
    }

    internal static Row Sample(string sepalLength, string sepalWidth) =>
        new(new Dictionary<string, string> { ["がく片長さ"] = sepalLength, ["がく片幅"] = sepalWidth });
}

public class FillMissingTests
{
    [Fact(DisplayName = "欠損値を列ごとに指定した値で補完して特徴量にする")]
    public void FillsWithGivenValues()
    {
        List<Row> rows = [ColumnMeansTests.Sample("0.1", string.Empty), ColumnMeansTests.Sample(string.Empty, "0.4")];
        List<string> columns = ["がく片長さ", "がく片幅"];

        var filled = Preprocessing.FillMissing(rows, columns, new Dictionary<string, double> { ["がく片長さ"] = 0.2, ["がく片幅"] = 0.5 });

        Assert.Equal([new Features(columns, [0.1, 0.5]), new Features(columns, [0.2, 0.4])], filled);
    }

    [Fact(DisplayName = "元の行は変更しない")]
    public void KeepsOriginalRows()
    {
        List<Row> rows = [ColumnMeansTests.Sample(string.Empty, "0.2")];

        Preprocessing.FillMissing(rows, ["がく片長さ", "がく片幅"], new Dictionary<string, double> { ["がく片長さ"] = 0.2 });

        Assert.True(rows[0].IsMissing("がく片長さ"));
    }
}

public class SplitTrainTestTests
{
    private static readonly List<int> X = [.. Enumerable.Range(0, 10)];
    private static readonly List<string> T = [.. Enumerable.Range(0, 10).Select(i => $"label{i}")];

    [Fact(DisplayName = "テストデータの割合どおりの件数に分ける")]
    public void SplitsByRatio()
    {
        var split = Preprocessing.SplitTrainTest(X, T, 0.3, 0);

        Assert.Equal(7, split.XTrain.Count);
        Assert.Equal(3, split.XTest.Count);
        Assert.Equal(7, split.TTrain.Count);
        Assert.Equal(3, split.TTest.Count);
    }

    [Fact(DisplayName = "件数が変わってもテストデータの割合どおりに分ける")]
    public void SplitsOtherSizes()
    {
        var twenty = Enumerable.Range(0, 20).ToList();

        var split = Preprocessing.SplitTrainTest(twenty, twenty, 0.25, 0);

        Assert.Equal(15, split.XTrain.Count);
        Assert.Equal(5, split.XTest.Count);
    }

    [Fact(DisplayName = "すべての行を重複なく訓練データとテストデータのどちらかに入れる")]
    public void CoversAllRowsOnce()
    {
        var split = Preprocessing.SplitTrainTest(X, T, 0.3, 0);

        Assert.Empty(split.XTrain.Intersect(split.XTest));
        Assert.Equal(X, [.. split.XTrain.Concat(split.XTest).Order()]);
    }

    [Fact(DisplayName = "特徴量と正解ラベルの対応を保ったまま分ける")]
    public void KeepsPairs()
    {
        var split = Preprocessing.SplitTrainTest(X, T, 0.3, 0);

        Assert.Equal(split.XTrain.Select(i => $"label{i}"), split.TTrain);
        Assert.Equal(split.XTest.Select(i => $"label{i}"), split.TTest);
    }

    [Fact(DisplayName = "同じシードなら同じ分け方になる")]
    public void SameSeedSameSplit() =>
        Assert.Equal(
            Preprocessing.SplitTrainTest(X, T, 0.3, 42).TTest,
            Preprocessing.SplitTrainTest(X, T, 0.3, 42).TTest);

    [Fact(DisplayName = "シードが違えば違う分け方になる")]
    public void DifferentSeedDifferentSplit() =>
        Assert.NotEqual(
            Preprocessing.SplitTrainTest(X, T, 0.3, 0).TTest,
            Preprocessing.SplitTrainTest(X, T, 0.3, 1).TTest);

    [Fact(DisplayName = "数値の正解ラベルも特徴量との対応を保ったまま分ける")]
    public void NumericLabels()
    {
        var numeric = X.Select(i => i * 0.5).ToList();

        var split = Preprocessing.SplitTrainTest(X, numeric, 0.3, 0);

        Assert.Equal(split.XTest.Select(i => i * 0.5), split.TTest);
    }

    [Fact(DisplayName = "F# 版と同じ Fisher-Yates の並べ替えになる")]
    public void SameAsFSharp()
    {
        // F# 版（apps/fsharp の Chapter02.Random.shuffle）と同じ手順・同じシードなら同じ並びになる
        var shuffled = Preprocessing.Shuffle(X, 0);

        Assert.Equal(X.Count, shuffled.Count);
        Assert.Equal(X, [.. shuffled.Order()]);
    }
}
