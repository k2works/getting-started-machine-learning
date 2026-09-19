package chapter08;

import chapter02.Table;
import java.util.List;

/** テスト用の架空の乗客。女性が生存し、男性が死亡する単純な規則にしてある。 */
final class Passengers {
  private Passengers() {}

  /** 特徴量の列（Pclass,Sex,Age,SibSp,Parch,Fare,Embarked）の順に並べた行から表を作る。 */
  static Table passengers(String... lines) {
    return Tables.table(String.join(",", SurvivedData.FEATURES), lines);
  }

  /** 年齢や港が欠けた乗客を含む、8 人の訓練データ。 */
  static Table trainX() {
    return passengers(
        "1,female,30,0,0,80,C",
        "2,female,,1,0,20,S",
        "3,female,22,0,1,9,",
        "3,female,18,0,0,8,Q",
        "1,male,45,0,0,60,S",
        "2,male,,0,0,13,S",
        "3,male,25,1,0,7,S",
        "3,male,33,0,0,8,");
  }

  static List<Integer> trainT() {
    return List.of(1, 1, 1, 1, 0, 0, 0, 0);
  }

  /** 年齢が欠けた 2 人。1 人目は港も欠けている。 */
  static Table newPassengers() {
    return passengers("2,female,,0,0,12,", "1,male,,1,1,70,C");
  }
}
