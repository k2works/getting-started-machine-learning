package machinelearning.chapter12

import java.nio.file.Paths
import java.util.Locale
import java.util.logging.{Level, Logger}
import machinelearning.chapter02.Table
import machinelearning.chapter07.RegressionMetrics
import machinelearning.dataset.DataDir
import org.tribuo.regression.slm.ElasticNetCDTrainer

/** 正則化の強さごとにリッジ回帰を学習し、検証データで選んだモデルとラッソ回帰の結果を表示する。 */
object Main:
  private val TestSize = 0.3
  private val ValidationSize = 0.3
  private val Seed = 0L
  private val Alphas = Vector(0.0, 0.1, 1.0, 10.0, 100.0)
  private val LassoAlpha = 0.5

  // Tribuo の座標降下法が収束のたびに出す INFO のログを表示しない。
  // Logger は弱い参照で管理されるので、設定した Logger を val で持ち続ける
  private val TribuoLogger = Logger.getLogger(classOf[ElasticNetCDTrainer].getName)

  def run(print: String => Unit): Unit =
    TribuoLogger.setLevel(Level.WARNING)
    val csvFile = Paths.get(DataDir.current(), "Boston.csv")
    val table = Table.load(csvFile)
    val kept =
      Boston.removeOutliers(table, Boston.OutlierColumns, Boston.OutlierThreshold).rows.size
    val data = Boston.prepare(csvFile, TestSize, ValidationSize, Seed)
    print(s"データ件数: $kept（外れ値 ${table.rows.size - kept} 件を除外）")
    print(
      s"訓練データ: ${data.tTrain.size} 件, 検証データ: ${data.tValid.size} 件, " +
        s"テストデータ: ${data.tTest.size} 件"
    )
    print(s"特徴量: ${data.featureNames.mkString(", ")}")

    val experiments =
      ModelSelection.runRidgeExperiments(data.xTrain, data.tTrain, data.xValid, data.tValid, Alphas)
    print("alpha  訓練 R²  検証 R²  係数の絶対値の合計")
    experiments.foreach(e =>
      print(
        String.format(
          Locale.ROOT,
          "%5.1f  %.4f  %.4f  %.3f",
          e.alpha,
          e.trainScore,
          e.validationScore,
          e.coefficientAbsSum
        )
      )
    )
    val best = ModelSelection.bestExperiment(experiments)
    print(s"検証データで選んだ alpha: ${best.alpha}")

    val linear = Ridge.fit(data.xTrain, data.tTrain, 0.0)
    val ridge = Ridge.fit(data.xTrain, data.tTrain, best.alpha)
    print(
      String.format(
        Locale.ROOT,
        "テストデータの決定係数: 線形回帰 %.4f, リッジ回帰 %.4f",
        RegressionMetrics.r2Score(data.tTest, linear.predict(data.xTest)),
        RegressionMetrics.r2Score(data.tTest, ridge.predict(data.xTest))
      )
    )

    val lasso = TribuoRegularization.fitLasso(data.xTrain, data.tTrain, LassoAlpha)
    val zeros = ModelSelection.zeroCoefficientNames(lasso.coefficients, data.featureNames)
    print(s"ラッソ回帰（alpha=$LassoAlpha）で係数が 0 になった特徴量: ${zeros.mkString(", ")}")
