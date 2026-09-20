package chapter11_test

import (
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter11"
)

// separable は「値が小さければ陰性、大きければ陽性」の架空のデータを作る。
func separable(t *testing.T) ([]chapter02.Features, []string) {
	t.Helper()

	values := []float64{1, 2, 3, 4, 7, 8, 9, 10}
	labels := []string{"0", "0", "0", "0", "1", "1", "1", "1"}
	x := make([]chapter02.Features, len(values))

	for i, value := range values {
		features, err := chapter02.NewFeatures([]string{"x"}, []float64{value})
		if err != nil {
			t.Fatalf("NewFeatures() でエラー: %v", err)
		}

		x[i] = features
	}

	return x, labels
}

// overlapping は陽性と陰性が入り混じった架空のデータを作る。確率が 0 と 1 に振り切れない。
func overlapping(t *testing.T) ([]chapter02.Features, []string) {
	t.Helper()

	values := []float64{1, 2, 3, 4, 5, 6, 7, 8}
	labels := []string{"0", "1", "0", "0", "1", "0", "1", "1"}
	x := make([]chapter02.Features, len(values))

	for i, value := range values {
		features, err := chapter02.NewFeatures([]string{"x"}, []float64{value})
		if err != nil {
			t.Fatalf("NewFeatures() でエラー: %v", err)
		}

		x[i] = features
	}

	return x, labels
}

func TestLogisticModel(t *testing.T) {
	t.Parallel()

	t.Run("分かれているデータなら全件を言い当てる", func(t *testing.T) {
		t.Parallel()

		x, labels := separable(t)
		model := chapter11.NewLogisticModel(survived)

		if err := model.Fit(x, labels); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		predictions, err := model.Predict(x)
		if err != nil {
			t.Fatalf("Predict() でエラー: %v", err)
		}

		accuracy, err := chapter11.Accuracy(labels, predictions)
		if err != nil {
			t.Fatalf("Accuracy() でエラー: %v", err)
		}

		if accuracy != 1 {
			t.Errorf("正解率 = %v, want 1", accuracy)
		}
	})

	t.Run("確率は 0 と 1 のあいだで、値が大きいほど高くなる", func(t *testing.T) {
		t.Parallel()

		x, labels := separable(t)
		model := chapter11.NewLogisticModel(survived)

		if err := model.Fit(x, labels); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		probabilities, err := model.PredictProba(x)
		if err != nil {
			t.Fatalf("PredictProba() でエラー: %v", err)
		}

		for i, probability := range probabilities {
			if probability < 0 || probability > 1 {
				t.Errorf("%d 件目の確率 = %v, want 0 以上 1 以下", i, probability)
			}

			if i > 0 && probability <= probabilities[i-1] {
				t.Errorf("%d 件目の確率 %v が前の %v より高くありません", i, probability, probabilities[i-1])
			}
		}
	})

	t.Run("境目を下げると陽性と予測する件数が増える", func(t *testing.T) {
		t.Parallel()

		// きれいに分かれたデータだと確率が 0 か 1 に振り切れて境目の違いが出ないので、重なりのあるデータを使う
		x, labels := overlapping(t)
		model := chapter11.NewLogisticModel(survived)

		if err := model.Fit(x, labels); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		counts := make([]int, 0, 2)

		for _, threshold := range []float64{0.5, 0.1} {
			predictions, err := model.PredictWithThreshold(x, threshold)
			if err != nil {
				t.Fatalf("PredictWithThreshold() でエラー: %v", err)
			}

			count := 0

			for _, prediction := range predictions {
				if prediction == survived {
					count++
				}
			}

			counts = append(counts, count)
		}

		if counts[1] <= counts[0] {
			t.Errorf("境目 0.1 の陽性 %d 件が、境目 0.5 の %d 件より多くありません", counts[1], counts[0])
		}
	})

	t.Run("学習していなければエラーを返す", func(t *testing.T) {
		t.Parallel()

		x, _ := separable(t)

		if _, err := chapter11.NewLogisticModel(survived).Predict(x); err == nil {
			t.Error("Predict() がエラーを返しませんでした")
		}
	})

	t.Run("ラベルが 3 種類以上ならエラーを返す", func(t *testing.T) {
		t.Parallel()

		x, labels := separable(t)
		labels[0] = "2"

		if err := chapter11.NewLogisticModel(survived).Fit(x, labels); err == nil {
			t.Error("Fit() がエラーを返しませんでした")
		}
	})
}
