package chapter09_test

import (
	"math"
	"reflect"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter09"
)

// features はテスト用の特徴量を作る。
func features(t *testing.T, columns []string, values ...float64) chapter02.Features {
	t.Helper()

	f, err := chapter02.NewFeatures(columns, values)
	if err != nil {
		t.Fatalf("NewFeatures() でエラー: %v", err)
	}

	return f
}

// row はテスト用の行を作る。
func row(cells map[string]string) chapter02.Row {
	return chapter02.NewRow(cells)
}

func TestCategories(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		values []string
		want   []string
	}{
		{name: "辞書順に並べて先頭を落とす", values: []string{"low", "high", "very_low"}, want: []string{"low", "very_low"}},
		{name: "重複はまとめる", values: []string{"low", "low", "high", "high"}, want: []string{"low"}},
		{name: "空欄は数えない", values: []string{"high", "", "low"}, want: []string{"low"}},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if got := chapter09.Categories(test.values); !reflect.DeepEqual(got, test.want) {
				t.Errorf("Categories() = %v, want %v", got, test.want)
			}
		})
	}
}

func TestEncode(t *testing.T) {
	t.Parallel()

	table := chapter02.Table{
		Columns: []string{"CRIME", "RM", "PRICE"},
		Rows: []chapter02.Row{
			row(map[string]string{"CRIME": "high", "RM": "6.0", "PRICE": "20"}),
			row(map[string]string{"CRIME": "low", "RM": "7.0", "PRICE": "30"}),
		},
	}

	t.Run("元の列を消してカテゴリごとの列を末尾に足す", func(t *testing.T) {
		t.Parallel()

		encoded, err := chapter09.Encode(table, "CRIME", []string{"low"})
		if err != nil {
			t.Fatalf("Encode() でエラー: %v", err)
		}

		want := []string{"RM", "PRICE", "CRIME_low"}
		if !reflect.DeepEqual(encoded.Columns, want) {
			t.Errorf("Columns = %v, want %v", encoded.Columns, want)
		}
	})

	t.Run("カテゴリと一致する行だけ 1 になる", func(t *testing.T) {
		t.Parallel()

		encoded, err := chapter09.Encode(table, "CRIME", []string{"low"})
		if err != nil {
			t.Fatalf("Encode() でエラー: %v", err)
		}

		for i, want := range []string{"0", "1"} {
			got, err := encoded.Rows[i].Text("CRIME_low")
			if err != nil {
				t.Fatalf("Text() でエラー: %v", err)
			}

			if got != want {
				t.Errorf("%d 行目の CRIME_low = %q, want %q", i, got, want)
			}
		}
	})

	t.Run("列が無ければエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter09.Encode(table, "NOTHING", []string{"low"}); err == nil {
			t.Error("Encode() がエラーを返しませんでした")
		}
	})
}

func TestStandardizer(t *testing.T) {
	t.Parallel()

	columns := []string{"A", "B"}
	x := []chapter02.Features{
		features(t, columns, 1, 5),
		features(t, columns, 3, 5),
		features(t, columns, 5, 5),
	}

	t.Run("平均と母標準偏差を求める", func(t *testing.T) {
		t.Parallel()

		standardizer, err := chapter09.Fit(x)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if got, want := standardizer.Means["A"], 3.0; math.Abs(got-want) > 1e-12 {
			t.Errorf("平均 = %v, want %v", got, want)
		}

		// 母標準偏差は sqrt(8/3) = 1.632...、標本標準偏差なら 2 になる
		if got, want := standardizer.Stds["A"], math.Sqrt(8.0/3.0); math.Abs(got-want) > 1e-12 {
			t.Errorf("標準偏差 = %v, want %v", got, want)
		}
	})

	t.Run("すべて同じ値の列は標準偏差を 1 にする", func(t *testing.T) {
		t.Parallel()

		standardizer, err := chapter09.Fit(x)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if got, want := standardizer.Stds["B"], 1.0; got != want {
			t.Errorf("標準偏差 = %v, want %v", got, want)
		}
	})

	t.Run("変換すると平均 0・標準偏差 1 になる", func(t *testing.T) {
		t.Parallel()

		standardizer, err := chapter09.Fit(x)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		transformed, err := standardizer.Transform(x)
		if err != nil {
			t.Fatalf("Transform() でエラー: %v", err)
		}

		checked, err := chapter09.Fit(transformed)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if got := checked.Means["A"]; math.Abs(got) > 1e-12 {
			t.Errorf("変換後の平均 = %v, want 0", got)
		}

		if got := checked.Stds["A"]; math.Abs(got-1) > 1e-12 {
			t.Errorf("変換後の標準偏差 = %v, want 1", got)
		}
	})

	t.Run("特徴量が 1 件も無ければエラーを返す", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter09.Fit(nil); err == nil {
			t.Error("Fit() がエラーを返しませんでした")
		}
	})
}

func TestGonumStats(t *testing.T) {
	t.Parallel()

	values := []float64{1, 3, 5}

	t.Run("gonum の stat.Mean は自作の平均と一致する", func(t *testing.T) {
		t.Parallel()

		if got, want := chapter09.GonumMean(values), 3.0; math.Abs(got-want) > 1e-12 {
			t.Errorf("GonumMean() = %v, want %v", got, want)
		}
	})

	t.Run("gonum の stat.StdDev は標本標準偏差なので自作の母標準偏差と食い違う", func(t *testing.T) {
		t.Parallel()

		sample := chapter09.GonumStdDev(values)
		if want := 2.0; math.Abs(sample-want) > 1e-12 {
			t.Errorf("GonumStdDev() = %v, want %v", sample, want)
		}

		if population := chapter09.PopulationStdDev(values); math.Abs(sample-population) < 1e-12 {
			t.Errorf("標本標準偏差 %v と母標準偏差 %v が一致してしまいました", sample, population)
		}
	})

	t.Run("件数で割り直すと母標準偏差と一致する", func(t *testing.T) {
		t.Parallel()

		n := float64(len(values))
		converted := chapter09.GonumStdDev(values) * math.Sqrt((n-1)/n)

		if got, want := converted, chapter09.PopulationStdDev(values); math.Abs(got-want) > 1e-12 {
			t.Errorf("換算した標準偏差 = %v, want %v", got, want)
		}
	})
}

func TestPolynomialFeatures(t *testing.T) {
	t.Parallel()

	t.Run("重複を許して 2 つ選ぶ組を作る", func(t *testing.T) {
		t.Parallel()

		got := chapter09.PairsWithReplacement([]string{"A", "B"})
		want := []chapter09.Pair{{Left: "A", Right: "A"}, {Left: "A", Right: "B"}, {Left: "B", Right: "B"}}

		if !reflect.DeepEqual(got, want) {
			t.Errorf("PairsWithReplacement() = %v, want %v", got, want)
		}
	})

	t.Run("2 乗の項と交互作用の項で名前を変える", func(t *testing.T) {
		t.Parallel()

		if got, want := (chapter09.Pair{Left: "A", Right: "A"}).Name(), "A^2"; got != want {
			t.Errorf("Name() = %q, want %q", got, want)
		}

		if got, want := (chapter09.Pair{Left: "A", Right: "B"}).Name(), "A B"; got != want {
			t.Errorf("Name() = %q, want %q", got, want)
		}
	})

	t.Run("元の列の後ろに 2 乗と交互作用の項を足す", func(t *testing.T) {
		t.Parallel()

		columns := []string{"A", "B"}
		x := []chapter02.Features{features(t, columns, 2, 3)}

		expanded, err := chapter09.Expand(x, columns)
		if err != nil {
			t.Fatalf("Expand() でエラー: %v", err)
		}

		wantColumns := []string{"A", "B", "A^2", "A B", "B^2"}
		if !reflect.DeepEqual(expanded[0].Columns, wantColumns) {
			t.Errorf("Columns = %v, want %v", expanded[0].Columns, wantColumns)
		}

		wantValues := []float64{2, 3, 4, 6, 9}
		if !reflect.DeepEqual(expanded[0].Values, wantValues) {
			t.Errorf("Values = %v, want %v", expanded[0].Values, wantValues)
		}
	})

	t.Run("指定した列だけをその順に選ぶ", func(t *testing.T) {
		t.Parallel()

		columns := []string{"A", "B", "C"}
		x := []chapter02.Features{features(t, columns, 1, 2, 3)}

		selected, err := chapter09.Select(x, []string{"C", "A"})
		if err != nil {
			t.Fatalf("Select() でエラー: %v", err)
		}

		if want := []float64{3, 1}; !reflect.DeepEqual(selected[0].Values, want) {
			t.Errorf("Values = %v, want %v", selected[0].Values, want)
		}
	})
}

func TestOutliers(t *testing.T) {
	t.Parallel()

	t.Run("分位数は値の間なら線形補間する", func(t *testing.T) {
		t.Parallel()

		values := []float64{1, 2, 3, 4}

		if got, want := chapter09.Quantile(values, 0.25), 1.75; math.Abs(got-want) > 1e-12 {
			t.Errorf("Quantile(0.25) = %v, want %v", got, want)
		}

		if got, want := chapter09.Quantile(values, 0.75), 3.25; math.Abs(got-want) > 1e-12 {
			t.Errorf("Quantile(0.75) = %v, want %v", got, want)
		}
	})

	t.Run("四分位範囲の 1.5 倍より外の値を外れ値とする", func(t *testing.T) {
		t.Parallel()

		values := []float64{10, 11, 12, 13, 14, 100}

		got := chapter09.IQROutliers(values, chapter09.DefaultK)
		want := []bool{false, false, false, false, false, true}

		if !reflect.DeepEqual(got, want) {
			t.Errorf("IQROutliers() = %v, want %v", got, want)
		}
	})

	t.Run("訓練データからだけ外れ値の行を取り除く", func(t *testing.T) {
		t.Parallel()

		columns := []string{"A"}
		split := chapter02.TrainTestSplit[chapter02.Features, float64]{
			XTrain: []chapter02.Features{
				features(t, columns, 1),
				features(t, columns, 2),
				features(t, columns, 3),
				features(t, columns, 4),
				features(t, columns, 5),
				features(t, columns, 6),
			},
			TTrain: []float64{10, 11, 12, 13, 14, 100},
			XTest:  []chapter02.Features{features(t, columns, 7)},
			TTest:  []float64{1000},
		}

		removed := chapter09.RemoveTargetOutliers(split)

		if got, want := len(removed.XTrain), 5; got != want {
			t.Errorf("訓練データ = %d 件, want %d 件", got, want)
		}

		if got, want := len(removed.TTest), 1; got != want {
			t.Errorf("テストデータ = %d 件, want %d 件", got, want)
		}
	})
}

func TestLinearModel(t *testing.T) {
	t.Parallel()

	t.Run("直線に乗るデータの係数と切片を求める", func(t *testing.T) {
		t.Parallel()

		rows := [][]float64{{1}, {2}, {3}}
		target := []float64{3, 5, 7}

		model, err := chapter09.FitLinearModel(rows, target)
		if err != nil {
			t.Fatalf("FitLinearModel() でエラー: %v", err)
		}

		if got, want := model.Intercept, 1.0; math.Abs(got-want) > 1e-9 {
			t.Errorf("切片 = %v, want %v", got, want)
		}

		if got, want := model.Weights[0], 2.0; math.Abs(got-want) > 1e-9 {
			t.Errorf("係数 = %v, want %v", got, want)
		}
	})

	t.Run("直線に乗るデータの決定係数は 1 になる", func(t *testing.T) {
		t.Parallel()

		rows := [][]float64{{1}, {2}, {3}}
		target := []float64{3, 5, 7}

		model, err := chapter09.FitLinearModel(rows, target)
		if err != nil {
			t.Fatalf("FitLinearModel() でエラー: %v", err)
		}

		if got, want := chapter09.RSquared(target, model.Predict(rows)), 1.0; math.Abs(got-want) > 1e-9 {
			t.Errorf("決定係数 = %v, want %v", got, want)
		}
	})

	t.Run("同じ値の列が 2 つあれば解けずにエラーを返す", func(t *testing.T) {
		t.Parallel()

		rows := [][]float64{{1, 1}, {2, 2}, {3, 3}}

		if _, err := chapter09.FitLinearModel(rows, []float64{3, 5, 7}); err == nil {
			t.Error("FitLinearModel() がエラーを返しませんでした")
		}
	})
}

func TestMeanCountByWeather(t *testing.T) {
	t.Parallel()

	t.Run("天気の表に無い ID の行は残さない", func(t *testing.T) {
		t.Parallel()

		bike := chapter02.Table{
			Columns: []string{"weather_id", "cnt"},
			Rows: []chapter02.Row{
				row(map[string]string{"weather_id": "1", "cnt": "100"}),
				row(map[string]string{"weather_id": "2", "cnt": "50"}),
				row(map[string]string{"weather_id": "9", "cnt": "1"}),
			},
		}
		weather := chapter02.Table{
			Columns: []string{"weather_id", "weather"},
			Rows: []chapter02.Row{
				row(map[string]string{"weather_id": "1", "weather": "晴れ"}),
				row(map[string]string{"weather_id": "2", "weather": "曇り"}),
			},
		}

		joined, err := chapter09.JoinWeather(bike, weather)
		if err != nil {
			t.Fatalf("JoinWeather() でエラー: %v", err)
		}

		if got, want := len(joined.Rows), 2; got != want {
			t.Errorf("結合した行 = %d 件, want %d 件", got, want)
		}

		means, err := chapter09.MeanCountByWeather(joined)
		if err != nil {
			t.Fatalf("MeanCountByWeather() でエラー: %v", err)
		}

		if got, want := means[0].Weather, "晴れ"; got != want {
			t.Errorf("先頭の天気 = %q, want %q", got, want)
		}

		if got, want := means[0].Mean, 100.0; math.Abs(got-want) > 1e-12 {
			t.Errorf("平均利用者数 = %v, want %v", got, want)
		}
	})
}
