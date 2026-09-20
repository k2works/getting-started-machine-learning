namespace MachineLearning.Chapter08;

using MachineLearning.Chapter02;
using Microsoft.ML;
using Microsoft.ML.Data;

// この章の ITransformer（前処理）と名前が重なるので、ML.NET の ITransformer には別名を付ける
using MlTransformer = Microsoft.ML.ITransformer;

/// <summary>ML.NET に渡す 1 行。生存したか（Label）と、行の重み（Weight）を持つ。</summary>
public sealed class WeightedRow
{
    public float[] Features { get; set; } = [];

    public bool Label { get; set; }

    public float Weight { get; set; }
}

/// <summary>ML.NET が返す 2 値分類の予測。</summary>
public sealed class BinaryPrediction
{
    public bool PredictedLabel { get; set; }
}

/// <summary>ML.NET の FastTree に行の重みを渡して、自作の重み付きの決定木と突き合わせる。</summary>
public static class MlNetAdapter
{
    /// <summary>木を 1 本だけ作る FastTree を、行の重み（Weight 列）を付けて学習する。</summary>
    public static MlTransformer TrainFastTree(
        int numberOfLeaves,
        IReadOnlyList<Features> x,
        IReadOnlyList<int> t,
        IReadOnlyList<double> weights)
    {
        ArgumentNullException.ThrowIfNull(x);
        var context = new MLContext(seed: 0);
        var fastTree = context.BinaryClassification.Trainers.FastTree(
            exampleWeightColumnName: "Weight",
            numberOfLeaves: numberOfLeaves,
            numberOfTrees: 1,
            minimumExampleCountPerLeaf: 1);
        return fastTree.Fit(ToDataView(context, x, t, weights));
    }

    /// <summary>学習した FastTree で、生存（1）か死亡（0）かを予測する。</summary>
    public static IReadOnlyList<int> PredictFastTree(MlTransformer model, IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(model);
        ArgumentNullException.ThrowIfNull(x);
        var context = new MLContext(seed: 0);
        var data = ToDataView(context, x, [.. x.Select(_ => 0)], [.. x.Select(_ => 1.0)]);
        return [.. context.Data
            .CreateEnumerable<BinaryPrediction>(model.Transform(data), reuseRowObject: false)
            .Select(prediction => prediction.PredictedLabel ? 1 : 0)];
    }

    /// <summary>学習した FastTree を zip で保存する。保存先のディレクトリが無ければ作る。</summary>
    public static void SaveFastTree(MlTransformer model, string modelFile)
    {
        var directory = Path.GetDirectoryName(Path.GetFullPath(modelFile));
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }

        new MLContext(seed: 0).Model.Save(model, null, modelFile);
    }

    /// <summary>zip に保存した FastTree を読み込む。</summary>
    public static MlTransformer LoadFastTree(string modelFile) =>
        new MLContext(seed: 0).Model.Load(modelFile, out _);

    /// <summary>特徴量・正解ラベル・重みを、ML.NET のデータ（IDataView）にする。</summary>
    private static IDataView ToDataView(
        MLContext context,
        IReadOnlyList<Features> x,
        IReadOnlyList<int> t,
        IReadOnlyList<double> weights)
    {
        var rows = x.Select((features, i) => new WeightedRow
        {
            Features = [.. features.Values.Select(value => (float)value)],
            Label = t[i] == 1,
            Weight = (float)weights[i],
        });
        return context.Data.LoadFromEnumerable(rows, SchemaFor(x[0].Columns.Count));
    }

    /// <summary>特徴量の数は実行時に決まるので、Features 列のベクトルの長さをスキーマで指定する。</summary>
    private static SchemaDefinition SchemaFor(int featureCount)
    {
        var schema = SchemaDefinition.Create(typeof(WeightedRow));
        schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, featureCount);
        return schema;
    }
}
