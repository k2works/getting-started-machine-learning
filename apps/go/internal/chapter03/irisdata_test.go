package chapter03_test

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter01"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter03"
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

// irisSplit は第 2 章と同じ前処理をしたアヤメのデータを返す。
func irisSplit(t *testing.T) chapter02.TrainTestSplit[chapter02.Features, string] {
	t.Helper()

	split, err := chapter02.PrepareIris(requireData(t), 0.3, 0)
	if err != nil {
		t.Fatalf("PrepareIris() でエラー: %v", err)
	}

	return split
}

func TestIrisData(t *testing.T) {
	t.Parallel()

	t.Run("深さ 2 の決定木はテストデータの 45 件中 43 件を正しく分類する", func(t *testing.T) {
		t.Parallel()

		split := irisSplit(t)

		model, err := chapter03.WithMaxDepth(2)
		if err != nil {
			t.Fatalf("WithMaxDepth() でエラー: %v", err)
		}

		if err := model.Fit(split.XTrain, split.TTrain); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		predictions, err := model.Predict(split.XTest)
		if err != nil {
			t.Fatalf("Predict() でエラー: %v", err)
		}

		got, err := chapter01.Accuracy(predictions, split.TTest)
		if err != nil {
			t.Fatalf("Accuracy() でエラー: %v", err)
		}

		if math.Abs(got-43.0/45) > 1e-12 {
			t.Errorf("正解率 = %v, want %v", got, 43.0/45)
		}
	})

	t.Run("実行すると深さごとの正解率と深さ 2 の決定木を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t)

		var out bytes.Buffer
		if err := chapter03.Run(&out); err != nil {
			t.Fatalf("Run() でエラー: %v", err)
		}

		// Go の math/rand は分け方がほかの言語版と違うので、正解率も木の境界も一致しない
		want := "深さ\t訓練データ\tテストデータ\n" +
			"1\t0.6857\t0.6222\n" +
			"2\t0.9333\t0.9556\n" +
			"3\t0.9429\t0.9556\n" +
			"4\t0.9524\t0.9778\n" +
			"5\t0.9714\t0.9778\n" +
			"制限なし\t1.0000\t1.0000\n" +
			"\n" +
			"深さ 2 の決定木:\n" +
			"花弁幅 <= 0.2950\n" +
			"  Iris-setosa\n" +
			"花弁幅 > 0.2950\n" +
			"  花弁幅 <= 0.6900\n" +
			"    Iris-versicolor\n" +
			"  花弁幅 > 0.6900\n" +
			"    Iris-virginica\n"
		if out.String() != want {
			t.Errorf("出力 = %q, want %q", out.String(), want)
		}
	})
}
