package chapter09

import (
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// Pair は 2 つの列の組。Left と Right が同じなら 2 乗の項を表す。
type Pair struct {
	Left  string
	Right string
}

// Name は項の名前。scikit-learn の get_feature_names_out と同じ形（"RM^2"、"RM LSTAT"）にする。
func (p Pair) Name() string {
	if p.Left == p.Right {
		return p.Left + "^2"
	}

	return p.Left + " " + p.Right
}

// PairsWithReplacement は重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。
func PairsWithReplacement(columns []string) []Pair {
	pairs := make([]Pair, 0, len(columns)*(len(columns)+1)/2)

	for i, left := range columns {
		for _, right := range columns[i:] {
			pairs = append(pairs, Pair{Left: left, Right: right})
		}
	}

	return pairs
}

// Expand は指定した列の後ろに、2 乗の項と交互作用の項を加える。
func Expand(x []chapter02.Features, columns []string) ([]chapter02.Features, error) {
	pairs := PairsWithReplacement(columns)
	names := slices.Clone(columns)

	for _, pair := range pairs {
		names = append(names, pair.Name())
	}

	expanded := make([]chapter02.Features, len(x))

	for i, features := range x {
		values := make([]float64, 0, len(names))

		for _, column := range columns {
			value, err := features.Value(column)
			if err != nil {
				return nil, err
			}

			values = append(values, value)
		}

		for _, pair := range pairs {
			left, err := features.Value(pair.Left)
			if err != nil {
				return nil, err
			}

			right, err := features.Value(pair.Right)
			if err != nil {
				return nil, err
			}

			values = append(values, left*right)
		}

		row, err := chapter02.NewFeatures(names, values)
		if err != nil {
			return nil, err
		}

		expanded[i] = row
	}

	return expanded, nil
}

// Select は指定した列だけを、その順に選ぶ。
func Select(x []chapter02.Features, columns []string) ([]chapter02.Features, error) {
	selected := make([]chapter02.Features, len(x))

	for i, features := range x {
		values := make([]float64, len(columns))

		for j, column := range columns {
			value, err := features.Value(column)
			if err != nil {
				return nil, err
			}

			values[j] = value
		}

		row, err := chapter02.NewFeatures(columns, values)
		if err != nil {
			return nil, err
		}

		selected[i] = row
	}

	return selected, nil
}
