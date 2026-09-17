package chapter01

import dataset.dataDir
import java.io.File
import java.util.Locale

fun main() {
    val people = loadPeople(File(dataDir(), "KvsT.csv"))
    val (features, labels) = splitFeaturesAndLabels(people)
    val predictions = features.map(::predictByRule)
    println("データ件数: ${people.size}")
    println("ルールによる判定の正解率: ${"%.4f".format(Locale.ROOT, accuracy(predictions, labels))}")
}
