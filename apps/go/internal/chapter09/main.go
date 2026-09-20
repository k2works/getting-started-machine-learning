package chapter09

import (
	"fmt"
	"io"
	"path/filepath"
	"slices"
	"strings"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	testSize = 0.3
	seed     = 0
	// zeroTolerance は、浮動小数点の誤差で -0.00 と表示されないように 0 とみなす幅。
	zeroTolerance = 1e-9
)

// Columns は多項式特徴量を作る元の列。
var Columns = []string{"RM", "LSTAT", "PTRATIO"}

// Squares は 2 乗の項。
var Squares = []string{"RM^2", "LSTAT^2", "PTRATIO^2"}

// FeatureSet は特徴量の組の名前と、使う項。
type FeatureSet struct {
	Name  string
	Terms []string
}

// FeatureSets は決定係数を比べる特徴量の組を、表示する順に返す。
func FeatureSets() []FeatureSet {
	interactions := make([]string, 0)
	for _, pair := range PairsWithReplacement(Columns) {
		interactions = append(interactions, pair.Name())
	}

	return []FeatureSet{
		{Name: "元の特徴量", Terms: Columns},
		{Name: "2 乗の項を追加", Terms: slices.Concat(Columns, Squares)},
		{Name: "交互作用の項も追加", Terms: slices.Concat(Columns, interactions)},
	}
}

// Run はボストンの住宅価格で特徴量エンジニアリングを試し、自転車の利用者数に天気を結合して集計する。
func Run(out io.Writer) error {
	dataDir := dataset.Current()

	split, err := PrepareBoston(filepath.Join(dataDir, "Boston.csv"), testSize, seed)
	if err != nil {
		return err
	}

	if err := printSplit(out, split); err != nil {
		return err
	}

	if err := printStandardized(out, split); err != nil {
		return err
	}

	if err := printScores(out, split); err != nil {
		return err
	}

	return printWeather(out, dataDir)
}

func printSplit(out io.Writer, split chapter02.TrainTestSplit[chapter02.Features, float64]) error {
	return writeLines(out,
		fmt.Sprintf("訓練データ: %d 件, テストデータ: %d 件", len(split.XTrain), len(split.XTest)),
		"特徴量の列: "+strings.Join(split.XTrain[0].Columns, ", "),
	)
}

// printStandardized は標準化した訓練データを、もう一度 Fit して平均と標準偏差を確かめる。
func printStandardized(out io.Writer, split chapter02.TrainTestSplit[chapter02.Features, float64]) error {
	standardizer, err := Fit(split.XTrain)
	if err != nil {
		return err
	}

	transformed, err := standardizer.Transform(split.XTrain)
	if err != nil {
		return err
	}

	checked, err := Fit(transformed)
	if err != nil {
		return err
	}

	values, err := columnValues(transformed, "RM")
	if err != nil {
		return err
	}

	return writeLines(out,
		fmt.Sprintf("標準化した訓練データの RM: 平均 %s, 標準偏差 %s",
			format(checked.Means["RM"], 2), format(checked.Stds["RM"], 2)),
		fmt.Sprintf("  gonum の stat.StdDev（標本標準偏差）: %s", format(GonumStdDev(values), 4)),
	)
}

func printScores(out io.Writer, split chapter02.TrainTestSplit[chapter02.Features, float64]) error {
	if err := writeLines(out, "決定係数:"); err != nil {
		return err
	}

	for _, set := range FeatureSets() {
		scores, err := ScoreFeatureSet(split, Columns, set.Terms)
		if err != nil {
			return err
		}

		line := fmt.Sprintf("  %s（%d 列）: %s", set.Name, len(set.Terms), formatScores(scores))
		if err := writeLines(out, line); err != nil {
			return err
		}
	}

	outliers := 0

	for _, outlier := range IQROutliers(split.TTrain, DefaultK) {
		if outlier {
			outliers++
		}
	}

	removed, err := ScoreFeatureSet(RemoveTargetOutliers(split), Columns, slices.Concat(Columns, Squares))
	if err != nil {
		return err
	}

	return writeLines(out,
		fmt.Sprintf("訓練データの PRICE の外れ値: %d 件", outliers),
		"  外れ値を除いて 2 乗の項を追加: "+formatScores(removed),
	)
}

func printWeather(out io.Writer, dataDir string) error {
	bike, err := LoadDelimited(filepath.Join(dataDir, "bike.tsv"), UTF8, "\t")
	if err != nil {
		return err
	}

	weather, err := LoadDelimited(filepath.Join(dataDir, "weather.csv"), ShiftJIS, ",")
	if err != nil {
		return err
	}

	joined, err := JoinWeather(bike, weather)
	if err != nil {
		return err
	}

	means, err := MeanCountByWeather(joined)
	if err != nil {
		return err
	}

	formatted := make([]string, len(means))
	for i, mean := range means {
		formatted[i] = fmt.Sprintf("%s=%s", mean.Weather, format(mean.Mean, 1))
	}

	return writeLines(out, "天気ごとの平均利用者数: "+strings.Join(formatted, ", "))
}

func writeLines(out io.Writer, lines ...string) error {
	for _, line := range lines {
		if _, err := fmt.Fprintln(out, line); err != nil {
			return fmt.Errorf("表示できません: %w", err)
		}
	}

	return nil
}

func format(value float64, digits int) string {
	if value < zeroTolerance && value > -zeroTolerance {
		value = 0
	}

	return fmt.Sprintf("%.*f", digits, value)
}

func formatScores(scores Scores) string {
	return fmt.Sprintf("訓練 %s, テスト %s", format(scores.Train, 4), format(scores.Test, 4))
}
