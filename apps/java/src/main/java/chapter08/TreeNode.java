package chapter08;

import java.io.Serializable;

/** 重み付きの決定木。葉（LeafNode）か節（SplitNode）のどちらか。 */
public sealed interface TreeNode extends Serializable permits LeafNode, SplitNode {}
