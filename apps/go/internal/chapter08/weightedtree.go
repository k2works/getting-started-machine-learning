// Package chapter08 は前処理のパイプラインと、重み付きの決定木で分類する。
package chapter08

import (
	"fmt"
	"sort"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter03"
)

// Unlimited は深さを制限しないことを表す値。
const Unlimited = -1

// Tree は重み付きの決定木。Leaf か Node のどちらか。
// 第 3 章の決定木はラベルが文字列なので、整数のラベルを持つ木をこの章で作り直す。
type Tree interface {
	isTree()
}

// Leaf は予測するラベルを持つ葉。
type Leaf struct {
	Label int
}

func (Leaf) isTree() {}

// Node は分割と、左右の部分木を持つ節。分割は第 3 章の Split をそのまま使う。
type Node struct {
	Split chapter03.Split
	Left  Tree
	Right Tree
}

func (Node) isTree() {}

// weightSums はラベルごとの重みの合計を、ラベルが先に現れた順に返す。
func weightSums(labels []int, weights []float64) ([]int, map[int]float64, error) {
	if len(labels) != len(weights) {
		return nil, nil, fmt.Errorf("ラベルと重みの件数が違います: %d と %d", len(labels), len(weights))
	}

	order := make([]int, 0, len(labels))
	sums := make(map[int]float64, len(labels))

	for i, label := range labels {
		if _, ok := sums[label]; !ok {
			order = append(order, label)
		}

		sums[label] += weights[i]
	}

	return order, sums, nil
}

// WeightedGini は重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。
func WeightedGini(labels []int, weights []float64) (float64, error) {
	order, sums, err := weightSums(labels, weights)
	if err != nil {
		return 0, err
	}

	total := 0.0
	for _, weight := range weights {
		total += weight
	}

	if total == 0 {
		return 0, fmt.Errorf("重みの合計が 0 です")
	}

	sum := 0.0

	for _, label := range order {
		share := sums[label] / total
		sum += share * share
	}

	return 1.0 - sum, nil
}

// BalancedWeights はクラスの件数に反比例する重み（件数 /（クラスの数 × そのクラスの件数））を 1 件ごとに求める。
func BalancedWeights(t []int) ([]float64, error) {
	if len(t) == 0 {
		return nil, fmt.Errorf("正解ラベルがありません")
	}

	counts := make(map[int]int, len(t))
	for _, label := range t {
		counts[label]++
	}

	weights := make([]float64, len(t))
	for i, label := range t {
		weights[i] = float64(len(t)) / float64(len(counts)*counts[label])
	}

	return weights, nil
}

// WeightedMajority は重みの合計が最も大きいラベルを返す。同じなら先に現れたラベルを選ぶ。
func WeightedMajority(labels []int, weights []float64) (int, error) {
	order, sums, err := weightSums(labels, weights)
	if err != nil {
		return 0, err
	}

	if len(order) == 0 {
		return 0, fmt.Errorf("ラベルがありません")
	}

	best, bestWeight := order[0], 0.0

	for _, label := range order {
		if sums[label] > bestWeight {
			best, bestWeight = label, sums[label]
		}
	}

	return best, nil
}

// weightedValue は並べ替えのあいだ、特徴量の値・正解ラベル・重みの対応を保つための組。
type weightedValue struct {
	value  float64
	label  int
	weight float64
}

// sortByFeature は 1 つの特徴量の値で昇順に並べ替える。
func sortByFeature(x []chapter02.Features, t []int, w []float64, feature string) ([]weightedValue, error) {
	sorted := make([]weightedValue, len(x))

	for i, features := range x {
		value, err := features.Value(feature)
		if err != nil {
			return nil, err
		}

		sorted[i] = weightedValue{value: value, label: t[i], weight: w[i]}
	}

	sort.SliceStable(sorted, func(i, j int) bool { return sorted[i].value < sorted[j].value })

	return sorted, nil
}

// splitGini は並べ替えた一部分の重み付きジニ不純度と、重みの合計を返す。
func splitGini(part []weightedValue) (float64, float64, error) {
	labels := make([]int, len(part))
	weights := make([]float64, len(part))
	total := 0.0

	for i, item := range part {
		labels[i] = item.label
		weights[i] = item.weight
		total += item.weight
	}

	gini, err := WeightedGini(labels, weights)
	if err != nil {
		return 0, 0, err
	}

	return gini, total, nil
}

// BestSplit は左右の重み付き不純度の、重みによる平均が最も小さくなる分割を返す。
// 見つからなければ ok が false になる。同じ不純度なら先に見つけた分割を選ぶ。
func BestSplit(x []chapter02.Features, t []int, w []float64) (chapter03.Split, bool, error) {
	gini, err := WeightedGini(t, w)
	if err != nil {
		return chapter03.Split{}, false, err
	}

	if gini == 0.0 {
		return chapter03.Split{}, false, nil
	}

	var best chapter03.Split

	found := false

	for _, feature := range x[0].Columns {
		sorted, err := sortByFeature(x, t, w, feature)
		if err != nil {
			return chapter03.Split{}, false, err
		}

		for i := 1; i < len(sorted); i++ {
			if sorted[i].value == sorted[i-1].value {
				continue
			}

			leftGini, leftWeight, err := splitGini(sorted[:i])
			if err != nil {
				return chapter03.Split{}, false, err
			}

			rightGini, rightWeight, err := splitGini(sorted[i:])
			if err != nil {
				return chapter03.Split{}, false, err
			}

			impurity := (leftWeight*leftGini + rightWeight*rightGini) / (leftWeight + rightWeight)

			if !found || impurity < best.Impurity {
				best = chapter03.Split{
					Feature:   feature,
					Threshold: (sorted[i-1].value + sorted[i].value) / 2,
					Impurity:  impurity,
				}
				found = true
			}
		}
	}

	return best, found, nil
}

// Build は深さの上限まで分割を繰り返して木を作る。maxDepth が負なら上限なし。
func Build(x []chapter02.Features, t []int, w []float64, maxDepth int) (Tree, error) {
	if maxDepth == 0 {
		return leafOf(t, w)
	}

	split, found, err := BestSplit(x, t, w)
	if err != nil {
		return nil, err
	}

	if !found {
		return leafOf(t, w)
	}

	var leftX, rightX []chapter02.Features

	var leftT, rightT []int

	var leftW, rightW []float64

	for i, features := range x {
		goLeft, err := goesLeft(split, features)
		if err != nil {
			return nil, err
		}

		if goLeft {
			leftX, leftT, leftW = append(leftX, features), append(leftT, t[i]), append(leftW, w[i])
		} else {
			rightX, rightT, rightW = append(rightX, features), append(rightT, t[i]), append(rightW, w[i])
		}
	}

	childDepth := maxDepth
	if maxDepth > 0 {
		childDepth = maxDepth - 1
	}

	left, err := Build(leftX, leftT, leftW, childDepth)
	if err != nil {
		return nil, err
	}

	right, err := Build(rightX, rightT, rightW, childDepth)
	if err != nil {
		return nil, err
	}

	return Node{Split: split, Left: left, Right: right}, nil
}

// leafOf は重みの合計が最も大きいラベルを持つ葉を作る。
func leafOf(t []int, w []float64) (Tree, error) {
	label, err := WeightedMajority(t, w)
	if err != nil {
		return nil, err
	}

	return Leaf{Label: label}, nil
}

// goesLeft は特徴量が境界以下かどうかを返す。
func goesLeft(split chapter03.Split, features chapter02.Features) (bool, error) {
	value, err := features.Value(split.Feature)
	if err != nil {
		return false, err
	}

	return value <= split.Threshold, nil
}

// PredictOne は 1 件の特徴量のラベルを予測する。
// Go は型スイッチの網羅性を検査しないので、知らない木は既定の分岐でエラーにする。
func PredictOne(tree Tree, features chapter02.Features) (int, error) {
	switch node := tree.(type) {
	case Leaf:
		return node.Label, nil
	case Node:
		left, err := goesLeft(node.Split, features)
		if err != nil {
			return 0, err
		}

		if left {
			return PredictOne(node.Left, features)
		}

		return PredictOne(node.Right, features)
	default:
		return 0, fmt.Errorf("知らない木です: %T", tree)
	}
}
