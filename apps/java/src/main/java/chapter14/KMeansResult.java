package chapter14;

import chapter07.Matrix;
import java.util.List;

/**
 * K-means の結果。
 *
 * <p>record の成分に配列を使うと equals が参照の比較になるので、割り当ては {@code List<Integer>}、中心は第 7 章の変更できない {@link
 * Matrix} で持つ。
 *
 * @param labels 点ごとのクラスタ番号
 * @param centers クラスタの中心を 1 行に 1 つずつ並べた行列
 * @param sse 誤差平方和
 */
public record KMeansResult(List<Integer> labels, Matrix centers, double sse) {
  public KMeansResult {
    labels = List.copyOf(labels);
  }
}
