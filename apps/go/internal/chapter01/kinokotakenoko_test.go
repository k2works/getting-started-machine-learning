package chapter01_test

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter01"
)

const header = "\uFEFF身長,体重,年代,派閥\n"

func writeCSV(t *testing.T, rows string) string {
	t.Helper()

	path := filepath.Join(t.TempDir(), "kvst.csv")
	if err := os.WriteFile(path, []byte(header+rows), 0o600); err != nil {
		t.Fatalf("CSV を書けません: %v", err)
	}

	return path
}

func TestLoadPeople(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		rows string
		want []chapter01.Person
	}{
		{
			name: "BOM 付き CSV を読み込んで人物のリストを返す",
			rows: "165,58,30,きのこ\n",
			want: []chapter01.Person{{Height: 165, Weight: 58, AgeGroup: 30, Faction: "きのこ"}},
		},
		{
			name: "複数行の CSV を読み込んで行の順に人物のリストを返す",
			rows: "161,52,20,きのこ\n183,74,50,たけのこ\n",
			want: []chapter01.Person{
				{Height: 161, Weight: 52, AgeGroup: 20, Faction: "きのこ"},
				{Height: 183, Weight: 74, AgeGroup: 50, Faction: "たけのこ"},
			},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			got, err := chapter01.LoadPeople(writeCSV(t, test.rows))
			if err != nil {
				t.Fatalf("LoadPeople() でエラー: %v", err)
			}

			if !reflect.DeepEqual(got, test.want) {
				t.Errorf("LoadPeople() = %v, want %v", got, test.want)
			}
		})
	}
}

func TestSplitFeaturesAndLabels(t *testing.T) {
	t.Parallel()

	people := []chapter01.Person{
		{Height: 161, Weight: 52, AgeGroup: 20, Faction: "きのこ"},
		{Height: 183, Weight: 74, AgeGroup: 50, Faction: "たけのこ"},
	}

	features, labels := chapter01.SplitFeaturesAndLabels(people)

	wantFeatures := []chapter01.Features{{Height: 161, Weight: 52, AgeGroup: 20}, {Height: 183, Weight: 74, AgeGroup: 50}}
	if !reflect.DeepEqual(features, wantFeatures) {
		t.Errorf("特徴量 = %v, want %v", features, wantFeatures)
	}

	if want := []string{"きのこ", "たけのこ"}; !reflect.DeepEqual(labels, want) {
		t.Errorf("正解ラベル = %v, want %v", labels, want)
	}
}

func TestPredictByRule(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name     string
		features chapter01.Features
		want     string
	}{
		{name: "20 代ならきのこ派と判定する", features: chapter01.Features{Height: 161, Weight: 52, AgeGroup: 20}, want: "きのこ"},
		{name: "20 代以外ならたけのこ派と判定する", features: chapter01.Features{Height: 183, Weight: 74, AgeGroup: 50}, want: "たけのこ"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if got := chapter01.PredictByRule(test.features); got != test.want {
				t.Errorf("PredictByRule() = %q, want %q", got, test.want)
			}
		})
	}
}

func TestAccuracy(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name        string
		predictions []string
		labels      []string
		want        float64
		wantErr     bool
	}{
		{
			name:        "すべての予測が正解なら正解率は 1",
			predictions: []string{"きのこ", "たけのこ"},
			labels:      []string{"きのこ", "たけのこ"},
			want:        1.0,
		},
		{
			name:        "4 件中 3 件の予測が正解なら正解率は 0.75",
			predictions: []string{"きのこ", "きのこ", "たけのこ", "たけのこ"},
			labels:      []string{"きのこ", "たけのこ", "たけのこ", "たけのこ"},
			want:        0.75,
		},
		{
			name:        "予測と正解ラベルの件数が違えばエラーになる",
			predictions: []string{"きのこ"},
			labels:      []string{"きのこ", "たけのこ"},
			wantErr:     true,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			got, err := chapter01.Accuracy(test.predictions, test.labels)
			if test.wantErr {
				if err == nil {
					t.Fatal("エラーを期待したが nil だった")
				}

				return
			}

			if err != nil {
				t.Fatalf("Accuracy() でエラー: %v", err)
			}

			if got != test.want {
				t.Errorf("Accuracy() = %v, want %v", got, test.want)
			}
		})
	}
}
