package machinelearning.chapter08

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import machinelearning.chapter03.Split
import scala.jdk.CollectionConverters.*

/** 学習済みのパイプラインを、タブ区切りのテキストとして保存し、読み込む。
  *
  * Java のシリアライズは使わない。Scala の不変コレクションは serialization proxy を通して書かれるので、 読み込むクラスを ObjectInputFilter
  * で絞ると proxy の解決前に型検査が走り、ClassCastException になる。 前処理もモデルも ADT
  * なので、自分でテキストに書き出すほうが素直で、ファイルを読んで中身を確かめられる。
  */
object ModelFiles:
  import Tree.*

  /** 形式の版。読み込むときに確かめる */
  private val Version = "format\t1"

  /** パイプライン全体（前処理で求めた値とモデル）をテキストで保存する。 */
  def save(pipeline: FittedPipeline, modelFile: Path): Unit =
    val _ = Files.createDirectories(modelFile.toAbsolutePath.getParent)
    val _ = Files.writeString(modelFile, render(pipeline), StandardCharsets.UTF_8)

  /** 保存したパイプラインを読み込む。形式が違えば失敗する。 */
  def load(modelFile: Path): FittedPipeline =
    parse(Files.readAllLines(modelFile, StandardCharsets.UTF_8).asScala.toVector)

  /** パイプラインを、1 行 1 要素のテキストにする。最後の行が決定木。 */
  private[chapter08] def render(pipeline: FittedPipeline): String =
    (Vector(Version) ++ pipeline.transformers.map(render) :+ render(pipeline.model.tree))
      .mkString("", "\n", "\n")

  private def render(transformer: FittedTransformer): String =
    val fields = transformer match
      case FittedGroupMedianImputer(column, by, medians, overallMedian) =>
        Vector("group-median", column, by.mkString(","), overallMedian.toString)
          ++ medians.toVector
            .sortBy((group, _) => group.mkString(","))
            .map((group, median) => s"${group.mkString(",")}=$median")
      case FittedMostFrequentImputer(column, mostFrequent) =>
        Vector("most-frequent", column, mostFrequent)
      case FittedDummyEncoder(dummies) =>
        "dummy" +: dummies.map((column, categories) => s"$column=${categories.mkString(",")}")
    fields.mkString("\t")

  private def render(tree: Tree): String = ("tree" +: tokensOf(tree)).mkString("\t")

  /** 木を行きがけ順（節・左・右）のトークンにする。 */
  private def tokensOf(tree: Tree): Vector[String] =
    tree match
      case Leaf(label) => Vector("L", label.toString)
      case Node(Split(feature, threshold, impurity), left, right) =>
        Vector("N", feature, threshold.toString, impurity.toString)
          ++ tokensOf(left) ++ tokensOf(right)

  private[chapter08] def parse(lines: Vector[String]): FittedPipeline =
    require(lines.headOption.contains(Version), "対応していない形式のモデルです")
    val (treeLines, transformerLines) =
      lines.tail.filter(_.nonEmpty).partition(_.startsWith("tree\t"))
    require(treeLines.size == 1, "決定木の行がありません")
    FittedPipeline(
      transformerLines.map(parseTransformer),
      FittedDecisionTree(parseTree(treeLines.head))
    )

  private def parseTransformer(line: String): FittedTransformer =
    line.split("\t", -1).toVector match
      case "group-median" +: column +: by +: overallMedian +: medians =>
        FittedGroupMedianImputer(
          column,
          by.split(",", -1).toVector,
          medians.map { entry =>
            val (group, median) = splitOnce(entry, '=')
            group.split(",", -1).toVector -> median.toDouble
          }.toMap,
          overallMedian.toDouble
        )
      case Vector("most-frequent", column, mostFrequent) =>
        FittedMostFrequentImputer(column, mostFrequent)
      case "dummy" +: entries =>
        FittedDummyEncoder(entries.map { entry =>
          val (column, categories) = splitOnce(entry, '=')
          column -> (if categories.isEmpty then Vector.empty
                     else categories.split(",", -1).toVector)
        })
      case _ => throw IllegalArgumentException(s"読み取れない前処理です: $line")

  private def parseTree(line: String): Tree =
    val (tree, rest) = readTree(line.split("\t", -1).toVector.tail)
    require(rest.isEmpty, "決定木の後ろに余分なものがあります")
    tree

  /** 行きがけ順のトークンから木を 1 つ読み、残りのトークンと一緒に返す。 */
  private def readTree(tokens: Vector[String]): (Tree, Vector[String]) =
    tokens match
      case "L" +: label +: rest => (Leaf(label.toInt), rest)
      case "N" +: feature +: threshold +: impurity +: rest =>
        val (left, afterLeft) = readTree(rest)
        val (right, afterRight) = readTree(afterLeft)
        (Node(Split(feature, threshold.toDouble, impurity.toDouble), left, right), afterRight)
      case _ => throw IllegalArgumentException(s"読み取れない決定木です: ${tokens.mkString("\t")}")

  /** 最初の区切り文字で 2 つに分ける。区切り文字が無ければ失敗する。 */
  private def splitOnce(text: String, separator: Char): (String, String) =
    val index = text.indexOf(separator.toInt)
    require(index >= 0, s"'$separator' がありません: $text")
    (text.take(index), text.drop(index + 1))
