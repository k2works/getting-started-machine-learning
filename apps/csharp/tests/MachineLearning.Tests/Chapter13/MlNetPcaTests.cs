namespace MachineLearning.Tests.Chapter13;

using MachineLearning.Chapter07;
using MachineLearning.Chapter13;
using MachineLearning.Dataset;
using Microsoft.ML.Transforms;

/// <summary>ML.NET の ProjectToPrincipalComponents の振る舞いを確かめる学習用テスト。</summary>
public class MlNetPcaTests
{
    /// <summary>2 列目が 1 列目の 2 倍の、完全に相関する架空のデータ。</summary>
    private static readonly Matrix Correlated =
        Matrix.FromRows([[1.0, 2.0], [2.0, 4.0], [3.0, 6.0], [4.0, 8.0]]);

    private readonly string bostonFile = Path.Combine(DataDir.Current(), "Boston.csv");

    [Fact(DisplayName = "変換の結果は主成分の座標だけで、主成分や寄与率を取り出す API は無い")]
    public void ExposesOnlyCoordinates()
    {
        var members = typeof(PrincipalComponentAnalysisTransformer)
            .GetMembers()
            .Select(member => member.Name)
            .ToList();

        Assert.DoesNotContain(members, name => name.Contains("Eigen", StringComparison.Ordinal));
        Assert.DoesNotContain(members, name => name.Contains("Variance", StringComparison.Ordinal));
    }

    [Fact(DisplayName = "平均を引かない設定（既定）では、中心化せずに主成分の向きへ射影する")]
    public void DoesNotCenterByDefault()
    {
        var projected = MlNetPca.Project(1, false, 0, Correlated);

        // 元の点と主成分の向き (1, 2) / sqrt(5) との内積そのもの。符号は逆向きになった
        Assert.Equal(-Math.Sqrt(5.0), projected[0, 0], 5);
        Assert.Equal(-2.0 * Math.Sqrt(5.0), projected[1, 0], 5);
    }

    [Fact(DisplayName = "平均を引く設定では、件数の少ない完全に相関したデータで NaN になる")]
    public void ReturnsNaNForDegenerateData()
    {
        // ランダム化 PCA は、既定で rank + 20 本の乱数ベクトルを使う。
        // 4 件・2 列のように件数が足りず、しかも 1 本の直線に乗っているデータでは値が定まらない
        var projected = MlNetPca.Project(1, true, 0, Correlated);

        Assert.True(double.IsNaN(projected[0, 0]));
    }

    [Fact(DisplayName = "標準化した実データでは、自作の Transform と同じ座標になる（符号を除く）")]
    public void AgreesWithMineOnRealData()
    {
        Assert.SkipUnless(File.Exists(this.bostonFile), "学習データ Boston.csv が配置されていない（gulp data:setup）");

        var x = BostonStandardized.Load(this.bostonFile).X;
        var mine = Pca.Transform(Pca.Fit(2, x), x);
        var library = MlNetPca.Project(2, false, 0, x);

        for (var row = 0; row < x.RowCount; row++)
        {
            for (var pc = 0; pc < 2; pc++)
            {
                // float32 で計算するので、小数第 3 位までの一致にとどめる
                Assert.Equal(Math.Abs(mine[row, pc]), Math.Abs(library[row, pc]), 3);
            }
        }
    }
}
