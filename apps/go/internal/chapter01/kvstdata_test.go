package chapter01_test

import (
	"bytes"
	"math"
	"os"
	"path/filepath"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter01"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

// requireData は学習データが無ければテストを飛ばす。
func requireData(t *testing.T) string {
	t.Helper()

	csvFile := filepath.Join(dataset.Current(), "KvsT.csv")
	if _, err := os.Stat(csvFile); err != nil {
		t.Skip("学習データ KvsT.csv が配置されていない（gulp data:setup）")
	}

	return csvFile
}

func TestKvsTData(t *testing.T) {
	t.Parallel()

	t.Run("実データから 19 人分を読み込む", func(t *testing.T) {
		t.Parallel()

		people, err := chapter01.LoadPeople(requireData(t))
		if err != nil {
			t.Fatalf("LoadPeople() でエラー: %v", err)
		}

		if want := 19; len(people) != want {
			t.Errorf("件数 = %d, want %d", len(people), want)
		}
	})

	t.Run("ルールによる判定の正解率を実データで計算する", func(t *testing.T) {
		t.Parallel()

		people, err := chapter01.LoadPeople(requireData(t))
		if err != nil {
			t.Fatalf("LoadPeople() でエラー: %v", err)
		}

		features, labels := chapter01.SplitFeaturesAndLabels(people)

		predictions := make([]string, len(features))
		for i, feature := range features {
			predictions[i] = chapter01.PredictByRule(feature)
		}

		got, err := chapter01.Accuracy(predictions, labels)
		if err != nil {
			t.Fatalf("Accuracy() でエラー: %v", err)
		}

		if want := 14.0 / 19; math.Abs(got-want) > 1e-12 {
			t.Errorf("正解率 = %v, want %v", got, want)
		}
	})

	t.Run("実行するとデータ件数と正解率を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t)

		var out bytes.Buffer
		if err := chapter01.Run(&out); err != nil {
			t.Fatalf("Run() でエラー: %v", err)
		}

		want := "データ件数: 19\nルールによる判定の正解率: 0.7368\n"
		if out.String() != want {
			t.Errorf("出力 = %q, want %q", out.String(), want)
		}
	})
}
