package chapter01

import (
	"fmt"
	"io"
	"path/filepath"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

// Run は実データでルールによる判定の正解率を表示する。
func Run(out io.Writer) error {
	people, err := LoadPeople(filepath.Join(dataset.Current(), "KvsT.csv"))
	if err != nil {
		return err
	}

	features, labels := SplitFeaturesAndLabels(people)

	predictions := make([]string, len(features))
	for i, feature := range features {
		predictions[i] = PredictByRule(feature)
	}

	accuracy, err := Accuracy(predictions, labels)
	if err != nil {
		return err
	}

	if _, err := fmt.Fprintf(out, "データ件数: %d\n", len(people)); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	if _, err := fmt.Fprintf(out, "ルールによる判定の正解率: %.4f\n", accuracy); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	return nil
}
