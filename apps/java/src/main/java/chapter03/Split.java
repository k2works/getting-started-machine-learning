package chapter03;

/** 決定木の分割。特徴量の値が境界以下なら左、境界より大きければ右へ進む。 */
public record Split(String feature, double threshold, double impurity) {}
