namespace MachineLearning.Tests.Chapter11;

using MachineLearning.Chapter02;
using MachineLearning.Chapter07;
using MachineLearning.Chapter11;

public class CrossValidationTests
{
    private static readonly IReadOnlyList<Features> X =
        [.. new[] { 1.0, 2.0, 3.0, 4.0 }.Select(value => new Features(["a"], [value]))];

    private static readonly IReadOnlyList<double> T = [1.0, 3.0, 5.0, 7.0];

    private static readonly IReadOnlyList<Fold> Folds =
        [new Fold([0, 1], [2, 3]), new Fold([2, 3], [0, 1])];

    [Fact(DisplayName = "データを k 個のテストデータにほぼ均等に分ける")]
    public void SplitsEvenly()
    {
        Assert.Equal<IReadOnlyList<int>>([4, 3, 3], TestSizes(CrossValidation.KFold(3, 0, 10)));
    }

    [Fact(DisplayName = "件数と分割数が変わってもほぼ均等に分ける")]
    public void SplitsEvenlyAgain()
    {
        Assert.Equal<IReadOnlyList<int>>([4, 3], TestSizes(CrossValidation.KFold(2, 0, 7)));
    }

    [Fact(DisplayName = "どの行もちょうど一度だけテストデータになる")]
    public void EachRowTestedOnce()
    {
        var folds = CrossValidation.KFold(3, 0, 10);

        Assert.Equal<IReadOnlyList<int>>(
            [.. Enumerable.Range(0, 10)], [.. folds.SelectMany(fold => fold.Test).Order()]);
    }

    [Fact(DisplayName = "各分割の訓練データはテストデータ以外のすべての行")]
    public void TrainIsComplement()
    {
        foreach (var fold in CrossValidation.KFold(3, 0, 10))
        {
            Assert.Empty(fold.Train.Intersect(fold.Test));
            Assert.Equal<IReadOnlyList<int>>(
                [.. Enumerable.Range(0, 10)], [.. fold.Train.Concat(fold.Test).Order()]);
        }
    }

    [Fact(DisplayName = "同じシードなら同じ分け方、違うシードなら違う分け方になる")]
    public void SeedDecidesSplit()
    {
        Assert.Equal<IReadOnlyList<int>>(CrossValidation.KFold(3, 0, 10)[0].Test, CrossValidation.KFold(3, 0, 10)[0].Test);
        Assert.NotEqual<IReadOnlyList<int>>(
            CrossValidation.KFold(3, 0, 10)[0].Test, CrossValidation.KFold(3, 1, 10)[0].Test);
    }

    [Fact(DisplayName = "分割数がデータの件数を超えればエラーになる")]
    public void TooManySplits()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => CrossValidation.KFold(11, 0, 10));
    }

    [Fact(DisplayName = "分割ごとに訓練データで学習してテストデータを評価する")]
    public void EvaluatesEachFold()
    {
        // 訓練データ [1, 3] の平均 2 をテストデータ [5, 7] に当てると誤差は 3 と 5 で MAE は 4
        Assert.Equal<IReadOnlyList<double>>(
            [4.0, 4.0],
            [.. CrossValidation.CrossValidate(
                Models.Mean, RegressionMetrics.MeanAbsoluteError, Folds, X, T)]);
    }

    [Fact(DisplayName = "評価関数を差し替えると別の指標で評価する")]
    public void SwapsMetric()
    {
        Assert.Equal<IReadOnlyList<double>>(
            [17.0, 17.0],
            [.. CrossValidation.CrossValidate(Models.Mean, Metrics.MeanSquaredError, Folds, X, T)]);
    }

    [Fact(DisplayName = "最初の分割のスコアだけを取り出すなら学習は 1 回で済む")]
    public void LazyTraining()
    {
        var trained = 0;
        Model<double> counting = (x, t) =>
        {
            trained++;
            return Models.Mean(x, t);
        };

        _ = CrossValidation
            .CrossValidate(counting, RegressionMetrics.MeanAbsoluteError, Folds, X, T)
            .First();

        Assert.Equal(1, trained);
    }

    [Fact(DisplayName = "同じ反復子を 2 回たどると学習も 2 回ずつ行われる")]
    public void RepeatedEnumerationRetrains()
    {
        var trained = 0;
        Model<double> counting = (x, t) =>
        {
            trained++;
            return Models.Mean(x, t);
        };
        var scores = CrossValidation.CrossValidate(
            counting, RegressionMetrics.MeanAbsoluteError, Folds, X, T);

        _ = scores.ToList();
        _ = scores.ToList();

        Assert.Equal(4, trained);
    }

    private static IReadOnlyList<int> TestSizes(IReadOnlyList<Fold> folds) =>
        [.. folds.Select(fold => fold.Test.Count)];
}
