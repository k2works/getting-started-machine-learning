package chapter02;

import java.util.Map;
import java.util.OptionalDouble;

/** CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。 */
public record Row(Map<String, String> cells) {
  public Row {
    cells = Map.copyOf(cells);
  }

  /** 数値の列を読む。空欄なら空の OptionalDouble を返す。 */
  public OptionalDouble number(String column) {
    String cell = text(column);
    return cell.isBlank() ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(cell));
  }

  /** 文字列の列を読む。 */
  public String text(String column) {
    String cell = cells.get(column);
    if (cell == null) {
      throw new IllegalArgumentException("列がありません: " + column);
    }
    return cell;
  }

  /** セルが空欄かどうか。 */
  public boolean isMissing(String column) {
    return text(column).isBlank();
  }
}
