package chapter15

import (
	"fmt"
	"io"
	"net/http"
	"path/filepath"
	"time"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter08"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/dataset"
)

const (
	testSize = 0.2
	seed     = 0
	maxDepth = 5
	// Port は API を待ち受けるポート。
	Port = 8015
	// readHeaderTimeout は要求のヘッダーを読む制限時間（gosec の G112 を避けるためにも必要）。
	readHeaderTimeout = 10 * time.Second
)

// ModelDir は学習済みモデルの保存先（apps/go/model/ は .gitignore の対象）。
const ModelDir = "model"

// TrainAndSaveModels は第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。
func TrainAndSaveModels(dataDir string, store *FileModelStore) error {
	cinema, err := chapter07.PrepareCinema(filepath.Join(dataDir, "cinema.csv"), testSize, seed)
	if err != nil {
		return err
	}

	salesModel, err := chapter07.Fit(cinema.XTrain, cinema.TTrain)
	if err != nil {
		return err
	}

	if err := store.SaveSalesModel(salesModel); err != nil {
		return err
	}

	table, err := chapter02.LoadTable(filepath.Join(dataDir, "Survived.csv"))
	if err != nil {
		return err
	}

	labels, err := chapter08.SurvivedLabels(table.Rows)
	if err != nil {
		return err
	}

	split, err := chapter02.SplitTrainTest(table.Rows, labels, testSize, seed)
	if err != nil {
		return err
	}

	pipeline := chapter08.BuildPipeline(maxDepth, chapter08.WeightBalanced)

	fitted, err := pipeline.Fit(chapter08.FeaturesTable(split.XTrain), split.TTrain)
	if err != nil {
		return err
	}

	return store.SaveSurvivalModel(fitted)
}

// Run はモデルを学習して保存し、予測 API を起動する。
func Run(out io.Writer) error {
	store := NewFileModelStore(ModelDir)
	if err := TrainAndSaveModels(dataset.Current(), store); err != nil {
		return err
	}

	service := NewPredictionService(store)

	for _, model := range service.Health() {
		if _, err := fmt.Fprintf(out, "モデル %s: %v\n", model.Name, model.Ready); err != nil {
			return fmt.Errorf("表示できません: %w", err)
		}
	}

	if _, err := fmt.Fprintf(out, "http://localhost:%d で待ち受けます\n", Port); err != nil {
		return fmt.Errorf("表示できません: %w", err)
	}

	server := &http.Server{
		Addr:              fmt.Sprintf("127.0.0.1:%d", Port),
		Handler:           NewHandler(service),
		ReadHeaderTimeout: readHeaderTimeout,
	}

	return server.ListenAndServe()
}
