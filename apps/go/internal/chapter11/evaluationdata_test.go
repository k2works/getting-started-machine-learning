package chapter11_test

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter11"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

// requireData は学習データが無ければテストを飛ばす。
func requireData(t *testing.T, name string) string {
	t.Helper()

	csvFile := filepath.Join(dataset.Current(), name)
	if _, err := os.Stat(csvFile); err != nil {
		t.Skipf("学習データ %s が配置されていない（gulp data:setup）", name)
	}

	return csvFile
}

// survivedDataset は Survived.csv を読み込んで、この章の 3 列の特徴量にする。
func survivedDataset(t *testing.T) chapter11.Dataset[string] {
	t.Helper()

	table, err := chapter02.LoadTable(requireData(t, "Survived.csv"))
	if err != nil {
		t.Fatalf("LoadTable() でエラー: %v", err)
	}

	data, err := chapter11.PrepareSurvived(table)
	if err != nil {
		t.Fatalf("PrepareSurvived() でエラー: %v", err)
	}

	return data
}

func TestEvaluationData(t *testing.T) {
	t.Parallel()

	t.Run("Survived の特徴量は 3 列で、欠損値が残らない", func(t *testing.T) {
		t.Parallel()

		data := survivedDataset(t)

		if len(data.X) != len(data.T) {
			t.Fatalf("特徴量 %d 件, 正解ラベル %d 件", len(data.X), len(data.T))
		}

		for i, features := range data.X {
			if len(features.Values) != 3 {
				t.Fatalf("%d 件目の特徴量の数 = %d, want 3", i, len(features.Values))
			}

			for j, value := range features.Values {
				if math.IsNaN(value) {
					t.Errorf("%d 件目の %s が NaN です", i, features.Columns[j])
				}
			}
		}
	})

	t.Run("ロジスティック回帰の AUC は自作でも gonum でも同じになる", func(t *testing.T) {
		t.Parallel()

		data := survivedDataset(t)
		model := chapter11.NewLogisticModel(chapter11.Survived)

		if err := model.Fit(data.X, data.T); err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		probabilities, err := model.PredictProba(data.X)
		if err != nil {
			t.Fatalf("PredictProba() でエラー: %v", err)
		}

		points, err := chapter11.ROCCurve(probabilities, data.T, chapter11.Survived)
		if err != nil {
			t.Fatalf("ROCCurve() でエラー: %v", err)
		}

		own, err := chapter11.AUC(points)
		if err != nil {
			t.Fatalf("AUC() でエラー: %v", err)
		}

		gonumAUC, err := chapter11.GonumAUC(probabilities, data.T, chapter11.Survived)
		if err != nil {
			t.Fatalf("GonumAUC() でエラー: %v", err)
		}

		if math.Abs(own-gonumAUC) > 1e-12 {
			t.Errorf("自作の AUC = %v, gonum の AUC = %v", own, gonumAUC)
		}

		if own < 0.5 {
			t.Errorf("AUC = %v は当てずっぽうの 0.5 を下回っています", own)
		}
	})

	t.Run("ロジスティック回帰の再現率は決定木より高い", func(t *testing.T) {
		t.Parallel()

		data := survivedDataset(t)
		recall := chapter11.ClassificationMetric(chapter11.Recall, chapter11.Survived)

		tree, err := chapter11.Evaluate(func() (chapter11.Model[string], error) {
			return chapter11.NewLogisticModel(chapter11.Survived), nil
		}, data, []chapter11.NamedMetric[string]{{Name: "再現率", Metric: recall}})
		if err != nil {
			t.Fatalf("Evaluate() でエラー: %v", err)
		}

		if tree[0] < 0.6 {
			t.Errorf("ロジスティック回帰の再現率 = %v, want 0.6 以上", tree[0])
		}
	})

	t.Run("実行すると交差検証の結果と AUC を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t, "Survived.csv")
		_ = requireData(t, "cinema.csv")

		var out bytes.Buffer
		if err := chapter11.Run(&out); err != nil {
			t.Fatalf("Run() でエラー: %v", err)
		}

		want := "Survived（5 分割交差検証の平均）\n" +
			"  決定木（深さ 2）\n" +
			"    正解率: 0.7800\n" +
			"    適合率: 0.7932\n" +
			"    再現率: 0.6284\n" +
			"    F 値: 0.6824\n" +
			"  ロジスティック回帰\n" +
			"    正解率: 0.7901\n" +
			"    適合率: 0.7359\n" +
			"    再現率: 0.7058\n" +
			"    F 値: 0.7197\n" +
			"ROC 曲線（ロジスティック回帰、全件で学習）\n" +
			"  点の数: 290\n" +
			"  AUC（自作）: 0.8479\n" +
			"  AUC（gonum）: 0.8479\n" +
			"cinema（線形回帰、5 分割交差検証の平均）\n" +
			"  RMSE: 391.73\n" +
			"  MAE: 315.23\n" +
			"  MSE: 154925.00\n"

		if got := out.String(); got != want {
			t.Errorf("Run() の出力 =\n%s\nwant\n%s", got, want)
		}
	})
}
