namespace MachineLearning.Tests.Chapter08;

using MachineLearning.Chapter02;
using MachineLearning.Chapter03;
using MachineLearning.Chapter08;
using MachineLearning.Dataset;

public class SurvivedDataTests
{
    private const double TestSize = 0.2;
    private const int Seed = 0;

    private readonly string csvFile = Path.Combine(DataDir.Current(), "Survived.csv");

    [Fact(DisplayName = "Survived.csv は 891 件で、年齢が 177 件・港が 2 件欠けている")]
    public void CountsMissing()
    {
        this.RequireData();

        var table = Table.Load(this.csvFile);
        var missing = table.CountMissing().ToDictionary(pair => pair.Key, pair => pair.Value, StringComparer.Ordinal);

        Assert.Equal(891, table.Rows.Count);
        Assert.Equal(177, missing["Age"]);
        Assert.Equal(2, missing["Embarked"]);
        Assert.Equal(687, missing["Cabin"]);
        Assert.Equal(342, SurvivedData.Labels(table.Rows).Count(label => label == 1));
    }

    [Fact(DisplayName = "深さ 5 では balanced にすると見つけられる生存者が増える")]
    public void BalancedFindsMoreSurvivors()
    {
        this.RequireData();
        var split = this.Split();

        Assert.Equal(54, Evaluate(split, 5, ClassWeight.None).FoundSurvivors);
        Assert.Equal(59, Evaluate(split, 5, ClassWeight.Balanced).FoundSurvivors);
    }

    [Fact(DisplayName = "深さ 1 では balanced にしても評価が変わらない")]
    public void DepthOneIsUnaffected()
    {
        this.RequireData();
        var split = this.Split();

        Assert.Equal(Evaluate(split, 1, ClassWeight.None), Evaluate(split, 1, ClassWeight.Balanced));
    }

    [Theory(DisplayName = "重み付けなしなら第 3 章の決定木と同じ形の木を作る")]
    [InlineData(1)]
    [InlineData(2)]
    [InlineData(3)]
    [InlineData(4)]
    [InlineData(5)]
    public void SameShapeAsChapter03(int maxDepth)
    {
        this.RequireData();
        var split = this.Split();
        var pipeline = Pipeline.Build(maxDepth, ClassWeight.None).Fit(SurvivedData.ToTable(split.XTrain), split.TTrain);
        var features = pipeline.ToFeatures(SurvivedData.ToTable(split.XTrain));

        var chapter03 = DecisionTree.WithMaxDepth(maxDepth)
            .Fit(features, [.. split.TTrain.Select(label => label.ToString(System.Globalization.CultureInfo.InvariantCulture))]);

        Assert.Equal(Shape(chapter03.Tree!), Shape(pipeline.Model.Root));
    }

    [Fact(DisplayName = "実行するとクラスの重みごとの評価と、保存したモデルの予測を表示する")]
    public void PrintsSummary()
    {
        this.RequireData();
        var directory = Directory.CreateTempSubdirectory("chapter08").FullName;
        try
        {
            using var output = new StringWriter();
            MachineLearning.Chapter08.Program.Run(output, directory);

            Assert.Equal(
                string.Join(
                    Environment.NewLine,
                    "データ件数: 891（生存 342, 死亡 549）",
                    "訓練データ: 712 件, テストデータ: 179 件",
                    "classWeight=None: 訓練 0.858, テスト 0.821, 生存者 72 人中 54 人を発見, ML.NET と一致 175/179",
                    "classWeight=Balanced: 訓練 0.840, テスト 0.788, 生存者 72 人中 59 人を発見, ML.NET と一致 170/179",
                    "架空の乗客の予測（survived.json）: [1, 0]",
                    "架空の乗客の予測（survived-mlnet.zip）: [1, 0]") + Environment.NewLine,
                output.ToString());
        }
        finally
        {
            Directory.Delete(directory, recursive: true);
        }
    }

    /// <summary>ラベルの型が違う 2 つの木を比べるために、分割の並びだけを取り出す。</summary>
    private static IReadOnlyList<string> Shape(TreeNode node) =>
        node switch
        {
            LeafNode => ["葉"],
            SplitNode split =>
                [$"{split.Feature}<={split.Threshold}", .. Shape(split.Left), .. Shape(split.Right)],
            _ => throw new ArgumentException($"知らない木です: {node}", nameof(node)),
        };

    private static IReadOnlyList<string> Shape(Tree tree) =>
        tree switch
        {
            Leaf => ["葉"],
            Node node => [$"{node.Split.Feature}<={node.Split.Threshold}", .. Shape(node.Left), .. Shape(node.Right)],
            _ => throw new ArgumentException($"知らない木です: {tree}", nameof(tree)),
        };

    private static Evaluation Evaluate(TrainTestSplit<Row, int> split, int maxDepth, ClassWeight classWeight) =>
        Evaluation.Of(
            Pipeline.Build(maxDepth, classWeight).Fit(SurvivedData.ToTable(split.XTrain), split.TTrain),
            split);

    private TrainTestSplit<Row, int> Split()
    {
        var rows = Table.Load(this.csvFile).Rows;
        return Preprocessing.SplitTrainTest(rows, SurvivedData.Labels(rows), TestSize, Seed);
    }

    private void RequireData() =>
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ Survived.csv が配置されていない（gulp data:setup）");
}
