// Package chapter13 は主成分分析（分散共分散行列・固有値分解・寄与率・射影）を扱う。
package chapter13

import (
	"fmt"
	"math"
	"slices"

	"gonum.org/v1/gonum/mat"
)

// Model は学習した主成分分析のモデル。
type Model struct {
	// Mean は列ごとの平均
	Mean []float64
	// Components は主成分を 1 行に 1 つずつ、寄与率の大きい順に並べたもの
	Components [][]float64
	// ExplainedVariance は主成分ごとの分散（固有値）
	ExplainedVariance []float64
	// ExplainedVarianceRatio は主成分ごとの寄与率
	ExplainedVarianceRatio []float64
}

// Loading は主成分の向きに対する 1 つの列の係数。
type Loading struct {
	// Column は列名
	Column string
	// Value は主成分の向きの成分（符号付き）
	Value float64
}

// validate は 1 件も無い、列の数がそろっていない、といった点数の並びを弾く。
func validate(x [][]float64) error {
	if len(x) == 0 {
		return fmt.Errorf("データが 1 件もありません")
	}

	for i, row := range x {
		if len(row) != len(x[0]) {
			return fmt.Errorf("%d 件目の列の数が違います: %d と %d", i+1, len(row), len(x[0]))
		}
	}

	if len(x[0]) == 0 {
		return fmt.Errorf("列が 1 つもありません")
	}

	return nil
}

// ColumnMeans は列ごとの平均を求める。
func ColumnMeans(x [][]float64) ([]float64, error) {
	if err := validate(x); err != nil {
		return nil, err
	}

	means := make([]float64, len(x[0]))

	for _, row := range x {
		for j, value := range row {
			means[j] += value
		}
	}

	for j := range means {
		means[j] /= float64(len(x))
	}

	return means, nil
}

// center は各列から平均を引く（中心化）。元のスライスは変更しない。
func center(x [][]float64, means []float64) [][]float64 {
	centered := make([][]float64, len(x))

	for i, row := range x {
		centered[i] = slices.Clone(row)
		for j := range centered[i] {
			centered[i][j] -= means[j]
		}
	}

	return centered
}

// CovarianceMatrix は列ごとの分散と、2 列ずつの共分散を並べた行列を返す。件数から 1 を引いた数で割る。
func CovarianceMatrix(x [][]float64) ([][]float64, error) {
	means, err := ColumnMeans(x)
	if err != nil {
		return nil, err
	}

	if len(x) < 2 {
		return nil, fmt.Errorf("分散共分散行列には 2 件以上が必要です: %d 件", len(x))
	}

	centered := center(x, means)
	columns := len(means)
	covariance := make([][]float64, columns)

	for j := range covariance {
		covariance[j] = make([]float64, columns)

		for k := range covariance[j] {
			sum := 0.0
			for _, row := range centered {
				sum += row[j] * row[k]
			}

			covariance[j][k] = sum / float64(len(x)-1)
		}
	}

	return covariance, nil
}

// Fit は分散共分散行列を固有値分解し、寄与率の大きい順に nComponents 個の主成分を求める。
// 固有値分解だけを gonum の mat.EigenSym に任せ、並べ替えと符号そろえは自分で行う。
func Fit(x [][]float64, nComponents int) (Model, error) {
	covariance, err := CovarianceMatrix(x)
	if err != nil {
		return Model{}, err
	}

	if nComponents < 1 || nComponents > len(covariance) {
		return Model{}, fmt.Errorf("主成分の数が範囲の外です: %d（列は %d）", nComponents, len(covariance))
	}

	values, vectors, err := eigen(covariance)
	if err != nil {
		return Model{}, err
	}

	total := 0.0
	for _, value := range values {
		total += value
	}

	if total == 0 {
		return Model{}, fmt.Errorf("すべての列の分散が 0 です")
	}

	means, err := ColumnMeans(x)
	if err != nil {
		return Model{}, err
	}

	model := Model{
		Mean:                   means,
		Components:             NormalizeSigns(vectors[:nComponents]),
		ExplainedVariance:      slices.Clone(values[:nComponents]),
		ExplainedVarianceRatio: make([]float64, nComponents),
	}

	for i, value := range model.ExplainedVariance {
		model.ExplainedVarianceRatio[i] = value / total
	}

	return model, nil
}

// eigen は対称行列を固有値分解し、固有値と固有ベクトル（1 行に 1 つ）を大きい順に返す。
// gonum の mat.EigenSym は小さい順に並べるので、ここで逆に並べ替える。
func eigen(symmetric [][]float64) ([]float64, [][]float64, error) {
	size := len(symmetric)
	flat := make([]float64, 0, size*size)

	for _, row := range symmetric {
		flat = append(flat, row...)
	}

	var decomposition mat.EigenSym
	if ok := decomposition.Factorize(mat.NewSymDense(size, flat), true); !ok {
		return nil, nil, fmt.Errorf("固有値分解に失敗しました")
	}

	ascending := decomposition.Values(nil)

	var vectors mat.Dense
	decomposition.VectorsTo(&vectors)

	values := make([]float64, size)
	descending := make([][]float64, size)

	for i := range size {
		source := size - 1 - i
		values[i] = ascending[source]
		descending[i] = mat.Col(nil, source, &vectors)
	}

	return values, descending, nil
}

// NormalizeSigns は、固有ベクトルは符号が逆でも同じ向きを表すので、
// 絶対値が最大の要素が正になるようにそろえる。元のスライスは変更しない。
func NormalizeSigns(components [][]float64) [][]float64 {
	normalized := make([][]float64, len(components))

	for i, component := range components {
		normalized[i] = slices.Clone(component)

		largest := 0.0
		for _, value := range component {
			if math.Abs(value) > math.Abs(largest) {
				largest = value
			}
		}

		if largest < 0 {
			for j := range normalized[i] {
				normalized[i][j] *= -1
			}
		}
	}

	return normalized
}

// Transform は平均を引いてから、データを主成分の向きに射影する。
func (m Model) Transform(x [][]float64) ([][]float64, error) {
	if err := validate(x); err != nil {
		return nil, err
	}

	if len(x[0]) != len(m.Mean) {
		return nil, fmt.Errorf("列の数が学習時と違います: %d と %d", len(x[0]), len(m.Mean))
	}

	centered := center(x, m.Mean)
	projected := make([][]float64, len(centered))

	for i, row := range centered {
		projected[i] = make([]float64, len(m.Components))

		for k, component := range m.Components {
			sum := 0.0
			for j, value := range row {
				sum += value * component[j]
			}

			projected[i][k] = sum
		}
	}

	return projected, nil
}

// ComponentsNeeded は累積寄与率がしきい値に届くまでの主成分の数を返す。
func ComponentsNeeded(ratios []float64, threshold float64) int {
	cumulative := 0.0

	for i, ratio := range ratios {
		cumulative += ratio
		if cumulative >= threshold {
			return i + 1
		}
	}

	return len(ratios)
}

// TopLoadings は主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。
func TopLoadings(component []float64, columns []string, k int) ([]Loading, error) {
	if len(component) != len(columns) {
		return nil, fmt.Errorf("主成分の成分の数と列名の数が違います: %d と %d", len(component), len(columns))
	}

	loadings := make([]Loading, len(columns))
	for j, column := range columns {
		loadings[j] = Loading{Column: column, Value: component[j]}
	}

	slices.SortStableFunc(loadings, func(a, b Loading) int {
		return cmpDescending(math.Abs(a.Value), math.Abs(b.Value))
	})

	if k > len(loadings) {
		k = len(loadings)
	}

	return loadings[:k], nil
}

// cmpDescending は大きい順に並べるための比較。
func cmpDescending(a, b float64) int {
	switch {
	case a > b:
		return -1
	case a < b:
		return 1
	default:
		return 0
	}
}
