package chapter07_test

import (
	"math"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
)

func TestRegressionMetrics(t *testing.T) {
	t.Parallel()

	// 実測値と、そこから 1・2・3 ずれた予測値。
	target := []float64{10, 20, 30}
	predictions := []float64{11, 18, 33}

	tests := []struct {
		name   string
		metric func([]float64, []float64) (float64, error)
		want   float64
	}{
		{
			name:   "平均絶対誤差は誤差の絶対値の平均",
			metric: chapter07.MeanAbsoluteError,
			want:   (1.0 + 2.0 + 3.0) / 3,
		},
		{
			name:   "二乗平均平方根誤差は誤差の 2 乗の平均の平方根",
			metric: chapter07.RootMeanSquaredError,
			want:   math.Sqrt((1.0 + 4.0 + 9.0) / 3),
		},
		{
			name:   "決定係数は 1 から残差平方和を全平方和で割った値を引いた値",
			metric: chapter07.R2Score,
			want:   1 - 14.0/200.0,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			got, err := test.metric(target, predictions)
			if err != nil {
				t.Fatalf("計算できません: %v", err)
			}

			if math.Abs(got-test.want) > tolerance {
				t.Errorf("値が違います: %v（期待 %v）", got, test.want)
			}
		})
	}
}

func TestRegressionMetricsCountMismatch(t *testing.T) {
	t.Parallel()

	if _, err := chapter07.MeanAbsoluteError([]float64{1, 2}, []float64{1}); err == nil {
		t.Error("件数が違えばエラーになるはずです")
	}
}
