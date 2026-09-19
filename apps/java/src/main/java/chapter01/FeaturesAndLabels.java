package chapter01;

import java.util.List;

/** 特徴量と正解ラベルの組。同じ位置の要素が同じ人物を表す。 */
public record FeaturesAndLabels(List<Features> features, List<String> labels) {}
