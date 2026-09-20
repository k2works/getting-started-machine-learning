// Package chapter11 は分類と回帰の評価指標、ROC 曲線、K 分割交差検証を扱う。
package chapter11

import (
	"fmt"
)

// ConfusionMatrix は混同行列。陽性と陰性の予測が、正解とどう食い違ったかを 4 つの数で表す。
type ConfusionMatrix struct {
	// TruePositive は陽性を陽性と当てた件数
	TruePositive int
	// FalsePositive は陰性を陽性と間違えた件数
	FalsePositive int
	// FalseNegative は陽性を陰性と見逃した件数
	FalseNegative int
	// TrueNegative は陰性を陰性と当てた件数
	TrueNegative int
}

// requireSameSize は正解と予測の件数が同じであることを確かめる。
// 短いほうに切り詰めると黙って別の指標を計算することになるので、エラーにする。
func requireSameSize[A, B any](actual []A, predicted []B) error {
	if len(actual) != len(predicted) {
		return fmt.Errorf("正解と予測の件数が違います: %d と %d", len(actual), len(predicted))
	}

	if len(actual) == 0 {
		return fmt.Errorf("正解が 1 件もありません")
	}

	return nil
}

// NewConfusionMatrix は正解と予測から混同行列を数える。positive が陽性とみなすラベル。
func NewConfusionMatrix[T comparable](actual, predicted []T, positive T) (ConfusionMatrix, error) {
	if err := requireSameSize(actual, predicted); err != nil {
		return ConfusionMatrix{}, err
	}

	matrix := ConfusionMatrix{}

	for i, label := range actual {
		switch {
		case label == positive && predicted[i] == positive:
			matrix.TruePositive++
		case label != positive && predicted[i] == positive:
			matrix.FalsePositive++
		case label == positive && predicted[i] != positive:
			matrix.FalseNegative++
		default:
			matrix.TrueNegative++
		}
	}

	return matrix, nil
}

// ratio は分母が 0 なら 0 を返す割り算。適合率と再現率が NaN にならないようにする。
func ratio(numerator, denominator int) float64 {
	if denominator == 0 {
		return 0
	}

	return float64(numerator) / float64(denominator)
}

// Precision は適合率。陽性と予測したもののうち、本当に陽性だった割合。
func Precision(matrix ConfusionMatrix) float64 {
	return ratio(matrix.TruePositive, matrix.TruePositive+matrix.FalsePositive)
}

// Recall は再現率。本当に陽性のもののうち、陽性と予測できた割合。
func Recall(matrix ConfusionMatrix) float64 {
	return ratio(matrix.TruePositive, matrix.TruePositive+matrix.FalseNegative)
}

// F1Score は F 値。適合率と再現率の調和平均。
func F1Score(matrix ConfusionMatrix) float64 {
	precision, recall := Precision(matrix), Recall(matrix)
	if precision+recall == 0 {
		return 0
	}

	return 2 * precision * recall / (precision + recall)
}

// Accuracy は正解率。予測が正解と一致した割合。
func Accuracy[T comparable](actual, predicted []T) (float64, error) {
	if err := requireSameSize(actual, predicted); err != nil {
		return 0, err
	}

	correct := 0

	for i, label := range actual {
		if label == predicted[i] {
			correct++
		}
	}

	return float64(correct) / float64(len(actual)), nil
}

// MeanSquaredError は平均二乗誤差（MSE）。誤差の 2 乗の平均。
func MeanSquaredError(actual, predicted []float64) (float64, error) {
	if err := requireSameSize(actual, predicted); err != nil {
		return 0, err
	}

	sum := 0.0

	for i, value := range actual {
		difference := value - predicted[i]
		sum += difference * difference
	}

	return sum / float64(len(actual)), nil
}

// Metric は正解と予測を受け取って 1 つの数値を返す評価関数。交差検証に渡す高階関数の型。
type Metric[T any] func(actual, predicted []T) (float64, error)

// ClassificationMetric は混同行列から求める指標を、Metric の形に合わせる。
// 「どのラベルを陽性とするか」をここで閉じ込めるので、交差検証は指標の中身を知らずに済む。
func ClassificationMetric[T comparable](score func(ConfusionMatrix) float64, positive T) Metric[T] {
	return func(actual, predicted []T) (float64, error) {
		matrix, err := NewConfusionMatrix(actual, predicted, positive)
		if err != nil {
			return 0, err
		}

		return score(matrix), nil
	}
}
