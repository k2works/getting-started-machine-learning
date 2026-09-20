namespace MachineLearning.Tests.Chapter11;

using MachineLearning.Chapter02;
using MachineLearning.Chapter11;
using MachineLearning.Dataset;

public class EvaluationDataTests
{
    private readonly string survivedCsv = Path.Combine(DataDir.Current(), "Survived.csv");
    private readonly string cinemaCsv = Path.Combine(DataDir.Current(), "cinema.csv");

    [Fact(DisplayName = "Survived の前処理は 891 件の 3 列の特徴量にする")]
    public void PreparesSurvived()
    {
        RequireData(this.survivedCsv);

        var (x, t) = Datasets.PrepareSurvived(this.survivedCsv);

        Assert.Equal((891, 891), (x.Count, t.Count));
        Assert.Equal<IReadOnlyList<string>>(["Age", "Pclass", "male"], x[0].Columns);
    }

    [Fact(DisplayName = "Survived の交差検証の平均は F# 版と一致する")]
    public void SurvivedScoresMatchFSharp()
    {
        RequireData(this.survivedCsv);

        var scores = Experiments.EvaluateSurvived(this.survivedCsv).ToDictionary(StringComparer.Ordinal);

        Assert.Equal(0.7800, scores["正解率"], 4);
        Assert.Equal(0.8114, scores["適合率"], 4);
        Assert.Equal(0.5736, scores["再現率"], 4);
        Assert.Equal(0.6587, scores["F値"], 4);
    }

    [Fact(DisplayName = "cinema の交差検証の平均は F# 版と一致する")]
    public void CinemaScoresMatchFSharp()
    {
        RequireData(this.cinemaCsv);

        var scores = Experiments.EvaluateCinema(this.cinemaCsv).ToDictionary(StringComparer.Ordinal);

        Assert.Equal(392.75, scores["RMSE"], 2);
        Assert.Equal(314.71, scores["MAE"], 2);
    }

    [Fact(DisplayName = "分割ごとの再現率は正解率よりも大きく揺れる")]
    public void RecallVariesMoreThanAccuracy()
    {
        RequireData(this.survivedCsv);
        var (x, t) = Datasets.PrepareSurvived(this.survivedCsv);
        var folds = CrossValidation.KFold(Experiments.NSplits, Experiments.Seed, x.Count);
        var model = Models.DecisionTree(Experiments.TreeDepth);

        var accuracy = Spread(model, (actual, predicted) =>
            MachineLearning.Chapter01.KinokoTakenoko.Accuracy(predicted, actual), folds, x, t);
        var recall = Spread(
            model, Metrics.ClassificationMetric(Metrics.Recall, Experiments.Survived), folds, x, t);

        Assert.Equal((0.753, 0.798), (Round(accuracy.Min), Round(accuracy.Max)));
        Assert.Equal((0.415, 0.746), (Round(recall.Min), Round(recall.Max)));
    }

    [Fact(DisplayName = "章の実行は 2 つのデータの交差検証の平均を表示する")]
    public void RunPrintsSummary()
    {
        RequireData(this.survivedCsv);
        RequireData(this.cinemaCsv);
        var output = new StringWriter();

        MachineLearning.Chapter11.Program.Run(output);

        Assert.Equal(
            """
            Survived（決定木、5 分割交差検証の平均）
              正解率: 0.7800
              適合率: 0.8114
              再現率: 0.5736
              F値: 0.6587
            cinema（線形回帰、5 分割交差検証の平均）
              RMSE: 392.75
              MAE: 314.71

            """.ReplaceLineEndings(),
            output.ToString());
    }

    private static (double Min, double Max) Spread(
        Model<string> model,
        Metric<string> metric,
        IReadOnlyList<Fold> folds,
        IReadOnlyList<Features> x,
        IReadOnlyList<string> t)
    {
        var scores = CrossValidation.CrossValidate(model, metric, folds, x, t).ToList();
        return (scores.Min(), scores.Max());
    }

    private static double Round(double value) => Math.Round(value, 3);

    private static void RequireData(string csvFile) =>
        Assert.SkipUnless(File.Exists(csvFile), $"学習データ {Path.GetFileName(csvFile)} が配置されていない（gulp data:setup）");
}
