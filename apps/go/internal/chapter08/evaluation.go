package chapter08

import (
	"fmt"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// Evaluation は訓練データとテストデータの正解率と、テストデータの生存者のうち生存と予測できた人数。
type Evaluation struct {
	TrainAccuracy  float64
	TestAccuracy   float64
	FoundSurvivors int
	Survivors      int
}

// Evaluate は学習済みのパイプラインを、訓練データとテストデータで評価する。
func Evaluate(pipeline *FittedPipeline, split chapter02.TrainTestSplit[chapter02.Row, int]) (Evaluation, error) {
	trainPredictions, err := pipeline.Predict(FeaturesTable(split.XTrain))
	if err != nil {
		return Evaluation{}, err
	}

	trainAccuracy, err := Accuracy(trainPredictions, split.TTrain)
	if err != nil {
		return Evaluation{}, err
	}

	testPredictions, err := pipeline.Predict(FeaturesTable(split.XTest))
	if err != nil {
		return Evaluation{}, err
	}

	testAccuracy, err := Accuracy(testPredictions, split.TTest)
	if err != nil {
		return Evaluation{}, err
	}

	found, survivors := 0, 0

	for i, label := range split.TTest {
		if label != Survived {
			continue
		}

		survivors++

		if testPredictions[i] == Survived {
			found++
		}
	}

	return Evaluation{
		TrainAccuracy:  trainAccuracy,
		TestAccuracy:   testAccuracy,
		FoundSurvivors: found,
		Survivors:      survivors,
	}, nil
}

// Accuracy は予測が正解ラベルと一致した割合を返す。件数が違えばエラーを返す。
// 第 1 章の Accuracy は文字列のラベル用なので、整数のラベル用にこの章で作る。
func Accuracy(predictions, labels []int) (float64, error) {
	if len(predictions) != len(labels) {
		return 0, fmt.Errorf("予測と正解ラベルの件数が違います: %d と %d", len(predictions), len(labels))
	}

	if len(labels) == 0 {
		return 0, fmt.Errorf("正解ラベルがありません")
	}

	correct := 0

	for i, prediction := range predictions {
		if prediction == labels[i] {
			correct++
		}
	}

	return float64(correct) / float64(len(labels)), nil
}
