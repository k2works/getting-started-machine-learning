package chapter15

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter08"
)

const (
	// SalesModelName は興行収入のモデルの名前。
	SalesModelName = "cinema"
	// SurvivalModelName は生存予測のモデルの名前。
	SurvivalModelName = "survived"
)

// FileModelStore は学習済みモデルをディレクトリのファイルに保存し、読み込む。
type FileModelStore struct {
	ModelDir string
}

// NewFileModelStore は保存先のディレクトリを指定した置き場を作る。
func NewFileModelStore(modelDir string) *FileModelStore {
	return &FileModelStore{ModelDir: modelDir}
}

// salesModelFile は興行収入のモデルのファイル。第 7 章のモデルは数値だけなので JSON にする。
func (s *FileModelStore) salesModelFile() string {
	return filepath.Join(s.ModelDir, SalesModelName+".json")
}

// survivalModelFile は生存予測のモデルのファイル。前処理を含むので第 8 章と同じ gob にする。
func (s *FileModelStore) survivalModelFile() string {
	return filepath.Join(s.ModelDir, SurvivalModelName+".gob")
}

// SaveSalesModel は線形回帰のモデルを JSON で保存する。
func (s *FileModelStore) SaveSalesModel(model chapter07.LinearModel) error {
	if err := os.MkdirAll(s.ModelDir, 0o750); err != nil {
		return fmt.Errorf("保存先を作れません: %w", err)
	}

	encoded, err := json.Marshal(model)
	if err != nil {
		return fmt.Errorf("モデルを JSON にできません: %w", err)
	}

	if err := os.WriteFile(s.salesModelFile(), encoded, 0o600); err != nil {
		return fmt.Errorf("モデルを保存できません: %w", err)
	}

	return nil
}

// SaveSurvivalModel は学習済みパイプラインを gob で保存する（第 8 章の SavePipeline）。
func (s *FileModelStore) SaveSurvivalModel(pipeline *chapter08.FittedPipeline) error {
	return chapter08.SavePipeline(pipeline, s.survivalModelFile())
}

// LoadSalesModel は興行収入のモデルを読み込む。ファイルが無ければ ErrModelNotFound を返す。
func (s *FileModelStore) LoadSalesModel() (SalesModel, error) {
	contents, err := os.ReadFile(s.salesModelFile())
	if err != nil {
		if os.IsNotExist(err) {
			return nil, ModelNotFound(SalesModelName)
		}

		return nil, fmt.Errorf("モデルを読み込めません: %w", err)
	}

	var model chapter07.LinearModel
	if err := json.Unmarshal(contents, &model); err != nil {
		return nil, fmt.Errorf("モデルの JSON が壊れています: %w", err)
	}

	return linearSalesModel{model: model}, nil
}

// LoadSurvivalModel は生存予測のパイプラインを読み込む。ファイルが無ければ ErrModelNotFound を返す。
func (s *FileModelStore) LoadSurvivalModel() (SurvivalModel, error) {
	if _, err := os.Stat(s.survivalModelFile()); err != nil {
		if os.IsNotExist(err) {
			return nil, ModelNotFound(SurvivalModelName)
		}

		return nil, fmt.Errorf("モデルを読み込めません: %w", err)
	}

	pipeline, err := chapter08.LoadPipeline(s.survivalModelFile())
	if err != nil {
		return nil, err
	}

	return pipelineSurvivalModel{pipeline: pipeline}, nil
}
