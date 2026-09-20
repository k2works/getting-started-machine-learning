package chapter09_test

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter09"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

// requireData は学習データが無ければテストを飛ばし、あればそのディレクトリを返す。
func requireData(t *testing.T) string {
	t.Helper()

	dataDir := dataset.Current()
	for _, name := range []string{"Boston.csv", "bike.tsv", "weather.csv"} {
		if _, err := os.Stat(filepath.Join(dataDir, name)); err != nil {
			t.Skip("学習データが配置されていない（gulp data:setup）")
		}
	}

	return dataDir
}

// bostonSplit はボストンの住宅価格を前処理して返す。
func bostonSplit(t *testing.T) chapter02.TrainTestSplit[chapter02.Features, float64] {
	t.Helper()

	split, err := chapter09.PrepareBoston(filepath.Join(requireData(t), "Boston.csv"), 0.3, 0)
	if err != nil {
		t.Fatalf("PrepareBoston() でエラー: %v", err)
	}

	return split
}

func TestBostonData(t *testing.T) {
	t.Parallel()

	t.Run("CRIME をダミー変数にすると特徴量が 14 列になる", func(t *testing.T) {
		t.Parallel()

		split := bostonSplit(t)

		if got, want := len(split.XTrain), 70; got != want {
			t.Errorf("訓練データ = %d 件, want %d 件", got, want)
		}

		if got, want := len(split.XTrain[0].Columns), 14; got != want {
			t.Errorf("特徴量 = %d 列, want %d 列", got, want)
		}
	})

	t.Run("2 乗の項を足すとテストデータの決定係数が上がる", func(t *testing.T) {
		t.Parallel()

		split := bostonSplit(t)

		base, err := chapter09.ScoreFeatureSet(split, chapter09.Columns, chapter09.Columns)
		if err != nil {
			t.Fatalf("ScoreFeatureSet() でエラー: %v", err)
		}

		squared, err := chapter09.ScoreFeatureSet(
			split, chapter09.Columns, append(append([]string{}, chapter09.Columns...), chapter09.Squares...))
		if err != nil {
			t.Fatalf("ScoreFeatureSet() でエラー: %v", err)
		}

		if math.Abs(base.Test-0.6113) > 5e-5 {
			t.Errorf("元の特徴量のテストデータの決定係数 = %.4f, want 0.6113", base.Test)
		}

		if math.Abs(squared.Test-0.8142) > 5e-5 {
			t.Errorf("2 乗の項を足したテストデータの決定係数 = %.4f, want 0.8142", squared.Test)
		}
	})

	t.Run("実行すると決定係数と天気ごとの平均利用者数を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t)

		var out bytes.Buffer
		if err := chapter09.Run(&out); err != nil {
			t.Fatalf("Run() でエラー: %v", err)
		}

		want := "訓練データ: 70 件, テストデータ: 30 件\n" +
			"特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low\n" +
			"標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00\n" +
			"  gonum の stat.StdDev（標本標準偏差）: 1.0072\n" +
			"決定係数:\n" +
			"  元の特徴量（3 列）: 訓練 0.6214, テスト 0.6113\n" +
			"  2 乗の項を追加（6 列）: 訓練 0.7836, テスト 0.8142\n" +
			"  交互作用の項も追加（9 列）: 訓練 0.8178, テスト 0.6415\n" +
			"訓練データの PRICE の外れ値: 5 件\n" +
			"  外れ値を除いて 2 乗の項を追加: 訓練 0.7494, テスト 0.8380\n" +
			"天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3\n"

		if got := out.String(); got != want {
			t.Errorf("Run() の出力 =\n%s\nwant\n%s", got, want)
		}
	})

	t.Run("Shift_JIS の天気を UTF-8 として読むと列名が合わない", func(t *testing.T) {
		t.Parallel()

		dataDir := requireData(t)

		weather, err := chapter09.LoadDelimited(filepath.Join(dataDir, "weather.csv"), chapter09.UTF8, ",")
		if err != nil {
			t.Fatalf("LoadDelimited() でエラー: %v", err)
		}

		// 文字コードが違っても読めてしまうが、日本語が化けるので天気の名前が一致しなくなる
		got, err := weather.Rows[0].Text("weather")
		if err != nil {
			t.Fatalf("Text() でエラー: %v", err)
		}

		if got == "晴れ" {
			t.Error("Shift_JIS のファイルを UTF-8 として読んだのに化けませんでした")
		}
	})
}
