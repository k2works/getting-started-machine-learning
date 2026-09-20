namespace MachineLearning.Tests.Chapter07;

using MachineLearning.Chapter02;
using MachineLearning.Chapter07;

public class LinearRegressionTests
{
    private static readonly string[] AB = ["a", "b"];
    private static readonly string[] A = ["a"];

    /// <summary>a・b の 2 列を持つ特徴量を作る。</summary>
    internal static IReadOnlyList<Features> Rows(IReadOnlyList<double> a, IReadOnlyList<double> b) =>
        [.. a.Zip(b).Select(pair => new Features(AB, [pair.First, pair.Second]))];

    [Fact(DisplayName = "1 つの特徴量から切片と係数を求める")]
    public void FitOneFeature()
    {
        double[] a = [0.0, 1.0, 2.0];
        IReadOnlyList<Features> x = [.. a.Select(value => new Features(A, [value]))];

        var model = LinearRegression.Fit(x, [5.0, 8.0, 11.0]);

        Assert.Equal(5.0, model.Intercept, 9);
        Assert.Equal(3.0, model.Coefficients.Value("a"), 9);
    }

    [Fact(DisplayName = "複数の特徴量から切片と係数を求める")]
    public void FitTwoFeatures()
    {
        double[] a = [0.0, 1.0, 0.0, 2.0, 1.0];
        double[] b = [0.0, 0.0, 1.0, 1.0, 3.0];
        var t = a.Zip(b).Select(pair => (3.0 * pair.First) - (2.0 * pair.Second) + 5.0).ToList();

        var model = LinearRegression.Fit(Rows(a, b), t);

        Assert.Equal(5.0, model.Intercept, 9);
        MatrixTests.AssertValues([3.0, -2.0], model.Coefficients.Values);
    }

    [Fact(DisplayName = "係数は特徴量の列名の順に並ぶ")]
    public void CoefficientColumns()
    {
        var model = LinearRegression.Fit(Rows([0.0, 1.0, 0.0], [0.0, 0.0, 1.0]), [1.0, 2.0, 3.0]);

        Assert.Equal(AB, model.Coefficients.Columns);
    }

    [Fact(DisplayName = "学習したモデルで予測する")]
    public void Predict()
    {
        var model = new LinearModel(5.0, new Features(AB, [3.0, -2.0]));

        var y = LinearRegression.Predict(model, Rows([1.0, 2.0], [2.0, 0.0]));

        MatrixTests.AssertValues([4.0, 11.0], y);
    }

    [Fact(DisplayName = "予測は係数の列だけを使うので、列の並びが違っても同じ結果になる")]
    public void PredictByColumnName()
    {
        var model = new LinearModel(5.0, new Features(AB, [3.0, -2.0]));
        IReadOnlyList<Features> x = [new(["b", "a"], [2.0, 1.0])];

        MatrixTests.AssertValues([4.0], LinearRegression.Predict(model, x));
    }

    [Fact(DisplayName = "特徴量と正解ラベルの件数が違えば学習できない")]
    public void FitMismatched()
    {
        Assert.Throws<ArgumentException>(() => LinearRegression.Fit(Rows([1.0, 2.0], [3.0, 4.0]), [1.0]));
    }
}
