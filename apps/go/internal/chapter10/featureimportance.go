package chapter10

import (
	"fmt"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter03"
)

// Importance は特徴量と、その重要度。列の順に並べて使う。
type Importance struct {
	Feature string
	Value   float64
}

// TreeImportances は決定木 1 本の重要度を、特徴量の列の順に並べて返す。合計は 1 になる。
func TreeImportances(tree chapter03.Tree, x []chapter02.Features, t []string) ([]Importance, error) {
	totals := make(map[string]float64, len(x[0].Columns))

	if err := addDecreases(tree, x, t, totals); err != nil {
		return nil, err
	}

	return normalize(x[0].Columns, totals), nil
}

// TreeImportancesOf は深さを制限しない決定木を学習してから、その重要度を求める。
func TreeImportancesOf(x []chapter02.Features, t []string) ([]Importance, error) {
	model := chapter03.Unlimited()
	if err := model.Fit(x, t); err != nil {
		return nil, err
	}

	tree, ok := model.Tree()
	if !ok {
		return nil, fmt.Errorf("決定木がまだ作られていません")
	}

	return TreeImportances(tree, x, t)
}

// addDecreases は分割で減った不純度（件数で重み付け）を、特徴量ごとに足し込む。
func addDecreases(tree chapter03.Tree, x []chapter02.Features, t []string, totals map[string]float64) error {
	node, ok := tree.(chapter03.Node)
	if !ok {
		// 葉には分割が無いので、減った不純度も無い
		return nil
	}

	var leftX, rightX []chapter02.Features

	var leftT, rightT []string

	for i, features := range x {
		value, err := features.Value(node.Split.Feature)
		if err != nil {
			return err
		}

		if value <= node.Split.Threshold {
			leftX, leftT = append(leftX, features), append(leftT, t[i])
		} else {
			rightX, rightT = append(rightX, features), append(rightT, t[i])
		}
	}

	totals[node.Split.Feature] += float64(len(t)) * (chapter03.Gini(t) - node.Split.Impurity)

	if err := addDecreases(node.Left, leftX, leftT, totals); err != nil {
		return err
	}

	return addDecreases(node.Right, rightX, rightT, totals)
}

// normalize は合計が 1 になるようにそろえ、列の順に並べて返す。合計が 0 ならそのまま返す。
func normalize(columns []string, totals map[string]float64) []Importance {
	sum := 0.0
	for _, value := range totals {
		sum += value
	}

	importances := make([]Importance, len(columns))

	for i, column := range columns {
		value := totals[column]
		if sum != 0 {
			value /= sum
		}

		importances[i] = Importance{Feature: column, Value: value}
	}

	return importances
}

// ForestImportances は木ごとに正規化した重要度の平均を返す。木が使わなかった特徴量は、その木では 0 とする。
func ForestImportances(forest *RandomForest, x []chapter02.Features, t []string) ([]Importance, error) {
	if len(forest.Trees()) == 0 {
		return nil, fmt.Errorf("Fit で学習してから ForestImportances を呼んでください")
	}

	totals := make(map[string]float64, len(x[0].Columns))

	for _, fitted := range forest.Trees() {
		sampleX := make([]chapter02.Features, len(fitted.Rows))
		sampleT := make([]string, len(fitted.Rows))

		for i, row := range fitted.Rows {
			sampleX[i] = x[row]
			sampleT[i] = t[row]
		}

		selected, err := SelectColumns(sampleX, fitted.Columns)
		if err != nil {
			return nil, err
		}

		tree, ok := fitted.Model.Tree()
		if !ok {
			return nil, fmt.Errorf("決定木がまだ作られていません")
		}

		importances, err := TreeImportances(tree, selected, sampleT)
		if err != nil {
			return nil, err
		}

		for _, importance := range importances {
			totals[importance.Feature] += importance.Value / float64(len(forest.Trees()))
		}
	}

	return normalize(x[0].Columns, totals), nil
}
