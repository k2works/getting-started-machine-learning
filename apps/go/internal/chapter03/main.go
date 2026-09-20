package chapter03

import (
	"fmt"
	"io"
	"path/filepath"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter01"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	testSize = 0.3
	seed     = 0
	// treeDepthToShow は最後に木そのものを表示する深さ。
	treeDepthToShow = 2
)

// maxDepths は正解率を比べる深さ。
var maxDepths = []int{1, 2, 3, 4, 5}

// Run は深さごとの正解率と、深さ 2 の決定木を表示する。
func Run(out io.Writer) error {
	split, err := chapter02.PrepareIris(filepath.Join(dataset.Current(), "iris.csv"), testSize, seed)
	if err != nil {
		return err
	}

	if _, err := fmt.Fprintln(out, "深さ\t訓練データ\tテストデータ"); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	for _, maxDepth := range maxDepths {
		model, err := WithMaxDepth(maxDepth)
		if err != nil {
			return err
		}

		if err := printAccuracy(out, fmt.Sprintf("%d", maxDepth), model, split); err != nil {
			return err
		}
	}

	if err := printAccuracy(out, "制限なし", Unlimited(), split); err != nil {
		return err
	}

	shallow, err := WithMaxDepth(treeDepthToShow)
	if err != nil {
		return err
	}

	if err := shallow.Fit(split.XTrain, split.TTrain); err != nil {
		return err
	}

	tree, ok := shallow.Tree()
	if !ok {
		return fmt.Errorf("決定木がまだ作られていません")
	}

	formatted, err := Format(tree)
	if err != nil {
		return err
	}

	if _, err := fmt.Fprintf(out, "\n深さ %d の決定木:\n%s\n", treeDepthToShow, formatted); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	return nil
}

// printAccuracy は木を作り、訓練データとテストデータの正解率を 1 行で表示する。
func printAccuracy(
	out io.Writer,
	label string,
	model *DecisionTree,
	split chapter02.TrainTestSplit[chapter02.Features, string],
) error {
	if err := model.Fit(split.XTrain, split.TTrain); err != nil {
		return err
	}

	train, err := accuracyOf(model, split.XTrain, split.TTrain)
	if err != nil {
		return err
	}

	test, err := accuracyOf(model, split.XTest, split.TTest)
	if err != nil {
		return err
	}

	if _, err := fmt.Fprintf(out, "%s\t%.4f\t%.4f\n", label, train, test); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	return nil
}

// accuracyOf は予測して正解率を求める。
func accuracyOf(model *DecisionTree, x []chapter02.Features, t []string) (float64, error) {
	predictions, err := model.Predict(x)
	if err != nil {
		return 0, err
	}

	return chapter01.Accuracy(predictions, t)
}
