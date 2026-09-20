namespace MachineLearning.Tests.Chapter04;

using MachineLearning.Chapter02;

/// <summary>
/// System.Random のシード付きの乱数列を固定する学習用テスト。
/// .NET を上げてこのテストが失敗したら、分割に入る行と記事の数値が変わる。
/// </summary>
public class RandomSequenceTests
{
    [Fact(DisplayName = "シード 0 の乱数列は .NET 8・9・10 で同じ値になる")]
    public void SeededSequenceIsStable()
    {
        var random = new Random(0);

        int[] values = [random.Next(), random.Next(), random.Next()];

        Assert.Equal([1559595546, 1755192844, 1649316166], values);
    }

    [Fact(DisplayName = "シード 0 で 0 から 9 を並べ替えた順は F# 版と同じ")]
    public void ShuffleMatchesFSharp() =>
        Assert.Equal([0, 4, 5, 8, 2, 1, 3, 6, 9, 7], Preprocessing.Shuffle([.. Enumerable.Range(0, 10)], 0));

    [Fact(DisplayName = "シードを渡さなければ乱数列は Random を作るたびに変わる")]
    public void UnseededSequenceVaries()
    {
        static IReadOnlyList<int> Draw(Random random) => [.. Enumerable.Range(0, 10).Select(_ => random.Next())];

        Assert.NotEqual(Draw(new Random()), Draw(new Random()));
    }
}
