namespace MachineLearning.Tests.Chapter08;

using MachineLearning.Chapter02;
using MachineLearning.Chapter08;

/// <summary>テストで使う架空の乗客の行と表を組み立てる。</summary>
internal static class Passengers
{
    /// <summary>特徴量の列の順に並べた値から、乗客 1 人分の行を作る。空文字列は欠損値。</summary>
    public static Row Of(params string[] values)
    {
        var cells = new Dictionary<string, string>(StringComparer.Ordinal);
        for (var i = 0; i < values.Length; i++)
        {
            cells[SurvivedData.FeatureColumns[i]] = values[i];
        }

        return new Row(cells);
    }

    /// <summary>行を、特徴量の列を持つ表にする。</summary>
    public static Table ToTable(params Row[] rows) => SurvivedData.ToTable(rows);
}
