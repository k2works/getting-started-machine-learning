namespace MachineLearning.Tests.Chapter07;

using MachineLearning.Chapter07;

public class MatrixTests
{
    /// <summary>期待する値と、許容誤差つきで突き合わせる。</summary>
    internal static void AssertValues(IReadOnlyList<double> expected, IReadOnlyList<double> actual)
    {
        Assert.Equal(expected.Count, actual.Count);
        for (var i = 0; i < expected.Count; i++)
        {
            Assert.Equal(expected[i], actual[i], 9);
        }
    }

    [Fact(DisplayName = "内積は対応する成分を掛けて足す")]
    public void Dot()
    {
        Assert.Equal(32.0, Matrix.Dot([1.0, 2.0, 3.0], [4.0, 5.0, 6.0]), 12);
    }

    [Fact(DisplayName = "1 行 1 列の行列の積は成分の積")]
    public void MultiplyOneByOne()
    {
        var product = Matrix.FromRows([[2.0]]).Multiply(Matrix.FromRows([[3.0]]));

        Assert.Equal(Matrix.FromRows([[6.0]]), product);
    }

    [Fact(DisplayName = "行数と列数が違う行列の積は左の行数・右の列数になる")]
    public void MultiplyRectangular()
    {
        var left = Matrix.FromRows([[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]);
        var right = Matrix.FromRows([[7.0, 8.0], [9.0, 10.0], [11.0, 12.0]]);

        var product = left.Multiply(right);

        Assert.Equal(Matrix.FromRows([[58.0, 64.0], [139.0, 154.0]]), product);
    }

    [Fact(DisplayName = "左の列数と右の行数が違えば積を求められない")]
    public void MultiplyMismatched()
    {
        var left = Matrix.FromRows([[1.0, 2.0]]);
        var right = Matrix.FromRows([[1.0, 2.0]]);

        var error = Assert.Throws<ArgumentException>(() => left.Multiply(right));

        Assert.Contains("2", error.Message, StringComparison.Ordinal);
    }

    [Fact(DisplayName = "転置は行と列を入れ替える")]
    public void Transpose()
    {
        var matrix = Matrix.FromRows([[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]);

        Assert.Equal(Matrix.FromRows([[1.0, 4.0], [2.0, 5.0], [3.0, 6.0]]), matrix.Transpose());
    }

    [Fact(DisplayName = "行列とベクトルの積は行ごとの内積")]
    public void MultiplyVector()
    {
        var matrix = Matrix.FromRows([[1.0, 2.0], [3.0, 4.0]]);

        AssertValues([5.0, 11.0], matrix.Multiply([1.0, 2.0]));
    }

    [Fact(DisplayName = "1 元 1 次の連立方程式を解く")]
    public void SolveOne()
    {
        AssertValues([2.0], Matrix.FromRows([[3.0]]).Solve([6.0]));
    }

    [Fact(DisplayName = "2 元 1 次の連立方程式を解く")]
    public void SolveTwo()
    {
        var a = Matrix.FromRows([[2.0, 1.0], [1.0, 3.0]]);

        AssertValues([1.0, 2.0], a.Solve([4.0, 7.0]));
    }

    [Fact(DisplayName = "対角成分が 0 でも部分ピボット選択で解ける")]
    public void SolveZeroPivot()
    {
        var a = Matrix.FromRows([[0.0, 1.0], [1.0, 0.0]]);

        AssertValues([2.0, 1.0], a.Solve([1.0, 2.0]));
    }

    [Fact(DisplayName = "連立方程式を解いても元の行列とベクトルは変わらない")]
    public void SolveDoesNotMutate()
    {
        var a = Matrix.FromRows([[2.0, 1.0], [1.0, 3.0]]);
        double[] b = [4.0, 7.0];

        _ = a.Solve(b);

        Assert.Equal(Matrix.FromRows([[2.0, 1.0], [1.0, 3.0]]), a);
        AssertValues([4.0, 7.0], b);
    }

    [Fact(DisplayName = "先頭に 1 の列を足す")]
    public void WithLeadingOnes()
    {
        var matrix = Matrix.FromRows([[2.0, 3.0], [4.0, 5.0]]);

        Assert.Equal(Matrix.FromRows([[1.0, 2.0, 3.0], [1.0, 4.0, 5.0]]), matrix.WithLeadingOnes());
    }

    [Fact(DisplayName = "行数と列数を読める")]
    public void Shape()
    {
        var matrix = Matrix.FromRows([[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]);

        Assert.Equal((2, 3, 6.0), (matrix.RowCount, matrix.ColumnCount, matrix[1, 2]));
    }
}
