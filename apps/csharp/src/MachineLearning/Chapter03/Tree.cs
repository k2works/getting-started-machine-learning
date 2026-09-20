namespace MachineLearning.Chapter03;

/// <summary>決定木の分割。特徴量の値が境界以下なら左、境界より大きければ右へ進む。</summary>
public sealed record Split(string Feature, double Threshold, double Impurity);

/// <summary>
/// 決定木。C# には sealed interface が無いので、抽象レコードと sealed な派生で閉じる。
/// このファイルの外では派生を作れないように、コンストラクターを internal にする。
/// </summary>
public abstract record Tree
{
    internal Tree()
    {
    }
}

/// <summary>予測するラベルを持つ葉。</summary>
public sealed record Leaf(string Label) : Tree;

/// <summary>分割と、左右の部分木を持つ節。</summary>
public sealed record Node(Split Split, Tree Left, Tree Right) : Tree;
