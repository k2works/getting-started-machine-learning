---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・外れ値の検出・文字コードの違う表の結合を Go で実装し、gonum の統計関数と突き合わせながら特徴量が決定係数に与える影響を測る。"
tags: [article,getting-start-ml,go]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T13:20:00Z }
---

# 第 9 章: 特徴量エンジニアリング

## 9.1 はじめに

前の章までで、データを読み込み、欠損値を補完し、モデルを学習させて評価するという一通りの流れができました。この章では、その手前にある **特徴量エンジニアリング** を扱います。モデルに渡す数値を作り変えて、同じアルゴリズムでも当たるようにする仕事です。

扱うのは次の 5 つです。

| 技法 | 何をするか | なぜ必要か |
|------|-----------|-----------|
| ダミー変数 | カテゴリ値の列を 0 と 1 の列に変える | 数値しか受け取れないモデルにカテゴリを渡す |
| 標準化 | 列ごとに平均 0・標準偏差 1 にそろえる | 単位の違う列を同じ土俵に乗せる |
| 多項式特徴量 | 2 乗の項と交互作用の項を足す | 直線では表せない関係を線形回帰で表す |
| 外れ値の検出 | 四分位範囲から大きく離れた行を見つける | 少数の極端な行にモデルが引きずられるのを防ぐ |
| 表の結合 | 別のファイルの列を結合して特徴量を増やす | 手元のデータだけでは足りない情報を足す |

[Python 版の第 9 章](../python/09-feature-engineering.md) と同じ題材・同じ TODO リストで進めます。Go 版では 2 つの版と対比します。1 つは [TypeScript 版](../typescript/09-feature-engineering.md) で、ライブラリが限られる環境で自作を積み上げる立場が同じです。もう 1 つは [Java 版](../java/09-feature-engineering.md) で、同じ静的型付けのコンパイル言語が例外とジェネリクスでどう書くかを比べられます。

この章は、Go にとって珍しく **ライブラリを積極的に使える章** です。gonum には統計関数（`stat.Mean`・`stat.StdDev`）と行列（`mat`）があるので、標準化と線形回帰はライブラリと突き合わせられます。ただし後で見るように、gonum の `stat.StdDev` と scikit-learn の `StandardScaler` は **同じ「標準偏差」でも値が違います**。その食い違いをテストで確かめるところが、この章の Go ならではの見どころです。

## 9.2 題材とデータ

ボストンの住宅価格（`Boston.csv`）を使います。100 行 14 列で、`PRICE`（住宅価格の中央値）を予測する回帰の問題です。

| 列 | 意味 |
|----|------|
| CRIME | 犯罪発生率の水準（`high`・`low`・`very_low` のカテゴリ値） |
| ZN | 広い住宅地の割合 |
| INDUS | 非小売業の面積の割合 |
| RM | 1 戸あたりの平均部屋数 |
| LSTAT | 低所得者層の割合 |
| PTRATIO | 生徒と教師の比率 |
| PRICE | 住宅価格の中央値（正解） |

表の結合では、自転車の利用者数（`bike.tsv`、タブ区切りの UTF-8）と天気の対応表（`weather.csv`、**Shift_JIS**）を使います。文字コードが違うファイルを読むのは、Go では標準ライブラリだけではできません。この章で唯一、`gonum` 以外の依存（`golang.org/x/text`）を足します。

データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) と同じです。

## 9.3 TODO リストの作成

ほかの言語版と同じ TODO リストで進めます。

```text
- [ ] カテゴリ値の列をダミー変数に変える
- [ ] 特徴量を標準化する（自作）
- [ ] gonum の stat.Mean・stat.StdDev と突き合わせる
- [ ] 2 乗の項と交互作用の項（多項式特徴量）を作る
- [ ] 四分位範囲で外れ値を検出する
- [ ] 訓練データから正解の外れ値を取り除く
- [ ] 文字コードと区切り文字を指定して表を読み込む
- [ ] 天気の表を結合して集計する
- [ ] 線形回帰と決定係数を実装する
- [ ] 特徴量の組ごとに決定係数を比べる
```

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

`CRIME` は `high`・`low`・`very_low` の 3 つのカテゴリを取ります。これを 0 と 1 の列に変えますが、3 つのカテゴリに 3 列を割り当てると、1 つの列がほかの 2 列から決まってしまい（「どちらでもない = high」）、線形回帰の正規方程式が解けなくなります。そこで先頭のカテゴリを落とします（pandas の `get_dummies(drop_first=True)` と同じ）。

テストから書きます。

```go
func TestCategories(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		values []string
		want   []string
	}{
		{name: "辞書順に並べて先頭を落とす", values: []string{"low", "high", "very_low"}, want: []string{"low", "very_low"}},
		{name: "重複はまとめる", values: []string{"low", "low", "high", "high"}, want: []string{"low"}},
		{name: "空欄は数えない", values: []string{"high", "", "low"}, want: []string{"low"}},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			if got := chapter09.Categories(test.values); !reflect.DeepEqual(got, test.want) {
				t.Errorf("Categories() = %v, want %v", got, test.want)
			}
		})
	}
}
```

表駆動テストは Go の慣習です。Java 版の `@ParameterizedTest` や TypeScript 版の `it.each` に当たるものを、言語機能だけで書けます。ケースを足すのは構造体リテラルを 1 行足すだけです。

実装はこうなります。

```go
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
```

Java 版は `stream().filter().distinct().sorted().skip(1).toList()` の 1 式です。Go には Stream API がないので、ループと `slices` パッケージ（Go 1.21 以降）の組み合わせになります。行数は増えますが、「何をしているか」は `for` と `if` のまま読めます。

`unique[1:]` はスライスの先頭を落とす書き方です。`len(unique) == 0` の分岐を先に置かないと、空のスライスで `unique[1:]` が範囲外になります。

### 表に列を加える

第 2 章の `Table` と `Row` に対して、列を入れ替えた新しい表を作ります。`Row` の `cells` は非公開なので、列名で `Text` を呼び直して作り直します。

```go
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
```

`len(columns) == len(table.Columns)` は「1 つも取り除けなかった = 指定した列が無かった」という判定です。Java 版はここで例外を投げますが、Go では `error` を返します。返す値がないときも `chapter02.Table{}` というゼロ値を返さなければならないのが Go の作法で、呼び出し側は必ず `err` を先に見ます。

ループのたびに `err` を書く冗長さは、Go を書いていて一番よく言われる点です。ただし「どの操作が失敗しうるか」がコードの見た目にそのまま出るので、第 2 章で見たように、失敗したときのメッセージが読みやすくなる利点もあります。

## 9.5 特徴量を標準化する

### 標準化とは

列ごとに平均を引いて標準偏差で割り、平均 0・標準偏差 1 にそろえます。`RM`（部屋数、5〜8 程度）と `TAX`（固定資産税率、200〜700 程度）のように桁の違う列をそのまま線形モデルに渡すと、係数の大きさが単位に左右されて比べられません。

重要なのは **訓練データだけで平均と標準偏差を求める** ことです。テストデータの平均を使うと、テストデータの情報が学習に漏れます（リーク）。

### 平均と標準偏差を求める

テストから書きます。値は 1・3・5 の 3 件です。平均は 3、差の 2 乗の和は 8 なので、件数 3 で割って平方根を取ると √(8/3) = 1.633 になります。

```go
	t.Run("平均と母標準偏差を求める", func(t *testing.T) {
		t.Parallel()

		standardizer, err := chapter09.Fit(x)
		if err != nil {
			t.Fatalf("Fit() でエラー: %v", err)
		}

		if got, want := standardizer.Means["A"], 3.0; math.Abs(got-want) > 1e-12 {
			t.Errorf("平均 = %v, want %v", got, want)
		}

		// 母標準偏差は sqrt(8/3) = 1.632...、標本標準偏差なら 2 になる
		if got, want := standardizer.Stds["A"], math.Sqrt(8.0/3.0); math.Abs(got-want) > 1e-12 {
			t.Errorf("標準偏差 = %v, want %v", got, want)
		}
	})
```

構造体は「Fit で求めた平均と標準偏差を持ち、Transform で使い回すもの」にします。

```go
// Standardizer は列ごとの平均と母標準偏差（件数で割る標準偏差）で、平均 0・標準偏差 1 にそろえる。
// 訓練データで Fit し、同じ平均と標準偏差で訓練データとテストデータの両方を Transform する。
type Standardizer struct {
	// Columns は Fit に渡した特徴量の列。並びを保つために持つ
	Columns []string
	// Means は列名ごとの平均
	Means map[string]float64
	// Stds は列名ごとの母標準偏差。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする
	Stds map[string]float64
}
```

Java 版は `record Standardizer(Map<String, Double> means, Map<String, Double> stds)` で、コンストラクタで不変な `Map` に包み直しています。Go の構造体はフィールドが公開なら書き換えられてしまうので、「作ったら書き換えない」という約束をコメントで示すのが現実的な落とし所です。不変性を型で守りたければ、フィールドを非公開にしてアクセサを足すことになりますが、この章の用途では過剰です。

標準偏差そのものは、自分で書きます。

```go
// PopulationStdDev は母標準偏差。差の 2 乗の和を件数で割ってから平方根を取る。
func PopulationStdDev(values []float64) float64 {
	average := mean(values)
	sum := 0.0

	for _, value := range values {
		sum += (value - average) * (value - average)
	}

	return math.Sqrt(sum / float64(len(values)))
}
```

すべて同じ値の列は標準偏差が 0 になり、割ると `NaN` や `+Inf` が出ます。そこで `Fit` では 0 を 1 に置き換えて、標準化した値が 0 になるようにします（scikit-learn の `StandardScaler` と同じ扱いです）。

```go
		std := PopulationStdDev(values)
		if std == 0 {
			std = 1
		}
```

### gonum の統計関数と突き合わせる

ここが Go 版の要点です。gonum には `stat.Mean` と `stat.StdDev` があります。どちらも第 2 引数に重みを取り、重みが要らなければ `nil` を渡します。

```go
// GonumMean は gonum の stat.Mean で平均を求める。重みを使わないので nil を渡す。
func GonumMean(values []float64) float64 {
	return stat.Mean(values, nil)
}

// GonumStdDev は gonum の stat.StdDev で標準偏差を求める。
// gonum は件数から 1 を引いて割る標本標準偏差なので、自作の母標準偏差とは値が違う。
func GonumStdDev(values []float64) float64 {
	return stat.StdDev(values, nil)
}
```

実際に確かめます。1・3・5 の 3 件なら、差の 2 乗の和は 8 です。件数 3 で割れば √(8/3) = 1.633（母標準偏差）、件数 − 1 = 2 で割れば √4 = 2（標本標準偏差）です。テストで両方を突き合わせました。

```go
	t.Run("gonum の stat.StdDev は標本標準偏差なので自作の母標準偏差と食い違う", func(t *testing.T) {
		t.Parallel()

		sample := chapter09.GonumStdDev(values)
		if want := 2.0; math.Abs(sample-want) > 1e-12 {
			t.Errorf("GonumStdDev() = %v, want %v", sample, want)
		}

		if population := chapter09.PopulationStdDev(values); math.Abs(sample-population) < 1e-12 {
			t.Errorf("標本標準偏差 %v と母標準偏差 %v が一致してしまいました", sample, population)
		}
	})

	t.Run("件数で割り直すと母標準偏差と一致する", func(t *testing.T) {
		t.Parallel()

		n := float64(len(values))
		converted := chapter09.GonumStdDev(values) * math.Sqrt((n-1)/n)

		if got, want := converted, chapter09.PopulationStdDev(values); math.Abs(got-want) > 1e-12 {
			t.Errorf("換算した標準偏差 = %v, want %v", got, want)
		}
	})
```

この 3 つはすべて通ります。つまり **gonum の `stat.StdDev` は標本標準偏差（不偏分散の平方根、n − 1 で割る）** で、scikit-learn の `StandardScaler` や本シリーズのほかの言語版が使う **母標準偏差（n で割る）** とは違います。換算は √((n − 1) / n) を掛けるだけです。

知らずに `stat.StdDev` で標準化すると、ほかの言語版と数値が合わなくなります。実際に `Fit` の中身を `PopulationStdDev` から `GonumStdDev` に差し替えると、先ほどのテストがこう落ちます。

```text
--- FAIL: TestStandardizer (0.00s)
    --- FAIL: TestStandardizer/平均と母標準偏差を求める (0.00s)
        featureengineering_test.go:131: 標準偏差 = 2, want 1.632993161855452
FAIL
```

件数が増えれば差は縮みます。実データ（訓練 70 件）で標準化したあとの `RM` を測ると、自作の母標準偏差では 1.00 ちょうど、gonum の `stat.StdDev` では 1.0072 でした。√(70/69) = 1.00723 なので、ぴったり理屈どおりです。第 9 章の実行結果にもこの 2 つを並べて表示します。

**教訓**: ライブラリの「標準偏差」は、母標準偏差か標本標準偏差かを必ず確かめてから使う。Java 版が Tribuo の `MeanStdDevTransformation` と突き合わせたのと同じ目的の検証ですが、Go では **食い違いが見つかった** ので、自作の実装を最終実装のまま残し、gonum は「突き合わせて換算式を確かめる相手」として使いました。

### 変換する

```go
// TransformOne は 1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。
func (s Standardizer) TransformOne(features chapter02.Features) (chapter02.Features, error) {
	values := slices.Clone(features.Values)

	for i, column := range features.Columns {
		std, ok := s.Stds[column]
		if !ok {
			continue
		}

		values[i] = (values[i] - s.Means[column]) / std
	}

	return chapter02.NewFeatures(features.Columns, values)
}
```

`slices.Clone` で写してから書き換えます。Go のスライスは参照なので、これを忘れると渡された特徴量を壊します。Java 版の `Features` も `double[]` を持つので同じ注意が要りますが、Go はスライスを引数で受けると「呼び出し元と同じ配列を見ている」ことが見えにくいぶん、`Clone` を習慣にしておくのが安全です。

`map` から値を取り出す `std, ok := s.Stds[column]` は、Go で Option 型の代わりに使う「値と ok」の形です。第 2 章の `Row.Number` と同じ考え方です。

## 9.6 多項式特徴量を作る

### 2 乗の項と交互作用の項

線形回帰は直線しか引けませんが、`RM^2` や `RM × LSTAT` という列をあらかじめ作っておけば、その列に対しては直線でも、元の列に対しては曲線を引けます。

列の組は「重複を許して 2 つ選ぶ」組み合わせです。列が A・B なら (A,A)・(A,B)・(B,B) の 3 つで、scikit-learn の `PolynomialFeatures` と同じ順に並べます。

```go
// Pair は 2 つの列の組。Left と Right が同じなら 2 乗の項を表す。
type Pair struct {
	Left  string
	Right string
}

// Name は項の名前。scikit-learn の get_feature_names_out と同じ形（"RM^2"、"RM LSTAT"）にする。
func (p Pair) Name() string {
	if p.Left == p.Right {
		return p.Left + "^2"
	}

	return p.Left + " " + p.Right
}

// PairsWithReplacement は重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。
func PairsWithReplacement(columns []string) []Pair {
	pairs := make([]Pair, 0, len(columns)*(len(columns)+1)/2)

	for i, left := range columns {
		for _, right := range columns[i:] {
			pairs = append(pairs, Pair{Left: left, Right: right})
		}
	}

	return pairs
}
```

`columns[i:]` と書けるので、内側のループの添字を扱わずに済みます。`Pair` は比較可能な構造体（フィールドが文字列だけ）なので、テストで `reflect.DeepEqual` はもちろん `==` でも比べられます。Java 版の `record` に近い使い勝手です。

`Expand` は元の列の後ろに項を足し、`Select` は指定した列だけをその順で選びます。特徴量の組を比べるときは「いったん全部作って、使う項だけ選ぶ」という流れにすると、組み合わせを足すのが楽になります。

```go
	t.Run("元の列の後ろに 2 乗と交互作用の項を足す", func(t *testing.T) {
		t.Parallel()

		columns := []string{"A", "B"}
		x := []chapter02.Features{features(t, columns, 2, 3)}

		expanded, err := chapter09.Expand(x, columns)
		if err != nil {
			t.Fatalf("Expand() でエラー: %v", err)
		}

		wantColumns := []string{"A", "B", "A^2", "A B", "B^2"}
		if !reflect.DeepEqual(expanded[0].Columns, wantColumns) {
			t.Errorf("Columns = %v, want %v", expanded[0].Columns, wantColumns)
		}

		wantValues := []float64{2, 3, 4, 6, 9}
		if !reflect.DeepEqual(expanded[0].Values, wantValues) {
			t.Errorf("Values = %v, want %v", expanded[0].Values, wantValues)
		}
	})
```

スライスは `==` で比べられないので、`reflect.DeepEqual` を使います。ここは Go の型の弱点が出るところで、Java 版の `List.equals` や TypeScript 版の `toEqual` のように「中身の比較」が言語の標準でできません。

## 9.7 外れ値を検出する

四分位範囲（IQR）で外れ値を見つけます。第 1 四分位数 Q1 と第 3 四分位数 Q3 を求め、Q1 − 1.5 × (Q3 − Q1) より小さい値と、Q3 + 1.5 × (Q3 − Q1) より大きい値を外れ値とします。

分位数は、位置が値の間に来たら前後の値から線形補間します（pandas の `quantile` の既定と同じ）。

```go
// Quantile は分位数を求める。位置が値の間にあれば前後の値から線形補間する（pandas の quantile の既定と同じ）。
func Quantile(values []float64, q float64) float64 {
	sorted := slices.Clone(values)
	slices.Sort(sorted)

	position := float64(len(sorted)-1) * q
	lower := int(math.Floor(position))
	upper := int(math.Ceil(position))

	return sorted[lower] + (sorted[upper]-sorted[lower])*(position-float64(lower))
}
```

`slices.Sort`（Go 1.21 以降）は `sort.Float64s` より新しい書き方で、ジェネリクスで型ごとに書き分けずに済みます。ここでも `slices.Clone` で写してから並べ替えます。

外れ値を取り除くのは **訓練データだけ** です。テストデータから都合の悪い行を消すと、評価が甘くなります。

```go
// RemoveTargetOutliers は訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。
func RemoveTargetOutliers(
	split chapter02.TrainTestSplit[chapter02.Features, float64],
) chapter02.TrainTestSplit[chapter02.Features, float64] {
	outliers := IQROutliers(split.TTrain, DefaultK)
	kept := chapter02.TrainTestSplit[chapter02.Features, float64]{XTest: split.XTest, TTest: split.TTest}

	for i, outlier := range outliers {
		if outlier {
			continue
		}

		kept.XTrain = append(kept.XTrain, split.XTrain[i])
		kept.TTrain = append(kept.TTrain, split.TTrain[i])
	}

	return kept
}
```

第 2 章で作った `TrainTestSplit[X, T any]` がここで効きます。第 2 章では正解ラベルが文字列（アヤメの品種）でしたが、この章では `float64`（住宅価格）です。ジェネリクスのおかげで同じ型を使い回せます。Go 1.18 より前なら、`interface{}` と型アサーションか、型ごとのコピーになっていたところです。

## 9.8 表を結合して特徴量を増やす

### 文字コードと区切り文字

`bike.tsv` はタブ区切りの UTF-8、`weather.csv` は Shift_JIS です。Go の標準ライブラリには **Shift_JIS のデコーダがありません**。文字列は UTF-8 であることが前提の言語なので、ほかの文字コードは準標準の `golang.org/x/text` で変換します。

```go
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
	// 以下、区切り文字で分割して Table を組み立てる
```

`charset.NewDecoder().Reader(opened)` は「読みながら変換する」ラッパーです。Java 版が `Files.readAllLines(file, charset)` の 1 行で済むのに比べると準備が要りますが、`io.Reader` を包むという Go の基本の形なので、gzip でも暗号でも同じ書き方で差し込めます。

`defer func() { _ = opened.Close() }()` の `_ =` は、`Close` の戻り値を捨てることを明示するためです。`golangci-lint` の既定に含まれる `errcheck` は、戻り値の `error` を無視したコードを指摘します。読み捨ててよい場面でも「意図して捨てた」と書かせるのが Go の流儀です。

Java 版は文字コードが違うと `MalformedInputException` を投げますが、Go の `x/text` のデコーダは **壊れたバイトを U+FFFD（置換文字）に変える** ので、例外にならず読めてしまいます。実際に `weather.csv` を UTF-8 として読むと、こうなりました。

```text
    zzrepro_test.go:18: UTF-8 として読んだ天気: "����"
```

そこで「化けること」自体をテストにしています。エラーにならない以上、気づく仕組みを自分で作るしかありません。

```go
	t.Run("Shift_JIS の天気を UTF-8 として読むと列名が合わない", func(t *testing.T) {
		// （中略）文字コードが違っても読めてしまうが、日本語が化けるので天気の名前が一致しなくなる
		if got == "晴れ" {
			t.Error("Shift_JIS のファイルを UTF-8 として読んだのに化けませんでした")
		}
	})
```

### map による結合と集計

天気 ID をキーにした `map[string]Row` を作り、自転車の表の各行から引きます（内部結合）。天気の表に無い ID の行は残しません。

集計は `map` で合計と件数を持ち、最後に割ります。**Go の `map` は反復の順序が決まっていない**（意図的にランダム化されている）ので、表示の順を決めたいなら別途スライスで順序を持つ必要があります。ここでは「最初に現れた順」を `order` に保ち、最後に平均の多い順へ並べ替えました。

```go
	means := make([]WeatherMean, 0, len(order))
	for _, weather := range order {
		means = append(means, WeatherMean{Weather: weather, Mean: sums[weather] / float64(counts[weather])})
	}

	sort.SliceStable(means, func(i, j int) bool { return means[i].Mean > means[j].Mean })
```

Java 版は `LinkedHashMap` で順序を保てますが、Go の `map` にはその選択肢がありません。「順序が要るならスライス」というのが Go の答えです。出力の安定を求めるなら `sort.SliceStable` を使い、同じ平均のときの並びが実行ごとに変わらないようにします。

結果は次のとおりでした。

```text
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

晴れの日は雨の日の 2.7 倍ほど使われています。天気は明らかに利用者数の特徴量になります。

## 9.9 特徴量の効果を測る

### 線形回帰を gonum の行列で解く

決定係数を比べるために、線形回帰が要ります。第 7 章で作ったものと同じ考え方ですが、この章では **gonum の `mat` で正規方程式を解く** 形にしました。先頭に 1 の列を足した計画行列 X について、XᵀX β = Xᵀt を解きます。

```go
	x := mat.NewDense(len(rows), columns, design)

	var normal mat.SymDense

	normal.SymOuterK(1, x.T())

	var cholesky mat.Cholesky
	if ok := cholesky.Factorize(&normal); !ok {
		return LinearModel{}, fmt.Errorf("特徴量の列が互いに独立でないため、正規方程式を解けません")
	}
```

`SymOuterK(1, x.T())` は XᵀX を対称行列として作ります。対称だと分かっている行列をコレスキー分解すると、一般の連立方程式より速く、数値的にも安定します。

`Factorize` は成功可否を `bool` で返します。列が互いに独立でなければ（たとえば同じ値の列が 2 つあれば）XᵀX が正定値でなくなり、分解できません。Go らしく、これを `error` に変換して返します。

```text
    zzrepro_test.go:21: 線形回帰のエラー: 特徴量の列が互いに独立でないため、正規方程式を解けません
```

Java 版は Tribuo の `choleskyFactorization()` が `Optional` を返すので `orElseThrow` で例外にしていました。gonum は `bool`、Tribuo は `Optional`、どちらも「失敗しうること」を型で示していますが、Go ではそれを最終的に `error` へ寄せます。呼び出し側の書き方が一種類に揃うのが、Go の多値返却の利点です。

gonum の `stat` には `LinearRegression` もありますが、これは **単回帰（説明変数が 1 つ）専用** で、切片と傾きを返すだけです。この章のように何列もある重回帰には使えないので、`mat` で自分で解きます。「線形回帰があります」と書いてあっても、何ができるかは確かめてから使うという、9.5 の教訓と同じ話です。

### 特徴量の組ごとに比べる

`RM`・`LSTAT`・`PTRATIO` の 3 列を元に、3 つの組を比べます。

| 組 | 列数 | 内容 |
|----|------|------|
| 元の特徴量 | 3 | RM, LSTAT, PTRATIO |
| 2 乗の項を追加 | 6 | 上記 + RM^2, LSTAT^2, PTRATIO^2 |
| 交互作用の項も追加 | 9 | 上記 + RM LSTAT, RM PTRATIO, LSTAT PTRATIO |

`go run ./cmd/chapters chapter09` の実行結果です。

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
  gonum の stat.StdDev（標本標準偏差）: 1.0072
決定係数:
  元の特徴量（3 列）: 訓練 0.6214, テスト 0.6113
  2 乗の項を追加（6 列）: 訓練 0.7836, テスト 0.8142
  交互作用の項も追加（9 列）: 訓練 0.8178, テスト 0.6415
訓練データの PRICE の外れ値: 5 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.7494, テスト 0.8380
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

読み取れることは 3 つあります。

1. **2 乗の項は効く**。テストデータの決定係数が 0.6113 から 0.8142 に上がりました。部屋数や低所得者層の割合と価格の関係が直線ではなかった、ということです
2. **交互作用の項は行きすぎ**。訓練データは 0.7836 → 0.8178 と上がったのに、テストデータは 0.8142 → 0.6415 に落ちました。訓練データにだけ合う形（過学習）になっています。列を増やせばよいわけではありません
3. **外れ値を除くとさらに上がる**。訓練データの `PRICE` の外れ値 5 件を取り除くと、訓練は 0.7836 → 0.7494 と下がるのにテストは 0.8142 → 0.8380 に上がりました。極端な 5 件に引きずられていた分だけ、ふつうの物件に当たるようになったということです

ここで注意したいのは、**これらの数値はほかの言語版と一致しません**。訓練データとテストデータの分け方が、`math/rand` のシードと乱数の実装に依存するからです。Java の `java.util.Random`、Scala、C# の `Random` とは生成される並びが違うので、同じシード 0 でも別の 70 件が訓練データになります。同じ結論（2 乗の項は効き、交互作用はやりすぎ、外れ値を除くと上がる）が出ていることが確かめたかった点で、小数第 4 位まで合わせることではありません。

## 9.10 可視化について

散布図や箱ひげ図で外れ値と特徴量の関係を眺める話は、[Python 版の 9.10](../python/09-feature-engineering.md) と [Kotlin 版](../kotlin/09-feature-engineering.md) の可視化の節を参照してください。Go 版では Notebook と可視化の節を作りません（gonum には `plot` がありますが、本シリーズの Go 版の対比の軸から外れるため扱いません）。

## 9.11 リファクタリング

書き終えたあとで整理した点です。

- **`columnValues` を共有する**。「特徴量から 1 つの列の値を並べて取り出す」処理は `Fit` と表示の両方で要るので、パッケージ内の関数に切り出しました
- **`selectTerms` にまとめる**。`Expand` してから `Select` する流れが訓練データとテストデータで 2 回出てきたので、1 つの関数にしました
- **`Run` を分割する**。第 3 章までの `Run` は一本道でしたが、この章は表示することが多いので、`printSplit`・`printStandardized`・`printScores`・`printWeather` に分けました。`golangci-lint` の既定には関数の長さの検査はありませんが、テストで出力を固定している以上、どこを直せばどの行が変わるかが見えるほうが直しやすくなります
- **`writeLines` で `Fprintln` のエラー処理をまとめる**。`fmt.Fprintln` も `error` を返すので、行ごとに `if err != nil` を書くと本筋が埋もれます。可変長引数の関数にまとめました

検査は `gofmt -l .`（出力が空）・`go vet ./...`・`golangci-lint run`・`go test ./... -cover` の 4 つです。データが無い環境（`ML_DATA_DIR=/nonexistent go test ./...`）でも、実データのテストが `t.Skip` で飛ぶので成功します。

## 9.12 まとめ

- **ダミー変数**はカテゴリ値を 0 と 1 の列に変える。先頭のカテゴリを落とさないと、列が互いに独立でなくなり正規方程式が解けなくなる
- **標準化**は訓練データだけで平均と標準偏差を求め、テストデータにも同じ値を使う。gonum の `stat.StdDev` は **標本標準偏差（n − 1 で割る）** で、scikit-learn やほかの言語版の母標準偏差（n で割る）とは違う。√((n − 1) / n) を掛ければ換算できる。実データ（70 件）では 1.0072 と 1.0000 の差として現れた
- **多項式特徴量**は効くこともやりすぎることもある。2 乗の項でテストの決定係数が 0.6113 → 0.8142 に上がり、交互作用の項を足すと 0.6415 に落ちた
- **外れ値**は訓練データからだけ取り除く。5 件を除くとテストの決定係数が 0.8142 → 0.8380 に上がった
- **表の結合**では文字コードに注意する。Go の標準ライブラリに Shift_JIS のデコーダは無く、`golang.org/x/text` を足す。デコーダは例外を投げずに U+FFFD で置き換えるので、化けたことに気づくテストを自分で書く
- **Go の型と文法**では、`slices` パッケージ（`Clone`・`Sort`・`Contains`・`Concat`）がこの章の主役だった。ジェネリクスの `TrainTestSplit[X, T]` が、第 2 章の文字列ラベルと第 9 章の数値ラベルの両方に使い回せた
- **エラーの扱い**では、gonum の `bool`（`Factorize`）を `error` に変換して呼び出し側の書き方を揃えた。Java 版の `Optional` + `orElseThrow` と同じ意図を、例外の無い言語で実現している

次の章では、ロジスティック回帰とランダムフォレストを自作して、複数のモデルを同じインターフェースで比べます。gonum にはどちらも無いので、この章とは逆に「ライブラリに頼れない章」になります。
