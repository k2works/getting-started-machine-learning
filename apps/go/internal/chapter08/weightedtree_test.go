package chapter08_test

import (
	"math"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter08"
)

// tolerance は浮動小数点の比較に使う許容誤差。
const tolerance = 1e-9

func TestWeightedGini(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		labels  []int
		weights []float64
		want    float64
	}{
		{
			name:    "ラベルが 1 種類なら 0",
			labels:  []int{1, 1, 1},
			weights: []float64{1, 1, 1},
			want:    0,
		},
		{
			name:    "重みがすべて 1 なら第 3 章のジニ不純度と同じ",
			labels:  []int{0, 0, 1, 1},
			weights: []float64{1, 1, 1, 1},
			want:    0.5,
		},
		{
			name:    "少数派を重くすると不純度が上がる",
			labels:  []int{0, 0, 0, 1},
			weights: []float64{1, 1, 1, 3},
			want:    0.5,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			got, err := chapter08.WeightedGini(test.labels, test.weights)
			if err != nil {
				t.Fatalf("計算できません: %v", err)
			}

			if math.Abs(got-test.want) > tolerance {
				t.Errorf("ジニ不純度 = %v, want %v", got, test.want)
			}
		})
	}
}

func TestBalancedWeights(t *testing.T) {
	t.Parallel()

	// 生存 1 人、死亡 3 人。件数に反比例する重みは 4/(2*1)=2 と 4/(2*3)=0.667。
	got, err := chapter08.BalancedWeights([]int{0, 0, 0, 1})
	if err != nil {
		t.Fatalf("計算できません: %v", err)
	}

	want := []float64{2.0 / 3, 2.0 / 3, 2.0 / 3, 2}
	for i := range want {
		if math.Abs(got[i]-want[i]) > tolerance {
			t.Fatalf("重み = %v, want %v", got, want)
		}
	}
}
