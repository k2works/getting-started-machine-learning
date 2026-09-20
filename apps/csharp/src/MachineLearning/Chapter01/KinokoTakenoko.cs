namespace MachineLearning.Chapter01;

/// <summary>きのこ派・たけのこ派の判定。</summary>
public static class KinokoTakenoko
{
    /// <summary>「20 代ならきのこ派」というルールの年代</summary>
    private const int KinokoAgeGroup = 20;

    /// <summary>
    /// BOM 付きの UTF-8 の CSV を読み込み、列名で値を取り出して人物のリストにする。
    /// .NET の File.ReadAllLines は BOM を取り除くので、列名から BOM を消す処理は要らない。
    /// </summary>
    public static IReadOnlyList<Person> LoadPeople(string csvFile)
    {
        var lines = File.ReadAllLines(csvFile);
        var header = lines[0].Split(',');
        var index = header.Select((name, i) => (name, i)).ToDictionary(pair => pair.name, pair => pair.i);
        return [.. lines.Skip(1)
            .Where(line => !string.IsNullOrWhiteSpace(line))
            .Select(line => line.Split(','))
            .Select(values => new Person(
                int.Parse(values[index["身長"]]),
                int.Parse(values[index["体重"]]),
                int.Parse(values[index["年代"]]),
                values[index["派閥"]]))];
    }

    /// <summary>人物のリストを特徴量と正解ラベルに分ける。</summary>
    public static (IReadOnlyList<Features> Features, IReadOnlyList<string> Labels) SplitFeaturesAndLabels(
        IEnumerable<Person> people)
    {
        ArgumentNullException.ThrowIfNull(people);
        var list = people.ToList();
        return (
            [.. list.Select(person => new Features(person.Height, person.Weight, person.AgeGroup))],
            [.. list.Select(person => person.Faction)]);
    }

    /// <summary>人間が決めたルールで派閥を判定する。</summary>
    public static string PredictByRule(Features features)
    {
        ArgumentNullException.ThrowIfNull(features);
        return features.AgeGroup == KinokoAgeGroup ? "きのこ" : "たけのこ";
    }

    /// <summary>予測が正解ラベルと一致した割合を返す。</summary>
    public static double Accuracy(IReadOnlyList<string> predictions, IReadOnlyList<string> labels)
    {
        ArgumentNullException.ThrowIfNull(predictions);
        ArgumentNullException.ThrowIfNull(labels);
        if (predictions.Count != labels.Count)
        {
            throw new ArgumentException("予測と正解ラベルの件数が違います", nameof(predictions));
        }

        var correct = predictions.Zip(labels).Count(pair => pair.First == pair.Second);
        return (double)correct / labels.Count;
    }
}
