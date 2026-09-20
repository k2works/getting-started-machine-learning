package chapter14_test

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter14"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

// requireData は学習データが無ければテストを飛ばす。
func requireData(t *testing.T) string {
	t.Helper()

	csvFile := filepath.Join(dataset.Current(), "Wholesale.csv")
	if _, err := os.Stat(csvFile); err != nil {
		t.Skip("学習データ Wholesale.csv が配置されていない（gulp data:setup）")
	}

	return csvFile
}

// spending は支出額のデータと、標準化した点を返す。
func spending(t *testing.T) ([]chapter02.Features, [][]float64) {
	t.Helper()

	x, err := chapter14.LoadSpending(requireData(t))
	if err != nil {
		t.Fatalf("LoadSpending() でエラー: %v", err)
	}

	points, err := chapter14.StandardizeSpending(x)
	if err != nil {
		t.Fatalf("StandardizeSpending() でエラー: %v", err)
	}

	return x, points
}

func TestSpendingData(t *testing.T) {
	t.Parallel()

	t.Run("区分の列を除いた 6 列を 440 件読み込む", func(t *testing.T) {
		t.Parallel()

		x, _ := spending(t)

		if len(x) != 440 {
			t.Errorf("件数 = %d, want 440", len(x))
		}

		want := []string{"Fresh", "Milk", "Grocery", "Frozen", "Detergents_Paper", "Delicassen"}
		for j, column := range want {
			if x[0].Columns[j] != column {
				t.Errorf("%d 列目 = %q, want %q", j, x[0].Columns[j], column)
			}
		}
	})

	t.Run("クラスタ数 1 の SSE は件数×列数になる", func(t *testing.T) {
		t.Parallel()

		_, points := spending(t)

		// 第 9 章の標準化は件数で割る標準偏差なので、標準化した各列の分散はちょうど 1 になる。
		// クラスタ数 1 のときの中心は全体の平均（原点）なので、SSE は 440 × 6 になる
		results, err := chapter14.SSEByClusterCount(points, []int{1}, 0, 1)
		if err != nil {
			t.Fatalf("SSEByClusterCount() でエラー: %v", err)
		}

		if want := 440.0 * 6; !closeTo(results[0].SSE, want) {
			t.Errorf("クラスタ数 1 の SSE = %v, want %v", results[0].SSE, want)
		}
	})

	t.Run("初期中心を 10 通り試すと 1 通りより SSE が小さくなる", func(t *testing.T) {
		t.Parallel()

		_, points := spending(t)

		once, err := chapter14.FitWithRestarts(points, 5, 0, 1)
		if err != nil {
			t.Fatalf("FitWithRestarts() でエラー: %v", err)
		}

		many, err := chapter14.FitWithRestarts(points, 5, 0, chapter14.DefaultNInit)
		if err != nil {
			t.Fatalf("FitWithRestarts() でエラー: %v", err)
		}

		if many.SSE >= once.SSE {
			t.Errorf("10 通りの SSE %v が、1 通りの %v より小さくなりませんでした", many.SSE, once.SSE)
		}

		t.Logf("初期中心 1 通りの SSE = %.2f, 10 通りの SSE = %.2f", once.SSE, many.SSE)
	})

	t.Run("クラスタごとの件数の合計が全体の件数になる", func(t *testing.T) {
		t.Parallel()

		x, points := spending(t)

		result, err := chapter14.FitWithRestarts(points, 5, 0, chapter14.DefaultNInit)
		if err != nil {
			t.Fatalf("FitWithRestarts() でエラー: %v", err)
		}

		summaries, err := chapter14.SummarizeClusters(x, result.Labels)
		if err != nil {
			t.Fatalf("SummarizeClusters() でエラー: %v", err)
		}

		total := 0
		for i, summary := range summaries {
			total += summary.Count

			if i > 0 && summaries[i-1].Count < summary.Count {
				t.Errorf("件数の多い順に並んでいません: %d の次に %d", summaries[i-1].Count, summary.Count)
			}
		}

		if total != len(x) {
			t.Errorf("件数の合計 = %d, want %d", total, len(x))
		}
	})

	t.Run("実行すると SSE の表とクラスタごとの特徴を表示する", func(t *testing.T) {
		t.Parallel()

		_ = requireData(t)

		var out bytes.Buffer
		if err := chapter14.Run(&out); err != nil {
			t.Fatalf("Run() でエラー: %v", err)
		}

		want := "データ件数: 440（支出額 6 列）\n" +
			"クラスタ数ごとの SSE（初期中心 10 通りの最小値）:\n" +
			"クラスタ数\tSSE\n" +
			"1\t2640.00\n" +
			"2\t1954.04\n" +
			"3\t1619.95\n" +
			"4\t1325.98\n" +
			"5\t1061.00\n" +
			"6\t947.09\n" +
			"7\t830.83\n" +
			"8\t761.04\n" +
			"9\t690.61\n" +
			"10\t606.47\n" +
			"\n" +
			"クラスタ数 5 のクラスタごとの件数と平均支出額:\n" +
			"クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen\n" +
			"1\t269\t9115\t2954\t3786\t2277\t979\t976\n" +
			"4\t96\t5509\t10556\t16478\t1420\t7199\t1659\n" +
			"3\t63\t32958\t4997\t5885\t8423\t955\t2463\n" +
			"0\t11\t16911\t34864\t46126\t3245\t23008\t4177\n" +
			"2\t1\t36847\t43950\t20170\t36534\t239\t47943\n"

		if got := out.String(); got != want {
			t.Errorf("Run() の出力 =\n%s\nwant\n%s", got, want)
		}
	})
}
