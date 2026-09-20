package chapter07_test

import (
	"math"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
)

func TestSimpleLinearRegressionMatchesNormalEquation(t *testing.T) {
	t.Parallel()

	// 直線に乗らない 5 点。正規方程式と stat.LinearRegression が同じ最小二乗解を出すはず。
	columns := []string{"x1"}
	x := []chapter02.Features{
		featuresOf(t, columns, []float64{1}),
		featuresOf(t, columns, []float64{2}),
		featuresOf(t, columns, []float64{3}),
		featuresOf(t, columns, []float64{4}),
		featuresOf(t, columns, []float64{5}),
	}
	target := []float64{2.1, 3.9, 6.2, 7.8, 10.1}

	ours, err := chapter07.Fit(x, target)
	if err != nil {
		t.Fatalf("学習できません: %v", err)
	}

	theirs, err := chapter07.SimpleLinearRegression(x, target, "x1")
	if err != nil {
		t.Fatalf("学習できません: %v", err)
	}

	if math.Abs(ours.Intercept-theirs.Intercept) > tolerance {
		t.Errorf("切片が違います: %v と %v", ours.Intercept, theirs.Intercept)
	}

	ourSlope, err := ours.Coefficient("x1")
	if err != nil {
		t.Fatalf("係数を読めません: %v", err)
	}

	theirSlope, err := theirs.Coefficient("x1")
	if err != nil {
		t.Fatalf("係数を読めません: %v", err)
	}

	if math.Abs(ourSlope-theirSlope) > tolerance {
		t.Errorf("係数が違います: %v と %v", ourSlope, theirSlope)
	}
}
