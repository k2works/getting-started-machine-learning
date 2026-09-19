package chapter01;

/** 学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。 */
public record Person(int height, int weight, int ageGroup, String faction) {}
