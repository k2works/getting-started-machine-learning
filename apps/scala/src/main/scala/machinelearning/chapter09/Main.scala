package machinelearning.chapter09

import java.nio.file.Paths
import java.util.Locale
import machinelearning.dataset.DataDir

/** ボストンの住宅価格で特徴量エンジニアリングを試し、自転車の利用者数に天気を結合して集計する。 */
object Main:

  /** 多項式特徴量を作る元の列 */
  val Columns: Vector[String] = Vector("RM", "LSTAT", "PTRATIO")

  /** 2 乗の項 */
  val Squares: Vector[String] = Vector("RM^2", "LSTAT^2", "PTRATIO^2")

  private val TestSize = 0.3
  private val Seed = 0L

  // 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす
  private val ZeroTolerance = 1e-9

  private val ScoreDigits = 4
  private val MeanDigits = 2
  private val CountDigits = 1

  /** 特徴量の組の名前と、使う項。 */
  def featureSets(): Vector[(String, Vector[String])] =
    Vector(
      "元の特徴量" -> Columns,
      "2 乗の項を追加" -> (Columns ++ Squares),
      "交互作用の項も追加" -> (Columns ++ PolynomialFeatures.pairsWithReplacement(Columns).map(_.name))
    )

  def run(print: String => Unit): Unit =
    val dataDir = DataDir.current()
    val split = Boston.prepare(Paths.get(dataDir, "Boston.csv"), TestSize, Seed)
    print(s"訓練データ: ${split.xTrain.size} 件, テストデータ: ${split.xTest.size} 件")
    print("特徴量の列: " + split.xTrain.head.columns.mkString(", "))

    // 標準化した訓練データの平均と標準偏差を、もう一度 fit して確かめる
    val check = Standardizer.fit(Standardizer.fit(split.xTrain).transform(split.xTrain))
    print(
      s"標準化した訓練データの RM: 平均 ${format(check.means("RM"), MeanDigits)}" +
        s", 標準偏差 ${format(check.stds("RM"), MeanDigits)}"
    )

    print("決定係数:")
    featureSets().foreach { (name, terms) =>
      print(
        s"  $name（${terms.size} 列）: ${formatScores(Boston.scoreFeatureSet(split, Columns, terms))}"
      )
    }

    print(s"訓練データの PRICE の外れ値: ${Outliers.iqrOutliers(split.tTrain).count(identity)} 件")
    val removed =
      Boston.scoreFeatureSet(Outliers.removeTargetOutliers(split), Columns, Columns ++ Squares)
    print(s"  外れ値を除いて 2 乗の項を追加: ${formatScores(removed)}")

    val joined = BikeWeather.joinWeather(
      BikeWeather.loadBike(Paths.get(dataDir, "bike.tsv")),
      BikeWeather.loadWeather(Paths.get(dataDir, "weather.csv"))
    )
    print(
      "天気ごとの平均利用者数: " + BikeWeather
        .meanCountByWeather(joined)
        .map((weather, mean) => s"$weather=${format(mean, CountDigits)}")
        .mkString(", ")
    )

  private def format(value: Double, digits: Int): String =
    String.format(
      Locale.ROOT,
      s"%.${digits}f",
      if math.abs(value) < ZeroTolerance then 0.0 else value
    )

  private def formatScores(scores: Scores): String =
    s"訓練 ${format(scores.train, ScoreDigits)}, テスト ${format(scores.test, ScoreDigits)}"
