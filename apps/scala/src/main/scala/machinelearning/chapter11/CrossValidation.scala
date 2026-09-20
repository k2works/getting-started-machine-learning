package machinelearning.chapter11

import machinelearning.chapter02.{Features, Preprocessing}

/** 交差検証の 1 回分の分け方。行の位置（0 始まり）を訓練データとテストデータに分けて持つ。
  *
  * @param train
  *   訓練データの行の位置
  * @param test
  *   テストデータの行の位置
  */
case class Fold(train: Vector[Int], test: Vector[Int])

/** K 分割交差検証。 */
object CrossValidation:

  /** シード付きの乱数で行を並べ替え、nSplits 個のテストデータに分ける。
    *
    * 件数が割り切れないときは、余りを先頭の分割から 1 件ずつ配る。第 2 章の `Preprocessing.shuffle` を使うので、Java 版と同じ並びになる。
    */
  def kFold(nSamples: Int, nSplits: Int, seed: Long): Vector[Fold] =
    require(nSplits >= 1, "分割の数は 1 以上にしてください")
    require(nSamples >= nSplits, "件数は分割の数以上でなければなりません")
    val positions = Preprocessing.shuffle((0 until nSamples).toVector, seed)
    val sizes =
      (0 until nSplits).toVector.map(i =>
        nSamples / nSplits + (if i < nSamples % nSplits then 1 else 0)
      )
    sizes
      .scanLeft(0)(_ + _)
      .lazyZip(sizes)
      .map { (from, size) =>
        val test = positions.slice(from, from + size)
        Fold(positions.filterNot(test.toSet), test)
      }
      .toVector

  /** 分割ごとに新しいモデルを作って訓練データで学習し、テストデータの予測を評価関数で採点する。
    *
    * スコアは遅延評価の LazyList で返す。取り出した分だけ学習する。
    */
  def crossValidate[T](
      makeModel: () => Model[T],
      x: Vector[Features],
      t: Vector[T],
      folds: Vector[Fold],
      metric: Metric[T]
  ): LazyList[Double] =
    folds.to(LazyList).map { fold =>
      val fitted = makeModel().fit(pick(x, fold.train), pick(t, fold.train))
      metric(pick(t, fold.test), fitted.predict(pick(x, fold.test)))
    }

  private def pick[A](values: Vector[A], positions: Vector[Int]): Vector[A] =
    positions.map(values)
