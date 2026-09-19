package chapter03;

/** 分割と、左右の部分木を持つ節。 */
public record Node(Split split, Tree left, Tree right) implements Tree {}
