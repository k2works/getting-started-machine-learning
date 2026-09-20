package chapter08

import (
	"encoding/gob"
	"fmt"
	"os"
	"path/filepath"
)

// init は gob にインターフェースの実装を登録する。
// gob は interface の値を、登録した名前とともに書き出す。登録を忘れると保存も読み込みも失敗する。
func init() {
	gob.Register(Leaf{})
	gob.Register(Node{})
	gob.Register(&FittedGroupMedianImputer{})
	gob.Register(&FittedMostFrequentImputer{})
	gob.Register(&FittedDummyEncoder{})
}

// SavePipeline は学習済みのパイプライン（前処理で求めた値とモデル）を gob で保存する。
func SavePipeline(pipeline *FittedPipeline, modelFile string) error {
	if err := os.MkdirAll(filepath.Dir(modelFile), 0o750); err != nil {
		return fmt.Errorf("保存先を作れません: %w", err)
	}

	file, err := os.Create(modelFile)
	if err != nil {
		return fmt.Errorf("保存先を開けません: %w", err)
	}

	defer func() { _ = file.Close() }()

	if err := gob.NewEncoder(file).Encode(pipeline); err != nil {
		return fmt.Errorf("モデルを保存できません: %w", err)
	}

	return file.Close()
}

// LoadPipeline は保存したパイプラインを読み込む。
// gob は登録した型しか復元しないので、知らない型が含まれていればここで失敗する。
func LoadPipeline(modelFile string) (*FittedPipeline, error) {
	file, err := os.Open(filepath.Clean(modelFile))
	if err != nil {
		return nil, fmt.Errorf("モデルを開けません: %w", err)
	}

	defer func() { _ = file.Close() }()

	var pipeline FittedPipeline
	if err := gob.NewDecoder(file).Decode(&pipeline); err != nil {
		return nil, fmt.Errorf("モデルを読み込めません: %w", err)
	}

	return &pipeline, nil
}
