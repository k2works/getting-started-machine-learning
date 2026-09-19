package chapter02;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 補完が済んだ 1 行分の特徴量。欠損値を持てないので、補完する前の行をモデルに渡すことはできない。
 *
 * <p>値は double の配列で持つ。record の成分に配列を使うと既定の equals が配列の中身ではなく参照を比べる（Error Prone の
 * ArrayRecordComponent）ので、record ではなくクラスにして、配列を写して持ち、equals・hashCode・toString を中身で比べる。
 */
public final class Features {
  private final List<String> columns;
  private final double[] values;

  public Features(List<String> columns, double[] values) {
    if (columns.size() != values.length) {
      throw new IllegalArgumentException("列名と値の数が違います");
    }
    this.columns = List.copyOf(columns);
    this.values = values.clone();
  }

  /** 列名の並び。 */
  public List<String> columns() {
    return columns;
  }

  /** 値の配列の写しを返す。返した配列を変えても、この特徴量は変わらない。 */
  public double[] values() {
    return values.clone();
  }

  /** 列名で値を読む。 */
  public double value(String column) {
    int index = columns.indexOf(column);
    if (index < 0) {
      throw new IllegalArgumentException("列がありません: " + column);
    }
    return values[index];
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Features that
        && columns.equals(that.columns)
        && Arrays.equals(values, that.values);
  }

  @Override
  public int hashCode() {
    return 31 * columns.hashCode() + Arrays.hashCode(values);
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder("Features[");
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        text.append(", ");
      }
      text.append(columns.get(i)).append('=').append(String.format(Locale.ROOT, "%s", values[i]));
    }
    return text.append(']').toString();
  }
}
