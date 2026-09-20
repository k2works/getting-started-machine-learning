package chapter10_test

import (
	"math"
	"math/rand"
	"reflect"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter10"
)

// columns はテストで使う特徴量の列。
var columns = []string{"長さ", "幅"}

// features はテスト用の特徴量を作る。
func features(t *testing.T, values ...float64) chapter02.Features {
	t.Helper()

	f, err := chapter02.NewFeatures(columns, values)
	if err != nil {
		t.Fatalf("NewFeatures() でエラー: %v", err)
	}

	return f
}

// 架空の 2 品種のデータ。長さが小さいほうが「あか」、大きいほうが「あお」。
func sampleData(t *testing.T) ([]chapter02.Features, []string) {
	t.Helper()

	x := []chapter02.Features{
		features(t, 1, 1),
		features(t, 1.2, 0.9),
		features(t, 0.9, 1.1),
		features(t, 5, 5),
		features(t, 5.2, 4.9),
		features(t, 4.8, 5.1),
	}
	target := []string{"あか", "あか", "あか", "あお", "あお", "あお"}

	return x, target
}

func TestSoftmax(t *testing.T) {
	t.Parallel()

	t.Run("同じスコアなら確率は等しくなる", func(t *testing.T) {
		t.Parallel()

		got := chapter10.Softmax([]float64{0, 0})

		if want := []float64{0.5, 0.5}; !reflect.DeepEqual(got, want) {
			t.Errorf("Softmax() = %v, want %v", got, want)
		}
	})

	t.Run("確率の合計は 1 になる", func(t *testing.T) {
		t.Parallel()

		sum := 0.0
		for _, p := range chapter10.Softmax([]float64{1, 2, 3}) {
			sum += p
		}

		if math.Abs(sum-1) > 1e-12 {
			t.Errorf("確率の合計 = %v, want 1", sum)
		}
	})

	t.Run("大きなスコアでもあふれない", func(t *testing.T) {
		t.Parallel()

		got := chapter10.Softmax([]float64{1000, 1000, 1000})

		for _, p := range got {
			if math.IsNaN(p) || math.IsInf(p, 0) {
				t.Fatalf("Softmax() = %v, NaN や Inf になってはいけません", got)
			}
		}

		if math.Abs(got[0]-1.0/3.0) > 1e-12 {
			t.Errorf("Softmax()[0] = %v, want %v", got[0], 1.0/3.0)
		}
	})

	t.Run("スコアが大きい品種の確率が高くなる", func(t *testing.T) {
		t.Parallel()

		got := chapter10.Softmax([]float64{0, 1})

		if got[1] <= got[0] {
			t.Errorf("Softmax() = %v, 2 番目のほうが高いはずです", got)
		}
	})
}

func TestCrossEntropy(t *testing.T) {
	t.Parallel()

	t.Run("正解の確率が 1 なら損失は 0 に近づく", func(t *testing.T) {
		t.Parallel()

		got := chapter10.CrossEntropy([][]float64{{1, 0}}, []int{0})

		if math.Abs(got) > 1e-9 {
			t.Errorf("CrossEntropy() = %v, want 0", got)
		}
	})

	t.Run("外すほど損失が大きくなる", func(t *testing.T) {
		t.Parallel()

		near := chapter10.CrossEntropy([][]float64{{0.9, 0.1}}, []int{0})
		far := chapter10.CrossEntropy([][]float64{{0.1, 0.9}}, []int{0})

		if near >= far {
			t.Errorf("損失 %v と %v: 外したほうが大きいはずです", near, far)
		}
	})
}

func TestLogisticRegression(t *testing.T) {
	t.Parallel()

	t.Run("学習すると訓練データを言い当てる", func(t *testing.T) {
		t.Parallel()

		x, target := sampleData(t)
		model := chapter10.NewLogisticRegression()

		if err := model.Fit(x, target); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		predictions, err := model.Predict(x)
		if err != nil {
			t.Fatalf("Predict() でエラー: %v", err)
		}

		if !reflect.DeepEqual(predictions, target) {
			t.Errorf("Predict() = %v, want %v", predictions, target)
		}
	})

	t.Run("学習を繰り返すほど損失が小さくなる", func(t *testing.T) {
		t.Parallel()

		x, target := sampleData(t)
		model := chapter10.NewLogisticRegression()

		if err := model.Fit(x, target); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		losses := model.Losses()
		if losses[0] <= losses[len(losses)-1] {
			t.Errorf("最初の損失 %v と最後の損失 %v: 小さくなるはずです", losses[0], losses[len(losses)-1])
		}
	})

	t.Run("品種は名前の順に並ぶ", func(t *testing.T) {
		t.Parallel()

		x, target := sampleData(t)
		model := chapter10.NewLogisticRegression()

		if err := model.Fit(x, target); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if want := []string{"あお", "あか"}; !reflect.DeepEqual(model.Classes(), want) {
			t.Errorf("Classes() = %v, want %v", model.Classes(), want)
		}
	})

	t.Run("学習する前に予測するとエラーを返す", func(t *testing.T) {
		t.Parallel()

		x, _ := sampleData(t)

		if _, err := chapter10.NewLogisticRegression().Predict(x); err == nil {
			t.Error("Predict() がエラーを返しませんでした")
		}
	})
}

func TestMajorityVote(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name  string
		votes [][]string
		want  []string
	}{
		{
			name:  "多いほうのラベルを選ぶ",
			votes: [][]string{{"あか", "あお"}, {"あか", "あお"}, {"あお", "あお"}},
			want:  []string{"あか", "あお"},
		},
		{
			name:  "同数なら先に現れたラベルを選ぶ",
			votes: [][]string{{"あか"}, {"あお"}},
			want:  []string{"あか"},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if got := chapter10.MajorityVote(test.votes); !reflect.DeepEqual(got, test.want) {
				t.Errorf("MajorityVote() = %v, want %v", got, test.want)
			}
		})
	}
}

func TestBootstrapSample(t *testing.T) {
	t.Parallel()

	t.Run("件数と同じ数だけ、重複を許して選ぶ", func(t *testing.T) {
		t.Parallel()

		got := chapter10.BootstrapSample(5, rand.New(rand.NewSource(0))) //nolint:gosec // 再現できる標本のための擬似乱数

		if len(got) != 5 {
			t.Errorf("BootstrapSample() = %v, 5 件のはずです", got)
		}

		for _, index := range got {
			if index < 0 || index >= 5 {
				t.Errorf("行番号 %d が範囲外です", index)
			}
		}
	})

	t.Run("同じシードなら同じ標本になる", func(t *testing.T) {
		t.Parallel()

		first := chapter10.BootstrapSample(10, rand.New(rand.NewSource(1)))  //nolint:gosec // 同上
		second := chapter10.BootstrapSample(10, rand.New(rand.NewSource(1))) //nolint:gosec // 同上

		if !reflect.DeepEqual(first, second) {
			t.Errorf("%v と %v: 同じシードなら一致するはずです", first, second)
		}
	})
}

func TestRandomForest(t *testing.T) {
	t.Parallel()

	t.Run("学習すると訓練データを言い当てる", func(t *testing.T) {
		t.Parallel()

		x, target := sampleData(t)
		forest := chapter10.NewRandomForest(10, 1, 0)

		if err := forest.Fit(x, target); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		predictions, err := forest.Predict(x)
		if err != nil {
			t.Fatalf("Predict() でエラー: %v", err)
		}

		if !reflect.DeepEqual(predictions, target) {
			t.Errorf("Predict() = %v, want %v", predictions, target)
		}
	})

	t.Run("指定した本数の木を作る", func(t *testing.T) {
		t.Parallel()

		x, target := sampleData(t)
		forest := chapter10.NewRandomForest(7, 1, 0)

		if err := forest.Fit(x, target); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if got := len(forest.Trees()); got != 7 {
			t.Errorf("木の本数 = %d, want 7", got)
		}
	})

	t.Run("木ごとに使う特徴量を絞る", func(t *testing.T) {
		t.Parallel()

		x, target := sampleData(t)
		forest := chapter10.NewRandomForest(5, 1, 0)

		if err := forest.Fit(x, target); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		for i, tree := range forest.Trees() {
			if got := len(tree.Columns); got != 1 {
				t.Errorf("%d 本目の特徴量 = %d 列, want 1 列", i, got)
			}
		}
	})

	t.Run("深さの上限が負ならエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter10.NewRandomForestWithMaxDepth(5, 1, -2, 0); err == nil {
			t.Error("NewRandomForestWithMaxDepth() がエラーを返しませんでした")
		}
	})

	t.Run("学習する前に予測するとエラーを返す", func(t *testing.T) {
		t.Parallel()

		x, _ := sampleData(t)

		if _, err := chapter10.NewRandomForest(5, 1, 0).Predict(x); err == nil {
			t.Error("Predict() がエラーを返しませんでした")
		}
	})
}

func TestFeatureImportance(t *testing.T) {
	t.Parallel()

	t.Run("決定木 1 本の重要度は合計が 1 になる", func(t *testing.T) {
		t.Parallel()

		x, target := sampleData(t)

		importances, err := chapter10.TreeImportancesOf(x, target)
		if err != nil {
			t.Fatalf("TreeImportancesOf() でエラー: %v", err)
		}

		sum := 0.0
		for _, importance := range importances {
			sum += importance.Value
		}

		if math.Abs(sum-1) > 1e-12 {
			t.Errorf("重要度の合計 = %v, want 1", sum)
		}
	})

	t.Run("分割に使われない特徴量の重要度は 0 になる", func(t *testing.T) {
		t.Parallel()

		// 「長さ」だけで分けられるデータなので、「幅」は使われない
		x := []chapter02.Features{features(t, 1, 3), features(t, 2, 3), features(t, 9, 3)}
		target := []string{"あか", "あか", "あお"}

		importances, err := chapter10.TreeImportancesOf(x, target)
		if err != nil {
			t.Fatalf("TreeImportancesOf() でエラー: %v", err)
		}

		for _, importance := range importances {
			if importance.Feature == "幅" && importance.Value != 0 {
				t.Errorf("幅の重要度 = %v, want 0", importance.Value)
			}
		}
	})

	t.Run("森の重要度は列の順に並び、合計が 1 になる", func(t *testing.T) {
		t.Parallel()

		x, target := sampleData(t)
		forest := chapter10.NewRandomForest(10, 1, 0)

		if err := forest.Fit(x, target); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		importances, err := chapter10.ForestImportances(forest, x, target)
		if err != nil {
			t.Fatalf("ForestImportances() でエラー: %v", err)
		}

		names := make([]string, len(importances))
		sum := 0.0

		for i, importance := range importances {
			names[i] = importance.Feature
			sum += importance.Value
		}

		if !reflect.DeepEqual(names, columns) {
			t.Errorf("並び = %v, want %v", names, columns)
		}

		if math.Abs(sum-1) > 1e-12 {
			t.Errorf("重要度の合計 = %v, want 1", sum)
		}
	})
}

func TestClassifierInterface(t *testing.T) {
	t.Parallel()

	t.Run("決定木もロジスティック回帰も森も同じインターフェースで評価できる", func(t *testing.T) {
		t.Parallel()

		x, target := sampleData(t)
		split := chapter02.TrainTestSplit[chapter02.Features, string]{
			XTrain: x, TTrain: target, XTest: x, TTest: target,
		}

		models, err := chapter10.Models()
		if err != nil {
			t.Fatalf("Models() でエラー: %v", err)
		}

		for _, model := range models {
			score, err := chapter10.Evaluate(model.Model, split)
			if err != nil {
				t.Fatalf("%s の Evaluate() でエラー: %v", model.Name, err)
			}

			if score.Train != 1.0 {
				t.Errorf("%s の訓練データの正解率 = %v, want 1", model.Name, score.Train)
			}
		}
	})
}
