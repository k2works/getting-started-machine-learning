package chapter01;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** きのこ派・たけのこ派の判定。 */
public final class KinokoTakenoko {
  private static final String BOM = "﻿";

  /** 「20 代ならきのこ派」というルールの年代 */
  private static final int KINOKO_AGE_GROUP = 20;

  private KinokoTakenoko() {}

  /** BOM 付きの UTF-8 の CSV を読み込み、列名で値を取り出して人物のリストにする。 */
  public static List<Person> loadPeople(Path csvFile) throws IOException {
    List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
    List<String> header = Arrays.asList(stripBom(lines.getFirst()).split(","));
    Map<String, Integer> index =
        IntStream.range(0, header.size())
            .boxed()
            .collect(Collectors.toMap(header::get, Function.identity()));
    return lines.stream()
        .skip(1)
        .filter(line -> !line.isBlank())
        .map(line -> line.split(","))
        .map(
            values ->
                new Person(
                    Integer.parseInt(values[index.get("身長")]),
                    Integer.parseInt(values[index.get("体重")]),
                    Integer.parseInt(values[index.get("年代")]),
                    values[index.get("派閥")]))
        .toList();
  }

  private static String stripBom(String line) {
    return line.startsWith(BOM) ? line.substring(BOM.length()) : line;
  }

  /** 人物のリストを特徴量と正解ラベルに分ける。 */
  public static FeaturesAndLabels splitFeaturesAndLabels(List<Person> people) {
    List<Features> features =
        people.stream().map(p -> new Features(p.height(), p.weight(), p.ageGroup())).toList();
    List<String> labels = people.stream().map(Person::faction).toList();
    return new FeaturesAndLabels(features, labels);
  }

  /** 人間が決めたルールで派閥を判定する。 */
  public static String predictByRule(Features features) {
    return features.ageGroup() == KINOKO_AGE_GROUP ? "きのこ" : "たけのこ";
  }

  /** 予測が正解ラベルと一致した割合を返す。 */
  public static double accuracy(List<String> predictions, List<String> labels) {
    if (predictions.size() != labels.size()) {
      throw new IllegalArgumentException("予測と正解ラベルの件数が違います");
    }
    long correct =
        IntStream.range(0, labels.size())
            .filter(i -> predictions.get(i).equals(labels.get(i)))
            .count();
    return (double) correct / labels.size();
  }
}
