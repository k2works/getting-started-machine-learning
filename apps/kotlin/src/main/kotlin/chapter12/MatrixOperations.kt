package chapter12

import chapter07.Matrix

operator fun Matrix.plus(other: Matrix): Matrix {
    require(rows.size == other.rows.size && columns.size == other.columns.size) {
        "${rows.size} 行 ${columns.size} 列の行列と ${other.rows.size} 行 ${other.columns.size} 列の行列は足せません"
    }
    return Matrix(rows.zip(other.rows) { a, b -> a.zip(b) { x, y -> x + y } })
}

operator fun Double.times(matrix: Matrix): Matrix = Matrix(matrix.rows.map { row -> row.map { this * it } })

fun identity(size: Int): Matrix = Matrix(List(size) { i -> List(size) { j -> if (i == j) 1.0 else 0.0 } })
