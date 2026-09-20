package machinelearning.chapter08

import machinelearning.chapter02.Table

/** テスト用の架空の乗客。女性が生存し、男性が死亡する単純な規則にしてある。 */
object Passengers:

  /** 特徴量の列（Pclass,Sex,Age,SibSp,Parch,Fare,Embarked）の順に並べた行から表を作る。 */
  def passengers(lines: String*): Table =
    Tables.table(SurvivedData.FeatureColumns.mkString(","), lines*)

  /** 年齢や港が欠けた乗客を含む、8 人の訓練データ。 */
  def trainX: Table =
    passengers(
      "1,female,30,0,0,80,C",
      "2,female,,1,0,20,S",
      "3,female,22,0,1,9,",
      "3,female,18,0,0,8,Q",
      "1,male,45,0,0,60,S",
      "2,male,,0,0,13,S",
      "3,male,25,1,0,7,S",
      "3,male,33,0,0,8,"
    )

  def trainT: Vector[Int] = Vector(1, 1, 1, 1, 0, 0, 0, 0)

  /** 年齢が欠けた 2 人。1 人目は港も欠けている。 */
  def newPassengers: Table = passengers("2,female,,0,0,12,", "1,male,,1,1,70,C")
