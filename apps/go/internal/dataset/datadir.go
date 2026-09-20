// Package dataset は学習データのディレクトリを求める。
package dataset

import (
	"os"
	"path/filepath"
)

// From は学習データのディレクトリを返す。環境変数 ML_DATA_DIR が無ければ
// apps/data/sukkiri-ml を使う。getenv はテストで差し替えるために引数で受け取る。
func From(getenv func(string) (string, bool)) string {
	if value, ok := getenv("ML_DATA_DIR"); ok && value != "" {
		return value
	}

	return filepath.Join("..", "data", "sukkiri-ml")
}

// Current は実行中のプロセスの環境変数から学習データのディレクトリを返す。
func Current() string {
	return From(os.LookupEnv)
}
