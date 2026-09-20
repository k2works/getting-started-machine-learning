package machinelearning.chapter01

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** 学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。 */
case class Person(height: Int, weight: Int, ageGroup: Int, faction: String)

/** 判定の手がかりになる特徴量。 */
case class Features(height: Int, weight: Int, ageGroup: Int)

/** きのこ派・たけのこ派の判定。 */
object KinokoTakenoko:
  private val Bom = "\uFEFF"

  /** 「20 代ならきのこ派」というルールの年代 */
  private val KinokoAgeGroup = 20

  /** BOM 付きの UTF-8 の CSV を読み込み、列名で値を取り出して人物のリストにする。 */
  def loadPeople(csvFile: Path): Vector[Person] =
    val lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8).asScala.toVector
    val header = lines.head.stripPrefix(Bom).split(",").toVector
    val index = header.zipWithIndex.toMap
    lines.tail
      .filter(_.trim.nonEmpty)
      .map { line =>
        val values = line.split(",").toVector
        Person(
          height = values(index("身長")).toInt,
          weight = values(index("体重")).toInt,
          ageGroup = values(index("年代")).toInt,
          faction = values(index("派閥"))
        )
      }

  /** 人物のリストを特徴量と正解ラベルに分ける。 */
  def splitFeaturesAndLabels(people: Vector[Person]): (Vector[Features], Vector[String]) =
    (people.map(p => Features(p.height, p.weight, p.ageGroup)), people.map(_.faction))

  /** 人間が決めたルールで派閥を判定する。 */
  def predictByRule(features: Features): String =
    if features.ageGroup == KinokoAgeGroup then "きのこ" else "たけのこ"

  /** 予測が正解ラベルと一致した割合を返す。 */
  def accuracy(predictions: Vector[String], labels: Vector[String]): Double =
    require(predictions.size == labels.size, "予測と正解ラベルの件数が違います")
    predictions.zip(labels).count(_ == _).toDouble / labels.size
