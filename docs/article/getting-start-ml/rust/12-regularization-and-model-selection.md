---
type: Article
title: "第 12 章: 正則化とモデル選択"
description: "リッジ回帰を閉形式で、ラッソ回帰を座標降下法で Rust の TDD で自作し、linfa-elasticnet の ElasticNet::ridge()・lasso() と突き合わせる。linfa の目的関数が誤差を 2n で割ること、linfa が特徴量を中心化しないことを実測し、penalty = alpha / n の読み替えで係数が 1e-14 まで一致することを確かめる。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T14:45:16Z }
---

# 第 12 章: 正則化とモデル選択

## 12.1 はじめに

第 9 章では、2 乗の項や交互作用の項を足して特徴量を増やしました。特徴量を増やすと訓練データへの当てはまりはよくなりますが、**過学習**（訓練データにだけ強く、未知のデータで弱いこと）が起きやすくなります。

この章では、過学習を抑える **正則化** と、設定の候補から 1 つを選ぶ **モデル選択** を扱います。

- **リッジ回帰（L2 正則化）** — 係数の 2 乗の和に罰則を加える。閉形式（連立方程式）で解ける
- **ラッソ回帰（L1 正則化）** — 係数の絶対値の和に罰則を加える。係数がちょうど 0 になり、特徴量を選ぶ働きがある。閉形式では解けないので **座標降下法** を使う
- **検証データによるモデル選択** — 訓練・検証・テストの 3 つに分け、検証データで正則化の強さを選び、テストデータで最後に 1 度だけ測る

linfa には `linfa-elasticnet` があり、`ElasticNet::ridge()`・`ElasticNet::lasso()` で両方を学習できます。ADR 009 のとおり、まず自作してから突き合わせます。この章では **目的関数の流儀が違う** ことが最大の論点で、そこを実測して合わせます。

## 12.2 過学習と正則化

### 係数が大きくなりすぎる

特徴量どうしが似ていると、最小二乗法は「片方に大きな正の係数、もう片方に大きな負の係数」を付けて訓練データに当てはめようとします。訓練データでは打ち消し合ってうまくいきますが、少しずれたデータでは大きく外れます。

### 係数の大きさに罰則を加える

そこで、当てはまりの悪さに **係数の大きさ** を足したものを最小にします。

| 手法 | 最小にするもの |
|------|--------------|
| 最小二乗法 | ‖t − Xw‖² |
| リッジ回帰 | ‖t − Xw‖² + α‖w‖² |
| ラッソ回帰 | ½‖t − Xw‖² + α‖w‖₁ |

α が大きいほど係数は 0 に近づき、当てはまりは悪くなります。**α をいくつにするか** を決めるのがモデル選択です。

切片には罰則をかけません。切片は「全体の水準」を表すだけで、大きくても過学習の原因にならないからです。そのために、学習の前に特徴量と正解から平均を引き（中心化）、解いたあとで平均を戻します。

### リッジ回帰の解き方

中心化したうえで、正規方程式の左辺に α を対角に足した連立方程式を解きます。

```text
(XᵀX + αI) w = Xᵀt
```

α を足すと対角が大きくなるので、解は小さいほうへ引き戻されます。α = 0 なら第 7 章の最小二乗法と同じ解になります。

## 12.3 題材とデータ

`Boston.csv` から `RM`（部屋数）・`PTRATIO`（生徒と教師の比）・`LSTAT`（低所得者の割合）の 3 列を使い、`PRICE` を予測します。過学習が起きやすい状況を作るために、次の手を入れます。

1. **z スコアの絶対値が 3 を超える行を外れ値として除く**
2. **標準化してから 2 次の項（2 乗と積）を足す** — 3 列が 9 列になる
3. **訓練・検証・テストの 3 つに分ける** — 全体を訓練用とテスト用に分け、訓練用をさらに訓練データと検証データに分ける

3 つに分ける理由は、**検証データで選んだ結果をテストデータで確かめる** ためです。検証データで選んでから同じデータで測ると、選ぶ段階で使った情報が漏れ、実力より高い値が出ます。

## 12.4 TODO リストの作成

**TODO リスト**:

- [ ] 係数と切片を持つモデルの型を作る
  - [ ] 列名と係数の数が違えば失敗を返す
  - [ ] 係数の絶対値の合計、係数が 0 の列名を求める
- [ ] リッジ回帰を閉形式で解く
  - [ ] α が 0 なら最小二乗法と同じ解になる
  - [ ] α を強くすると係数が小さくなる
  - [ ] 切片には罰則をかけない
  - [ ] α が負なら失敗を返す
- [ ] ラッソ回帰を座標降下法で解く
  - [ ] 軟しきい値作用素を作る
  - [ ] α を強くすると係数がちょうど 0 になる
  - [ ] 正解に効かない列が先に 0 になる
- [ ] 標準化 + 2 次の項の変換を作る（第 9 章の道具を組み合わせる）
- [ ] 外れ値を除いて訓練・検証・テストの 3 つに分ける
- [ ] α ごとに実験し、検証データで最もよい α を選ぶ
- [ ] linfa-elasticnet の `ridge()`・`lasso()` と突き合わせる
  - [ ] 目的関数の流儀の違いを実測して読み替える
- [ ] 実データで結果を表示する

## 12.5 章ごとの失敗を積み上げる

この章は第 2 章の表・前処理と、第 9 章の標準化・多項式特徴量の両方を使います。失敗の型は、両方を包む `enum` にします。

```rust
/// 第 12 章で起こりうる失敗。第 2 章・第 9 章の失敗を包み、この章だけの失敗を足す。
///
/// 章ごとに `enum` を足していくと、上の章の失敗をそのまま持ち上げられる。`?` は `From` が
/// あれば自動で包み直すので、呼び出し側に書き足すことは無い。
#[derive(Debug)]
pub enum Error {
    /// 第 2 章の表・前処理の失敗。
    Chapter02(crate::chapter02::Error),
    /// 第 9 章の標準化・多項式特徴量の失敗。
    Chapter09(crate::chapter09::Error),
    /// 正則化の強さが負。
    NegativeAlpha(f64),
    /// 実験の結果が 1 件も無い。
    NoExperiments,
}
```

`From` を実装しておけば、`?` が自動で包み直します。

```rust
impl From<crate::chapter09::Error> for Error {
    fn from(error: crate::chapter09::Error) -> Self {
        Error::Chapter09(error)
    }
}
```

こうすると、第 9 章の `Standardizer::fit(x)?` と第 2 章の `Table::load(csv_file)?` を **同じ関数の中で混ぜて書けます**。Java 版は検査例外を使わないので意識せずに済みますが、Go 版では `fmt.Errorf("%w", err)` で毎回包み直す必要がありました。Rust は `From` を 1 回書けば、以後は `?` が黙って変換します。

`Display` は素通しにします。利用者にとっては「どの章の失敗か」より「何が起きたか」が大事だからです。

```rust
Error::Chapter02(error) => write!(f, "{error}"),
Error::Chapter09(error) => write!(f, "{error}"),
```

## 12.6 リッジ回帰を自作する

### Red: α が 0 なら最小二乗法と同じ

`t = 2a + 3b + 1` ちょうどの架空のデータを使います。

```rust
#[test]
fn 罰則が零なら最小二乗法と同じ解になる() {
    let (columns, rows, t) = sample();
    let model = fit(&columns, &rows, &t, 0.0).unwrap();

    assert!((model.coefficients[0] - 2.0).abs() < 1e-9);
    assert!((model.coefficients[1] - 3.0).abs() < 1e-9);
    assert!((model.intercept - 1.0).abs() < 1e-9);
}
```

### Green: 中心化して対角に足す

中心化は `center` という関数にまとめます。ラッソ回帰と linfa との突き合わせでも使い回すからです。

```rust
/// 行と正解から平均を引く。切片に罰則をかけないための下ごしらえ。
pub fn center(rows: &[Vec<f64>], t: &[f64]) -> (Vec<Vec<f64>>, Vec<f64>, Vec<f64>, f64) { … }

/// 切片を平均から求める。中心化した解に、引いた平均を戻す。
pub fn intercept_from(coefficients: &[f64], x_means: &[f64], t_mean: f64) -> f64 {
    t_mean
        - coefficients
            .iter()
            .zip(x_means)
            .map(|(coefficient, mean)| coefficient * mean)
            .sum::<f64>()
}
```

本体は短いです。

```rust
let (centered, residuals, x_means, t_mean) = center(rows, t);
let x = matrix(&centered)?;
let transposed = x.t();

// XᵀX に alpha を対角に足す。対角を大きくするほど解が小さいほうへ引き戻される
let mut normal = transposed.dot(&x);

for index in 0..normal.nrows() {
    normal[[index, index]] += alpha;
}

let right = transposed.dot(&Array1::from(residuals));
let coefficients = solve(&normal, &right)?.to_vec();
let intercept = intercept_from(&coefficients, &x_means, t_mean);
```

Java 版は `Matrix` に `plus`・`times`・`identity` を `MatrixOperations` として足しました（100 行以上）。Rust では **ndarray の `Array2` がそのまま行列** なので、`dot`・`t()` は既にあり、単位行列を作る代わりに `for` で対角に足すだけです。`solve` は第 7 章で自作したガウスの消去法をそのまま使います。

`center` の戻り値がタプルなのは、4 つの値をまとめて返したいからです。Rust には多値返却があるので、Java 版のように 4 つのフィールドを持つ入れ物の型を作らずに済みます。呼ぶ側は分解代入で受け取ります。

```rust
let (centered, residuals, x_means, t_mean) = center(rows, t);
```

linfa に渡すときは残差が要らないので、使わない要素を `_` で捨てます。**使わない値に名前を付けずに済む** のも、`match` と同じ分解の仕組みです。

```rust
let (centered, _, x_means, t_mean) = center(rows, t);
```

### 三角測量: 罰則の効き方

```rust
#[test]
fn 罰則を強くすると係数が小さくなる() {
    let (columns, rows, t) = sample();
    let weak = fit(&columns, &rows, &t, 1.0).unwrap();
    let strong = fit(&columns, &rows, &t, 100.0).unwrap();

    assert!(strong.coefficient_abs_sum() < weak.coefficient_abs_sum());
}

#[test]
fn 罰則を強くしても切片は正解の平均に近いまま() {
    let (columns, rows, t) = sample();
    let model = fit(&columns, &rows, &t, 1e9).unwrap();

    assert!((model.intercept - average(&t)).abs() < 1e-6);
}
```

2 つ目が「切片に罰則をかけていない」ことの証拠です。α を極端に大きくすると係数はすべて 0 に潰れますが、切片だけは正解の平均のまま残ります。

### α が負なら失敗を返す

```rust
#[test]
fn 負の罰則は受け付けない() {
    let (columns, rows, t) = sample();
    let error = fit(&columns, &rows, &t, -1.0).unwrap_err();

    assert_eq!(error.to_string(), "正則化の強さは 0 以上にしてください: -1");
}
```

Rust には「非負の `f64`」を表す標準の型が無いので、実行時に確かめて `Result` で返します。型で防げるものは型で防ぎ、防げないものは `Result` にする、という切り分けです。

## 12.7 ラッソ回帰を座標降下法で自作する

### なぜ閉形式で解けないのか

L1 の罰則 ‖w‖₁ は原点で折れているので、微分して 0 と置く方法が使えません。代わりに **係数を 1 つずつ順に、ほかを固定して最適化する**（座標降下法）と、1 座標ぶんは手で解けます。そこに出てくるのが **軟しきい値作用素** です。

```rust
/// 軟しきい値作用素。`|value|` が `threshold` 以下なら 0 にし、そうでなければ 0 のほうへ縮める。
pub fn soft_threshold(value: f64, threshold: f64) -> f64 {
    if value > threshold {
        value - threshold
    } else if value < -threshold {
        value + threshold
    } else {
        0.0
    }
}
```

これが「係数がちょうど 0 になる」正体です。しきい値の内側に入った係数は、近づけるのではなく **0 そのもの** にされます。

```rust
#[test]
fn 軟しきい値作用素は零に寄せる() {
    assert!((soft_threshold(3.0, 1.0) - 2.0).abs() < 1e-12);
    assert!((soft_threshold(-3.0, 1.0) + 2.0).abs() < 1e-12);
    assert!(soft_threshold(0.5, 1.0).abs() < 1e-12);
}
```

### 残差を差分で更新する

座標降下法は、係数を 1 つ動かすたびに残差 `r = t − Xw` を更新します。毎回ゼロから計算し直すと遅いので、動かした列のぶんだけ足し引きします。

```rust
let old = weights[j];

// いったん j 列の寄与を残差に戻す
if old != 0.0 {
    for (value, row) in r.iter_mut().zip(&x) {
        *value += old * row[j];
    }
}

let correlation: f64 = r.iter().zip(&x).map(|(value, row)| value * row[j]).sum();

weights[j] = soft_threshold(correlation, alpha) / norms[j];

if weights[j] != 0.0 {
    for (value, row) in r.iter_mut().zip(&x) {
        *value -= weights[j] * row[j];
    }
}
```

`r.iter_mut().zip(&x)` は、残差を書き換えながら行列を読む書き方です。**同じベクタを同時に可変と不変で借りることはできません** が、違うベクタどうしなら問題ありません。ここで `r` と `x` を分けて持っているのは、借用検査に合わせた結果というより、「残差と入力は別のもの」という素直な設計です。

止める条件は「いちばん大きく動いた係数がしきい値未満」です。

```rust
if largest_change < TOLERANCE {
    break;
}
```

### 正解に効かない列が先に 0 になる

架空のデータに、正解とまったく関係のない列 `c` を足します。

```rust
#[test]
fn 正解に効かない列が先に零になる() {
    let (columns, rows, t) = sample();
    let model = fit(&columns, &rows, &t, 1.0).unwrap();

    assert!(model.zero_columns().contains(&"c".to_string()));
    assert!(!model.zero_columns().contains(&"a".to_string()));
}

#[test]
fn 罰則を強くすると係数がちょうど零になる() {
    let (columns, rows, t) = sample();
    let model = fit(&columns, &rows, &t, 100.0).unwrap();

    assert_eq!(model.zero_columns(), columns);
}
```

「ちょうど 0」を `== 0.0` で確かめられるのがラッソ回帰の特徴です。浮動小数点で等値比較をするのは普通は避けますが、ここは軟しきい値作用素が **リテラルの `0.0` を代入している** ので、等値で正しく判定できます。

```rust
/// 係数がちょうど 0 になった列の名前を、列の順に返す。
pub fn zero_columns(&self) -> Vec<String> {
    self.columns
        .iter()
        .zip(&self.coefficients)
        .filter(|(_, coefficient)| **coefficient == 0.0)
        .map(|(column, _)| column.clone())
        .collect()
}
```

## 12.8 前処理を第 9 章の道具で組み立てる

標準化してから 2 次の項を足す変換は、第 9 章の `Standardizer` と `polynomial::expand` をつなげるだけです。

```rust
/// 標準化と多項式特徴量をつなげた変換。訓練データで `fit` し、同じ平均と標準偏差で
/// 訓練・検証・テストの 3 つを `transform` する。
#[derive(Debug, Clone, PartialEq)]
pub struct PolynomialScaler {
    standardizer: Standardizer,
    columns: Vec<String>,
}

/// 標準化してから 2 次の項を足し、値だけの行にする。
pub fn transform(&self, x: &[Features]) -> Result<Vec<Vec<f64>>> {
    let standardized = self.standardizer.transform(x)?;
    let expanded = polynomial::expand(&standardized, &self.columns)?;

    Ok(expanded
        .iter()
        .map(|features| features.values.clone())
        .collect())
}
```

Java 版は `PolynomialScaler` を 80 行の record として自前で書きました（平均・標準偏差の計算、組の列挙、列名の生成）。Rust 版は第 9 章のものをそのまま使い回せるので 50 行ほどで済みます。`?` が第 9 章の失敗をこの章の `Error` に自動で包み直してくれるので、つなぎ目にコードが要りません。

列名は第 9 章と同じ形（`RM^2`・`RM LSTAT`）です。

```rust
#[test]
fn 列名は元の列と二次の項の順に並ぶ() {
    let scaler = PolynomialScaler::fit(&sample()).unwrap();

    assert_eq!(
        scaler.feature_names(),
        strings(&["a", "b", "a^2", "a b", "b^2"])
    );
}
```

「訓練データの平均と標準偏差で検証データも変換する」ことをテストに固定します。ここを間違えると、検証データの情報が訓練に漏れます。

```rust
#[test]
fn 検証データは訓練データの平均と標準偏差で変換される() {
    let scaler = PolynomialScaler::fit(&sample()).unwrap();
    // 訓練データの平均は a=3、標準偏差は sqrt(8/3)
    let rows = scaler.transform(&[row(3.0, 30.0)]).unwrap();

    assert!(rows[0][0].abs() < 1e-12);
}
```

外れ値の除去は、第 9 章の IQR ではなく z スコアで行います（Java 版・Kotlin 版と同じ基準にそろえるため）。標準偏差は件数 n − 1 で割る標本標準偏差です。

## 12.9 実験結果を記録して選ぶ

α 1 つ分の結果を、作ったあとで変えられない小さな型にします。

```rust
/// 正則化の強さ 1 つ分の実験結果。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Experiment {
    pub alpha: f64,
    pub train_score: f64,
    pub validation_score: f64,
    pub coefficient_abs_sum: f64,
}
```

`Copy` を付けているのは、`f64` が 4 つだけの小さな型だからです。`Copy` な型は、代入や関数への受け渡しで **所有権が動かず複製されます**。Java 版の record は常に参照渡しですが、Rust では「小さくて値として扱いたいもの」に `Copy` を付けて、所有権を気にせず使えるようにします。

最もよい実験を選ぶところで、`f64` の比較が再び出てきます。`max_by_key` は `Ord` を要求するので `f64` には使えません。`reduce` で自分で比べます。

```rust
/// 検証データの決定係数が最も高い実験。同じ値なら先の実験を選ぶ。
pub fn best_experiment(experiments: &[Experiment]) -> Result<Experiment> {
    experiments
        .iter()
        .copied()
        .reduce(|best, experiment| {
            if experiment.validation_score > best.validation_score {
                experiment
            } else {
                best
            }
        })
        .ok_or(Error::NoExperiments)
}
```

`>` にしてあるので、同点なら先の実験（α の小さいほう）が残ります。Java 版の `Comparator` + `max` と同じ振る舞いです。「同点ならどちらか」は実行結果を左右するので、テストで固定します。

```rust
#[test]
fn 同じ値なら先の実験を選ぶ() {
    let experiments = vec![experiment(0.1, 0.8), experiment(1.0, 0.8)];

    assert!((best_experiment(&experiments).unwrap().alpha - 0.1).abs() < 1e-12);
}
```

## 12.10 linfa-elasticnet と突き合わせる

### 目的関数の流儀が違う

ここがこの章のいちばんの山場です。`linfa-elasticnet` が最小にするのは次の式です。

```text
‖t − Xw − c‖² / (2n) + penalty × ( l1_ratio ‖w‖₁ + (1 − l1_ratio) ‖w‖² / 2 )
```

自作の目的関数と違うところが 2 つあります。

1. **誤差を 2n で割る**（n は件数）。自作は割らない
2. **罰則は `penalty` 1 つで、`l1_ratio` で L1 と L2 に配分する**。`ridge()` は `l1_ratio = 0`、`lasso()` は `l1_ratio = 1`

両辺を 2n 倍すると、linfa の式はこうなります。

```text
‖t − Xw‖² + 2n·penalty·l1_ratio·‖w‖₁ + n·penalty·(1 − l1_ratio)·‖w‖²
```

自作のリッジは `‖t − Xw‖² + α‖w‖²` なので、`l1_ratio = 0` のとき **α = n × penalty**。自作のラッソは `½‖t − Xw‖² + α‖w‖₁` を最小にしていて、2 倍すると `‖t − Xw‖² + 2α‖w‖₁` なので、`l1_ratio = 1` のときも **α = n × penalty**。どちらも同じ読み替えになりました。

```rust
/// 自作の alpha を linfa の `penalty` に直す。
#[allow(clippy::cast_precision_loss)]
pub fn penalty_for(alpha: f64, n_samples: usize) -> f64 {
    alpha / n_samples as f64
}
```

Java 版の Tribuo も同じく `2n` で割る流儀だったので、`alpha / t.size()` で読み替えていました。**「件数で割るかどうか」はライブラリごとに違うので、必ず実測して合わせる** 必要があります。

### 実測して見つけたもう 1 つの違い: 特徴量を中心化しない

読み替えだけを入れてテストを走らせると、まだ大きく食い違いました。

```text
thread '…件数で割ればlinfaのリッジ回帰は自作と一致する' panicked at src/chapter12/linfaelasticnet.rs:120:13:
1.535714285714286 と 0.40789473684210575
```

linfa の実装を読むと、`with_intercept(true)`（既定）でも **中心化されるのは正解だけ** で、特徴量はそのまま渡されていました。

```rust
let (intercept, y) = compute_intercept(self.with_intercept(), target);
let (hyperplane, duality_gap, n_steps) = coordinate_descent(
    dataset.records().view(), y.view(), …
);
```

特徴量の平均が 0 でないと、切片が担うべきぶんまで係数に押し付けられます。罰則がかかるのは係数だけなので、結果がずれます。そこで、**こちらで平均を引いてから渡し、切片を組み立て直します**。

```rust
/// linfa で学習して係数と切片を取り出す。
///
/// **linfa は正解の平均しか引かない。** `with_intercept(true)` でも特徴量は中心化されないので、
/// 列の平均が 0 でないデータをそのまま渡すと、切片のぶんまで係数に押し付けられて自作と
/// 大きく食い違う。そこで**こちらで特徴量の平均を引いてから**渡し、切片は平均から組み立て直す。
/// 第 12 章のデータは標準化してあるので実害は小さいが、標準化しない列があると効いてくる。
fn fit(…) -> Result<RegularizedModel> {
    let (centered, _, x_means, t_mean) = center(rows, t);
    let dataset = Dataset::new(records(&centered)?, Array1::from(t.to_vec()));

    let model = params
        .penalty(penalty_for(alpha, t.len()))
        .max_iterations(MAX_ITERATIONS)
        .tolerance(TOLERANCE)
        .fit(&dataset)
        .map_err(|error| Error::Chapter02(Chapter02Error::Library(error.to_string())))?;

    let coefficients = model.hyperplane().to_vec();
    let intercept = intercept_from(&coefficients, &x_means, t_mean);

    RegularizedModel::new(columns.to_vec(), coefficients, intercept)
}
```

`ridge()` と `lasso()` は、このひな型に `l1_ratio` の違う設定を渡すだけです。

```rust
/// linfa のリッジ回帰（`l1_ratio` が 0）。
pub fn linfa_ridge(…) -> Result<RegularizedModel> {
    fit(columns, rows, t, ElasticNet::ridge(), alpha)
}

/// linfa のラッソ回帰（`l1_ratio` が 1）。
pub fn linfa_lasso(…) -> Result<RegularizedModel> {
    fit(columns, rows, t, ElasticNet::lasso(), alpha)
}
```

これで一致しました。

```rust
#[test]
fn 件数で割ればlinfaのリッジ回帰は自作と一致する() {
    let (columns, rows, t) = sample();
    let own = ridge::fit(&columns, &rows, &t, 2.0).unwrap();
    let library = linfa_ridge(&columns, &rows, &t, 2.0).unwrap();

    for (left, right) in own.coefficients.iter().zip(&library.coefficients) {
        assert!((left - right).abs() < 1e-6, "{left} と {right}");
    }

    assert!((own.intercept - library.intercept).abs() < 1e-6);
}
```

Tribuo との違いも 1 つ記録しておきます。Java 版の `ElasticNetCDTrainer` は `l1Ratio = 0`（純粋なリッジ回帰）を受け付けなかったので、`1e-12` を代わりに渡す小細工が要りました。linfa の `ElasticNet::ridge()` は `l1_ratio = 0` をそのまま受け付けるので、その回避は要りません。

### 繰り返し回数は必要なだけにする

最初は `max_iterations` を 100000、`tolerance` を `1e-10` にしていました。動きはしましたが、`chapter12` の実行に 1 分かかりました。linfa の座標降下法は L2 だけのとき（`l1_ratio = 0`）に収束の判定が効きにくく、上限まで回り切っていたからです。10000 回に減らしても実データの一致は `1.27e-14` のままで、実行は 8 秒になりました（既定の 1000 回でも同じ値になることを確かめたうえで、余裕を見て 10000 回にしています）。**必要な精度が出たら、そこで止める** のも数値で確かめられる判断です。

## 12.11 実データで比べる

```bash
cargo run --bin chapters -- chapter12
```

```text
データ件数: 98（外れ値 2 件を除外）
訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件
特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2
alpha	訓練 R²	検証 R²	係数の絶対値の合計
0.0	0.9123	0.7861	12.057
0.1	0.9123	0.7861	12.019
1.0	0.9121	0.7862	11.735
10.0	0.9023	0.7820	10.357
100.0	0.7227	0.6596	6.785
検証データで選んだ alpha: 1.0
テストデータの決定係数: 線形回帰 0.4728, リッジ回帰 0.5019
リッジ回帰の係数の最大の差（自作と linfa）: 1.27e-14
ラッソ回帰（alpha ごとに 0 になった特徴量）
  alpha=1: 自作 [] / linfa []
  alpha=10: 自作 [RM LSTAT, PTRATIO^2, PTRATIO LSTAT] / linfa [RM LSTAT, PTRATIO^2, PTRATIO LSTAT]
  alpha=50: 自作 [RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2] / linfa [RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2]
  alpha=100: 自作 [RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2] / linfa [RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2]
ラッソ回帰の係数の最大の差（自作と linfa）: 3.87e-9
```

この表示は `tests/regularization.rs` の `第十二章の実行結果が固定される` で固定しています。読み取れることは次のとおりです。

- **α を強くすると係数の絶対値の合計は単調に減る**。12.057 → 12.019 → 11.735 → 10.357 → 6.785 と、5 つの α すべてで減りました。これはテストにも固定しています
- **訓練 R² と検証 R² の差が過学習の大きさ**。α = 0 では訓練 0.9123 に対して検証 0.7861 で、0.126 の開きがあります。特徴量を 3 列から 9 列に増やした結果です
- **検証データで選ばれた α は 1.0**。検証 R² は 0.7861 → 0.7861 → **0.7862** → 0.7820 → 0.6596 で、α = 1.0 がわずかに最大でした。差は 0.0001 と小さく、検証データ 21 件では「はっきり勝った」とは言えません
- **それでもテストデータでは差が付いた**。線形回帰（α = 0）の 0.4728 に対して、リッジ回帰（α = 1.0）は 0.5019 でした。検証 R² の差が 0.0001 でも、テストデータでは 0.029 の差になりました。**検証データで選ぶことには意味があった** ということです
- **テストデータの R² は検証データより大幅に低い**（0.7862 → 0.5019）。テストデータ 30 件のほうが難しい行を含んでいるためで、**最後に 1 度だけ測る値こそが実力** だと分かります
- **自作と linfa は一致した**。リッジ回帰の係数の最大の差は `1.27e-14`（倍精度の丸め誤差の範囲）、ラッソ回帰は `3.87e-9`（座標降下法の収束条件の差）でした。0 になる特徴量の集合は、4 つの α すべてで完全に一致しました
- **ラッソ回帰は特徴量を選ぶ**。α = 10 で `RM LSTAT`・`PTRATIO^2`・`PTRATIO LSTAT` の 3 つが 0 になり、α = 50 で `LSTAT^2` も加わって 4 つになりました。リッジ回帰は係数を小さくするだけで 0 にはしないので、「どの特徴量が要らないか」を教えてくれるのはラッソ回帰だけです

分割が違うので、Java 版の数値とは一致しません。**「検証データで選んだ α がテストデータでも線形回帰に勝つ」「ラッソ回帰が交互作用の項を先に落とす」という傾向は Java 版と同じ** でした。

### 実データのテスト

```rust
#[test]
fn 検証データで選んだリッジ回帰はテストデータで線形回帰に勝つ() {
    // …
    assert!((best.alpha - 1.0).abs() < 1e-12);
    assert!((linear_score - 0.4728).abs() < 1e-4, "{linear_score}");
    assert!((ridge_score - 0.5019).abs() < 1e-4, "{ridge_score}");
    assert!(ridge_score > linear_score);
}

#[test]
fn 実データでもラッソ回帰はlinfaと同じ特徴量を零にする() {
    // …
    assert_eq!(
        own.zero_columns(),
        vec!["RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT"]
    );
    assert_eq!(own.zero_columns(), library.zero_columns());
}
```

### 品質チェック

```bash
cargo fmt --check
cargo clippy --all-targets -- -D warnings
cargo test
```

第 12 章を足した時点で、`cargo test` は 264 件（ライブラリの単体テスト 222 件と、実データのテスト 42 件）すべて通ります。データが無い環境（`ML_DATA_DIR=/nonexistent cargo test`）でも全部通り、この章の実データのテスト 6 件は理由を標準エラーに出して早期に戻ります。

clippy の指摘はありませんでした。`usize` から `f64` への変換に `cast_precision_loss` が付く場所は、件数を変換する小さな関数に閉じ込めて `#[allow]` を 1 つだけ置いています。

## 12.12 可視化について

Rust 版では Notebook と可視化を扱いません。α と決定係数の関係、係数の縮み方、ラッソ回帰で 0 になっていく様子（正則化パス）の可視化は、[Python 版の第 12 章](../python/12-regularization-and-model-selection.md) と [Kotlin 版の第 12 章](../kotlin/12-regularization-and-model-selection.md) の可視化の節を参照してください。Rust 版の `run_ridge_experiments` は `Experiment`（α・訓練 R²・検証 R²・係数の絶対値の合計）のベクタを返すので、そのままグラフの元データになります。

## 12.13 まとめ

この章では、正則化とモデル選択を Rust の TDD で実装しました。

| 作ったもの | 自作 | 突き合わせたライブラリ |
|-----------|------|-------------------|
| リッジ回帰 | `ridge::fit`（中心化 + 対角に α を足して解く） | linfa-elasticnet の `ElasticNet::ridge()`（差 `1.27e-14`） |
| ラッソ回帰 | `lasso::fit`（座標降下法 + 軟しきい値作用素） | linfa-elasticnet の `ElasticNet::lasso()`（差 `3.87e-9`、0 になる列は完全一致） |
| 標準化 + 2 次の項 | `PolynomialScaler`（第 9 章の道具を組み合わせ） | — |
| モデル選択 | `run_ridge_experiments`・`best_experiment` | — |

Rust らしさが出たのは次の 5 点です。

1. **`enum` で章の失敗を積み上げる** — `Chapter02` と `Chapter09` を包む `enum` に `From` を実装すると、`?` だけで 3 つの章の関数を混ぜて書ける。Go 版のように毎回包み直す必要がない
2. **ndarray がそのまま行列** — Java 版が `MatrixOperations` として足した `plus`・`times`・`identity` は、`dot`・`t()` と `for` の 3 行で済む
3. **タプルの分解代入** — 中心化の結果 4 つをタプルで返し、使わない要素は `_` で捨てる。入れ物の型を作らずに多値を返せる
4. **小さな値型に `Copy`** — `f64` を 4 つ持つ `Experiment` は `Copy`。所有権を気にせず値として回せる
5. **`f64` は `Ord` ではない** — `max_by_key` が使えないので `reduce` で比べる。「同点ならどちらを選ぶか」を書き手が決め、テストで固定する

ライブラリとの突き合わせでは、**目的関数の流儀を式で書き出してから実測する** ことが効きました。`penalty = alpha / n` の読み替えだけでは足りず、「linfa は特徴量を中心化しない」というもう 1 つの違いをテストの失敗が教えてくれました。API のドキュメントは「切片を学習する」としか書いていないので、**数式レベルで一致を確かめるテストが無ければ気づけません**。

検証データで選んだリッジ回帰（α = 1.0）は、テストデータの決定係数で線形回帰を 0.4728 → 0.5019 と上回りました。ラッソ回帰は、交互作用の項から順に係数を 0 にして特徴量を選びました。次の章では、特徴量そのものを作り直す **主成分分析** を学びます。
