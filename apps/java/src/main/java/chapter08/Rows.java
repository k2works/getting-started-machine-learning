package chapter08;

import chapter02.Row;
import java.util.HashMap;
import java.util.Map;

/** 行を書き換えた新しい行を作る。 */
final class Rows {
  private Rows() {}

  /** 列の値を置き換えた（列が無ければ足した）新しい行を返す。元の行は変更しない。 */
  static Row with(Row row, String column, String value) {
    Map<String, String> cells = new HashMap<>(row.cells());
    cells.put(column, value);
    return new Row(cells);
  }
}
