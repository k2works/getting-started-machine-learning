package chapter11

import (
	"fmt"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// Fold は 1 回分の分け方。訓練データとテストデータの行番号を持つ。
// 行そのものではなく行番号を持つので、特徴量と正解ラベルのどちらにも同じ分け方を使える。
type Fold struct {
	Train []int
	Test  []int
}

// KFold は行数を nSplits 個に分け、順番にテストデータにした分け方を返す。
// 余りは先の分け方に 1 件ずつ配るので、分け方の大きさの差は 1 件までになる。
func KFold(nSamples, nSplits int, seed int64) ([]Fold, error) {
	if nSplits < 2 {
		return nil, fmt.Errorf("分割数は 2 以上にしてください: %d", nSplits)
	}

	if nSamples < nSplits {
		return nil, fmt.Errorf("行数 %d が分割数 %d より少ないです", nSamples, nSplits)
	}

	positions := make([]int, nSamples)
	for i := range positions {
		positions[i] = i
	}

	shuffled := chapter02.Shuffle(positions, seed)
	folds := make([]Fold, 0, nSplits)
	start := 0

	for i := range nSplits {
		size := nSamples / nSplits
		if i < nSamples%nSplits {
			size++
		}

		test := slices.Clone(shuffled[start : start+size])
		train := make([]int, 0, nSamples-size)

		train = append(train, shuffled[:start]...)
		train = append(train, shuffled[start+size:]...)

		folds = append(folds, Fold{Train: train, Test: test})
		start += size
	}

	return folds, nil
}

// pick は行番号の順に値を取り出す。
func pick[E any](values []E, positions []int) ([]E, error) {
	picked := make([]E, 0, len(positions))

	for _, position := range positions {
		if position < 0 || position >= len(values) {
			return nil, fmt.Errorf("行番号が範囲の外です: %d", position)
		}

		picked = append(picked, values[position])
	}

	return picked, nil
}

// CrossValidate は分け方ごとに新しいモデルを学習し、評価関数の値を並べて返す。
// モデルは makeModel で毎回作り直す。前の分け方の学習が残ったモデルを使い回さないようにするため。
func CrossValidate[T any](
	makeModel func() (Model[T], error),
	x []chapter02.Features,
	t []T,
	folds []Fold,
	metric Metric[T],
) ([]float64, error) {
	if err := requireSameSize(x, t); err != nil {
		return nil, err
	}

	scores := make([]float64, 0, len(folds))

	for _, fold := range folds {
		score, err := validateOne(makeModel, x, t, fold, metric)
		if err != nil {
			return nil, err
		}

		scores = append(scores, score)
	}

	return scores, nil
}

// validateOne は 1 つの分け方で学習して評価する。
func validateOne[T any](
	makeModel func() (Model[T], error),
	x []chapter02.Features,
	t []T,
	fold Fold,
	metric Metric[T],
) (float64, error) {
	model, err := makeModel()
	if err != nil {
		return 0, err
	}

	xTrain, err := pick(x, fold.Train)
	if err != nil {
		return 0, err
	}

	tTrain, err := pick(t, fold.Train)
	if err != nil {
		return 0, err
	}

	if err := model.Fit(xTrain, tTrain); err != nil {
		return 0, err
	}

	xTest, err := pick(x, fold.Test)
	if err != nil {
		return 0, err
	}

	tTest, err := pick(t, fold.Test)
	if err != nil {
		return 0, err
	}

	predicted, err := model.Predict(xTest)
	if err != nil {
		return 0, err
	}

	return metric(tTest, predicted)
}

// Mean は平均。交差検証の分け方ごとのスコアをまとめるのに使う。
func Mean(values []float64) (float64, error) {
	if len(values) == 0 {
		return 0, fmt.Errorf("値が 1 件もありません")
	}

	sum := 0.0
	for _, value := range values {
		sum += value
	}

	return sum / float64(len(values)), nil
}
