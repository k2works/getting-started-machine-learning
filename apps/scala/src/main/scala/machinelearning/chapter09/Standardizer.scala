package machinelearning.chapter09

import machinelearning.chapter02.Features
import scala.collection.immutable.SeqMap

/** 列ごとの平均と標準偏差（件数で割る標準偏差）で、平均 0・標準偏差 1 にそろえる。
  *
  * 訓練データで [[Standardizer.fit]] し、同じ平均と標準偏差で訓練データとテストデータの両方を [[transform]] する。
  *
  * @param means
  *   列名ごとの平均。SeqMap なので列の順を保つ
  * @param stds
  *   列名ごとの標準偏差。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする
  */
case class Standardizer(means: SeqMap[String, Double], stds: SeqMap[String, Double]):
  require(means.keySet == stds.keySet, "平均と標準偏差の列が違います")

  /** 1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。 */
  def transform(features: Features): Features =
    Features(
      features.columns,
      features.columns.zip(features.values).map { (column, value) =>
        means.get(column).fold(value)(mean => (value - mean) / stds(column))
      }
    )

  /** 特徴量のリストを標準化する。 */
  def transform(x: Vector[Features]): Vector[Features] = x.map(transform)

object Standardizer:

  /** 特徴量のすべての列について、平均と標準偏差を求める。 */
  def fit(x: Vector[Features]): Standardizer =
    require(x.nonEmpty, "特徴量が 1 件もありません")
    val stats = x.head.columns.map { column =>
      val values = x.map(_.value(column))
      val mean = values.sum / values.size
      val std = math.sqrt(values.map(value => (value - mean) * (value - mean)).sum / values.size)
      (column, mean, if std == 0 then 1.0 else std)
    }
    Standardizer(
      SeqMap.from(stats.map((column, mean, _) => column -> mean)),
      SeqMap.from(stats.map((column, _, std) => column -> std))
    )
