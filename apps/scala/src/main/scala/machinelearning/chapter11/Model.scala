package machinelearning.chapter11

import machinelearning.chapter02.Features

/** 正解と予測のリストから 1 つのスコアを求める評価関数。T は正解ラベルの型。
  *
  * Java 版は `ToDoubleBiFunction` を継承した関数型インターフェースを定義したが、Scala では関数そのものが型なので、型の別名で足りる。
  */
type Metric[T] = (Vector[T], Vector[T]) => Double

/** 交差検証で学習と予測を繰り返すモデル。T は正解ラベルの型。
  *
  * 第 10 章の `Classifier` と同じく、`fit` は学習した結果のモデルを返す。
  */
trait Model[T]:
  /** 訓練データで学習する。 */
  def fit(x: Vector[Features], t: Vector[T]): Model[T]

  /** 学習した結果で予測する。 */
  def predict(x: Vector[Features]): Vector[T]
