---
type: Article
title: "第 8 章: 実践的な分類と前処理パイプライン"
description: "タイタニック号の乗客データを題材に、グループ中央値・最頻値の補完とダミー変数化をインターフェースでつなぐ前処理パイプラインと、クラスの重みを付けた決定木を Go で実装し、encoding/gob でモデルを保存する。"
tags: [article,getting-start-ml,go]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T15:40:00Z }
---

# 第 8 章: 実践的な分類と前処理パイプライン

## 8.1 はじめに

第 3 章のアヤメのデータは、すべての列が数値で、欠損値も 1 列にしかない、教科書的なデータでした。この章では、もっと現実に近いデータを扱います。タイタニック号の乗客名簿です。

このデータには、実務でよく出会う 3 つの困りごとが揃っています。

1. **欠損値が多い** — 年齢の約 2 割が空欄
2. **文字列の列がある** — 性別（male / female）、乗船した港（S / C / Q）
3. **クラスが偏っている** — 891 人のうち生存者は 342 人（38%）

前処理の手順が増えると、「訓練データで求めた値を、テストデータにも同じように使う」という約束を守るのが難しくなります。年齢の中央値をテストデータから計算してしまえば、その時点でデータリークです。この章では、前処理とモデルを **パイプライン** として 1 つにまとめ、この約束を型と構造で守らせます。

決定木は第 3 章で自作したものを拡張します。gonum に決定木はありません（[ADR 008](../../../adr/008-go-ml-libraries.md)）。この章にはライブラリとの突き合わせの節がなく、自作が最終実装です。同じ立場の [TypeScript 版の第 8 章](../typescript/08-classification-and-preprocessing-pipeline.md) と読み比べてください。比較の相手としては、`sealed interface` とレコードでパイプラインを組んだ [Java 版の第 8 章](../java/08-classification-and-preprocessing-pipeline.md) を使います。

## 8.2 題材とデータ

### Survived.csv

891 人の乗客について、次の列が記録されています。

| 列 | 内容 | 欠損値 |
|----|------|-------|
| PassengerId | 乗客の ID | なし |
| Survived | 生存したか（1 / 0） | なし |
| Pclass | 客室の等級（1 / 2 / 3） | なし |
| Sex | 性別（male / female） | なし |
| Age | 年齢 | あり |
| SibSp | 同乗した兄弟姉妹・配偶者の数 | なし |
| Parch | 同乗した親・子の数 | なし |
| Ticket | チケット番号 | なし |
| Fare | 運賃 | なし |
| Cabin | 客室番号 | 多い |
| Embarked | 乗船した港（S / C / Q） | あり |

### 使う特徴量と、使わない列

特徴量には `Pclass`・`Sex`・`Age`・`SibSp`・`Parch`・`Fare`・`Embarked` の 7 列を使います。`PassengerId`・`Ticket`・`Cabin` は使いません。ID とチケット番号は乗客を区別するための値で、生存と関係がありません。`Cabin` は欠損が多すぎます。

### 年齢はグループごとの中央値で補完する

第 2 章では、欠損値を列全体の平均値で補完しました。年齢はそれでは粗すぎます。1 等客室の乗客と 3 等客室の乗客では、年齢の分布が違うからです。そこで **客室の等級と性別の組ごとに中央値** を求めて補完します。平均値ではなく中央値にするのは、年齢のように偏った分布では、少数の高齢者に引っ張られにくいためです。

## 8.3 TODO リストの作成

**TODO リスト**:

- [ ] 前処理の部品をインターフェースで定義する
- [ ] 年齢をグループごとの中央値で補完する
  - [ ] グループごとに異なる中央値で補完する
  - [ ] 訓練データに無いグループは全体の中央値で補完する
- [ ] 乗船した港を最頻値で補完する
- [ ] カテゴリ値をダミー変数にする
  - [ ] 別のデータにも同じ列を作る
- [ ] クラスの重みを付けた決定木を作る
  - [ ] 重み付きのジニ不純度を求める
  - [ ] クラスの件数に反比例する重みを求める
- [ ] 前処理とモデルをパイプラインにつなぐ
- [ ] モデルを保存して読み込む
- [ ] 実データでクラスの重みの効果を確かめる

## 8.4 前処理の部品をインターフェースで定義する

前処理はどれも「訓練データから何かを学び（`Fit`）、その値でデータを変換する（`Transform`）」という同じ形をしています。この形をインターフェースにします。

```go
// internal/chapter08/transformer.go（抜粋）
// Transformer は訓練データから変換に必要な値を求める前処理。
type Transformer interface {
	Fit(x chapter02.Table) (FittedTransformer, error)
}

// FittedTransformer は Fit で求めた値を使ってデータを変換する前処理。
type FittedTransformer interface {
	Transform(x chapter02.Table) (chapter02.Table, error)
}
```

**インターフェースを 2 つに分けている** のが要点です。`Transformer` は「まだ何も学んでいない設定」、`FittedTransformer` は「学び終わって変換できる状態」を表します。この 2 つを 1 つの型にまとめ、`fit` を呼ばずに `transform` を呼べる設計にすると、「学習していない前処理で変換する」というバグが型では防げません。Java 版も同じ 2 段構えにしています。

Go のインターフェースは **暗黙的に実装** されます。`implements` と書く場所がありません。`Fit(chapter02.Table) (FittedTransformer, error)` というメソッドを持つ型は、自動的に `Transformer` です。この章では 3 つの前処理を作りますが、どれもインターフェースの名前をコードに書きません。

これは利点でもあり、落とし穴でもあります。メソッド名を 1 文字打ち間違えると、その型は静かにインターフェースを満たさなくなり、「使おうとした場所」で初めてコンパイルエラーになります。`var _ Transformer = GroupMedianImputer{}` という行を書いておけば、定義したファイルで検査できます。この章では、`BuildPipeline` が 3 つとも `[]Transformer` に入れるので、そこで検査されます。

### 行を書き換える

第 2 章の `Row` は不変です。セルの `map` は非公開で、外から書き換えられません。補完した値を入れるには、新しい行を作ることになります。

```go
// internal/chapter08/transformer.go（抜粋）
// withColumn は列の値を置き換えた（列が無ければ足した）新しい行を返す。元の行は変更しない。
func withColumn(columns []string, row chapter02.Row, column, value string) (chapter02.Row, error) {
	cells, err := cellsOf(columns, row)
	if err != nil {
		return chapter02.Row{}, err
	}

	cells[column] = value

	return chapter02.NewRow(cells), nil
}
```

Java 版の `Rows.with` と同じ役割です。違うのは、Go の `Row` にはセルを全部取り出すメソッドが無いので、表の列名の並びを渡して 1 列ずつ読むところです。「表の列」に無いセルは写されません。この章では、特徴量として使う 7 列だけを持つ表を作って渡すので、`Ticket` や `Cabin` は最初から落ちます。

## 8.5 年齢をグループごとの中央値で補完する

### グループのキーをどう表すか

「客室の等級と性別の組」ごとに中央値を求めます。Java 版はキーに `List<String>` を使いました。Go では **これができません**。`map` のキーは比較できる型でなければならず、スライスは比較できないからです。

```go
// これはコンパイルエラーになる
// medians := make(map[[]string]float64)
```

取れる手は 2 つです。1 つは要素数を固定した配列（`[2]string`）にすること。配列は比較できます。ただし、グループにする列の数を固定してしまいます。もう 1 つは **値を 1 つの文字列につなぐ** ことです。この章では後者を選びました。

```go
// internal/chapter08/transformer.go（抜粋）
// groupKey は複数の列の値を 1 つの文字列にまとめ、map のキーにできるようにする。
// Go の map のキーは比較できる型でなければならず、スライスは使えない。
func groupKey(row chapter02.Row, by []string) (string, error) {
	values := make([]string, len(by))

	for i, column := range by {
		value, err := row.Text(column)
		if err != nil {
			return "", err
		}

		values[i] = value
	}

	// 値そのものに現れない区切り文字（Unit Separator）でつなぐ。
	return strings.Join(values, "\x1f"), nil
}
```

区切り文字に `,` や `-` を使うと、`("a,b", "c")` と `("a", "b,c")` が同じキーになってしまいます。制御文字の `\x1f`（Unit Separator）なら、CSV のセルに現れることはまずありません。**キーを文字列に潰すときは、区切りの衝突を必ず考える** 必要があります。Java 版の `List<String>` にはこの問題がありませんでした。型の表現力を、Go では設計で埋め合わせています。

### テストファースト

架空の乗客 5 人で、グループごとに違う中央値が使われることを確かめます。

```go
// internal/chapter08/transformer_test.go（抜粋）
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
```

もし列全体の中央値を使っていたら、5 人の年齢 20・24・30・40 の中央値である 27 が入ります。22 と 27 は違う値なので、このテストは「グループごとに求めているか」を確かに区別します。

### 訓練データに無いグループ

テストデータにしかいないグループ（たとえば 2 等客室の男性が訓練データに 1 人もいない場合）に出会うと、中央値が引けません。ここで失敗させてしまうと、モデルが使えなくなります。全体の中央値を使って先に進みます。

```go
// internal/chapter08/transformer_test.go（抜粋）
	// テストデータにしかないグループ（2 等客室の男性）は、全体の中央値 30 で補完される。
	test := tableOf(columns,
		[]string{"1", "female", ""},
		[]string{"2", "male", ""},
	)
```

実装では、`Medians` に無ければ `Overall` を使うだけです。

```go
// internal/chapter08/imputer.go（抜粋）
		value, found := f.Medians[key]
		if !found {
			value = f.Overall
		}
```

`value, found := m[key]` という「カンマ ok」の形は、第 1 章から繰り返し出てきます。Go には Optional 型が無く、「無い」を表すのはこの 2 つ目の戻り値です。Java 版の `getOrDefault` に当たるものは標準ライブラリにありませんが、3 行で同じことが書けます。

### 補完した値を文字列に戻す

`Row` はセルを文字列で持つので、補完した中央値を文字列に戻さなければなりません。

```go
// internal/chapter08/imputer.go（抜粋）
		filled, err := withColumn(x.Columns, row, f.Column, strconv.FormatFloat(value, 'g', -1, 64))
```

`strconv.FormatFloat(value, 'g', -1, 64)` の `-1` は「元の `float64` に戻せる最小の桁数で書く」という意味です。`fmt.Sprintf("%v", value)` でもほぼ同じ結果になりますが、こちらは意図が明示的で、リフレクションも通りません。22 は `"22"` に、22.5 は `"22.5"` になります。Java 版の `String.valueOf(median)` は `"22.0"` になるので、文字列として比べると違いますが、`Number` で読み直せば同じ値です。

## 8.6 乗船した港を最頻値で補完する

港は文字列なので、中央値も平均値も意味がありません。最も多い値（最頻値）で埋めます。

```go
// internal/chapter08/imputer.go（抜粋）
// Fit は最頻値を求める。同数なら先に現れた値を選ぶ。
func (m MostFrequentImputer) Fit(x chapter02.Table) (FittedTransformer, error) {
	order := make([]string, 0, len(x.Rows))
	counts := make(map[string]int, len(x.Rows))

	for _, row := range x.Rows {
		// …（中略：欠損値を飛ばして数える）…
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
```

数えるのは `map` ですが、**選ぶときは `order` の順に見ます**。Go の `map` は反復の順序がランダム化されているので、`for value, count := range counts` で最大を探すと、同数のときにどれが選ばれるか実行のたびに変わります。テストが「たまに落ちる」いちばんよくある原因です。第 3 章の `Majority` も同じ形で書きました。

`counts[value]++` は、キーが無くても動きます。`map` から取り出した値は、キーが無ければ型のゼロ値（`int` なら 0）になるからです。Java 版の `merge(value, 1, Integer::sum)` に当たる処理が、Go では演算子 1 つで書けます。

## 8.7 カテゴリ値をダミー変数にする

決定木は数値しか扱えません。`male` / `female` を 0 と 1 の列に変えます。カテゴリが n 種類あるとき、作る列は n-1 個です。`Sex_male` が 0 なら女性だと分かるので、`Sex_female` は要りません（**ダミー変数の罠** を避ける定石です）。

どのカテゴリを落とすかは、訓練データで決めて固定します。テストデータに女性しかいなくても、同じ `Sex_male` の列を作らなければ、モデルが受け取る列がずれてしまいます。

```go
// internal/chapter08/transformer_test.go（抜粋）
	// テストデータに女性しかいなくても、訓練データで決めた列を作る。
	test := tableOf(columns, []string{"female"})
```

保持する形は、`map` ではなくスライスです。

```go
// internal/chapter08/dummy.go（抜粋）
// Dummy は 1 つの列と、その列のダミー変数にするカテゴリ。
// map ではなくスライスで持つのは、列の順を保つため。
type Dummy struct {
	Column     string
	Categories []string
}
```

Java 版は `LinkedHashMap<String, List<String>>` を使い、「`Map.copyOf` は順序を保たないので `LinkedHashMap` に写す」という注意書きを添えていました。Go にはそもそも順序を保つ `map` がないので、順序が要るなら最初からスライスにします。**選べないことが、設計をはっきりさせている** 例です。

列の付け替えは `slices` パッケージで書けます。

```go
// internal/chapter08/dummy.go（抜粋）
	for _, dummy := range f.Dummies {
		columns = slices.DeleteFunc(columns, func(column string) bool { return column == dummy.Column })
		for _, category := range dummy.Categories {
			columns = append(columns, dummy.Column+"_"+category)
		}
	}
```

`slices.DeleteFunc` は Go 1.21 で入った関数で、条件に合う要素を取り除きます。8.5 節の `groupKey` のように「エラーを返す述語」を渡す必要がない場面では、素直に高階関数が使えます。

## 8.8 クラスの重みを付けた決定木を作る

### なぜ自作するのか

gonum には決定木がありません。[ADR 008](../../../adr/008-go-ml-libraries.md) のとおり、Go 版は決定木・ランダムフォレスト・K-means・ロジスティック回帰を自作し、ライブラリとの突き合わせの節を省きます。第 7 章で線形回帰を gonum に任せたのと対照的です。「あるものは使い、無いものは作る」という線引きが、章によって見える形になります。

第 3 章の決定木をそのまま使えないのは、ラベルの型が違うからです。第 3 章の `Leaf` は `Label string` を持ちます。この章のラベルは 0 と 1 の整数です。

```go
// internal/chapter08/weightedtree.go（抜粋）
// Tree は重み付きの決定木。Leaf か Node のどちらか。
// 第 3 章の決定木はラベルが文字列なので、整数のラベルを持つ木をこの章で作り直す。
type Tree interface {
	isTree()
}

// Leaf は予測するラベルを持つ葉。
type Leaf struct {
	Label int
}

func (Leaf) isTree() {}

// Node は分割と、左右の部分木を持つ節。分割は第 3 章の Split をそのまま使う。
type Node struct {
	Split chapter03.Split
	Left  Tree
	Right Tree
}
```

ジェネリクスで `Tree[T comparable]` にすれば 1 つにまとめられそうに見えます。しかし第 3 章の `Tree` はすでにジェネリクスでない形で公開されていて、あとから型引数を足すのは互換性のない変更です。分割を表す `chapter03.Split` は、ラベルの型に依存しないのでそのまま使い回せます。**型引数を後付けできないときは、共有できる部分だけを共有する** のが現実的な落としどころです。

### 重み付きのジニ不純度

第 3 章のジニ不純度は「ラベルの件数の割合」から求めました。重み付きでは、件数の代わりに **重みの合計** を使います。

```go
// internal/chapter08/weightedtree_test.go（抜粋）
		{
			name:    "重みがすべて 1 なら第 3 章のジニ不純度と同じ",
			labels:  []int{0, 0, 1, 1},
			weights: []float64{1, 1, 1, 1},
			want:    0.5,
		},
		{
			name:    "少数派を重くすると不純度が上がる",
			labels:  []int{0, 0, 0, 1},
			weights: []float64{1, 1, 1, 3},
			want:    0.5,
		},
```

2 つ目のケースが重み付けの意味を表しています。重みなしなら、3 対 1 の偏りでジニ不純度は 0.375 です。少数派に重み 3 を与えると、重みの合計が 3 対 3 になり、0.5 に上がります。**不純度が上がるということは、この分割では「まだ混ざっている」と木が判断する** ということです。少数派を無視して多数派だけを当てにいく木が作られにくくなります。

```go
// internal/chapter08/weightedtree.go（抜粋）
// WeightedGini は重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。
func WeightedGini(labels []int, weights []float64) (float64, error) {
	order, sums, err := weightSums(labels, weights)
	if err != nil {
		return 0, err
	}

	total := 0.0
	for _, weight := range weights {
		total += weight
	}

	if total == 0 {
		return 0, fmt.Errorf("重みの合計が 0 です")
	}

	sum := 0.0

	for _, label := range order {
		share := sums[label] / total
		sum += share * share
	}

	return 1.0 - sum, nil
}
```

`weightSums` が「ラベルが先に現れた順のスライス」と「ラベルごとの合計の `map`」を両方返しているのは、8.6 節と同じ理由です。合計を求めるのは `map` が速く、順に見るにはスライスが要ります。

### balanced の重み

クラスの件数に反比例する重みです。件数 ÷（クラスの数 × そのクラスの件数）で求めます。

```go
// internal/chapter08/weightedtree_test.go（抜粋）
	// 生存 1 人、死亡 3 人。件数に反比例する重みは 4/(2*1)=2 と 4/(2*3)=0.667。
	got, err := chapter08.BalancedWeights([]int{0, 0, 0, 1})
```

この式だと、クラスごとの重みの合計がどのクラスでも同じ（件数 ÷ クラスの数）になります。scikit-learn の `class_weight="balanced"` と同じ定義です。

```go
// internal/chapter08/weightedtree.go（抜粋）
	weights := make([]float64, len(t))
	for i, label := range t {
		weights[i] = float64(len(t)) / float64(len(counts)*counts[label])
	}
```

`float64(...)` の変換が 2 つ要るのは、Go が整数と浮動小数点の暗黙の変換をしないからです。`len(t) / (len(counts) * counts[label])` と書くと整数の除算になり、0 になってしまいます。コンパイラは型が揃っているので何も言いません。**Go でいちばん静かに間違える場所** の 1 つです。

### 重みなしなら第 3 章と同じ

`DecisionTreeClassifier` は、重みの付け方を受け取り、すべて 1 の重みか、balanced の重みを作って `Build` に渡します。

```go
// internal/chapter08/classifier.go（抜粋）
// weightsOf は 1 件ごとの重みを求める。
func (d DecisionTreeClassifier) weightsOf(t []int) ([]float64, error) {
	if d.ClassWeight == WeightBalanced {
		return BalancedWeights(t)
	}

	weights := make([]float64, len(t))
	for i := range weights {
		weights[i] = 1
	}

	return weights, nil
}
```

`ClassWeight` は列挙ではなく、文字列の別名です。

```go
// internal/chapter08/classifier.go（抜粋）
type ClassWeight string

const (
	// WeightNone は重みを付けない（すべて 1）。
	WeightNone ClassWeight = "none"
	// WeightBalanced はクラスの件数に反比例する重みを付ける。
	WeightBalanced ClassWeight = "balanced"
)
```

Go に列挙型はありません。慣習として、専用の型を作って定数を並べます。`int` の別名にして `iota` で数えるやり方もありますが、文字列にしておくと、`fmt` でそのまま `classWeight=balanced` と表示できます。Java 版の `enum` と違って、`ClassWeight("typo")` という値も作れてしまうので、網羅性はコンパイラが保証しません。ここでは `weightsOf` が「balanced でなければ重みなし」と扱うので、未知の値でも壊れません。

## 8.9 前処理とモデルをパイプラインにつなぐ

`Pipeline` は前処理の並びと、モデルの設定を持ちます。

```go
// internal/chapter08/pipeline.go（抜粋）
// BuildPipeline は Survived.csv 用のパイプラインを作る。
// 年齢・港の補完とダミー変数化の後に、決定木で分類する。
func BuildPipeline(maxDepth int, classWeight ClassWeight) Pipeline {
	return Pipeline{
		Transformers: []Transformer{
			GroupMedianImputer{Column: "Age", By: []string{"Pclass", "Sex"}},
			MostFrequentImputer{Column: "Embarked"},
			DummyEncoder{Columns: []string{"Sex", "Embarked"}},
		},
		Model: DecisionTreeClassifier{MaxDepth: maxDepth, ClassWeight: classWeight},
	}
}
```

順序が大事です。**補完してからダミー変数にします**。逆にすると、欠損値のままの港がダミー変数の対象になり、どの列も 0 の行ができてしまいます。

`Fit` の肝は、**前の前処理で変換した結果を、次の前処理に渡す** ことです。ダミー変数化は、補完が済んだデータで `Fit` しなければなりません。

```go
// internal/chapter08/pipeline.go（抜粋）
	for _, transformer := range p.Transformers {
		fittedTransformer, err := transformer.Fit(prepared)
		if err != nil {
			return nil, err
		}

		fitted = append(fitted, fittedTransformer)

		prepared, err = fittedTransformer.Transform(prepared)
		if err != nil {
			return nil, err
		}
	}
```

Java 版は `FittedTransformer` を関数型インターフェースにして、`reduce` と `andThen` で合成しました。Go では、素直なループのほうが短く読めます。`prepared, err = ...` で `prepared` を更新しているのは、ループの外で宣言した変数に代入するためです。ここで `:=` を使うと、ループの中だけの新しい変数ができてしまい、次の反復に伝わりません。Go の `:=` と `=` の使い分けが効いてくる場面です。

### 型が「欠損値が残っていない」ことを保証する

学習の直前で、表を `Features` に変えます。`Features` は第 2 章で作った「欠損値を持てない型」です。

```go
// internal/chapter08/pipeline.go（抜粋）
		for i, column := range x.Columns {
			value, ok, err := row.Number(column)
			if err != nil {
				return nil, err
			}

			if !ok {
				return nil, fmt.Errorf("欠損値が残っています: %s", column)
			}

			values[i] = value
		}
```

前処理に漏れがあれば、モデルに渡る前にここで止まります。「補完し忘れた列がないか」を数えて確かめるテストは要りません。**型と変換の境界が検査そのものになっている** からです。Java 版・Kotlin 版と同じ考え方を、Go でも `Features` が担っています。

パイプラインのテストは、女性が全員生存・男性が全員死亡という架空の 6 人で書きました。前処理が正しく動いていれば、深さ 5 の決定木はこのデータを完全に当てられます。

```go
// internal/chapter08/pipeline_test.go（抜粋）
// trainingTable は架空の乗客 6 人の表。女性は全員生存、男性は全員死亡にしてある。
func trainingTable() (chapter02.Table, []int) {
	table := tableOf(chapter08.SurvivedFeatures,
		[]string{"1", "female", "30", "0", "0", "80", "C"},
		[]string{"1", "female", "", "0", "0", "70", "C"},
		[]string{"3", "male", "20", "0", "0", "8", "S"},
		[]string{"3", "male", "24", "0", "0", "7", ""},
		[]string{"2", "female", "40", "0", "0", "30", "S"},
		[]string{"2", "male", "35", "0", "0", "25", "S"},
	)

	return table, []int{1, 1, 0, 0, 1, 0}
}
```

2 行目の年齢と 4 行目の港をわざと空けてあります。補完が働かなければ、`ToFeatures` が「欠損値が残っています」で落ちます。

## 8.10 モデルを保存して読み込む

学習済みのパイプラインをファイルに保存して、あとから読み込めるようにします。Java 版は Java のシリアライズを使い、`serialVersionUID` と `ObjectInputFilter` の話をしました。Go の標準ライブラリには **`encoding/gob`** があります。

```go
// internal/chapter08/modelfile.go（抜粋）
// SavePipeline は学習済みのパイプライン（前処理で求めた値とモデル）を gob で保存する。
func SavePipeline(pipeline *FittedPipeline, modelFile string) error {
	if err := os.MkdirAll(filepath.Dir(modelFile), 0o750); err != nil {
		return fmt.Errorf("保存先を作れません: %w", err)
	}

	file, err := os.Create(modelFile)
	if err != nil {
		return fmt.Errorf("保存先を開けません: %w", err)
	}

	defer func() { _ = file.Close() }()

	if err := gob.NewEncoder(file).Encode(pipeline); err != nil {
		return fmt.Errorf("モデルを保存できません: %w", err)
	}

	return file.Close()
}
```

`defer` と、最後の `return file.Close()` が両方あるのが気になるかもしれません。ファイルを書き出すときは、`Close` が返すエラー（ディスクへの書き込みの失敗など）を捨ててはいけません。そのため、正常系では明示的に `Close` して結果を返し、途中でエラーになった場合に備えて `defer` も置いています。`Close` を 2 回呼ぶことになりますが、2 回目はエラーを返すだけで害がありません。`golangci-lint` の `errcheck` が、`defer file.Close()` と素で書くと指摘します。

### インターフェースを保存するには登録が要る

`FittedPipeline` は `[]FittedTransformer` と `Tree` というインターフェースを含みます。gob は、インターフェースの中身を書き出すとき「どの具体型か」を名前で記録します。その名前を先に教えておかなければなりません。

登録を忘れると、保存の時点で失敗します。実際に `gob.Register` の行を消して実行しました。

```console
$ go test ./internal/chapter08/ -run TestSavedPipelinePredictsTheSame
--- FAIL: TestSavedPipelinePredictsTheSame (0.00s)
    pipeline_test.go:90: SavePipeline() でエラー: モデルを保存できません: gob: type not registered for interface: chapter08.FittedGroupMedianImputer
FAIL
```

エラーメッセージが具体型の名前を教えてくれるので、直し方がすぐ分かります。

```go
// internal/chapter08/modelfile.go（抜粋）
// init は gob にインターフェースの実装を登録する。
// gob は interface の値を、登録した名前とともに書き出す。登録を忘れると保存も読み込みも失敗する。
func init() {
	gob.Register(Leaf{})
	gob.Register(Node{})
	gob.Register(&FittedGroupMedianImputer{})
	gob.Register(&FittedMostFrequentImputer{})
	gob.Register(&FittedDummyEncoder{})
}
```

`init` 関数は、パッケージが読み込まれるときに自動的に実行されます。使う側が登録を呼び忘れる余地がありません。

gob にはもう 1 つ約束があります。**公開されたフィールドしか保存されません**。非公開のフィールドは黙って落ちます。そのため、`FittedGroupMedianImputer` などの学習済みの型は、フィールドをすべて公開しています。

```go
// internal/chapter08/imputer.go（抜粋）
// FittedGroupMedianImputer は Fit で求めたグループごとの中央値と全体の中央値を持ち、欠損値を補完する。
// gob で保存するため、フィールドはすべて公開する。
type FittedGroupMedianImputer struct {
	Column  string
	By      []string
	Medians map[string]float64
	Overall float64
}
```

「隠したいが、保存もしたい」は gob では両立しません。`GobEncode`／`GobDecode` を自分で書けば隠せますが、この章の目的には公開で十分です。Java 版は `private final` のまま保存でき、代わりに `ObjectInputFilter` で「読み込んでよいクラス」を絞る必要がありました。

### 読み込みの安全性

Java のシリアライズは、ファイルに書かれた任意のクラスを復元しようとするため、細工されたファイルで任意のコードを実行されうる危険があります。Java 版が `ObjectInputFilter` でクラスを絞ったのはそのためです。

gob には、その危険がほとんどありません。登録していない型は復元されず、復元先の構造体も `Decode` に渡した型で決まっています。壊れたファイルを渡せば、エラーになります。

```go
// internal/chapter08/pipeline_test.go（抜粋）
	modelFile := filepath.Join(t.TempDir(), "broken.gob")
	if err := writeFile(t, modelFile, "これは gob ではありません"); err != nil {
		t.Fatalf("ファイルを書けません: %v", err)
	}

	if _, err := chapter08.LoadPipeline(modelFile); err == nil {
		t.Error("壊れたファイルを読み込めてしまいました")
	}
```

それでも、gob は「信頼できない入力」を前提にした形式ではありません。標準ライブラリの注意書きにも、悪意のある入力でリソースを使い果たす可能性があると書かれています。外部から受け取るモデルファイルを扱うなら、署名や出所の検証が別に要ります。

保存して読み込んだモデルが、元のモデルと同じ予測を返すことを確かめます。これが、保存が正しいことのいちばん直接的なテストです。

```go
// internal/chapter08/pipeline_test.go（抜粋）
	if !reflect.DeepEqual(before, after) {
		t.Errorf("読み込んだモデルの予測 = %v, want %v", after, before)
	}
```

保存先には `t.TempDir()` を使います。テストが終われば自動的に消え、並行に走るテストどうしもぶつかりません。`Run` が保存先を引数で受け取る `RunWithModelFile` を分けているのは、このためです。

## 8.11 実データでクラスの重みの効果を確かめる

### 実行する

第 7 章と同じく、`Run` の出力を固定するテストを書きます。

```console
$ go run ./cmd/chapters chapter08
データ件数: 891（生存 342, 死亡 549）
訓練データ: 712 件, テストデータ: 179 件
classWeight=none: 訓練 0.837, テスト 0.860, 生存者 65 人中 50 人を発見
classWeight=balanced: 訓練 0.841, テスト 0.844, 生存者 65 人中 51 人を発見
保存したモデル: survived.gob
架空の乗客の予測: [1 0]
```

最後の行は、年齢の分からない架空の乗客 2 人の予測です。1 等客室の女性は生存（1）、3 等客室の男性は死亡（0）と予測しました。年齢が空欄でも、パイプラインの中で「1 等客室の女性」「3 等客室の男性」のグループの中央値が入るので、そのまま予測できます。前処理をモデルと一緒に保存しておくことの値打ちが、ここに出ています。**モデルだけを保存すると、使うときに前処理を手で再現することになり、訓練時と食い違う余地が生まれます。**

### 重みの効果は深さによって変わる

深さ 5 では、balanced のほうがテストの正解率がわずかに低く（0.844 対 0.860）、見つけた生存者は 1 人多い（51 人対 50 人）だけでした。効果がはっきりしません。深さを変えて測り直しました。

| 深さ | 重み | 訓練 | テスト | 発見した生存者 |
|---|---|---|---|---|
| 2 | none | 0.787 | 0.788 | 45 / 65 |
| 2 | balanced | 0.787 | 0.788 | 45 / 65 |
| 3 | none | 0.819 | 0.821 | 47 / 65 |
| 3 | balanced | 0.813 | 0.821 | 46 / 65 |
| 5 | none | 0.837 | 0.860 | 50 / 65 |
| 5 | balanced | 0.841 | 0.844 | 51 / 65 |
| 10 | none | 0.947 | 0.816 | 51 / 65 |
| 10 | balanced | 0.921 | 0.793 | 57 / 65 |

読み取れることが 2 つあります。

1. **重み付けは「正解率」と「少数派の取りこぼし」を取り引きする。** 深さ 10 では、balanced のテスト正解率は 0.793 と none（0.816）より低いのに、見つけた生存者は 57 人と 6 人多い。多数派（死亡）を少し取りこぼす代わりに、少数派（生存）を拾いにいっています
2. **深さが浅いと効果が出ない。** 深さ 2 では両者がまったく同じ結果です。分割の回数が少なすぎて、重みを変えても選ばれる分割が変わらないためです

どちらが「良い」かは、何を大事にするかで決まります。救助の優先順位を決めるなら、生存者を取りこぼさないほうが大事かもしれません。正解率という 1 つの数だけを見ていると、この取り引きが見えません。評価指標を正解率から広げる話は、第 11 章で扱います。

なお、深さ 10 の訓練 0.947 とテスト 0.816 の開きは、過学習の典型です。訓練データを覚えこんで、テストデータで落ちています。

### ほかの言語版と数値が一致しない

第 7 章と同じ理由です。Go の `math/rand` は Java の `java.util.Random` と乱数列が違うので、891 人をどう分けるかが変わります。訓練 712 件・テスト 179 件という **件数は一致** しますが、どの乗客がテストデータに入るかは違います。生存者 65 人という内訳も、Go 版の分け方での実測値です。

## 8.12 品質チェック

```console
$ gofmt -l .
$ go vet ./...
$ golangci-lint run
0 issues.
```

第 8 章のテストの実行結果です（学習データを配置した状態）。

```text
--- PASS: TestWeightedGini (0.00s)
    --- PASS: TestWeightedGini/ラベルが_1_種類なら_0 (0.00s)
    --- PASS: TestWeightedGini/重みがすべて_1_なら第_3_章のジニ不純度と同じ (0.00s)
    --- PASS: TestWeightedGini/少数派を重くすると不純度が上がる (0.00s)
--- PASS: TestBalancedWeights (0.00s)
--- PASS: TestGroupMedianImputer (0.00s)
--- PASS: TestGroupMedianImputerUsesTrainMedianForOtherData (0.00s)
--- PASS: TestMostFrequentImputer (0.00s)
--- PASS: TestDummyEncoder (0.00s)
--- PASS: TestDummyEncoderMakesSameColumnsForOtherData (0.00s)
--- PASS: TestPipelineFitAndPredict (0.00s)
--- PASS: TestPipelineLeavesNoMissingValue (0.00s)
--- PASS: TestSavedPipelinePredictsTheSame (0.01s)
--- PASS: TestLoadPipelineFailsForBrokenFile (0.00s)
--- PASS: TestSurvivedData (0.00s)
    --- PASS: TestSurvivedData/891_件のうち_342_件が生存している (0.01s)
    --- PASS: TestSurvivedData/実行すると重みの付け方ごとの評価と保存したモデルの予測を表示する (0.16s)
ok  	github.com/k2works/.../internal/chapter08	1.541s	coverage: 82.4% of statements
```

学習データが無い環境（`ML_DATA_DIR=/nonexistent go test ./...`）では、`TestSurvivedData` の 2 件がスキップされ、テストは成功します。

## 8.13 探索と可視化

年齢の分布や、客室の等級ごとの生存率をグラフで確かめる手順は、[Python 版の第 8 章](../python/08-classification-and-preprocessing-pipeline.md) と [Kotlin 版の第 8 章](../kotlin/08-classification-and-preprocessing-pipeline.md) の Notebook を参照してください。Go 版には Notebook と可視化の節を設けません。

## 8.14 まとめ

この章では、前処理のパイプラインと、クラスの重みを付けた決定木を Go で実装しました。

1. **インターフェースを 2 つに分ける** — `Transformer`（まだ学んでいない設定）と `FittedTransformer`（学び終わって変換できる状態）に分け、「学習せずに変換する」を型で防いだ。Go のインターフェースは暗黙的に実装されるので、`implements` を書く場所が無い
2. **`map` のキーはスライスにできない** — グループのキーは、制御文字でつないだ文字列にした。Java 版の `List<String>` のキーに当たるものが無く、区切りの衝突を設計で避ける必要があった
3. **`map` の反復は順序がランダム** — 最頻値や多数派を選ぶときは、「先に現れた順」のスライスを別に持って走査した。`map` をそのまま回すと、テストが不安定になる
4. **整数の除算に注意** — `balanced` の重みは `float64(...)` の変換を忘れると 0 になる。コンパイラは何も言わない
5. **`encoding/gob` は登録と公開が要る** — インターフェースの具体型を `gob.Register` で登録し、保存したいフィールドは公開する。登録を忘れると `type not registered for interface` で止まる。Java のシリアライズと違い、任意のクラスを復元しないので `ObjectInputFilter` に当たる仕組みは要らない
6. **パイプラインごと保存する** — 前処理で求めた中央値・最頻値・カテゴリも一緒に保存したので、年齢が空欄の新しい乗客をそのまま予測できた
7. **重み付けは取り引き** — 深さ 10 で、balanced はテスト正解率を 0.816 から 0.793 に下げる代わりに、見つけた生存者を 51 人から 57 人に増やした。正解率という 1 つの数では見えない

次の章では、特徴量そのものを作り変える **特徴量エンジニアリング** を扱います。標準化では、自作の計算を gonum の `stat.Mean`・`stat.StdDev` と突き合わせます。第 7 章と同じく、gonum にあるものは使い、無いものは自作する、という線引きが続きます。
