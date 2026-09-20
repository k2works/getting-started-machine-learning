package chapter08_test

import (
	"path/filepath"
	"reflect"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter08"
)

// trainingTable は架空の乗客 6 人の表。女性は全員生存、男性は全員死亡にしてある。
func trainingTable() (chapter02.Table, []int) {
	table := tableOf(chapter08.SurvivedFeatures,
		[]string{"1", "female", "30", "0", "0", "80", "C"},
		[]string{"1", "female", "", "0", "0", "70", "C"},
		[]string{"3", "male", "20", "0", "0", "8", "S"},
		[]string{"3", "male", "24", "0", "0", "7", ""},
		[]string{"2", "female", "40", "0", "0", "30", "S"},
		[]string{"2", "male", "35", "0", "0", "25", "S"},
	)

	return table, []int{1, 1, 0, 0, 1, 0}
}

func TestPipelineFitAndPredict(t *testing.T) {
	t.Parallel()

	table, labels := trainingTable()

	pipeline, err := chapter08.BuildPipeline(5, chapter08.WeightNone).Fit(table, labels)
	if err != nil {
		t.Fatalf("Fit() でエラー: %v", err)
	}

	predictions, err := pipeline.Predict(table)
	if err != nil {
		t.Fatalf("Predict() でエラー: %v", err)
	}

	if !reflect.DeepEqual(predictions, labels) {
		t.Errorf("予測 = %v, want %v", predictions, labels)
	}
}

func TestPipelineLeavesNoMissingValue(t *testing.T) {
	t.Parallel()

	table, labels := trainingTable()

	pipeline, err := chapter08.BuildPipeline(5, chapter08.WeightBalanced).Fit(table, labels)
	if err != nil {
		t.Fatalf("Fit() でエラー: %v", err)
	}

	// 年齢も港も欠けた乗客。前処理が補完するので、特徴量にできる。
	unknown := tableOf(chapter08.SurvivedFeatures,
		[]string{"3", "male", "", "0", "0", "8", ""},
	)

	features, err := pipeline.Features(unknown)
	if err != nil {
		t.Fatalf("Features() でエラー: %v", err)
	}

	if len(features) != 1 {
		t.Fatalf("件数 = %d, want 1", len(features))
	}

	// Sex と Embarked はダミー変数になるので、元の列は残らない。
	for _, column := range features[0].Columns {
		if column == "Sex" || column == "Embarked" {
			t.Errorf("カテゴリ値の列が残っています: %s", column)
		}
	}
}

func TestSavedPipelinePredictsTheSame(t *testing.T) {
	t.Parallel()

	table, labels := trainingTable()

	pipeline, err := chapter08.BuildPipeline(5, chapter08.WeightBalanced).Fit(table, labels)
	if err != nil {
		t.Fatalf("Fit() でエラー: %v", err)
	}

	modelFile := filepath.Join(t.TempDir(), "model", "survived.gob")
	if err := chapter08.SavePipeline(pipeline, modelFile); err != nil {
		t.Fatalf("SavePipeline() でエラー: %v", err)
	}

	loaded, err := chapter08.LoadPipeline(modelFile)
	if err != nil {
		t.Fatalf("LoadPipeline() でエラー: %v", err)
	}

	before, err := pipeline.Predict(table)
	if err != nil {
		t.Fatalf("Predict() でエラー: %v", err)
	}

	after, err := loaded.Predict(table)
	if err != nil {
		t.Fatalf("Predict() でエラー: %v", err)
	}

	if !reflect.DeepEqual(before, after) {
		t.Errorf("読み込んだモデルの予測 = %v, want %v", after, before)
	}
}

func TestLoadPipelineFailsForBrokenFile(t *testing.T) {
	t.Parallel()

	modelFile := filepath.Join(t.TempDir(), "broken.gob")
	if err := writeFile(t, modelFile, "これは gob ではありません"); err != nil {
		t.Fatalf("ファイルを書けません: %v", err)
	}

	if _, err := chapter08.LoadPipeline(modelFile); err == nil {
		t.Error("壊れたファイルを読み込めてしまいました")
	}
}
