package chapter11

import (
	"fmt"
	"io"
	"path/filepath"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter03"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	// nSplits は交差検証の分割数。
	nSplits = 5
	// seed は分け方を再現するための乱数の種。
	seed = 0
	// treeDepth は決定木の深さ。
	treeDepth = 2
	// Survived は陽性とみなすラベル（生存）。
	Survived = "1"
)

// NamedMetric は表示する名前と評価関数。map は反復順が決まらないので、並びを持つ構造体にする。
type NamedMetric[T any] struct {
	Name   string
	Metric Metric[T]
}

// SurvivedMetrics は分類の評価指標を、表示する順に返す。
func SurvivedMetrics() []NamedMetric[string] {
	return []NamedMetric[string]{
		{Name: "正解率", Metric: Accuracy[string]},
		{Name: "適合率", Metric: ClassificationMetric(Precision, Survived)},
		{Name: "再現率", Metric: ClassificationMetric(Recall, Survived)},
		{Name: "F 値", Metric: ClassificationMetric(F1Score, Survived)},
	}
}

// CinemaMetrics は回帰の評価指標を、表示する順に返す。
func CinemaMetrics() []NamedMetric[float64] {
	return []NamedMetric[float64]{
		{Name: "RMSE", Metric: chapter07.RootMeanSquaredError},
		{Name: "MAE", Metric: chapter07.MeanAbsoluteError},
		{Name: "MSE", Metric: MeanSquaredError},
	}
}

// Evaluate は同じ分け方をすべての評価指標で使い回し、分割ごとのスコアの平均を返す。
func Evaluate[T any](
	makeModel func() (Model[T], error),
	data Dataset[T],
	metrics []NamedMetric[T],
) ([]float64, error) {
	folds, err := KFold(len(data.X), nSplits, seed)
	if err != nil {
		return nil, err
	}

	means := make([]float64, 0, len(metrics))

	for _, metric := range metrics {
		scores, err := CrossValidate(makeModel, data.X, data.T, folds, metric.Metric)
		if err != nil {
			return nil, err
		}

		mean, err := Mean(scores)
		if err != nil {
			return nil, err
		}

		means = append(means, mean)
	}

	return means, nil
}

// newTree は深さ 2 の決定木を Model[string] として作る。
func newTree() (Model[string], error) {
	return chapter03.WithMaxDepth(treeDepth)
}

// newLogistic はロジスティック回帰を Model[string] として作る。
func newLogistic() (Model[string], error) {
	return NewLogisticModel(Survived), nil
}

// Run は Survived と cinema を交差検証で評価し、ROC 曲線の AUC を自作と gonum で突き合わせる。
func Run(out io.Writer) error {
	survived, err := chapter02.LoadTable(filepath.Join(dataset.Current(), "Survived.csv"))
	if err != nil {
		return err
	}

	survivedData, err := PrepareSurvived(survived)
	if err != nil {
		return err
	}

	models := []struct {
		name      string
		makeModel func() (Model[string], error)
	}{
		{name: "決定木（深さ 2）", makeModel: newTree},
		{name: "ロジスティック回帰", makeModel: newLogistic},
	}

	if err := writeLine(out, "Survived（%d 分割交差検証の平均）", nSplits); err != nil {
		return err
	}

	for _, model := range models {
		means, err := Evaluate(model.makeModel, survivedData, SurvivedMetrics())
		if err != nil {
			return err
		}

		if err := writeLine(out, "  %s", model.name); err != nil {
			return err
		}

		for i, metric := range SurvivedMetrics() {
			if err := writeLine(out, "    %s: %.4f", metric.Name, means[i]); err != nil {
				return err
			}
		}
	}

	if err := printROC(out, survivedData); err != nil {
		return err
	}

	return printCinema(out)
}

// printROC はロジスティック回帰の確率から AUC を求め、自作と gonum を並べる。
func printROC(out io.Writer, data Dataset[string]) error {
	model := NewLogisticModel(Survived)
	if err := model.Fit(data.X, data.T); err != nil {
		return err
	}

	probabilities, err := model.PredictProba(data.X)
	if err != nil {
		return err
	}

	points, err := ROCCurve(probabilities, data.T, Survived)
	if err != nil {
		return err
	}

	own, err := AUC(points)
	if err != nil {
		return err
	}

	gonumAUC, err := GonumAUC(probabilities, data.T, Survived)
	if err != nil {
		return err
	}

	if err := writeLine(out, "ROC 曲線（ロジスティック回帰、全件で学習）"); err != nil {
		return err
	}

	if err := writeLine(out, "  点の数: %d", len(points)); err != nil {
		return err
	}

	if err := writeLine(out, "  AUC（自作）: %.4f", own); err != nil {
		return err
	}

	return writeLine(out, "  AUC（gonum）: %.4f", gonumAUC)
}

// printCinema は cinema の交差検証の結果を表示する。
func printCinema(out io.Writer) error {
	table, err := chapter02.LoadTable(filepath.Join(dataset.Current(), "cinema.csv"))
	if err != nil {
		return err
	}

	cleaned, err := chapter07.RemoveOutliers(table)
	if err != nil {
		return err
	}

	data, err := PrepareCinema(cleaned)
	if err != nil {
		return err
	}

	means, err := Evaluate(func() (Model[float64], error) {
		return NewLinearRegressionModel(), nil
	}, data, CinemaMetrics())
	if err != nil {
		return err
	}

	if err := writeLine(out, "cinema（線形回帰、%d 分割交差検証の平均）", nSplits); err != nil {
		return err
	}

	for i, metric := range CinemaMetrics() {
		if err := writeLine(out, "  %s: %.2f", metric.Name, means[i]); err != nil {
			return err
		}
	}

	return nil
}

// writeLine は 1 行書き出す。io.Writer のエラーを毎回たたむのを 1 か所にまとめる。
func writeLine(out io.Writer, format string, args ...any) error {
	if _, err := fmt.Fprintf(out, format+"\n", args...); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	return nil
}
