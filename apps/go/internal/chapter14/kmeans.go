// Package chapter14 は K-means によるクラスタリングを扱う。
package chapter14

import (
	"fmt"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

const (
	// DefaultMaxIterations は更新の回数の既定の上限（scikit-learn の KMeans と同じ）。
	DefaultMaxIterations = 300
	// DefaultNInit は初期中心を試す回数の既定値（scikit-learn の KMeans の n_init と同じ）。
	DefaultNInit = 10
)

// Result は K-means の結果。
type Result struct {
	// Labels は点ごとのクラスタ番号
	Labels []int
	// Centers はクラスタの中心を 1 行に 1 つずつ並べたもの
	Centers [][]float64
	// SSE は誤差平方和
	SSE float64
}

// squaredDistance は 2 点間の距離の 2 乗。長さがそろっていることは呼ぶ前に確かめる。
func squaredDistance(a, b []float64) float64 {
	sum := 0.0

	for j, value := range a {
		d := value - b[j]
		sum += d * d
	}

	return sum
}

// SquaredDistance は 2 点間の距離の 2 乗を返す。次元が違えばエラーを返す。
func SquaredDistance(a, b []float64) (float64, error) {
	if len(a) != len(b) {
		return 0, fmt.Errorf("点の次元が違います: %d と %d", len(a), len(b))
	}

	return squaredDistance(a, b), nil
}

// validate は点と中心の並びが計算できる形かを確かめる。
func validate(points, centers [][]float64) error {
	if len(points) == 0 {
		return fmt.Errorf("点が 1 つもありません")
	}

	if len(centers) == 0 {
		return fmt.Errorf("中心が 1 つもありません")
	}

	dimensions := len(points[0])
	for i, point := range points {
		if len(point) != dimensions {
			return fmt.Errorf("%d 番目の点の次元が違います: %d と %d", i+1, len(point), dimensions)
		}
	}

	for k, center := range centers {
		if len(center) != dimensions {
			return fmt.Errorf("%d 番目の中心の次元が違います: %d と %d", k+1, len(center), dimensions)
		}
	}

	return nil
}

// AssignClusters は各点を、最も近い中心のクラスタ番号に割り当てる。
func AssignClusters(points, centers [][]float64) ([]int, error) {
	if err := validate(points, centers); err != nil {
		return nil, err
	}

	return assignClusters(points, centers), nil
}

func assignClusters(points, centers [][]float64) []int {
	labels := make([]int, len(points))

	for i, point := range points {
		nearest := 0
		best := squaredDistance(point, centers[0])

		for k := 1; k < len(centers); k++ {
			if distance := squaredDistance(point, centers[k]); distance < best {
				nearest = k
				best = distance
			}
		}

		labels[i] = nearest
	}

	return labels
}

// UpdateCenters はクラスタごとに、割り当てられた点の平均を新しい中心にする。
// 点が 1 つも無いクラスタは、前の中心をそのまま残す。
func UpdateCenters(points [][]float64, labels []int, previous [][]float64) ([][]float64, error) {
	if err := validate(points, previous); err != nil {
		return nil, err
	}

	if len(labels) != len(points) {
		return nil, fmt.Errorf("点とクラスタ番号の数が違います: %d と %d", len(points), len(labels))
	}

	for i, label := range labels {
		if label < 0 || label >= len(previous) {
			return nil, fmt.Errorf("%d 番目のクラスタ番号が範囲の外です: %d", i+1, label)
		}
	}

	return updateCenters(points, labels, previous), nil
}

func updateCenters(points [][]float64, labels []int, previous [][]float64) [][]float64 {
	dimensions := len(points[0])
	sums := make([][]float64, len(previous))
	counts := make([]int, len(previous))

	for k := range sums {
		sums[k] = make([]float64, dimensions)
	}

	for i, point := range points {
		counts[labels[i]]++

		for j, value := range point {
			sums[labels[i]][j] += value
		}
	}

	for k, sum := range sums {
		if counts[k] == 0 {
			sums[k] = slices.Clone(previous[k])
			continue
		}

		for j := range sum {
			sum[j] /= float64(counts[k])
		}
	}

	return sums
}

// SumOfSquaredErrors は各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）を返す。
func SumOfSquaredErrors(points [][]float64, labels []int, centers [][]float64) (float64, error) {
	if err := validate(points, centers); err != nil {
		return 0, err
	}

	if len(labels) != len(points) {
		return 0, fmt.Errorf("点とクラスタ番号の数が違います: %d と %d", len(points), len(labels))
	}

	for i, label := range labels {
		if label < 0 || label >= len(centers) {
			return 0, fmt.Errorf("%d 番目のクラスタ番号が範囲の外です: %d", i+1, label)
		}
	}

	return sumOfSquaredErrors(points, labels, centers), nil
}

func sumOfSquaredErrors(points [][]float64, labels []int, centers [][]float64) float64 {
	sum := 0.0
	for i, point := range points {
		sum += squaredDistance(point, centers[labels[i]])
	}

	return sum
}

// Fit は中心が変わらなくなるまで、割り当てと中心の更新を繰り返す（最大 DefaultMaxIterations 回）。
func Fit(points, initialCenters [][]float64) (Result, error) {
	return FitWithMaxIterations(points, initialCenters, DefaultMaxIterations)
}

// FitWithMaxIterations は中心が変わらなくなるか、更新の回数が maxIterations に達するまで繰り返す。
func FitWithMaxIterations(points, initialCenters [][]float64, maxIterations int) (Result, error) {
	if err := validate(points, initialCenters); err != nil {
		return Result{}, err
	}

	if maxIterations < 1 {
		return Result{}, fmt.Errorf("更新の回数の上限は 1 以上です: %d", maxIterations)
	}

	centers := initialCenters

	for range maxIterations {
		next := updateCenters(points, assignClusters(points, centers), centers)
		if equalCenters(next, centers) {
			break
		}

		centers = next
	}

	labels := assignClusters(points, centers)

	return Result{
		Labels:  labels,
		Centers: centers,
		SSE:     sumOfSquaredErrors(points, labels, centers),
	}, nil
}

// equalCenters は 2 つの中心の並びが同じ値かを返す。
func equalCenters(a, b [][]float64) bool {
	return slices.EqualFunc(a, b, slices.Equal[[]float64])
}

// ChooseInitialCenters はシード付きの乱数で点を並べ替え、先頭から nClusters 個を初期中心にする。
// 第 2 章の Shuffle（math/rand + Fisher-Yates）を使うので、同じシードなら同じ初期中心になる。
func ChooseInitialCenters(points [][]float64, nClusters int, seed int64) ([][]float64, error) {
	if nClusters < 1 || nClusters > len(points) {
		return nil, fmt.Errorf("クラスタ数が範囲の外です: %d（点は %d 個）", nClusters, len(points))
	}

	shuffled := chapter02.Shuffle(points, seed)
	centers := make([][]float64, nClusters)

	for k := range centers {
		centers[k] = slices.Clone(shuffled[k])
	}

	return centers, nil
}

// FitWithRestarts はシードを 1 ずつずらして初期中心を nInit 通り選び、SSE が最小の結果を返す。
func FitWithRestarts(points [][]float64, nClusters int, seed int64, nInit int) (Result, error) {
	if nInit < 1 {
		return Result{}, fmt.Errorf("初期中心を試す回数は 1 以上です: %d", nInit)
	}

	best := Result{}

	for i := range nInit {
		centers, err := ChooseInitialCenters(points, nClusters, seed+int64(i))
		if err != nil {
			return Result{}, err
		}

		result, err := Fit(points, centers)
		if err != nil {
			return Result{}, err
		}

		if i == 0 || result.SSE < best.SSE {
			best = result
		}
	}

	return best, nil
}

// ClusterSSE はクラスタ数と、そのときの誤差平方和。
type ClusterSSE struct {
	// Clusters はクラスタ数
	Clusters int
	// SSE は初期中心を何通りか試したときの最小の誤差平方和
	SSE float64
}

// SSEByClusterCount はクラスタ数ごとに、初期中心を nInit 通り試した最小の SSE を返す。
func SSEByClusterCount(points [][]float64, clusterCounts []int, seed int64, nInit int) ([]ClusterSSE, error) {
	results := make([]ClusterSSE, len(clusterCounts))

	for i, nClusters := range clusterCounts {
		result, err := FitWithRestarts(points, nClusters, seed, nInit)
		if err != nil {
			return nil, err
		}

		results[i] = ClusterSSE{Clusters: nClusters, SSE: result.SSE}
	}

	return results, nil
}
