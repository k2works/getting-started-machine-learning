namespace MachineLearning.Chapter08;

using MachineLearning.Chapter02;

/// <summary>前処理を順に Fit・Transform してから、モデルを学習する。</summary>
public sealed record Pipeline(IReadOnlyList<ITransformer> Transformers, DecisionTreeClassifier Model)
{
    /// <summary>Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。</summary>
    public static Pipeline Build(int maxDepth, ClassWeight classWeight) =>
        new(
            [
                new GroupMedianImputer("Age", ["Pclass", "Sex"]),
                new MostFrequentImputer("Embarked"),
                new DummyEncoder(["Sex", "Embarked"]),
            ],
            new DecisionTreeClassifier(maxDepth, classWeight));

    /// <summary>訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで Fit する。</summary>
    public FittedPipeline Fit(Table x, IReadOnlyList<int> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        var fitted = new List<IFittedTransformer>();
        var prepared = x;
        foreach (var transformer in this.Transformers)
        {
            var fittedTransformer = transformer.Fit(prepared);
            fitted.Add(fittedTransformer);
            prepared = fittedTransformer.Transform(prepared);
        }

        return new FittedPipeline(fitted, this.Model.Fit(FittedPipeline.FeaturesOf(prepared), t));
    }
}

/// <summary>学習済みの前処理とモデル。予測するときは Fit で覚えた値だけを使う。</summary>
public sealed record FittedPipeline(IReadOnlyList<IFittedTransformer> Transformers, FittedDecisionTree Model)
{
    /// <summary>学習済みの前処理を順に合成して、データを変換する。</summary>
    public Table Transform(Table x) => FittedTransformers.Compose(this.Transformers).Transform(x);

    /// <summary>前処理をして、モデルに渡す特徴量にする。</summary>
    public IReadOnlyList<Features> ToFeatures(Table x) => FeaturesOf(this.Transform(x));

    /// <summary>前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。</summary>
    public IReadOnlyList<int> Predict(Table x) => this.Model.Predict(this.ToFeatures(x));

    /// <summary>乗客 1 人分の行から、生存（1）か死亡（0）かを予測する。</summary>
    public int PredictOne(Row row) => this.Predict(SurvivedData.ToTable([row]))[0];

    /// <summary>前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。</summary>
    internal static IReadOnlyList<Features> FeaturesOf(Table x) =>
        [.. x.Rows.Select(row => new Features(
            x.Columns,
            [.. x.Columns.Select(column => row.Number(column)
                ?? throw new ArgumentException($"欠損値が残っています: {column}", nameof(x)))]))];
}
