// Package chapter03 は、ジニ不純度で分割する決定木を自作する。
package chapter03

import (
	"fmt"
	"slices"
	"sort"
	"strings"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// unlimited は深さを制限しないことを表す値。
const unlimited = -1

// Split は決定木の分割。特徴量の値が境界以下なら左、境界より大きければ右へ進む。
type Split struct {
	Feature   string
	Threshold float64
	Impurity  float64
}

// Tree は決定木。Leaf か Node のどちらか。
// Go には判別共用体が無いので、非公開のメソッドを持つインターフェースで、
// このパッケージの外から実装を足せないようにする。
type Tree interface {
	isTree()
}

// Leaf は予測するラベルを持つ葉。
type Leaf struct {
	Label string
}

func (Leaf) isTree() {}

// Node は分割と、左右の部分木を持つ節。
type Node struct {
	Split Split
	Left  Tree
	Right Tree
}

func (Node) isTree() {}

// Gini はジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。
func Gini(labels []string) float64 {
	total := float64(len(labels))
	sum := 0.0

	for _, entry := range counts(labels) {
		share := float64(entry.Count) / total
		sum += share * share
	}

	return 1.0 - sum
}

// labelCount はラベルと、その件数。
type labelCount struct {
	Label string
	Count int
}

// counts はラベルごとの件数を、ラベルが先に現れた順に返す。
func counts(labels []string) []labelCount {
	order := make([]string, 0, len(labels))
	found := make(map[string]int, len(labels))

	for _, label := range labels {
		if _, ok := found[label]; !ok {
			order = append(order, label)
		}

		found[label]++
	}

	result := make([]labelCount, 0, len(order))
	for _, label := range order {
		result = append(result, labelCount{Label: label, Count: found[label]})
	}

	return result
}

// Majority は多数派のラベルを返す。同数なら先に現れたラベルを選ぶ。
func Majority(labels []string) string {
	best, bestCount := "", 0

	for _, entry := range counts(labels) {
		if entry.Count > bestCount {
			best, bestCount = entry.Label, entry.Count
		}
	}

	return best
}

// BestSplit は左右の不純度の重み付き平均が最も小さくなる分割を返す。
// 見つからなければ ok が false になる。同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。
func BestSplit(x []chapter02.Features, t []string) (Split, bool, error) {
	if Gini(t) == 0.0 {
		return Split{}, false, nil
	}

	var best Split

	found := false

	for _, feature := range x[0].Columns {
		sorted, err := sortByFeature(x, t, feature)
		if err != nil {
			return Split{}, false, err
		}

		for i := 1; i < len(sorted); i++ {
			if sorted[i].value == sorted[i-1].value {
				continue
			}

			left := labelsOf(sorted[:i])
			right := labelsOf(sorted[i:])
			impurity := (float64(len(left))*Gini(left) + float64(len(right))*Gini(right)) / float64(len(sorted))

			if !found || impurity < best.Impurity {
				best = Split{
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

// valueLabel は並べ替えのあいだ、特徴量の値と正解ラベルの対応を保つための組。
type valueLabel struct {
	value float64
	label string
}

func sortByFeature(x []chapter02.Features, t []string, feature string) ([]valueLabel, error) {
	pairs := make([]valueLabel, len(x))

	for i, features := range x {
		value, err := features.Value(feature)
		if err != nil {
			return nil, err
		}

		pairs[i] = valueLabel{value: value, label: t[i]}
	}

	sort.SliceStable(pairs, func(i, j int) bool { return pairs[i].value < pairs[j].value })

	return pairs, nil
}

func labelsOf(pairs []valueLabel) []string {
	labels := make([]string, len(pairs))
	for i, pair := range pairs {
		labels[i] = pair.label
	}

	return labels
}

// Build は深さの上限まで分割を繰り返して木を作る。maxDepth が負なら上限なし。
func Build(x []chapter02.Features, t []string, maxDepth int) (Tree, error) {
	if maxDepth == 0 {
		return Leaf{Label: Majority(t)}, nil
	}

	split, found, err := BestSplit(x, t)
	if err != nil {
		return nil, err
	}

	if !found {
		return Leaf{Label: Majority(t)}, nil
	}

	var leftX, rightX []chapter02.Features

	var leftT, rightT []string

	for i, features := range x {
		goesLeft, err := goesLeft(split, features)
		if err != nil {
			return nil, err
		}

		if goesLeft {
			leftX, leftT = append(leftX, features), append(leftT, t[i])
		} else {
			rightX, rightT = append(rightX, features), append(rightT, t[i])
		}
	}

	childDepth := maxDepth
	if maxDepth > 0 {
		childDepth = maxDepth - 1
	}

	left, err := Build(leftX, leftT, childDepth)
	if err != nil {
		return nil, err
	}

	right, err := Build(rightX, rightT, childDepth)
	if err != nil {
		return nil, err
	}

	return Node{Split: split, Left: left, Right: right}, nil
}

func goesLeft(split Split, features chapter02.Features) (bool, error) {
	value, err := features.Value(split.Feature)
	if err != nil {
		return false, err
	}

	return value <= split.Threshold, nil
}

// PredictOne は 1 件の特徴量のラベルを予測する。
// Go は型スイッチの網羅性を検査しないので、知らない木は既定の分岐でエラーにする。
func PredictOne(tree Tree, features chapter02.Features) (string, error) {
	switch node := tree.(type) {
	case Leaf:
		return node.Label, nil
	case Node:
		left, err := goesLeft(node.Split, features)
		if err != nil {
			return "", err
		}

		if left {
			return PredictOne(node.Left, features)
		}

		return PredictOne(node.Right, features)
	default:
		return "", fmt.Errorf("知らない木です: %T", tree)
	}
}

// Format は木を、条件ごとに字下げした文字列にする。
func Format(tree Tree) (string, error) {
	return format(tree, "")
}

func format(tree Tree, indent string) (string, error) {
	switch node := tree.(type) {
	case Leaf:
		return indent + node.Label, nil
	case Node:
		threshold := fmt.Sprintf("%.4f", node.Split.Threshold)

		left, err := format(node.Left, indent+"  ")
		if err != nil {
			return "", err
		}

		right, err := format(node.Right, indent+"  ")
		if err != nil {
			return "", err
		}

		return strings.Join([]string{
			fmt.Sprintf("%s%s <= %s", indent, node.Split.Feature, threshold),
			left,
			fmt.Sprintf("%s%s > %s", indent, node.Split.Feature, threshold),
			right,
		}, "\n"), nil
	default:
		return "", fmt.Errorf("知らない木です: %T", tree)
	}
}

// DecisionTree は自作の決定木の分類器。Fit で学習してから Predict で予測する。
type DecisionTree struct {
	maxDepth int
	tree     Tree
}

// Unlimited は深さを制限しない決定木を返す。
func Unlimited() *DecisionTree {
	return &DecisionTree{maxDepth: unlimited}
}

// WithMaxDepth は深さの上限を指定した決定木を返す。
func WithMaxDepth(maxDepth int) (*DecisionTree, error) {
	if maxDepth < 0 {
		return nil, fmt.Errorf("深さの上限は 0 以上にしてください: %d", maxDepth)
	}

	return &DecisionTree{maxDepth: maxDepth}, nil
}

// Fit は訓練データから木を作る。
func (d *DecisionTree) Fit(x []chapter02.Features, t []string) error {
	tree, err := Build(slices.Clone(x), slices.Clone(t), d.maxDepth)
	if err != nil {
		return err
	}

	d.tree = tree

	return nil
}

// Tree は学習した木を返す。学習する前は ok が false になる。
func (d *DecisionTree) Tree() (Tree, bool) {
	return d.tree, d.tree != nil
}

// Predict は特徴量ごとのラベルを予測する。
func (d *DecisionTree) Predict(x []chapter02.Features) ([]string, error) {
	if d.tree == nil {
		return nil, fmt.Errorf("Fit で学習してから Predict を呼んでください")
	}

	predictions := make([]string, len(x))

	for i, features := range x {
		prediction, err := PredictOne(d.tree, features)
		if err != nil {
			return nil, err
		}

		predictions[i] = prediction
	}

	return predictions, nil
}
