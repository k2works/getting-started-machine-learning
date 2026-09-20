package chapter08_test

import (
	"reflect"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter08"
)

// tableOf は列名と、行ごとの値の並びから表を作る。空文字列は欠損値。
func tableOf(columns []string, rows ...[]string) chapter02.Table {
	built := make([]chapter02.Row, 0, len(rows))

	for _, values := range rows {
		cells := make(map[string]string, len(columns))
		for i, column := range columns {
			cells[column] = values[i]
		}

		built = append(built, chapter02.NewRow(cells))
	}

	return chapter02.Table{Columns: columns, Rows: built}
}

// cellsOf は表の列の順に、1 行分のセルを取り出す。
func cellsOf(t *testing.T, table chapter02.Table, index int) []string {
	t.Helper()

	cells := make([]string, len(table.Columns))

	for i, column := range table.Columns {
		cell, err := table.Rows[index].Text(column)
		if err != nil {
			t.Fatalf("列を読めません: %v", err)
		}

		cells[i] = cell
	}

	return cells
}

func TestGroupMedianImputer(t *testing.T) {
	t.Parallel()

	// Pclass と Sex の組ごとに年齢の中央値が違う架空のデータ。4 行目の年齢が欠けている。
	columns := []string{"Pclass", "Sex", "Age"}
	train := tableOf(columns,
		[]string{"1", "female", "30"},
		[]string{"1", "female", "40"},
		[]string{"3", "male", "20"},
		[]string{"3", "male", ""},
		[]string{"3", "male", "24"},
	)

	imputer := chapter08.GroupMedianImputer{Column: "Age", By: []string{"Pclass", "Sex"}}

	fitted, err := imputer.Fit(train)
	if err != nil {
		t.Fatalf("Fit() でエラー: %v", err)
	}

	filled, err := fitted.Transform(train)
	if err != nil {
		t.Fatalf("Transform() でエラー: %v", err)
	}

	// 3 等客室の男性の年齢は 20 と 24 なので、中央値は 22。
	if got := cellsOf(t, filled, 3); !reflect.DeepEqual(got, []string{"3", "male", "22"}) {
		t.Errorf("補完後の行 = %v, want [3 male 22]", got)
	}

	// 欠けていない行はそのまま残る。
	if got := cellsOf(t, filled, 0); !reflect.DeepEqual(got, []string{"1", "female", "30"}) {
		t.Errorf("補完後の行 = %v, want [1 female 30]", got)
	}
}

func TestGroupMedianImputerUsesTrainMedianForOtherData(t *testing.T) {
	t.Parallel()

	columns := []string{"Pclass", "Sex", "Age"}
	train := tableOf(columns,
		[]string{"1", "female", "30"},
		[]string{"1", "female", "40"},
		[]string{"3", "male", "20"},
	)
	// テストデータにしかないグループ（2 等客室の男性）は、全体の中央値 30 で補完される。
	test := tableOf(columns,
		[]string{"1", "female", ""},
		[]string{"2", "male", ""},
	)

	fitted, err := chapter08.GroupMedianImputer{Column: "Age", By: []string{"Pclass", "Sex"}}.Fit(train)
	if err != nil {
		t.Fatalf("Fit() でエラー: %v", err)
	}

	filled, err := fitted.Transform(test)
	if err != nil {
		t.Fatalf("Transform() でエラー: %v", err)
	}

	if got := cellsOf(t, filled, 0); got[2] != "35" {
		t.Errorf("1 等客室の女性の年齢 = %v, want 35", got[2])
	}

	if got := cellsOf(t, filled, 1); got[2] != "30" {
		t.Errorf("2 等客室の男性の年齢 = %v, want 30", got[2])
	}
}

func TestMostFrequentImputer(t *testing.T) {
	t.Parallel()

	columns := []string{"Embarked"}
	train := tableOf(columns, []string{"S"}, []string{"C"}, []string{"S"}, []string{""})

	fitted, err := chapter08.MostFrequentImputer{Column: "Embarked"}.Fit(train)
	if err != nil {
		t.Fatalf("Fit() でエラー: %v", err)
	}

	filled, err := fitted.Transform(train)
	if err != nil {
		t.Fatalf("Transform() でエラー: %v", err)
	}

	if got := cellsOf(t, filled, 3); got[0] != "S" {
		t.Errorf("補完後の値 = %v, want S", got[0])
	}
}

func TestDummyEncoder(t *testing.T) {
	t.Parallel()

	columns := []string{"Sex", "Age"}
	train := tableOf(columns, []string{"female", "30"}, []string{"male", "20"})

	fitted, err := chapter08.DummyEncoder{Columns: []string{"Sex"}}.Fit(train)
	if err != nil {
		t.Fatalf("Fit() でエラー: %v", err)
	}

	encoded, err := fitted.Transform(train)
	if err != nil {
		t.Fatalf("Transform() でエラー: %v", err)
	}

	// 並べ替えると female・male なので、最初の female を除いて Sex_male だけが残る。
	if !reflect.DeepEqual(encoded.Columns, []string{"Age", "Sex_male"}) {
		t.Fatalf("列 = %v, want [Age Sex_male]", encoded.Columns)
	}

	if got := cellsOf(t, encoded, 0); !reflect.DeepEqual(got, []string{"30", "0"}) {
		t.Errorf("女性の行 = %v, want [30 0]", got)
	}

	if got := cellsOf(t, encoded, 1); !reflect.DeepEqual(got, []string{"20", "1"}) {
		t.Errorf("男性の行 = %v, want [20 1]", got)
	}
}

func TestDummyEncoderMakesSameColumnsForOtherData(t *testing.T) {
	t.Parallel()

	columns := []string{"Sex"}
	train := tableOf(columns, []string{"female"}, []string{"male"})
	// テストデータに女性しかいなくても、訓練データで決めた列を作る。
	test := tableOf(columns, []string{"female"})

	fitted, err := chapter08.DummyEncoder{Columns: []string{"Sex"}}.Fit(train)
	if err != nil {
		t.Fatalf("Fit() でエラー: %v", err)
	}

	encoded, err := fitted.Transform(test)
	if err != nil {
		t.Fatalf("Transform() でエラー: %v", err)
	}

	if !reflect.DeepEqual(encoded.Columns, []string{"Sex_male"}) {
		t.Errorf("列 = %v, want [Sex_male]", encoded.Columns)
	}

	if got := cellsOf(t, encoded, 0); got[0] != "0" {
		t.Errorf("Sex_male = %v, want 0", got[0])
	}
}
