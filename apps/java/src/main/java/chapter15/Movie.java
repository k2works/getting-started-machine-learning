package chapter15;

/** 映画の特徴量。SNS の評判 2 種類・主演の人気・原作の有無（0 か 1）。 */
public record Movie(double sns1, double sns2, double actor, int original) {}
