package chapter10

import (
	"fmt"
	"math/rand"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter03"
)

// unlimited は深さを制限しないことを表す値。
const unlimited = -1

// FittedTree は学習した決定木 1 本と、その木が使った特徴量の列・訓練データの行番号。
type FittedTree struct {
	Columns []string
	Rows    []int
	Model   *chapter03.DecisionTree
}

// RandomForest は第 3 章の決定木をブートストラップ標本と特徴量の部分集合で学習し、多数決で予測する。
type RandomForest struct {
	nEstimators int
	maxFeatures int
	maxDepth    int
	seed        int64
	trees       []FittedTree
}

// NewRandomForest は深さを制限しない決定木の森を返す。
func NewRandomForest(nEstimators, maxFeatures int, seed int64) *RandomForest {
	return &RandomForest{nEstimators: nEstimators, maxFeatures: maxFeatures, maxDepth: unlimited, seed: seed}
}

// NewRandomForestWithMaxDepth は深さの上限を指定した決定木の森を返す。
func NewRandomForestWithMaxDepth(nEstimators, maxFeatures, maxDepth int, seed int64) (*RandomForest, error) {
	if maxDepth < 0 {
		return nil, fmt.Errorf("深さの上限は 0 以上にしてください: %d", maxDepth)
	}

	return &RandomForest{nEstimators: nEstimators, maxFeatures: maxFeatures, maxDepth: maxDepth, seed: seed}, nil
}

// Trees は学習した決定木を返す。
func (r *RandomForest) Trees() []FittedTree {
	return r.trees
}

// MajorityVote はサンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。
func MajorityVote(votes [][]string) []string {
	predictions := make([]string, len(votes[0]))

	for sample := range predictions {
		labels := make([]string, len(votes))
		for i, vote := range votes {
			labels[i] = vote[sample]
		}

		predictions[sample] = chapter03.Majority(labels)
	}

	return predictions
}

// BootstrapSample は 0 から size - 1 までの行番号を、重複を許して size 個選ぶ。
func BootstrapSample(size int, random *rand.Rand) []int {
	rows := make([]int, size)
	for i := range rows {
		rows[i] = random.Intn(size)
	}

	return rows
}

// SelectColumns は特徴量から、指定した列だけをその順に取り出す。
func SelectColumns(x []chapter02.Features, columns []string) ([]chapter02.Features, error) {
	selected := make([]chapter02.Features, len(x))

	for i, features := range x {
		values := make([]float64, len(columns))

		for j, column := range columns {
			value, err := features.Value(column)
			if err != nil {
				return nil, err
			}

			values[j] = value
		}

		row, err := chapter02.NewFeatures(columns, values)
		if err != nil {
			return nil, err
		}

		selected[i] = row
	}

	return selected, nil
}

// chooseColumns は列を並べ替えて先頭から maxFeatures 個選び、元の列の順に戻して返す。
func chooseColumns(allColumns []string, maxFeatures int, random *rand.Rand) []string {
	shuffled := slices.Clone(allColumns)
	random.Shuffle(len(shuffled), func(i, j int) { shuffled[i], shuffled[j] = shuffled[j], shuffled[i] })

	chosen := shuffled[:maxFeatures]
	columns := make([]string, 0, maxFeatures)

	for _, column := range allColumns {
		if slices.Contains(chosen, column) {
			columns = append(columns, column)
		}
	}

	return columns
}

// newTree は森の 1 本分の決定木を作る。
func (r *RandomForest) newTree() (*chapter03.DecisionTree, error) {
	if r.maxDepth == unlimited {
		return chapter03.Unlimited(), nil
	}

	return chapter03.WithMaxDepth(r.maxDepth)
}

// Fit はブートストラップ標本と特徴量の部分集合で、決定木を指定した本数だけ学習する。
func (r *RandomForest) Fit(x []chapter02.Features, t []string) error {
	if len(x) == 0 {
		return fmt.Errorf("特徴量が 1 件もありません")
	}

	if r.maxFeatures < 1 || r.maxFeatures > len(x[0].Columns) {
		return fmt.Errorf("使う特徴量の数が範囲外です: %d", r.maxFeatures)
	}

	random := rand.New(rand.NewSource(r.seed)) //nolint:gosec // 再現できる森のための擬似乱数で、暗号用途ではない
	allColumns := x[0].Columns
	trees := make([]FittedTree, 0, r.nEstimators)

	for range r.nEstimators {
		rows := BootstrapSample(len(x), random)
		columns := chooseColumns(allColumns, r.maxFeatures, random)

		sampleX := make([]chapter02.Features, len(rows))
		sampleT := make([]string, len(rows))

		for i, row := range rows {
			sampleX[i] = x[row]
			sampleT[i] = t[row]
		}

		selected, err := SelectColumns(sampleX, columns)
		if err != nil {
			return err
		}

		tree, err := r.newTree()
		if err != nil {
			return err
		}

		if err := tree.Fit(selected, sampleT); err != nil {
			return err
		}

		trees = append(trees, FittedTree{Columns: columns, Rows: rows, Model: tree})
	}

	r.trees = trees

	return nil
}

// Predict は木ごとの予測を多数決でまとめる。
func (r *RandomForest) Predict(x []chapter02.Features) ([]string, error) {
	if len(r.trees) == 0 {
		return nil, fmt.Errorf("Fit で学習してから Predict を呼んでください")
	}

	votes := make([][]string, len(r.trees))

	for i, tree := range r.trees {
		selected, err := SelectColumns(x, tree.Columns)
		if err != nil {
			return nil, err
		}

		vote, err := tree.Model.Predict(selected)
		if err != nil {
			return nil, err
		}

		votes[i] = vote
	}

	return MajorityVote(votes), nil
}
