package chapter08

import (
	"fmt"
	"slices"
	"strings"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// Transformer は訓練データから変換に必要な値を求める前処理。
type Transformer interface {
	Fit(x chapter02.Table) (FittedTransformer, error)
}

// FittedTransformer は Fit で求めた値を使ってデータを変換する前処理。
type FittedTransformer interface {
	Transform(x chapter02.Table) (chapter02.Table, error)
}

// cellsOf は表の列の順に、1 行分のセルを取り出す。
func cellsOf(columns []string, row chapter02.Row) (map[string]string, error) {
	cells := make(map[string]string, len(columns))

	for _, column := range columns {
		cell, err := row.Text(column)
		if err != nil {
			return nil, err
		}

		cells[column] = cell
	}

	return cells, nil
}

// withColumn は列の値を置き換えた（列が無ければ足した）新しい行を返す。元の行は変更しない。
func withColumn(columns []string, row chapter02.Row, column, value string) (chapter02.Row, error) {
	cells, err := cellsOf(columns, row)
	if err != nil {
		return chapter02.Row{}, err
	}

	cells[column] = value

	return chapter02.NewRow(cells), nil
}

// groupKey は複数の列の値を 1 つの文字列にまとめ、map のキーにできるようにする。
// Go の map のキーは比較できる型でなければならず、スライスは使えない。
func groupKey(row chapter02.Row, by []string) (string, error) {
	values := make([]string, len(by))

	for i, column := range by {
		value, err := row.Text(column)
		if err != nil {
			return "", err
		}

		values[i] = value
	}

	// 値そのものに現れない区切り文字（Unit Separator）でつなぐ。
	return strings.Join(values, "\x1f"), nil
}

// median は中央値。件数が偶数なら中央の 2 つの平均。
func median(values []float64) (float64, error) {
	if len(values) == 0 {
		return 0, fmt.Errorf("値がありません")
	}

	sorted := slices.Clone(values)
	slices.Sort(sorted)

	middle := len(sorted) / 2
	if len(sorted)%2 == 1 {
		return sorted[middle], nil
	}

	return (sorted[middle-1] + sorted[middle]) / 2, nil
}
