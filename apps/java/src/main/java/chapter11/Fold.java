package chapter11;

import java.util.List;

/**
 * 交差検証の 1 回分の分け方。行の位置（0 始まり）を訓練データとテストデータに分けて持つ。
 *
 * @param train 訓練データの行の位置
 * @param test テストデータの行の位置
 */
public record Fold(List<Integer> train, List<Integer> test) {
  public Fold {
    train = List.copyOf(train);
    test = List.copyOf(test);
  }
}
