package chapter11_test

import (
	"math"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter11"
)

// aucOf は確率と正解から自作の AUC を求める。
func aucOf(t *testing.T, scores []float64, actual []string) float64 {
	t.Helper()

	points, err := chapter11.ROCCurve(scores, actual, survived)
	if err != nil {
		t.Fatalf("ROCCurve() でエラー: %v", err)
	}

	auc, err := chapter11.AUC(points)
	if err != nil {
		t.Fatalf("AUC() でエラー: %v", err)
	}

	return auc
}

func TestROCCurve(t *testing.T) {
	t.Parallel()

	t.Run("始点は (0, 0) で終点は (1, 1) になる", func(t *testing.T) {
		t.Parallel()

		points, err := chapter11.ROCCurve([]float64{0.9, 0.4, 0.6, 0.1}, []string{"1", "0", "1", "0"}, survived)
		if err != nil {
			t.Fatalf("ROCCurve() でエラー: %v", err)
		}

		first, last := points[0], points[len(points)-1]

		if first.FPR != 0 || first.TPR != 0 {
			t.Errorf("始点 = (%v, %v), want (0, 0)", first.FPR, first.TPR)
		}

		if last.FPR != 1 || last.TPR != 1 {
			t.Errorf("終点 = (%v, %v), want (1, 1)", last.FPR, last.TPR)
		}
	})

	t.Run("同じ確率の行は 1 つの点にまとめる", func(t *testing.T) {
		t.Parallel()

		points, err := chapter11.ROCCurve([]float64{0.5, 0.5, 0.5, 0.5}, []string{"1", "0", "1", "0"}, survived)
		if err != nil {
			t.Fatalf("ROCCurve() でエラー: %v", err)
		}

		// 始点と「全員を陽性と言う」点の 2 つだけになる
		if len(points) != 2 {
			t.Errorf("点の数 = %d, want 2", len(points))
		}
	})

	t.Run("陽性か陰性が片方しかなければエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter11.ROCCurve([]float64{0.9, 0.1}, []string{"1", "1"}, survived); err == nil {
			t.Error("ROCCurve() がエラーを返しませんでした")
		}
	})
}

func TestAUC(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		scores []float64
		actual []string
		want   float64
	}{
		{
			name:   "陽性の確率がすべて陰性より高ければ 1 になる",
			scores: []float64{0.9, 0.8, 0.2, 0.1},
			actual: []string{"1", "1", "0", "0"},
			want:   1,
		},
		{
			name:   "並びが逆なら 0 になる",
			scores: []float64{0.1, 0.2, 0.8, 0.9},
			actual: []string{"1", "1", "0", "0"},
			want:   0,
		},
		{
			name:   "すべて同じ確率なら当てずっぽうの 0.5 になる",
			scores: []float64{0.5, 0.5, 0.5, 0.5},
			actual: []string{"1", "0", "1", "0"},
			want:   0.5,
		},
		{
			name:   "陽性 1 件が陰性 2 件中 1 件より上なら 0.5 になる",
			scores: []float64{0.5, 0.9, 0.1},
			actual: []string{"1", "0", "0"},
			want:   0.5,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if got := aucOf(t, test.scores, test.actual); math.Abs(got-test.want) > 1e-12 {
				t.Errorf("AUC() = %v, want %v", got, test.want)
			}
		})
	}

	t.Run("点が 1 つだけならエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter11.AUC([]chapter11.ROCPoint{{}}); err == nil {
			t.Error("AUC() がエラーを返しませんでした")
		}
	})
}

func TestGonumAUC(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		scores []float64
		actual []string
	}{
		{name: "完全に分かれる", scores: []float64{0.9, 0.8, 0.2, 0.1}, actual: []string{"1", "1", "0", "0"}},
		{name: "逆に並ぶ", scores: []float64{0.1, 0.2, 0.8, 0.9}, actual: []string{"1", "1", "0", "0"}},
		{name: "入り混じる", scores: []float64{0.9, 0.4, 0.6, 0.1}, actual: []string{"1", "0", "1", "0"}},
		{name: "同じ確率が並ぶ", scores: []float64{0.5, 0.5, 0.5, 0.5}, actual: []string{"1", "0", "1", "0"}},
		{
			name:   "件数が偏る",
			scores: []float64{0.9, 0.7, 0.55, 0.5, 0.4, 0.2},
			actual: []string{"0", "1", "0", "1", "0", "0"},
		},
	}

	for _, test := range tests {
		t.Run(test.name+"（自作と gonum が一致する）", func(t *testing.T) {
			t.Parallel()

			gonumAUC, err := chapter11.GonumAUC(test.scores, test.actual, survived)
			if err != nil {
				t.Fatalf("GonumAUC() でエラー: %v", err)
			}

			if own := aucOf(t, test.scores, test.actual); math.Abs(own-gonumAUC) > 1e-12 {
				t.Errorf("自作の AUC = %v, gonum の AUC = %v", own, gonumAUC)
			}
		})
	}

	t.Run("並べ替えていない確率を渡しても panic しない", func(t *testing.T) {
		t.Parallel()

		// stat.ROC は昇順に並んでいない入力で panic するので、GonumAUC の中で並べ替える
		if _, err := chapter11.GonumAUC([]float64{0.9, 0.1, 0.5}, []string{"1", "0", "1"}, survived); err != nil {
			t.Fatalf("GonumAUC() でエラー: %v", err)
		}
	})
}

func TestSigmoid(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		z    float64
		want float64
	}{
		{name: "0 なら 0.5", z: 0, want: 0.5},
		{name: "大きな正の値でも 1 を超えない", z: 1000, want: 1},
		{name: "大きな負の値でもあふれずに 0 になる", z: -1000, want: 0},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			got := chapter11.Sigmoid(test.z)
			if math.IsNaN(got) {
				t.Fatalf("Sigmoid(%v) = NaN", test.z)
			}

			if math.Abs(got-test.want) > 1e-12 {
				t.Errorf("Sigmoid(%v) = %v, want %v", test.z, got, test.want)
			}
		})
	}
}
