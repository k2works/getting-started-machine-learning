package chapter03

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.tribuo.Example
import org.tribuo.Model
import org.tribuo.MutableDataset
import org.tribuo.classification.Label
import org.tribuo.classification.LabelFactory
import org.tribuo.classification.dtree.CARTClassificationTrainer
import org.tribuo.classification.dtree.impurity.GiniIndex
import org.tribuo.impl.ArrayExample
import org.tribuo.provenance.SimpleDataSourceProvenance

private val labelFactory = LabelFactory()

private fun toExample(
    x: AnyFrame,
    row: Int,
    label: Label,
): Example<Label> {
    val names = x.columnNames().toTypedArray()
    val values = DoubleArray(names.size) { (x[names[it]][row] as Number).toDouble() }
    return ArrayExample(label, names, values)
}

fun toTribuoDataset(
    x: AnyFrame,
    t: List<String>,
): MutableDataset<Label> {
    val dataset = MutableDataset(SimpleDataSourceProvenance("dataframe", labelFactory), labelFactory)
    t.forEachIndexed { row, label -> dataset.add(toExample(x, row, Label(label))) }
    return dataset
}

fun trainTribuoTree(
    x: AnyFrame,
    t: List<String>,
    maxDepth: Int?,
    minChildWeight: Float,
): Model<Label> {
    val trainer = CARTClassificationTrainer(maxDepth ?: Int.MAX_VALUE, minChildWeight, 0.0f, 1.0f, GiniIndex(), 0L)
    return trainer.train(toTribuoDataset(x, t))
}

fun predictWithTribuo(
    model: Model<Label>,
    x: AnyFrame,
): List<String> = (0 until x.rowsCount()).map { row -> model.predict(toExample(x, row, LabelFactory.UNKNOWN_LABEL)).output.label }
