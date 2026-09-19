package chapter03;

/** 決定木。葉（Leaf）か節（Node）のどちらかで、ほかの実装は許さない。 */
public sealed interface Tree permits Leaf, Node {}
