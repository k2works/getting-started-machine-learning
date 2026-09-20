package machinelearning.chapter09

import java.nio.file.Path
import machinelearning.chapter02.{Features, Preprocessing, Table, TrainTestSplit}

/** ボストンの住宅価格（Boston.csv）の前処理と、特徴量の組ごとの決定係数。 */
object Boston:

  /** 正解の列 */
  val Target = "PRICE"

  /** カテゴリ値の列 */
  val Category = "CRIME"

  /** CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。 */
  def prepare(csvFile: Path, testSize: Double, seed: Long): TrainTestSplit[Features, Double] =
    val table = Table.load(csvFile)
    val encoded =
      Dummies.encode(table, Category, Dummies.categories(table.rows.map(_.text(Category))))
    val (columns, rows, target) = Preprocessing.splitFeaturesAndTarget(encoded, Target)
    val split = Preprocessing.splitTrainTest(rows, target.map(_.toDouble), testSize, seed)
    val means = Preprocessing.columnMeans(split.xTrain, columns)
    TrainTestSplit(
      Preprocessing.fillMissing(split.xTrain, columns, means),
      Preprocessing.fillMissing(split.xTest, columns, means),
      split.tTrain,
      split.tTest
    )

  /** 列から多項式特徴量を作って terms の項を選び、訓練データで標準化してから線形回帰で学習し、決定係数を求める。 */
  def scoreFeatureSet(
      split: TrainTestSplit[Features, Double],
      columns: Vector[String],
      terms: Vector[String]
  ): Scores =
    val train = PolynomialFeatures.select(PolynomialFeatures.expand(split.xTrain, columns), terms)
    val test = PolynomialFeatures.select(PolynomialFeatures.expand(split.xTest, columns), terms)
    val standardizer = Standardizer.fit(train)
    val xTrain = standardizer.transform(train).map(_.values)
    val xTest = standardizer.transform(test).map(_.values)
    val model = LinearModel.fit(xTrain, split.tTrain)
    Scores(
      LinearModel.rSquared(split.tTrain, model.predict(xTrain)),
      LinearModel.rSquared(split.tTest, model.predict(xTest))
    )
