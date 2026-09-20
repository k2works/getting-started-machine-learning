package chapter07

import (
	"fmt"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"gonum.org/v1/gonum/stat"
)

// SimpleLinearRegression は gonum の stat.LinearRegression で単回帰を学習する。
// gonum の単回帰は特徴量を 1 つしか取れないので、列名で 1 列だけ取り出して渡す。
func SimpleLinearRegression(x []chapter02.Features, t []float64, column string) (LinearModel, error) {
	if len(x) != len(t) {
		return LinearModel{}, fmt.Errorf("特徴量と実測値の件数が違います: %d と %d", len(x), len(t))
	}

	values := make([]float64, len(x))

	for i, features := range x {
		value, err := features.Value(column)
		if err != nil {
			return LinearModel{}, err
		}

		values[i] = value
	}

	// 第 3 引数の weights は nil で「すべて重み 1」、第 4 引数の origin は
	// false で「切片を求める（原点を通らせない）」を表す。
	intercept, slope := stat.LinearRegression(values, t, nil, false)

	return NewLinearModel(intercept, []string{column}, []float64{slope})
}
