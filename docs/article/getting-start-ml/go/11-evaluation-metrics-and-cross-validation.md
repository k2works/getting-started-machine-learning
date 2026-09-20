---
type: Article
title: "第 11 章: 評価指標と交差検証"
description: "混同行列・適合率・再現率・F 値・平均二乗誤差と K 分割交差検証を Go で自作し、評価関数を高階関数として渡す設計にする。自作した ROC 曲線と AUC は gonum の stat.ROC と突き合わせる。"
tags: [article,getting-start-ml,go]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T12:12:11Z }
---

# 第 11 章: 評価指標と交差検証

## 11.1 はじめに

前の章まで、モデルの良し悪しを **正解率** だけで見てきました。この章では、正解率だけでは見えないものを見るための指標（混同行列・適合率・再現率・F 値・平均二乗誤差）と、1 回きりの分割に頼らない **K 分割交差検証** を作ります。

Go の観点で面白いのは次の 3 つです。

| 論点 | Go でどうするか |
|------|----------------|
| 評価関数を差し替える | 関数型 `Metric[T]` を定義して高階関数として渡す。Java の関数型インターフェースに当たるものが、Go では型宣言 1 行で済む |
| ラベルの型が章ごとに違う | ジェネリクス（Go 1.18 以降）で `Model[T]` と `Metric[T]` を書き、文字列ラベルの分類にも float64 の回帰にも同じ交差検証を使う |
| ROC 曲線と AUC | **gonum に `stat.ROC` がある**。第 10 章と違って、この章は自作とライブラリを突き合わせられる |

[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と同じ題材・同じ TODO リストで進めます。対比の相手は [Java 版](../java/11-evaluation-metrics-and-cross-validation.md)（関数型インターフェースと `DoubleStream` による遅延評価）と [TypeScript 版](../typescript/11-evaluation-metrics-and-cross-validation.md)（ライブラリが無い環境での自作）です。

### この章はライブラリと突き合わせられる

第 10 章では「gonum に分類器が無いので自作が最終実装」としました（[ADR 008](../../../adr/008-go-ml-libraries.md)）。この章は違います。`gonum.org/v1/gonum/stat` には ROC 曲線を求める `ROC` があり、`gonum.org/v1/gonum/integrate` の `Trapezoidal` と組み合わせれば AUC が出ます。

混同行列・適合率・再現率・F 値・K 分割交差検証は gonum にありません（`stat` は統計の道具の集まりで、評価器ではない）。したがってこの章の突き合わせは **ROC と AUC だけ** です。自作した ROC 曲線が gonum と一致するかを、架空のデータと実データの両方で確かめます。

## 11.2 正解率だけでは足りない理由

Survived.csv（タイタニック号の乗客）で「全員が死亡したと予測する」モデルを考えます。生存者は 4 割弱なので、このモデルでも正解率は 6 割を超えます。生存者を 1 人も見つけていないのに、です。

正解と予測の食い違い方を 4 つに分けて数えたものが **混同行列** です。

| | 予測: 陽性 | 予測: 陰性 |
|---|---|---|
| **正解: 陽性** | 真陽性（TP） | 偽陰性（FN） |
| **正解: 陰性** | 偽陽性（FP） | 真陰性（TN） |

ここから 3 つの指標が出ます。

| 指標 | 式 | 読み方 |
|------|-----|--------|
| 適合率（precision） | TP ÷ (TP + FP) | 陽性と言ったもののうち、当たっていた割合。「無駄撃ちの少なさ」 |
| 再現率（recall） | TP ÷ (TP + FN) | 本当に陽性のもののうち、見つけられた割合。「取りこぼしの少なさ」 |
| F 値（F1 score） | 2pr ÷ (p + r) | 適合率と再現率の調和平均。片方だけ高くても上がらない |

「全員が死亡」のモデルは TP が 0 なので、適合率も再現率も F 値も 0 になります。正解率 0.6 の裏にあるものが、これで見えます。

回帰には別の指標が要ります。第 7 章で MAE と RMSE を作ったので、この章では **平均二乗誤差（MSE）** を足します。MSE は誤差を 2 乗してから平均するので、大きく外した 1 件に強く反応します。

## 11.3 TODO リストの作成

```text
- [ ] 混同行列を数える
- [ ] 適合率・再現率・F 値
- [ ] 分母が 0 のときに NaN を返さない
- [ ] 平均二乗誤差（MSE）
- [ ] K 分割交差検証の分け方
- [ ] 分け方の性質（全件が 1 回だけテストになる・重ならない）をテストで固める
- [ ] 評価関数を高階関数として渡す
- [ ] 混同行列の指標を評価関数の形に合わせる
- [ ] 2 クラスのロジスティック回帰で確率を出す
- [ ] ROC 曲線と AUC を自作する
- [ ] gonum の stat.ROC と突き合わせる
- [ ] Survived と cinema を交差検証で評価する
```

## 11.4 混同行列を数える

### Red

まず「すべて言い当てたら真陽性と真陰性だけになる」という、いちばん弱いテストから始めます。

```go
	t.Run("すべて言い当てれば真陽性と真陰性だけになる", func(t *testing.T) {
		t.Parallel()

		matrix, err := chapter11.NewConfusionMatrix([]string{"1", "0"}, []string{"1", "0"}, survived)
		if err != nil {
			t.Fatalf("NewConfusionMatrix() でエラー: %v", err)
		}

		if want := (chapter11.ConfusionMatrix{TruePositive: 1, TrueNegative: 1}); matrix != want {
			t.Errorf("NewConfusionMatrix() = %+v, want %+v", matrix, want)
		}
	})
```

実装がまだ無いので、コンパイルが通りません。

```text
# github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter11_test [...]
internal/chapter11/metrics_test.go:20:23: undefined: chapter11.ConfusionMatrix
internal/chapter11/metrics_test.go:57:26: undefined: chapter11.NewConfusionMatrix
internal/chapter11/metrics_test.go:98:88: undefined: chapter11.Precision
internal/chapter11/metrics_test.go:98:88: too many errors
FAIL	github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter11 [build failed]
```

Go のテストは、実装が無いと **テストの実行まで届かず** ビルドが落ちます。Java の「コンパイルエラー」と同じで、これも立派な Red です。`too many errors` で打ち切られるので、最初の数行しか出ません。

### 構造体は比較できる

Go の構造体は、フィールドがすべて比較可能なら `==` で比べられます。混同行列は `int` 4 つなので、`reflect.DeepEqual` を使わずに `matrix != want` と書けます。第 2 章の `Features` はスライスを持つので比較できませんでしたが、こちらは値の集まりなので比較できます。

```go
// ConfusionMatrix は混同行列。陽性と陰性の予測が、正解とどう食い違ったかを 4 つの数で表す。
type ConfusionMatrix struct {
	// TruePositive は陽性を陽性と当てた件数
	TruePositive int
	// FalsePositive は陰性を陽性と間違えた件数
	FalsePositive int
	// FalseNegative は陽性を陰性と見逃した件数
	FalseNegative int
	// TrueNegative は陰性を陰性と当てた件数
	TrueNegative int
}
```

「どのラベルを陽性とみなすか」は呼ぶ側が決めます。ラベルの型はジェネリクスにして、文字列でも整数でも使えるようにしました。

```go
// NewConfusionMatrix は正解と予測から混同行列を数える。positive が陽性とみなすラベル。
func NewConfusionMatrix[T comparable](actual, predicted []T, positive T) (ConfusionMatrix, error) {
	if err := requireSameSize(actual, predicted); err != nil {
		return ConfusionMatrix{}, err
	}

	matrix := ConfusionMatrix{}

	for i, label := range actual {
		switch {
		case label == positive && predicted[i] == positive:
			matrix.TruePositive++
		case label != positive && predicted[i] == positive:
			matrix.FalsePositive++
		case label == positive && predicted[i] != positive:
			matrix.FalseNegative++
		default:
			matrix.TrueNegative++
		}
	}

	return matrix, nil
}
```

`[T comparable]` は「`==` で比べられる型」という制約です。Java の `T` には制約が要りませんでしたが（`equals` はすべての `Object` にあるので）、Go は `==` が使える型に絞る必要があります。

### 三角測量

1 件のテストでは「常に TP と TN を 1 ずつ返す」実装でも通ります。偽陽性・偽陰性・4 つ全部の 3 件を足して、一般化を強制しました。表駆動テストにすると、4 つの場合が 1 つの表に並びます。

```go
	tests := []struct {
		name      string
		actual    []string
		predicted []string
		want      chapter11.ConfusionMatrix
	}{
		{
			name:      "すべて言い当てれば真陽性と真陰性だけになる",
			actual:    []string{"1", "0"},
			predicted: []string{"1", "0"},
			want:      chapter11.ConfusionMatrix{TruePositive: 1, TrueNegative: 1},
		},
		// （偽陽性・偽陰性・4 つの区分すべて、の 3 件が続く）
	}
```

### 件数が違うときは黙って切り詰めない

正解が 10 件で予測が 3 件のとき、短いほうに合わせて数えると「3 件分の混同行列」が返ってしまいます。数字が出るぶん、間違いに気づきにくい。エラーにします。

```go
// requireSameSize は正解と予測の件数が同じであることを確かめる。
// 短いほうに切り詰めると黙って別の指標を計算することになるので、エラーにする。
func requireSameSize[A, B any](actual []A, predicted []B) error {
	if len(actual) != len(predicted) {
		return fmt.Errorf("正解と予測の件数が違います: %d と %d", len(actual), len(predicted))
	}

	if len(actual) == 0 {
		return fmt.Errorf("正解が 1 件もありません")
	}

	return nil
}
```

型引数が `[A, B any]` と 2 つあるのは、後で「確率（`[]float64`）と正解ラベル（`[]string`）」の組にも使うからです。最初は `[T any]` で書いたのですが、ROC 曲線から呼んだところコンパイラにこう言われました。

```text
internal/chapter11/roc.go:21:36: in call to requireSameSize, type []string of actual
	does not match inferred type []float64 for []T
```

型推論が「1 つの `T`」に 2 つの型を当てようとして失敗しています。型引数を分けると通りました。Java のジェネリクスは実行時に消えるので気づきにくい種類の間違いが、Go ではコンパイル時に出ます。

## 11.5 適合率・再現率・F 値

### 明白な実装

式がそのまま書けるので、仮実装は挟みません。

```go
// Precision は適合率。陽性と予測したもののうち、本当に陽性だった割合。
func Precision(matrix ConfusionMatrix) float64 {
	return ratio(matrix.TruePositive, matrix.TruePositive+matrix.FalsePositive)
}

// Recall は再現率。本当に陽性のもののうち、陽性と予測できた割合。
func Recall(matrix ConfusionMatrix) float64 {
	return ratio(matrix.TruePositive, matrix.TruePositive+matrix.FalseNegative)
}

// F1Score は F 値。適合率と再現率の調和平均。
func F1Score(matrix ConfusionMatrix) float64 {
	precision, recall := Precision(matrix), Recall(matrix)
	if precision+recall == 0 {
		return 0
	}

	return 2 * precision * recall / (precision + recall)
}
```

### 分母が 0 になる場合

Go の `float64` の割り算は、0 で割っても例外になりません。`0.0 / 0.0` は `NaN` を返します。そして `NaN` はどの数とも等しくないので、`got != want` のテストが通らないだけでなく、平均を取ると全体が `NaN` に染まります。

```go
// ratio は分母が 0 なら 0 を返す割り算。適合率と再現率が NaN にならないようにする。
func ratio(numerator, denominator int) float64 {
	if denominator == 0 {
		return 0
	}

	return float64(numerator) / float64(denominator)
}
```

Java 版は `ratio` を private static メソッドにして同じことをしています。違いは、Java の `int / int` が 0 除算で `ArithmeticException` を投げるのに対し、Go は `float64` に変換してから割るので **静かに `NaN` になる** ことです。例外が飛ばないぶん、こちらのほうが危ない。テストで固めます。

```go
	t.Run("陽性と予測しなければ適合率も再現率も F 値も 0 になる", func(t *testing.T) {
		t.Parallel()

		empty := chapter11.ConfusionMatrix{FalseNegative: 3, TrueNegative: 7}

		for name, got := range map[string]float64{
			"適合率": chapter11.Precision(empty),
			"再現率": chapter11.Recall(empty),
			"F 値": chapter11.F1Score(empty),
		} {
			if got != 0 {
				t.Errorf("%s = %v, want 0", name, got)
			}
		}
	})
```

### F 値は「間」に来る

F 値が調和平均であることは、次のテストで示せます。適合率 0.6 と再現率 0.9 なら、F 値はその間（約 0.72）です。相加平均（0.75）より、低いほうに引っ張られます。

```go
	t.Run("適合率と再現率が違えば F 値はその間になる", func(t *testing.T) {
		t.Parallel()

		// 陽性 10 件のうち 9 件を当てるが、陰性 10 件のうち 6 件も陽性と言ってしまう
		unbalanced := chapter11.ConfusionMatrix{
			TruePositive:  9,
			FalsePositive: 6,
			FalseNegative: 1,
			TrueNegative:  4,
		}

		precision, recall := chapter11.Precision(unbalanced), chapter11.Recall(unbalanced)
		f1 := chapter11.F1Score(unbalanced)

		if !(precision < f1 && f1 < recall) {
			t.Errorf("適合率 %v < F 値 %v < 再現率 %v になっていません", precision, f1, recall)
		}
	})
```

値そのものではなく **関係** を固定するテストです。式を書き換えても、この性質が壊れたらすぐ分かります。

## 11.6 回帰の評価指標

MSE は明白な実装です。学習用のテストとして「大きく外した 1 件に敏感である」ことを確かめます。

```go
	t.Run("大きく外した 1 件に平均二乗誤差は敏感に反応する", func(t *testing.T) {
		t.Parallel()

		// 10 件のうち 1 件だけ 10 外す場合と、10 件すべてを 1 ずつ外す場合
		actual := make([]float64, 10)
		spike := make([]float64, 10)
		spread := make([]float64, 10)

		spike[0] = 10

		for i := range spread {
			spread[i] = 1
		}
		// （中略）
		if big <= small {
			t.Errorf("1 件を大きく外した誤差 %v が、全件を少しずつ外した誤差 %v 以下です", big, small)
		}
	})
```

誤差の絶対値の合計はどちらも 10 ですが、MSE は 10 対 1 になります。MAE（第 7 章）なら同じ値です。指標を選ぶとはこういうことだ、というのがテストで言えます。

RMSE と MAE は第 7 章の `chapter07.RootMeanSquaredError`・`chapter07.MeanAbsoluteError` をそのまま使います。書き直しません。

## 11.7 K 分割交差検証

### なぜ分割を入れ替えるのか

第 2 章から、データを訓練用とテスト用に 1 回だけ分けてきました。100 件しかないデータでこれをやると、たまたま難しい行がテストに集まったかどうかで、評価が上下します。

K 分割交差検証は、データを K 個の塊に分け、順番に 1 つをテスト、残りを訓練にします。K 回の評価の平均を取れば、1 回の分け方の運不運がならされます。

### 分け方は「行番号」で表す

分け方を表す型は、行そのものではなく **行番号** を持ちます。こうすると、特徴量（`[]Features`）にも正解ラベル（`[]string` でも `[]float64` でも）にも同じ分け方を使えます。

```go
// Fold は 1 回分の分け方。訓練データとテストデータの行番号を持つ。
// 行そのものではなく行番号を持つので、特徴量と正解ラベルのどちらにも同じ分け方を使える。
type Fold struct {
	Train []int
	Test  []int
}
```

`KFold` は、第 2 章の `chapter02.Shuffle`（`math/rand` と Fisher-Yates）で行番号を並べ替えてから、先頭から順に塊に切ります。割り切れないときの余りは、先の分け方に 1 件ずつ配ります。

```go
// KFold は行数を nSplits 個に分け、順番にテストデータにした分け方を返す。
// 余りは先の分け方に 1 件ずつ配るので、分け方の大きさの差は 1 件までになる。
func KFold(nSamples, nSplits int, seed int64) ([]Fold, error) {
	if nSplits < 2 {
		return nil, fmt.Errorf("分割数は 2 以上にしてください: %d", nSplits)
	}

	if nSamples < nSplits {
		return nil, fmt.Errorf("行数 %d が分割数 %d より少ないです", nSamples, nSplits)
	}

	positions := make([]int, nSamples)
	for i := range positions {
		positions[i] = i
	}

	shuffled := chapter02.Shuffle(positions, seed)
	folds := make([]Fold, 0, nSplits)
	start := 0

	for i := range nSplits {
		size := nSamples / nSplits
		if i < nSamples%nSplits {
			size++
		}

		test := slices.Clone(shuffled[start : start+size])
		train := make([]int, 0, nSamples-size)

		train = append(train, shuffled[:start]...)
		train = append(train, shuffled[start+size:]...)

		folds = append(folds, Fold{Train: train, Test: test})
		start += size
	}

	return folds, nil
}
```

`for i := range nSplits` は Go 1.22 以降の書き方で、`for i := 0; i < nSplits; i++` と同じです。`slices.Clone` を忘れると、返した `Test` が元の `shuffled` を指したままになり、呼ぶ側が書き換えたときに壊れます。

### 分け方の性質をテストで固定する

具体的な行番号の並びは乱数しだいなので、テストで固定するのは **性質** です。

```text
- 分割数と同じ数の分け方を返す
- すべての行がちょうど 1 回だけテストデータになる
- 訓練データとテストデータは重ならず、合わせて全件になる
- 割り切れないときは差が 1 件までになる（11 件を 3 分割 → 4, 4, 3）
- 同じ種なら同じ分け方になる
```

「すべての行がちょうど 1 回だけテストになる」は、全分け方の `Test` を集めて並べ替え、`0..n-1` と一致するかで見ます。

```go
		tested := make([]int, 0, 10)
		for _, fold := range folds {
			tested = append(tested, fold.Test...)
		}

		slices.Sort(tested)

		if want := []int{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}; !slices.Equal(tested, want) {
			t.Errorf("テストデータになった行 = %v, want %v", tested, want)
		}
```

この 1 本で「重複して数えていない」「取りこぼしていない」の両方が言えます。

## 11.8 評価関数を高階関数として渡す

### 関数型を 1 行で宣言する

交差検証は「学習して、予測して、評価する」までが手順です。**何で評価するか** は呼ぶ側が決めたい。Java 版は `@FunctionalInterface interface Metric<T> extends ToDoubleBiFunction<...>` という 1 ファイルを用意しましたが、Go は関数型をそのまま名前にできます。

```go
// Metric は正解と予測を受け取って 1 つの数値を返す評価関数。交差検証に渡す高階関数の型。
type Metric[T any] func(actual, predicted []T) (float64, error)
```

Go の関数は第一級の値なので、インターフェースを経由する必要がありません。`Accuracy[string]` と書けば、ジェネリック関数を文字列に固定した値が得られ、そのまま `Metric[string]` として渡せます。

### 混同行列の指標を評価関数に変える

`Precision` は `ConfusionMatrix` を受け取る関数なので、そのままでは `Metric` になりません。間に **アダプタ** を挟みます。「どのラベルを陽性とするか」をここで閉じ込めるのが要点です。

```go
// ClassificationMetric は混同行列から求める指標を、Metric の形に合わせる。
// 「どのラベルを陽性とするか」をここで閉じ込めるので、交差検証は指標の中身を知らずに済む。
func ClassificationMetric[T comparable](score func(ConfusionMatrix) float64, positive T) Metric[T] {
	return func(actual, predicted []T) (float64, error) {
		matrix, err := NewConfusionMatrix(actual, predicted, positive)
		if err != nil {
			return 0, err
		}

		return score(matrix), nil
	}
}
```

クロージャが `positive` を捕まえるので、呼ぶ側は `ClassificationMetric(Recall, "1")` と書くだけです。

### モデルもジェネリクスにする

```go
// Model は Fit で学習し、Predict で予測するモデル。T は正解ラベルの型。
// 第 10 章の Classifier をジェネリクスにしたもので、回帰（T が float64）にも使える。
type Model[T any] interface {
	Fit(x []chapter02.Features, t []T) error
	Predict(x []chapter02.Features) ([]T, error)
}
```

第 3 章の `chapter03.DecisionTree` は `Fit(x []Features, t []string) error` と `Predict(x []Features) ([]string, error)` を持つので、**1 行も直さずに** `Model[string]` を満たします。Go のインターフェースは実装側で宣言しないからです（第 10 章と同じ話）。

いっぽう第 7 章の線形回帰は、`chapter07.Fit` が **パッケージの関数** で、学習した結果を戻り値で返す形でした。これは `Model` の形に合いません。薄いアダプタを書きます。

```go
// LinearRegressionModel は第 7 章の線形回帰を Model[float64] にする。
// 第 7 章の Fit はパッケージの関数なので、学習したモデルを持つ構造体で包む。
type LinearRegressionModel struct {
	model   chapter07.LinearModel
	fitted  bool
	Columns []string
}
```

Java 版も `DecisionTreeModel` と `LinearRegressionModel` の 2 つのアダプタを書きました。Go では決定木のぶんが要らなくなり、**回帰のぶんだけ** 残りました。「関数で返す設計」と「インターフェースに合わせる設計」の差がここに出ます。

### 交差検証の手順を 1 つの関数にまとめる

```go
// CrossValidate は分け方ごとに新しいモデルを学習し、評価関数の値を並べて返す。
// モデルは makeModel で毎回作り直す。前の分け方の学習が残ったモデルを使い回さないようにするため。
func CrossValidate[T any](
	makeModel func() (Model[T], error),
	x []chapter02.Features,
	t []T,
	folds []Fold,
	metric Metric[T],
) ([]float64, error) {
	if err := requireSameSize(x, t); err != nil {
		return nil, err
	}

	scores := make([]float64, 0, len(folds))

	for _, fold := range folds {
		score, err := validateOne(makeModel, x, t, fold, metric)
		if err != nil {
			return nil, err
		}

		scores = append(scores, score)
	}

	return scores, nil
}
```

`makeModel` が `(Model[T], error)` を返すのは、`chapter03.WithMaxDepth` が深さの検査でエラーを返すからです。Java の `Supplier<Model<T>>` は例外を投げるしかありませんが、Go は戻り値でそのまま表せます。

「分け方ごとに新しいモデルを作る」ことは、テストで数えて固定します。

```go
	t.Run("分け方ごとに新しいモデルを学習する", func(t *testing.T) {
		t.Parallel()
		// （中略）
		made := 0

		if _, err := chapter11.CrossValidate(
			func() (chapter11.Model[string], error) {
				made++

				return &countingModel{}, nil
			},
			x, labels, folds, chapter11.Accuracy[string],
		); err != nil {
			t.Fatalf("CrossValidate() でエラー: %v", err)
		}

		if made != 5 {
			t.Errorf("作ったモデルの数 = %d, want 5", made)
		}
	})
```

`countingModel` は「学習した件数を覚えるだけ」の架空のモデルです。実データも重い計算も使わずに、交差検証の **手順** だけをテストできます。

### 遅延評価はしない

Java 版は `DoubleStream` を返して「必要な分だけ学習する」ようにしました。Go の標準ライブラリにストリームはありません。Go 1.23 の反復子（`iter.Seq`）を使えば似たことはできますが、この章では **スライスをそのまま返します**。

- 交差検証は「K 回すべて回して平均する」のが目的で、途中で打ち切る使い方をしない
- Java 版の記事が強調しているとおり `DoubleStream` は 1 回しか使えず、平均と分散を両方取りたいときに困る
- スライスなら何度でも読める

「遅延のほうが偉い」わけではない、という判断です。

### 評価関数を差し替える

高階関数にした効果は、同じ分け方で指標だけを取り替えるテストで確かめます。

```go
		accuracy, err := chapter11.CrossValidate(makeModel, x, labels, folds, chapter11.Accuracy[string])
		// （中略）
		recall, err := chapter11.CrossValidate(
			makeModel, x, labels, folds,
			chapter11.ClassificationMetric(chapter11.Recall, survived),
		)
		// （中略）
		if slices.Equal(accuracy, recall) {
			t.Errorf("正解率と再現率が同じ値になりました: %v", accuracy)
		}
```

## 11.9 gonum の stat.ROC と突き合わせる

### ROC 曲線とは

適合率と再現率は「境目を 0.5 にしたとき」の値です。境目を動かすと、再現率と偽陽性率が両方変わります。境目をいちばん高いところから下げていったときの「偽陽性率（横軸）と真陽性率（縦軸）」の軌跡が **ROC 曲線** で、その下の面積が **AUC** です。AUC は 0.5 が当てずっぽう、1 が完全な分類です。

境目を動かすには「陽性である確率」が要ります。第 10 章のロジスティック回帰は 3 品種のソフトマックスで、確率を外に出していませんでした。この章では 2 クラス用に、シグモイドのロジスティック回帰を書きます。

```go
// Sigmoid は実数を 0 と 1 のあいだの確率に変える。
// z が負のときに exp(-z) を取ると大きな値になってあふれるので、符号で式を分ける。
func Sigmoid(z float64) float64 {
	if z >= 0 {
		return 1 / (1 + math.Exp(-z))
	}

	exp := math.Exp(z)

	return exp / (1 + exp)
}
```

第 10 章のソフトマックスで「最大値を引いてから exp を取る」ことであふれを防いだのと同じ話です。`Sigmoid(-1000)` が素朴な式では `math.Exp(1000)` を通って `+Inf` になり、`1/+Inf` で 0 にはなりますが、符号を分けたほうが確実です。テストで両端を押さえました。

学習の前に、第 9 章の `chapter09.Standardizer` で特徴量を標準化します。年齢（0〜80）と客室等級（1〜3）を混ぜたまま勾配降下法を回すと、学習率をどう選んでも進みません。

```go
// Fit は特徴量を標準化してから、勾配降下法で重みと切片を学習する。
// 標準化しないと、年齢のように桁の大きい列に引きずられて学習が進まない。
```

### 自作の ROC 曲線

確率の高い順に並べ、1 件ずつ境目を下げながら真陽性と偽陽性を数え上げます。同じ確率の行は 1 つの点にまとめます（まとめないと、並び順しだいで曲線が変わってしまう）。

```go
	points := []ROCPoint{{Threshold: scores[order[0]] + 1, FPR: 0, TPR: 0}}
	truePositives, falsePositives := 0, 0

	for i, index := range order {
		if actual[index] == positive {
			truePositives++
		} else {
			falsePositives++
		}

		// 次の行も同じ確率なら、境目をまだ下げ切っていないので点にしない
		if i+1 < len(order) && scores[order[i+1]] == scores[index] {
			continue
		}

		points = append(points, ROCPoint{
			Threshold: scores[index],
			FPR:       float64(falsePositives) / float64(negatives),
			TPR:       float64(truePositives) / float64(positives),
		})
	}
```

AUC は台形で積みます。

```go
	for i := 1; i < len(points); i++ {
		width := points[i].FPR - points[i-1].FPR
		area += width * (points[i].TPR + points[i-1].TPR) / 2
	}
```

### gonum の stat.ROC を呼ぶ

gonum は `tpr, fpr, thresh` の 3 つを返します。AUC は別パッケージの `integrate.Trapezoidal(fpr, tpr)` で求めます。「ROC は `stat`、面積は `integrate`」という分かれ方は、gonum のドキュメントの例のとおりです。

```go
// GonumAUC は gonum の stat.ROC と integrate.Trapezoidal で AUC を求める。
// stat.ROC は確率が昇順に並んでいることを前提にし、そうでなければ panic するので、先に並べ替える。
func GonumAUC(scores []float64, actual []string, positive string) (float64, error) {
	if err := requireSameSize(scores, actual); err != nil {
		return 0, err
	}

	sorted := slices.Clone(scores)
	classes := make([]bool, len(actual))

	for i, label := range actual {
		classes[i] = label == positive
	}

	// stat.SortWeightedLabeled は確率と正解ラベルの対応を保ったまま昇順に並べる
	stat.SortWeightedLabeled(sorted, classes, nil)

	tpr, fpr, _ := stat.ROC(nil, sorted, classes, nil)

	return integrate.Trapezoidal(fpr, tpr), nil
}
```

`stat.ROC` の癖を 3 つ、実際に呼んで確かめました。

| 癖 | 確かめたこと |
|----|-------------|
| 入力は昇順に並んでいる必要がある | 並べずに渡すと `panic: stat: input must be sorted ascending` で落ちる。`error` ではなく panic |
| 正解ラベルは `[]bool` | 文字列や整数のラベルは自分で `[]bool` に直す |
| AUC は返ってこない | `integrate.Trapezoidal(fpr, tpr)` を別に呼ぶ |

panic は実際にこう出ます。

```text
--- FAIL: TestPanicProbe (0.00s)
panic: stat: input must be sorted ascending [recovered, repanicked]
	gonum.org/v1/gonum/stat.ROC(...)
		/…/gonum@v0.17.0/stat/roc.go:49
```

Go の標準的な作法なら `error` を返すところですが、gonum は数値計算ライブラリらしく **前提条件を破ったら panic** します。ライブラリの流儀が言語の流儀と違うところで、自分のラッパー（`GonumAUC`）が並べ替えと型変換を引き受け、外には `error` だけを見せる形にしました。

### 突き合わせる

架空のデータ 5 通りで、自作と gonum の AUC が 1e-12 の範囲で一致することを確かめます。

```text
- 完全に分かれる（AUC 1）
- 逆に並ぶ（AUC 0）
- 入り混じる
- 同じ確率が並ぶ（AUC 0.5）
- 件数が偏る
```

「同じ確率が並ぶ」を入れたのは、同着の扱いがずれやすいからです。自作の「同じ確率は 1 点にまとめる」という判断と、gonum の「ユニークな値ごとに切る」という実装が、同じ面積を出すことをここで確認しています。

実データでも同じ検査をし、`Run` の出力では自作と gonum の AUC を並べて表示します。

## 11.10 実データで評価する

### 前処理は簡略化する

Survived.csv の特徴量は `Pclass`・`Age`・`male`（`Sex` が `male` なら 1）の 3 列だけにしました。第 8 章のパイプライン（グループ別補完・ダミー変数化・クラス重み）をここでも使うと、記事が前処理の話に戻ってしまうからです。

年齢の欠損値は **全件の平均** で補います。本来は訓練データだけで補うべきですが、交差検証では分け方ごとに訓練データが変わるので、この章は簡略化します。コメントにそう書きました。

```go
// PrepareSurvived は Pclass・Age・male（Sex が male なら 1）の 3 列を作る。
// 年齢の欠損値は全件の平均で補う。訓練データだけで補うのが本来だが、
// 交差検証では分け方ごとに訓練データが変わるので、この章は簡略化する。
```

### エラー文が大文字で始まってはいけない

`Pclass` が空欄のときのエラーを `fmt.Errorf("Pclass が空欄の行があります")` と書いたら、`golangci-lint` に止められました。

```text
internal/chapter11/dataset.go:39:30: ST1005: error strings should not be capitalized (staticcheck)
			return Dataset[string]{}, fmt.Errorf("Pclass が空欄の行があります")
```

staticcheck の ST1005 は「エラー文は大文字で始めない」という Go の慣習の検査です。日本語の文でも、先頭が英語の列名なら大文字と見なされます。列名を後ろに回して直しました。

```go
// errMissing は空欄の行があるときのエラーを作る。
// staticcheck の ST1005 は英語の列名で始まるエラー文も「大文字で始まる」とみなすので、列名を後ろに回す。
func errMissing(column string) error {
	return fmt.Errorf("空欄の行があります: %s", column)
}
```

エラー文が文中に埋め込まれる（`%w` でくるまれる）ことを前提とした慣習です。1 か所にまとめたので、ほかの列でも同じ形になります。

### 同じ分け方をすべての指標で使い回す

正解率と再現率を「別々の分け方」で測ると、比べる意味が薄れます。`KFold` を 1 回だけ呼び、その分け方をすべての指標に渡します。

```go
// Evaluate は同じ分け方をすべての評価指標で使い回し、分割ごとのスコアの平均を返す。
func Evaluate[T any](
	makeModel func() (Model[T], error),
	data Dataset[T],
	metrics []NamedMetric[T],
) ([]float64, error) {
	folds, err := KFold(len(data.X), nSplits, seed)
	// （以下、指標ごとに CrossValidate して平均する）
```

指標の並びは `map` ではなくスライスです。Go の `map` は反復順が決まらないので、表示の順が実行ごとに変わってしまいます（第 10 章と同じ判断）。

```go
// NamedMetric は表示する名前と評価関数。map は反復順が決まらないので、並びを持つ構造体にする。
type NamedMetric[T any] struct {
	Name   string
	Metric Metric[T]
}
```

### 実測値

`go run ./cmd/chapters chapter11` の出力です。

```text
Survived（5 分割交差検証の平均）
  決定木（深さ 2）
    正解率: 0.7800
    適合率: 0.7932
    再現率: 0.6284
    F 値: 0.6824
  ロジスティック回帰
    正解率: 0.7901
    適合率: 0.7359
    再現率: 0.7058
    F 値: 0.7197
ROC 曲線（ロジスティック回帰、全件で学習）
  点の数: 290
  AUC（自作）: 0.8479
  AUC（gonum）: 0.8479
cinema（線形回帰、5 分割交差検証の平均）
  RMSE: 391.73
  MAE: 315.23
  MSE: 154925.00
```

読み取れることは 3 つあります。

- **正解率はほぼ同じでも、中身が違う**。決定木 0.7800 とロジスティック回帰 0.7901 はわずか 1 ポイントの差ですが、再現率は 0.6284 と 0.7058 で 8 ポイント離れています。決定木は「生存」と言うときは当たりやすい（適合率 0.7932）かわりに、生存者を取りこぼしています。正解率だけ見ていたら気づきません
- **F 値がその差をまとめる**。0.6824 と 0.7197 で、再現率の差が F 値に出ています
- **自作と gonum の AUC が小数点以下 4 桁まで一致した**。実データ（290 点の曲線）でも一致します。第 10 章では突き合わせる相手がいませんでしたが、この章は答え合わせができました

MSE（154925.00）は RMSE（391.73）の 2 乗です。単位が「興行収入の 2 乗」になるので、人に見せる数字としては RMSE のほうが読めます。MSE を出したのは、学習の目的関数（第 12 章の正則化で最小にするもの）が MSE だからです。

### ほかの言語版と数値は一致しません

Java 版・Scala 版・C# 版の同じ章と、この数値は一致しません。交差検証の分け方が `math/rand` の実装に依存するからです（[Go 版の執筆計画](../outline.md)）。同じシードでも `java.util.Random` とは違う並びになります。

加えて、Java 版はこの章に ROC・AUC の節を置いていません（Python 版・Kotlin 版の Notebook に譲っています）。Go 版は gonum に `stat.ROC` があるので、突き合わせの節として組み入れました。節の番号（11.9）は、ほかの言語版の「ライブラリと突き合わせる」節と同じ位置です。

### 実データのテスト

出力を丸ごと固定するテストを置きます。データが無ければ `t.Skip` で飛ぶので、`ML_DATA_DIR=/nonexistent go test ./...` も成功します。

```go
		want := "Survived（5 分割交差検証の平均）\n" +
			"  決定木（深さ 2）\n" +
			"    正解率: 0.7800\n" +
			// （中略）
			"  AUC（自作）: 0.8479\n" +
			"  AUC（gonum）: 0.8479\n" +
			// （中略）
```

加えて、実データで次の 3 つを固定しました。

- 特徴量が 3 列で、`NaN` が残っていない
- 自作と gonum の AUC が一致し、0.5 を上回る
- ロジスティック回帰の再現率が 0.6 以上

3 つ目は「0.7058」という値そのものではなく **下限** を書いています。モデルのささいな変更で値は動きますが、0.6 を割ったら何かが壊れています。

## 11.11 リファクタリング

- **`requireSameSize` を 1 か所に集めた**。混同行列・正解率・MSE・交差検証・ROC の 5 か所で「件数が同じか」を確かめるので、型引数 2 つのジェネリック関数にまとめました
- **`validateOne` を切り出した**。`CrossValidate` の中に「モデルを作る・訓練を取り出す・学習する・テストを取り出す・予測する・評価する」の 6 段が並び、`err` の検査で 20 行を超えたので、1 つの分け方を処理する関数に分けました
- **`writeLine` で `io.Writer` のエラーをたたむ**。`fmt.Fprintf` は `(int, error)` を返すので、素直に書くと出力 1 行ごとに 3 行の `if` が付きます。1 つの補助関数にまとめました
- **`errMissing` で ST1005 の指摘を 1 か所にした**（11.10 のとおり）
- **ファイルを役割で分けた**。`metrics.go`（指標）・`crossvalidation.go`（分け方）・`model.go`（アダプタ）・`logistic.go`（確率）・`roc.go`（ROC）・`dataset.go`（前処理）・`main.go`（表示）

検査は前の章と同じ 4 つ（`gofmt -l .`・`go vet ./...`・`golangci-lint run`・`go test ./... -cover`）で、`golangci-lint` は `0 issues.` でした。

## 11.12 可視化について

混同行列のヒートマップと ROC 曲線の描画は、[Python 版の 11.12](../python/11-evaluation-metrics-and-cross-validation.md) と [Kotlin 版](../kotlin/11-evaluation-metrics-and-cross-validation.md) の可視化の節を参照してください。Go 版では Notebook と可視化の節を作りません。

## 11.13 まとめ

- **正解率だけでは足りない**。決定木とロジスティック回帰の正解率は 0.7800 と 0.7901 でほぼ同じだったが、再現率は 0.6284 と 0.7058 で大きく違った。混同行列を通すと何が違うのかが見える
- **Go の `float64` は 0 で割っても例外にならず、静かに `NaN` になる**。適合率・再現率は分母が 0 になりうるので、`ratio` で 0 を返す。Java の `ArithmeticException` のような合図は出ない
- **関数型は 1 行で宣言できる**。`type Metric[T any] func(actual, predicted []T) (float64, error)` だけで、Java の `@FunctionalInterface` に当たるものになる。アダプタ（`ClassificationMetric`）はクロージャで書ける
- **インターフェースを実装側で宣言しないことが、ここでも効いた**。第 3 章の決定木は 1 行も直さずに `Model[string]` を満たし、アダプタが要らなかった。関数で結果を返す第 7 章の線形回帰だけはアダプタを書いた
- **型引数を 1 つにまとめるとコンパイルが通らないことがある**。`requireSameSize[T any](actual, predicted []T)` は、確率と正解ラベルのような異なる型の組に使えない。`[A, B any]` に分ける
- **gonum の `stat.ROC` は使えた**。ただし入力は昇順（そうでなければ `error` ではなく panic）、ラベルは `[]bool`、AUC は `integrate.Trapezoidal` で別に求める。自作の AUC と 0.8479 まで一致した
- **遅延評価をわざわざ持ち込まない**。Java 版の `DoubleStream` に当たるものは Go にもあるが、K 回すべて回して平均する処理では、何度でも読めるスライスのほうが素直

次の章では、過学習を抑える正則化（リッジ回帰・ラッソ回帰）と、検証データを使ったハイパーパラメータの選び方を扱います。
