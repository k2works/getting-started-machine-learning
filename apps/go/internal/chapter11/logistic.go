package chapter11

import (
	"fmt"
	"math"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter09"
)

const (
	// defaultLearningRate は勾配降下法の学習率。
	defaultLearningRate = 0.5
	// defaultEpochs は学習を繰り返す回数。
	defaultEpochs = 2000
	// defaultThreshold は確率をラベルに変えるときの境目。
	defaultThreshold = 0.5
)

// Sigmoid は実数を 0 と 1 のあいだの確率に変える。
// z が負のときに exp(-z) を取ると大きな値になってあふれるので、符号で式を分ける。
func Sigmoid(z float64) float64 {
	if z >= 0 {
		return 1 / (1 + math.Exp(-z))
	}

	exp := math.Exp(z)

	return exp / (1 + exp)
}

// LogisticModel は 2 クラスのロジスティック回帰。確率を返せるので ROC 曲線に使える。
// 第 10 章はソフトマックスで 3 品種を分けたが、ここは陽性か陰性かの 2 択なのでシグモイドを使う。
type LogisticModel struct {
	positive     string
	negative     string
	learningRate float64
	epochs       int
	scaler       chapter09.Standardizer
	weights      []float64
	bias         float64
	fitted       bool
}

// NewLogisticModel は陽性のラベルを指定して、学習していないモデルを返す。
func NewLogisticModel(positive string) *LogisticModel {
	return &LogisticModel{positive: positive, learningRate: defaultLearningRate, epochs: defaultEpochs}
}

// Fit は特徴量を標準化してから、勾配降下法で重みと切片を学習する。
// 標準化しないと、年齢のように桁の大きい列に引きずられて学習が進まない。
func (l *LogisticModel) Fit(x []chapter02.Features, t []string) error {
	if err := requireSameSize(x, t); err != nil {
		return err
	}

	negative, err := l.otherLabel(t)
	if err != nil {
		return err
	}

	l.negative = negative

	scaler, err := chapter09.Fit(x)
	if err != nil {
		return err
	}

	l.scaler = scaler

	rows, err := l.standardize(x)
	if err != nil {
		return err
	}

	targets := make([]float64, len(t))

	for i, label := range t {
		if label == l.positive {
			targets[i] = 1
		}
	}

	l.weights = make([]float64, len(x[0].Values))
	l.bias = 0

	for range l.epochs {
		l.step(rows, targets)
	}

	l.fitted = true

	return nil
}

// otherLabel は陽性でないラベルを 1 つ見つける。3 種類以上あればエラーを返す。
func (l *LogisticModel) otherLabel(t []string) (string, error) {
	labels := make([]string, 0, 2)

	for _, label := range t {
		if !slices.Contains(labels, label) {
			labels = append(labels, label)
		}
	}

	if !slices.Contains(labels, l.positive) {
		return "", fmt.Errorf("陽性のラベル %q が正解ラベルにありません", l.positive)
	}

	if len(labels) > 2 {
		return "", fmt.Errorf("2 クラスのモデルに %d 種類のラベルを渡しています", len(labels))
	}

	for _, label := range labels {
		if label != l.positive {
			return label, nil
		}
	}

	return "", fmt.Errorf("陰性のラベルがありません")
}

// standardize は学習した平均と標準偏差で特徴量をそろえ、数値の行にする。
func (l *LogisticModel) standardize(x []chapter02.Features) ([][]float64, error) {
	transformed, err := l.scaler.Transform(x)
	if err != nil {
		return nil, err
	}

	rows := make([][]float64, len(transformed))
	for i, features := range transformed {
		rows[i] = features.Values
	}

	return rows, nil
}

// step は 1 回分の勾配降下法。誤差（確率 − 正解）から重みと切片を更新する。
func (l *LogisticModel) step(rows [][]float64, targets []float64) {
	n := float64(len(rows))
	errors := make([]float64, len(rows))

	for i, row := range rows {
		errors[i] = Sigmoid(l.score(row)) - targets[i]
	}

	for f := range l.weights {
		gradient := 0.0
		for i, row := range rows {
			gradient += row[f] * errors[i]
		}

		l.weights[f] -= l.learningRate * gradient / n
	}

	biasGradient := 0.0
	for _, e := range errors {
		biasGradient += e
	}

	l.bias -= l.learningRate * biasGradient / n
}

// score は 1 行の重み付き合計。
func (l *LogisticModel) score(row []float64) float64 {
	sum := l.bias
	for i, value := range row {
		sum += l.weights[i] * value
	}

	return sum
}

// PredictProba は行ごとに「陽性である確率」を返す。
func (l *LogisticModel) PredictProba(x []chapter02.Features) ([]float64, error) {
	if !l.fitted {
		return nil, fmt.Errorf("Fit で学習してから予測してください")
	}

	rows, err := l.standardize(x)
	if err != nil {
		return nil, err
	}

	probabilities := make([]float64, len(rows))
	for i, row := range rows {
		probabilities[i] = Sigmoid(l.score(row))
	}

	return probabilities, nil
}

// Predict は確率が 0.5 を超えた行を陽性と予測する。
func (l *LogisticModel) Predict(x []chapter02.Features) ([]string, error) {
	return l.PredictWithThreshold(x, defaultThreshold)
}

// PredictWithThreshold は境目を指定して予測する。境目を下げると再現率が上がり、適合率が下がる。
func (l *LogisticModel) PredictWithThreshold(x []chapter02.Features, threshold float64) ([]string, error) {
	probabilities, err := l.PredictProba(x)
	if err != nil {
		return nil, err
	}

	predictions := make([]string, len(probabilities))

	for i, probability := range probabilities {
		predictions[i] = l.negative
		if probability >= threshold {
			predictions[i] = l.positive
		}
	}

	return predictions, nil
}
