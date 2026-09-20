package chapter12

import (
	"fmt"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
)

// Experiment は 1 つの alpha で学習した結果。作ったあとは書き換えない。
// 実験の記録を書き換えられないようにしておくと、あとから「どの alpha で何が起きたか」を必ず読み返せる。
type Experiment struct {
	Alpha             float64
	TrainScore        float64
	ValidationScore   float64
	CoefficientAbsSum float64
}

// scoreOf はモデルの決定係数を求める。
func scoreOf(model RegularizedModel, x []chapter02.Features, t []float64) (float64, error) {
	predictions, err := model.PredictAll(x)
	if err != nil {
		return 0, err
	}

	return chapter07.R2Score(t, predictions)
}

// RunRidgeExperiments は alpha ごとにリッジ回帰を学習し、訓練データと検証データの決定係数を記録する。
func RunRidgeExperiments(data BostonDataset, alphas []float64) ([]Experiment, error) {
	experiments := make([]Experiment, 0, len(alphas))

	for _, alpha := range alphas {
		model, err := FitRidge(data.XTrain, data.TTrain, alpha)
		if err != nil {
			return nil, err
		}

		train, err := scoreOf(model, data.XTrain, data.TTrain)
		if err != nil {
			return nil, err
		}

		validation, err := scoreOf(model, data.XValidation, data.TValidation)
		if err != nil {
			return nil, err
		}

		experiments = append(experiments, Experiment{
			Alpha:             alpha,
			TrainScore:        train,
			ValidationScore:   validation,
			CoefficientAbsSum: model.CoefficientAbsSum(),
		})
	}

	return experiments, nil
}

// BestExperiment は検証データの決定係数が最も高い実験を返す。同じなら先の実験を選ぶ。
func BestExperiment(experiments []Experiment) (Experiment, error) {
	if len(experiments) == 0 {
		return Experiment{}, fmt.Errorf("実験が 1 件もありません")
	}

	best := experiments[0]

	for _, experiment := range experiments[1:] {
		if experiment.ValidationScore > best.ValidationScore {
			best = experiment
		}
	}

	return best, nil
}
