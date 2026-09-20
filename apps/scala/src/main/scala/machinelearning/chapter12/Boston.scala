package machinelearning.chapter12

import java.nio.file.Path
import machinelearning.chapter02.{Features, Preprocessing, Row, Table}
import machinelearning.chapter07.Matrix
import machinelearning.chapter09.{PolynomialFeatures, Standardizer}

/** 訓練・検証・テストの特徴量（多項式特徴量にした行列）と正解。
  *
  * @param xTrain
  *   訓練データの特徴量
  * @param tTrain
  *   訓練データの正解
  * @param xValid
  *   検証データの特徴量
  * @param tValid
  *   検証データの正解
  * @param xTest
  *   テストデータの特徴量
  * @param tTest
  *   テストデータの正解
  * @param featureNames
  *   特徴量の列名
  */
case class BostonDataset(
    xTrain: Matrix,
    tTrain: Vector[Double],
    xValid: Matrix,
    tValid: Vector[Double],
    xTest: Matrix,
    tTest: Vector[Double],
    featureNames: Vector[String]
)

/** ボストンの住宅価格（Boston.csv）を、外れ値を除いて訓練・検証・テストの 3 つに分ける。 */
object Boston:

  /** 特徴量の列 */
  val FeatureColumns: Vector[String] = Vector("RM", "PTRATIO", "LSTAT")

  /** 正解の列 */
  val Target = "PRICE"

  /** z スコアの絶対値がこの値を超える行を外れ値とする */
  val OutlierThreshold = 3.0

  /** 外れ値を調べる列（特徴量と正解） */
  val OutlierColumns: Vector[String] = FeatureColumns :+ Target

  /** 列ごとの z スコア（標準偏差は件数 n - 1 で割る標本標準偏差）の絶対値が threshold を超える値を 1 つでも持つ行を除く。 */
  def removeOutliers(table: Table, columns: Vector[String], threshold: Double): Table =
    val stats = columns.map { column =>
      val values = table.rows.map(number(_, column))
      val mean = values.sum / values.size
      val std =
        math.sqrt(values.map(value => (value - mean) * (value - mean)).sum / (values.size - 1))
      (column, mean, std)
    }
    table.copy(rows =
      table.rows.filterNot(row =>
        stats.exists((column, mean, std) =>
          math.abs((number(row, column) - mean) / std) > threshold
        )
      )
    )

  /** 外れ値を除き、全体を訓練用とテスト用に、訓練用をさらに訓練データと検証データに分ける。
    *
    * 標準化の平均と標準偏差は訓練データだけから求め、3 つとも同じ値で変換する。第 9 章の Standardizer と PolynomialFeatures をそのまま使う。
    */
  def prepare(
      csvFile: Path,
      testSize: Double,
      validationSize: Double,
      seed: Long
  ): BostonDataset =
    val table = removeOutliers(Table.load(csvFile), OutlierColumns, OutlierThreshold)
    val x = table.rows.map(row => Features(FeatureColumns, FeatureColumns.map(number(row, _))))
    val t = table.rows.map(number(_, Target))
    val outer = Preprocessing.splitTrainTest(x, t, testSize, seed)
    val inner = Preprocessing.splitTrainTest(outer.xTrain, outer.tTrain, validationSize, seed)
    val standardizer = Standardizer.fit(inner.xTrain)
    def transform(rows: Vector[Features]): Vector[Features] =
      PolynomialFeatures.expand(standardizer.transform(rows), FeatureColumns)
    val train = transform(inner.xTrain)
    BostonDataset(
      toMatrix(train),
      inner.tTrain,
      toMatrix(transform(inner.xTest)),
      inner.tTest,
      toMatrix(transform(outer.xTest)),
      outer.tTest,
      train.head.columns
    )

  private def toMatrix(x: Vector[Features]): Matrix = Matrix(x.map(_.values))

  private def number(row: Row, column: String): Double =
    row.number(column).getOrElse(throw IllegalArgumentException(s"欠損値です: $column"))
