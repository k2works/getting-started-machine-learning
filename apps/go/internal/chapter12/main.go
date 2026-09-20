package chapter12

import (
	"fmt"
	"io"
	"path/filepath"
	"strings"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	testSize       = 0.3
	validationSize = 0.3
	seed           = 0
	// lassoAlpha はラッソ回帰で係数を 0 にする様子を見るための alpha。
	// 罰則を ‖t − X w‖² に足すので、平均で割る流儀のライブラリより大きな値が要る。
	lassoAlpha = 50
)

// Alphas は試す alpha の並び。0 は正則化なし（最小二乗法）と同じ。
var Alphas = []float64{0, 0.1, 1, 10, 100}

// Run は Boston の住宅価格で、alpha ごとのリッジ回帰を比べ、ラッソ回帰で 0 になる係数を表示する。
func Run(out io.Writer) error {
	data, err := PrepareBoston(filepath.Join(dataset.Current(), "Boston.csv"), testSize, validationSize, seed)
	if err != nil {
		return err
	}

	total := len(data.XTrain) + len(data.XValidation) + len(data.XTest)
	if err := writeLine(out, "データ件数: %d（外れ値 %d 件を除外）", total, data.Removed); err != nil {
		return err
	}

	if err := writeLine(
		out,
		"訓練データ: %d 件, 検証データ: %d 件, テストデータ: %d 件",
		len(data.XTrain), len(data.XValidation), len(data.XTest),
	); err != nil {
		return err
	}

	if err := writeLine(out, "特徴量: %s", strings.Join(data.Columns, ", ")); err != nil {
		return err
	}

	experiments, err := RunRidgeExperiments(data, Alphas)
	if err != nil {
		return err
	}

	if err := writeLine(out, "alpha\t訓練 R²\t検証 R²\t係数の絶対値の合計"); err != nil {
		return err
	}

	for _, experiment := range experiments {
		if err := writeLine(
			out,
			"%5.1f\t%.4f\t%.4f\t%.3f",
			experiment.Alpha, experiment.TrainScore, experiment.ValidationScore, experiment.CoefficientAbsSum,
		); err != nil {
			return err
		}
	}

	best, err := BestExperiment(experiments)
	if err != nil {
		return err
	}

	if err := writeLine(out, "検証データで選んだ alpha: %v", best.Alpha); err != nil {
		return err
	}

	return printTestScores(out, data, best.Alpha)
}

// printTestScores は選んだ alpha のリッジ回帰と、正則化なしの線形回帰をテストデータで比べる。
func printTestScores(out io.Writer, data BostonDataset, alpha float64) error {
	plain, err := FitRidge(data.XTrain, data.TTrain, 0)
	if err != nil {
		return err
	}

	plainScore, err := scoreOf(plain, data.XTest, data.TTest)
	if err != nil {
		return err
	}

	ridge, err := FitRidge(data.XTrain, data.TTrain, alpha)
	if err != nil {
		return err
	}

	ridgeScore, err := scoreOf(ridge, data.XTest, data.TTest)
	if err != nil {
		return err
	}

	lasso, err := FitLasso(data.XTrain, data.TTrain, lassoAlpha)
	if err != nil {
		return err
	}

	lassoScore, err := scoreOf(lasso, data.XTest, data.TTest)
	if err != nil {
		return err
	}

	if err := writeLine(
		out,
		"テストデータの決定係数: 線形回帰 %.4f, リッジ回帰 %.4f, ラッソ回帰 %.4f",
		plainScore, ridgeScore, lassoScore,
	); err != nil {
		return err
	}

	zero := ZeroCoefficientNames(lasso)

	return writeLine(out, "ラッソ回帰（alpha=%v）で係数が 0 になった特徴量: %s", lassoAlpha, strings.Join(zero, ", "))
}

// writeLine は 1 行書き出す。
func writeLine(out io.Writer, format string, args ...any) error {
	if _, err := fmt.Fprintf(out, format+"\n", args...); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	return nil
}
