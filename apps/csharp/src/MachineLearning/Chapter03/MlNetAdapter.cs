namespace MachineLearning.Chapter03;

using MachineLearning.Chapter02;
using Microsoft.ML;
using Microsoft.ML.Data;

/// <summary>ML.NET に渡す 1 行。ML.NET は書き換えられるプロパティを持つクラスを求める。</summary>
public sealed class MlRow
{
    public float[] Features { get; set; } = [];

    public string Label { get; set; } = string.Empty;
}

/// <summary>ML.NET が返す予測。列の名前（PredictedLabel）でプロパティに対応づけられる。</summary>
public sealed class MlPrediction
{
    public string PredictedLabel { get; set; } = string.Empty;
}

/// <summary>ML.NET の FastTree を使って、自作の決定木と突き合わせる。</summary>
public static class MlNetAdapter
{
    /// <summary>
    /// 木を 1 本だけ作る FastTree を、クラスごとの 2 値分類（OneVersusAll）で多クラスにして学習する。
    /// ML.NET には単一の決定木（CART）の学習器が無いので、勾配ブースティングの 1 本目の木で代わりにする。
    /// 葉の数の上限 numberOfLeaves は、深さ d の決定木なら 2 の d 乗に当たる。
    /// </summary>
    public static Func<IReadOnlyList<Features>, IReadOnlyList<string>> TrainFastTree(
        int numberOfLeaves, IReadOnlyList<Features> x, IReadOnlyList<string> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        var context = new MLContext(seed: 0);
        var schema = SchemaFor(x[0].Columns.Count);
        var data = context.Data.LoadFromEnumerable(ToRows(x, i => t[i]), schema);
        var fastTree = context.BinaryClassification.Trainers.FastTree(
            numberOfLeaves: numberOfLeaves, numberOfTrees: 1, minimumExampleCountPerLeaf: 1);
        var pipeline = context.Transforms.Conversion.MapValueToKey("Label")
            .Append(context.MulticlassClassification.Trainers.OneVersusAll(fastTree))
            .Append(context.Transforms.Conversion.MapKeyToValue("PredictedLabel"));
        var model = pipeline.Fit(data);

        return newX =>
        {
            var newData = context.Data.LoadFromEnumerable(ToRows(newX, _ => string.Empty), schema);
            return [.. context.Data
                .CreateEnumerable<MlPrediction>(model.Transform(newData), reuseRowObject: false)
                .Select(prediction => prediction.PredictedLabel)];
        };
    }

    private static List<MlRow> ToRows(IReadOnlyList<Features> x, Func<int, string> label) =>
        [.. x.Select((features, i) => new MlRow
        {
            Features = [.. features.Values.Select(value => (float)value)],
            Label = label(i),
        })];

    /// <summary>特徴量の数は実行時に決まるので、Features 列のベクトルの長さをスキーマで指定する。</summary>
    private static SchemaDefinition SchemaFor(int featureCount)
    {
        var schema = SchemaDefinition.Create(typeof(MlRow));
        schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, featureCount);
        return schema;
    }
}
