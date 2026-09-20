namespace MachineLearning.Chapter09;

using MachineLearning.Chapter02;

/// <summary>2 つの列の組。<paramref name="Left"/> と <paramref name="Right"/> が同じなら 2 乗の項を表す。</summary>
/// <param name="Left">左の列</param>
/// <param name="Right">右の列</param>
public sealed record Term(string Left, string Right)
{
    /// <summary>項の名前。scikit-learn の get_feature_names_out と同じ形（"RM^2"・"RM LSTAT"）にする。</summary>
    public string Name => string.Equals(this.Left, this.Right, StringComparison.Ordinal)
        ? $"{this.Left}^2"
        : $"{this.Left} {this.Right}";
}

/// <summary>2 次の多項式特徴量（2 乗の項と交互作用の項）を作る。</summary>
public static class PolynomialFeatures
{
    /// <summary>同じ列どうしの組も含めて、列の組を重複なく作る（[a, b] なら (a,a)・(a,b)・(b,b)）。</summary>
    public static IReadOnlyList<Term> PairsWithReplacement(IReadOnlyList<string> columns)
    {
        ArgumentNullException.ThrowIfNull(columns);
        return [.. columns.SelectMany((left, i) => columns.Skip(i).Select(right => new Term(left, right)))];
    }

    /// <summary>指定した列の後ろに、2 乗の項と交互作用の項を加える。指定しなかった列は残さない。</summary>
    public static IReadOnlyList<Features> Expand(IReadOnlyList<Features> x, IReadOnlyList<string> columns)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(columns);
        var terms = PairsWithReplacement(columns);
        IReadOnlyList<string> names = [.. columns, .. terms.Select(term => term.Name)];
        return [.. x.Select(features => new Features(
            names,
            [
                .. columns.Select(features.Value),
                .. terms.Select(term => features.Value(term.Left) * features.Value(term.Right)),
            ]))];
    }

    /// <summary>指定した列だけを、その順に選ぶ。</summary>
    public static IReadOnlyList<Features> Select(IReadOnlyList<Features> x, IReadOnlyList<string> columns)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(columns);
        return [.. x.Select(features => new Features(columns, [.. columns.Select(features.Value)]))];
    }
}
