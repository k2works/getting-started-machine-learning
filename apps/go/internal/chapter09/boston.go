package chapter09

import (
	"fmt"
	"strconv"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

const (
	// BostonTarget はボストンの住宅価格の正解の列。
	BostonTarget = "PRICE"
	// BostonCategory はカテゴリ値の列。
	BostonCategory = "CRIME"
)

// Scores は訓練データとテストデータの決定係数。
type Scores struct {
	Train float64
	Test  float64
}

// PrepareBoston は CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。
func PrepareBoston(
	csvFile string,
	testSize float64,
	seed int64,
) (chapter02.TrainTestSplit[chapter02.Features, float64], error) {
	empty := chapter02.TrainTestSplit[chapter02.Features, float64]{}

	table, err := chapter02.LoadTable(csvFile)
	if err != nil {
		return empty, err
	}

	crimes := make([]string, 0, len(table.Rows))

	for _, row := range table.Rows {
		crime, err := row.Text(BostonCategory)
		if err != nil {
			return empty, err
		}

		crimes = append(crimes, crime)
	}

	encoded, err := Encode(table, BostonCategory, Categories(crimes))
	if err != nil {
		return empty, err
	}

	columns, rows, labels, err := chapter02.SplitFeaturesAndTarget(encoded, BostonTarget)
	if err != nil {
		return empty, err
	}

	prices := make([]float64, len(labels))

	for i, label := range labels {
		price, err := strconv.ParseFloat(label, 64)
		if err != nil {
			return empty, fmt.Errorf("%s を数値として読めません: %w", BostonTarget, err)
		}

		prices[i] = price
	}

	split, err := chapter02.SplitTrainTest(rows, prices, testSize, seed)
	if err != nil {
		return empty, err
	}

	means, err := chapter02.ColumnMeans(split.XTrain, columns)
	if err != nil {
		return empty, err
	}

	xTrain, err := chapter02.FillMissing(split.XTrain, columns, means)
	if err != nil {
		return empty, err
	}

	xTest, err := chapter02.FillMissing(split.XTest, columns, means)
	if err != nil {
		return empty, err
	}

	return chapter02.TrainTestSplit[chapter02.Features, float64]{
		XTrain: xTrain, XTest: xTest, TTrain: split.TTrain, TTest: split.TTest,
	}, nil
}

// ScoreFeatureSet は列から多項式特徴量を作って terms の項を選び、
// 訓練データで標準化してから線形回帰で学習し、決定係数を求める。
func ScoreFeatureSet(
	split chapter02.TrainTestSplit[chapter02.Features, float64],
	columns []string,
	terms []string,
) (Scores, error) {
	train, err := selectTerms(split.XTrain, columns, terms)
	if err != nil {
		return Scores{}, err
	}

	test, err := selectTerms(split.XTest, columns, terms)
	if err != nil {
		return Scores{}, err
	}

	standardizer, err := Fit(train)
	if err != nil {
		return Scores{}, err
	}

	xTrain, err := toRows(standardizer, train)
	if err != nil {
		return Scores{}, err
	}

	xTest, err := toRows(standardizer, test)
	if err != nil {
		return Scores{}, err
	}

	model, err := FitLinearModel(xTrain, split.TTrain)
	if err != nil {
		return Scores{}, err
	}

	return Scores{
		Train: RSquared(split.TTrain, model.Predict(xTrain)),
		Test:  RSquared(split.TTest, model.Predict(xTest)),
	}, nil
}

func selectTerms(x []chapter02.Features, columns, terms []string) ([]chapter02.Features, error) {
	expanded, err := Expand(x, columns)
	if err != nil {
		return nil, err
	}

	return Select(expanded, terms)
}

func toRows(standardizer Standardizer, x []chapter02.Features) ([][]float64, error) {
	transformed, err := standardizer.Transform(x)
	if err != nil {
		return nil, err
	}

	rows := make([][]float64, len(transformed))
	for i, features := range transformed {
		rows[i] = features.Values
	}

	return rows, nil
}
