package chapter15_test

import (
	"encoding/json"
	"math"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter15"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

// requireData は学習データが無ければテストを飛ばす。
func requireData(t *testing.T) string {
	t.Helper()

	dataDir := dataset.Current()
	for _, name := range []string{"/cinema.csv", "/Survived.csv"} {
		if _, err := os.Stat(dataDir + name); err != nil {
			t.Skip("学習データが配置されていない（gulp data:setup）")
		}
	}

	return dataDir
}

// trainedStore は実データで学習して一時ディレクトリに保存した置き場を返す。
func trainedStore(t *testing.T) *chapter15.FileModelStore {
	t.Helper()

	store := chapter15.NewFileModelStore(t.TempDir())
	if err := chapter15.TrainAndSaveModels(requireData(t), store); err != nil {
		t.Fatalf("TrainAndSaveModels() でエラー: %v", err)
	}

	return store
}

func TestTrainedModels(t *testing.T) {
	t.Parallel()

	t.Run("保存したモデルを読み込んで予測できる", func(t *testing.T) {
		t.Parallel()

		service := chapter15.NewPredictionService(trainedStore(t))

		sales, err := service.PredictSales(chapter15.Movie{SNS1: 100, SNS2: 2000, Actor: 300, Original: 1})
		if err != nil {
			t.Fatalf("PredictSales() でエラー: %v", err)
		}

		if math.Abs(sales-7984.316555463484) > 1e-9 {
			t.Errorf("興行収入 = %v, want %v", sales, 7984.316555463484)
		}

		survived, err := service.PredictSurvival(chapter15.Passenger{
			Pclass: "1", Sex: "female", Age: "30", SibSp: "0", Parch: "0", Fare: "80", Embarked: "S",
		})
		if err != nil {
			t.Fatalf("PredictSurvival() でエラー: %v", err)
		}

		if !survived {
			t.Errorf("生存 = %v, want true", survived)
		}
	})

	t.Run("学習していなければモデルは読み込めない", func(t *testing.T) {
		t.Parallel()

		service := chapter15.NewPredictionService(chapter15.NewFileModelStore(t.TempDir()))

		for _, model := range service.Health() {
			if model.Ready {
				t.Errorf("モデル %s = 読み込める, want 読み込めない", model.Name)
			}
		}
	})

	t.Run("学習したモデルで API が動く", func(t *testing.T) {
		t.Parallel()

		handler := chapter15.NewHandler(chapter15.NewPredictionService(trainedStore(t)))
		server := httptest.NewServer(handler)

		t.Cleanup(server.Close)

		response, err := http.Post(
			server.URL+"/cinema/sales",
			"application/json",
			strings.NewReader(`{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}`),
		)
		if err != nil {
			t.Fatalf("要求を送れません: %v", err)
		}

		t.Cleanup(func() { _ = response.Body.Close() })

		if response.StatusCode != http.StatusOK {
			t.Fatalf("状態コード = %d, want %d", response.StatusCode, http.StatusOK)
		}

		var body struct {
			Sales float64 `json:"sales"`
		}
		if err := json.NewDecoder(response.Body).Decode(&body); err != nil {
			t.Fatalf("応答が JSON ではありません: %v", err)
		}

		if math.Abs(body.Sales-7984.316555463484) > 1e-9 {
			t.Errorf("sales = %v, want %v", body.Sales, 7984.316555463484)
		}
	})

	t.Run("ヘルスチェックは学習後に ok になる", func(t *testing.T) {
		t.Parallel()

		service := chapter15.NewPredictionService(trainedStore(t))

		for _, model := range service.Health() {
			if !model.Ready {
				t.Errorf("モデル %s = 読み込めない, want 読み込める", model.Name)
			}
		}
	})
}
