namespace MachineLearning.Tests.Chapter01;

using MachineLearning.Chapter01;

public class LoadPeopleTests : IDisposable
{
    private const string Header = "\uFEFF身長,体重,年代,派閥\n";

    private readonly string directory = Directory.CreateTempSubdirectory("ml-csharp-").FullName;

    private string WriteCsv(string rows)
    {
        var path = Path.Combine(this.directory, "kvst.csv");
        File.WriteAllText(path, Header + rows);
        return path;
    }

    [Fact(DisplayName = "BOM 付き CSV を読み込んで人物のリストを返す")]
    public void ReadsCsvWithBom()
    {
        var csvFile = this.WriteCsv("165,58,30,きのこ\n");

        var people = KinokoTakenoko.LoadPeople(csvFile);

        Assert.Equal([new Person(165, 58, 30, "きのこ")], people);
    }

    [Fact(DisplayName = ".NET は BOM を取り除くので、列名に BOM は残らない")]
    public void DotNetStripsBom()
    {
        var csvFile = this.WriteCsv("165,58,30,きのこ\n");

        var header = File.ReadAllLines(csvFile)[0].Split(',');

        Assert.Equal("身長", header[0]);
    }

    [Fact(DisplayName = "複数行の CSV を読み込んで行の順に人物のリストを返す")]
    public void ReadsRowsInOrder()
    {
        var csvFile = this.WriteCsv("161,52,20,きのこ\n183,74,50,たけのこ\n");

        var people = KinokoTakenoko.LoadPeople(csvFile);

        Assert.Equal(
            [new Person(161, 52, 20, "きのこ"), new Person(183, 74, 50, "たけのこ")],
            people);
    }

    public void Dispose()
    {
        Directory.Delete(this.directory, recursive: true);
        GC.SuppressFinalize(this);
    }
}

public class SplitFeaturesAndLabelsTests
{
    [Fact(DisplayName = "人物のリストを特徴量と正解ラベルに分ける")]
    public void SplitsPeople()
    {
        List<Person> people = [new(161, 52, 20, "きのこ"), new(183, 74, 50, "たけのこ")];

        var (features, labels) = KinokoTakenoko.SplitFeaturesAndLabels(people);

        Assert.Equal([new Features(161, 52, 20), new Features(183, 74, 50)], features);
        Assert.Equal(["きのこ", "たけのこ"], labels);
    }
}

public class PredictByRuleTests
{
    [Fact(DisplayName = "20 代ならきのこ派と判定する")]
    public void TwentiesAreKinoko() =>
        Assert.Equal("きのこ", KinokoTakenoko.PredictByRule(new Features(161, 52, 20)));

    [Fact(DisplayName = "20 代以外ならたけのこ派と判定する")]
    public void OthersAreTakenoko() =>
        Assert.Equal("たけのこ", KinokoTakenoko.PredictByRule(new Features(183, 74, 50)));
}

public class AccuracyTests
{
    [Fact(DisplayName = "すべての予測が正解なら正解率は 1")]
    public void AllCorrect() =>
        Assert.Equal(1.0, KinokoTakenoko.Accuracy(["きのこ", "たけのこ"], ["きのこ", "たけのこ"]));

    [Fact(DisplayName = "4 件中 3 件の予測が正解なら正解率は 0.75")]
    public void ThreeOfFour() =>
        Assert.Equal(
            0.75,
            KinokoTakenoko.Accuracy(
                ["きのこ", "きのこ", "たけのこ", "たけのこ"],
                ["きのこ", "たけのこ", "たけのこ", "たけのこ"]));

    [Fact(DisplayName = "予測と正解ラベルの件数が違えばエラーになる")]
    public void SizeMismatch() =>
        Assert.Throws<ArgumentException>(() => KinokoTakenoko.Accuracy(["きのこ"], ["きのこ", "たけのこ"]));
}
