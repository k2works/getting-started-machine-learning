package chapter14

import (
	"fmt"
	"io"
	"path/filepath"
	"strings"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	// seed は初期中心を選ぶ乱数のシード。
	seed = 0
	// nClusters は特徴を読むときのクラスタ数。
	nClusters = 5
	// maxClusterCount はエルボー法で試すクラスタ数の上限。
	maxClusterCount = 10
)

// Run は卸売業者の顧客を支出額でクラスタリングし、エルボー法の SSE とクラスタごとの特徴を表示する。
func Run(out io.Writer) error {
	x, err := LoadSpending(filepath.Join(dataset.Current(), "Wholesale.csv"))
	if err != nil {
		return err
	}

	if len(x) == 0 {
		return fmt.Errorf("データが 1 件もありません")
	}

	columns := x[0].Columns

	points, err := StandardizeSpending(x)
	if err != nil {
		return err
	}

	lines := []string{
		fmt.Sprintf("データ件数: %d（支出額 %d 列）", len(x), len(columns)),
		fmt.Sprintf("クラスタ数ごとの SSE（初期中心 %d 通りの最小値）:", DefaultNInit),
		"クラスタ数\tSSE",
	}

	clusterCounts := make([]int, maxClusterCount)
	for i := range clusterCounts {
		clusterCounts[i] = i + 1
	}

	sse, err := SSEByClusterCount(points, clusterCounts, seed, DefaultNInit)
	if err != nil {
		return err
	}

	for _, entry := range sse {
		lines = append(lines, fmt.Sprintf("%d\t%.2f", entry.Clusters, entry.SSE))
	}

	result, err := FitWithRestarts(points, nClusters, seed, DefaultNInit)
	if err != nil {
		return err
	}

	summaries, err := SummarizeClusters(x, result.Labels)
	if err != nil {
		return err
	}

	lines = append(lines,
		"",
		fmt.Sprintf("クラスタ数 %d のクラスタごとの件数と平均支出額:", nClusters),
		strings.Join(append([]string{"クラスタ", "件数"}, columns...), "\t"),
	)

	for _, summary := range summaries {
		cells := []string{fmt.Sprintf("%d", summary.Cluster), fmt.Sprintf("%d", summary.Count)}
		for _, mean := range summary.Means {
			cells = append(cells, fmt.Sprintf("%.0f", mean))
		}

		lines = append(lines, strings.Join(cells, "\t"))
	}

	for _, line := range lines {
		if _, err := fmt.Fprintln(out, line); err != nil {
			return fmt.Errorf("表示できません: %w", err)
		}
	}

	return nil
}
