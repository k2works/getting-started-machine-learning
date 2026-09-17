package chapter11

import dataset.dataDir
import java.io.File
import java.util.Locale

private fun fourDecimals(value: Double): String = "%.4f".format(Locale.ROOT, value)

private fun twoDecimals(value: Double): String = "%.2f".format(Locale.ROOT, value)

fun main() {
    println("Survived（決定木、$N_SPLITS 分割交差検証の平均）")
    for ((name, score) in evaluateSurvived(File(dataDir(), "Survived.csv"))) {
        println("  $name: ${fourDecimals(score)}")
    }
    println("cinema（線形回帰、$N_SPLITS 分割交差検証の平均）")
    for ((name, score) in evaluateCinema(File(dataDir(), "cinema.csv"))) {
        println("  $name: ${twoDecimals(score)}")
    }
}
