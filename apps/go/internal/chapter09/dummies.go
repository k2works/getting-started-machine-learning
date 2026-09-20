// Package chapter09 は特徴量エンジニアリング（ダミー変数・標準化・多項式特徴量・外れ値・表の結合）を扱う。
package chapter09

import (
	"slices"
	"strings"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// Categories は空欄を除いたカテゴリを辞書順に並べ、先頭を除いて返す（pandas の drop_first=True と同じ）。
func Categories(values []string) []string {
	unique := make([]string, 0, len(values))

	for _, value := range values {
		if strings.TrimSpace(value) == "" || slices.Contains(unique, value) {
			continue
		}

		unique = append(unique, value)
	}

	slices.Sort(unique)

	if len(unique) == 0 {
		return []string{}
	}

	return unique[1:]
}

// Encode は列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。値が一致すれば "1"、それ以外は "0"。
func Encode(table chapter02.Table, column string, categories []string) (chapter02.Table, error) {
	columns := make([]string, 0, len(table.Columns)+len(categories))

	for _, name := range table.Columns {
		if name != column {
			columns = append(columns, name)
		}
	}

	if len(columns) == len(table.Columns) {
		return chapter02.Table{}, errColumnNotFound(column)
	}

	for _, category := range categories {
		columns = append(columns, column+"_"+category)
	}

	rows := make([]chapter02.Row, 0, len(table.Rows))

	for _, row := range table.Rows {
		encoded, err := encodeRow(row, table.Columns, column, categories)
		if err != nil {
			return chapter02.Table{}, err
		}

		rows = append(rows, encoded)
	}

	return chapter02.Table{Columns: columns, Rows: rows}, nil
}

func encodeRow(row chapter02.Row, columns []string, column string, categories []string) (chapter02.Row, error) {
	cells := make(map[string]string, len(columns)+len(categories))

	for _, name := range columns {
		value, err := row.Text(name)
		if err != nil {
			return chapter02.Row{}, err
		}

		if name != column {
			cells[name] = value
		}
	}

	value, err := row.Text(column)
	if err != nil {
		return chapter02.Row{}, err
	}

	for _, category := range categories {
		if category == value {
			cells[column+"_"+category] = "1"
		} else {
			cells[column+"_"+category] = "0"
		}
	}

	return chapter02.NewRow(cells), nil
}
