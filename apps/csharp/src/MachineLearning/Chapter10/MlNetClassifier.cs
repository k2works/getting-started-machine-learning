namespace MachineLearning.Chapter10;

using MachineLearning.Chapter02;
using MachineLearning.Chapter03;
using Microsoft.ML;
using Microsoft.ML.Data;

/// <summary>ML.NET の多クラス分類の学習器を、第 10 章の分類器として使えるようにする。</summary>
public sealed class MlNetClassifier : IClassifier
{
    private readonly Func<MLContext, IEstimator<ITransformer>> trainer;

    private MlNetClassifier(Func<MLContext, IEstimator<ITransformer>> trainer) => this.trainer = trainer;

    /// <summary>ML.NET のソフトマックスのロジスティック回帰（L-BFGS で最適化する）。L1・L2 正則化の強さを指定する。</summary>
    public static MlNetClassifier LbfgsMaximumEntropy(float l1Regularization, float l2Regularization) =>
        new(context => context.MulticlassClassification.Trainers.LbfgsMaximumEntropy(
            l1Regularization: l1Regularization, l2Regularization: l2Regularization));

    /// <summary>ML.NET のランダムフォレスト（FastForest）。2 クラス用なので、OneVersusAll で多クラスにする。</summary>
    public static MlNetClassifier FastForest(int numberOfTrees, int minimumExampleCountPerLeaf) =>
        new(context => context.MulticlassClassification.Trainers.OneVersusAll(
            context.BinaryClassification.Trainers.FastForest(
                numberOfTrees: numberOfTrees, minimumExampleCountPerLeaf: minimumExampleCountPerLeaf)));

    public Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
        IReadOnlyList<Features> x, IReadOnlyList<string> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        var context = new MLContext(seed: 0);
        var schema = SchemaFor(x[0].Columns.Count);
        var pipeline = context.Transforms.Conversion.MapValueToKey("Label")
            .Append(this.trainer(context))
            .Append(context.Transforms.Conversion.MapKeyToValue("PredictedLabel"));
        var model = pipeline.Fit(context.Data.LoadFromEnumerable(ToRows(x, i => t[i]), schema));

        return newX =>
        {
            var data = context.Data.LoadFromEnumerable(ToRows(newX, _ => string.Empty), schema);
            return [.. context.Data
                .CreateEnumerable<MlPrediction>(model.Transform(data), reuseRowObject: false)
                .Select(prediction => prediction.PredictedLabel)];
        };
    }

    private static List<MlRow> ToRows(IReadOnlyList<Features> x, Func<int, string> label) =>
        [.. x.Select((features, i) => new MlRow
        {
            Features = [.. features.Values.Select(value => (float)value)],
            Label = label(i),
        })];

    /// <summary>Features 列のベクトルの長さは実行時に決まるので、スキーマで指定する（第 3 章と同じ）。</summary>
    private static SchemaDefinition SchemaFor(int featureCount)
    {
        var schema = SchemaDefinition.Create(typeof(MlRow));
        schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, featureCount);
        return schema;
    }
}
