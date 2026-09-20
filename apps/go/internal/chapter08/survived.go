package chapter08

import (
	"fmt"
	"slices"
	"strconv"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// SurvivedFeatures はモデルに渡す特徴量の列。
var SurvivedFeatures = []string{"Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked"}

// SurvivedTarget は正解ラベルの列（1 が生存、0 が死亡）。
const SurvivedTarget = "Survived"

// Survived は生存を表すラベル。
const Survived = 1

// FeaturesTable は行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。
func FeaturesTable(rows []chapter02.Row) chapter02.Table {
	return chapter02.Table{Columns: slices.Clone(SurvivedFeatures), Rows: rows}
}

// SurvivedLabels は行の Survived 列を、整数の正解ラベルにする。
func SurvivedLabels(rows []chapter02.Row) ([]int, error) {
	labels := make([]int, len(rows))

	for i, row := range rows {
		cell, err := row.Text(SurvivedTarget)
		if err != nil {
			return nil, err
		}

		label, err := strconv.Atoi(cell)
		if err != nil {
			return nil, fmt.Errorf("%s を整数として読めません: %w", SurvivedTarget, err)
		}

		labels[i] = label
	}

	return labels, nil
}

// Passenger は特徴量の列の順に並べた値から、乗客 1 人分の行を作る。空文字列は欠損値。
func Passenger(values ...string) (chapter02.Row, error) {
	if len(values) != len(SurvivedFeatures) {
		return chapter02.Row{}, fmt.Errorf("値の数が特徴量の列の数と違います: %d と %d", len(values), len(SurvivedFeatures))
	}

	cells := make(map[string]string, len(values))
	for i, column := range SurvivedFeatures {
		cells[column] = values[i]
	}

	return chapter02.NewRow(cells), nil
}
