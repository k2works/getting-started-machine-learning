package chapter11

import (
	"fmt"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
)

// SurvivedFeatures はこの章で使う Survived.csv の特徴量の列。
// 第 8 章のパイプラインより少ない 3 列に絞る。評価指標と交差検証に集中するため。
var SurvivedFeatures = []string{"Pclass", "Age", "male"}

// errMissing は空欄の行があるときのエラーを作る。
// staticcheck の ST1005 は英語の列名で始まるエラー文も「大文字で始まる」とみなすので、列名を後ろに回す。
func errMissing(column string) error {
	return fmt.Errorf("空欄の行があります: %s", column)
}

// Dataset は特徴量と正解ラベルの組。交差検証は分割の前に全件を受け取る。
type Dataset[T any] struct {
	X []chapter02.Features
	T []T
}

// PrepareSurvived は Pclass・Age・male（Sex が male なら 1）の 3 列を作る。
// 年齢の欠損値は全件の平均で補う。訓練データだけで補うのが本来だが、
// 交差検証では分け方ごとに訓練データが変わるので、この章は簡略化する。
func PrepareSurvived(table chapter02.Table) (Dataset[string], error) {
	means, err := chapter02.ColumnMeans(table.Rows, []string{"Age"})
	if err != nil {
		return Dataset[string]{}, err
	}

	x := make([]chapter02.Features, 0, len(table.Rows))
	t := make([]string, 0, len(table.Rows))

	for _, row := range table.Rows {
		pclass, ok, err := row.Number("Pclass")
		if err != nil {
			return Dataset[string]{}, err
		}

		if !ok {
			return Dataset[string]{}, errMissing("Pclass")
		}

		age, ok, err := row.Number("Age")
		if err != nil {
			return Dataset[string]{}, err
		}

		if !ok {
			age = means["Age"]
		}

		sex, err := row.Text("Sex")
		if err != nil {
			return Dataset[string]{}, err
		}

		male := 0.0
		if sex == "male" {
			male = 1
		}

		features, err := chapter02.NewFeatures(SurvivedFeatures, []float64{pclass, age, male})
		if err != nil {
			return Dataset[string]{}, err
		}

		label, err := row.Text("Survived")
		if err != nil {
			return Dataset[string]{}, err
		}

		x = append(x, features)
		t = append(t, label)
	}

	return Dataset[string]{X: x, T: t}, nil
}

// PrepareCinema は第 7 章と同じ 4 列の特徴量と興行収入を、全件分そろえる。
func PrepareCinema(table chapter02.Table) (Dataset[float64], error) {
	means, err := chapter02.ColumnMeans(table.Rows, chapter07.CinemaFeatures)
	if err != nil {
		return Dataset[float64]{}, err
	}

	x, err := chapter02.FillMissing(table.Rows, chapter07.CinemaFeatures, means)
	if err != nil {
		return Dataset[float64]{}, err
	}

	t := make([]float64, 0, len(table.Rows))

	for _, row := range table.Rows {
		sales, ok, err := row.Number(chapter07.CinemaTarget)
		if err != nil {
			return Dataset[float64]{}, err
		}

		if !ok {
			return Dataset[float64]{}, errMissing(chapter07.CinemaTarget)
		}

		t = append(t, sales)
	}

	return Dataset[float64]{X: x, T: t}, nil
}
