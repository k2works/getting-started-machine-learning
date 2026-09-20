namespace MachineLearning.Tests.Chapter12;

using MachineLearning.Chapter07;
using MachineLearning.Chapter12;

public class MatrixOperationsTests
{
    private static readonly Matrix A = Matrix.FromRows([[1.0, 2.0], [3.0, 4.0]]);

    [Fact(DisplayName = "単位行列は対角成分だけが 1")]
    public void Identity()
    {
        Assert.Equal(Matrix.FromRows([[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]]), MatrixOperations.Identity(3));
    }

    [Fact(DisplayName = "同じ形の行列は成分ごとに足せる")]
    public void Add()
    {
        Assert.Equal(Matrix.FromRows([[11.0, 22.0], [33.0, 44.0]]), A.Add(Matrix.FromRows([[10.0, 20.0], [30.0, 40.0]])));
    }

    [Fact(DisplayName = "形が違う行列は足せない")]
    public void AddMismatched()
    {
        Assert.Throws<ArgumentException>(() => A.Add(Matrix.FromRows([[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]])));
    }

    [Fact(DisplayName = "定数倍はすべての成分に掛かる")]
    public void Scale()
    {
        Assert.Equal(Matrix.FromRows([[2.0, 4.0], [6.0, 8.0]]), A.Scale(2.0));
    }

    [Fact(DisplayName = "列ごとの平均値を求める")]
    public void ColumnMeans()
    {
        Assert.Equal<IReadOnlyList<double>>([2.0, 3.0], A.ColumnMeans());
    }

    [Fact(DisplayName = "中心化すると列ごとの平均が 0 になる")]
    public void Center()
    {
        var centered = A.Center(A.ColumnMeans());

        Assert.Equal(Matrix.FromRows([[-1.0, -1.0], [1.0, 1.0]]), centered);
        Assert.Equal<IReadOnlyList<double>>([0.0, 0.0], centered.ColumnMeans());
    }
}
