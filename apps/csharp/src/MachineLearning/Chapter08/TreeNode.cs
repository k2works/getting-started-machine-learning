namespace MachineLearning.Chapter08;

using System.Text.Json.Serialization;

/// <summary>
/// 重み付きの決定木。葉（<see cref="LeafNode"/>）か節（<see cref="SplitNode"/>）のどちらか。
/// 第 3 章の木と同じく、抽象レコードと sealed な派生で閉じる。JSON には "kind" で種類を書き分ける。
/// </summary>
[JsonPolymorphic(TypeDiscriminatorPropertyName = "kind")]
[JsonDerivedType(typeof(LeafNode), "leaf")]
[JsonDerivedType(typeof(SplitNode), "split")]
public abstract record TreeNode
{
    internal TreeNode()
    {
    }
}

/// <summary>予測するラベル（1 が生存、0 が死亡）を持つ葉。</summary>
public sealed record LeafNode(int Label) : TreeNode;

/// <summary>特徴量の値が境界以下なら左、境界より大きければ右へ進む節。</summary>
public sealed record SplitNode(string Feature, double Threshold, TreeNode Left, TreeNode Right) : TreeNode;
