// Package chapter10 はロジスティック回帰とランダムフォレストを自作し、同じインターフェースで比べる。
package chapter10

import (
	"fmt"
	"math"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

const (
	// defaultLearningRate は勾配降下法の学習率。
	defaultLearningRate = 1.0
	// defaultEpochs は学習を繰り返す回数。
	defaultEpochs = 5000
	// epsilon は対数を取るときに 0 を避けるための小さな値。
	epsilon = 1e-12
)

// Classifier は Fit で学習し、Predict でラベルを予測する分類器。
// Go のインターフェースは実装側で宣言しないので、第 3 章の決定木もそのまま満たす。
type Classifier interface {
	Fit(x []chapter02.Features, t []string) error
	Predict(x []chapter02.Features) ([]string, error)
}

// LogisticRegression はソフトマックスと勾配降下法によるロジスティック回帰。
type LogisticRegression struct {
	learningRate float64
	epochs       int
	classes      []string
	// weights[特徴量][品種]
	weights [][]float64
	bias    []float64
	losses  []float64
}

// NewLogisticRegression は学習率 1.0、繰り返し 5000 回のロジスティック回帰を返す。
func NewLogisticRegression() *LogisticRegression {
	return NewLogisticRegressionWith(defaultLearningRate, defaultEpochs)
}

// NewLogisticRegressionWith は学習率と繰り返しの回数を指定する。
func NewLogisticRegressionWith(learningRate float64, epochs int) *LogisticRegression {
	return &LogisticRegression{learningRate: learningRate, epochs: epochs}
}

// Softmax はスコアを、合計が 1 になる確率に変換する。
// 最大値を引いてから exp を求めるので、大きな値でもあふれない。
func Softmax(z []float64) []float64 {
	max := slices.Max(z)
	exps := make([]float64, len(z))
	total := 0.0

	for i, value := range z {
		exps[i] = math.Exp(value - max)
		total += exps[i]
	}

	for i := range exps {
		exps[i] /= total
	}

	return exps
}

// CrossEntropy は交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。
func CrossEntropy(probabilities [][]float64, targets []int) float64 {
	sum := 0.0
	for i, probability := range probabilities {
		sum += math.Log(probability[targets[i]] + epsilon)
	}

	return -sum / float64(len(probabilities))
}

// Classes は学習した品種の並び（名前の順）を返す。
func (l *LogisticRegression) Classes() []string {
	return l.classes
}

// Losses は繰り返しごとの訓練データの損失を返す。
func (l *LogisticRegression) Losses() []float64 {
	return l.losses
}

// scores は 1 行の特徴量から、品種ごとのスコアを求める。
func (l *LogisticRegression) scores(row []float64) []float64 {
	scores := slices.Clone(l.bias)

	for f, value := range row {
		for k := range scores {
			scores[k] += value * l.weights[f][k]
		}
	}

	return scores
}

// Fit はバッチ勾配降下法で重みと切片を学習する。
func (l *LogisticRegression) Fit(x []chapter02.Features, t []string) error {
	if len(x) == 0 {
		return fmt.Errorf("特徴量が 1 件もありません")
	}

	if len(x) != len(t) {
		return fmt.Errorf("特徴量と正解ラベルの件数が違います: %d と %d", len(x), len(t))
	}

	rows := make([][]float64, len(x))
	for i, features := range x {
		rows[i] = features.Values
	}

	l.classes = sortedClasses(t)
	targets := make([]int, len(t))

	for i, label := range t {
		targets[i] = slices.Index(l.classes, label)
	}

	l.weights = make([][]float64, len(x[0].Columns))
	for f := range l.weights {
		l.weights[f] = make([]float64, len(l.classes))
	}

	l.bias = make([]float64, len(l.classes))
	l.losses = make([]float64, 0, l.epochs)

	for range l.epochs {
		probabilities := make([][]float64, len(rows))
		for i, row := range rows {
			probabilities[i] = Softmax(l.scores(row))
		}

		l.losses = append(l.losses, CrossEntropy(probabilities, targets))

		// 誤差は「確率 − 正解」。正解の品種だけ 1 を引く
		errors := make([][]float64, len(rows))
		for i, probability := range probabilities {
			errors[i] = slices.Clone(probability)
			errors[i][targets[i]] -= 1.0
		}

		l.update(rows, errors)
	}

	return nil
}

// sortedClasses は正解ラベルの種類を名前の順に並べて返す。
func sortedClasses(t []string) []string {
	classes := make([]string, 0)

	for _, label := range t {
		if !slices.Contains(classes, label) {
			classes = append(classes, label)
		}
	}

	slices.Sort(classes)

	return classes
}

// update は誤差から勾配を求めて、重みと切片を更新する。
func (l *LogisticRegression) update(rows, errors [][]float64) {
	n := float64(len(rows))

	for k := range l.classes {
		for f := range l.weights {
			gradient := 0.0
			for i, row := range rows {
				gradient += row[f] * errors[i][k]
			}

			l.weights[f][k] -= l.learningRate * gradient / n
		}

		biasGradient := 0.0
		for _, e := range errors {
			biasGradient += e[k]
		}

		l.bias[k] -= l.learningRate * biasGradient / n
	}
}

// Predict はスコアが最大の品種を予測する。
func (l *LogisticRegression) Predict(x []chapter02.Features) ([]string, error) {
	if len(l.classes) == 0 {
		return nil, fmt.Errorf("Fit で学習してから Predict を呼んでください")
	}

	predictions := make([]string, len(x))
	for i, features := range x {
		predictions[i] = l.classes[argmax(l.scores(features.Values))]
	}

	return predictions, nil
}

// argmax は最大の値の位置を返す。同じ値なら先の位置を選ぶ。
func argmax(values []float64) int {
	best := 0

	for i, value := range values {
		if value > values[best] {
			best = i
		}
	}

	return best
}
