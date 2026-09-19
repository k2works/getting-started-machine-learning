package chapter14;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1 つのクラスタの特徴。
 *
 * @param cluster クラスタ番号
 * @param count 所属する件数
 * @param means 列名ごとの平均（列の順を保つ）
 */
public record ClusterSummary(int cluster, int count, Map<String, Double> means) {
  public ClusterSummary {
    means = Collections.unmodifiableMap(new LinkedHashMap<>(means));
  }
}
