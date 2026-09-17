package chapter07

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.tribuo.Example
import org.tribuo.Model
import org.tribuo.MutableDataset
import org.tribuo.impl.ArrayExample
import org.tribuo.provenance.SimpleDataSourceProvenance
import org.tribuo.regression.RegressionFactory
import org.tribuo.regression.Regressor
import org.tribuo.regression.slm.SLMTrainer

private val regressionFactory = RegressionFactory()

private fun toExample(
    x: AnyFrame,
    row: Int,
    output: Regressor,
): Example<Regressor> {
    val names = x.columnNames().toTypedArray()
    val values = DoubleArray(names.size) { (x[names[it]][row] as Number).toDouble() }
    return ArrayExample(output, names, values)
}

fun toRegressionDataset(
    x: AnyFrame,
    t: List<Double>,
): MutableDataset<Regressor> {
    val dataset = MutableDataset(SimpleDataSourceProvenance("dataframe", regressionFactory), regressionFactory)
    t.forEachIndexed { row, value -> dataset.add(toExample(x, row, Regressor(TARGET, value))) }
    return dataset
}

fun trainTribuoLinearRegression(
    x: AnyFrame,
    t: List<Double>,
): Model<Regressor> = SLMTrainer(true).train(toRegressionDataset(x, t))

fun predictWithTribuo(
    model: Model<Regressor>,
    x: AnyFrame,
): List<Double> =
    (0 until x.rowsCount()).map { row ->
        model
            .predict(toExample(x, row, RegressionFactory.UNKNOWN_REGRESSOR))
            .output.values
            .single()
    }
