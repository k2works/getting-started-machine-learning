namespace MachineLearning.Chapter08;

using System.Globalization;
using MachineLearning.Chapter02;

/// <summary>Survived.csv の特徴量の列と正解ラベルの列。</summary>
public static class SurvivedData
{
    /// <summary>正解ラベルの列（1 が生存、0 が死亡）</summary>
    public const string Target = "Survived";

    /// <summary>モデルに渡す特徴量の列</summary>
    public static IReadOnlyList<string> FeatureColumns { get; } =
        ["Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked"];

    /// <summary>行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。</summary>
    public static Table ToTable(IReadOnlyList<Row> rows) => new(FeatureColumns, rows);

    /// <summary>行の Survived 列を、整数の正解ラベルにする。</summary>
    public static IReadOnlyList<int> Labels(IReadOnlyList<Row> rows)
    {
        ArgumentNullException.ThrowIfNull(rows);
        return [.. rows.Select(row => int.Parse(row.Text(Target), CultureInfo.InvariantCulture))];
    }
}
