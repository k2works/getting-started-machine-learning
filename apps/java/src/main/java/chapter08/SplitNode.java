package chapter08;

/** 特徴量の値が境界以下なら左、境界より大きければ右へ進む節。 */
public record SplitNode(String feature, double threshold, TreeNode left, TreeNode right)
    implements TreeNode {}
