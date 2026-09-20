package chapter11_test

import (
	"math"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter11"
)

// survived は陽性とみなすラベル（生存）。
const survived = "1"

func TestConfusionMatrix(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name      string
		actual    []string
		predicted []string
		want      chapter11.ConfusionMatrix
	}{
		{
			name:      "すべて言い当てれば真陽性と真陰性だけになる",
			actual:    []string{"1", "0"},
			predicted: []string{"1", "0"},
			want:      chapter11.ConfusionMatrix{TruePositive: 1, TrueNegative: 1},
		},
		{
			name:      "陰性を陽性と間違えると偽陽性が増える",
			actual:    []string{"0", "0"},
			predicted: []string{"1", "0"},
			want:      chapter11.ConfusionMatrix{FalsePositive: 1, TrueNegative: 1},
		},
		{
			name:      "陽性を陰性と見逃すと偽陰性が増える",
			actual:    []string{"1", "1"},
			predicted: []string{"0", "1"},
			want:      chapter11.ConfusionMatrix{TruePositive: 1, FalseNegative: 1},
		},
		{
			name:      "4 つの区分をすべて数える",
			actual:    []string{"1", "1", "0", "0"},
			predicted: []string{"1", "0", "1", "0"},
			want: chapter11.ConfusionMatrix{
				TruePositive:  1,
				FalseNegative: 1,
				FalsePositive: 1,
				TrueNegative:  1,
			},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			got, err := chapter11.NewConfusionMatrix(test.actual, test.predicted, survived)
			if err != nil {
				t.Fatalf("NewConfusionMatrix() でエラー: %v", err)
			}

			if got != test.want {
				t.Errorf("NewConfusionMatrix() = %+v, want %+v", got, test.want)
			}
		})
	}

	t.Run("件数が違えばエラーを返す", func(t *testing.T) {
		t.Parallel()

		_, err := chapter11.NewConfusionMatrix([]string{"1", "0"}, []string{"1"}, survived)
		if err == nil {
			t.Fatal("NewConfusionMatrix() がエラーを返しませんでした")
		}

		if want := "正解と予測の件数が違います: 2 と 1"; err.Error() != want {
			t.Errorf("エラー = %q, want %q", err, want)
		}
	})
}

func TestClassificationMetrics(t *testing.T) {
	t.Parallel()

	// 陽性 10 件のうち 6 件を当て、陰性 10 件のうち 4 件を陽性と間違えた混同行列
	matrix := chapter11.ConfusionMatrix{
		TruePositive:  6,
		FalsePositive: 4,
		FalseNegative: 4,
		TrueNegative:  6,
	}

	tests := []struct {
		name  string
		score func(chapter11.ConfusionMatrix) float64
		want  float64
	}{
		{name: "適合率は陽性と予測したうち当たった割合", score: chapter11.Precision, want: 0.6},
		{name: "再現率は陽性のうち見つけられた割合", score: chapter11.Recall, want: 0.6},
		{name: "F 値は適合率と再現率の調和平均", score: chapter11.F1Score, want: 0.6},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if got := test.score(matrix); math.Abs(got-test.want) > 1e-12 {
				t.Errorf("score() = %v, want %v", got, test.want)
			}
		})
	}

	t.Run("適合率と再現率が違えば F 値はその間になる", func(t *testing.T) {
		t.Parallel()

		// 陽性 10 件のうち 9 件を当てるが、陰性 10 件のうち 6 件も陽性と言ってしまう
		unbalanced := chapter11.ConfusionMatrix{
			TruePositive:  9,
			FalsePositive: 6,
			FalseNegative: 1,
			TrueNegative:  4,
		}

		precision, recall := chapter11.Precision(unbalanced), chapter11.Recall(unbalanced)
		f1 := chapter11.F1Score(unbalanced)

		if !(precision < f1 && f1 < recall) {
			t.Errorf("適合率 %v < F 値 %v < 再現率 %v になっていません", precision, f1, recall)
		}
	})

	t.Run("陽性と予測しなければ適合率も再現率も F 値も 0 になる", func(t *testing.T) {
		t.Parallel()

		empty := chapter11.ConfusionMatrix{FalseNegative: 3, TrueNegative: 7}

		for name, got := range map[string]float64{
			"適合率": chapter11.Precision(empty),
			"再現率": chapter11.Recall(empty),
			"F 値": chapter11.F1Score(empty),
		} {
			if got != 0 {
				t.Errorf("%s = %v, want 0", name, got)
			}
		}
	})

	t.Run("ClassificationMetric は混同行列の指標を評価関数にする", func(t *testing.T) {
		t.Parallel()

		metric := chapter11.ClassificationMetric(chapter11.Recall, survived)

		got, err := metric([]string{"1", "1", "0"}, []string{"1", "0", "0"})
		if err != nil {
			t.Fatalf("metric() でエラー: %v", err)
		}

		if want := 0.5; got != want {
			t.Errorf("metric() = %v, want %v", got, want)
		}
	})
}

func TestRegressionMetrics(t *testing.T) {
	t.Parallel()

	t.Run("ぴったり当たれば平均二乗誤差は 0 になる", func(t *testing.T) {
		t.Parallel()

		got, err := chapter11.MeanSquaredError([]float64{1, 2, 3}, []float64{1, 2, 3})
		if err != nil {
			t.Fatalf("MeanSquaredError() でエラー: %v", err)
		}

		if got != 0 {
			t.Errorf("MeanSquaredError() = %v, want 0", got)
		}
	})

	t.Run("誤差を 2 乗してから平均する", func(t *testing.T) {
		t.Parallel()

		got, err := chapter11.MeanSquaredError([]float64{1, 2}, []float64{3, 2})
		if err != nil {
			t.Fatalf("MeanSquaredError() でエラー: %v", err)
		}

		if want := 2.0; got != want {
			t.Errorf("MeanSquaredError() = %v, want %v", got, want)
		}
	})

	t.Run("大きく外した 1 件に平均二乗誤差は敏感に反応する", func(t *testing.T) {
		t.Parallel()

		// 10 件のうち 1 件だけ 10 外す場合と、10 件すべてを 1 ずつ外す場合
		actual := make([]float64, 10)
		spike := make([]float64, 10)
		spread := make([]float64, 10)

		spike[0] = 10

		for i := range spread {
			spread[i] = 1
		}

		big, err := chapter11.MeanSquaredError(actual, spike)
		if err != nil {
			t.Fatalf("MeanSquaredError() でエラー: %v", err)
		}

		small, err := chapter11.MeanSquaredError(actual, spread)
		if err != nil {
			t.Fatalf("MeanSquaredError() でエラー: %v", err)
		}

		if big <= small {
			t.Errorf("1 件を大きく外した誤差 %v が、全件を少しずつ外した誤差 %v 以下です", big, small)
		}
	})
}

func TestAccuracy(t *testing.T) {
	t.Parallel()

	t.Run("一致した割合を返す", func(t *testing.T) {
		t.Parallel()

		got, err := chapter11.Accuracy([]string{"1", "1", "0", "0"}, []string{"1", "0", "0", "0"})
		if err != nil {
			t.Fatalf("Accuracy() でエラー: %v", err)
		}

		if want := 0.75; got != want {
			t.Errorf("Accuracy() = %v, want %v", got, want)
		}
	})

	t.Run("件数が違えばエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter11.Accuracy([]string{"1"}, []string{"1", "0"}); err == nil {
			t.Fatal("Accuracy() がエラーを返しませんでした")
		}
	})
}
