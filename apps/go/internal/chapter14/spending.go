package chapter14

import (
	"fmt"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter09"
)

// Categories は区分を表す番号で、支出額ではない列。
var Categories = []string{"Channel", "Region"}

// ClusterSummary は 1 つのクラスタの特徴。
type ClusterSummary struct {
	// Cluster はクラスタ番号
	Cluster int
	// Count は所属する件数
	Count int
	// Columns は Means の並びに対応する列名
	Columns []string
	// Means は元の単位での列ごとの平均
	Means []float64
}

// LoadSpending は Channel と Region を除いた支出額の列を読み込む。欠損値があればエラーを返す。
func LoadSpending(csvFile string) ([]chapter02.Features, error) {
	table, err := chapter02.LoadTable(csvFile)
	if err != nil {
		return nil, err
	}

	columns := make([]string, 0, len(table.Columns))

	for _, column := range table.Columns {
		if !slices.Contains(Categories, column) {
			columns = append(columns, column)
		}
	}

	x := make([]chapter02.Features, 0, len(table.Rows))

	for i, row := range table.Rows {
		values := make([]float64, len(columns))

		for j, column := range columns {
			value, ok, err := row.Number(column)
			if err != nil {
				return nil, err
			}

			if !ok {
				return nil, fmt.Errorf("%d 行目の %s が欠損値です", i+1, column)
			}

			values[j] = value
		}

		features, err := chapter02.NewFeatures(columns, values)
		if err != nil {
			return nil, err
		}

		x = append(x, features)
	}

	return x, nil
}

// StandardizeSpending は第 9 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 つの点にする。
func StandardizeSpending(x []chapter02.Features) ([][]float64, error) {
	standardizer, err := chapter09.Fit(x)
	if err != nil {
		return nil, err
	}

	transformed, err := standardizer.Transform(x)
	if err != nil {
		return nil, err
	}

	points := make([][]float64, len(transformed))
	for i, features := range transformed {
		points[i] = features.Values
	}

	return points, nil
}

// SummarizeClusters はクラスタごとの件数と、元の単位での列ごとの平均を、件数の多い順に返す。
func SummarizeClusters(x []chapter02.Features, labels []int) ([]ClusterSummary, error) {
	if len(x) == 0 {
		return nil, fmt.Errorf("特徴量が 1 件もありません")
	}

	if len(x) != len(labels) {
		return nil, fmt.Errorf("特徴量とクラスタ番号の数が違います: %d と %d", len(x), len(labels))
	}

	columns := x[0].Columns
	clusters := make([]int, 0)
	members := make(map[int][]chapter02.Features)

	for i, label := range labels {
		if _, ok := members[label]; !ok {
			clusters = append(clusters, label)
		}

		members[label] = append(members[label], x[i])
	}

	slices.Sort(clusters)

	summaries := make([]ClusterSummary, 0, len(clusters))

	for _, cluster := range clusters {
		means, err := columnMeans(members[cluster], columns)
		if err != nil {
			return nil, err
		}

		summaries = append(summaries, ClusterSummary{
			Cluster: cluster,
			Count:   len(members[cluster]),
			Columns: slices.Clone(columns),
			Means:   means,
		})
	}

	// 件数が同じときにクラスタ番号の順が保たれるように、安定ソートで並べ替える
	slices.SortStableFunc(summaries, func(a, b ClusterSummary) int {
		return b.Count - a.Count
	})

	return summaries, nil
}

// columnMeans は列ごとの平均を求める。
func columnMeans(rows []chapter02.Features, columns []string) ([]float64, error) {
	means := make([]float64, len(columns))

	for j, column := range columns {
		sum := 0.0

		for _, features := range rows {
			value, err := features.Value(column)
			if err != nil {
				return nil, err
			}

			sum += value
		}

		means[j] = sum / float64(len(rows))
	}

	return means, nil
}
