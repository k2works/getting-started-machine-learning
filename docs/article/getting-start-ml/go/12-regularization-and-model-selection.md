---
type: Article
title: "第 12 章: 正則化とモデル選択"
description: "係数の大きさに罰則を加えるリッジ回帰を正規方程式で、ラッソ回帰を座標降下法で Go に自作し、訓練・検証・テストの 3 分割と書き換えない実験記録で alpha を選ぶ。"
tags: [article,getting-start-ml,go]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T12:12:11Z }
---

# 第 12 章: 正則化とモデル選択

## 12.1 はじめに

第 9 章で多項式特徴量（2 乗の項と交互作用の項）を作りました。特徴量を増やすと訓練データへの当てはまりは良くなりますが、テストデータでは悪くなることがあります。**過学習** です。

この章では、過学習を抑える **正則化** を 2 種類作ります。

| 手法 | 罰則 | 特徴 |
|------|------|------|
| リッジ回帰（Ridge） | 係数の 2 乗の合計 | 係数を 0 に近づけるが、0 にはしない。正規方程式に足すだけで解ける |
| ラッソ回帰（Lasso） | 係数の絶対値の合計 | 係数をちょうど 0 にする。行列の式では解けないので、座標降下法で 1 つずつ更新する |

そして、罰則の強さ `alpha` を **検証データ** で選びます。テストデータは最後に 1 回だけ使います。

[Python 版の第 12 章](../python/12-regularization-and-model-selection.md) と同じ題材・同じ TODO リストで進めます。対比の相手は [Java 版](../java/12-regularization-and-model-selection.md)（Tribuo の `ElasticNetCDTrainer` でラッソ回帰を表す）と [TypeScript 版](../typescript/12-regularization-and-model-selection.md)（自作）です。

### ライブラリ未対応について

第 11 章では gonum の `stat.ROC` と突き合わせられましたが、この章は突き合わせの節を **省きます**。理由は [ADR 008](../../../adr/008-go-ml-libraries.md) に書いたとおりです。

- gonum に **正則化付きの回帰は無い**。`stat.LinearRegression` は単回帰（切片と傾き）だけで、罰則項を渡す口が無い。`mat` は行列の道具なので、リッジ回帰の式は自分で組み立てる
- `optimize` パッケージで目的関数を自分で書けば数値的に解けるが、それは「自作の別の書き方」であって、ライブラリの実装と突き合わせることにはならない
- GoLearn は 3 年以上更新が止まっているので採らない（第 10 章と同じ判断）

したがって、この章の **自作が Go 版の最終実装** です。Java 版が Tribuo と突き合わせた節（12.8）は、Go 版には置きません。

ただし、まったく突き合わせる相手がいないわけではありません。**alpha が 0 のリッジ回帰は、第 7 章の最小二乗法と一致するはず** です。これをテストで固定すれば、行列の式を間違えていないことが言えます。自作どうしの突き合わせです。

## 12.2 過学習と正則化

### 係数が大きくなりすぎる

特徴量どうしが似ていると（`LSTAT` と `LSTAT^2` のように）、最小二乗法は「片方に大きな正の係数、もう片方に大きな負の係数」を当てて、訓練データにぴったり合わせようとします。訓練データの小さなゆらぎに合わせた結果なので、新しいデータでは外れます。

係数が大きくなること自体を罰することで、これを抑えます。

| 手法 | 最小にするもの |
|------|---------------|
| 最小二乗法 | ‖t − X w‖² |
| リッジ回帰 | ‖t − X w‖² + alpha × Σ wⱼ² |
| ラッソ回帰 | ‖t − X w‖² + alpha × Σ \|wⱼ\| |

`alpha` が 0 なら、どちらも最小二乗法に戻ります。`alpha` を大きくすると係数が縮み、極端に大きくすると全部 0 に近づいて「平均を答えるだけのモデル」になります。

### リッジ回帰の解き方

リッジ回帰は、微分して 0 と置くと次の式になります。

```text
(Xᵀ X + alpha × I) w = Xᵀ t
```

第 7 章の正規方程式 `(Xᵀ X) w = Xᵀ t` の対角に `alpha` を足しただけです。しかも、`Xᵀ X` が特異（列が互いに独立でない）で解けない場合でも、`alpha` を足すと解けるようになります。正則化は過学習を抑えるだけでなく、**数値的な安定** ももたらします。

### 切片には罰則をかけない

`w` には切片を含めません。切片は「全体の水準」を表す値で、これを 0 に近づける理由が無いからです（データの単位を変えただけで予測が壊れてしまう）。

実装では、特徴量と正解をそれぞれ平均で引いて **中心化** してから解き、あとで切片を戻します。

```go
// interceptOf は中心化を元に戻して切片を求める。
func interceptOf(weights, means []float64, targetMean float64) float64 {
	intercept := targetMean

	for j, weight := range weights {
		intercept -= weight * means[j]
	}

	return intercept
}
```

## 12.3 題材とデータ

Boston.csv（住宅価格）を使います。過学習が起きやすい状況を意図的に作ります。

- 特徴量を `RM`（部屋数）・`PTRATIO`（生徒教師比）・`LSTAT`（低所得者率）の 3 列に絞る
- 標準化してから、2 乗の項と交互作用の項を足して **9 列** にする
- 外れ値（いずれかの列で標準得点の絶対値が 3 を超える行）を除く
- 訓練 47 件・検証 21 件・テスト 30 件に分ける

47 件の訓練データに 9 個の係数を当てるので、過学習が起きます。

### 3 つに分ける理由

```text
98 件
├── テスト 30 件（最後に 1 回だけ使う）
└── 68 件
    ├── 訓練 47 件（係数を学習する）
    └── 検証 21 件（alpha を選ぶ）
```

検証データを分けずにテストデータで `alpha` を選ぶと、テストデータの情報がモデル選択に漏れて、性能を過大評価します。第 11 章の交差検証でも同じことができますが、この章は「検証データを 1 つ取り分ける」いちばん単純な形にします。

Go では、第 2 章の `chapter02.SplitTrainTest` を **2 回呼ぶ** だけで 3 分割になります。

```go
	outer, err := chapter02.SplitTrainTest(x, t, testSize, seed)
	if err != nil {
		return BostonDataset{}, err
	}

	inner, err := chapter02.SplitTrainTest(outer.XTrain, outer.TTrain, validationSize, seed)
	if err != nil {
		return BostonDataset{}, err
	}
```

## 12.4 TODO リストの作成

```text
- [ ] 中心化（切片に罰則をかけないため）
- [ ] リッジ回帰（alpha が 0 なら第 7 章と一致する）
- [ ] alpha を上げると係数が縮む
- [ ] 軟判定しきい値関数
- [ ] ラッソ回帰（座標降下法）
- [ ] 係数が 0 になった列の名前を返す
- [ ] 標準化と多項式特徴量
- [ ] 外れ値を除く
- [ ] 訓練・検証・テストの 3 分割
- [ ] 書き換えない実験記録
- [ ] 検証データで最もよい alpha を選ぶ
- [ ] テストデータで最後に 1 回だけ比べる
```

## 12.5 リッジ回帰を自作する

### Red: 最小二乗法と同じになるテスト

最初のテストは「alpha が 0 なら第 7 章と同じ係数になる」です。答えが既に手元にあるので、三角測量の相手に困りません。

```go
	t.Run("alpha が 0 なら第 7 章の最小二乗法と同じ係数になる", func(t *testing.T) {
		t.Parallel()

		x, targets := sample(t)

		ridge, err := chapter12.FitRidge(x, targets, 0)
		if err != nil {
			t.Fatalf("FitRidge() でエラー: %v", err)
		}

		least, err := chapter07.Fit(x, targets)
		if err != nil {
			t.Fatalf("chapter07.Fit() でエラー: %v", err)
		}

		if math.Abs(ridge.Intercept-least.Intercept) > 1e-8 {
			t.Errorf("切片 = %v, want %v", ridge.Intercept, least.Intercept)
		}
		// （列ごとに係数を比べる）
	})
```

第 7 章は「先頭に 1 の列を足した計画行列」で切片を一緒に解きましたが、この章は中心化で切片を外します。解き方が違うのに同じ答えが出ることを確かめるので、実装の検算になります。

### 仮実装が通ってしまう範囲

alpha を無視した実装（対角に何も足さない）でも、この 1 件は通ります。通らなくなるのは「alpha を上げると係数が縮む」テストです。実際に alpha を足す行を外してみると、こう落ちました。

```text
--- FAIL: TestFitRidge (0.00s)
    --- FAIL: TestFitRidge/alpha_を大きくすると係数の絶対値の合計が小さくなる (0.00s)
        regularization_test.go:87: alpha=1 の係数の合計 2 が、前の 2 より小さくありません
        regularization_test.go:87: alpha=10 の係数の合計 2 が、前の 2 より小さくありません
        regularization_test.go:87: alpha=100 の係数の合計 2 が、前の 2 より小さくありません
FAIL
```

合計が 2 のまま動かない、つまり alpha が効いていない、と読めます。

### 明白な実装

gonum の `mat` で組み立てます。対角に `alpha` を足す部分が、第 7 章との唯一の差です。

```go
// FitRidge は (Xᵀ X + alpha × I) w = Xᵀ t を解く。alpha が 0 なら最小二乗法と同じになる。
func FitRidge(x []chapter02.Features, t []float64, alpha float64) (RegularizedModel, error) {
	if alpha < 0 {
		return RegularizedModel{}, fmt.Errorf("alpha は 0 以上にしてください: %v", alpha)
	}

	design, targets, means, targetMean, err := centered(x, t)
	if err != nil {
		return RegularizedModel{}, err
	}

	_, columns := design.Dims()

	var normal mat.Dense

	normal.Mul(design.T(), design)

	for j := range columns {
		normal.Set(j, j, normal.At(j, j)+alpha)
	}

	var moment mat.VecDense

	moment.MulVec(design.T(), mat.NewVecDense(len(targets), targets))

	var weights mat.VecDense
	if err := weights.SolveVec(&normal, &moment); err != nil {
		return RegularizedModel{}, fmt.Errorf("正規方程式を解けません: %w", err)
	}

	coefficients := mat.Col(nil, 0, &weights)

	return RegularizedModel{
		Columns:      slices.Clone(x[0].Columns),
		Coefficients: coefficients,
		Intercept:    interceptOf(coefficients, means, targetMean),
	}, nil
}
```

Java 版は、ch07 の `Matrix` 型に無い演算（行列の和、スカラー倍、単位行列）を補う `MatrixOperations` クラスを書きました。gonum には `mat.NewDiagDense` も `Add` もありますが、単位行列を作って足すより **対角要素を直接足す** ほうが素直で、割り当ても 1 つ減ります。`normal.Set(j, j, normal.At(j, j)+alpha)` の 1 行で済みました。

### 切片に罰則がかかっていないことを確かめる

alpha を極端に大きく（1000）すると係数はほぼ 0 になり、モデルは「いつも同じ値を答える」ようになります。そのときの答えが **正解の平均** になっていれば、切片が罰則を免れている証拠です。

```go
	t.Run("切片には罰則をかけないので、alpha を上げても予測の平均は正解の平均に近い", func(t *testing.T) {
		// （中略）
		if math.Abs(predicted-actual) > 1e-8 {
			t.Errorf("予測の平均 = %v, want %v", predicted, actual)
		}
	})
```

### 列名で係数を持つ

`RegularizedModel` は、第 7 章の `LinearModel` と同じく **列名と係数を対にして** 持ちます。多項式特徴量で列が 9 個に増えるので、どの係数がどの項のものか分からなくなると読めません。

```go
// RegularizedModel は正則化した線形モデル。列名つきの係数と切片を持つ。
type RegularizedModel struct {
	Columns      []string
	Coefficients []float64
	Intercept    float64
}
```

「正則化がどれだけ係数を縮めたか」を 1 つの数で見るために、係数の絶対値の合計も持たせました。

```go
// CoefficientAbsSum は係数の絶対値の合計。正則化がどれだけ係数を縮めたかを 1 つの数で見る。
```

## 12.6 実験結果を記録して選ぶ

### 書き換えない実験記録

`alpha` ごとの結果を構造体に記録します。作ったあとは書き換えません。

```go
// Experiment は 1 つの alpha で学習した結果。作ったあとは書き換えない。
// 実験の記録を書き換えられないようにしておくと、あとから「どの alpha で何が起きたか」を必ず読み返せる。
type Experiment struct {
	Alpha             float64
	TrainScore        float64
	ValidationScore   float64
	CoefficientAbsSum float64
}
```

Go には Java の `record` や Kotlin の `data class` のような「不変を宣言する仕組み」がありません。フィールドを公開すれば誰でも書き換えられます。では何で不変性を担保するかというと、**値として扱う** ことです。

- `Experiment` は `float64` 4 つなので、スライスに入れても代入しても **値がコピー** される。ポインタ（`*Experiment`）を渡さない限り、呼び先が元を書き換えることはない
- `BestExperiment` は `Experiment` を値で返す。返した先で書き換えても、スライスの中身は変わらない
- フィールドを非公開にして getter を並べる手もあるが、Go では過剰と見なされる

構造体は `==` で比べられるので、テストで「記録が変わっていない」ことも確かめやすくなります。第 11 章の `ConfusionMatrix` と同じ設計です。

### 検証データで最もよい実験を選ぶ

```go
// BestExperiment は検証データの決定係数が最も高い実験を返す。同じなら先の実験を選ぶ。
func BestExperiment(experiments []Experiment) (Experiment, error) {
	if len(experiments) == 0 {
		return Experiment{}, fmt.Errorf("実験が 1 件もありません")
	}

	best := experiments[0]

	for _, experiment := range experiments[1:] {
		if experiment.ValidationScore > best.ValidationScore {
			best = experiment
		}
	}

	return best, nil
}
```

`>` であって `>=` ではないので、同じ値なら先の実験（= 小さい alpha）が残ります。この振る舞いはテストで固定しました。同点のときに「より単純なモデル」を選ぶ、という判断です。

「訓練データの決定係数では選ばない」ことも、テストで固定しています。訓練 R² が 0.99 の実験より、訓練 0.80・検証 0.70 の実験を選ぶ、というテストです。これを間違えると、正則化を入れた意味がまるごと消えます。

## 12.7 ラッソ回帰で特徴量を絞り込む

### なぜ 0 になるのか

リッジ回帰の罰則（2 乗）は、係数が 0 に近づくほど罰則の減りが小さくなるので、ぴったり 0 には届きません。ラッソ回帰の罰則（絶対値）は 0 の近くでも減り方が一定なので、**0 で止まります**。

この性質を式にしたものが **軟判定しきい値関数** です。

```go
// SoftThreshold は軟判定しきい値関数。絶対値が threshold 以下なら 0 にし、そうでなければ threshold だけ 0 に近づける。
// ラッソ回帰が係数をぴったり 0 にできるのは、この関数が 0 を返す幅を持つからです。
func SoftThreshold(value, threshold float64) float64 {
	switch {
	case value > threshold:
		return value - threshold
	case value < -threshold:
		return value + threshold
	default:
		return 0
	}
}
```

Go の `switch` は条件式を省くと「最初に真になった `case`」を選ぶ形になり、`if / else if / else` より読めます。5 通りの入力（しきい値の内側・外側、正負、しきい値 0）を表駆動テストで押さえました。

### 座標降下法

絶対値は 0 で微分できないので、リッジ回帰のように「微分して 0 と置く」ことができません。そこで **1 つの係数だけを動かして、ほかは固定する** ことを順番に繰り返します。1 つに絞れば、軟判定しきい値関数で最適値が直接書けます。

```go
// descend は全部の係数を 1 巡だけ更新し、変化の最大値を返す。
func descend(design *mat.Dense, residuals, weights, squares []float64, alpha float64) float64 {
	rows, columns := design.Dims()
	maxChange := 0.0

	for j := range columns {
		if squares[j] == 0 {
			continue
		}

		// その列を外したときの残差と、この列との内積
		rho := 0.0
		for i := range rows {
			rho += design.At(i, j) * (residuals[i] + design.At(i, j)*weights[j])
		}

		updated := SoftThreshold(rho, alpha/2) / squares[j]

		if change := math.Abs(updated - weights[j]); change > maxChange {
			maxChange = change
		}

		// 残差を差分だけ直す。毎回すべて計算し直すより速い
		difference := updated - weights[j]
		if difference != 0 {
			for i := range rows {
				residuals[i] -= design.At(i, j) * difference
			}

			weights[j] = updated
		}
	}

	return maxChange
}
```

残差を毎回ゼロから計算し直すと、1 巡あたり「行数 × 列数 × 列数」の計算になります。**差分だけ直す** と「行数 × 列数」で済みます。係数の変化が `1e-10` より小さくなったら打ち切ります。

`FitLasso` 側は繰り返しの上限（10 万回）と収束判定だけを持ちます。

```go
	for range lassoMaxIterations {
		if descend(design, residuals, weights, squares, alpha) < lassoTolerance {
			break
		}
	}
```

### ラッソとリッジの違いをテストで示す

同じデータに同じ大きさの alpha をかけて、片方だけが 0 を作ることを確かめます。

```go
	t.Run("alpha を大きくすると係数がちょうど 0 になる", func(t *testing.T) {
		// （中略）
		zero := chapter12.ZeroCoefficientNames(model)
		if len(zero) == 0 {
			t.Errorf("0 になった係数がありません: %v", model.Coefficients)
		}
	})

	t.Run("リッジ回帰は係数を 0 に近づけるだけで 0 にはしない", func(t *testing.T) {
		// （中略）
		if zero := chapter12.ZeroCoefficientNames(model); len(zero) != 0 {
			t.Errorf("0 になった係数 = %v, want なし", zero)
		}
	})
```

`ZeroCoefficientNames` は `== 0` のちょうど一致で判定します。「ほぼ 0」を拾う実装にすると、リッジ回帰とラッソ回帰の違いが消えてしまうからです。

```go
// ZeroCoefficientNames は係数がちょうど 0 になった列の名前を、列の順に返す。
```

「alpha が 0 ならリッジ回帰の alpha 0 と一致する」も固定しました。座標降下法という別の解き方が、行列を解いた答えにたどり着くことの確認です。

### alpha の大きさは流儀で変わる

罰則を `‖t − X w‖²` に足すか、`‖t − X w‖² ÷ (2n)` に足すかで、同じ強さを表す alpha の値が変わります。Java 版が Tribuo に渡すとき `alpha / t.size()` と割っていたのは、この違いを合わせるためです。

Go 版は自作なので、素直に `‖t − X w‖² + alpha × Σ|w|` にしました。そのぶん、ライブラリで見慣れた値（0.1〜1 あたり）より大きな alpha が要ります。実データでは 50 を使いました。

```go
	// lassoAlpha はラッソ回帰で係数を 0 にする様子を見るための alpha。
	// 罰則を ‖t − X w‖² に足すので、平均で割る流儀のライブラリより大きな値が要る。
	lassoAlpha = 50
```

**言語をまたいで alpha の値を比べても意味がありません**。比べるべきは「alpha を上げると係数が縮み、ラッソ回帰では 0 になる」という振る舞いです。

## 12.8 最小限の前処理

### 標準化と多項式特徴量

第 9 章で作った標準化と多項式特徴量を、この章の `PolynomialScaler` にまとめます。訓練データで `FitScaler` し、同じ平均と標準偏差で検証・テストを `Transform` します。

罰則は係数の大きさにかかるので、列の単位がばらばらだと「単位が小さい列の係数が大きくなり、不当に強く罰される」ことになります。正則化の前に標準化は必須です。

列の名前も一緒に作ります。

```go
// FeatureNames は変換した後の列の名前を、元の列・2 乗の項・交互作用の項の順に返す。
func (s PolynomialScaler) FeatureNames() []string {
	names := slices.Clone(s.Inputs)

	for _, pair := range s.pairs() {
		if pair[0] == pair[1] {
			names = append(names, s.Inputs[pair[0]]+"^2")
		} else {
			names = append(names, s.Inputs[pair[0]]+" "+s.Inputs[pair[1]])
		}
	}

	return names
}
```

3 列 → 9 列（元の 3 列 + 2 乗 3 つ + 交互作用 3 つ）です。

前処理が正しいことは、性質で固定します。「標準化した値の合計が 0」「2 乗の項が標準化した値の 2 乗になっている」「訓練データの平均をそのまま渡したら全部 0 になる」の 3 つです。3 つ目は「同じ平均と標準偏差を使っている」ことを外から確かめる書き方です。

### 外れ値を除く

いずれかの列で標準得点の絶対値が 3 を超える行を除きます。ここは **標本標準偏差**（件数から 1 を引いて割る）を使います。第 9 章の標準化は母標準偏差（件数で割る）でした。

```go
// sampleStdDev は標本標準偏差。差の 2 乗の和を「件数 − 1」で割ってから平方根を取る。
```

どちらを使っても外れ値の判定はほとんど変わりませんが、標準得点の値そのものは違うので、どちらを使ったかをコメントに残します（第 9 章で `gonum` の `stat.StdDev` が標本標準偏差だったことと同じ話です）。

## 12.9 実データで比べる

### 結果

`go run ./cmd/chapters chapter12` の出力です。

```text
データ件数: 98（外れ値 2 件を除外）
訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件
特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2
alpha	訓練 R²	検証 R²	係数の絶対値の合計
  0.0	0.8420	0.7540	14.781
  0.1	0.8420	0.7542	14.707
  1.0	0.8416	0.7550	14.154
 10.0	0.8294	0.7514	11.836
100.0	0.6567	0.5812	6.623
検証データで選んだ alpha: 1
テストデータの決定係数: 線形回帰 0.5520, リッジ回帰 0.5913, ラッソ回帰 0.6163
ラッソ回帰（alpha=50）で係数が 0 になった特徴量: RM PTRATIO, PTRATIO^2, LSTAT^2
```

### 結果を読む

- **過学習が起きている**。alpha=0 で訓練 R² 0.8420 に対し、検証 R² 0.7540、テスト R² 0.5520 です。訓練データに合わせすぎています
- **alpha を上げると係数が縮む**。14.781 → 14.707 → 14.154 → 11.836 → 6.623 と単調に減っています。これは罰則が効いている証拠で、テストでも単調性を固定しました
- **検証 R² は山なりになる**。0.7540 → 0.7542 → 0.7550（最大）→ 0.7514 → 0.5812。alpha が小さすぎると過学習、大きすぎると当てはまらない。その間に最適な値がある、という正則化の基本がそのまま出ました
- **テストデータで正則化が効いた**。線形回帰 0.5520 に対し、リッジ回帰（alpha=1）0.5913、ラッソ回帰（alpha=50）0.6163 です。検証データでの差はわずか 0.001 でしたが、テストデータでは 0.04〜0.06 の差になりました。**検証データでの小さな改善が、未知のデータでは大きな差になる** ことがあります
- **ラッソ回帰は 9 列のうち 3 列を捨てた**。`RM PTRATIO`・`PTRATIO^2`・`LSTAT^2` の係数が 0 になりました。多項式特徴量で機械的に増やした項のうち、効いていないものを自動で落としたことになります。しかもテスト R² はいちばん高い

最後の点は偶然の面もあります（テストデータ 30 件での差なので）。alpha=50 を選んだのは検証データではなく「0 になる係数が見える値」という基準なので、ラッソ回帰のテスト R² を「勝った」と読むのは行きすぎです。言えるのは「特徴量を 3 つ落としても性能が落ちなかった」ことまでです。

### ほかの言語版と数値は一致しません

Java 版・Scala 版・C# 版の同じ章と、この数値は一致しません。訓練・検証・テストの分け方が `math/rand` の実装に依存するからです（[Go 版の執筆計画](../outline.md)）。件数（98 / 47 / 21 / 30）は、`SplitTrainTest` の切り上げの規則が同じなので一致しますが、**どの行がどこに入るかが違います**。

ラッソ回帰の alpha も、12.7 のとおり流儀が違うので比べられません。

### 実データのテスト

出力を丸ごと固定するテストに加えて、次の 4 つを固定しました。

```text
- 3 列の特徴量が 9 列になり、訓練 47 件・検証 21 件・テスト 30 件に分かれる
- 正則化なしは訓練 R² が検証 R² より高い（過学習している）
- alpha を上げると係数の絶対値の合計が単調に減る
- ラッソ回帰（alpha=50）は 3 つの特徴量の係数を 0 にする
```

2 つ目と 3 つ目は、具体的な数値ではなく **性質** です。データが増えても前処理を変えても、この関係が壊れたら実装が間違っています。

データが無ければ `t.Skip` で飛ぶので、`ML_DATA_DIR=/nonexistent go test ./...` も成功します。

## 12.10 可視化について

alpha と係数の変化を描いた図（正則化パス）や、alpha と決定係数の折れ線は、[Python 版の 12.10](../python/12-regularization-and-model-selection.md) と [Kotlin 版](../kotlin/12-regularization-and-model-selection.md) の可視化の節を参照してください。Go 版では Notebook と可視化の節を作りません。

## 12.11 リファクタリング

- **`centered` をリッジとラッソで共有した**。「平均を引いた行列と正解、平均そのもの」を返す関数を 1 つにして、`FitRidge` と `FitLasso` の両方から呼びます。切片を戻す `interceptOf` も共有です
- **`descend` を `FitLasso` から切り出した**。座標降下法の「1 巡」を関数にすると、`FitLasso` 側は繰り返しと収束判定だけになり、どちらも短く読めます
- **`RegularizedModel` をリッジとラッソで共通にした**。解き方は違っても、結果は「列名・係数・切片」で同じです。`ZeroCoefficientNames` が両方に使えるので、12.7 の「リッジは 0 にしない」テストが書けました
- **`scoreOf` で決定係数の計算を 1 か所にした**。予測して `chapter07.R2Score` に渡す 2 段を、実験でもテストデータの比較でも同じ関数で通します
- **ファイルを役割で分けた**。`ridge.go`（モデルと中心化とリッジ）・`lasso.go`（軟判定しきい値と座標降下法）・`scaler.go`（前処理）・`boston.go`（データ）・`experiment.go`（実験記録）・`main.go`（表示）

検査は前の章と同じ 4 つ（`gofmt -l .`・`go vet ./...`・`golangci-lint run`・`go test ./... -cover`）で、`golangci-lint` は `0 issues.` でした。

## 12.12 まとめ

- **リッジ回帰は正規方程式の対角に alpha を足すだけ**。第 7 章の式との差は `normal.Set(j, j, normal.At(j, j)+alpha)` の 1 行。Java 版が必要とした「行列の和・スカラー倍・単位行列」の補助クラスは、gonum では要らなかった
- **alpha が 0 なら第 7 章と一致する**。ライブラリと突き合わせられない章でも、自作どうしの一致でテストが書ける。中心化で解いた答えと、1 の列を足して解いた答えが 1e-8 の範囲で一致した
- **切片には罰則をかけない**。中心化してから解き、あとで戻す。alpha を 1000 にしても予測の平均が正解の平均と一致することで確かめた
- **ラッソ回帰は行列の式では解けない**。絶対値が 0 で微分できないので、座標降下法で 1 つずつ更新する。残差は差分だけ直すと「行数 × 列数」で済む
- **軟判定しきい値関数が 0 を作る**。`switch` の条件なし形で 3 分岐を書くと読みやすい。「0 を返す幅がある」ことが、ラッソ回帰が特徴量を落とせる理由そのもの
- **不変な記録は「値として扱う」ことで表す**。Go に `record` はないが、`float64` だけの構造体を値で渡せばコピーされる。`==` で比べられるのでテストも書きやすい
- **alpha の値は流儀で変わる**。罰則を平均で割るかどうかで桁が変わるので、言語をまたいで alpha を比べない。比べるのは振る舞い（係数が縮む・0 になる）
- **正則化はテストデータで効いた**。検証データでの差は 0.001 だったが、テストデータでは線形回帰 0.5520 に対しリッジ 0.5913・ラッソ 0.6163 になった。ラッソ回帰は 9 列のうち 3 列を落としたうえでこの結果だった

次の章では、教師なし学習に入り、主成分分析（PCA）で特徴量の次元を落とします。gonum には `stat.PC` があるので、久しぶりにライブラリとの突き合わせができる章になります。
