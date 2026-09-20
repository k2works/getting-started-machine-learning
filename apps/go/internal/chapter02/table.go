// Package chapter02 は CSV の表と、アヤメのデータの前処理を扱う。
package chapter02

import (
	"fmt"
	"os"
	"strconv"
	"strings"
)

// bom は BOM（バイトオーダーマーク）の文字。
const bom = "\uFEFF"

// Row は CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。
type Row struct {
	cells map[string]string
}

// NewRow はセルの対応表から行を作る。渡した map を写すので、後から変えても影響しない。
func NewRow(cells map[string]string) Row {
	copied := make(map[string]string, len(cells))
	for name, value := range cells {
		copied[name] = value
	}

	return Row{cells: copied}
}

// Number は数値の列を読む。空欄なら ok が false になる。
func (r Row) Number(column string) (float64, bool, error) {
	cell, err := r.Text(column)
	if err != nil {
		return 0, false, err
	}

	if strings.TrimSpace(cell) == "" {
		return 0, false, nil
	}

	value, err := strconv.ParseFloat(cell, 64)
	if err != nil {
		return 0, false, fmt.Errorf("%s を数値として読めません: %w", column, err)
	}

	return value, true, nil
}

// Text は文字列の列を読む。列が無ければエラーを返す。
func (r Row) Text(column string) (string, error) {
	cell, ok := r.cells[column]
	if !ok {
		return "", fmt.Errorf("列がありません: %s", column)
	}

	return cell, nil
}

// IsMissing はセルが空欄かどうかを返す。
func (r Row) IsMissing(column string) (bool, error) {
	cell, err := r.Text(column)
	if err != nil {
		return false, err
	}

	return strings.TrimSpace(cell) == "", nil
}

// Table は列名の並びと行のリスト。データフレームのライブラリの代わりに使う。
type Table struct {
	Columns []string
	Rows    []Row
}

// Missing は列名と欠損値の数の組。列の順に並べて返す。
type Missing struct {
	Column string
	Count  int
}

// LoadTable は BOM 付きの UTF-8 の CSV を読み込む。
func LoadTable(csvFile string) (Table, error) {
	content, err := os.ReadFile(csvFile)
	if err != nil {
		return Table{}, fmt.Errorf("CSV を読めません: %w", err)
	}

	lines := strings.Split(strings.ReplaceAll(string(content), "\r\n", "\n"), "\n")
	columns := strings.Split(strings.TrimPrefix(lines[0], bom), ",")
	rows := make([]Row, 0, len(lines)-1)

	for _, line := range lines[1:] {
		if strings.TrimSpace(line) == "" {
			continue
		}

		values := strings.Split(line, ",")
		cells := make(map[string]string, len(columns))

		for i, name := range columns {
			if i < len(values) {
				cells[name] = values[i]
			} else {
				cells[name] = ""
			}
		}

		rows = append(rows, NewRow(cells))
	}

	return Table{Columns: columns, Rows: rows}, nil
}

// CountMissing は列ごとの欠損値の数を、列の順に並べて返す。
func (t Table) CountMissing() ([]Missing, error) {
	counts := make([]Missing, 0, len(t.Columns))

	for _, column := range t.Columns {
		count := 0

		for _, row := range t.Rows {
			missing, err := row.IsMissing(column)
			if err != nil {
				return nil, err
			}

			if missing {
				count++
			}
		}

		counts = append(counts, Missing{Column: column, Count: count})
	}

	return counts, nil
}
