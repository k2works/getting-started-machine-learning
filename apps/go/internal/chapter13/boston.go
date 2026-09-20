package chapter13

import (
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter09"
)

// BostonCategory はカテゴリ値の列。
const BostonCategory = "CRIME"

// StandardizeBoston は CRIME をダミー変数にし、欠損値を列の平均値で補完してから、すべての列を標準化する。
// 正解ラベルを予測しないので、PRICE も 1 つの列として扱い、訓練データとテストデータに分けない。
func StandardizeBoston(table chapter02.Table) ([]chapter02.Features, error) {
	crimes := make([]string, 0, len(table.Rows))

	for _, row := range table.Rows {
		crime, err := row.Text(BostonCategory)
		if err != nil {
			return nil, err
		}

		crimes = append(crimes, crime)
	}

	encoded, err := chapter09.Encode(table, BostonCategory, chapter09.Categories(crimes))
	if err != nil {
		return nil, err
	}

	means, err := chapter02.ColumnMeans(encoded.Rows, encoded.Columns)
	if err != nil {
		return nil, err
	}

	filled, err := chapter02.FillMissing(encoded.Rows, encoded.Columns, means)
	if err != nil {
		return nil, err
	}

	standardizer, err := chapter09.Fit(filled)
	if err != nil {
		return nil, err
	}

	return standardizer.Transform(filled)
}

// LoadBoston は CSV を読み込んで前処理する。
func LoadBoston(csvFile string) ([]chapter02.Features, error) {
	table, err := chapter02.LoadTable(csvFile)
	if err != nil {
		return nil, err
	}

	return StandardizeBoston(table)
}

// ToPoints は特徴量のリストを、1 件を 1 行とする数値の並びにする。
func ToPoints(x []chapter02.Features) [][]float64 {
	points := make([][]float64, len(x))
	for i, features := range x {
		points[i] = features.Values
	}

	return points
}
