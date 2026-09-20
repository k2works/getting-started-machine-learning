package chapter10_test

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter10"
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

	t.Run("ロジスティック回帰はテストデータの 45 件中 42 件を正しく分類する", func(t *testing.T) {
		t.Parallel()

		split := irisSplit(t)

		score, err := chapter10.Evaluate(chapter10.NewLogisticRegression(), split)
		if err != nil {
			t.Fatalf("Evaluate() でエラー: %v", err)
		}

		if want := 42.0 / 45; math.Abs(score.Test-want) > 1e-12 {
			t.Errorf("テストデータの正解率 = %v, want %v", score.Test, want)
		}
	})

	t.Run("ランダムフォレストは訓練データをすべて言い当てる", func(t *testing.T) {
		t.Parallel()

		split := irisSplit(t)

		score, err := chapter10.Evaluate(chapter10.NewRandomForest(100, 2, 0), split)
		if err != nil {
			t.Fatalf("Evaluate() でエラー: %v", err)
		}

		if score.Train != 1.0 {
			t.Errorf("訓練データの正解率 = %v, want 1", score.Train)
		}

		if want := 43.0 / 45; math.Abs(score.Test-want) > 1e-12 {
			t.Errorf("テストデータの正解率 = %v, want %v", score.Test, want)
		}
	})

	t.Run("花弁幅の重要度が最も大きい", func(t *testing.T) {
		t.Parallel()

		split := irisSplit(t)
		forest := chapter10.NewRandomForest(100, 2, 0)

		if err := forest.Fit(split.XTrain, split.TTrain); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		importances, err := chapter10.ForestImportances(forest, split.XTrain, split.TTrain)
		if err != nil {
			t.Fatalf("ForestImportances() でエラー: %v", err)
		}

		best := importances[0]
		for _, importance := range importances {
			if importance.Value > best.Value {
				best = importance
			}
		}

		if best.Feature != "花弁幅" {
			t.Errorf("最も重要な特徴量 = %q, want %q", best.Feature, "花弁幅")
		}
	})

	t.Run("実行するとモデルごとの正解率と重要度を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t)

		var out bytes.Buffer
		if err := chapter10.Run(&out); err != nil {
			t.Fatalf("Run() でエラー: %v", err)
		}

		want := "モデル\t訓練データ\tテストデータ\n" +
			"決定木（深さ 2）\t0.9333\t0.9556\n" +
			"ロジスティック回帰\t0.9333\t0.9333\n" +
			"ランダムフォレスト（100 本）\t1.0000\t0.9556\n" +
			"ランダムフォレスト（100 本・深さ 2）\t0.9238\t0.9556\n" +
			"\nランダムフォレスト（100 本）の特徴量の重要度:\n" +
			"がく片長さ\t0.1994\n" +
			"がく片幅\t0.1627\n" +
			"花弁長さ\t0.1828\n" +
			"花弁幅\t0.4551\n"

		if got := out.String(); got != want {
			t.Errorf("Run() の出力 =\n%s\nwant\n%s", got, want)
		}
	})
}
