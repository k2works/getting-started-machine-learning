package chapter02;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.stream.IntStream;

/** アヤメのデータの前処理。 */
public final class Preprocessing {
  /** 正解ラベルの列 */
  public static final String TARGET = "種類";

  private Preprocessing() {}

  /** 欠損値を除いて、列ごとの平均値を求める。 */
  public static Map<String, Double> columnMeans(List<Row> rows, List<String> columns) {
    Map<String, Double> means = new LinkedHashMap<>();
    for (String column : columns) {
      OptionalDouble mean =
          rows.stream()
              .map(row -> row.number(column))
              .filter(OptionalDouble::isPresent)
              .mapToDouble(OptionalDouble::getAsDouble)
              .average();
      means.put(column, mean.orElseThrow());
    }
    return means;
  }

  /** 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変更しない。 */
  public static List<Features> fillMissing(
      List<Row> rows, List<String> columns, Map<String, Double> values) {
    return rows.stream()
        .map(
            row ->
                new Features(
                    columns,
                    columns.stream()
                        .mapToDouble(
                            column -> row.number(column).orElseGet(() -> fillValue(values, column)))
                        .toArray()))
        .toList();
  }

  private static double fillValue(Map<String, Double> values, String column) {
    Double value = values.get(column);
    if (value == null) {
      throw new IllegalArgumentException("補完する値がありません: " + column);
    }
    return value;
  }

  /** 正解ラベルの列を取り出し、残りの列を特徴量の列にする。 */
  public static FeaturesAndTarget splitFeaturesAndTarget(Table table, String target) {
    List<String> columns = table.columns().stream().filter(c -> !c.equals(target)).toList();
    List<String> labels = table.rows().stream().map(row -> row.text(target)).toList();
    return new FeaturesAndTarget(columns, table.rows(), labels);
  }

  /** シード付きの乱数で行を並べ替え、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。 */
  public static <X, T> TrainTestSplit<X, T> splitTrainTest(
      List<X> x, List<T> t, double testSize, long seed) {
    if (x.size() != t.size()) {
      throw new IllegalArgumentException("特徴量と正解ラベルの件数が違います");
    }
    List<Integer> positions = new ArrayList<>(IntStream.range(0, x.size()).boxed().toList());
    Collections.shuffle(positions, new Random(seed));
    int nTrain = x.size() - (int) Math.ceil(x.size() * testSize);
    List<Integer> train = positions.subList(0, nTrain);
    List<Integer> test = positions.subList(nTrain, positions.size());
    return new TrainTestSplit<>(pick(x, train), pick(x, test), pick(t, train), pick(t, test));
  }

  private static <E> List<E> pick(List<E> values, List<Integer> positions) {
    return positions.stream().map(values::get).toList();
  }

  /** iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。 */
  public static TrainTestSplit<Features, String> prepareIris(
      Path csvFile, double testSize, long seed) throws IOException {
    FeaturesAndTarget data = splitFeaturesAndTarget(Table.load(csvFile), TARGET);
    TrainTestSplit<Row, String> split = splitTrainTest(data.rows(), data.target(), testSize, seed);
    Map<String, Double> means = columnMeans(split.xTrain(), data.columns());
    return new TrainTestSplit<>(
        fillMissing(split.xTrain(), data.columns(), means),
        fillMissing(split.xTest(), data.columns(), means),
        split.tTrain(),
        split.tTest());
  }
}
