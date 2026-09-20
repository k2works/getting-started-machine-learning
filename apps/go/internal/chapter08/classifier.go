package chapter08

import (
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// ClassWeight はクラスの重みの付け方。
type ClassWeight string

const (
	// WeightNone は重みを付けない（すべて 1）。
	WeightNone ClassWeight = "none"
	// WeightBalanced はクラスの件数に反比例する重みを付ける。
	WeightBalanced ClassWeight = "balanced"
)

// ClassWeights は表示と繰り返しのための、重みの付け方の並び。
var ClassWeights = []ClassWeight{WeightNone, WeightBalanced}

// DecisionTreeClassifier はクラスの重みを付けられる決定木の分類器。MaxDepth が負なら深さの上限なし。
type DecisionTreeClassifier struct {
	MaxDepth    int
	ClassWeight ClassWeight
}

// Fit は訓練データから木を作る。
func (d DecisionTreeClassifier) Fit(x []chapter02.Features, t []int) (*FittedDecisionTree, error) {
	weights, err := d.weightsOf(t)
	if err != nil {
		return nil, err
	}

	tree, err := Build(x, t, weights, d.MaxDepth)
	if err != nil {
		return nil, err
	}

	return &FittedDecisionTree{Root: tree}, nil
}

// weightsOf は 1 件ごとの重みを求める。
func (d DecisionTreeClassifier) weightsOf(t []int) ([]float64, error) {
	if d.ClassWeight == WeightBalanced {
		return BalancedWeights(t)
	}

	weights := make([]float64, len(t))
	for i := range weights {
		weights[i] = 1
	}

	return weights, nil
}

// FittedDecisionTree は学習済みの決定木。
type FittedDecisionTree struct {
	Root Tree
}

// Predict は特徴量ごとのラベルを予測する。
func (f *FittedDecisionTree) Predict(x []chapter02.Features) ([]int, error) {
	predictions := make([]int, len(x))

	for i, features := range x {
		prediction, err := PredictOne(f.Root, features)
		if err != nil {
			return nil, err
		}

		predictions[i] = prediction
	}

	return predictions, nil
}
