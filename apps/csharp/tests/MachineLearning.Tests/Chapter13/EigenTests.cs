namespace MachineLearning.Tests.Chapter13;

using MachineLearning.Chapter07;
using MachineLearning.Chapter13;

public class EigenTests
{
    [Fact(DisplayName = "対角行列の固有値は対角成分、固有ベクトルは軸の向きになる")]
    public void DecomposesDiagonalMatrix()
    {
        var pairs = Eigen.Symmetric(Matrix.FromRows([[3.0, 0.0], [0.0, 1.0]]));

        Assert.Equal([3.0, 1.0], pairs.Select(pair => pair.Value));
        Assert.Equal([1.0, 0.0], pairs[0].Vector);
        Assert.Equal([0.0, 1.0], pairs[1].Vector);
    }

    [Fact(DisplayName = "固有値を大きい順に並べる")]
    public void SortsByValueDescending()
    {
        var pairs = Eigen.Symmetric(Matrix.FromRows([[1.0, 0.0], [0.0, 5.0]]));

        Assert.Equal([5.0, 1.0], pairs.Select(pair => pair.Value));
    }

    [Fact(DisplayName = "対角成分以外が 0 でない対称行列を分解する")]
    public void DecomposesSymmetricMatrix()
    {
        // [[2, 1], [1, 2]] の固有値は 3 と 1、固有ベクトルは (1, 1) と (1, -1) を長さ 1 にしたもの
        var pairs = Eigen.Symmetric(Matrix.FromRows([[2.0, 1.0], [1.0, 2.0]]));
        var unit = 1.0 / Math.Sqrt(2.0);

        Assert.Equal(3.0, pairs[0].Value, 10);
        Assert.Equal(1.0, pairs[1].Value, 10);
        Assert.Equal(unit, Math.Abs(pairs[0].Vector[0]), 10);
        Assert.Equal(unit, Math.Abs(pairs[0].Vector[1]), 10);
        Assert.Equal(pairs[0].Vector[0], pairs[0].Vector[1], 10);
        Assert.Equal(-pairs[1].Vector[0], pairs[1].Vector[1], 10);
    }

    [Fact(DisplayName = "固有ベクトルは A v = λ v を満たし、長さが 1 になる")]
    public void SatisfiesEigenEquation()
    {
        var a = Matrix.FromRows([[4.0, 1.0, 2.0], [1.0, 3.0, 0.5], [2.0, 0.5, 5.0]]);

        foreach (var pair in Eigen.Symmetric(a))
        {
            var left = a.Multiply(pair.Vector);
            var right = pair.Vector.Select(value => pair.Value * value).ToList();
            Assert.Equal(1.0, Math.Sqrt(Matrix.Dot(pair.Vector, pair.Vector)), 10);
            for (var i = 0; i < left.Count; i++)
            {
                Assert.Equal(right[i], left[i], 10);
            }
        }
    }

    [Fact(DisplayName = "回転で対角成分以外の 2 乗和が小さくなる")]
    public void RotationReducesOffDiagonal()
    {
        var a = Matrix.FromRows([[2.0, 1.0], [1.0, 2.0]]);
        var j = Eigen.Rotation(a, 0, 1);

        var rotated = j.Transpose().Multiply(a).Multiply(j);

        Assert.Equal(2.0, Eigen.OffDiagonal(a), 10);
        Assert.Equal(0.0, Eigen.OffDiagonal(rotated), 15);
    }
}
