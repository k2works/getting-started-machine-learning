namespace MachineLearning.Chapter08;

using System.Text.Json.Serialization;
using MachineLearning.Chapter02;

/// <summary>訓練データから変換に必要な値を求める前処理。</summary>
public interface ITransformer
{
    /// <summary>訓練データから値を学習し、学習済みの前処理を返す。</summary>
    IFittedTransformer Fit(Table x);
}

/// <summary>
/// Fit で求めた値を使ってデータを変換する前処理。保存できるように、関数（デリゲート）ではなく
/// 値を持つレコードにして、JSON には "type" で種類を書き分ける。
/// </summary>
[JsonPolymorphic(TypeDiscriminatorPropertyName = "type")]
[JsonDerivedType(typeof(GroupMedianImputer.Fitted), "groupMedian")]
[JsonDerivedType(typeof(MostFrequentImputer.Fitted), "mostFrequent")]
[JsonDerivedType(typeof(DummyEncoder.Fitted), "dummy")]
public interface IFittedTransformer
{
    /// <summary>学習した値を使ってデータを変換する。元の表は変更しない。</summary>
    Table Transform(Table x);
}

/// <summary>学習済みの前処理をつなぐ。</summary>
public static class FittedTransformers
{
    /// <summary>この変換の後に next の変換を行う、合成した変換を返す。</summary>
    public static IFittedTransformer AndThen(this IFittedTransformer first, IFittedTransformer next) =>
        new Composed(first, next);

    /// <summary>学習済みの前処理を順に合成する。1 つも無ければ何も変えない変換になる。</summary>
    public static IFittedTransformer Compose(IEnumerable<IFittedTransformer> transformers)
    {
        ArgumentNullException.ThrowIfNull(transformers);
        return transformers.Aggregate((IFittedTransformer)new Identity(), AndThen);
    }

    /// <summary>何も変えない変換。合成の初期値に使う。</summary>
    private sealed record Identity : IFittedTransformer
    {
        public Table Transform(Table x) => x;
    }

    /// <summary>2 つの変換を順に行う変換。</summary>
    private sealed record Composed(IFittedTransformer First, IFittedTransformer Next) : IFittedTransformer
    {
        public Table Transform(Table x) => this.Next.Transform(this.First.Transform(x));
    }
}
