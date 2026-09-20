namespace MachineLearning.Tests.Chapter12;

using MachineLearning.Chapter02;
using MachineLearning.Chapter12;

public class BostonTests
{
    private static readonly string[] Columns = ["RM", "PTRATIO", "LSTAT"];

    [Fact(DisplayName = "標本標準偏差は件数から 1 を引いて割る")]
    public void SampleStd()
    {
        // 平均は 3、偏差の二乗和は 4 + 1 + 0 + 1 + 4 = 10
        Assert.Equal(Math.Sqrt(10.0 / 4), Boston.MeanAndStd([1.0, 2.0, 3.0, 4.0, 5.0], 1).Std, 12);
    }

    [Fact(DisplayName = "母標準偏差は件数で割る")]
    public void PopulationStd()
    {
        Assert.Equal(Math.Sqrt(10.0 / 5), Boston.MeanAndStd([1.0, 2.0, 3.0, 4.0, 5.0], 0).Std, 12);
    }

    [Fact(DisplayName = "z スコアが閾値を超える行を、正解の列も含めて取り除く")]
    public void RemovesOutliers()
    {
        var x = Rows([1.0, 1.1, 0.9, 1.0, 1.05, 30.0]);
        IReadOnlyList<double> t = [1.0, 1.0, 1.0, 1.0, 1.0, 1.0];

        var (keptX, keptT) = Boston.RemoveOutliers(x, t, 2.0);

        Assert.Equal((5, 5), (keptX.Count, keptT.Count));
    }

    [Fact(DisplayName = "正解の列だけが外れていても行を取り除く")]
    public void RemovesTargetOutliers()
    {
        var x = Rows([1.0, 1.1, 0.9, 1.0, 1.05, 1.0]);
        IReadOnlyList<double> t = [1.0, 1.0, 1.0, 1.0, 1.0, 50.0];

        var (keptX, _) = Boston.RemoveOutliers(x, t, 2.0);

        Assert.Equal(5, keptX.Count);
    }

    [Fact(DisplayName = "3 列から 9 個の特徴量（元の列と 2 次の項）を作る")]
    public void BuildsPolynomialNames()
    {
        var scaler = PolynomialScaler.Fit(Columns, Rows([1.0, 2.0, 3.0]));

        Assert.Equal<IReadOnlyList<string>>(
            [
                "RM", "PTRATIO", "LSTAT",
                "RM^2", "RM PTRATIO", "RM LSTAT",
                "PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2",
            ],
            scaler.FeatureNames);
    }

    [Fact(DisplayName = "標準化した列は平均 0・標準偏差 1 になり、2 次の項はその積になる")]
    public void StandardizesAndSquares()
    {
        var x = Rows([1.0, 2.0, 3.0, 4.0, 5.0]);
        var scaler = PolynomialScaler.Fit(Columns, x);

        var transformed = scaler.Transform(x);

        // 3 列とも同じ値なので、標準化した値は 5 件で -√2、-1/√2、0、1/√2、√2
        Assert.Equal(-Math.Sqrt(2.0), transformed[0, 0], 12);
        Assert.Equal(0.0, transformed[2, 0], 12);
        // 2 次の項（RM^2）は標準化した値の 2 乗
        Assert.Equal(2.0, transformed[0, 3], 12);
    }

    [Fact(DisplayName = "標準化は訓練データの平均と標準偏差で、別のデータにも同じように適用する")]
    public void TransformsWithTrainingStats()
    {
        var scaler = PolynomialScaler.Fit(Columns, Rows([1.0, 2.0, 3.0, 4.0, 5.0]));

        var transformed = scaler.Transform(Rows([3.0]));

        Assert.Equal(0.0, transformed[0, 0], 12);
    }

    private static IReadOnlyList<Features> Rows(IReadOnlyList<double> values) =>
        [.. values.Select(value => new Features(Columns, [value, value, value]))];
}
