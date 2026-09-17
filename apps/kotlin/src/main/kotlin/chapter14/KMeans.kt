package chapter14

import kotlin.random.Random

typealias Point = List<Double>

fun squaredDistance(
    a: Point,
    b: Point,
): Double = a.zip(b).sumOf { (x, y) -> (x - y) * (x - y) }

fun assignClusters(
    points: List<Point>,
    centers: List<Point>,
): List<Int> = points.map { point -> centers.indices.minBy { squaredDistance(point, centers[it]) } }

fun updateCenters(
    points: List<Point>,
    labels: List<Int>,
    previousCenters: List<Point>,
): List<Point> =
    previousCenters.mapIndexed { k, previous ->
        val members = points.filterIndexed { i, _ -> labels[i] == k }
        if (members.isEmpty()) previous else previous.indices.map { j -> members.map { it[j] }.average() }
    }

fun sumOfSquaredErrors(
    points: List<Point>,
    labels: List<Int>,
    centers: List<Point>,
): Double = points.indices.sumOf { squaredDistance(points[it], centers[labels[it]]) }

data class KMeansResult(
    val labels: List<Int>,
    val centers: List<Point>,
    val sse: Double,
)

fun kmeans(
    points: List<Point>,
    initialCenters: List<Point>,
    maxIterations: Int = 300,
): KMeansResult {
    val centers =
        generateSequence(initialCenters) { centers -> updateCenters(points, assignClusters(points, centers), centers) }
            .zipWithNext()
            .withIndex()
            .first { (iteration, step) -> step.first == step.second || iteration + 1 == maxIterations }
            .value
            .second
    val labels = assignClusters(points, centers)
    return KMeansResult(labels = labels, centers = centers, sse = sumOfSquaredErrors(points, labels, centers))
}

fun chooseInitialCenters(
    points: List<Point>,
    nClusters: Int,
    seed: Int,
): List<Point> =
    points.indices
        .shuffled(Random(seed))
        .take(nClusters)
        .map { points[it] }

fun bestKMeans(
    points: List<Point>,
    initialCenterCandidates: List<List<Point>>,
): KMeansResult = initialCenterCandidates.map { kmeans(points, it) }.minBy { it.sse }

fun kmeansWithRestarts(
    points: List<Point>,
    nClusters: Int,
    seed: Int,
    nInit: Int = 10,
): KMeansResult = bestKMeans(points, (0 until nInit).map { chooseInitialCenters(points, nClusters, seed + it) })

fun sseByClusterCount(
    points: List<Point>,
    clusterCounts: List<Int>,
    seed: Int,
    nInit: Int = 10,
): Map<Int, Double> = clusterCounts.associateWith { n -> kmeansWithRestarts(points, n, seed, nInit).sse }
