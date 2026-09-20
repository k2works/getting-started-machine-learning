package machinelearning.chapter12

import machinelearning.chapter07.{Matrix, RegressionMetrics}

/** 正則化の強さ 1 つ分の実験結果。case class なので作ったあとで書き換えられない。
  *
  * @param alpha
  *   正則化の強さ
  * @param trainScore
  *   訓練データの決定係数
  * @param validationScore
  *   検証データの決定係数
  * @param coefficientAbsSum
  *   係数の絶対値の合計
  */
case class Experiment(
    alpha: Double,
    trainScore: Double,
    validationScore: Double,
    coefficientAbsSum: Double
)

/** 正則化の強さごとに実験し、検証データでモデルを選ぶ。 */
object ModelSelection:

  /** alpha ごとにリッジ回帰を訓練データで学習し、訓練データと検証データの決定係数を記録する。 */
  def runRidgeExperiments(
      xTrain: Matrix,
      tTrain: Vector[Double],
      xValid: Matrix,
      tValid: Vector[Double],
      alphas: Vector[Double]
  ): Vector[Experiment] =
    alphas.map { alpha =>
      val model = Ridge.fit(xTrain, tTrain, alpha)
      Experiment(
        alpha,
        RegressionMetrics.r2Score(tTrain, model.predict(xTrain)),
        RegressionMetrics.r2Score(tValid, model.predict(xValid)),
        model.coefficientAbsSum
      )
    }

  /** 検証データの決定係数が最も高い実験。同じ値なら先の実験を選ぶ。 */
  def bestExperiment(experiments: Vector[Experiment]): Experiment =
    require(experiments.nonEmpty, "実験結果が 1 件もありません")
    experiments.reduceLeft((best, next) =>
      if next.validationScore > best.validationScore then next else best
    )

  /** 係数がちょうど 0 になった特徴量の名前を、列の順に返す。 */
  def zeroCoefficientNames(
      coefficients: Vector[Double],
      featureNames: Vector[String]
  ): Vector[String] =
    require(coefficients.size == featureNames.size, "係数と特徴量名の数が違います")
    coefficients.lazyZip(featureNames).collect { case (0.0, name) => name }.toVector
