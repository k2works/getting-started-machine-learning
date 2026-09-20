package machinelearning.chapter14

import java.util
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite
import org.tribuo.clustering.kmeans.KMeansTrainer

class TribuoKMeansSpec extends AnyFunSuite:

  test("KMeansTrainer の初期化方法は RANDOM と PLUSPLUS だけで初期中心を渡すコンストラクターは無い") {
    assert(
      KMeansTrainer.Initialisation.values.toVector.map(_.name) === Vector("RANDOM", "PLUSPLUS")
    )
    assert(
      !classOf[KMeansTrainer].getConstructors.exists(
        _.getParameterTypes.exists(t =>
          t.isArray || classOf[util.Collection[?]].isAssignableFrom(t)
        )
      )
    )
  }

  test("はっきり分かれた 2 グループなら Tribuo の SSE も自作と同じになる") {
    val points: Vector[Vector[Double]] =
      Vector(Vector(0, 0), Vector(0, 1), Vector(10, 10), Vector(10, 11))

    assert(TribuoKMeans.sse(points, 2, 0L) === 1.0 +- 1e-9)
  }

  test("Tribuo でもシードを変えて繰り返し最小の SSE を使える") {
    val points =
      Vector(Vector(0.0), Vector(1.0), Vector(10.0), Vector(11.0), Vector(20.0), Vector(21.0))

    assert(TribuoKMeans.bestSse(points, 3, 0L, 10) === 1.5 +- 1e-9)
  }
