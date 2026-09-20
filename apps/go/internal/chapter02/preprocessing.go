package chapter02

import (
	"fmt"
	"math"
	"math/rand"
	"slices"
)

// Target は正解ラベルの列。
const Target = "種類"

// Features は補完が済んだ 1 行分の特徴量。欠損値を持てないので、補完する前の行をモデルに渡せない。
type Features struct {
	Columns []string
	Values  []float64
}

// NewFeatures は列名と値から特徴量を作る。数が違えばエラーを返す。
func NewFeatures(columns []string, values []float64) (Features, error) {
	if len(columns) != len(values) {
		return Features{}, fmt.Errorf("列名と値の数が違います: %d と %d", len(columns), len(values))
	}

	return Features{Columns: slices.Clone(columns), Values: slices.Clone(values)}, nil
}

// Value は列名で値を読む。
func (f Features) Value(column string) (float64, error) {
	index := slices.Index(f.Columns, column)
	if index < 0 {
		return 0, fmt.Errorf("列がありません: %s", column)
	}

	return f.Values[index], nil
}

// TrainTestSplit は訓練データとテストデータ。X は特徴量の型、T は正解ラベルの型。
type TrainTestSplit[X, T any] struct {
	XTrain []X
	XTest  []X
	TTrain []T
	TTest  []T
}

// ColumnMeans は欠損値を除いて、列ごとの平均値を求める。
func ColumnMeans(rows []Row, columns []string) (map[string]float64, error) {
	means := make(map[string]float64, len(columns))

	for _, column := range columns {
		sum, count := 0.0, 0

		for _, row := range rows {
			value, ok, err := row.Number(column)
			if err != nil {
				return nil, err
			}

			if ok {
				sum += value
				count++
			}
		}

		if count == 0 {
			return nil, fmt.Errorf("値がすべて空欄です: %s", column)
		}

		means[column] = sum / float64(count)
	}

	return means, nil
}

// FillMissing は欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変更しない。
func FillMissing(rows []Row, columns []string, values map[string]float64) ([]Features, error) {
	filled := make([]Features, 0, len(rows))

	for _, row := range rows {
		numbers := make([]float64, len(columns))

		for i, column := range columns {
			value, ok, err := row.Number(column)
			if err != nil {
				return nil, err
			}

			if !ok {
				fill, found := values[column]
				if !found {
					return nil, fmt.Errorf("補完する値がありません: %s", column)
				}

				value = fill
			}

			numbers[i] = value
		}

		features, err := NewFeatures(columns, numbers)
		if err != nil {
			return nil, err
		}

		filled = append(filled, features)
	}

	return filled, nil
}

// SplitFeaturesAndTarget は正解ラベルの列を取り出し、残りの列を特徴量の列にする。
func SplitFeaturesAndTarget(table Table, target string) ([]string, []Row, []string, error) {
	columns := make([]string, 0, len(table.Columns))

	for _, column := range table.Columns {
		if column != target {
			columns = append(columns, column)
		}
	}

	labels := make([]string, 0, len(table.Rows))

	for _, row := range table.Rows {
		label, err := row.Text(target)
		if err != nil {
			return nil, nil, nil, err
		}

		labels = append(labels, label)
	}

	return columns, table.Rows, labels, nil
}

// Shuffle はシードを使って Fisher-Yates のシャッフルで並べ替える。元のスライスは変更しない。
func Shuffle[E any](items []E, seed int64) []E {
	random := rand.New(rand.NewSource(seed)) //nolint:gosec // 再現できる分割のための擬似乱数で、暗号用途ではない
	shuffled := slices.Clone(items)

	for i := len(shuffled) - 1; i >= 1; i-- {
		j := random.Intn(i + 1)
		shuffled[i], shuffled[j] = shuffled[j], shuffled[i]
	}

	return shuffled
}

// pair は並べ替えのあいだ、特徴量と正解ラベルの対応を保つための組。
type pair[X, T any] struct {
	x X
	t T
}

// SplitTrainTest は並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。
func SplitTrainTest[X, T any](x []X, t []T, testSize float64, seed int64) (TrainTestSplit[X, T], error) {
	if len(x) != len(t) {
		return TrainTestSplit[X, T]{}, fmt.Errorf("特徴量と正解ラベルの件数が違います: %d と %d", len(x), len(t))
	}

	pairs := make([]pair[X, T], len(x))
	for i := range x {
		pairs[i] = pair[X, T]{x: x[i], t: t[i]}
	}

	shuffled := Shuffle(pairs, seed)
	trainCount := len(shuffled) - int(math.Ceil(float64(len(shuffled))*testSize))
	split := TrainTestSplit[X, T]{}

	for i, p := range shuffled {
		if i < trainCount {
			split.XTrain = append(split.XTrain, p.x)
			split.TTrain = append(split.TTrain, p.t)
		} else {
			split.XTest = append(split.XTest, p.x)
			split.TTest = append(split.TTest, p.t)
		}
	}

	return split, nil
}

// PrepareIris は iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。
func PrepareIris(csvFile string, testSize float64, seed int64) (TrainTestSplit[Features, string], error) {
	table, err := LoadTable(csvFile)
	if err != nil {
		return TrainTestSplit[Features, string]{}, err
	}

	columns, rows, labels, err := SplitFeaturesAndTarget(table, Target)
	if err != nil {
		return TrainTestSplit[Features, string]{}, err
	}

	split, err := SplitTrainTest(rows, labels, testSize, seed)
	if err != nil {
		return TrainTestSplit[Features, string]{}, err
	}

	means, err := ColumnMeans(split.XTrain, columns)
	if err != nil {
		return TrainTestSplit[Features, string]{}, err
	}

	xTrain, err := FillMissing(split.XTrain, columns, means)
	if err != nil {
		return TrainTestSplit[Features, string]{}, err
	}

	xTest, err := FillMissing(split.XTest, columns, means)
	if err != nil {
		return TrainTestSplit[Features, string]{}, err
	}

	return TrainTestSplit[Features, string]{XTrain: xTrain, XTest: xTest, TTrain: split.TTrain, TTest: split.TTest}, nil
}
