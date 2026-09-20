package chapter12_test

import (
	"math"
	"slices"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter12"
)

// scalerSample は架空の 2 列のデータを作る。平均と標準偏差が分かりやすい値にしてある。
func scalerSample(t *testing.T) []chapter02.Features {
	t.Helper()

	rows := [][]float64{{1, 10}, {3, 20}, {5, 30}}
	x := make([]chapter02.Features, len(rows))

	for i, row := range rows {
		features, err := chapter02.NewFeatures([]string{"p", "q"}, row)
		if err != nil {
			t.Fatalf("NewFeatures() でエラー: %v", err)
		}

		x[i] = features
	}

	return x
}

func TestPolynomialScaler(t *testing.T) {
	t.Parallel()

	t.Run("列の名前は元の列・2 乗の項・交互作用の項の順になる", func(t *testing.T) {
		t.Parallel()

		scaler, err := chapter12.FitScaler(scalerSample(t))
		if err != nil {
			t.Fatalf("FitScaler() でエラー: %v", err)
		}

		want := []string{"p", "q", "p^2", "p q", "q^2"}
		if got := scaler.FeatureNames(); !slices.Equal(got, want) {
			t.Errorf("FeatureNames() = %v, want %v", got, want)
		}
	})

	t.Run("標準化した値の平均は 0 になる", func(t *testing.T) {
		t.Parallel()

		x := scalerSample(t)

		scaler, err := chapter12.FitScaler(x)
		if err != nil {
			t.Fatalf("FitScaler() でエラー: %v", err)
		}

		transformed, err := scaler.Transform(x)
		if err != nil {
			t.Fatalf("Transform() でエラー: %v", err)
		}

		for _, column := range []string{"p", "q"} {
			sum := 0.0

			for _, features := range transformed {
				value, err := features.Value(column)
				if err != nil {
					t.Fatalf("Value() でエラー: %v", err)
				}

				sum += value
			}

			if math.Abs(sum) > 1e-12 {
				t.Errorf("%s の合計 = %v, want 0", column, sum)
			}
		}
	})

	t.Run("2 乗の項と交互作用の項は標準化した値から作る", func(t *testing.T) {
		t.Parallel()

		x := scalerSample(t)

		scaler, err := chapter12.FitScaler(x)
		if err != nil {
			t.Fatalf("FitScaler() でエラー: %v", err)
		}

		transformed, err := scaler.Transform(x)
		if err != nil {
			t.Fatalf("Transform() でエラー: %v", err)
		}

		for i, features := range transformed {
			p, err := features.Value("p")
			if err != nil {
				t.Fatalf("Value() でエラー: %v", err)
			}

			q, err := features.Value("q")
			if err != nil {
				t.Fatalf("Value() でエラー: %v", err)
			}

			square, err := features.Value("p^2")
			if err != nil {
				t.Fatalf("Value() でエラー: %v", err)
			}

			cross, err := features.Value("p q")
			if err != nil {
				t.Fatalf("Value() でエラー: %v", err)
			}

			if math.Abs(square-p*p) > 1e-12 {
				t.Errorf("%d 件目の p^2 = %v, want %v", i, square, p*p)
			}

			if math.Abs(cross-p*q) > 1e-12 {
				t.Errorf("%d 件目の p q = %v, want %v", i, cross, p*q)
			}
		}
	})

	t.Run("別のデータも訓練データの平均と標準偏差で変換する", func(t *testing.T) {
		t.Parallel()

		x := scalerSample(t)

		scaler, err := chapter12.FitScaler(x)
		if err != nil {
			t.Fatalf("FitScaler() でエラー: %v", err)
		}

		// 訓練データの平均（p は 3、q は 20）をそのまま渡せば、標準化した値は 0 になる
		mean, err := chapter02.NewFeatures([]string{"p", "q"}, []float64{3, 20})
		if err != nil {
			t.Fatalf("NewFeatures() でエラー: %v", err)
		}

		transformed, err := scaler.Transform([]chapter02.Features{mean})
		if err != nil {
			t.Fatalf("Transform() でエラー: %v", err)
		}

		for i, value := range transformed[0].Values {
			if value != 0 {
				t.Errorf("%s = %v, want 0", transformed[0].Columns[i], value)
			}
		}
	})

	t.Run("特徴量が 1 件も無ければエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter12.FitScaler(nil); err == nil {
			t.Error("FitScaler() がエラーを返しませんでした")
		}
	})
}

func TestRemoveOutliers(t *testing.T) {
	t.Parallel()

	// 1 件だけ大きく離れた値を混ぜた架空の表
	rows := make([]chapter02.Row, 0, 10)
	for _, value := range []string{"10", "11", "12", "10", "11", "12", "10", "11", "12", "100"} {
		rows = append(rows, chapter02.NewRow(map[string]string{"v": value}))
	}

	table := chapter02.Table{Columns: []string{"v"}, Rows: rows}

	t.Run("標準得点が大きい行を除く", func(t *testing.T) {
		t.Parallel()

		cleaned, err := chapter12.RemoveOutliers(table, []string{"v"}, 2)
		if err != nil {
			t.Fatalf("RemoveOutliers() でエラー: %v", err)
		}

		if len(cleaned.Rows) != 9 {
			t.Errorf("残った行数 = %d, want 9", len(cleaned.Rows))
		}
	})

	t.Run("しきい値を上げれば何も除かない", func(t *testing.T) {
		t.Parallel()

		cleaned, err := chapter12.RemoveOutliers(table, []string{"v"}, 10)
		if err != nil {
			t.Fatalf("RemoveOutliers() でエラー: %v", err)
		}

		if len(cleaned.Rows) != 10 {
			t.Errorf("残った行数 = %d, want 10", len(cleaned.Rows))
		}
	})

	t.Run("元の表は変わらない", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter12.RemoveOutliers(table, []string{"v"}, 2); err != nil {
			t.Fatalf("RemoveOutliers() でエラー: %v", err)
		}

		if len(table.Rows) != 10 {
			t.Errorf("元の表の行数 = %d, want 10", len(table.Rows))
		}
	})

	t.Run("空欄があればエラーを返す", func(t *testing.T) {
		t.Parallel()

		empty := chapter02.Table{
			Columns: []string{"v"},
			Rows:    []chapter02.Row{chapter02.NewRow(map[string]string{"v": ""})},
		}

		if _, err := chapter12.RemoveOutliers(empty, []string{"v"}, 2); err == nil {
			t.Error("RemoveOutliers() がエラーを返しませんでした")
		}
	})
}

func TestBestExperiment(t *testing.T) {
	t.Parallel()

	t.Run("検証データの決定係数が最も高い実験を選ぶ", func(t *testing.T) {
		t.Parallel()

		experiments := []chapter12.Experiment{
			{Alpha: 0, ValidationScore: 0.70},
			{Alpha: 1, ValidationScore: 0.75},
			{Alpha: 10, ValidationScore: 0.72},
		}

		best, err := chapter12.BestExperiment(experiments)
		if err != nil {
			t.Fatalf("BestExperiment() でエラー: %v", err)
		}

		if best.Alpha != 1 {
			t.Errorf("選んだ alpha = %v, want 1", best.Alpha)
		}
	})

	t.Run("同じ決定係数なら先の実験を選ぶ", func(t *testing.T) {
		t.Parallel()

		experiments := []chapter12.Experiment{
			{Alpha: 1, ValidationScore: 0.75},
			{Alpha: 10, ValidationScore: 0.75},
		}

		best, err := chapter12.BestExperiment(experiments)
		if err != nil {
			t.Fatalf("BestExperiment() でエラー: %v", err)
		}

		if best.Alpha != 1 {
			t.Errorf("選んだ alpha = %v, want 1", best.Alpha)
		}
	})

	t.Run("訓練データの決定係数では選ばない", func(t *testing.T) {
		t.Parallel()

		experiments := []chapter12.Experiment{
			{Alpha: 0, TrainScore: 0.99, ValidationScore: 0.60},
			{Alpha: 10, TrainScore: 0.80, ValidationScore: 0.70},
		}

		best, err := chapter12.BestExperiment(experiments)
		if err != nil {
			t.Fatalf("BestExperiment() でエラー: %v", err)
		}

		if best.Alpha != 10 {
			t.Errorf("選んだ alpha = %v, want 10", best.Alpha)
		}
	})

	t.Run("実験が無ければエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter12.BestExperiment(nil); err == nil {
			t.Error("BestExperiment() がエラーを返しませんでした")
		}
	})
}
