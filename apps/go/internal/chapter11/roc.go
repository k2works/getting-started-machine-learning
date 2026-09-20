package chapter11

import (
	"fmt"
	"slices"

	"gonum.org/v1/gonum/integrate"
	"gonum.org/v1/gonum/stat"
)

// ROCPoint は ROC 曲線の 1 点。境目をその値にしたときの偽陽性率と真陽性率。
type ROCPoint struct {
	Threshold float64
	FPR       float64
	TPR       float64
}

// ROCCurve は確率の高い順に境目を下げながら、偽陽性率と真陽性率を並べる。
// 先頭は「誰も陽性と言わない」点 (0, 0) で、末尾は「全員を陽性と言う」点 (1, 1) になる。
func ROCCurve(scores []float64, actual []string, positive string) ([]ROCPoint, error) {
	if err := requireSameSize(scores, actual); err != nil {
		return nil, err
	}

	positives, negatives := 0, 0

	for _, label := range actual {
		if label == positive {
			positives++
		} else {
			negatives++
		}
	}

	if positives == 0 || negatives == 0 {
		return nil, fmt.Errorf("陽性 %d 件、陰性 %d 件では ROC 曲線を描けません", positives, negatives)
	}

	order := make([]int, len(scores))
	for i := range order {
		order[i] = i
	}

	// 同じ確率の行は 1 つの境目にまとめたいので、確率の高い順に並べる
	slices.SortStableFunc(order, func(a, b int) int {
		switch {
		case scores[a] > scores[b]:
			return -1
		case scores[a] < scores[b]:
			return 1
		default:
			return 0
		}
	})

	points := []ROCPoint{{Threshold: scores[order[0]] + 1, FPR: 0, TPR: 0}}
	truePositives, falsePositives := 0, 0

	for i, index := range order {
		if actual[index] == positive {
			truePositives++
		} else {
			falsePositives++
		}

		// 次の行も同じ確率なら、境目をまだ下げ切っていないので点にしない
		if i+1 < len(order) && scores[order[i+1]] == scores[index] {
			continue
		}

		points = append(points, ROCPoint{
			Threshold: scores[index],
			FPR:       float64(falsePositives) / float64(negatives),
			TPR:       float64(truePositives) / float64(positives),
		})
	}

	return points, nil
}

// AUC は ROC 曲線の下の面積を台形で求める。0.5 が当てずっぽう、1 が完全な分類。
func AUC(points []ROCPoint) (float64, error) {
	if len(points) < 2 {
		return 0, fmt.Errorf("ROC 曲線の点が %d 個では面積を求められません", len(points))
	}

	area := 0.0

	for i := 1; i < len(points); i++ {
		width := points[i].FPR - points[i-1].FPR
		area += width * (points[i].TPR + points[i-1].TPR) / 2
	}

	return area, nil
}

// GonumAUC は gonum の stat.ROC と integrate.Trapezoidal で AUC を求める。
// stat.ROC は確率が昇順に並んでいることを前提にし、そうでなければ panic するので、先に並べ替える。
func GonumAUC(scores []float64, actual []string, positive string) (float64, error) {
	if err := requireSameSize(scores, actual); err != nil {
		return 0, err
	}

	sorted := slices.Clone(scores)
	classes := make([]bool, len(actual))

	for i, label := range actual {
		classes[i] = label == positive
	}

	// stat.SortWeightedLabeled は確率と正解ラベルの対応を保ったまま昇順に並べる
	stat.SortWeightedLabeled(sorted, classes, nil)

	tpr, fpr, _ := stat.ROC(nil, sorted, classes, nil)

	return integrate.Trapezoidal(fpr, tpr), nil
}
