package chapter12_test

import (
	"math"
	"slices"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter12"
)

// sample は架空の 2 列のデータを作る。1 列目だけが正解に効いていて、2 列目は関係が薄い。
func sample(t *testing.T) ([]chapter02.Features, []float64) {
	t.Helper()

	rows := [][]float64{
		{1, 2},
		{2, 1},
		{3, 4},
		{4, 3},
		{5, 6},
		{6, 5},
	}
	targets := []float64{3, 5, 7, 9, 11, 13}
	x := make([]chapter02.Features, len(rows))

	for i, row := range rows {
		features, err := chapter02.NewFeatures([]string{"a", "b"}, row)
		if err != nil {
			t.Fatalf("NewFeatures() でエラー: %v", err)
		}

		x[i] = features
	}

	return x, targets
}

func TestFitRidge(t *testing.T) {
	t.Parallel()

	t.Run("alpha が 0 なら第 7 章の最小二乗法と同じ係数になる", func(t *testing.T) {
		t.Parallel()

		x, targets := sample(t)

		ridge, err := chapter12.FitRidge(x, targets, 0)
		if err != nil {
			t.Fatalf("FitRidge() でエラー: %v", err)
		}

		least, err := chapter07.Fit(x, targets)
		if err != nil {
			t.Fatalf("chapter07.Fit() でエラー: %v", err)
		}

		if math.Abs(ridge.Intercept-least.Intercept) > 1e-8 {
			t.Errorf("切片 = %v, want %v", ridge.Intercept, least.Intercept)
		}

		for i, column := range ridge.Columns {
			want, err := least.Coefficient(column)
			if err != nil {
				t.Fatalf("Coefficient() でエラー: %v", err)
			}

			if math.Abs(ridge.Coefficients[i]-want) > 1e-8 {
				t.Errorf("%s の係数 = %v, want %v", column, ridge.Coefficients[i], want)
			}
		}
	})

	t.Run("alpha を大きくすると係数の絶対値の合計が小さくなる", func(t *testing.T) {
		t.Parallel()

		x, targets := sample(t)
		previous := math.Inf(1)

		for _, alpha := range []float64{0, 1, 10, 100} {
			model, err := chapter12.FitRidge(x, targets, alpha)
			if err != nil {
				t.Fatalf("FitRidge() でエラー: %v", err)
			}

			if sum := model.CoefficientAbsSum(); sum >= previous {
				t.Errorf("alpha=%v の係数の合計 %v が、前の %v より小さくありません", alpha, sum, previous)
			} else {
				previous = sum
			}
		}
	})

	t.Run("切片には罰則をかけないので、alpha を上げても予測の平均は正解の平均に近い", func(t *testing.T) {
		t.Parallel()

		x, targets := sample(t)

		model, err := chapter12.FitRidge(x, targets, 1000)
		if err != nil {
			t.Fatalf("FitRidge() でエラー: %v", err)
		}

		predictions, err := model.PredictAll(x)
		if err != nil {
			t.Fatalf("PredictAll() でエラー: %v", err)
		}

		predicted, actual := 0.0, 0.0

		for i := range predictions {
			predicted += predictions[i] / float64(len(predictions))
			actual += targets[i] / float64(len(targets))
		}

		if math.Abs(predicted-actual) > 1e-8 {
			t.Errorf("予測の平均 = %v, want %v", predicted, actual)
		}
	})

	t.Run("alpha が負ならエラーを返す", func(t *testing.T) {
		t.Parallel()

		x, targets := sample(t)

		if _, err := chapter12.FitRidge(x, targets, -1); err == nil {
			t.Error("FitRidge() がエラーを返しませんでした")
		}
	})

	t.Run("特徴量と正解の件数が違えばエラーを返す", func(t *testing.T) {
		t.Parallel()

		x, targets := sample(t)

		if _, err := chapter12.FitRidge(x, targets[:2], 1); err == nil {
			t.Error("FitRidge() がエラーを返しませんでした")
		}
	})
}

func TestSoftThreshold(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		value     float64
		threshold float64
		want      float64
	}{
		{name: "しきい値より小さければ 0 になる", value: 0.5, threshold: 1, want: 0},
		{name: "負の側でもしきい値の内側なら 0 になる", value: -0.5, threshold: 1, want: 0},
		{name: "正の値はしきい値の分だけ 0 に近づく", value: 3, threshold: 1, want: 2},
		{name: "負の値もしきい値の分だけ 0 に近づく", value: -3, threshold: 1, want: -2},
		{name: "しきい値が 0 なら何もしない", value: 3, threshold: 0, want: 3},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if got := chapter12.SoftThreshold(test.value, test.threshold); got != test.want {
				t.Errorf("SoftThreshold(%v, %v) = %v, want %v", test.value, test.threshold, got, test.want)
			}
		})
	}
}

func TestFitLasso(t *testing.T) {
	t.Parallel()

	t.Run("alpha が 0 ならリッジ回帰の alpha 0 と同じ係数になる", func(t *testing.T) {
		t.Parallel()

		x, targets := sample(t)

		lasso, err := chapter12.FitLasso(x, targets, 0)
		if err != nil {
			t.Fatalf("FitLasso() でエラー: %v", err)
		}

		ridge, err := chapter12.FitRidge(x, targets, 0)
		if err != nil {
			t.Fatalf("FitRidge() でエラー: %v", err)
		}

		for i := range lasso.Coefficients {
			if math.Abs(lasso.Coefficients[i]-ridge.Coefficients[i]) > 1e-6 {
				t.Errorf("%s の係数 = %v, want %v", lasso.Columns[i], lasso.Coefficients[i], ridge.Coefficients[i])
			}
		}
	})

	t.Run("alpha を大きくすると係数がちょうど 0 になる", func(t *testing.T) {
		t.Parallel()

		x, targets := sample(t)

		model, err := chapter12.FitLasso(x, targets, 1000)
		if err != nil {
			t.Fatalf("FitLasso() でエラー: %v", err)
		}

		zero := chapter12.ZeroCoefficientNames(model)
		if len(zero) == 0 {
			t.Errorf("0 になった係数がありません: %v", model.Coefficients)
		}
	})

	t.Run("リッジ回帰は係数を 0 に近づけるだけで 0 にはしない", func(t *testing.T) {
		t.Parallel()

		x, targets := sample(t)

		model, err := chapter12.FitRidge(x, targets, 1000)
		if err != nil {
			t.Fatalf("FitRidge() でエラー: %v", err)
		}

		if zero := chapter12.ZeroCoefficientNames(model); len(zero) != 0 {
			t.Errorf("0 になった係数 = %v, want なし", zero)
		}
	})

	t.Run("0 になった列の名前を列の順に返す", func(t *testing.T) {
		t.Parallel()

		model := chapter12.RegularizedModel{
			Columns:      []string{"a", "b", "c"},
			Coefficients: []float64{0, 1.5, 0},
		}

		if got := chapter12.ZeroCoefficientNames(model); !slices.Equal(got, []string{"a", "c"}) {
			t.Errorf("ZeroCoefficientNames() = %v, want [a c]", got)
		}
	})
}

func TestRegularizedModel(t *testing.T) {
	t.Parallel()

	model := chapter12.RegularizedModel{
		Columns:      []string{"a", "b"},
		Coefficients: []float64{2, -3},
		Intercept:    1,
	}

	t.Run("列名で係数を読める", func(t *testing.T) {
		t.Parallel()

		got, err := model.Coefficient("b")
		if err != nil {
			t.Fatalf("Coefficient() でエラー: %v", err)
		}

		if got != -3 {
			t.Errorf("Coefficient() = %v, want -3", got)
		}
	})

	t.Run("無い列を読むとエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := model.Coefficient("z"); err == nil {
			t.Error("Coefficient() がエラーを返しませんでした")
		}
	})

	t.Run("係数の絶対値の合計を返す", func(t *testing.T) {
		t.Parallel()

		if got := model.CoefficientAbsSum(); got != 5 {
			t.Errorf("CoefficientAbsSum() = %v, want 5", got)
		}
	})

	t.Run("切片と係数の重み付き合計で予測する", func(t *testing.T) {
		t.Parallel()

		features, err := chapter02.NewFeatures([]string{"a", "b"}, []float64{2, 1})
		if err != nil {
			t.Fatalf("NewFeatures() でエラー: %v", err)
		}

		got, err := model.Predict(features)
		if err != nil {
			t.Fatalf("Predict() でエラー: %v", err)
		}

		if want := 1.0 + 2*2 - 3*1; got != want {
			t.Errorf("Predict() = %v, want %v", got, want)
		}
	})
}
