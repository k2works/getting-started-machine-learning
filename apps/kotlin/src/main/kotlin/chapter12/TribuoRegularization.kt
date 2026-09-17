package chapter12

import chapter07.Matrix
import org.tribuo.MutableDataset
import org.tribuo.impl.ArrayExample
import org.tribuo.provenance.SimpleDataSourceProvenance
import org.tribuo.regression.RegressionFactory
import org.tribuo.regression.Regressor
import org.tribuo.regression.slm.ElasticNetCDTrainer
import org.tribuo.regression.slm.SparseLinearModel

/** ElasticNetCDTrainer が受け付ける l1Ratio の下限。0（純粋なリッジ回帰）は受け付けない */
private const val MIN_L1_RATIO = 1e-12
private const val TOLERANCE = 1e-10
private const val MAX_ITERATIONS = 100_000
private const val SEED = 0L

private val regressionFactory = RegressionFactory()

private fun featureNames(x: Matrix): List<String> = x.columns.indices.map { "x$it" }

private fun toRegressionDataset(
    x: Matrix,
    t: List<Double>,
): MutableDataset<Regressor> {
    val names = featureNames(x).toTypedArray()
    val dataset = MutableDataset(SimpleDataSourceProvenance("matrix", regressionFactory), regressionFactory)
    x.rows.zip(t).forEach { (row, value) -> dataset.add(ArrayExample(Regressor("t", value), names, row.toDoubleArray())) }
    return dataset
}

fun fitElasticNet(
    x: Matrix,
    t: List<Double>,
    alpha: Double,
    l1Ratio: Double,
): RegularizedModel {
    val trainer = ElasticNetCDTrainer(alpha, l1Ratio, TOLERANCE, MAX_ITERATIONS, false, SEED)
    val model = trainer.train(toRegressionDataset(x, t)) as SparseLinearModel
    val weights = model.weights.values.single()
    val coefficients = featureNames(x).map { weights.get(model.featureIDMap.get(it).id) }
    // Tribuo は特徴量の平均を引いてから学習するので、切片は平均値から求める
    val intercept =
        t.average() -
            x.columns
                .map { it.average() }
                .zip(coefficients)
                .sumOf { (mean, coefficient) -> mean * coefficient }
    return RegularizedModel(coefficients = coefficients, intercept = intercept)
}

fun fitLasso(
    x: Matrix,
    t: List<Double>,
    alpha: Double,
): RegularizedModel = fitElasticNet(x, t, alpha, l1Ratio = 1.0)

fun fitRidgeWithTribuo(
    x: Matrix,
    t: List<Double>,
    alpha: Double,
): RegularizedModel = fitElasticNet(x, t, alpha / t.size, MIN_L1_RATIO)
