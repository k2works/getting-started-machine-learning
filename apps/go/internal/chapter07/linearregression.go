// Package chapter07 は線形回帰で数値を予測する。
package chapter07

import (
	"fmt"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"gonum.org/v1/gonum/mat"
)

// LinearModel は学習した線形回帰のモデル。切片と、列名の順に並んだ係数を持つ。
type LinearModel struct {
	Intercept    float64
	Columns      []string
	Coefficients []float64
}

// NewLinearModel は切片・列名・係数からモデルを作る。列名と係数の数が違えばエラーを返す。
func NewLinearModel(intercept float64, columns []string, coefficients []float64) (LinearModel, error) {
	if len(columns) != len(coefficients) {
		return LinearModel{}, fmt.Errorf("列名と係数の数が違います: %d と %d", len(columns), len(coefficients))
	}

	return LinearModel{
		Intercept:    intercept,
		Columns:      slices.Clone(columns),
		Coefficients: slices.Clone(coefficients),
	}, nil
}

// Coefficient は列名で係数を読む。
func (m LinearModel) Coefficient(column string) (float64, error) {
	index := slices.Index(m.Columns, column)
	if index < 0 {
		return 0, fmt.Errorf("列がありません: %s", column)
	}

	return m.Coefficients[index], nil
}

// Predict は 1 行分の特徴量の予測値を返す。係数は列名で対応させるので、列の並び順は問わない。
func (m LinearModel) Predict(features chapter02.Features) (float64, error) {
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
func (m LinearModel) PredictAll(x []chapter02.Features) ([]float64, error) {
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

// DesignMatrix は先頭に 1 の列を足した特徴量の行列（計画行列）を作る。1 の列の係数が切片になる。
func DesignMatrix(x []chapter02.Features) (*mat.Dense, error) {
	if len(x) == 0 {
		return nil, fmt.Errorf("訓練データが空です")
	}

	columns := len(x[0].Columns) + 1
	values := make([]float64, 0, len(x)*columns)

	for _, features := range x {
		if len(features.Values) != columns-1 {
			return nil, fmt.Errorf("行によって特徴量の数が違います: %d と %d", len(features.Values), columns-1)
		}

		values = append(values, 1)
		values = append(values, features.Values...)
	}

	return mat.NewDense(len(x), columns, values), nil
}

// Fit は正規方程式 (Xᵀ X) w = Xᵀ t を解いて、切片と係数を求める。
func Fit(x []chapter02.Features, t []float64) (LinearModel, error) {
	if len(x) != len(t) {
		return LinearModel{}, fmt.Errorf("特徴量と実測値の件数が違います: %d と %d", len(x), len(t))
	}

	design, err := DesignMatrix(x)
	if err != nil {
		return LinearModel{}, err
	}

	target := mat.NewVecDense(len(t), slices.Clone(t))

	var normal mat.Dense

	normal.Mul(design.T(), design)

	var moment mat.VecDense

	moment.MulVec(design.T(), target)

	var weights mat.VecDense
	if err := weights.SolveVec(&normal, &moment); err != nil {
		return LinearModel{}, fmt.Errorf("正規方程式を解けません: %w", err)
	}

	return NewLinearModel(weights.AtVec(0), x[0].Columns, mat.Col(nil, 0, weights.SliceVec(1, weights.Len())))
}
