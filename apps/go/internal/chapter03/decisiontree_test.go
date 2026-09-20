package chapter03_test

import (
	"math"
	"reflect"
	"strings"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter03"
)

// column は 1 列だけの特徴量を値の数だけ作る。
func column(t *testing.T, name string, values ...float64) []chapter02.Features {
	t.Helper()

	features := make([]chapter02.Features, len(values))

	for i, value := range values {
		got, err := chapter02.NewFeatures([]string{name}, []float64{value})
		if err != nil {
			t.Fatalf("NewFeatures() でエラー: %v", err)
		}

		features[i] = got
	}

	return features
}

// threeSpecies は 3 品種のうち、境界の右側に versicolor 2 件と virginica 1 件が残るデータ。
func threeSpecies(t *testing.T) ([]chapter02.Features, []string) {
	t.Helper()

	return column(t, "花弁幅", 0.1, 0.2, 0.3, 0.5, 0.6, 0.9),
		[]string{"setosa", "setosa", "setosa", "versicolor", "versicolor", "virginica"}
}

func fit(t *testing.T, tree *chapter03.DecisionTree, x []chapter02.Features, labels []string) *chapter03.DecisionTree {
	t.Helper()

	if err := tree.Fit(x, labels); err != nil {
		t.Fatalf("Fit() でエラー: %v", err)
	}

	return tree
}

func predict(t *testing.T, tree *chapter03.DecisionTree, x []chapter02.Features) []string {
	t.Helper()

	got, err := tree.Predict(x)
	if err != nil {
		t.Fatalf("Predict() でエラー: %v", err)
	}

	return got
}

func TestGini(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		labels []string
		want   float64
	}{
		{name: "1 種類のラベルだけならジニ不純度は 0", labels: []string{"setosa", "setosa", "setosa"}, want: 0},
		{name: "2 種類のラベルが半分ずつならジニ不純度は 0.5", labels: []string{"setosa", "virginica"}, want: 0.5},
		{name: "3 種類のラベルが同じ数ならジニ不純度は 3 分の 2", labels: []string{"setosa", "versicolor", "virginica"}, want: 2.0 / 3},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if got := chapter03.Gini(test.labels); math.Abs(got-test.want) > 1e-12 {
				t.Errorf("Gini() = %v, want %v", got, test.want)
			}
		})
	}
}

func TestBestSplit(t *testing.T) {
	t.Parallel()

	t.Run("ラベルを完全に分けられる境界を見つける", func(t *testing.T) {
		t.Parallel()

		split, found, err := chapter03.BestSplit(
			column(t, "花弁幅", 0.1, 0.2, 0.7, 0.8),
			[]string{"setosa", "setosa", "virginica", "virginica"},
		)
		if err != nil || !found {
			t.Fatalf("BestSplit() = (found=%v, err=%v)", found, err)
		}

		if split.Feature != "花弁幅" || math.Abs(split.Threshold-0.45) > 1e-12 || math.Abs(split.Impurity) > 1e-12 {
			t.Errorf("BestSplit() = %+v, want 花弁幅 0.45 不純度 0", split)
		}
	})

	t.Run("複数の特徴量から不純度が最も小さくなる特徴量と境界を選ぶ", func(t *testing.T) {
		t.Parallel()

		columns := []string{"がく片長さ", "花弁長さ"}
		values := [][]float64{{0.1, 0.2}, {0.3, 0.1}, {0.2, 0.9}, {0.4, 0.6}}
		x := make([]chapter02.Features, len(values))

		for i, value := range values {
			features, err := chapter02.NewFeatures(columns, value)
			if err != nil {
				t.Fatalf("NewFeatures() でエラー: %v", err)
			}

			x[i] = features
		}

		split, found, err := chapter03.BestSplit(x, []string{"setosa", "setosa", "virginica", "virginica"})
		if err != nil || !found {
			t.Fatalf("BestSplit() = (found=%v, err=%v)", found, err)
		}

		if split.Feature != "花弁長さ" || math.Abs(split.Threshold-0.4) > 1e-12 {
			t.Errorf("BestSplit() = %+v, want 花弁長さ 0.4", split)
		}
	})

	t.Run("ラベルが 1 種類なら分割しない", func(t *testing.T) {
		t.Parallel()

		_, found, err := chapter03.BestSplit(column(t, "花弁幅", 0.1, 0.2, 0.7), []string{"setosa", "setosa", "setosa"})
		if err != nil || found {
			t.Errorf("BestSplit() = (found=%v, err=%v), want found=false", found, err)
		}
	})
}

func TestDecisionTree(t *testing.T) {
	t.Parallel()

	t.Run("1 種類のラベルだけを学習するとそのラベルを予測する", func(t *testing.T) {
		t.Parallel()

		model := fit(t, chapter03.Unlimited(), column(t, "花弁幅", 0.1, 0.2), []string{"setosa", "setosa"})

		if got, want := predict(t, model, column(t, "花弁幅", 0.15, 0.9)), []string{"setosa", "setosa"}; !reflect.DeepEqual(got, want) {
			t.Errorf("Predict() = %v, want %v", got, want)
		}
	})

	t.Run("境界の左右で異なるラベルを予測する", func(t *testing.T) {
		t.Parallel()

		model := fit(t, chapter03.Unlimited(),
			column(t, "花弁幅", 0.1, 0.2, 0.7, 0.8),
			[]string{"setosa", "setosa", "virginica", "virginica"})

		if got, want := predict(t, model, column(t, "花弁幅", 0.15, 0.75)), []string{"setosa", "virginica"}; !reflect.DeepEqual(got, want) {
			t.Errorf("Predict() = %v, want %v", got, want)
		}
	})

	t.Run("深さを制限しなければすべての訓練データを分け切る", func(t *testing.T) {
		t.Parallel()

		x, labels := threeSpecies(t)
		model := fit(t, chapter03.Unlimited(), x, labels)

		if got := predict(t, model, x); !reflect.DeepEqual(got, labels) {
			t.Errorf("Predict() = %v, want %v", got, labels)
		}
	})

	t.Run("深さを 1 に制限すると境界の先は多数派のラベルを予測する", func(t *testing.T) {
		t.Parallel()

		x, labels := threeSpecies(t)

		shallow, err := chapter03.WithMaxDepth(1)
		if err != nil {
			t.Fatalf("WithMaxDepth() でエラー: %v", err)
		}

		model := fit(t, shallow, x, labels)

		if got, want := predict(t, model, column(t, "花弁幅", 0.2, 0.95)), []string{"setosa", "versicolor"}; !reflect.DeepEqual(got, want) {
			t.Errorf("Predict() = %v, want %v", got, want)
		}
	})

	t.Run("学習する前に予測するとエラーになる", func(t *testing.T) {
		t.Parallel()

		_, err := chapter03.Unlimited().Predict(column(t, "花弁幅", 0.1))
		if err == nil {
			t.Fatal("エラーを期待したが nil だった")
		}

		if want := "Fit で学習してから Predict を呼んでください"; err.Error() != want {
			t.Errorf("エラー = %q, want %q", err.Error(), want)
		}
	})

	t.Run("深さの上限に負の数を渡すとエラーになる", func(t *testing.T) {
		t.Parallel()

		if _, err := chapter03.WithMaxDepth(-1); err == nil {
			t.Error("エラーを期待したが nil だった")
		}
	})
}

func TestFormat(t *testing.T) {
	t.Parallel()

	t.Run("葉だけの木はラベルを表示する", func(t *testing.T) {
		t.Parallel()

		got, err := chapter03.Format(chapter03.Leaf{Label: "setosa"})
		if err != nil || got != "setosa" {
			t.Errorf("Format() = (%q, %v), want (\"setosa\", nil)", got, err)
		}
	})

	t.Run("節は条件ごとに字下げして表示する", func(t *testing.T) {
		t.Parallel()

		tree := chapter03.Node{
			Split: chapter03.Split{Feature: "花弁幅", Threshold: 0.4},
			Left:  chapter03.Leaf{Label: "setosa"},
			Right: chapter03.Node{
				Split: chapter03.Split{Feature: "花弁長さ", Threshold: 0.75},
				Left:  chapter03.Leaf{Label: "versicolor"},
				Right: chapter03.Leaf{Label: "virginica"},
			},
		}

		got, err := chapter03.Format(tree)
		if err != nil {
			t.Fatalf("Format() でエラー: %v", err)
		}

		want := strings.Join([]string{
			"花弁幅 <= 0.4000",
			"  setosa",
			"花弁幅 > 0.4000",
			"  花弁長さ <= 0.7500",
			"    versicolor",
			"  花弁長さ > 0.7500",
			"    virginica",
		}, "\n")
		if got != want {
			t.Errorf("Format() = %q, want %q", got, want)
		}
	})
}
