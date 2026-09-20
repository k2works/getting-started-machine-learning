package chapter10

import (
	"fmt"
	"io"
	"path/filepath"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter01"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter03"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	testSize = 0.3
	seed     = 0
	// nEstimators は森を作る決定木の本数。
	nEstimators = 100
	// maxFeatures は 1 本の木が使う特徴量の数。
	maxFeatures = 2
	// shallowDepth は浅い木の深さ。
	shallowDepth = 2
)

// Score は訓練データとテストデータの正解率。
type Score struct {
	Train float64
	Test  float64
}

// NamedModel は表示する名前と、モデル。
type NamedModel struct {
	Name  string
	Model Classifier
}

// Models は比べるモデルを、表示する順に返す。
func Models() ([]NamedModel, error) {
	tree, err := chapter03.WithMaxDepth(shallowDepth)
	if err != nil {
		return nil, err
	}

	shallowForest, err := NewRandomForestWithMaxDepth(nEstimators, maxFeatures, shallowDepth, seed)
	if err != nil {
		return nil, err
	}

	return []NamedModel{
		{Name: fmt.Sprintf("決定木（深さ %d）", shallowDepth), Model: tree},
		{Name: "ロジスティック回帰", Model: NewLogisticRegression()},
		{Name: fmt.Sprintf("ランダムフォレスト（%d 本）", nEstimators), Model: NewRandomForest(nEstimators, maxFeatures, seed)},
		{Name: fmt.Sprintf("ランダムフォレスト（%d 本・深さ %d）", nEstimators, shallowDepth), Model: shallowForest},
	}, nil
}

// Evaluate はモデルを訓練データで学習させてから、訓練データとテストデータの正解率を求める。
func Evaluate(model Classifier, split chapter02.TrainTestSplit[chapter02.Features, string]) (Score, error) {
	if err := model.Fit(split.XTrain, split.TTrain); err != nil {
		return Score{}, err
	}

	train, err := accuracyOf(model, split.XTrain, split.TTrain)
	if err != nil {
		return Score{}, err
	}

	test, err := accuracyOf(model, split.XTest, split.TTest)
	if err != nil {
		return Score{}, err
	}

	return Score{Train: train, Test: test}, nil
}

func accuracyOf(model Classifier, x []chapter02.Features, t []string) (float64, error) {
	predictions, err := model.Predict(x)
	if err != nil {
		return 0, err
	}

	return chapter01.Accuracy(predictions, t)
}

// Run はモデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。
func Run(out io.Writer) error {
	split, err := chapter02.PrepareIris(filepath.Join(dataset.Current(), "iris.csv"), testSize, seed)
	if err != nil {
		return err
	}

	if _, err := fmt.Fprintln(out, "モデル\t訓練データ\tテストデータ"); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	models, err := Models()
	if err != nil {
		return err
	}

	for _, model := range models {
		score, err := Evaluate(model.Model, split)
		if err != nil {
			return err
		}

		if _, err := fmt.Fprintf(out, "%s\t%.4f\t%.4f\n", model.Name, score.Train, score.Test); err != nil {
			return fmt.Errorf("表示できません: %w", err)
		}
	}

	return printImportances(out, split)
}

// printImportances はランダムフォレストの特徴量の重要度を表示する。
func printImportances(out io.Writer, split chapter02.TrainTestSplit[chapter02.Features, string]) error {
	forest := NewRandomForest(nEstimators, maxFeatures, seed)
	if err := forest.Fit(split.XTrain, split.TTrain); err != nil {
		return err
	}

	importances, err := ForestImportances(forest, split.XTrain, split.TTrain)
	if err != nil {
		return err
	}

	if _, err := fmt.Fprintf(out, "\nランダムフォレスト（%d 本）の特徴量の重要度:\n", nEstimators); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	for _, importance := range importances {
		if _, err := fmt.Fprintf(out, "%s\t%.4f\n", importance.Feature, importance.Value); err != nil {
			return fmt.Errorf("表示できません: %w", err)
		}
	}

	return nil
}
