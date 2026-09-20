package chapter11_test

import (
	"math"
	"slices"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter11"
)

// countingModel は「学習した件数を覚えるだけ」の架空のモデル。交差検証の手順を確かめるために使う。
type countingModel struct {
	trained int
	label   string
}

func (c *countingModel) Fit(x []chapter02.Features, t []string) error {
	c.trained = len(x)
	c.label = t[0]

	return nil
}

func (c *countingModel) Predict(x []chapter02.Features) ([]string, error) {
	predictions := make([]string, len(x))
	for i := range predictions {
		predictions[i] = c.label
	}

	return predictions, nil
}

// fakeFeatures は値が 1 つだけの架空の特徴量を n 件作る。
func fakeFeatures(t *testing.T, n int) []chapter02.Features {
	t.Helper()

	x := make([]chapter02.Features, n)

	for i := range x {
		features, err := chapter02.NewFeatures([]string{"x"}, []float64{float64(i)})
		if err != nil {
			t.Fatalf("NewFeatures() でエラー: %v", err)
		}

		x[i] = features
	}

	return x
}

func TestKFold(t *testing.T) {
	t.Parallel()

	t.Run("分割数と同じ数の分け方を返す", func(t *testing.T) {
		t.Parallel()

		folds, err := chapter11.KFold(10, 5, 0)
		if err != nil {
			t.Fatalf("KFold() でエラー: %v", err)
		}

		if len(folds) != 5 {
			t.Errorf("分け方の数 = %d, want 5", len(folds))
		}
	})

	t.Run("すべての行がちょうど 1 回だけテストデータになる", func(t *testing.T) {
		t.Parallel()

		folds, err := chapter11.KFold(10, 5, 0)
		if err != nil {
			t.Fatalf("KFold() でエラー: %v", err)
		}

		tested := make([]int, 0, 10)
		for _, fold := range folds {
			tested = append(tested, fold.Test...)
		}

		slices.Sort(tested)

		if want := []int{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}; !slices.Equal(tested, want) {
			t.Errorf("テストデータになった行 = %v, want %v", tested, want)
		}
	})

	t.Run("訓練データとテストデータは重ならず、合わせて全件になる", func(t *testing.T) {
		t.Parallel()

		folds, err := chapter11.KFold(10, 5, 0)
		if err != nil {
			t.Fatalf("KFold() でエラー: %v", err)
		}

		for i, fold := range folds {
			if len(fold.Train)+len(fold.Test) != 10 {
				t.Errorf("%d 番目の分け方の合計 = %d, want 10", i, len(fold.Train)+len(fold.Test))
			}

			for _, position := range fold.Test {
				if slices.Contains(fold.Train, position) {
					t.Errorf("%d 番目の分け方で行 %d が訓練データにもテストデータにもあります", i, position)
				}
			}
		}
	})

	t.Run("割り切れないときは差が 1 件までになる", func(t *testing.T) {
		t.Parallel()

		folds, err := chapter11.KFold(11, 3, 0)
		if err != nil {
			t.Fatalf("KFold() でエラー: %v", err)
		}

		sizes := make([]int, len(folds))
		for i, fold := range folds {
			sizes[i] = len(fold.Test)
		}

		if want := []int{4, 4, 3}; !slices.Equal(sizes, want) {
			t.Errorf("テストデータの件数 = %v, want %v", sizes, want)
		}
	})

	t.Run("同じ種なら同じ分け方になる", func(t *testing.T) {
		t.Parallel()

		first, err := chapter11.KFold(10, 5, 42)
		if err != nil {
			t.Fatalf("KFold() でエラー: %v", err)
		}

		second, err := chapter11.KFold(10, 5, 42)
		if err != nil {
			t.Fatalf("KFold() でエラー: %v", err)
		}

		for i := range first {
			if !slices.Equal(first[i].Test, second[i].Test) {
				t.Errorf("%d 番目の分け方が違います: %v と %v", i, first[i].Test, second[i].Test)
			}
		}
	})

	t.Run("分割数が 2 未満ならエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter11.KFold(10, 1, 0); err == nil {
			t.Error("KFold() がエラーを返しませんでした")
		}
	})

	t.Run("行数が分割数より少なければエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter11.KFold(3, 5, 0); err == nil {
			t.Error("KFold() がエラーを返しませんでした")
		}
	})
}

func TestCrossValidate(t *testing.T) {
	t.Parallel()

	x := fakeFeatures(t, 10)
	labels := []string{"1", "1", "1", "1", "1", "0", "0", "0", "0", "0"}

	t.Run("分け方の数だけスコアを返す", func(t *testing.T) {
		t.Parallel()

		folds, err := chapter11.KFold(len(x), 5, 0)
		if err != nil {
			t.Fatalf("KFold() でエラー: %v", err)
		}

		scores, err := chapter11.CrossValidate(
			func() (chapter11.Model[string], error) { return &countingModel{}, nil },
			x, labels, folds, chapter11.Accuracy[string],
		)
		if err != nil {
			t.Fatalf("CrossValidate() でエラー: %v", err)
		}

		if len(scores) != 5 {
			t.Errorf("スコアの数 = %d, want 5", len(scores))
		}
	})

	t.Run("分け方ごとに新しいモデルを学習する", func(t *testing.T) {
		t.Parallel()

		folds, err := chapter11.KFold(len(x), 5, 0)
		if err != nil {
			t.Fatalf("KFold() でエラー: %v", err)
		}

		made := 0

		if _, err := chapter11.CrossValidate(
			func() (chapter11.Model[string], error) {
				made++

				return &countingModel{}, nil
			},
			x, labels, folds, chapter11.Accuracy[string],
		); err != nil {
			t.Fatalf("CrossValidate() でエラー: %v", err)
		}

		if made != 5 {
			t.Errorf("作ったモデルの数 = %d, want 5", made)
		}
	})

	t.Run("評価関数を差し替えると別の値になる", func(t *testing.T) {
		t.Parallel()

		folds, err := chapter11.KFold(len(x), 5, 0)
		if err != nil {
			t.Fatalf("KFold() でエラー: %v", err)
		}

		makeModel := func() (chapter11.Model[string], error) { return &countingModel{}, nil }

		accuracy, err := chapter11.CrossValidate(makeModel, x, labels, folds, chapter11.Accuracy[string])
		if err != nil {
			t.Fatalf("CrossValidate() でエラー: %v", err)
		}

		recall, err := chapter11.CrossValidate(
			makeModel, x, labels, folds,
			chapter11.ClassificationMetric(chapter11.Recall, survived),
		)
		if err != nil {
			t.Fatalf("CrossValidate() でエラー: %v", err)
		}

		if slices.Equal(accuracy, recall) {
			t.Errorf("正解率と再現率が同じ値になりました: %v", accuracy)
		}
	})

	t.Run("特徴量と正解ラベルの件数が違えばエラーを返す", func(t *testing.T) {
		t.Parallel()

		folds, err := chapter11.KFold(len(x), 5, 0)
		if err != nil {
			t.Fatalf("KFold() でエラー: %v", err)
		}

		if _, err := chapter11.CrossValidate(
			func() (chapter11.Model[string], error) { return &countingModel{}, nil },
			x, labels[:3], folds, chapter11.Accuracy[string],
		); err == nil {
			t.Error("CrossValidate() がエラーを返しませんでした")
		}
	})
}

func TestMean(t *testing.T) {
	t.Parallel()

	t.Run("平均を返す", func(t *testing.T) {
		t.Parallel()

		got, err := chapter11.Mean([]float64{1, 2, 3, 4})
		if err != nil {
			t.Fatalf("Mean() でエラー: %v", err)
		}

		if want := 2.5; math.Abs(got-want) > 1e-12 {
			t.Errorf("Mean() = %v, want %v", got, want)
		}
	})

	t.Run("値が無ければエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter11.Mean(nil); err == nil {
			t.Error("Mean() がエラーを返しませんでした")
		}
	})
}
