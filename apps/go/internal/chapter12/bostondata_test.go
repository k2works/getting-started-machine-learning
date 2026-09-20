package chapter12_test

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter12"
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

// bostonDataset は外れ値を除いて 3 つに分けたデータを返す。
func bostonDataset(t *testing.T) chapter12.BostonDataset {
	t.Helper()

	data, err := chapter12.PrepareBoston(requireData(t), 0.3, 0.3, 0)
	if err != nil {
		t.Fatalf("PrepareBoston() でエラー: %v", err)
	}

	return data
}

func TestBostonData(t *testing.T) {
	t.Parallel()

	t.Run("3 列の特徴量が 9 列になり、訓練 47 件・検証 21 件・テスト 30 件に分かれる", func(t *testing.T) {
		t.Parallel()

		data := bostonDataset(t)

		if len(data.Columns) != 9 {
			t.Errorf("特徴量の列数 = %d, want 9", len(data.Columns))
		}

		counts := []int{len(data.XTrain), len(data.XValidation), len(data.XTest)}
		if want := []int{47, 21, 30}; counts[0] != want[0] || counts[1] != want[1] || counts[2] != want[2] {
			t.Errorf("件数 = %v, want %v", counts, want)
		}
	})

	t.Run("正則化なしは訓練データによく合うが、テストデータでは落ちる", func(t *testing.T) {
		t.Parallel()

		data := bostonDataset(t)

		experiments, err := chapter12.RunRidgeExperiments(data, []float64{0})
		if err != nil {
			t.Fatalf("RunRidgeExperiments() でエラー: %v", err)
		}

		plain := experiments[0]
		if plain.TrainScore <= plain.ValidationScore {
			t.Errorf("訓練 R² %v が検証 R² %v より高くありません", plain.TrainScore, plain.ValidationScore)
		}
	})

	t.Run("alpha を上げると係数の絶対値の合計が減る", func(t *testing.T) {
		t.Parallel()

		data := bostonDataset(t)

		experiments, err := chapter12.RunRidgeExperiments(data, chapter12.Alphas)
		if err != nil {
			t.Fatalf("RunRidgeExperiments() でエラー: %v", err)
		}

		for i := 1; i < len(experiments); i++ {
			if experiments[i].CoefficientAbsSum >= experiments[i-1].CoefficientAbsSum {
				t.Errorf(
					"alpha=%v の合計 %v が alpha=%v の %v より小さくありません",
					experiments[i].Alpha, experiments[i].CoefficientAbsSum,
					experiments[i-1].Alpha, experiments[i-1].CoefficientAbsSum,
				)
			}
		}
	})

	t.Run("ラッソ回帰は 3 つの特徴量の係数を 0 にする", func(t *testing.T) {
		t.Parallel()

		data := bostonDataset(t)

		model, err := chapter12.FitLasso(data.XTrain, data.TTrain, 50)
		if err != nil {
			t.Fatalf("FitLasso() でエラー: %v", err)
		}

		if zero := chapter12.ZeroCoefficientNames(model); len(zero) != 3 {
			t.Errorf("0 になった係数 = %v, want 3 個", zero)
		}
	})

	t.Run("実行すると実験の表と選んだ alpha を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t)

		var out bytes.Buffer
		if err := chapter12.Run(&out); err != nil {
			t.Fatalf("Run() でエラー: %v", err)
		}

		want := "データ件数: 98（外れ値 2 件を除外）\n" +
			"訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件\n" +
			"特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2\n" +
			"alpha\t訓練 R²\t検証 R²\t係数の絶対値の合計\n" +
			"  0.0\t0.8420\t0.7540\t14.781\n" +
			"  0.1\t0.8420\t0.7542\t14.707\n" +
			"  1.0\t0.8416\t0.7550\t14.154\n" +
			" 10.0\t0.8294\t0.7514\t11.836\n" +
			"100.0\t0.6567\t0.5812\t6.623\n" +
			"検証データで選んだ alpha: 1\n" +
			"テストデータの決定係数: 線形回帰 0.5520, リッジ回帰 0.5913, ラッソ回帰 0.6163\n" +
			"ラッソ回帰（alpha=50）で係数が 0 になった特徴量: RM PTRATIO, PTRATIO^2, LSTAT^2\n"

		if got := out.String(); got != want {
			t.Errorf("Run() の出力 =\n%s\nwant\n%s", got, want)
		}
	})
}
