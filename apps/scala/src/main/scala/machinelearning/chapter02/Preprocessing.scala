package machinelearning.chapter02

import java.nio.file.Path
import java.util.Random

/** 補完が済んだ 1 行分の特徴量。欠損値を持てないので、補完する前の行をモデルに渡すことはできない。 値は Vector なので、case class
  * の等価判定がそのまま値の比較になる（Java・C# では配列を包む工夫が要る）。
  */
case class Features(columns: Vector[String], values: Vector[Double]):
  require(columns.size == values.size, "列名と値の数が違います")

  /** 列名で値を読む。 */
  def value(column: String): Double =
    val index = columns.indexOf(column)
    if index < 0 then throw IllegalArgumentException(s"列がありません: $column") else values(index)

/** 訓練データとテストデータ。X は特徴量の型、T は正解ラベルの型。 */
case class TrainTestSplit[X, T](
    xTrain: Vector[X],
    xTest: Vector[X],
    tTrain: Vector[T],
    tTest: Vector[T]
)

/** アヤメのデータの前処理。 */
object Preprocessing:

  /** 正解ラベルの列 */
  val Target = "種類"

  /** 欠損値を除いて、列ごとの平均値を求める。 */
  def columnMeans(rows: Vector[Row], columns: Vector[String]): Map[String, Double] =
    columns.map { column =>
      val values = rows.flatMap(_.number(column))
      column -> values.sum / values.size
    }.toMap

  /** 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変更しない。 */
  def fillMissing(
      rows: Vector[Row],
      columns: Vector[String],
      values: Map[String, Double]
  ): Vector[Features] =
    rows.map { row =>
      Features(
        columns,
        columns.map(column =>
          row
            .number(column)
            .getOrElse(
              values.getOrElse(column, throw IllegalArgumentException(s"補完する値がありません: $column"))
            )
        )
      )
    }

  /** 正解ラベルの列を取り出し、残りの列を特徴量の列にする。 */
  def splitFeaturesAndTarget(
      table: Table,
      target: String
  ): (Vector[String], Vector[Row], Vector[String]) =
    (table.columns.filterNot(_ == target), table.rows, table.rows.map(_.text(target)))

  /** シードを使って Fisher-Yates のシャッフルで並べ替える。Java 版（Collections.shuffle）と同じ
    * 乱数・同じ手順なので、同じシードなら同じ並びになる。元のリストは変更しない。
    */
  def shuffle[A](items: Vector[A], seed: Long): Vector[A] =
    val random = Random(seed)
    val array = items.toArray[Any]
    for i <- array.length - 1 to 1 by -1 do
      val j = random.nextInt(i + 1)
      val tmp = array(i)
      array(i) = array(j)
      array(j) = tmp
    array.toVector.map(_.asInstanceOf[A])

  /** 並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。 */
  def splitTrainTest[X, T](
      x: Vector[X],
      t: Vector[T],
      testSize: Double,
      seed: Long
  ): TrainTestSplit[X, T] =
    require(x.size == t.size, "特徴量と正解ラベルの件数が違います")
    val pairs = shuffle(x.zip(t), seed)
    val trainCount = pairs.size - math.ceil(pairs.size * testSize).toInt
    val (train, test) = pairs.splitAt(trainCount)
    TrainTestSplit(train.map(_._1), test.map(_._1), train.map(_._2), test.map(_._2))

  /** iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。 */
  def prepareIris(csvFile: Path, testSize: Double, seed: Long): TrainTestSplit[Features, String] =
    val (columns, rows, target) = splitFeaturesAndTarget(Table.load(csvFile), Target)
    val split = splitTrainTest(rows, target, testSize, seed)
    val means = columnMeans(split.xTrain, columns)
    TrainTestSplit(
      fillMissing(split.xTrain, columns, means),
      fillMissing(split.xTest, columns, means),
      split.tTrain,
      split.tTest
    )
