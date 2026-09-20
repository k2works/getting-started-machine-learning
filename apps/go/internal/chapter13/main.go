package chapter13

import (
	"fmt"
	"io"
	"path/filepath"
	"strings"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	// threshold は残す主成分を決める累積寄与率の目安。
	threshold = 0.8
	// topK は主成分ごとに表示する列の数。
	topK = 3
	// componentsToExplain は意味を読む主成分の数。
	componentsToExplain = 2
)

// Run はボストンの住宅価格の 15 列を主成分分析し、寄与率と主成分の意味を表示する。
func Run(out io.Writer) error {
	features, err := LoadBoston(filepath.Join(dataset.Current(), "Boston.csv"))
	if err != nil {
		return err
	}

	if len(features) == 0 {
		return fmt.Errorf("データが 1 件もありません")
	}

	columns := features[0].Columns
	points := ToPoints(features)

	model, err := Fit(points, len(columns))
	if err != nil {
		return err
	}

	needed := ComponentsNeeded(model.ExplainedVarianceRatio, threshold)

	cumulative := 0.0
	ratios := make([]string, needed)

	for i := range needed {
		cumulative += model.ExplainedVarianceRatio[i]
		ratios[i] = fmt.Sprintf("PC%d %.4f", i+1, model.ExplainedVarianceRatio[i])
	}

	lines := []string{
		fmt.Sprintf("データ件数: %d, 列数: %d", len(points), len(columns)),
		"寄与率: " + strings.Join(ratios, ", "),
		fmt.Sprintf("累積寄与率が %v に届く主成分の数: %d（累積寄与率 %.4f）", threshold, needed, cumulative),
	}

	for i := range componentsToExplain {
		loadings, err := TopLoadings(model.Components[i], columns, topK)
		if err != nil {
			return err
		}

		formatted := make([]string, len(loadings))
		for j, loading := range loadings {
			formatted[j] = fmt.Sprintf("%s %.3f", loading.Column, loading.Value)
		}

		lines = append(lines, fmt.Sprintf("第 %d 主成分で影響の大きい列: %s", i+1, strings.Join(formatted, ", ")))
	}

	for _, line := range lines {
		if _, err := fmt.Fprintln(out, line); err != nil {
			return fmt.Errorf("表示できません: %w", err)
		}
	}

	return nil
}
