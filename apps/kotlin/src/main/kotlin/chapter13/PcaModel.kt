package chapter13

import chapter07.Matrix

/** 学習した主成分分析のモデル。components の 1 行が 1 つの主成分を表す */
data class PcaModel(
    val mean: List<Double>,
    val components: Matrix,
    val explainedVariance: List<Double>,
    val explainedVarianceRatio: List<Double>,
)
