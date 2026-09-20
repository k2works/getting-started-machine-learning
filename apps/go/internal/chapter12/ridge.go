// Package chapter12 は正則化（リッジ回帰・ラッソ回帰）とモデル選択を扱う。
package chapter12

import (
	"fmt"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"gonum.org/v1/gonum/mat"
)

// RegularizedModel は正則化した線形モデル。列名つきの係数と切片を持つ。
type RegularizedModel struct {
	Columns      []string
	Coefficients []float64
	Intercept    float64
}

// Coefficient は列名で係数を読む。
func (m RegularizedModel) Coefficient(column string) (float64, error) {
	index := slices.Index(m.Columns, column)
	if index < 0 {
		return 0, fmt.Errorf("列がありません: %s", column)
	}

	return m.Coefficients[index], nil
}

// CoefficientAbsSum は係数の絶対値の合計。正則化がどれだけ係数を縮めたかを 1 つの数で見る。
func (m RegularizedModel) CoefficientAbsSum() float64 {
	sum := 0.0

	for _, coefficient := range m.Coefficients {
		if coefficient < 0 {
			sum -= coefficient
		} else {
			sum += coefficient
		}
	}

	return sum
}

// Predict は 1 行分の予測値を返す。
func (m RegularizedModel) Predict(features chapter02.Features) (float64, error) {
	sum := m.Intercept

	for i, column := range m.Columns {
		value, err := features.Value(column)
		if err != nil {
			return 0, err
		}

		sum += m.Coefficients[i] * value
	}

	return sum, nil
}

// PredictAll は行ごとの予測値を返す。
func (m RegularizedModel) PredictAll(x []chapter02.Features) ([]float64, error) {
	predictions := make([]float64, len(x))

	for i, features := range x {
		prediction, err := m.Predict(features)
		if err != nil {
			return nil, err
		}

		predictions[i] = prediction
	}

	return predictions, nil
}

// centered は特徴量と正解を、それぞれの平均を引いた行列とベクトルにする。
// 切片には罰則をかけないので、中心化して切片を式から外す。
func centered(x []chapter02.Features, t []float64) (*mat.Dense, []float64, []float64, float64, error) {
	if len(x) != len(t) {
		return nil, nil, nil, 0, fmt.Errorf("特徴量と正解の件数が違います: %d と %d", len(x), len(t))
	}

	if len(x) == 0 {
		return nil, nil, nil, 0, fmt.Errorf("特徴量が 1 件もありません")
	}

	columns := len(x[0].Values)
	means := make([]float64, columns)

	for _, features := range x {
		if len(features.Values) != columns {
			return nil, nil, nil, 0, fmt.Errorf("行によって特徴量の数が違います: %d と %d", len(features.Values), columns)
		}

		for j, value := range features.Values {
			means[j] += value / float64(len(x))
		}
	}

	targetMean := 0.0
	for _, value := range t {
		targetMean += value / float64(len(t))
	}

	values := make([]float64, 0, len(x)*columns)
	targets := make([]float64, len(t))

	for i, features := range x {
		for j, value := range features.Values {
			values = append(values, value-means[j])
		}

		targets[i] = t[i] - targetMean
	}

	return mat.NewDense(len(x), columns, values), targets, means, targetMean, nil
}

// interceptOf は中心化を元に戻して切片を求める。
func interceptOf(weights, means []float64, targetMean float64) float64 {
	intercept := targetMean

	for j, weight := range weights {
		intercept -= weight * means[j]
	}

	return intercept
}

// FitRidge は (Xᵀ X + alpha × I) w = Xᵀ t を解く。alpha が 0 なら最小二乗法と同じになる。
func FitRidge(x []chapter02.Features, t []float64, alpha float64) (RegularizedModel, error) {
	if alpha < 0 {
		return RegularizedModel{}, fmt.Errorf("alpha は 0 以上にしてください: %v", alpha)
	}

	design, targets, means, targetMean, err := centered(x, t)
	if err != nil {
		return RegularizedModel{}, err
	}

	_, columns := design.Dims()

	var normal mat.Dense

	normal.Mul(design.T(), design)

	for j := range columns {
		normal.Set(j, j, normal.At(j, j)+alpha)
	}

	var moment mat.VecDense

	moment.MulVec(design.T(), mat.NewVecDense(len(targets), targets))

	var weights mat.VecDense
	if err := weights.SolveVec(&normal, &moment); err != nil {
		return RegularizedModel{}, fmt.Errorf("正規方程式を解けません: %w", err)
	}

	coefficients := mat.Col(nil, 0, &weights)

	return RegularizedModel{
		Columns:      slices.Clone(x[0].Columns),
		Coefficients: coefficients,
		Intercept:    interceptOf(coefficients, means, targetMean),
	}, nil
}
