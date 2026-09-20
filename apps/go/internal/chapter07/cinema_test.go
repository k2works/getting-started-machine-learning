package chapter07_test

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
)

// writeCSV は架空の CSV を一時ディレクトリに書き出し、そのパスを返す。
func writeCSV(t *testing.T, content string) string {
	t.Helper()

	csvFile := filepath.Join(t.TempDir(), "cinema.csv")
	if err := os.WriteFile(csvFile, []byte(content), 0o600); err != nil {
		t.Fatalf("CSV を書けません: %v", err)
	}

	return csvFile
}

func TestRemoveOutliers(t *testing.T) {
	t.Parallel()

	// 2 行目だけが「SNS2 が 1000 を超えるのに興行収入が 8500 未満」の外れ値。
	csvFile := writeCSV(t, "cinema_id,SNS1,SNS2,actor,original,sales\n"+
		"1,100,900,5000,0,9000\n"+
		"2,120,1200,5200,1,8000\n"+
		"3,130,1100,5300,0,9500\n")

	table, err := chapter02.LoadTable(csvFile)
	if err != nil {
		t.Fatalf("CSV を読めません: %v", err)
	}

	cleaned, err := chapter07.RemoveOutliers(table)
	if err != nil {
		t.Fatalf("外れ値を除けません: %v", err)
	}

	if len(cleaned.Rows) != 2 {
		t.Fatalf("件数が違います: %d", len(cleaned.Rows))
	}

	for _, row := range cleaned.Rows {
		id, err := row.Text("cinema_id")
		if err != nil {
			t.Fatalf("列を読めません: %v", err)
		}

		if id == "2" {
			t.Error("外れ値の行が残っています")
		}
	}

	if len(table.Rows) != 3 {
		t.Error("元の表が変わっています")
	}
}

func TestPrepareCinemaFillsMissingWithTrainMean(t *testing.T) {
	t.Parallel()

	// SNS1 が 1 行だけ空欄。訓練データの平均値で補完されるので、欠損値は残らない。
	csvFile := writeCSV(t, "cinema_id,SNS1,SNS2,actor,original,sales\n"+
		"1,100,900,5000,0,9000\n"+
		"2,200,800,5200,1,9100\n"+
		"3,300,700,5300,0,9200\n"+
		"4,,600,5400,1,9300\n"+
		"5,500,500,5500,0,9400\n")

	split, err := chapter07.PrepareCinema(csvFile, 0.2, 0)
	if err != nil {
		t.Fatalf("前処理できません: %v", err)
	}

	if len(split.XTrain) != 4 || len(split.XTest) != 1 {
		t.Fatalf("件数が違います: 訓練 %d, テスト %d", len(split.XTrain), len(split.XTest))
	}

	// 補完に成功した行だけが Features になるので、特徴量の列は必ずこの 4 列になる。
	for _, features := range append(append([]chapter02.Features{}, split.XTrain...), split.XTest...) {
		if !reflect.DeepEqual(features.Columns, chapter07.CinemaFeatures) {
			t.Fatalf("特徴量の列が違います: %v", features.Columns)
		}
	}

	// 補完した SNS1 は 100〜500 の平均値の範囲に入る。0 のまま残っていないことを確かめる。
	for _, features := range append(append([]chapter02.Features{}, split.XTrain...), split.XTest...) {
		value, err := features.Value("SNS1")
		if err != nil {
			t.Fatalf("値を読めません: %v", err)
		}

		if value < 100 || value > 500 {
			t.Errorf("SNS1 が補完されていません: %v", value)
		}
	}
}
