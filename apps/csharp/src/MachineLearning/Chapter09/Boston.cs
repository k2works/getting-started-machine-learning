namespace MachineLearning.Chapter09;

using System.Globalization;
using MachineLearning.Chapter02;

/// <summary>訓練データとテストデータの決定係数。</summary>
/// <param name="Train">訓練データの決定係数</param>
/// <param name="Test">テストデータの決定係数</param>
public readonly record struct Scores(double Train, double Test);

/// <summary>ボストンの住宅価格（Boston.csv）の前処理と、特徴量の組ごとの決定係数。</summary>
public static class Boston
{
    /// <summary>正解の列。</summary>
    public const string Target = "PRICE";

    /// <summary>カテゴリ値の列。</summary>
    public const string Category = "CRIME";

    /// <summary>CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。</summary>
    public static TrainTestSplit<Features, double> Prepare(string csvFile, double testSize, int seed)
    {
        var table = Table.Load(csvFile);
        var categories = Dummies.Categories(table.Rows.Select(row => row.Text(Category)));
        var encoded = Dummies.Encode(table, Category, categories);
        var (columns, rows, target) = Preprocessing.SplitFeaturesAndTarget(encoded, Target);
        var prices = target.Select(value => double.Parse(value, CultureInfo.InvariantCulture)).ToList();
        var split = Preprocessing.SplitTrainTest(rows, prices, testSize, seed);
        var means = Preprocessing.ColumnMeans(split.XTrain, columns);
        return new TrainTestSplit<Features, double>(
            Preprocessing.FillMissing(split.XTrain, columns, means),
            Preprocessing.FillMissing(split.XTest, columns, means),
            split.TTrain,
            split.TTest);
    }

    /// <summary>
    /// columns から 2 次の項を作って terms の列だけを選び、訓練データの平均と標準偏差で標準化してから
    /// 線形回帰で学習し、訓練データとテストデータの決定係数を返す。
    /// </summary>
    public static Scores ScoreFeatureSet(
        TrainTestSplit<Features, double> split, IReadOnlyList<string> columns, IReadOnlyList<string> terms)
    {
        ArgumentNullException.ThrowIfNull(split);
        var train = PolynomialFeatures.Select(PolynomialFeatures.Expand(split.XTrain, columns), terms);
        var test = PolynomialFeatures.Select(PolynomialFeatures.Expand(split.XTest, columns), terms);
        var standardizer = Standardizer.Fit(train);
        var xTrain = standardizer.Transform(train);
        var xTest = standardizer.Transform(test);
        var model = LinearModel.Fit(xTrain, split.TTrain);
        return new Scores(
            LinearModel.RSquared(split.TTrain, model.Predict(xTrain)),
            LinearModel.RSquared(split.TTest, model.Predict(xTest)));
    }
}
