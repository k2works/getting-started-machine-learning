package chapter12

import (
	"fmt"
	"math"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// BostonFeatures はこの章で使う Boston.csv の列。3 列に絞ってから多項式特徴量で 9 列に増やす。
var BostonFeatures = []string{"RM", "PTRATIO", "LSTAT"}

// BostonTarget は予測する列（住宅価格）。
const BostonTarget = "PRICE"

// OutlierThreshold は外れ値とみなす標準得点の大きさ。
const OutlierThreshold = 3.0

// BostonDataset は訓練・検証・テストの 3 つに分けたデータ。
// 検証データは alpha を選ぶために使い、テストデータは最後に 1 回だけ使う。
type BostonDataset struct {
	XTrain      []chapter02.Features
	TTrain      []float64
	XValidation []chapter02.Features
	TValidation []float64
	XTest       []chapter02.Features
	TTest       []float64
	Columns     []string
	Removed     int
}

// sampleStdDev は標本標準偏差。差の 2 乗の和を「件数 − 1」で割ってから平方根を取る。
func sampleStdDev(values []float64) float64 {
	mean := 0.0
	for _, value := range values {
		mean += value / float64(len(values))
	}

	sum := 0.0
	for _, value := range values {
		sum += (value - mean) * (value - mean)
	}

	return math.Sqrt(sum / float64(len(values)-1))
}

// RemoveOutliers は、いずれかの列で標準得点の絶対値が threshold を超える行を除く。
func RemoveOutliers(table chapter02.Table, columns []string, threshold float64) (chapter02.Table, error) {
	means := make(map[string]float64, len(columns))
	stds := make(map[string]float64, len(columns))

	for _, column := range columns {
		values := make([]float64, 0, len(table.Rows))

		for _, row := range table.Rows {
			value, ok, err := row.Number(column)
			if err != nil {
				return chapter02.Table{}, err
			}

			if !ok {
				return chapter02.Table{}, fmt.Errorf("空欄の行があります: %s", column)
			}

			values = append(values, value)
		}

		mean := 0.0
		for _, value := range values {
			mean += value / float64(len(values))
		}

		means[column] = mean
		stds[column] = sampleStdDev(values)
	}

	rows := make([]chapter02.Row, 0, len(table.Rows))

	for _, row := range table.Rows {
		outlier := false

		for _, column := range columns {
			value, _, err := row.Number(column)
			if err != nil {
				return chapter02.Table{}, err
			}

			if stds[column] != 0 && math.Abs((value-means[column])/stds[column]) > threshold {
				outlier = true

				break
			}
		}

		if !outlier {
			rows = append(rows, row)
		}
	}

	return chapter02.Table{Columns: table.Columns, Rows: rows}, nil
}

// PrepareBoston は外れ値を除き、訓練・検証・テストの 3 つに分けてから、
// 訓練データで合わせた前処理（標準化と多項式特徴量）を 3 つすべてに適用する。
func PrepareBoston(csvFile string, testSize, validationSize float64, seed int64) (BostonDataset, error) {
	table, err := chapter02.LoadTable(csvFile)
	if err != nil {
		return BostonDataset{}, err
	}

	columns := append(append([]string{}, BostonFeatures...), BostonTarget)

	cleaned, err := RemoveOutliers(table, columns, OutlierThreshold)
	if err != nil {
		return BostonDataset{}, err
	}

	means, err := chapter02.ColumnMeans(cleaned.Rows, BostonFeatures)
	if err != nil {
		return BostonDataset{}, err
	}

	x, err := chapter02.FillMissing(cleaned.Rows, BostonFeatures, means)
	if err != nil {
		return BostonDataset{}, err
	}

	t := make([]float64, 0, len(cleaned.Rows))

	for _, row := range cleaned.Rows {
		price, ok, err := row.Number(BostonTarget)
		if err != nil {
			return BostonDataset{}, err
		}

		if !ok {
			return BostonDataset{}, fmt.Errorf("空欄の行があります: %s", BostonTarget)
		}

		t = append(t, price)
	}

	outer, err := chapter02.SplitTrainTest(x, t, testSize, seed)
	if err != nil {
		return BostonDataset{}, err
	}

	inner, err := chapter02.SplitTrainTest(outer.XTrain, outer.TTrain, validationSize, seed)
	if err != nil {
		return BostonDataset{}, err
	}

	scaler, err := FitScaler(inner.XTrain)
	if err != nil {
		return BostonDataset{}, err
	}

	transformed := make([][]chapter02.Features, 3)

	for i, part := range [][]chapter02.Features{inner.XTrain, inner.XTest, outer.XTest} {
		converted, err := scaler.Transform(part)
		if err != nil {
			return BostonDataset{}, err
		}

		transformed[i] = converted
	}

	return BostonDataset{
		XTrain:      transformed[0],
		TTrain:      inner.TTrain,
		XValidation: transformed[1],
		TValidation: inner.TTest,
		XTest:       transformed[2],
		TTest:       outer.TTest,
		Columns:     scaler.FeatureNames(),
		Removed:     len(table.Rows) - len(cleaned.Rows),
	}, nil
}
