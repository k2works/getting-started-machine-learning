package chapter11;

import chapter02.Features;
import chapter02.Preprocessing;
import chapter02.Row;
import chapter02.Table;
import chapter07.Cinema;
import java.util.List;
import java.util.Map;

/**
 * 交差検証に渡す特徴量と正解ラベル。この章では分割の前に全体の平均値で補完する、簡略化した前処理を使う。
 *
 * @param x 補完が済んだ特徴量
 * @param t 正解ラベル
 */
public record Dataset<T>(List<Features> x, List<T> t) {
  /** Survived.csv の特徴量の列 */
  public static final List<String> SURVIVED_FEATURES = List.of("Pclass", "Age", "male");

  private static final String AGE = "Age";

  public Dataset {
    x = List.copyOf(x);
    t = List.copyOf(t);
  }

  /** 客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解ラベルにする。年齢の欠損値は平均値で補う。 */
  public static Dataset<String> prepareSurvived(Table table) {
    double ageMean = Preprocessing.columnMeans(table.rows(), List.of(AGE)).get(AGE);
    List<Features> x =
        table.rows().stream()
            .map(
                row ->
                    new Features(
                        SURVIVED_FEATURES,
                        new double[] {
                          number(row, "Pclass"),
                          row.number(AGE).orElse(ageMean),
                          "male".equals(row.text("Sex")) ? 1 : 0
                        }))
            .toList();
    List<String> t = table.rows().stream().map(row -> row.text("Survived")).toList();
    return new Dataset<>(x, t);
  }

  /** 第 7 章の 4 列を特徴量に、興行収入を正解にする。特徴量の欠損値は列ごとの平均値で補う。 */
  public static Dataset<Double> prepareCinema(Table table) {
    Map<String, Double> means = Preprocessing.columnMeans(table.rows(), Cinema.FEATURES);
    List<Features> x = Preprocessing.fillMissing(table.rows(), Cinema.FEATURES, means);
    List<Double> t = table.rows().stream().map(row -> number(row, Cinema.TARGET)).toList();
    return new Dataset<>(x, t);
  }

  private static double number(Row row, String column) {
    return row.number(column).orElseThrow(() -> new IllegalArgumentException("欠損値です: " + column));
  }
}
