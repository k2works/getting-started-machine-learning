package chapter09

import (
	"fmt"
	"slices"

	"gonum.org/v1/gonum/mat"
)

// LinearModel は線形回帰のモデル。正規方程式を gonum の行列（コレスキー分解）で解いて求める。
type LinearModel struct {
	// Intercept は切片
	Intercept float64
	// Weights は特徴量の列ごとの係数
	Weights []float64
}

// PredictOne は 1 行の特徴量から予測する。
func (m LinearModel) PredictOne(row []float64) float64 {
	sum := m.Intercept
	for i, value := range row {
		sum += m.Weights[i] * value
	}

	return sum
}

// Predict は行ごとに予測する。
func (m LinearModel) Predict(rows [][]float64) []float64 {
	predictions := make([]float64, len(rows))
	for i, row := range rows {
		predictions[i] = m.PredictOne(row)
	}

	return predictions
}

// FitLinearModel は先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く。
// 列が互いに独立でなければ XᵀX を分解できないので、エラーを返す。
func FitLinearModel(rows [][]float64, t []float64) (LinearModel, error) {
	if len(rows) != len(t) {
		return LinearModel{}, fmt.Errorf("特徴量と正解の件数が違います: %d と %d", len(rows), len(t))
	}

	if len(rows) == 0 {
		return LinearModel{}, fmt.Errorf("特徴量が 1 件もありません")
	}

	columns := len(rows[0]) + 1
	design := make([]float64, 0, len(rows)*columns)

	for _, row := range rows {
		design = append(design, 1)
		design = append(design, row...)
	}

	x := mat.NewDense(len(rows), columns, design)

	var normal mat.SymDense

	normal.SymOuterK(1, x.T())

	var cholesky mat.Cholesky
	if ok := cholesky.Factorize(&normal); !ok {
		return LinearModel{}, fmt.Errorf("特徴量の列が互いに独立でないため、正規方程式を解けません")
	}

	target := mat.NewVecDense(len(t), slices.Clone(t))

	var right mat.VecDense

	right.MulVec(x.T(), target)

	var beta mat.VecDense
	if err := cholesky.SolveVecTo(&beta, &right); err != nil {
		return LinearModel{}, fmt.Errorf("正規方程式を解けません: %w", err)
	}

	weights := make([]float64, columns-1)
	for i := range weights {
		weights[i] = beta.AtVec(i + 1)
	}

	return LinearModel{Intercept: beta.AtVec(0), Weights: weights}, nil
}

// RSquared は決定係数。1 から、残差の 2 乗和を平均との差の 2 乗和で割った値を引く。
func RSquared(actual, predicted []float64) float64 {
	average := mean(actual)
	residual, total := 0.0, 0.0

	for i, value := range actual {
		residual += (value - predicted[i]) * (value - predicted[i])
		total += (value - average) * (value - average)
	}

	return 1 - residual/total
}
