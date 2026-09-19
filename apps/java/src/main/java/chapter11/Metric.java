package chapter11;

import java.util.List;
import java.util.function.ToDoubleBiFunction;

/**
 * 正解と予測のリストから 1 つのスコアを求める評価関数。T は正解ラベルの型。
 *
 * <p>java.util.function の ToDoubleBiFunction に名前を付けただけのインターフェース。メソッド参照やラムダ式をそのまま渡せる。
 */
@FunctionalInterface
public interface Metric<T> extends ToDoubleBiFunction<List<T>, List<T>> {}
