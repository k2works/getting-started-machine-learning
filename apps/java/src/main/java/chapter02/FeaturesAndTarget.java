package chapter02;

import java.util.List;

/** 特徴量の列名・補完する前の行・正解ラベル。同じ位置の行と正解ラベルが同じ事例を表す。 */
public record FeaturesAndTarget(List<String> columns, List<Row> rows, List<String> target) {
  public FeaturesAndTarget {
    columns = List.copyOf(columns);
    rows = List.copyOf(rows);
    target = List.copyOf(target);
  }
}
