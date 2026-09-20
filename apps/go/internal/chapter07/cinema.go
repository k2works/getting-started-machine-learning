package chapter07

import (
	"fmt"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// CinemaFeatures は映画の興行収入のデータで使う特徴量の列。
var CinemaFeatures = []string{"SNS1", "SNS2", "actor", "original"}

// CinemaTarget は予測する列（興行収入）。
const CinemaTarget = "sales"

const (
	// outlierSNS2 は外れ値とみなす SNS2 の下限。
	outlierSNS2 = 1000
	// outlierSales は外れ値とみなす興行収入の上限。
	outlierSales = 8500
)

// number は欠損値を許さずに数値の列を読む。
func number(row chapter02.Row, column string) (float64, error) {
	value, ok, err := row.Number(column)
	if err != nil {
		return 0, err
	}

	if !ok {
		return 0, fmt.Errorf("欠損値です: %s", column)
	}

	return value, nil
}

// isOutlier は「SNS2 が多いのに興行収入が少ない」行かどうかを返す。
func isOutlier(row chapter02.Row) (bool, error) {
	sns2, err := number(row, "SNS2")
	if err != nil {
		return false, err
	}

	sales, err := number(row, CinemaTarget)
	if err != nil {
		return false, err
	}

	return sns2 > outlierSNS2 && sales < outlierSales, nil
}

// RemoveOutliers は外れ値の行を除いた表を返す。元の表は変更しない。
func RemoveOutliers(table chapter02.Table) (chapter02.Table, error) {
	rows := make([]chapter02.Row, 0, len(table.Rows))

	for _, row := range table.Rows {
		outlier, err := isOutlier(row)
		if err != nil {
			return chapter02.Table{}, err
		}

		if !outlier {
			rows = append(rows, row)
		}
	}

	return chapter02.Table{Columns: table.Columns, Rows: rows}, nil
}

// PrepareCinema は外れ値を除き、分割してから訓練データの平均値で両方の欠損値を補完する。
func PrepareCinema(csvFile string, testSize float64, seed int64) (chapter02.TrainTestSplit[chapter02.Features, float64], error) {
	empty := chapter02.TrainTestSplit[chapter02.Features, float64]{}

	loaded, err := chapter02.LoadTable(csvFile)
	if err != nil {
		return empty, err
	}

	table, err := RemoveOutliers(loaded)
	if err != nil {
		return empty, err
	}

	target := make([]float64, 0, len(table.Rows))

	for _, row := range table.Rows {
		sales, err := number(row, CinemaTarget)
		if err != nil {
			return empty, err
		}

		target = append(target, sales)
	}

	split, err := chapter02.SplitTrainTest(table.Rows, target, testSize, seed)
	if err != nil {
		return empty, err
	}

	means, err := chapter02.ColumnMeans(split.XTrain, CinemaFeatures)
	if err != nil {
		return empty, err
	}

	xTrain, err := chapter02.FillMissing(split.XTrain, CinemaFeatures, means)
	if err != nil {
		return empty, err
	}

	xTest, err := chapter02.FillMissing(split.XTest, CinemaFeatures, means)
	if err != nil {
		return empty, err
	}

	return chapter02.TrainTestSplit[chapter02.Features, float64]{
		XTrain: xTrain,
		XTest:  xTest,
		TTrain: split.TTrain,
		TTest:  split.TTest,
	}, nil
}
