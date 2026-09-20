package chapter02

import (
	"fmt"
	"io"
	"path/filepath"
	"strings"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	testSize = 0.3
	seed     = 0
)

// Run はアヤメのデータの前処理の結果を表示する。
func Run(out io.Writer) error {
	csvFile := filepath.Join(dataset.Current(), "iris.csv")

	table, err := LoadTable(csvFile)
	if err != nil {
		return err
	}

	counts, err := table.CountMissing()
	if err != nil {
		return err
	}

	split, err := PrepareIris(csvFile, testSize, seed)
	if err != nil {
		return err
	}

	formatted := make([]string, len(counts))
	for i, missing := range counts {
		formatted[i] = fmt.Sprintf("%s=%d", missing.Column, missing.Count)
	}

	lines := []string{
		fmt.Sprintf("データ件数: %d", len(table.Rows)),
		"欠損値の数: " + strings.Join(formatted, ", "),
		fmt.Sprintf("訓練データ: %d 件, テストデータ: %d 件", len(split.XTrain), len(split.XTest)),
		"特徴量: " + strings.Join(split.XTrain[0].Columns, ", "),
	}

	for _, line := range lines {
		if _, err := fmt.Fprintln(out, line); err != nil {
			return fmt.Errorf("表示できません: %w", err)
		}
	}

	return nil
}
