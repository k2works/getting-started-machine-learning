namespace MachineLearning.Chapter01;

/// <summary>学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。</summary>
public record Person(int Height, int Weight, int AgeGroup, string Faction);
