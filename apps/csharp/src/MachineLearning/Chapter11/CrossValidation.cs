namespace MachineLearning.Chapter11;

using MachineLearning.Chapter02;

/// <summary>1 回分の分け方。訓練データとテストデータの行番号。</summary>
/// <param name="Train">訓練データにする行番号</param>
/// <param name="Test">テストデータにする行番号</param>
public sealed record Fold(IReadOnlyList<int> Train, IReadOnlyList<int> Test);

/// <summary>
/// モデル。訓練データ（特徴量と正解）を受け取り、予測する関数を返す関数。
/// 第 3 章の Fit と Predict をつないだ形で、F# の <c>Model&lt;'T&gt;</c> に当たる。
/// </summary>
/// <typeparam name="T">正解ラベルの型</typeparam>
/// <param name="x">訓練データの特徴量</param>
/// <param name="t">訓練データの正解</param>
/// <returns>特徴量から予測を返す関数</returns>
public delegate Func<IReadOnlyList<Features>, IReadOnlyList<T>> Model<T>(
    IReadOnlyList<Features> x, IReadOnlyList<T> t);

/// <summary>K 分割交差検証。</summary>
public static class CrossValidation
{
    /// <summary>行番号をシードで並べ替えてから nSplits 個に分け、それぞれをテストデータ、残りを訓練データにする。</summary>
    public static IReadOnlyList<Fold> KFold(int nSplits, int seed, int nSamples)
    {
        if (nSplits < 2 || nSplits > nSamples)
        {
            throw new ArgumentOutOfRangeException(nameof(nSplits), $"分割数は 2 以上 {nSamples} 以下にしてください");
        }

        var positions = Preprocessing.Shuffle([.. Enumerable.Range(0, nSamples)], seed);
        return [.. SplitInto(positions, nSplits).Select(test =>
            new Fold([.. positions.Except(test)], test))];
    }

    /// <summary>
    /// 分割ごとに、訓練データで学習し、テストデータの予測を評価関数で採点する。
    /// 反復子なので、スコアは取り出すときに初めて計算する（取り出した分だけ学習する）。
    /// </summary>
    public static IEnumerable<double> CrossValidate<T>(
        Model<T> model,
        Metric<T> metric,
        IReadOnlyList<Fold> folds,
        IReadOnlyList<Features> x,
        IReadOnlyList<T> t)
    {
        ArgumentNullException.ThrowIfNull(model);
        ArgumentNullException.ThrowIfNull(metric);
        ArgumentNullException.ThrowIfNull(folds);
        foreach (var fold in folds)
        {
            var predict = model(Pick(x, fold.Train), Pick(t, fold.Train));
            yield return metric(Pick(t, fold.Test), predict(Pick(x, fold.Test)));
        }
    }

    /// <summary>行番号の順に要素を取り出す。</summary>
    public static IReadOnlyList<TItem> Pick<TItem>(IReadOnlyList<TItem> items, IReadOnlyList<int> rows)
    {
        ArgumentNullException.ThrowIfNull(items);
        ArgumentNullException.ThrowIfNull(rows);
        return [.. rows.Select(row => items[row])];
    }

    /// <summary>
    /// ほぼ同じ長さの count 個に分ける。割り切れないときは先頭のまとまりから 1 件ずつ多くする
    /// （F# の List.splitInto と同じ分け方）。
    /// </summary>
    private static IEnumerable<IReadOnlyList<int>> SplitInto(IReadOnlyList<int> positions, int count)
    {
        var size = positions.Count / count;
        var remainder = positions.Count % count;
        var taken = 0;
        for (var i = 0; i < count; i++)
        {
            var length = size + (i < remainder ? 1 : 0);
            yield return [.. positions.Skip(taken).Take(length)];
            taken += length;
        }
    }
}
