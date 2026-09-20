package chapter12

import (
	"fmt"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter09"
)

// PolynomialScaler は標準化してから 2 乗の項と交互作用の項を足す前処理。
// 訓練データで Fit し、同じ平均・標準偏差で検証データとテストデータを Transform する。
type PolynomialScaler struct {
	// Inputs は元の列の名前
	Inputs []string
	// Means は列ごとの平均
	Means []float64
	// Stds は列ごとの母標準偏差。すべて同じ値の列は 1 にする
	Stds []float64
}

// FitScaler は訓練データから平均と母標準偏差を求める。
func FitScaler(x []chapter02.Features) (PolynomialScaler, error) {
	if len(x) == 0 {
		return PolynomialScaler{}, fmt.Errorf("特徴量が 1 件もありません")
	}

	inputs := slices.Clone(x[0].Columns)
	means := make([]float64, len(inputs))
	stds := make([]float64, len(inputs))

	for j := range inputs {
		values := make([]float64, len(x))

		for i, features := range x {
			if len(features.Values) != len(inputs) {
				return PolynomialScaler{}, fmt.Errorf("行によって特徴量の数が違います: %d と %d", len(features.Values), len(inputs))
			}

			values[i] = features.Values[j]
		}

		mean := 0.0
		for _, value := range values {
			mean += value / float64(len(values))
		}

		std := chapter09.PopulationStdDev(values)
		if std == 0 {
			std = 1
		}

		means[j] = mean
		stds[j] = std
	}

	return PolynomialScaler{Inputs: inputs, Means: means, Stds: stds}, nil
}

// pairs は i ≤ j の組を、列の順に返す。2 乗の項は i == j の組。
func (s PolynomialScaler) pairs() [][2]int {
	combinations := make([][2]int, 0, len(s.Inputs)*(len(s.Inputs)+1)/2)

	for i := range s.Inputs {
		for j := i; j < len(s.Inputs); j++ {
			combinations = append(combinations, [2]int{i, j})
		}
	}

	return combinations
}

// FeatureNames は変換した後の列の名前を、元の列・2 乗の項・交互作用の項の順に返す。
func (s PolynomialScaler) FeatureNames() []string {
	names := slices.Clone(s.Inputs)

	for _, pair := range s.pairs() {
		if pair[0] == pair[1] {
			names = append(names, s.Inputs[pair[0]]+"^2")
		} else {
			names = append(names, s.Inputs[pair[0]]+" "+s.Inputs[pair[1]])
		}
	}

	return names
}

// Transform は標準化してから、2 乗の項と交互作用の項を足した特徴量にする。
func (s PolynomialScaler) Transform(x []chapter02.Features) ([]chapter02.Features, error) {
	names := s.FeatureNames()
	transformed := make([]chapter02.Features, 0, len(x))

	for _, features := range x {
		if len(features.Values) != len(s.Inputs) {
			return nil, fmt.Errorf("行によって特徴量の数が違います: %d と %d", len(features.Values), len(s.Inputs))
		}

		standardized := make([]float64, len(s.Inputs))
		for j, value := range features.Values {
			standardized[j] = (value - s.Means[j]) / s.Stds[j]
		}

		values := slices.Clone(standardized)
		for _, pair := range s.pairs() {
			values = append(values, standardized[pair[0]]*standardized[pair[1]])
		}

		converted, err := chapter02.NewFeatures(names, values)
		if err != nil {
			return nil, err
		}

		transformed = append(transformed, converted)
	}

	return transformed, nil
}
