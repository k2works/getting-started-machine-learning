package chapter13;

import chapter02.Features;
import chapter02.Preprocessing;
import chapter02.Table;
import chapter07.Matrix;
import chapter09.Dummies;
import chapter09.Standardizer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** 主成分分析のために、ボストンの住宅価格（Boston.csv）のすべての列を前処理する。 */
public final class BostonPca {
  /** カテゴリ値の列 */
  public static final String CATEGORY = "CRIME";

  private BostonPca() {}

  /** CRIME をダミー変数にし、欠損値を列の平均値で補完してから、すべての列を標準化する。 */
  public static List<Features> standardize(Table table) {
    List<String> crimes = table.rows().stream().map(row -> row.text(CATEGORY)).toList();
    Table encoded = Dummies.encode(table, CATEGORY, Dummies.categories(crimes));
    List<String> columns = encoded.columns();
    List<Features> filled =
        Preprocessing.fillMissing(
            encoded.rows(), columns, Preprocessing.columnMeans(encoded.rows(), columns));
    return Standardizer.fit(filled).transform(filled);
  }

  /** CSV を読み込んで前処理する。 */
  public static List<Features> load(Path csvFile) throws IOException {
    return standardize(Table.load(csvFile));
  }

  /** 特徴量のリストを、1 件を 1 行とする行列にする。 */
  public static Matrix toMatrix(List<Features> x) {
    return Matrix.of(x.stream().map(Features::values).toArray(double[][]::new));
  }
}
