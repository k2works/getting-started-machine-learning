namespace MachineLearning.Tests.Chapter11;

using MachineLearning.Chapter02;
using MachineLearning.Chapter11;

public class ModelsTests
{
    private static readonly string[] Column = ["a"];

    private static readonly double[] TreeValues = [1.0, 2.0, 8.0, 9.0];

    private static readonly double[] LineValues = [1.0, 2.0, 3.0];

    private static readonly double[] FiveValues = [1.0, 2.0, 3.0, 4.0, 5.0];

    [Fact(DisplayName = "決定木のモデルは学習してから予測する関数を返す")]
    public void DecisionTreePredicts()
    {
        IReadOnlyList<Features> x =
            [.. TreeValues.Select(value => new Features(Column, [value]))];
        IReadOnlyList<string> t = ["低", "低", "高", "高"];

        var predict = Models.DecisionTree(1)(x, t);

        Assert.Equal<IReadOnlyList<string>>(["低", "高"], predict([
            new Features(Column, [1.5]), new Features(Column, [8.5])]));
    }

    [Fact(DisplayName = "線形回帰のモデルは直線に乗る値をそのまま予測する")]
    public void LinearRegressionPredicts()
    {
        IReadOnlyList<Features> x =
            [.. LineValues.Select(value => new Features(Column, [value]))];
        IReadOnlyList<double> t = [3.0, 5.0, 7.0];

        var predict = Models.LinearRegression(x, t);

        Assert.Equal(9.0, predict([new Features(Column, [4.0])])[0], 9);
    }

    [Fact(DisplayName = "常に同じラベルを予測するモデルは正解率が高くても再現率は 0 になる")]
    public void ConstantModelHasNoRecall()
    {
        IReadOnlyList<Features> x =
            [.. FiveValues.Select(value => new Features(Column, [value]))];
        IReadOnlyList<string> t = ["0", "0", "0", "0", "1"];

        var predicted = Models.Constant("0")(x, t)(x);

        Assert.Equal(0.8, MachineLearning.Chapter01.KinokoTakenoko.Accuracy(predicted, t), 12);
        Assert.Equal(0.0, Metrics.Recall(Metrics.Confusion("1", t, predicted)), 12);
    }
}
