package chapter02_test

import (
	"math"
	"reflect"
	"slices"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

func sample(sepalLength, sepalWidth string) chapter02.Row {
	return chapter02.NewRow(map[string]string{"がく片長さ": sepalLength, "がく片幅": sepalWidth})
}

func features(t *testing.T, columns []string, values []float64) chapter02.Features {
	t.Helper()

	got, err := chapter02.NewFeatures(columns, values)
	if err != nil {
		t.Fatalf("NewFeatures() でエラー: %v", err)
	}

	return got
}

func TestNewFeatures(t *testing.T) {
	t.Parallel()

	if _, err := chapter02.NewFeatures([]string{"a", "b"}, []float64{0.1}); err == nil {
		t.Error("列名と値の数が違えばエラーになるはず")
	}
}

func TestColumnMeans(t *testing.T) {
	t.Parallel()

	rows := []chapter02.Row{sample("0.1", "0.2"), sample("", "0.4"), sample("0.3", "0.9")}

	means, err := chapter02.ColumnMeans(rows, []string{"がく片長さ", "がく片幅"})
	if err != nil {
		t.Fatalf("ColumnMeans() でエラー: %v", err)
	}

	if math.Abs(means["がく片長さ"]-0.2) > 1e-12 || math.Abs(means["がく片幅"]-0.5) > 1e-12 {
		t.Errorf("平均値 = %v, want がく片長さ 0.2・がく片幅 0.5", means)
	}
}

func TestFillMissing(t *testing.T) {
	t.Parallel()

	columns := []string{"がく片長さ", "がく片幅"}
	rows := []chapter02.Row{sample("0.1", ""), sample("", "0.4")}

	filled, err := chapter02.FillMissing(rows, columns, map[string]float64{"がく片長さ": 0.2, "がく片幅": 0.5})
	if err != nil {
		t.Fatalf("FillMissing() でエラー: %v", err)
	}

	want := []chapter02.Features{
		features(t, columns, []float64{0.1, 0.5}),
		features(t, columns, []float64{0.2, 0.4}),
	}
	if !reflect.DeepEqual(filled, want) {
		t.Errorf("FillMissing() = %v, want %v", filled, want)
	}

	if missing, _ := rows[0].IsMissing("がく片幅"); !missing {
		t.Error("元の行は変更しないはず")
	}
}

func TestSplitTrainTest(t *testing.T) {
	t.Parallel()

	numbers := make([]int, 10)
	labels := make([]string, 10)

	for i := range numbers {
		numbers[i] = i
		labels[i] = "label" + string(rune('0'+i))
	}

	t.Run("テストデータの割合どおりの件数に分ける", func(t *testing.T) {
		t.Parallel()

		split, err := chapter02.SplitTrainTest(numbers, labels, 0.3, 0)
		if err != nil {
			t.Fatalf("SplitTrainTest() でエラー: %v", err)
		}

		if len(split.XTrain) != 7 || len(split.XTest) != 3 {
			t.Errorf("件数 = (%d, %d), want (7, 3)", len(split.XTrain), len(split.XTest))
		}

		if len(split.TTrain) != 7 || len(split.TTest) != 3 {
			t.Errorf("正解ラベルの件数 = (%d, %d), want (7, 3)", len(split.TTrain), len(split.TTest))
		}
	})

	t.Run("件数が変わってもテストデータの割合どおりに分ける", func(t *testing.T) {
		t.Parallel()

		twenty := make([]int, 20)
		for i := range twenty {
			twenty[i] = i
		}

		split, err := chapter02.SplitTrainTest(twenty, twenty, 0.25, 0)
		if err != nil {
			t.Fatalf("SplitTrainTest() でエラー: %v", err)
		}

		if len(split.XTrain) != 15 || len(split.XTest) != 5 {
			t.Errorf("件数 = (%d, %d), want (15, 5)", len(split.XTrain), len(split.XTest))
		}
	})

	t.Run("すべての行を重複なく訓練データとテストデータのどちらかに入れる", func(t *testing.T) {
		t.Parallel()

		split, err := chapter02.SplitTrainTest(numbers, labels, 0.3, 0)
		if err != nil {
			t.Fatalf("SplitTrainTest() でエラー: %v", err)
		}

		all := slices.Concat(split.XTrain, split.XTest)
		slices.Sort(all)

		if !reflect.DeepEqual(all, numbers) {
			t.Errorf("すべての行 = %v, want %v", all, numbers)
		}
	})

	t.Run("特徴量と正解ラベルの対応を保ったまま分ける", func(t *testing.T) {
		t.Parallel()

		split, err := chapter02.SplitTrainTest(numbers, labels, 0.3, 0)
		if err != nil {
			t.Fatalf("SplitTrainTest() でエラー: %v", err)
		}

		for i, x := range split.XTest {
			if want := labels[x]; split.TTest[i] != want {
				t.Errorf("テストデータの正解ラベル[%d] = %q, want %q", i, split.TTest[i], want)
			}
		}
	})

	t.Run("同じシードなら同じ分け方になる", func(t *testing.T) {
		t.Parallel()

		first, _ := chapter02.SplitTrainTest(numbers, labels, 0.3, 42)
		second, _ := chapter02.SplitTrainTest(numbers, labels, 0.3, 42)

		if !reflect.DeepEqual(first.TTest, second.TTest) {
			t.Errorf("同じシードで違う分け方: %v と %v", first.TTest, second.TTest)
		}
	})

	t.Run("シードが違えば違う分け方になる", func(t *testing.T) {
		t.Parallel()

		first, _ := chapter02.SplitTrainTest(numbers, labels, 0.3, 0)
		second, _ := chapter02.SplitTrainTest(numbers, labels, 0.3, 1)

		if reflect.DeepEqual(first.TTest, second.TTest) {
			t.Errorf("違うシードで同じ分け方: %v", first.TTest)
		}
	})

	t.Run("数値の正解ラベルも特徴量との対応を保ったまま分ける", func(t *testing.T) {
		t.Parallel()

		numeric := make([]float64, len(numbers))
		for i := range numbers {
			numeric[i] = float64(i) * 0.5
		}

		split, err := chapter02.SplitTrainTest(numbers, numeric, 0.3, 0)
		if err != nil {
			t.Fatalf("SplitTrainTest() でエラー: %v", err)
		}

		for i, x := range split.XTest {
			if want := float64(x) * 0.5; split.TTest[i] != want {
				t.Errorf("テストデータの正解ラベル[%d] = %v, want %v", i, split.TTest[i], want)
			}
		}
	})

	t.Run("件数が違えばエラーになる", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter02.SplitTrainTest(numbers, labels[:5], 0.3, 0); err == nil {
			t.Error("エラーを期待したが nil だった")
		}
	})
}
