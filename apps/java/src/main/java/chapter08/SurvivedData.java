package chapter08;

import chapter02.Row;
import chapter02.Table;
import java.util.List;

/** Survived.csv の特徴量の列と正解ラベルの列。 */
public final class SurvivedData {
  /** モデルに渡す特徴量の列 */
  public static final List<String> FEATURES =
      List.of("Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked");

  /** 正解ラベルの列（1 が生存、0 が死亡） */
  public static final String TARGET = "Survived";

  private SurvivedData() {}

  /** 行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。 */
  public static Table features(List<Row> rows) {
    return new Table(FEATURES, rows);
  }

  /** 行の Survived 列を、整数の正解ラベルにする。 */
  public static List<Integer> target(List<Row> rows) {
    return rows.stream().map(row -> Integer.parseInt(row.text(TARGET))).toList();
  }
}
