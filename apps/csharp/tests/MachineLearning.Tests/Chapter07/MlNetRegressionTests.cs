namespace MachineLearning.Tests.Chapter07;

using MachineLearning.Chapter02;
using MachineLearning.Chapter07;

public class MlNetRegressionTests
{
    /// <summary>t = 4 + 1.5a - 0.5b + 2c にノイズを加えた 30 件。</summary>
    internal static (IReadOnlyList<Features> X, IReadOnlyList<double> T) NoisyDataset()
    {
        string[] columns = ["a", "b", "c"];
        var random = new Random(0);
        var x = new List<Features>();
        var t = new List<double>();
        for (var i = 0; i < 30; i++)
        {
            double a = random.Next(0, 100);
            double b = random.Next(0, 100);
            double c = random.Next(0, 100);
            x.Add(new Features(columns, [a, b, c]));
            t.Add(4.0 + (1.5 * a) - (0.5 * b) + (2.0 * c) + ((random.NextDouble() - 0.5) * 2.0));
        }

        return (x, t);
    }

    [Fact(DisplayName = "学習用テスト: SDCA はスレッドを 1 本にすると同じ予測を返す")]
    public void SdcaIsReproducible()
    {
        var (x, t) = NoisyDataset();

        var first = MlNetRegression.TrainSdca(x, t)(x);
        var second = MlNetRegression.TrainSdca(x, t)(x);

        MatrixTests.AssertValues(first, second);
    }

    [Fact(DisplayName = "SDCA の予測は自作の線形回帰とおおむね一致する")]
    public void AgreesWithNormalEquation()
    {
        var (x, t) = NoisyDataset();

        var mine = LinearRegression.Predict(LinearRegression.Fit(x, t), x);
        var library = MlNetRegression.TrainSdca(x, t)(x);

        // SDCA は反復で近づける解法なので、正規方程式の厳密解とは小数点以下で食い違う
        Assert.True(
            RegressionMetrics.R2Score(mine, library) > 0.999,
            $"自作と SDCA の予測の R2 が低すぎる: {RegressionMetrics.R2Score(mine, library)}");
    }
}
