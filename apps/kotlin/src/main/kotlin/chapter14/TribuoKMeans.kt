package chapter14

import org.tribuo.MutableDataset
import org.tribuo.clustering.ClusterID
import org.tribuo.clustering.ClusteringFactory
import org.tribuo.clustering.kmeans.KMeansModel
import org.tribuo.clustering.kmeans.KMeansTrainer
import org.tribuo.impl.ArrayExample
import org.tribuo.math.distance.L2Distance
import org.tribuo.provenance.SimpleDataSourceProvenance

private const val MAX_ITERATIONS = 300
private val clusteringFactory = ClusteringFactory()

// Tribuo は特徴量を名前の順に並べるので、列の順と名前の順が一致する名前にする
private fun featureNames(dimensions: Int): Array<String> = Array(dimensions) { "x" + it.toString().padStart(2, '0') }

fun toTribuoDataset(points: List<Point>): MutableDataset<ClusterID> {
    val dataset = MutableDataset(SimpleDataSourceProvenance("points", clusteringFactory), clusteringFactory)
    val names = featureNames(points.first().size)
    points.forEach { dataset.add(ArrayExample(ClusteringFactory.UNASSIGNED_CLUSTER_ID, names, it.toDoubleArray())) }
    return dataset
}

fun trainTribuoKMeans(
    points: List<Point>,
    nClusters: Int,
    seed: Long,
    initialisation: KMeansTrainer.Initialisation = KMeansTrainer.Initialisation.PLUSPLUS,
): KMeansModel {
    val trainer = KMeansTrainer(nClusters, MAX_ITERATIONS, L2Distance(), initialisation, 1, seed)
    return trainer.train(toTribuoDataset(points))
}

fun tribuoCenters(model: KMeansModel): List<Point> = model.centroidVectors.map { vector -> List(vector.size()) { vector.get(it) } }

fun tribuoKMeansSse(
    points: List<Point>,
    nClusters: Int,
    seed: Long,
    initialisation: KMeansTrainer.Initialisation = KMeansTrainer.Initialisation.PLUSPLUS,
): Double {
    val centers = tribuoCenters(trainTribuoKMeans(points, nClusters, seed, initialisation))
    return sumOfSquaredErrors(points, assignClusters(points, centers), centers)
}

fun tribuoBestSse(
    points: List<Point>,
    nClusters: Int,
    seed: Long,
    nInit: Int = 10,
): Double = (0 until nInit).minOf { tribuoKMeansSse(points, nClusters, seed + it) }
