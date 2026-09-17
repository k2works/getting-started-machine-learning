package chapter01

import java.io.File

private const val BOM = '\uFEFF'

data class Person(
    val height: Int,
    val weight: Int,
    val ageGroup: Int,
    val faction: String,
)

fun loadPeople(csvFile: File): List<Person> {
    val lines = csvFile.readLines(Charsets.UTF_8)
    val header = lines.first().removePrefix(BOM.toString()).split(",")
    val index = header.withIndex().associate { (i, name) -> name to i }
    return lines.drop(1).filter { it.isNotBlank() }.map { line ->
        val values = line.split(",")
        Person(
            height = values[index.getValue("身長")].toInt(),
            weight = values[index.getValue("体重")].toInt(),
            ageGroup = values[index.getValue("年代")].toInt(),
            faction = values[index.getValue("派閥")],
        )
    }
}

data class Features(
    val height: Int,
    val weight: Int,
    val ageGroup: Int,
)

fun splitFeaturesAndLabels(people: List<Person>): Pair<List<Features>, List<String>> {
    val features = people.map { Features(it.height, it.weight, it.ageGroup) }
    val labels = people.map { it.faction }
    return features to labels
}

/** 「20 代ならきのこ派」というルールの年代 */
private const val KINOKO_AGE_GROUP = 20

fun predictByRule(features: Features): String = if (features.ageGroup == KINOKO_AGE_GROUP) "きのこ" else "たけのこ"

fun accuracy(
    predictions: List<String>,
    labels: List<String>,
): Double {
    require(predictions.size == labels.size) { "予測と正解ラベルの件数が違います" }
    val correct = predictions.zip(labels).count { (p, t) -> p == t }
    return correct.toDouble() / labels.size
}
