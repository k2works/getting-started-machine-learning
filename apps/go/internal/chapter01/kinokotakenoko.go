// Package chapter01 は、人間が決めたルールできのこ派・たけのこ派を判定する。
package chapter01

import (
	"fmt"
	"os"
	"strconv"
	"strings"
)

// bom は BOM（バイトオーダーマーク）の文字。
const bom = "\uFEFF"

// kinokoAgeGroup は「20 代ならきのこ派」というルールの年代。
const kinokoAgeGroup = 20

// Person は学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。
type Person struct {
	Height   int
	Weight   int
	AgeGroup int
	Faction  string
}

// Features は判定の手がかりになる特徴量。
type Features struct {
	Height   int
	Weight   int
	AgeGroup int
}

// LoadPeople は BOM 付きの UTF-8 の CSV を読み込み、列名で値を取り出して人物のリストにする。
func LoadPeople(csvFile string) ([]Person, error) {
	content, err := os.ReadFile(csvFile)
	if err != nil {
		return nil, fmt.Errorf("CSV を読めません: %w", err)
	}

	lines := strings.Split(strings.ReplaceAll(string(content), "\r\n", "\n"), "\n")
	index := make(map[string]int)

	for i, name := range strings.Split(strings.TrimPrefix(lines[0], bom), ",") {
		index[name] = i
	}

	people := make([]Person, 0, len(lines)-1)

	for _, line := range lines[1:] {
		if strings.TrimSpace(line) == "" {
			continue
		}

		person, err := toPerson(index, strings.Split(line, ","))
		if err != nil {
			return nil, err
		}

		people = append(people, person)
	}

	return people, nil
}

// toPerson は 1 行の値を Person にする。列が無い場合と数値でない場合はエラーを返す。
func toPerson(index map[string]int, values []string) (Person, error) {
	height, err := number(index, values, "身長")
	if err != nil {
		return Person{}, err
	}

	weight, err := number(index, values, "体重")
	if err != nil {
		return Person{}, err
	}

	ageGroup, err := number(index, values, "年代")
	if err != nil {
		return Person{}, err
	}

	faction, ok := text(index, values, "派閥")
	if !ok {
		return Person{}, fmt.Errorf("列がありません: %s", "派閥")
	}

	return Person{Height: height, Weight: weight, AgeGroup: ageGroup, Faction: faction}, nil
}

func number(index map[string]int, values []string, column string) (int, error) {
	cell, ok := text(index, values, column)
	if !ok {
		return 0, fmt.Errorf("列がありません: %s", column)
	}

	value, err := strconv.Atoi(cell)
	if err != nil {
		return 0, fmt.Errorf("%s を数値として読めません: %w", column, err)
	}

	return value, nil
}

func text(index map[string]int, values []string, column string) (string, bool) {
	position, ok := index[column]
	if !ok || position >= len(values) {
		return "", false
	}

	return values[position], true
}

// SplitFeaturesAndLabels は人物のリストを特徴量と正解ラベルに分ける。
func SplitFeaturesAndLabels(people []Person) ([]Features, []string) {
	features := make([]Features, len(people))
	labels := make([]string, len(people))

	for i, person := range people {
		features[i] = Features{Height: person.Height, Weight: person.Weight, AgeGroup: person.AgeGroup}
		labels[i] = person.Faction
	}

	return features, labels
}

// PredictByRule は人間が決めたルールで派閥を判定する。
func PredictByRule(features Features) string {
	if features.AgeGroup == kinokoAgeGroup {
		return "きのこ"
	}

	return "たけのこ"
}

// Accuracy は予測が正解ラベルと一致した割合を返す。件数が違えばエラーを返す。
func Accuracy(predictions, labels []string) (float64, error) {
	if len(predictions) != len(labels) {
		return 0, fmt.Errorf("予測と正解ラベルの件数が違います: %d と %d", len(predictions), len(labels))
	}

	correct := 0

	for i, prediction := range predictions {
		if prediction == labels[i] {
			correct++
		}
	}

	return float64(correct) / float64(len(labels)), nil
}
