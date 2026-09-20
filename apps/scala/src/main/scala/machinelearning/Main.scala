package machinelearning

/** 章を選んで実行する入口。使い方: sbt "run chapter01" */
object Main:
  private val chapters: Map[String, (String => Unit) => Unit] = Map(
    "chapter01" -> machinelearning.chapter01.Main.run,
    "chapter02" -> machinelearning.chapter02.Main.run,
    "chapter03" -> machinelearning.chapter03.Main.run,
    "chapter07" -> machinelearning.chapter07.Main.run,
    "chapter08" -> machinelearning.chapter08.Main.run,
    "chapter09" -> machinelearning.chapter09.Main.run,
    "chapter10" -> machinelearning.chapter10.Main.run
  )

  def main(args: Array[String]): Unit =
    args.toList match
      case name :: Nil if chapters.contains(name) => chapters(name)(println)
      case _ =>
        Console.err.println(s"使い方: sbt \"run (${chapters.keys.toSeq.sorted.mkString(" | ")})\"")
