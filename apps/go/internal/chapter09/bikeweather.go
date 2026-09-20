package chapter09

import (
	"fmt"
	"io"
	"os"
	"slices"
	"sort"
	"strings"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"golang.org/x/text/encoding"
	"golang.org/x/text/encoding/japanese"
	"golang.org/x/text/encoding/unicode"
)

// weatherKey は自転車の表と天気の表を結合するキー。
const weatherKey = "weather_id"

// UTF8 は変換しない文字コード。bike.tsv に使う。
var UTF8 encoding.Encoding = unicode.UTF8

// ShiftJIS は weather.csv の文字コード。
var ShiftJIS encoding.Encoding = japanese.ShiftJIS

// LoadDelimited は文字コードと区切り文字を指定して、区切り文字で区切ったファイルを表に読み込む。
// 1 行目を列名として扱う。
func LoadDelimited(file string, charset encoding.Encoding, delimiter string) (chapter02.Table, error) {
	opened, err := os.Open(file)
	if err != nil {
		return chapter02.Table{}, fmt.Errorf("ファイルを開けません: %w", err)
	}

	defer func() { _ = opened.Close() }()

	content, err := io.ReadAll(charset.NewDecoder().Reader(opened))
	if err != nil {
		return chapter02.Table{}, fmt.Errorf("%s を読めません: %w", file, err)
	}

	lines := strings.Split(strings.ReplaceAll(string(content), "\r\n", "\n"), "\n")
	columns := strings.Split(strings.TrimPrefix(lines[0], "\uFEFF"), delimiter)
	rows := make([]chapter02.Row, 0, len(lines)-1)

	for _, line := range lines[1:] {
		if strings.TrimSpace(line) == "" {
			continue
		}

		values := strings.Split(line, delimiter)
		cells := make(map[string]string, len(columns))

		for i, name := range columns {
			if i < len(values) {
				cells[name] = values[i]
			} else {
				cells[name] = ""
			}
		}

		rows = append(rows, chapter02.NewRow(cells))
	}

	return chapter02.Table{Columns: columns, Rows: rows}, nil
}

// JoinWeather は天気 ID をキーにした対応表を引いて、天気の列を加える（内部結合）。
// 天気の表に無い ID の行は残さない。
func JoinWeather(bike, weather chapter02.Table) (chapter02.Table, error) {
	byID := make(map[string]chapter02.Row, len(weather.Rows))

	for _, row := range weather.Rows {
		id, err := row.Text(weatherKey)
		if err != nil {
			return chapter02.Table{}, err
		}

		byID[id] = row
	}

	added := make([]string, 0, len(weather.Columns))

	for _, column := range weather.Columns {
		if column != weatherKey {
			added = append(added, column)
		}
	}

	rows := make([]chapter02.Row, 0, len(bike.Rows))

	for _, row := range bike.Rows {
		id, err := row.Text(weatherKey)
		if err != nil {
			return chapter02.Table{}, err
		}

		found, ok := byID[id]
		if !ok {
			continue
		}

		joined, err := joinRow(row, bike.Columns, found, added)
		if err != nil {
			return chapter02.Table{}, err
		}

		rows = append(rows, joined)
	}

	columns := slices.Concat(bike.Columns, added)

	return chapter02.Table{Columns: columns, Rows: rows}, nil
}

func joinRow(row chapter02.Row, columns []string, found chapter02.Row, added []string) (chapter02.Row, error) {
	cells := make(map[string]string, len(columns)+len(added))

	for _, column := range columns {
		value, err := row.Text(column)
		if err != nil {
			return chapter02.Row{}, err
		}

		cells[column] = value
	}

	for _, column := range added {
		value, err := found.Text(column)
		if err != nil {
			return chapter02.Row{}, err
		}

		cells[column] = value
	}

	return chapter02.NewRow(cells), nil
}

// WeatherMean は天気と、その天気の日の平均利用者数。
type WeatherMean struct {
	Weather string
	Mean    float64
}

// MeanCountByWeather は天気ごとの平均利用者数を、多い順に並べて返す。
func MeanCountByWeather(joined chapter02.Table) ([]WeatherMean, error) {
	order := make([]string, 0)
	sums := make(map[string]float64)
	counts := make(map[string]int)

	for _, row := range joined.Rows {
		weather, err := row.Text("weather")
		if err != nil {
			return nil, err
		}

		count, ok, err := row.Number("cnt")
		if err != nil {
			return nil, err
		}

		if !ok {
			return nil, fmt.Errorf("利用者数が空欄です")
		}

		if _, found := sums[weather]; !found {
			order = append(order, weather)
		}

		sums[weather] += count
		counts[weather]++
	}

	means := make([]WeatherMean, 0, len(order))
	for _, weather := range order {
		means = append(means, WeatherMean{Weather: weather, Mean: sums[weather] / float64(counts[weather])})
	}

	sort.SliceStable(means, func(i, j int) bool { return means[i].Mean > means[j].Mean })

	return means, nil
}
