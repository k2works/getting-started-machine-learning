package chapter13_test

import (
	"math"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter13"
)

// tolerance は浮動小数点の計算の誤差を許す幅。
const tolerance = 1e-9

// closeTo は 2 つの値が tolerance の中で一致するかを返す。
func closeTo(got, want float64) bool {
	return math.Abs(got-want) <= tolerance
}

// dot は 2 つのベクトルの内積。
func dot(a, b []float64) float64 {
	sum := 0.0
	for i := range a {
		sum += a[i] * b[i]
	}

	return sum
}

func TestCovarianceMatrix(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		x    [][]float64
		want [][]float64
	}{
		{
			// 平均は 2 と 4。差は (-1,-2), (0,0), (1,2) なので、
			// 分散は 2/2=1 と 8/2=4、共分散は 4/2=2 になる
			name: "2 列の分散と共分散を並べる",
			x:    [][]float64{{1, 2}, {2, 4}, {3, 6}},
			want: [][]float64{{1, 2}, {2, 4}},
		},
		{
			// 3 列目は平均 1 のまま動かないので、分散も共分散も 0 になる
			name: "3 列でも各列の分散と 2 列ずつの共分散を並べる",
			x:    [][]float64{{1, 2, 1}, {2, 4, 1}, {3, 6, 1}},
			want: [][]float64{{1, 2, 0}, {2, 4, 0}, {0, 0, 0}},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			got, err := chapter13.CovarianceMatrix(test.x)
			if err != nil {
				t.Fatalf("CovarianceMatrix() でエラー: %v", err)
			}

			for j := range test.want {
				for k := range test.want[j] {
					if !closeTo(got[j][k], test.want[j][k]) {
						t.Errorf("共分散[%d][%d] = %v, want %v", j, k, got[j][k], test.want[j][k])
					}
				}
			}
		})
	}
}

func TestCovarianceMatrixErrors(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		x    [][]float64
	}{
		{name: "1 件も無ければエラー", x: [][]float64{}},
		{name: "1 件だけならエラー", x: [][]float64{{1, 2}}},
		{name: "列の数がそろっていなければエラー", x: [][]float64{{1, 2}, {3}}},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if _, err := chapter13.CovarianceMatrix(test.x); err == nil {
				t.Error("CovarianceMatrix() がエラーを返しませんでした")
			}
		})
	}
}

func TestFit(t *testing.T) {
	t.Parallel()

	t.Run("完全に相関する 2 列なら第 1 主成分だけで分散をすべて説明する", func(t *testing.T) {
		t.Parallel()

		model, err := chapter13.Fit([][]float64{{1, 2}, {2, 4}, {3, 6}}, 2)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if !closeTo(model.ExplainedVarianceRatio[0], 1) {
			t.Errorf("第 1 主成分の寄与率 = %v, want 1", model.ExplainedVarianceRatio[0])
		}

		if !closeTo(model.ExplainedVarianceRatio[1], 0) {
			t.Errorf("第 2 主成分の寄与率 = %v, want 0", model.ExplainedVarianceRatio[1])
		}

		// 2 列目の動きは 1 列目の 2 倍なので、主成分の向きは (1, 2) を長さ 1 にしたもの
		want := []float64{1 / math.Sqrt(5), 2 / math.Sqrt(5)}
		for j, value := range want {
			if !closeTo(model.Components[0][j], value) {
				t.Errorf("第 1 主成分[%d] = %v, want %v", j, model.Components[0][j], value)
			}
		}
	})

	t.Run("寄与率の大きい順に、指定した数だけ並ぶ", func(t *testing.T) {
		t.Parallel()

		model, err := chapter13.Fit([][]float64{{1, 2, 1}, {2, 4, 3}, {3, 6, 2}, {4, 8, 5}}, 2)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if len(model.Components) != 2 {
			t.Fatalf("主成分の数 = %d, want 2", len(model.Components))
		}

		if model.ExplainedVariance[0] < model.ExplainedVariance[1] {
			t.Errorf("分散が大きい順に並んでいません: %v", model.ExplainedVariance)
		}
	})

	t.Run("主成分が固有ベクトルの性質を満たす", func(t *testing.T) {
		t.Parallel()

		x := [][]float64{{1, 2, 1}, {2, 4, 3}, {3, 6, 2}, {4, 8, 5}}

		model, err := chapter13.Fit(x, 3)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		covariance, err := chapter13.CovarianceMatrix(x)
		if err != nil {
			t.Fatalf("CovarianceMatrix() でエラー: %v", err)
		}

		for i, component := range model.Components {
			if !closeTo(dot(component, component), 1) {
				t.Errorf("第 %d 主成分の長さの 2 乗 = %v, want 1", i+1, dot(component, component))
			}

			// 分散共分散行列を掛けると、固有値（= その主成分の分散）倍になる
			for j := range component {
				got := dot(covariance[j], component)
				if want := model.ExplainedVariance[i] * component[j]; !closeTo(got, want) {
					t.Errorf("共分散 × 第 %d 主成分[%d] = %v, want %v", i+1, j, got, want)
				}
			}
		}

		for i := range model.Components {
			for k := i + 1; k < len(model.Components); k++ {
				if got := dot(model.Components[i], model.Components[k]); !closeTo(got, 0) {
					t.Errorf("第 %d 主成分と第 %d 主成分の内積 = %v, want 0", i+1, k+1, got)
				}
			}
		}
	})

	t.Run("主成分の数が範囲の外ならエラー", func(t *testing.T) {
		t.Parallel()

		for _, nComponents := range []int{0, 3} {
			if _, err := chapter13.Fit([][]float64{{1, 2}, {2, 4}}, nComponents); err == nil {
				t.Errorf("Fit(_, %d) がエラーを返しませんでした", nComponents)
			}
		}
	})
}

func TestNormalizeSigns(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name       string
		components [][]float64
		want       [][]float64
	}{
		{
			name:       "絶対値が最大の要素が負なら全体の符号を反転する",
			components: [][]float64{{0.3, -0.9}},
			want:       [][]float64{{-0.3, 0.9}},
		},
		{
			name:       "絶対値が最大の要素が正ならそのまま",
			components: [][]float64{{-0.3, 0.9}},
			want:       [][]float64{{-0.3, 0.9}},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			got := chapter13.NormalizeSigns(test.components)
			for i := range test.want {
				for j := range test.want[i] {
					if !closeTo(got[i][j], test.want[i][j]) {
						t.Errorf("[%d][%d] = %v, want %v", i, j, got[i][j], test.want[i][j])
					}
				}
			}
		})
	}
}

func TestTransform(t *testing.T) {
	t.Parallel()

	t.Run("完全に相関する 2 列は第 1 主成分の 1 本の軸に並ぶ", func(t *testing.T) {
		t.Parallel()

		x := [][]float64{{1, 2}, {2, 4}, {3, 6}}

		model, err := chapter13.Fit(x, 2)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		projected, err := model.Transform(x)
		if err != nil {
			t.Fatalf("Transform() でエラー: %v", err)
		}

		for i, row := range projected {
			if !closeTo(row[1], 0) {
				t.Errorf("%d 件目の第 2 主成分の値 = %v, want 0", i+1, row[1])
			}
		}

		// 中心の (2, 4) は原点に移る
		if !closeTo(projected[1][0], 0) {
			t.Errorf("中心の第 1 主成分の値 = %v, want 0", projected[1][0])
		}

		if !closeTo(projected[0][0], -projected[2][0]) {
			t.Errorf("前後の点が対称になりません: %v と %v", projected[0][0], projected[2][0])
		}
	})

	t.Run("列の数が学習時と違えばエラー", func(t *testing.T) {
		t.Parallel()

		model, err := chapter13.Fit([][]float64{{1, 2}, {2, 4}, {3, 6}}, 1)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if _, err := model.Transform([][]float64{{1, 2, 3}}); err == nil {
			t.Error("Transform() がエラーを返しませんでした")
		}
	})
}

func TestComponentsNeeded(t *testing.T) {
	t.Parallel()

	ratios := []float64{0.5, 0.25, 0.15, 0.1}

	tests := []struct {
		name      string
		threshold float64
		want      int
	}{
		{name: "第 1 主成分で届く", threshold: 0.5, want: 1},
		{name: "2 つ目で届く", threshold: 0.7, want: 2},
		{name: "3 つ目で届く", threshold: 0.8, want: 3},
		{name: "届かなければすべて使う", threshold: 1.5, want: 4},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if got := chapter13.ComponentsNeeded(ratios, test.threshold); got != test.want {
				t.Errorf("ComponentsNeeded(_, %v) = %d, want %d", test.threshold, got, test.want)
			}
		})
	}
}

func TestTopLoadings(t *testing.T) {
	t.Parallel()

	t.Run("係数の絶対値が大きい順に並ぶ", func(t *testing.T) {
		t.Parallel()

		got, err := chapter13.TopLoadings([]float64{0.2, -0.8, 0.5}, []string{"A", "B", "C"}, 2)
		if err != nil {
			t.Fatalf("TopLoadings() でエラー: %v", err)
		}

		want := []chapter13.Loading{{Column: "B", Value: -0.8}, {Column: "C", Value: 0.5}}
		for i := range want {
			if got[i] != want[i] {
				t.Errorf("%d 番目 = %+v, want %+v", i+1, got[i], want[i])
			}
		}
	})

	t.Run("列名の数が合わなければエラー", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter13.TopLoadings([]float64{0.2, 0.5}, []string{"A"}, 1); err == nil {
			t.Error("TopLoadings() がエラーを返しませんでした")
		}
	})
}
