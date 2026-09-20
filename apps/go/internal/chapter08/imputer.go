package chapter08

import (
	"slices"
	"strconv"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// GroupMedianImputer は数値の列の欠損値を、同じグループ（By の列の値の組）の中央値で補完する前処理。
type GroupMedianImputer struct {
	Column string
	By     []string
}

// Fit はグループごとの中央値と、全体の中央値を求める。
func (g GroupMedianImputer) Fit(x chapter02.Table) (FittedTransformer, error) {
	groups := make(map[string][]float64)
	all := make([]float64, 0, len(x.Rows))

	for _, row := range x.Rows {
		value, ok, err := row.Number(g.Column)
		if err != nil {
			return nil, err
		}

		if !ok {
			continue
		}

		key, err := groupKey(row, g.By)
		if err != nil {
			return nil, err
		}

		groups[key] = append(groups[key], value)
		all = append(all, value)
	}

	medians := make(map[string]float64, len(groups))

	for key, values := range groups {
		value, err := median(values)
		if err != nil {
			return nil, err
		}

		medians[key] = value
	}

	overall, err := median(all)
	if err != nil {
		return nil, err
	}

	return &FittedGroupMedianImputer{
		Column:  g.Column,
		By:      slices.Clone(g.By),
		Medians: medians,
		Overall: overall,
	}, nil
}

// FittedGroupMedianImputer は Fit で求めたグループごとの中央値と全体の中央値を持ち、欠損値を補完する。
// gob で保存するため、フィールドはすべて公開する。
type FittedGroupMedianImputer struct {
	Column  string
	By      []string
	Medians map[string]float64
	Overall float64
}

// Transform は欠損値をグループの中央値で補完する。グループが訓練データに無ければ全体の中央値を使う。
func (f *FittedGroupMedianImputer) Transform(x chapter02.Table) (chapter02.Table, error) {
	rows := make([]chapter02.Row, 0, len(x.Rows))

	for _, row := range x.Rows {
		missing, err := row.IsMissing(f.Column)
		if err != nil {
			return chapter02.Table{}, err
		}

		if !missing {
			rows = append(rows, row)

			continue
		}

		key, err := groupKey(row, f.By)
		if err != nil {
			return chapter02.Table{}, err
		}

		value, found := f.Medians[key]
		if !found {
			value = f.Overall
		}

		filled, err := withColumn(x.Columns, row, f.Column, strconv.FormatFloat(value, 'g', -1, 64))
		if err != nil {
			return chapter02.Table{}, err
		}

		rows = append(rows, filled)
	}

	return chapter02.Table{Columns: slices.Clone(x.Columns), Rows: rows}, nil
}

// MostFrequentImputer は文字列の列の欠損値を、訓練データで最も多い値（最頻値）で補完する前処理。
type MostFrequentImputer struct {
	Column string
}

// Fit は最頻値を求める。同数なら先に現れた値を選ぶ。
func (m MostFrequentImputer) Fit(x chapter02.Table) (FittedTransformer, error) {
	order := make([]string, 0, len(x.Rows))
	counts := make(map[string]int, len(x.Rows))

	for _, row := range x.Rows {
		missing, err := row.IsMissing(m.Column)
		if err != nil {
			return nil, err
		}

		if missing {
			continue
		}

		value, err := row.Text(m.Column)
		if err != nil {
			return nil, err
		}

		if _, ok := counts[value]; !ok {
			order = append(order, value)
		}

		counts[value]++
	}

	best, bestCount := "", 0

	for _, value := range order {
		if counts[value] > bestCount {
			best, bestCount = value, counts[value]
		}
	}

	return &FittedMostFrequentImputer{Column: m.Column, MostFrequent: best}, nil
}

// FittedMostFrequentImputer は Fit で求めた最頻値を持ち、欠損値を補完する。
type FittedMostFrequentImputer struct {
	Column       string
	MostFrequent string
}

// Transform は欠損値を最頻値で補完する。
func (f *FittedMostFrequentImputer) Transform(x chapter02.Table) (chapter02.Table, error) {
	rows := make([]chapter02.Row, 0, len(x.Rows))

	for _, row := range x.Rows {
		missing, err := row.IsMissing(f.Column)
		if err != nil {
			return chapter02.Table{}, err
		}

		if !missing {
			rows = append(rows, row)

			continue
		}

		filled, err := withColumn(x.Columns, row, f.Column, f.MostFrequent)
		if err != nil {
			return chapter02.Table{}, err
		}

		rows = append(rows, filled)
	}

	return chapter02.Table{Columns: slices.Clone(x.Columns), Rows: rows}, nil
}
