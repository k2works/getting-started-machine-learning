namespace MachineLearning.Chapter10;

using MachineLearning.Chapter02;
using MachineLearning.Chapter03;

/// <summary>1 本分の学習結果。使った列と、ブートストラップ標本の行番号と、学習した木。</summary>
public sealed record FittedTree(IReadOnlyList<string> Columns, IReadOnlyList<int> Rows, Tree Tree);

/// <summary>
/// 第 3 章の決定木を、ブートストラップ標本と特徴量の部分集合で何本も学習して多数決する（バギング）。
/// 乱数は F# 版と同じ System.Random を使う。
/// </summary>
public sealed class RandomForest(RandomForest.Settings settings) : IClassifier
{
    /// <summary>学習の設定。</summary>
    /// <param name="NEstimators">木の本数。</param>
    /// <param name="MaxFeatures">木 1 本が使う特徴量の数。</param>
    /// <param name="MaxDepth">木の深さの上限。null なら上限なし。</param>
    /// <param name="Seed">乱数のシード。</param>
    public sealed record Settings(int NEstimators, int MaxFeatures, int? MaxDepth, int Seed)
    {
        /// <summary>既定の設定（10 本・特徴量 2 つ・深さの上限なし・シード 0）。</summary>
        public static Settings Default { get; } = new(10, 2, null, 0);
    }

    /// <summary>木ごとの予測のリストから、サンプルごとに最も多い予測を選ぶ。</summary>
    public static IReadOnlyList<string> MajorityVote(IReadOnlyList<IReadOnlyList<string>> votes)
    {
        ArgumentNullException.ThrowIfNull(votes);
        return [.. Enumerable.Range(0, votes[0].Count)
            .Select(i => DecisionTrees.Majority([.. votes.Select(vote => vote[i])]))];
    }

    /// <summary>シードを使って、0 以上 size 未満の行番号を size 個、重複を許して選ぶ。</summary>
    public static IReadOnlyList<int> BootstrapSample(int seed, int size)
    {
        var random = new Random(seed);
        return [.. Enumerable.Range(0, size).Select(_ => random.Next(size))];
    }

    /// <summary>シードで並べ替えた先頭 maxFeatures 個の特徴量を、元の列の順で返す。</summary>
    public static IReadOnlyList<string> ChooseFeatures(
        int seed, int maxFeatures, IReadOnlyList<string> features)
    {
        ArgumentNullException.ThrowIfNull(features);
        var chosen = Preprocessing.Shuffle(features, seed).Take(maxFeatures).ToHashSet(StringComparer.Ordinal);
        return [.. features.Where(chosen.Contains)];
    }

    /// <summary>行番号と列名で、1 本分の学習データを取り出す。同じ行番号が重複していれば、その回数だけ行が並ぶ。</summary>
    public static (IReadOnlyList<Features> X, IReadOnlyList<string> T) SampleOf(
        IReadOnlyList<int> rows,
        IReadOnlyList<string> columns,
        IReadOnlyList<Features> x,
        IReadOnlyList<string> t)
    {
        ArgumentNullException.ThrowIfNull(rows);
        ArgumentNullException.ThrowIfNull(columns);
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        return (
            [.. rows.Select(row => new Features(columns, [.. columns.Select(x[row].Value)]))],
            [.. rows.Select(row => t[row])]);
    }

    /// <summary>ブートストラップ標本と特徴量の部分集合で、第 3 章の決定木を NEstimators 本学習する。</summary>
    public static IReadOnlyList<FittedTree> Learn(
        Settings settings, IReadOnlyList<Features> x, IReadOnlyList<string> t)
    {
        ArgumentNullException.ThrowIfNull(settings);
        ArgumentNullException.ThrowIfNull(x);
        var features = x[0].Columns;
        return [.. TreeSeeds(settings.Seed, settings.NEstimators).Select(seeds =>
        {
            var rows = BootstrapSample(seeds.RowSeed, x.Count);
            var columns = ChooseFeatures(seeds.FeatureSeed, settings.MaxFeatures, features);
            var (sampleX, sampleT) = SampleOf(rows, columns, x, t);
            var tree = settings.MaxDepth is int maxDepth
                ? DecisionTree.WithMaxDepth(maxDepth)
                : DecisionTree.Unlimited();
            return new FittedTree(columns, rows, tree.Fit(sampleX, sampleT).Tree!);
        })];
    }

    /// <summary>木ごとに予測して多数決する。第 3 章の木は分割に使った列だけを見るので、列を絞らずに渡せる。</summary>
    public static IReadOnlyList<string> Predict(IReadOnlyList<FittedTree> forest, IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(forest);
        ArgumentNullException.ThrowIfNull(x);
        return MajorityVote(
            [.. forest.Select(fitted =>
                (IReadOnlyList<string>)[.. x.Select(features => DecisionTrees.PredictOne(fitted.Tree, features))])]);
    }

    public Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
        IReadOnlyList<Features> x, IReadOnlyList<string> t)
    {
        var forest = Learn(settings, x, t);
        return newX => Predict(forest, newX);
    }

    /// <summary>森のシードから、木ごとに「ブートストラップ標本のシード」と「特徴量を選ぶシード」の組を作る。</summary>
    private static IReadOnlyList<(int RowSeed, int FeatureSeed)> TreeSeeds(int seed, int count)
    {
        var random = new Random(seed);
        return [.. Enumerable.Range(0, count).Select(_ => (random.Next(), random.Next()))];
    }
}
