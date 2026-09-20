package machinelearning.chapter08

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import machinelearning.chapter08.Passengers.{newPassengers, trainT, trainX}
import org.scalatest.funsuite.AnyFunSuite

class ModelFilesSpec extends AnyFunSuite:

  private def withTempDirectory(body: Path => Any): Unit =
    val directory = Files.createTempDirectory("chapter08")
    try
      val _ = body(directory)
    finally Files.walk(directory).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)

  private def fitted(): FittedPipeline =
    Pipeline.build(Some(3), ClassWeight.Unweighted).fit(trainX, trainT)

  test("保存したパイプラインを読み込むと同じ予測をする") {
    withTempDirectory { directory =>
      val modelFile = directory.resolve("model/survived.model")

      ModelFiles.save(fitted(), modelFile)
      val loaded = ModelFiles.load(modelFile)

      assert(loaded.predict(newPassengers) === Vector(1, 0))
    }
  }

  test("保存したパイプラインを読み込むと同じ値になる") {
    withTempDirectory { directory =>
      val pipeline = fitted()
      val modelFile = directory.resolve("survived.model")

      ModelFiles.save(pipeline, modelFile)

      assert(ModelFiles.load(modelFile) === pipeline)
    }
  }

  test("保存したファイルは読める形式で、前処理と決定木が 1 行ずつ並ぶ") {
    withTempDirectory { directory =>
      val modelFile = directory.resolve("survived.model")

      ModelFiles.save(fitted(), modelFile)
      val lines = Files.readString(modelFile, StandardCharsets.UTF_8).linesIterator.toVector

      assert(lines.head === "format\t1")
      assert(
        lines.map(_.takeWhile(_ != '\t'))
          === Vector("format", "group-median", "most-frequent", "dummy", "tree")
      )
    }
  }

  test("対応していない形式のファイルは読み込まない") {
    withTempDirectory { directory =>
      val modelFile = directory.resolve("unknown.model")
      val _ = Files.writeString(modelFile, "format\t99\n", StandardCharsets.UTF_8)

      assertThrows[IllegalArgumentException](ModelFiles.load(modelFile))
    }
  }

  test("決定木の行が欠けたファイルは読み込まない") {
    withTempDirectory { directory =>
      val modelFile = directory.resolve("broken.model")
      val _ =
        Files.writeString(
          modelFile,
          "format\t1\nmost-frequent\tEmbarked\tS\n",
          StandardCharsets.UTF_8
        )

      assertThrows[IllegalArgumentException](ModelFiles.load(modelFile))
    }
  }
