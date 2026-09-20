package dataset_test

import (
	"path/filepath"
	"strings"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

func TestFrom(t *testing.T) {
	t.Parallel()

	t.Run("環境変数 ML_DATA_DIR が指定されていればそのディレクトリを返す", func(t *testing.T) {
		t.Parallel()

		got := dataset.From(func(string) (string, bool) { return "/tmp/ml-data", true })

		if want := "/tmp/ml-data"; got != want {
			t.Errorf("From() = %q, want %q", got, want)
		}
	})

	t.Run("環境変数が無ければ apps/data/sukkiri-ml を返す", func(t *testing.T) {
		t.Parallel()

		got := dataset.From(func(string) (string, bool) { return "", false })

		if want := filepath.Join("data", "sukkiri-ml"); !strings.HasSuffix(got, want) {
			t.Errorf("From() = %q, want suffix %q", got, want)
		}
	})
}
