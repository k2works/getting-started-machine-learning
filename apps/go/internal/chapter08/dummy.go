package chapter08

import (
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// DummyEncoder はカテゴリ値の列を、最初のカテゴリを除いたカテゴリごとの 0 と 1 の列（ダミー変数）にする前処理。
type DummyEncoder struct {
	Columns []string
}

// Fit は列ごとに、ダミー変数にするカテゴリ（並べ替えて最初のカテゴリを除いたもの）を求める。
func (d DummyEncoder) Fit(x chapter02.Table) (FittedTransformer, error) {
	dummies := make([]Dummy, 0, len(d.Columns))

	for _, column := range d.Columns {
		categories, err := categoriesOf(x, column)
		if err != nil {
			return nil, err
		}

		if len(categories) > 0 {
			categories = categories[1:]
		}

		dummies = append(dummies, Dummy{Column: column, Categories: categories})
	}

	return &FittedDummyEncoder{Dummies: dummies}, nil
}

// categoriesOf は列の値を重複なく並べ替えて返す。欠損値は除く。
func categoriesOf(x chapter02.Table, column string) ([]string, error) {
	found := make(map[string]bool)
	categories := make([]string, 0)

	for _, row := range x.Rows {
		missing, err := row.IsMissing(column)
		if err != nil {
			return nil, err
		}

		if missing {
			continue
		}

		value, err := row.Text(column)
		if err != nil {
			return nil, err
		}

		if !found[value] {
			found[value] = true

			categories = append(categories, value)
		}
	}

	slices.Sort(categories)

	return categories, nil
}

// Dummy は 1 つの列と、その列のダミー変数にするカテゴリ。
// map ではなくスライスで持つのは、列の順を保つため。
type Dummy struct {
	Column     string
	Categories []string
}

// FittedDummyEncoder は Fit で求めたカテゴリを持ち、どのデータにも同じダミー変数の列を作る。
type FittedDummyEncoder struct {
	Dummies []Dummy
}

// Transform は元の列を除き、ダミー変数の列を末尾に足す。
func (f *FittedDummyEncoder) Transform(x chapter02.Table) (chapter02.Table, error) {
	columns := slices.Clone(x.Columns)

	for _, dummy := range f.Dummies {
		columns = slices.DeleteFunc(columns, func(column string) bool { return column == dummy.Column })
		for _, category := range dummy.Categories {
			columns = append(columns, dummy.Column+"_"+category)
		}
	}

	rows := make([]chapter02.Row, 0, len(x.Rows))

	for _, row := range x.Rows {
		cells, err := cellsOf(x.Columns, row)
		if err != nil {
			return chapter02.Table{}, err
		}

		for _, dummy := range f.Dummies {
			value := cells[dummy.Column]
			for _, category := range dummy.Categories {
				cells[dummy.Column+"_"+category] = boolCell(value == category)
			}
		}

		rows = append(rows, chapter02.NewRow(cells))
	}

	return chapter02.Table{Columns: columns, Rows: rows}, nil
}

// boolCell は真偽を "1" と "0" のセルにする。
func boolCell(value bool) string {
	if value {
		return "1"
	}

	return "0"
}
