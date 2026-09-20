package chapter07_test

import (
	"math"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
)

// tolerance は浮動小数点の比較に使う許容誤差。
const tolerance = 1e-9

// featuresOf はテスト用に特徴量を作る。作れなければテストを止める。
func featuresOf(t *testing.T, columns []string, values []float64) chapter02.Features {
	t.Helper()

	features, err := chapter02.NewFeatures(columns, values)
	if err != nil {
		t.Fatalf("特徴量を作れません: %v", err)
	}

	return features
}

func TestFit(t *testing.T) {
	t.Parallel()

	// t = 3 + 2 * x1 を満たす 3 点。切片 3、係数 2 が求まるはず。
	x := []chapter02.Features{
		featuresOf(t, []string{"x1"}, []float64{0}),
		featuresOf(t, []string{"x1"}, []float64{1}),
		featuresOf(t, []string{"x1"}, []float64{2}),
	}
	target := []float64{3, 5, 7}

	model, err := chapter07.Fit(x, target)
	if err != nil {
		t.Fatalf("学習できません: %v", err)
	}

	if math.Abs(model.Intercept-3) > tolerance {
		t.Errorf("切片が違います: %v", model.Intercept)
	}

	coefficient, err := model.Coefficient("x1")
	if err != nil {
		t.Fatalf("係数を読めません: %v", err)
	}

	if math.Abs(coefficient-2) > tolerance {
		t.Errorf("係数が違います: %v", coefficient)
	}
}

func TestFitMultiple(t *testing.T) {
	t.Parallel()

	// t = 1 + 2 * x1 - 3 * x2 を満たす 4 点。
	columns := []string{"x1", "x2"}
	x := []chapter02.Features{
		featuresOf(t, columns, []float64{0, 0}),
		featuresOf(t, columns, []float64{1, 0}),
		featuresOf(t, columns, []float64{0, 1}),
		featuresOf(t, columns, []float64{1, 1}),
	}
	target := []float64{1, 3, -2, 0}

	model, err := chapter07.Fit(x, target)
	if err != nil {
		t.Fatalf("学習できません: %v", err)
	}

	expected := map[string]float64{"x1": 2, "x2": -3}

	if math.Abs(model.Intercept-1) > tolerance {
		t.Errorf("切片が違います: %v", model.Intercept)
	}

	for column, want := range expected {
		got, err := model.Coefficient(column)
		if err != nil {
			t.Fatalf("係数を読めません: %v", err)
		}

		if math.Abs(got-want) > tolerance {
			t.Errorf("%s の係数が違います: %v（期待 %v）", column, got, want)
		}
	}
}
