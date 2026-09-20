package chapter13_test

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter13"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

// requireData は学習データが無ければテストを飛ばす。
func requireData(t *testing.T) string {
	t.Helper()

	csvFile := filepath.Join(dataset.Current(), "Boston.csv")
	if _, err := os.Stat(csvFile); err != nil {
		t.Skip("学習データ Boston.csv が配置されていない（gulp data:setup）")
	}

	return csvFile
}

// bostonPoints は前処理をした Boston を、1 件 1 行の点にして返す。
func bostonPoints(t *testing.T) ([]chapter02.Features, [][]float64) {
	t.Helper()

	features, err := chapter13.LoadBoston(requireData(t))
	if err != nil {
		t.Fatalf("LoadBoston() でエラー: %v", err)
	}

	return features, chapter13.ToPoints(features)
}

func TestBostonPcaData(t *testing.T) {
	t.Parallel()

	t.Run("前処理で 100 件 15 列になり、欠損値が残らない", func(t *testing.T) {
		t.Parallel()

		features, points := bostonPoints(t)

		if len(features) != 100 {
			t.Errorf("件数 = %d, want 100", len(features))
		}

		if len(features[0].Columns) != 15 {
			t.Errorf("列数 = %d, want 15", len(features[0].Columns))
		}

		for i, point := range points {
			for j, value := range point {
				if math.IsNaN(value) {
					t.Fatalf("%d 件目の %d 列目が NaN です", i+1, j)
				}
			}
		}
	})

	t.Run("寄与率の合計が 1 になる", func(t *testing.T) {
		t.Parallel()

		_, points := bostonPoints(t)

		model, err := chapter13.Fit(points, 15)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		total := 0.0
		for _, ratio := range model.ExplainedVarianceRatio {
			total += ratio
		}

		if !closeTo(total, 1) {
			t.Errorf("寄与率の合計 = %v, want 1", total)
		}
	})

	t.Run("実データでも gonum の stat.PC と一致する", func(t *testing.T) {
		t.Parallel()

		_, points := bostonPoints(t)

		mine, err := chapter13.Fit(points, 6)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		theirs, err := chapter13.GonumFit(points, 6)
		if err != nil {
			t.Fatalf("GonumFit() でエラー: %v", err)
		}

		// 実データでは固有値分解と特異値分解の誤差が積もるので、小数第 9 位までは求めない
		const dataTolerance = 1e-8

		for i := range mine.ExplainedVarianceRatio {
			if math.Abs(mine.ExplainedVarianceRatio[i]-theirs.ExplainedVarianceRatio[i]) > dataTolerance {
				t.Errorf("第 %d 主成分の寄与率 = %v, want %v",
					i+1, theirs.ExplainedVarianceRatio[i], mine.ExplainedVarianceRatio[i])
			}

			for j := range mine.Components[i] {
				if math.Abs(mine.Components[i][j]-theirs.Components[i][j]) > dataTolerance {
					t.Errorf("第 %d 主成分[%d] = %v, want %v",
						i+1, j, theirs.Components[i][j], mine.Components[i][j])
				}
			}
		}
	})

	t.Run("実行すると寄与率と主成分の意味を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t)

		var out bytes.Buffer
		if err := chapter13.Run(&out); err != nil {
			t.Fatalf("Run() でエラー: %v", err)
		}

		want := "データ件数: 100, 列数: 15\n" +
			"寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581\n" +
			"累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）\n" +
			"第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328\n" +
			"第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405\n"

		if got := out.String(); got != want {
			t.Errorf("Run() の出力 =\n%s\nwant\n%s", got, want)
		}
	})
}
