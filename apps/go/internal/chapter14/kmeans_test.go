package chapter14_test

import (
	"math"
	"slices"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter14"
)

// tolerance は浮動小数点の計算の誤差を許す幅。
const tolerance = 1e-9

// closeTo は 2 つの値が tolerance の中で一致するかを返す。
func closeTo(got, want float64) bool {
	return math.Abs(got-want) <= tolerance
}

// 左下と右上に 2 つのかたまりがある、架空の 6 点。
var twoBlobs = [][]float64{
	{0, 0}, {0, 1}, {1, 0},
	{10, 10}, {10, 11}, {11, 10},
}

func TestSquaredDistance(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		a    []float64
		b    []float64
		want float64
	}{
		{name: "同じ点なら 0", a: []float64{1, 2}, b: []float64{1, 2}, want: 0},
		{name: "3 と 4 の直角三角形なら 25", a: []float64{0, 0}, b: []float64{3, 4}, want: 25},
		{name: "3 次元でも各軸の差の 2 乗を足す", a: []float64{1, 1, 1}, b: []float64{2, 3, 5}, want: 21},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			got, err := chapter14.SquaredDistance(test.a, test.b)
			if err != nil {
				t.Fatalf("SquaredDistance() でエラー: %v", err)
			}

			if !closeTo(got, test.want) {
				t.Errorf("SquaredDistance() = %v, want %v", got, test.want)
			}
		})
	}

	t.Run("次元が違えばエラー", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter14.SquaredDistance([]float64{1, 2}, []float64{1}); err == nil {
			t.Error("SquaredDistance() がエラーを返しませんでした")
		}
	})
}

func TestAssignClusters(t *testing.T) {
	t.Parallel()

	t.Run("各点を最も近い中心に割り当てる", func(t *testing.T) {
		t.Parallel()

		centers := [][]float64{{0, 0}, {10, 10}}

		got, err := chapter14.AssignClusters(twoBlobs, centers)
		if err != nil {
			t.Fatalf("AssignClusters() でエラー: %v", err)
		}

		want := []int{0, 0, 0, 1, 1, 1}
		if !slices.Equal(got, want) {
			t.Errorf("AssignClusters() = %v, want %v", got, want)
		}
	})

	t.Run("距離が同じなら番号の小さいクラスタに入れる", func(t *testing.T) {
		t.Parallel()

		got, err := chapter14.AssignClusters([][]float64{{1, 0}}, [][]float64{{0, 0}, {2, 0}})
		if err != nil {
			t.Fatalf("AssignClusters() でエラー: %v", err)
		}

		if want := []int{0}; !slices.Equal(got, want) {
			t.Errorf("AssignClusters() = %v, want %v", got, want)
		}
	})

	t.Run("点や中心が無ければエラー", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter14.AssignClusters(nil, [][]float64{{0, 0}}); err == nil {
			t.Error("点が無いのにエラーを返しませんでした")
		}

		if _, err := chapter14.AssignClusters(twoBlobs, nil); err == nil {
			t.Error("中心が無いのにエラーを返しませんでした")
		}
	})

	t.Run("点と中心の次元が違えばエラー", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter14.AssignClusters(twoBlobs, [][]float64{{0, 0, 0}}); err == nil {
			t.Error("AssignClusters() がエラーを返しませんでした")
		}
	})
}

func TestUpdateCenters(t *testing.T) {
	t.Parallel()

	t.Run("クラスタごとに割り当てられた点の平均を返す", func(t *testing.T) {
		t.Parallel()

		labels := []int{0, 0, 0, 1, 1, 1}
		previous := [][]float64{{0, 0}, {10, 10}}

		got, err := chapter14.UpdateCenters(twoBlobs, labels, previous)
		if err != nil {
			t.Fatalf("UpdateCenters() でエラー: %v", err)
		}

		want := [][]float64{{1.0 / 3, 1.0 / 3}, {31.0 / 3, 31.0 / 3}}
		for k := range want {
			for j := range want[k] {
				if !closeTo(got[k][j], want[k][j]) {
					t.Errorf("中心[%d][%d] = %v, want %v", k, j, got[k][j], want[k][j])
				}
			}
		}
	})

	t.Run("点が 1 つも無いクラスタは前の中心を残す", func(t *testing.T) {
		t.Parallel()

		labels := []int{0, 0, 0, 0, 0, 0}
		previous := [][]float64{{0, 0}, {99, 99}}

		got, err := chapter14.UpdateCenters(twoBlobs, labels, previous)
		if err != nil {
			t.Fatalf("UpdateCenters() でエラー: %v", err)
		}

		if want := []float64{99, 99}; !slices.Equal(got[1], want) {
			t.Errorf("空のクラスタの中心 = %v, want %v", got[1], want)
		}
	})

	t.Run("クラスタ番号が範囲の外ならエラー", func(t *testing.T) {
		t.Parallel()

		previous := [][]float64{{0, 0}, {10, 10}}

		if _, err := chapter14.UpdateCenters(twoBlobs, []int{0, 0, 0, 1, 1, 2}, previous); err == nil {
			t.Error("UpdateCenters() がエラーを返しませんでした")
		}
	})

	t.Run("点とクラスタ番号の数が違えばエラー", func(t *testing.T) {
		t.Parallel()

		previous := [][]float64{{0, 0}, {10, 10}}

		if _, err := chapter14.UpdateCenters(twoBlobs, []int{0, 1}, previous); err == nil {
			t.Error("UpdateCenters() がエラーを返しませんでした")
		}
	})
}

func TestSumOfSquaredErrors(t *testing.T) {
	t.Parallel()

	t.Run("中心と一致していれば 0", func(t *testing.T) {
		t.Parallel()

		points := [][]float64{{0, 0}, {10, 10}}

		got, err := chapter14.SumOfSquaredErrors(points, []int{0, 1}, points)
		if err != nil {
			t.Fatalf("SumOfSquaredErrors() でエラー: %v", err)
		}

		if !closeTo(got, 0) {
			t.Errorf("SumOfSquaredErrors() = %v, want 0", got)
		}
	})

	t.Run("各点と所属する中心の距離の 2 乗を足す", func(t *testing.T) {
		t.Parallel()

		points := [][]float64{{0, 0}, {3, 4}}
		centers := [][]float64{{0, 0}, {0, 0}}

		got, err := chapter14.SumOfSquaredErrors(points, []int{0, 1}, centers)
		if err != nil {
			t.Fatalf("SumOfSquaredErrors() でエラー: %v", err)
		}

		if !closeTo(got, 25) {
			t.Errorf("SumOfSquaredErrors() = %v, want 25", got)
		}
	})
}

func TestFit(t *testing.T) {
	t.Parallel()

	t.Run("2 つのかたまりを言い当てる", func(t *testing.T) {
		t.Parallel()

		// わざと両方とも左下寄りの中心から始める
		result, err := chapter14.Fit(twoBlobs, [][]float64{{0, 0}, {1, 1}})
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if result.Labels[0] == result.Labels[3] {
			t.Errorf("左下と右上が同じクラスタになりました: %v", result.Labels)
		}

		for _, i := range []int{1, 2} {
			if result.Labels[i] != result.Labels[0] {
				t.Errorf("左下のかたまりが分かれました: %v", result.Labels)
			}
		}

		for _, i := range []int{4, 5} {
			if result.Labels[i] != result.Labels[3] {
				t.Errorf("右上のかたまりが分かれました: %v", result.Labels)
			}
		}

		// 各かたまりの平均（1/3, 1/3）と（31/3, 31/3）からの距離の 2 乗の合計
		if want := 4.0 / 3 * 2; !closeTo(result.SSE, want) {
			t.Errorf("SSE = %v, want %v", result.SSE, want)
		}
	})

	t.Run("中心が動かなければ 1 回で止まる", func(t *testing.T) {
		t.Parallel()

		points := [][]float64{{0, 0}, {10, 10}}

		// 点そのものを中心にすると、更新しても中心は変わらない
		result, err := chapter14.FitWithMaxIterations(points, points, 1)
		if err != nil {
			t.Fatalf("FitWithMaxIterations() でエラー: %v", err)
		}

		if !closeTo(result.SSE, 0) {
			t.Errorf("SSE = %v, want 0", result.SSE)
		}
	})

	t.Run("回数の上限で打ち切る", func(t *testing.T) {
		t.Parallel()

		// 縦に並んだ中心から始めると、1 回の更新ではかたまりに分かれきらない
		initial := [][]float64{{0, 0}, {0, 1}}

		once, err := chapter14.FitWithMaxIterations(twoBlobs, initial, 1)
		if err != nil {
			t.Fatalf("FitWithMaxIterations() でエラー: %v", err)
		}

		converged, err := chapter14.Fit(twoBlobs, initial)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if once.SSE <= converged.SSE {
			t.Errorf("1 回で打ち切った SSE %v が、収束した SSE %v 以下になりました", once.SSE, converged.SSE)
		}
	})

	t.Run("更新の回数の上限が 0 以下ならエラー", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter14.FitWithMaxIterations(twoBlobs, [][]float64{{0, 0}}, 0); err == nil {
			t.Error("FitWithMaxIterations() がエラーを返しませんでした")
		}
	})
}

func TestChooseInitialCenters(t *testing.T) {
	t.Parallel()

	t.Run("同じシードなら同じ初期中心を選ぶ", func(t *testing.T) {
		t.Parallel()

		first, err := chapter14.ChooseInitialCenters(twoBlobs, 2, 0)
		if err != nil {
			t.Fatalf("ChooseInitialCenters() でエラー: %v", err)
		}

		second, err := chapter14.ChooseInitialCenters(twoBlobs, 2, 0)
		if err != nil {
			t.Fatalf("ChooseInitialCenters() でエラー: %v", err)
		}

		for k := range first {
			if !slices.Equal(first[k], second[k]) {
				t.Errorf("%d 番目の中心 = %v, want %v", k, second[k], first[k])
			}
		}
	})

	t.Run("選んだ中心は元の点のどれかで、重ならない", func(t *testing.T) {
		t.Parallel()

		centers, err := chapter14.ChooseInitialCenters(twoBlobs, 3, 1)
		if err != nil {
			t.Fatalf("ChooseInitialCenters() でエラー: %v", err)
		}

		for k, center := range centers {
			if !slices.ContainsFunc(twoBlobs, func(point []float64) bool { return slices.Equal(point, center) }) {
				t.Errorf("%d 番目の中心 %v が元の点にありません", k, center)
			}

			for l := k + 1; l < len(centers); l++ {
				if slices.Equal(center, centers[l]) {
					t.Errorf("%d 番目と %d 番目の中心が同じです: %v", k, l, center)
				}
			}
		}
	})

	t.Run("返した中心を書き換えても元の点は変わらない", func(t *testing.T) {
		t.Parallel()

		points := [][]float64{{1, 2}, {3, 4}}

		centers, err := chapter14.ChooseInitialCenters(points, 2, 0)
		if err != nil {
			t.Fatalf("ChooseInitialCenters() でエラー: %v", err)
		}

		centers[0][0] = 99

		for i, point := range points {
			if point[0] == 99 {
				t.Errorf("%d 番目の点が書き換わりました: %v", i, point)
			}
		}
	})

	t.Run("クラスタ数が点の数より多ければエラー", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter14.ChooseInitialCenters(twoBlobs, 7, 0); err == nil {
			t.Error("ChooseInitialCenters() がエラーを返しませんでした")
		}
	})
}

func TestFitWithRestarts(t *testing.T) {
	t.Parallel()

	t.Run("初期中心を何通りか試して SSE の小さいほうを選ぶ", func(t *testing.T) {
		t.Parallel()

		once, err := chapter14.FitWithRestarts(twoBlobs, 2, 0, 1)
		if err != nil {
			t.Fatalf("FitWithRestarts() でエラー: %v", err)
		}

		many, err := chapter14.FitWithRestarts(twoBlobs, 2, 0, chapter14.DefaultNInit)
		if err != nil {
			t.Fatalf("FitWithRestarts() でエラー: %v", err)
		}

		if many.SSE > once.SSE+tolerance {
			t.Errorf("10 通り試した SSE %v が、1 通りの %v より大きくなりました", many.SSE, once.SSE)
		}
	})

	t.Run("初期中心の選び方が悪いと局所解で止まる", func(t *testing.T) {
		t.Parallel()

		// 3 つの中心をすべて左下のかたまりに置くと、右上の 3 点が 1 つのクラスタにまとまり、
		// 左下は 3 つに分かれないまま止まる
		stuck, err := chapter14.Fit(twoBlobs, [][]float64{{0, 0}, {0, 1}, {1, 0}})
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if !closeTo(stuck.SSE, 8.0/3) {
			t.Errorf("局所解の SSE = %v, want %v", stuck.SSE, 8.0/3)
		}

		best, err := chapter14.FitWithRestarts(twoBlobs, 3, 0, chapter14.DefaultNInit)
		if err != nil {
			t.Fatalf("FitWithRestarts() でエラー: %v", err)
		}

		if !closeTo(best.SSE, 11.0/6) {
			t.Errorf("初期中心を 10 通り試した SSE = %v, want %v", best.SSE, 11.0/6)
		}
	})

	t.Run("試す回数が 0 以下ならエラー", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter14.FitWithRestarts(twoBlobs, 2, 0, 0); err == nil {
			t.Error("FitWithRestarts() がエラーを返しませんでした")
		}
	})
}

func TestSSEByClusterCount(t *testing.T) {
	t.Parallel()

	t.Run("クラスタ数を増やすと SSE は小さくなる", func(t *testing.T) {
		t.Parallel()

		results, err := chapter14.SSEByClusterCount(twoBlobs, []int{1, 2, 3}, 0, chapter14.DefaultNInit)
		if err != nil {
			t.Fatalf("SSEByClusterCount() でエラー: %v", err)
		}

		for i := 1; i < len(results); i++ {
			if results[i].SSE > results[i-1].SSE+tolerance {
				t.Errorf("クラスタ数 %d の SSE %v が、%d の %v より大きくなりました",
					results[i].Clusters, results[i].SSE, results[i-1].Clusters, results[i-1].SSE)
			}
		}
	})

	t.Run("クラスタ数 1 の SSE は全体の平均からの距離の 2 乗の合計", func(t *testing.T) {
		t.Parallel()

		results, err := chapter14.SSEByClusterCount(twoBlobs, []int{1}, 0, 1)
		if err != nil {
			t.Fatalf("SSEByClusterCount() でエラー: %v", err)
		}

		// 平均は (16/3, 16/3)。x も y も差の 2 乗の合計が 1362/9 なので、SSE は 2724/9 になる
		if want := 2724.0 / 9; !closeTo(results[0].SSE, want) {
			t.Errorf("クラスタ数 1 の SSE = %v, want %v", results[0].SSE, want)
		}
	})
}
