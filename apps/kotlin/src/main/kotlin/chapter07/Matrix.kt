package chapter07

import kotlin.math.abs

data class Matrix(
    val rows: List<List<Double>>,
) {
    val columns: List<List<Double>>
        get() = rows.first().indices.map { j -> rows.map { it[j] } }

    operator fun times(other: Matrix): Matrix {
        require(columns.size == other.rows.size) { "左の行列の列数 ${columns.size} と右の行列の行数 ${other.rows.size} が違います" }
        return Matrix(rows.map { row -> other.columns.map { column -> row.zip(column).sumOf { (a, b) -> a * b } } })
    }

    fun transpose(): Matrix = Matrix(columns)

    fun solve(b: Matrix): Matrix {
        val n = rows.size
        val augmented = rows.mapIndexed { i, row -> (row + b.rows[i]).toMutableList() }.toMutableList()
        for (pivot in 0 until n) {
            val largest = (pivot until n).maxBy { abs(augmented[it][pivot]) }
            augmented[pivot] = augmented[largest].also { augmented[largest] = augmented[pivot] }
            for (i in pivot + 1 until n) {
                val factor = augmented[i][pivot] / augmented[pivot][pivot]
                for (j in pivot..n) augmented[i][j] -= factor * augmented[pivot][j]
            }
        }
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            val known = (i + 1 until n).sumOf { j -> augmented[i][j] * x[j] }
            x[i] = (augmented[i][n] - known) / augmented[i][i]
        }
        return Matrix(x.map { listOf(it) })
    }
}
