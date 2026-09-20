package chapter08_test

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter08"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

// writeFile はテスト用のファイルを書き出す。
func writeFile(t *testing.T, path, content string) error {
	t.Helper()

	return os.WriteFile(path, []byte(content), 0o600)
}

// requireData は学習データが無ければテストを飛ばす。
func requireData(t *testing.T) string {
	t.Helper()

	csvFile := filepath.Join(dataset.Current(), "Survived.csv")
	if _, err := os.Stat(csvFile); err != nil {
		t.Skip("学習データ Survived.csv が配置されていない（gulp data:setup）")
	}

	return csvFile
}

func TestSurvivedData(t *testing.T) {
	t.Parallel()

	t.Run("891 件のうち 342 件が生存している", func(t *testing.T) {
		t.Parallel()

		table, err := chapter02.LoadTable(requireData(t))
		if err != nil {
			t.Fatalf("LoadTable() でエラー: %v", err)
		}

		labels, err := chapter08.SurvivedLabels(table.Rows)
		if err != nil {
			t.Fatalf("SurvivedLabels() でエラー: %v", err)
		}

		survivors := 0

		for _, label := range labels {
			if label == chapter08.Survived {
				survivors++
			}
		}

		if len(table.Rows) != 891 || survivors != 342 {
			t.Errorf("件数 = %d, 生存 = %d, want 891 と 342", len(table.Rows), survivors)
		}
	})

	t.Run("実行すると重みの付け方ごとの評価と保存したモデルの予測を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t)

		var out bytes.Buffer

		modelFile := filepath.Join(t.TempDir(), "model", "survived.gob")
		if err := chapter08.RunWithModelFile(&out, modelFile); err != nil {
			t.Fatalf("RunWithModelFile() でエラー: %v", err)
		}

		// Go の math/rand は分け方がほかの言語版と違うので、正解率も発見した人数も一致しない
		want := "データ件数: 891（生存 342, 死亡 549）\n" +
			"訓練データ: 712 件, テストデータ: 179 件\n" +
			"classWeight=none: 訓練 0.837, テスト 0.860, 生存者 65 人中 50 人を発見\n" +
			"classWeight=balanced: 訓練 0.841, テスト 0.844, 生存者 65 人中 51 人を発見\n" +
			"保存したモデル: survived.gob\n" +
			"架空の乗客の予測: [1 0]\n"
		if out.String() != want {
			t.Errorf("出力 = %q, want %q", out.String(), want)
		}
	})
}
