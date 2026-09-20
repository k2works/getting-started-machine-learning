package chapter07_test

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

// requireData は学習データが無ければテストを飛ばす。
func requireData(t *testing.T) string {
	t.Helper()

	csvFile := filepath.Join(dataset.Current(), "cinema.csv")
	if _, err := os.Stat(csvFile); err != nil {
		t.Skip("学習データ cinema.csv が配置されていない（gulp data:setup）")
	}

	return csvFile
}

func TestCinemaData(t *testing.T) {
	t.Parallel()

	t.Run("外れ値を 1 件除いて 99 件になる", func(t *testing.T) {
		t.Parallel()

		table, err := chapter02.LoadTable(requireData(t))
		if err != nil {
			t.Fatalf("LoadTable() でエラー: %v", err)
		}

		cleaned, err := chapter07.RemoveOutliers(table)
		if err != nil {
			t.Fatalf("RemoveOutliers() でエラー: %v", err)
		}

		if len(table.Rows) != 100 || len(cleaned.Rows) != 99 {
			t.Errorf("件数 = %d と %d, want 100 と 99", len(table.Rows), len(cleaned.Rows))
		}
	})

	t.Run("テストデータの決定係数が 0.6591 になる", func(t *testing.T) {
		t.Parallel()

		split, err := chapter07.PrepareCinema(requireData(t), 0.2, 0)
		if err != nil {
			t.Fatalf("PrepareCinema() でエラー: %v", err)
		}

		model, err := chapter07.Fit(split.XTrain, split.TTrain)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		predictions, err := model.PredictAll(split.XTest)
		if err != nil {
			t.Fatalf("PredictAll() でエラー: %v", err)
		}

		got, err := chapter07.R2Score(split.TTest, predictions)
		if err != nil {
			t.Fatalf("R2Score() でエラー: %v", err)
		}

		if math.Abs(got-0.6591) > 5e-5 {
			t.Errorf("決定係数 = %v, want 0.6591", got)
		}
	})

	t.Run("実行すると係数と評価指標を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t)

		var out bytes.Buffer
		if err := chapter07.Run(&out); err != nil {
			t.Fatalf("Run() でエラー: %v", err)
		}

		// Go の math/rand は分け方がほかの言語版と違うので、係数も評価指標も一致しない
		want := "データ件数: 100\n" +
			"外れ値を除いた件数: 99\n" +
			"訓練データ: 79 件, テストデータ: 20 件\n" +
			"切片: 6392.98\n" +
			"係数: SNS1=1.2610, SNS2=0.5934, actor=0.2577, original=201.1699\n" +
			"テストデータの評価: R2=0.6591, MAE=342.40, RMSE=409.68\n" +
			"単回帰（SNS2 のみ・gonum）: 切片=8979.41, 係数=1.3074\n"
		if out.String() != want {
			t.Errorf("出力 = %q, want %q", out.String(), want)
		}
	})
}
