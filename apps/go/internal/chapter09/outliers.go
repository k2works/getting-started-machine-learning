package chapter09

import (
	"math"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

const (
	// DefaultK は外れ値とみなす、四分位数から四分位範囲の何倍離れているか。
	DefaultK = 1.5
	// firstQuartile は第 1 四分位数の位置。
	firstQuartile = 0.25
	// thirdQuartile は第 3 四分位数の位置。
	thirdQuartile = 0.75
)

// Quantile は分位数を求める。位置が値の間にあれば前後の値から線形補間する（pandas の quantile の既定と同じ）。
func Quantile(values []float64, q float64) float64 {
	sorted := slices.Clone(values)
	slices.Sort(sorted)

	position := float64(len(sorted)-1) * q
	lower := int(math.Floor(position))
	upper := int(math.Ceil(position))

	return sorted[lower] + (sorted[upper]-sorted[lower])*(position-float64(lower))
}

// IQROutliers は第 1 四分位数から四分位範囲の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。
func IQROutliers(values []float64, k float64) []bool {
	q1 := Quantile(values, firstQuartile)
	q3 := Quantile(values, thirdQuartile)
	iqr := q3 - q1

	outliers := make([]bool, len(values))
	for i, value := range values {
		outliers[i] = value < q1-k*iqr || value > q3+k*iqr
	}

	return outliers
}

// RemoveTargetOutliers は訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。
func RemoveTargetOutliers(
	split chapter02.TrainTestSplit[chapter02.Features, float64],
) chapter02.TrainTestSplit[chapter02.Features, float64] {
	outliers := IQROutliers(split.TTrain, DefaultK)
	kept := chapter02.TrainTestSplit[chapter02.Features, float64]{XTest: split.XTest, TTest: split.TTest}

	for i, outlier := range outliers {
		if outlier {
			continue
		}

		kept.XTrain = append(kept.XTrain, split.XTrain[i])
		kept.TTrain = append(kept.TTrain, split.TTrain[i])
	}

	return kept
}
