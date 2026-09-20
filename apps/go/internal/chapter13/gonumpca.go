package chapter13

import (
	"fmt"
	"slices"

	"gonum.org/v1/gonum/mat"
	"gonum.org/v1/gonum/stat"
)

// GonumFit は gonum の stat.PC で主成分分析をする。
// stat.PC は中心化から特異値分解までをまとめて行うので、自作の Fit と同じ形の Model に詰め替える。
func GonumFit(x [][]float64, nComponents int) (Model, error) {
	means, err := ColumnMeans(x)
	if err != nil {
		return Model{}, err
	}

	if nComponents < 1 || nComponents > len(means) {
		return Model{}, fmt.Errorf("主成分の数が範囲の外です: %d（列は %d）", nComponents, len(means))
	}

	flat := make([]float64, 0, len(x)*len(means))
	for _, row := range x {
		flat = append(flat, row...)
	}

	// 第 2 引数の weights は nil で「すべて重み 1」を表す。
	// 成功したかどうかは戻り値の真偽で返り、失敗したときの結果は使えない。
	var pc stat.PC
	if ok := pc.PrincipalComponents(mat.NewDense(len(x), len(means), flat), nil); !ok {
		return Model{}, fmt.Errorf("gonum の主成分分析に失敗しました")
	}

	variances := pc.VarsTo(nil)

	var vectors mat.Dense
	pc.VectorsTo(&vectors)

	total := 0.0
	for _, variance := range variances {
		total += variance
	}

	if total == 0 {
		return Model{}, fmt.Errorf("すべての列の分散が 0 です")
	}

	components := make([][]float64, nComponents)
	for i := range components {
		components[i] = mat.Col(nil, i, &vectors)
	}

	model := Model{
		Mean:                   means,
		Components:             NormalizeSigns(components),
		ExplainedVariance:      slices.Clone(variances[:nComponents]),
		ExplainedVarianceRatio: make([]float64, nComponents),
	}

	for i, variance := range model.ExplainedVariance {
		model.ExplainedVarianceRatio[i] = variance / total
	}

	return model, nil
}
