package chapter07

import (
	"fmt"
	"math"
)

// residuals は実測値と予測値の差を返す。件数が違えばエラーを返す。
func residuals(t, y []float64) ([]float64, error) {
	if len(t) != len(y) {
		return nil, fmt.Errorf("実測値と予測値の件数が違います: %d と %d", len(t), len(y))
	}

	if len(t) == 0 {
		return nil, fmt.Errorf("実測値がありません")
	}

	differences := make([]float64, len(t))
	for i := range t {
		differences[i] = t[i] - y[i]
	}

	return differences, nil
}

// MeanAbsoluteError は平均絶対誤差（MAE）。誤差の絶対値の平均。
func MeanAbsoluteError(t, y []float64) (float64, error) {
	differences, err := residuals(t, y)
	if err != nil {
		return 0, err
	}

	sum := 0.0
	for _, difference := range differences {
		sum += math.Abs(difference)
	}

	return sum / float64(len(differences)), nil
}

// RootMeanSquaredError は二乗平均平方根誤差（RMSE）。
func RootMeanSquaredError(t, y []float64) (float64, error) {
	differences, err := residuals(t, y)
	if err != nil {
		return 0, err
	}

	return math.Sqrt(sumOfSquares(differences) / float64(len(differences))), nil
}

// R2Score は決定係数（R²）。1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。
func R2Score(t, y []float64) (float64, error) {
	differences, err := residuals(t, y)
	if err != nil {
		return 0, err
	}

	mean := 0.0
	for _, value := range t {
		mean += value
	}

	mean /= float64(len(t))

	deviations := make([]float64, len(t))
	for i, value := range t {
		deviations[i] = value - mean
	}

	total := sumOfSquares(deviations)
	if total == 0 {
		return 0, fmt.Errorf("実測値がすべて同じなので決定係数を求められません")
	}

	return 1 - sumOfSquares(differences)/total, nil
}

// sumOfSquares は 2 乗の合計。
func sumOfSquares(values []float64) float64 {
	sum := 0.0
	for _, value := range values {
		sum += value * value
	}

	return sum
}
