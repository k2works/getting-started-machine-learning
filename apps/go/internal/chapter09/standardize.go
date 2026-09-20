package chapter09

import (
	"fmt"
	"math"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"gonum.org/v1/gonum/stat"
)

// errColumnNotFound は列が見つからないときのエラーを作る。
func errColumnNotFound(column string) error {
	return fmt.Errorf("列がありません: %s", column)
}

// Standardizer は列ごとの平均と母標準偏差（件数で割る標準偏差）で、平均 0・標準偏差 1 にそろえる。
// 訓練データで Fit し、同じ平均と標準偏差で訓練データとテストデータの両方を Transform する。
type Standardizer struct {
	// Columns は Fit に渡した特徴量の列。並びを保つために持つ
	Columns []string
	// Means は列名ごとの平均
	Means map[string]float64
	// Stds は列名ごとの母標準偏差。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする
	Stds map[string]float64
}

// Fit は特徴量のすべての列について、平均と母標準偏差を求める。
func Fit(x []chapter02.Features) (Standardizer, error) {
	if len(x) == 0 {
		return Standardizer{}, fmt.Errorf("特徴量が 1 件もありません")
	}

	columns := slices.Clone(x[0].Columns)
	means := make(map[string]float64, len(columns))
	stds := make(map[string]float64, len(columns))

	for _, column := range columns {
		values, err := columnValues(x, column)
		if err != nil {
			return Standardizer{}, err
		}

		std := PopulationStdDev(values)
		if std == 0 {
			std = 1
		}

		means[column] = mean(values)
		stds[column] = std
	}

	return Standardizer{Columns: columns, Means: means, Stds: stds}, nil
}

// columnValues は 1 つの列の値を並べて取り出す。
func columnValues(x []chapter02.Features, column string) ([]float64, error) {
	values := make([]float64, len(x))

	for i, features := range x {
		value, err := features.Value(column)
		if err != nil {
			return nil, err
		}

		values[i] = value
	}

	return values, nil
}

// mean は平均。
func mean(values []float64) float64 {
	sum := 0.0
	for _, value := range values {
		sum += value
	}

	return sum / float64(len(values))
}

// PopulationStdDev は母標準偏差。差の 2 乗の和を件数で割ってから平方根を取る。
func PopulationStdDev(values []float64) float64 {
	average := mean(values)
	sum := 0.0

	for _, value := range values {
		sum += (value - average) * (value - average)
	}

	return math.Sqrt(sum / float64(len(values)))
}

// GonumMean は gonum の stat.Mean で平均を求める。重みを使わないので nil を渡す。
func GonumMean(values []float64) float64 {
	return stat.Mean(values, nil)
}

// GonumStdDev は gonum の stat.StdDev で標準偏差を求める。
// gonum は件数から 1 を引いて割る標本標準偏差なので、自作の母標準偏差とは値が違う。
func GonumStdDev(values []float64) float64 {
	return stat.StdDev(values, nil)
}

// TransformOne は 1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。
func (s Standardizer) TransformOne(features chapter02.Features) (chapter02.Features, error) {
	values := slices.Clone(features.Values)

	for i, column := range features.Columns {
		std, ok := s.Stds[column]
		if !ok {
			continue
		}

		values[i] = (values[i] - s.Means[column]) / std
	}

	return chapter02.NewFeatures(features.Columns, values)
}

// Transform は特徴量のリストを標準化する。
func (s Standardizer) Transform(x []chapter02.Features) ([]chapter02.Features, error) {
	transformed := make([]chapter02.Features, len(x))

	for i, features := range x {
		one, err := s.TransformOne(features)
		if err != nil {
			return nil, err
		}

		transformed[i] = one
	}

	return transformed, nil
}
