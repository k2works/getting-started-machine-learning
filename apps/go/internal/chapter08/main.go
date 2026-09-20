package chapter08

import (
	"fmt"
	"io"
	"path/filepath"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	testSize = 0.2
	seed     = 0
	maxDepth = 5
)

// ModelFile は学習済みのパイプラインの保存先（apps/go/model/ は .gitignore の対象）。
const ModelFile = "model/survived.gob"

// Run は重みの付け方ごとの評価結果を表示し、学習済みのパイプラインを保存して読み込む。
func Run(out io.Writer) error {
	return RunWithModelFile(out, ModelFile)
}

// RunWithModelFile は保存先を指定して実行する。テストから一時ディレクトリを渡すために使う。
func RunWithModelFile(out io.Writer, modelFile string) error {
	table, err := chapter02.LoadTable(filepath.Join(dataset.Current(), "Survived.csv"))
	if err != nil {
		return err
	}

	labels, err := SurvivedLabels(table.Rows)
	if err != nil {
		return err
	}

	split, err := chapter02.SplitTrainTest(table.Rows, labels, testSize, seed)
	if err != nil {
		return err
	}

	survivors := 0

	for _, label := range labels {
		if label == Survived {
			survivors++
		}
	}

	if _, err := fmt.Fprintf(out, "データ件数: %d（生存 %d, 死亡 %d）\n", len(table.Rows), survivors, len(labels)-survivors); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	if _, err := fmt.Fprintf(out, "訓練データ: %d 件, テストデータ: %d 件\n", len(split.XTrain), len(split.XTest)); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	pipelines := make(map[ClassWeight]*FittedPipeline, len(ClassWeights))

	for _, classWeight := range ClassWeights {
		pipeline, err := BuildPipeline(maxDepth, classWeight).Fit(FeaturesTable(split.XTrain), split.TTrain)
		if err != nil {
			return err
		}

		pipelines[classWeight] = pipeline

		result, err := Evaluate(pipeline, split)
		if err != nil {
			return err
		}

		if _, err := fmt.Fprintf(
			out,
			"classWeight=%s: 訓練 %.3f, テスト %.3f, 生存者 %d 人中 %d 人を発見\n",
			classWeight, result.TrainAccuracy, result.TestAccuracy, result.Survivors, result.FoundSurvivors,
		); err != nil {
			return fmt.Errorf("表示できません: %w", err)
		}
	}

	if err := SavePipeline(pipelines[WeightBalanced], modelFile); err != nil {
		return err
	}

	loaded, err := LoadPipeline(modelFile)
	if err != nil {
		return err
	}

	newPassengers, err := newPassengers()
	if err != nil {
		return err
	}

	predictions, err := loaded.Predict(newPassengers)
	if err != nil {
		return err
	}

	if _, err := fmt.Fprintf(out, "保存したモデル: %s\n", filepath.Base(modelFile)); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	if _, err := fmt.Fprintf(out, "架空の乗客の予測: %v\n", predictions); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	return nil
}

// newPassengers は年齢が分からない架空の乗客 2 人（1 等客室の女性と、3 等客室の男性）の表を作る。
func newPassengers() (chapter02.Table, error) {
	rows := make([]chapter02.Row, 0, 2)

	for _, values := range [][]string{
		{"1", "female", "", "0", "0", "50", "C"},
		{"3", "male", "", "0", "0", "8", "S"},
	} {
		row, err := Passenger(values...)
		if err != nil {
			return chapter02.Table{}, err
		}

		rows = append(rows, row)
	}

	return FeaturesTable(rows), nil
}
