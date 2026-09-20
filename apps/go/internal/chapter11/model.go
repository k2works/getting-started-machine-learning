package chapter11

import (
	"fmt"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
)

// Model は Fit で学習し、Predict で予測するモデル。T は正解ラベルの型。
// 第 10 章の Classifier をジェネリクスにしたもので、回帰（T が float64）にも使える。
type Model[T any] interface {
	Fit(x []chapter02.Features, t []T) error
	Predict(x []chapter02.Features) ([]T, error)
}

// LinearRegressionModel は第 7 章の線形回帰を Model[float64] にする。
// 第 7 章の Fit はパッケージの関数なので、学習したモデルを持つ構造体で包む。
type LinearRegressionModel struct {
	model   chapter07.LinearModel
	fitted  bool
	Columns []string
}

// NewLinearRegressionModel は学習していない線形回帰のモデルを返す。
func NewLinearRegressionModel() *LinearRegressionModel {
	return &LinearRegressionModel{}
}

// Fit は正規方程式を解いて係数を求める。
func (l *LinearRegressionModel) Fit(x []chapter02.Features, t []float64) error {
	model, err := chapter07.Fit(x, t)
	if err != nil {
		return err
	}

	l.model = model
	l.Columns = model.Columns
	l.fitted = true

	return nil
}

// Predict は行ごとの予測値を返す。学習していなければエラーを返す。
func (l *LinearRegressionModel) Predict(x []chapter02.Features) ([]float64, error) {
	if !l.fitted {
		return nil, fmt.Errorf("Fit で学習してから Predict を呼んでください")
	}

	return l.model.PredictAll(x)
}
