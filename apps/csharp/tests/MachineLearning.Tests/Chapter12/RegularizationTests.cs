namespace MachineLearning.Tests.Chapter12;

using MachineLearning.Chapter02;
using MachineLearning.Chapter07;
using MachineLearning.Chapter12;

public class RegularizationTests
{
    /// <summary>架空のデータ。3 列目は 1 列目と 2 列目に関係しない、役に立たない特徴量。</summary>
    private static readonly Matrix X = Matrix.FromRows(
    [
        [1.0, 2.0, 0.2],
        [2.0, 1.0, -0.3],
        [3.0, 4.0, 0.7],
        [4.0, 3.0, -0.1],
        [5.0, 6.0, 0.4],
        [6.0, 5.0, -0.6],
    ]);

    /// <summary>正解は 3 + 2 × 1 列目 + 1 × 2 列目。3 列目は正解に関係しない。</summary>
    private static readonly double[] T = [7.0, 8.0, 13.0, 14.0, 19.0, 20.0];

    [Fact(DisplayName = "alpha が 0 のリッジ回帰は第 7 章の最小二乗法と同じ係数になる")]
    public void ZeroAlphaEqualsLeastSquares()
    {
        var features = Rows(X);

        var ridge = Regularization.FitRidge(0.0, X, T);
        var leastSquares = LinearRegression.Fit(features, T);

        Assert.Equal(leastSquares.Intercept, ridge.Intercept, 8);
        for (var j = 0; j < X.ColumnCount; j++)
        {
            Assert.Equal(leastSquares.Coefficients.Values[j], ridge.Coefficients[j], 8);
        }
    }

    [Fact(DisplayName = "alpha を大きくすると係数の絶対値の合計が小さくなる")]
    public void LargerAlphaShrinksCoefficients()
    {
        var weak = Regularization.FitRidge(0.1, X, T).Coefficients.Sum(Math.Abs);
        var strong = Regularization.FitRidge(100.0, X, T).Coefficients.Sum(Math.Abs);

        Assert.True(strong < weak, $"alpha=100 の {strong} は alpha=0.1 の {weak} より小さいはず");
    }

    [Fact(DisplayName = "リッジ回帰は学習に使った行をおおむね当てる")]
    public void PredictsTrainingRows()
    {
        var model = Regularization.FitRidge(0.0, X, T);

        Assert.Equal(1.0, RegressionMetrics.R2Score(T, Regularization.Predict(model, X)), 6);
    }

    [Fact(DisplayName = "ラッソ回帰は役に立たない特徴量の係数をちょうど 0 にする")]
    public void LassoZeroesUselessFeature()
    {
        var model = Regularization.FitLasso(1.0, X, T);

        Assert.Equal(0.0, model.Coefficients[2]);
        Assert.NotEqual(0.0, model.Coefficients[0]);
    }

    [Fact(DisplayName = "alpha が 0 のラッソ回帰は罰則が無いのでリッジ回帰と同じ係数になる")]
    public void ZeroAlphaLassoEqualsRidge()
    {
        var lasso = Regularization.FitLasso(0.0, X, T);
        var ridge = Regularization.FitRidge(0.0, X, T);

        for (var j = 0; j < X.ColumnCount; j++)
        {
            Assert.Equal(ridge.Coefficients[j], lasso.Coefficients[j], 6);
        }
    }

    [Fact(DisplayName = "実験は alpha ごとに訓練と検証の決定係数と係数の絶対値の合計を記録する")]
    public void RecordsExperiments()
    {
        var experiments = Regularization.RunRidgeExperiments([0.0, 10.0], X, T, X, T);

        Assert.Equal<IReadOnlyList<double>>([0.0, 10.0], [.. experiments.Select(e => e.Alpha)]);
        Assert.Equal(1.0, experiments[0].TrainScore, 6);
        Assert.Equal(experiments[0].TrainScore, experiments[0].ValidationScore, 12);
        Assert.True(experiments[1].CoefficientAbsSum < experiments[0].CoefficientAbsSum);
    }

    [Fact(DisplayName = "実験の記録は with で一部を変えても元の記録が変わらない")]
    public void ExperimentIsImmutable()
    {
        var original = new Experiment(1.0, 0.9, 0.8, 5.0);

        var changed = original with { Alpha = 2.0 };

        Assert.Equal((1.0, 2.0), (original.Alpha, changed.Alpha));
        Assert.Equal(original.ValidationScore, changed.ValidationScore);
    }

    [Fact(DisplayName = "検証データの決定係数が最も高い実験を選ぶ")]
    public void ChoosesBest()
    {
        IReadOnlyList<Experiment> experiments =
            [new Experiment(0.0, 0.9, 0.1, 5.0), new Experiment(1.0, 0.8, 0.5, 4.0), new Experiment(10.0, 0.7, 0.3, 3.0)];

        Assert.Equal(1.0, Regularization.BestExperiment(experiments).Alpha);
    }

    [Fact(DisplayName = "実験が 1 つも無ければ選べない")]
    public void CannotChooseFromEmpty()
    {
        Assert.Throws<ArgumentException>(() => Regularization.BestExperiment([]));
    }

    [Fact(DisplayName = "係数がちょうど 0 の特徴量の名前を集める")]
    public void ListsZeroCoefficients()
    {
        Assert.Equal<IReadOnlyList<string>>(
            ["b", "d"], Regularization.ZeroCoefficientNames([1.0, 0.0, -2.0, 0.0], ["a", "b", "c", "d"]));
    }

    private static IReadOnlyList<Features> Rows(Matrix x)
    {
        string[] columns = ["a", "b", "c"];
        return [.. Enumerable.Range(0, x.RowCount).Select(i => new Features(columns, x.Row(i)))];
    }
}
