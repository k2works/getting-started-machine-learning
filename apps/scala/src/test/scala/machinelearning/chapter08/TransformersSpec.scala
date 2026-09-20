package machinelearning.chapter08

import machinelearning.chapter08.Tables.{numbers, table, texts}
import org.scalatest.funsuite.AnyFunSuite

class TransformersSpec extends AnyFunSuite:
  private val ageImputer = GroupMedianImputer("Age", Vector("Pclass", "Sex"))
  private val embarkedImputer = MostFrequentImputer("Embarked")

  test("GroupMedianImputer は同じグループの中央値で欠損値を補完する") {
    val x = table("Pclass,Sex,Age", "1,female,20", "1,female,30", "1,female,70", "1,female,")

    val filled = ageImputer.fit(x).transform(x)

    assert(numbers(filled, "Age") === Vector(20.0, 30.0, 70.0, 30.0))
  }

  test("GroupMedianImputer はグループごとに異なる中央値で補完する") {
    val x = table(
      "Pclass,Sex,Age",
      "1,female,40",
      "1,female,50",
      "1,female,",
      "3,male,10",
      "3,male,20",
      "3,male,"
    )

    val filled = ageImputer.fit(x).transform(x)

    assert(numbers(filled, "Age") === Vector(40.0, 50.0, 45.0, 10.0, 20.0, 15.0))
  }

  test("GroupMedianImputer は訓練データで求めた中央値を別のデータの補完に使う") {
    val train = table("Pclass,Sex,Age", "2,male,30", "2,male,34")
    val other = table("Pclass,Sex,Age", "2,male,")

    val filled = ageImputer.fit(train).transform(other)

    assert(numbers(filled, "Age") === Vector(32.0))
  }

  test("GroupMedianImputer は訓練データに無いグループを全体の中央値で補完する") {
    val train = table("Pclass,Sex,Age", "1,female,30", "1,female,40", "3,male,20")
    val other = table("Pclass,Sex,Age", "2,female,")

    val filled = ageImputer.fit(train).transform(other)

    assert(numbers(filled, "Age") === Vector(30.0))
  }

  test("GroupMedianImputer は年齢がすべて欠けたグループを全体の中央値で補完する") {
    val x = table("Pclass,Sex,Age", "1,female,30", "1,female,40", "2,female,")

    val filled = ageImputer.fit(x).transform(x)

    assert(numbers(filled, "Age") === Vector(30.0, 40.0, 35.0))
  }

  test("GroupMedianImputer は元の表を変更しない") {
    val x = table("Pclass,Sex,Age", "1,male,30", "1,male,")

    val _ = ageImputer.fit(x).transform(x)

    assert(x.rows(1).isMissing("Age"))
  }

  test("MostFrequentImputer は訓練データで最も多い値で欠損値を補完する") {
    val train = table("Embarked", "S", "C", "S", "")
    val other = table("Embarked", "", "Q")

    val filled = embarkedImputer.fit(train).transform(other)

    assert(texts(filled, "Embarked") === Vector("S", "Q"))
  }

  test("DummyEncoder は 2 値のカテゴリを、最初のカテゴリを除いた 0 と 1 の列にする") {
    val x = table("Pclass,Sex", "1,female", "3,male", "2,male")

    val encoded = DummyEncoder(Vector("Sex")).fit(x).transform(x)

    assert(encoded.columns === Vector("Pclass", "Sex_male"))
    assert(numbers(encoded, "Sex_male") === Vector(0.0, 1.0, 1.0))
  }

  test("DummyEncoder は別のデータにも訓練データと同じ列を作る") {
    val train = table("Embarked", "C", "Q", "S")
    val other = table("Embarked", "S", "S")

    val encoded = DummyEncoder(Vector("Embarked")).fit(train).transform(other)

    assert(encoded.columns === Vector("Embarked_Q", "Embarked_S"))
    assert(numbers(encoded, "Embarked_Q") === Vector(0.0, 0.0))
    assert(numbers(encoded, "Embarked_S") === Vector(1.0, 1.0))
  }
