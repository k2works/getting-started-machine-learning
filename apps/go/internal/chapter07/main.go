package chapter07

import (
	"fmt"
	"io"
	"path/filepath"
	"strings"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	testSize = 0.2
	seed     = 0
	// simpleColumn は gonum の単回帰と突き合わせる列。
	simpleColumn = "SNS2"
)

// Run は映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。
func Run(out io.Writer) error {
	csvFile := filepath.Join(dataset.Current(), "cinema.csv")

	table, err := chapter02.LoadTable(csvFile)
	if err != nil {
		return err
	}

	cleaned, err := RemoveOutliers(table)
	if err != nil {
		return err
	}

	split, err := PrepareCinema(csvFile, testSize, seed)
	if err != nil {
		return err
	}

	model, err := Fit(split.XTrain, split.TTrain)
	if err != nil {
		return err
	}

	predictions, err := model.PredictAll(split.XTest)
	if err != nil {
		return err
	}

	r2, err := R2Score(split.TTest, predictions)
	if err != nil {
		return err
	}

	mae, err := MeanAbsoluteError(split.TTest, predictions)
	if err != nil {
		return err
	}

	rmse, err := RootMeanSquaredError(split.TTest, predictions)
	if err != nil {
		return err
	}

	simple, err := SimpleLinearRegression(split.XTrain, split.TTrain, simpleColumn)
	if err != nil {
		return err
	}

	simpleSlope, err := simple.Coefficient(simpleColumn)
	if err != nil {
		return err
	}

	lines := []string{
		fmt.Sprintf("データ件数: %d", len(table.Rows)),
		fmt.Sprintf("外れ値を除いた件数: %d", len(cleaned.Rows)),
		fmt.Sprintf("訓練データ: %d 件, テストデータ: %d 件", len(split.XTrain), len(split.XTest)),
		fmt.Sprintf("切片: %.2f", model.Intercept),
		"係数: " + formatCoefficients(model),
		fmt.Sprintf("テストデータの評価: R2=%.4f, MAE=%.2f, RMSE=%.2f", r2, mae, rmse),
		fmt.Sprintf("単回帰（%s のみ・gonum）: 切片=%.2f, 係数=%.4f", simpleColumn, simple.Intercept, simpleSlope),
	}

	for _, line := range lines {
		if _, err := fmt.Fprintln(out, line); err != nil {
			return fmt.Errorf("表示できません: %w", err)
		}
	}

	return nil
}

// formatCoefficients は係数を「列名=値」の並びにする。
func formatCoefficients(model LinearModel) string {
	formatted := make([]string, len(model.Columns))
	for i, column := range model.Columns {
		formatted[i] = fmt.Sprintf("%s=%.4f", column, model.Coefficients[i])
	}

	return strings.Join(formatted, ", ")
}
