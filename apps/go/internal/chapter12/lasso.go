package chapter12

import (
	"fmt"
	"math"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"gonum.org/v1/gonum/mat"
)

const (
	// lassoTolerance は係数の変化がこれより小さくなったら止める。
	lassoTolerance = 1e-10
	// lassoMaxIterations は座標降下法の繰り返しの上限。
	lassoMaxIterations = 100000
)

// SoftThreshold は軟判定しきい値関数。絶対値が threshold 以下なら 0 にし、そうでなければ threshold だけ 0 に近づける。
// ラッソ回帰が係数をぴったり 0 にできるのは、この関数が 0 を返す幅を持つからです。
func SoftThreshold(value, threshold float64) float64 {
	switch {
	case value > threshold:
		return value - threshold
	case value < -threshold:
		return value + threshold
	default:
		return 0
	}
}

// FitLasso は ‖t − X w‖² + alpha × Σ|w| を座標降下法で最小にする。
// 係数の絶対値に罰則をかけるので、効いていない特徴量の係数は 0 になる。
// リッジ回帰のように行列を解く式にはできないので、1 つずつ順番に係数を更新する。
func FitLasso(x []chapter02.Features, t []float64, alpha float64) (RegularizedModel, error) {
	if alpha < 0 {
		return RegularizedModel{}, fmt.Errorf("alpha は 0 以上にしてください: %v", alpha)
	}

	design, targets, means, targetMean, err := centered(x, t)
	if err != nil {
		return RegularizedModel{}, err
	}

	rows, columns := design.Dims()
	weights := make([]float64, columns)
	residuals := slices.Clone(targets)
	squares := make([]float64, columns)

	for j := range columns {
		for i := range rows {
			value := design.At(i, j)
			squares[j] += value * value
		}
	}

	for range lassoMaxIterations {
		if descend(design, residuals, weights, squares, alpha) < lassoTolerance {
			break
		}
	}

	return RegularizedModel{
		Columns:      slices.Clone(x[0].Columns),
		Coefficients: weights,
		Intercept:    interceptOf(weights, means, targetMean),
	}, nil
}

// descend は全部の係数を 1 巡だけ更新し、変化の最大値を返す。
func descend(design *mat.Dense, residuals, weights, squares []float64, alpha float64) float64 {
	rows, columns := design.Dims()
	maxChange := 0.0

	for j := range columns {
		if squares[j] == 0 {
			continue
		}

		// その列を外したときの残差と、この列との内積
		rho := 0.0
		for i := range rows {
			rho += design.At(i, j) * (residuals[i] + design.At(i, j)*weights[j])
		}

		updated := SoftThreshold(rho, alpha/2) / squares[j]

		if change := math.Abs(updated - weights[j]); change > maxChange {
			maxChange = change
		}

		// 残差を差分だけ直す。毎回すべて計算し直すより速い
		difference := updated - weights[j]
		if difference != 0 {
			for i := range rows {
				residuals[i] -= design.At(i, j) * difference
			}

			weights[j] = updated
		}
	}

	return maxChange
}

// ZeroCoefficientNames は係数がちょうど 0 になった列の名前を、列の順に返す。
func ZeroCoefficientNames(model RegularizedModel) []string {
	names := make([]string, 0, len(model.Columns))

	for i, coefficient := range model.Coefficients {
		if coefficient == 0 {
			names = append(names, model.Columns[i])
		}
	}

	return names
}
