package chapter02_test

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

// requireData は学習データが無ければテストを飛ばす。
func requireData(t *testing.T) string {
	t.Helper()

	csvFile := filepath.Join(dataset.Current(), "iris.csv")
	if _, err := os.Stat(csvFile); err != nil {
		t.Skip("学習データ iris.csv が配置されていない（gulp data:setup）")
	}

	return csvFile
}

func TestIrisData(t *testing.T) {
	t.Parallel()

	t.Run("実データの列ごとの欠損値の数を数える", func(t *testing.T) {
		t.Parallel()

		table, err := chapter02.LoadTable(requireData(t))
		if err != nil {
			t.Fatalf("LoadTable() でエラー: %v", err)
		}

		got, err := table.CountMissing()
		if err != nil {
			t.Fatalf("CountMissing() でエラー: %v", err)
		}

		want := []chapter02.Missing{
			{Column: "がく片長さ", Count: 2},
			{Column: "がく片幅", Count: 1},
			{Column: "花弁長さ", Count: 2},
			{Column: "花弁幅", Count: 2},
			{Column: "種類", Count: 0},
		}
		if !reflect.DeepEqual(got, want) {
			t.Errorf("CountMissing() = %v, want %v", got, want)
		}
	})

	t.Run("実データを 105 件と 45 件に分けて欠損値を補完する", func(t *testing.T) {
		t.Parallel()

		split, err := chapter02.PrepareIris(requireData(t), 0.3, 0)
		if err != nil {
			t.Fatalf("PrepareIris() でエラー: %v", err)
		}

		if len(split.XTrain) != 105 || len(split.XTest) != 45 {
			t.Errorf("件数 = (%d, %d), want (105, 45)", len(split.XTrain), len(split.XTest))
		}
	})

	t.Run("訓練データの平均値は math/rand の分け方で決まる", func(t *testing.T) {
		t.Parallel()

		table, err := chapter02.LoadTable(requireData(t))
		if err != nil {
			t.Fatalf("LoadTable() でエラー: %v", err)
		}

		columns, rows, labels, err := chapter02.SplitFeaturesAndTarget(table, chapter02.Target)
		if err != nil {
			t.Fatalf("SplitFeaturesAndTarget() でエラー: %v", err)
		}

		split, err := chapter02.SplitTrainTest(rows, labels, 0.3, 0)
		if err != nil {
			t.Fatalf("SplitTrainTest() でエラー: %v", err)
		}

		means, err := chapter02.ColumnMeans(split.XTrain, columns)
		if err != nil {
			t.Fatalf("ColumnMeans() でエラー: %v", err)
		}

		// Go の math/rand は Java・Scala の java.util.Random とも .NET の Random とも
		// 乱数列が違うので、分かれる行と平均値はほかの言語版と一致しない
		wants := map[string]float64{
			"がく片長さ": 0.4145192307692307,
			"がく片幅":  0.41721153846153847,
			"花弁長さ":  0.4921153846153846,
			"花弁幅":   0.44086538461538455,
		}
		for column, want := range wants {
			if math.Abs(means[column]-want) > 1e-12 {
				t.Errorf("%s の平均値 = %v, want %v", column, means[column], want)
			}
		}
	})

	t.Run("実行すると前処理の結果を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t)

		var out bytes.Buffer
		if err := chapter02.Run(&out); err != nil {
			t.Fatalf("Run() でエラー: %v", err)
		}

		want := "データ件数: 150\n" +
			"欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0\n" +
			"訓練データ: 105 件, テストデータ: 45 件\n" +
			"特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅\n"
		if out.String() != want {
			t.Errorf("出力 = %q, want %q", out.String(), want)
		}
	})
}
