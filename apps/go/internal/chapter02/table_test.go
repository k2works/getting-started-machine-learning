package chapter02_test

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

const header = "\uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類\n"

func writeCSV(t *testing.T, rows string) string {
	t.Helper()

	path := filepath.Join(t.TempDir(), "iris.csv")
	if err := os.WriteFile(path, []byte(header+rows), 0o600); err != nil {
		t.Fatalf("CSV を書けません: %v", err)
	}

	return path
}

func loadTable(t *testing.T, rows string) chapter02.Table {
	t.Helper()

	table, err := chapter02.LoadTable(writeCSV(t, rows))
	if err != nil {
		t.Fatalf("LoadTable() でエラー: %v", err)
	}

	return table
}

func TestLoadTable(t *testing.T) {
	t.Parallel()

	t.Run("CSV を読み込むと列名の並びを保ち、BOM は残らない", func(t *testing.T) {
		t.Parallel()

		table := loadTable(t, "0.1,0.2,0.3,0.4,Iris-setosa\n")

		want := []string{"がく片長さ", "がく片幅", "花弁長さ", "花弁幅", "種類"}
		if !reflect.DeepEqual(table.Columns, want) {
			t.Errorf("列名 = %v, want %v", table.Columns, want)
		}
	})

	t.Run("空欄は欠損値として読み込む", func(t *testing.T) {
		t.Parallel()

		row := loadTable(t, "0.1,,0.3,0.4,Iris-setosa\n").Rows[0]

		if _, ok, err := row.Number("がく片幅"); err != nil || ok {
			t.Errorf("がく片幅 = (ok=%v, err=%v), want ok=false", ok, err)
		}

		value, ok, err := row.Number("がく片長さ")
		if err != nil || !ok || value != 0.1 {
			t.Errorf("がく片長さ = (%v, %v, %v), want (0.1, true, nil)", value, ok, err)
		}
	})

	t.Run("行の最後の列が空欄でも欠損値として読み込む", func(t *testing.T) {
		t.Parallel()

		row := loadTable(t, "0.1,0.2,0.3,,\n").Rows[0]

		if _, ok, _ := row.Number("花弁幅"); ok {
			t.Error("花弁幅 は欠損値のはず")
		}

		if text, err := row.Text("種類"); err != nil || text != "" {
			t.Errorf("種類 = (%q, %v), want (\"\", nil)", text, err)
		}
	})

	t.Run("無い列を読み出すと列名を示すエラーになる", func(t *testing.T) {
		t.Parallel()

		row := chapter02.NewRow(map[string]string{"がく片長さ": "0.1"})

		_, _, err := row.Number("花弁幅")
		if err == nil {
			t.Fatal("エラーを期待したが nil だった")
		}

		if want := "花弁幅"; !contains(err.Error(), want) {
			t.Errorf("エラー = %q, want containing %q", err.Error(), want)
		}
	})

	t.Run("Row は受け取った map を写して持ち、元の map の変更の影響を受けない", func(t *testing.T) {
		t.Parallel()

		cells := map[string]string{"がく片長さ": "0.1"}
		row := chapter02.NewRow(cells)
		cells["がく片長さ"] = "0.9"

		if value, _, _ := row.Number("がく片長さ"); value != 0.1 {
			t.Errorf("がく片長さ = %v, want 0.1", value)
		}
	})

	t.Run("列ごとの欠損値の数を列の順に数える", func(t *testing.T) {
		t.Parallel()

		table := loadTable(t, "0.1,0.2,0.3,0.4,Iris-setosa\n,0.3,0.5,0.6,Iris-setosa\n,,0.7,0.8,Iris-virginica\n")

		got, err := table.CountMissing()
		if err != nil {
			t.Fatalf("CountMissing() でエラー: %v", err)
		}

		want := []chapter02.Missing{
			{Column: "がく片長さ", Count: 2},
			{Column: "がく片幅", Count: 1},
			{Column: "花弁長さ", Count: 0},
			{Column: "花弁幅", Count: 0},
			{Column: "種類", Count: 0},
		}
		if !reflect.DeepEqual(got, want) {
			t.Errorf("CountMissing() = %v, want %v", got, want)
		}
	})
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(substr) == 0 || indexOf(s, substr) >= 0)
}

func indexOf(s, substr string) int {
	for i := 0; i+len(substr) <= len(s); i++ {
		if s[i:i+len(substr)] == substr {
			return i
		}
	}

	return -1
}
