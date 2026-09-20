package chapter13_test

import (
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter13"
	"gonum.org/v1/gonum/mat"
	"gonum.org/v1/gonum/stat"
)

// TestGonumEigenSymLearning は gonum の固有値分解の振る舞いを確かめる学習用テスト。
func TestGonumEigenSymLearning(t *testing.T) {
	t.Parallel()

	// 固有値が 1 と 3 になる対称行列
	symmetric := mat.NewSymDense(2, []float64{2, 1, 1, 2})

	t.Run("固有値は小さい順に並ぶ", func(t *testing.T) {
		t.Parallel()

		var decomposition mat.EigenSym
		if ok := decomposition.Factorize(symmetric, true); !ok {
			t.Fatal("Factorize() が false を返しました")
		}

		want := []float64{1, 3}
		for i, value := range decomposition.Values(nil) {
			if !closeTo(value, want[i]) {
				t.Errorf("固有値[%d] = %v, want %v", i, value, want[i])
			}
		}
	})

	t.Run("固有ベクトルは列に並び、長さが 1 になる", func(t *testing.T) {
		t.Parallel()

		var decomposition mat.EigenSym
		if ok := decomposition.Factorize(symmetric, true); !ok {
			t.Fatal("Factorize() が false を返しました")
		}

		var vectors mat.Dense
		decomposition.VectorsTo(&vectors)

		rows, columns := vectors.Dims()
		if rows != 2 || columns != 2 {
			t.Fatalf("固有ベクトルの大きさ = %d×%d, want 2×2", rows, columns)
		}

		for j := range columns {
			column := mat.Col(nil, j, &vectors)
			if got := dot(column, column); !closeTo(got, 1) {
				t.Errorf("%d 列目の長さの 2 乗 = %v, want 1", j, got)
			}
		}
	})

	t.Run("対称でない値を渡すと右上の三角だけが使われる", func(t *testing.T) {
		t.Parallel()

		// Tribuo は対称でない行列に空の Optional を返すが、gonum は
		// 対称行列を別の型にしていて、左下（ここでは 3）を読まずに右上の 2 で埋める
		asymmetric := mat.NewSymDense(2, []float64{1, 2, 3, 4})

		if got := asymmetric.At(1, 0); !closeTo(got, 2) {
			t.Errorf("左下の要素 = %v, want 2", got)
		}
	})
}

// TestGonumPCLearning は gonum の stat.PC の振る舞いを確かめる学習用テスト。
func TestGonumPCLearning(t *testing.T) {
	t.Parallel()

	x := mat.NewDense(4, 2, []float64{1, 2, 2, 4, 3, 6, 4, 9})

	t.Run("成功したかどうかを真偽で返す", func(t *testing.T) {
		t.Parallel()

		var pc stat.PC
		if ok := pc.PrincipalComponents(x, nil); !ok {
			t.Error("PrincipalComponents() が false を返しました")
		}
	})

	t.Run("VarsTo は分散を大きい順に返し、合計が分散共分散行列の対角の和になる", func(t *testing.T) {
		t.Parallel()

		var pc stat.PC
		if ok := pc.PrincipalComponents(x, nil); !ok {
			t.Fatal("PrincipalComponents() が false を返しました")
		}

		variances := pc.VarsTo(nil)
		if variances[0] < variances[1] {
			t.Errorf("分散が大きい順に並んでいません: %v", variances)
		}

		// 元の 2 列の分散の和（1.666... + 8.916...）と一致する
		if got, want := variances[0]+variances[1], 1.6666666666666665+8.916666666666666; !closeTo(got, want) {
			t.Errorf("分散の合計 = %v, want %v", got, want)
		}
	})

	t.Run("VectorsTo は主成分を列に並べる", func(t *testing.T) {
		t.Parallel()

		var pc stat.PC
		if ok := pc.PrincipalComponents(x, nil); !ok {
			t.Fatal("PrincipalComponents() が false を返しました")
		}

		var vectors mat.Dense
		pc.VectorsTo(&vectors)

		if rows, columns := vectors.Dims(); rows != 2 || columns != 2 {
			t.Fatalf("主成分の大きさ = %d×%d, want 2×2", rows, columns)
		}

		first := mat.Col(nil, 0, &vectors)
		if got := dot(first, first); !closeTo(got, 1) {
			t.Errorf("第 1 主成分の長さの 2 乗 = %v, want 1", got)
		}
	})
}

// TestGonumFit は自作の Fit と gonum の stat.PC を突き合わせる。
func TestGonumFit(t *testing.T) {
	t.Parallel()

	x := [][]float64{{1, 2, 1}, {2, 4, 3}, {3, 6, 2}, {4, 8, 5}}

	t.Run("分散・寄与率・主成分の向きが自作と一致する", func(t *testing.T) {
		t.Parallel()

		mine, err := chapter13.Fit(x, 3)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		theirs, err := chapter13.GonumFit(x, 3)
		if err != nil {
			t.Fatalf("GonumFit() でエラー: %v", err)
		}

		for i := range mine.ExplainedVariance {
			if !closeTo(mine.ExplainedVariance[i], theirs.ExplainedVariance[i]) {
				t.Errorf("第 %d 主成分の分散 = %v, want %v",
					i+1, theirs.ExplainedVariance[i], mine.ExplainedVariance[i])
			}

			if !closeTo(mine.ExplainedVarianceRatio[i], theirs.ExplainedVarianceRatio[i]) {
				t.Errorf("第 %d 主成分の寄与率 = %v, want %v",
					i+1, theirs.ExplainedVarianceRatio[i], mine.ExplainedVarianceRatio[i])
			}

			for j := range mine.Components[i] {
				if !closeTo(mine.Components[i][j], theirs.Components[i][j]) {
					t.Errorf("第 %d 主成分[%d] = %v, want %v",
						i+1, j, theirs.Components[i][j], mine.Components[i][j])
				}
			}
		}
	})

	t.Run("符号をそろえなければ向きが逆になることがある", func(t *testing.T) {
		t.Parallel()

		var pc stat.PC

		flat := make([]float64, 0, len(x)*len(x[0]))
		for _, row := range x {
			flat = append(flat, row...)
		}

		if ok := pc.PrincipalComponents(mat.NewDense(len(x), len(x[0]), flat), nil); !ok {
			t.Fatal("PrincipalComponents() が false を返しました")
		}

		var vectors mat.Dense
		pc.VectorsTo(&vectors)

		mine, err := chapter13.Fit(x, 1)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		raw := mat.Col(nil, 0, &vectors)

		// 符号をそろえる前の主成分は、自作の主成分と同じ向きか、ちょうど逆向きのどちらかになる
		same := true
		opposite := true

		for j := range raw {
			if !closeTo(raw[j], mine.Components[0][j]) {
				same = false
			}

			if !closeTo(raw[j], -mine.Components[0][j]) {
				opposite = false
			}
		}

		if !same && !opposite {
			t.Errorf("gonum の主成分 %v が自作の %v と同じ軸を指していません", raw, mine.Components[0])
		}

		if same == opposite {
			t.Errorf("同じ向きと逆向きの判定が両立しました: %v", raw)
		}

		t.Logf("符号をそろえる前の第 1 主成分: %v（自作と%s）", raw, map[bool]string{true: "同じ向き", false: "逆向き"}[same])
	})
}

// TestGonumFitErrors は gonum 版が自作と同じ条件でエラーを返すことを確かめる。
func TestGonumFitErrors(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name        string
		x           [][]float64
		nComponents int
	}{
		{name: "1 件も無ければエラー", x: [][]float64{}, nComponents: 1},
		{name: "主成分の数が多すぎればエラー", x: [][]float64{{1, 2}, {2, 4}}, nComponents: 3},
		{name: "すべての列の分散が 0 ならエラー", x: [][]float64{{1, 1}, {1, 1}}, nComponents: 1},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if _, err := chapter13.GonumFit(test.x, test.nComponents); err == nil {
				t.Error("GonumFit() がエラーを返しませんでした")
			}
		})
	}
}
