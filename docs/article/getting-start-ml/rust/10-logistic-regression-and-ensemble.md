---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ソフトマックスと勾配降下のロジスティック回帰、第 3 章の決定木を再利用したランダムフォレストと特徴量の重要度を Rust の TDD で自作し、trait Classifier で共通化して linfa-logistic と突き合わせる。L2 を外すと予測が完全に一致することを実測する。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T15:40:00Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。そこで、`trait Classifier` でモデルに共通する操作（`fit` と `predict`）を定義し、どのモデルも同じ関数で評価できるようにします。最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作します。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進め、[Java 版](../java/10-logistic-regression-and-ensemble.md)・[Go 版](../go/10-logistic-regression-and-ensemble.md) と対比します。Rust 版で拾う論点は次の 3 つです。

- **トレイトは型の宣言に書かなくてよい**。Java・C# の `interface` は「この型はこのインターフェースを実装する」と型の側に書く必要があるので、第 3 章の決定木を変更せずに合わせるにはアダプターのクラスが要ります。Rust は **トレイトが自分のクレートのものなら、型のほうに手を入れずに後から実装を足せます**
- **`Box<dyn Classifier>` で並べる**。モデルの種類が違っても、トレイトオブジェクトにすれば 1 つの `Vec` に入れて順に評価できます
- **ランダムフォレストは linfa に無い**（linfa-trees は決定木だけ）ので、自作が最終の実装になります（[ADR 009](../../../adr/009-rust-ml-libraries.md)）

ライブラリとの突き合わせには、linfa-logistic の `MultiLogisticRegression` を使います。データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `prepare_iris` で前処理します。乱数に `rand` の `StdRng` を使うので、訓練データとテストデータに入る行は Java 版・Go 版と違い、正解率も一致しません（第 2 章）。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 値がすべて同じなら確率は均等になる
  - [ ] 値の差が指数の比になる
  - [ ] 大きな値でもあふれない
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 2 種類・3 種類のラベルを予測する
  - [ ] 学習を繰り返すと損失が小さくなる
- [ ] ランダムフォレストを学習して予測する
  - [ ] 多数決で予測を 1 つに決める
  - [ ] ブートストラップ標本を選ぶ
  - [ ] 第 3 章の決定木を再利用する
- [ ] 特徴量の重要度を計算する
  - [ ] 決定木 1 本の重要度
  - [ ] ランダムフォレストの重要度
- [ ] どのモデルも同じ関数で評価する
  - [ ] 第 3 章の決定木を変更せずに共通のトレイトに合わせる
  - [ ] linfa のモデルも同じ関数で評価する
- [ ] 実データで linfa と正解率を突き合わせる

この章では、第 2 章の `Error` をそのまま使います。新しく起こる失敗は「学習する前に予測した」（`Error::NotFitted`）と「linfa が失敗した」（`Error::Library`）だけで、どちらも第 2 章にすでにあるからです。第 9 章のように章の `Error` を足すかどうかは、**新しい失敗が本当にあるか** で決めます。

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和 + 切片」を計算し、それを **ソフトマックス関数** で確率に変換します。

```text
z_k = w_1k * x_1 + w_2k * x_2 + ... + b_k     （品種 k のスコア）
p_k = exp(z_k) / Σ_j exp(z_j)                 （ソフトマックス）
```

### 三角測量

まず、性質を 2 つテストにします。

```rust
#[test]
fn 値がすべて同じなら確率は均等になる() {
    let probabilities = softmax(&[1.0, 1.0, 1.0]);

    for probability in &probabilities {
        assert!((probability - 1.0 / 3.0).abs() < 1e-12);
    }
}

#[test]
fn 値の差が指数の比になる() {
    let probabilities = softmax(&[0.0, 1.0]);

    assert!((probabilities[1] / probabilities[0] - std::f64::consts::E).abs() < 1e-12);
    assert!((probabilities.iter().sum::<f64>() - 1.0).abs() < 1e-12);
}
```

定義どおりに書けば、この 2 つは通ります。

```rust
pub fn softmax(z: &[f64]) -> Vec<f64> {
    let exps: Vec<f64> = z.iter().map(|value| value.exp()).collect();
    let total: f64 = exps.iter().sum();

    exps.iter().map(|value| value / total).collect()
}
```

### 大きな値でもあふれない

3 つめのテストで、この実装は壊れます。

```rust
#[test]
fn 大きな値でもあふれない() {
    // 定義どおり exp(1000) を求めると inf になり、inf / inf が NaN になる
    assert!(1000.0_f64.exp().is_infinite());

    let probabilities = softmax(&[1000.0, 1001.0]);

    assert!(probabilities.iter().all(|value| value.is_finite()));
    assert!((probabilities.iter().sum::<f64>() - 1.0).abs() < 1e-12);
}
```

```text
---- chapter10::logistic::tests::大きな値でもあふれない stdout ----

thread 'chapter10::logistic::tests::大きな値でもあふれない' (2082867) panicked at src/chapter10/logistic.rs:232:9:
assertion failed: probabilities.iter().all(|value| value.is_finite())
```

`f64::exp` は `inf` を返してもパニックせず、`inf / inf` は `NaN` になります。Rust は整数のオーバーフローならデバッグビルドでパニックしてくれますが、**浮動小数点のオーバーフローは黙って `inf` になります**。この点は Java・C#・Go と同じで、境界の値のテストを自分で書くしかありません。

直し方は、スコアから最大値を引いてから `exp` を取ることです。全部の項を同じ数で割ることになるので、確率は変わりません。

```rust
/// スコアを、合計が 1 になる確率に変換する。
/// 最大値を引いてから exp を求めるので、大きな値でもあふれない。
pub fn softmax(z: &[f64]) -> Vec<f64> {
    let max = z.iter().copied().fold(f64::NEG_INFINITY, f64::max);
    let exps: Vec<f64> = z.iter().map(|value| (value - max).exp()).collect();
    let total: f64 = exps.iter().sum();

    exps.iter().map(|value| value / total).collect()
}
```

最大値を `fold(f64::NEG_INFINITY, f64::max)` で求めているのは、第 9 章と同じ理由です。`f64` は `Ord` を実装していないので、`iter().max()` をそのまま呼べません。

## 10.4 ロジスティック回帰

### 損失と勾配

学習の良し悪しは **交差エントロピー** で測ります。正解の品種の確率の対数の平均に、マイナスを付けたものです。

```rust
/// 交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。
pub fn cross_entropy(probabilities: &[Vec<f64>], targets: &[usize]) -> f64 {
    if probabilities.is_empty() {
        return 0.0;
    }

    let count = probabilities.len() as f64;

    -probabilities
        .iter()
        .zip(targets)
        .map(|(probability, target)| (probability[*target] + EPSILON).ln())
        .sum::<f64>()
        / count
}
```

確率が 0 のとき `ln(0)` が `-inf` になるので、ごく小さい `EPSILON`（1e-12）を足します。

```rust
#[test]
fn 正解の確率が1なら交差エントロピーは0になる() {
    assert!(cross_entropy(&[vec![1.0, 0.0]], &[0]).abs() < 1e-9);
    assert!(cross_entropy(&[vec![0.5, 0.5]], &[0]) > 0.69);
}
```

ソフトマックスと交差エントロピーを組み合わせると、勾配は「確率 − 正解（正解の品種だけ 1 を引く）」という簡単な形になります。

### バッチ勾配降下法

NumPy の行列演算の代わりに、`Vec<Vec<f64>>` と添字で書きます。

```rust
/// ソフトマックスと勾配降下法で学習するロジスティック回帰。
#[derive(Debug, Clone)]
pub struct LogisticRegression {
    learning_rate: f64,
    epochs: usize,
    classes: Vec<String>,
    /// weights[特徴量][品種]
    weights: Vec<Vec<f64>>,
    bias: Vec<f64>,
    losses: Vec<f64>,
}

impl Default for LogisticRegression {
    /// 学習率 1.0、繰り返し 5000 回のロジスティック回帰。
    fn default() -> Self {
        LogisticRegression::new(DEFAULT_LEARNING_RATE, DEFAULT_EPOCHS)
    }
}
```

Java 版・Kotlin 版は「引数なしのコンストラクタ」と「既定引数」で既定値を表しました。Rust には既定引数もオーバーロードも無いので、`Default` トレイトを実装します。`LogisticRegression::default()` と `LogisticRegression::new(1.0, 5000)` の 2 通りの作り方が、名前だけで読み分けられます。

学習の本体です。

```rust
fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()> {
    let first = x.first().ok_or(Error::NotFitted)?;
    let rows: Vec<Vec<f64>> = x.iter().map(|features| features.values.clone()).collect();

    let mut classes: Vec<String> = t.to_vec();
    classes.sort_unstable();
    classes.dedup();

    let targets: Vec<usize> = t
        .iter()
        .map(|label| {
            classes
                .iter()
                .position(|name| name == label)
                .ok_or_else(|| Error::MissingColumn(label.clone()))
        })
        .collect::<Result<_>>()?;

    self.weights = vec![vec![0.0; classes.len()]; first.columns.len()];
    self.bias = vec![0.0; classes.len()];
    self.classes = classes;

    let mut losses = Vec::with_capacity(self.epochs);

    for _ in 0..self.epochs {
        let probabilities: Vec<Vec<f64>> =
            rows.iter().map(|row| softmax(&self.scores(row))).collect();

        losses.push(cross_entropy(&probabilities, &targets));

        // 確率 − 正解（正解の品種だけ 1 を引く）
        let errors: Vec<Vec<f64>> = probabilities
            .iter()
            .zip(&targets)
            .map(|(probability, target)| {
                let mut error = probability.clone();
                error[*target] -= 1.0;

                error
            })
            .collect();

        self.update(&rows, &errors);
    }

    self.losses = losses;

    Ok(())
}
```

`fit` は `&mut self` を取ります。所有権の規則により、**学習しているあいだは誰もこのモデルを読めません**。Java 版のようにフィールドを書き換えている途中のオブジェクトを別のスレッドから読む、という事故が起きません。

その代わり、`probabilities` を作る行で `self.scores(row)`（`&self`）を呼び、そのあと `self.update(...)`（`&mut self`）を呼ぶ、という順に分ける必要があります。`self` を借りたまま `&mut self` のメソッドを呼ぼうとすると、借用検査に止められます。一度 `Vec` に集めてから更新するこの形は、**勾配降下法の「全データの勾配を集めてから 1 回だけ動かす（バッチ）」という定義とちょうど一致します**。

```rust
#[test]
fn 学習を繰り返すと損失が小さくなる() {
    let x = vec![column(0.2), column(0.3), column(2.3), column(2.5)];
    let t = labels(&["setosa", "setosa", "virginica", "virginica"]);

    let mut model = LogisticRegression::default();
    model.fit(&x, &t).unwrap();

    let losses = model.losses();

    assert_eq!(losses.len(), 5000);
    assert!(losses[0] > losses[losses.len() - 1]);
}
```

## 10.5 モデル共通のトレイト

### トレイトは型の宣言に書かなくてよい

`fit` と `predict` をトレイトにします。

```rust
/// `fit` で学習し、`predict` でラベルを予測する分類器。
pub trait Classifier {
    /// 訓練データで学習する。
    fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()>;

    /// 特徴量ごとのラベルを予測する。
    fn predict(&self, x: &[Features]) -> Result<Vec<String>>;
}
```

第 3 章の `DecisionTree` は、`fit` が `Result<&mut Self>` を返す形でした。シグネチャが違うので、そのままではトレイトに合いません。Java 版は `DecisionTreeClassifier implements Classifier` というアダプターのクラスを 1 つ書きました。Rust では、**第 3 章のファイルを 1 行も変えずに、この章のファイルに実装を足せます**。

```rust
/// 第 3 章の決定木を、変更せずにこの章のトレイトに合わせる。
/// トレイトがこのクレートのものなので、型のほうに手を入れずに後から実装を足せる。
/// Java・C# の `interface` は型の宣言に書く必要があるので、アダプターのクラスが要る。
impl Classifier for DecisionTree {
    fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()> {
        DecisionTree::fit(self, x, t)?;

        Ok(())
    }

    fn predict(&self, x: &[Features]) -> Result<Vec<String>> {
        DecisionTree::predict(self, x)
    }
}
```

これは **孤児規則（orphan rule）** のうち「トレイトが自分のクレートのものなら実装してよい」に当たる書き方です。型もトレイトも他人のものだと書けませんが、今回はトレイトが自分のものなので通ります。Go 版は「メソッドの名前と形が合えばインターフェースを満たす」構造的部分型だったので、そもそも宣言も実装も要りませんでした。

| 言語 | 既存の型を共通の型に合わせるには |
|------|----------------------------|
| Rust | トレイトが自分のクレートのものなら `impl Trait for 既存の型` を後から書ける |
| Java・C# | 型の宣言に `implements`／`:` が要るので、アダプターのクラスを書く |
| Go | 構造的部分型。メソッドの形が合えば、何も書かなくても満たす |

同じ名前の `fit` が 2 つできるので、呼び分けは `DecisionTree::fit(self, ...)`（第 3 章のもの）と `Classifier::fit(&mut model, ...)`（この章のもの）のように、どちらの実装かを明示します。名前が衝突していることがコンパイラに分かるので、**どちらか一方だけをうっかり呼ぶ、ということが起きません**。

### 同じ関数で評価する

```rust
/// 訓練データとテストデータの正解率。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Score {
    pub train: f64,
    pub test: f64,
}

impl Score {
    /// モデルを訓練データで学習させてから、訓練データとテストデータの正解率を求める。
    pub fn evaluate(
        model: &mut dyn Classifier,
        split: &TrainTestSplit<Features, String>,
    ) -> Result<Score> {
        model.fit(&split.x_train, &split.t_train)?;

        Ok(Score {
            train: accuracy(&model.predict(&split.x_train)?, &split.t_train),
            test: accuracy(&model.predict(&split.x_test)?, &split.t_test),
        })
    }
}
```

`&mut dyn Classifier` は **トレイトオブジェクト** です。「`Classifier` を満たす何か」への参照で、実体がどの型かは実行時に決まります。ジェネリクス（`impl Classifier`）にすると型ごとに関数が複製されて速くなりますが、種類の違うモデルを 1 つの `Vec` に入れて順に回せません。表示のために並べたいので、ここは `dyn` を選びました。

```rust
/// 名前とモデル。表示する順に並べる。
pub fn models() -> Vec<(String, Box<dyn Classifier>)> {
    vec![
        (
            format!("決定木（深さ {SHALLOW_DEPTH}）"),
            Box::new(DecisionTree::with_max_depth(SHALLOW_DEPTH)),
        ),
        ("ロジスティック回帰".to_string(), Box::new(LogisticRegression::default())),
        // …ランダムフォレスト 2 つと linfa のロジスティック回帰
    ]
}
```

Java 版は入れた順を保つために `LinkedHashMap` を使いました。Rust の `Vec<(String, Box<dyn Classifier>)>` は最初から順のある入れ物なので、並びのために別の型を選ぶ必要がありません。

## 10.6 ランダムフォレスト

### 仕組み

1. 訓練データから、重複を許して同じ件数を選ぶ（**ブートストラップ標本**）
2. 特徴量の一部だけを使って決定木を 1 本学習する
3. 1 と 2 を木の本数だけ繰り返す
4. 予測は全部の木の **多数決** で決める

### 多数決とブートストラップ標本

```rust
#[test]
fn 多数決で予測を1つに決める() {
    let votes = vec![labels(&["a", "b"]), labels(&["a", "c"]), labels(&["b", "c"])];

    assert_eq!(majority_vote(&votes), labels(&["a", "c"]));
}

#[test]
fn 同数なら先に現れた予測を選ぶ() {
    let votes = vec![labels(&["b"]), labels(&["a"])];

    assert_eq!(majority_vote(&votes), labels(&["b"]));
}
```

「同数なら先に現れたほう」は、第 3 章の `majority` がすでに満たしている性質です。そのまま呼びます。

```rust
/// サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。
pub fn majority_vote(votes: &[Vec<String>]) -> Vec<String> {
    let Some(first) = votes.first() else {
        return Vec::new();
    };

    (0..first.len())
        .map(|sample| {
            let labels: Vec<String> = votes.iter().map(|vote| vote[sample].clone()).collect();

            crate::chapter03::majority(&labels)
        })
        .collect()
}
```

ブートストラップ標本と、木ごとに使う特徴量の選び方です。`rand` 0.8 の `StdRng` を 1 つだけ作り、森全体で使い回します（木ごとに作り直すと、全部の木が同じ標本になります）。

```rust
/// 0 から size - 1 までの行番号を、重複を許して size 個選ぶ。
fn bootstrap_sample(size: usize, rng: &mut StdRng) -> Vec<usize> {
    (0..size).map(|_| rng.gen_range(0..size)).collect()
}

/// Fisher-Yates で並べ替えて、先頭から `count` 個を選ぶ。
fn choose(columns: &[String], count: usize, rng: &mut StdRng) -> Vec<String> {
    let mut shuffled = columns.to_vec();

    for i in (1..shuffled.len()).rev() {
        let j = rng.gen_range(0..(i + 1));
        shuffled.swap(i, j);
    }

    let chosen = &shuffled[..count.min(shuffled.len())];

    // 列の順は元のまま残す
    columns
        .iter()
        .filter(|column| chosen.contains(column))
        .cloned()
        .collect()
}
```

選んだ列は、元の表の順に並べ直します。列の順が木ごとにばらばらだと、特徴量の重要度を足し合わせるときに対応を取りにくくなるからです。

### 森を作る

1 本分の情報は構造体にまとめます。特徴量の重要度を求めるときに、その木が使った列と標本の行番号が要るためです。

```rust
/// ランダムフォレストの 1 本分。使った特徴量の列、ブートストラップ標本の行番号、学習した決定木。
#[derive(Debug, Clone)]
pub struct FittedTree {
    pub columns: Vec<String>,
    pub rows: Vec<usize>,
    pub model: DecisionTree,
}
```

深さを制限するかどうかは `Option<usize>` で表します。Java 版は `-1` を「制限なし」の印にする定数を置きましたが、`Option` なら **「無い」ことが型に書いてあります**。

```rust
let mut model = match self.max_depth {
    Some(max_depth) => DecisionTree::with_max_depth(max_depth),
    None => DecisionTree::unlimited(),
};
```

`with_max_depth` は、`new` の結果の一部だけを差し替える **構造体更新記法** で書きました。

```rust
pub fn with_max_depth(n_estimators: usize, max_features: usize, max_depth: usize, seed: u64) -> Self {
    RandomForest {
        max_depth: Some(max_depth),
        ..RandomForest::new(n_estimators, max_features, seed)
    }
}
```

**ランダムフォレストは linfa 0.8.1 に無い** ので、この自作が最終の実装です。linfa-trees にあるのは決定木（`DecisionTree`）だけで、森を束ねる仕組みは公開 API に見当たりませんでした（ADR 009）。Java 版は Tribuo の `RandomForestTrainer`、C# 版は ML.NET の `FastForest` と突き合わせましたが、Rust 版にはその節がありません。**ライブラリが揃った言語でも、章によっては自作が最終になります。**

## 10.7 特徴量の重要度

### 計算方法

決定木は、分割のたびに不純度（ジニ不純度）を下げます。「その分割でどれだけ不純度が下がったか」を、分割に使った特徴量の得点として足していきます。件数が多い分割ほど重みを大きくしたいので、その節に来たデータの件数を掛けます。

```text
その分割の得点 = 件数 × (分割前のジニ不純度 − 分割後の重み付き平均の不純度)
```

第 3 章の `Split` は `impurity` に「分割後の左右の重み付き平均」を持っているので、そのまま使えます。

### 決定木 1 本の重要度

木をたどるところは、第 3 章の `enum Tree` の `match` で書きます。

```rust
/// 木をたどって、分割ごとに減った不純度（件数で重み付け）を足し込む。
fn accumulate(
    tree: &Tree,
    x: &[Features],
    t: &[String],
    totals: &mut Vec<(String, f64)>,
) -> Result<()> {
    let Tree::Node { split, left, right } = tree else {
        return Ok(());
    };

    let decrease = t.len() as f64 * (gini(t) - split.impurity);

    if let Some(entry) = totals.iter_mut().find(|(name, _)| *name == split.feature) {
        entry.1 += decrease;
    }

    // …左右に振り分けて再帰する
}
```

`let ... else` は「この形でなければ早期に戻る」という書き方です。`Tree::Leaf` のときは何もしないので、`match` の腕を 2 つ書くより短くなります。Java 版は `switch` のパターンマッチで `case Leaf ignored -> Stream.empty()` と書いた場所です。

最後に、合計が 1 になるように割ります。

```rust
#[test]
fn 使った特徴量だけが重要度を持つ() {
    let (x, t) = separable();
    let mut model = DecisionTree::with_max_depth(1);
    Classifier::fit(&mut model, &x, &t).unwrap();

    let importances = tree_importances(model.tree().unwrap(), &x, &t).unwrap();

    assert_eq!(importances[0].0, "花弁幅");
    assert!((importances[0].1 - 1.0).abs() < 1e-12);
    assert!(importances[1].1.abs() < 1e-12);
}
```

### ランダムフォレストの重要度

森の重要度は、**木ごとに正規化してから平均** します。ほかの言語版と同じ方式です。

```rust
for fitted in forest.trees() {
    let Some(tree) = fitted.model.tree() else {
        continue;
    };

    let sampled: Vec<Features> = fitted.rows.iter().map(|row| x[*row].clone()).collect();
    let sample_x = RandomForest::select_columns(&sampled, &fitted.columns)?;
    let sample_t: Vec<String> = fitted.rows.iter().map(|row| t[*row].clone()).collect();

    // 木ごとに正規化してから平均する
    for (feature, value) in tree_importances(tree, &sample_x, &sample_t)? {
        if let Some(entry) = totals.iter_mut().find(|(name, _)| *name == feature) {
            entry.1 += value / count;
        }
    }
}
```

木が使わなかった特徴量は、その木では 0 として扱われます（`totals` に足されないだけ）。木ごとに正規化しないと、たまたま大きく不純度を下げた木の影響が強く出ます。

## 10.8 linfa のロジスティック回帰と突き合わせる

linfa-logistic の `MultiLogisticRegression` を、同じ `Classifier` トレイトに合わせます。トレイトが自分のものなので、**linfa のモデルにも直接実装を書けます**が、ここでは「学習したパラメータを取り出して自前で予測する」形にして、自作の重みと並べて見られるようにしました。

```rust
impl Classifier for LinfaLogisticRegression {
    fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()> {
        let dataset = Dataset::new(records(x)?, Array1::from(t.to_vec()));

        let model = MultiLogisticRegression::default()
            .max_iterations(self.max_iterations)
            .alpha(self.alpha)
            .fit(&dataset)
            .map_err(|error| Error::Library(error.to_string()))?;

        // 学習した重みを取り出しておくと、自作の重みと並べて見られる
        self.classes = model.classes().to_vec();
        self.weights = model.params().rows().into_iter().map(|row| row.to_vec()).collect();
        self.bias = model.intercept().to_vec();

        Ok(())
    }

    // …取り出した重みでスコアを求め、最大の品種を返す
}
```

第 3 章の linfa の決定木は、ラベルを `usize` に番号付けしてから渡しました。linfa-logistic は **`String` のラベルをそのまま受け取れます**（`linfa::Label` は `Eq + Hash + Ord + Clone` を満たす型なら何でもよいため）。番号付けと戻し方を書かずに済みます。

### L2 の強さをそろえると予測が完全に一致する

linfa の `MultiLogisticRegression` は、**既定で L2 正則化の強さ（`alpha`）が 1.0** です。自作は正則化を入れていないので、このままでは別のモデルになります。`alpha` をごく小さくして（`1e-8`）比べました。

```rust
#[test]
fn 正則化を外すと自作とlinfaの予測が完全に一致する() {
    let Some(split) = iris_split() else {
        return;
    };

    let mut ours = LogisticRegression::default();
    ours.fit(&split.x_train, &split.t_train).expect("学習できること");

    let mut theirs = LinfaLogisticRegression::new(1000, WEAK_PENALTY);
    theirs.fit(&split.x_train, &split.t_train).expect("学習できること");

    assert_eq!(
        ours.predict(&split.x_test).expect("予測できること"),
        theirs.predict(&split.x_test).expect("予測できること")
    );
}
```

テストデータ 45 件の予測が **1 件も違わず一致** しました。学習の解き方は違う（自作はバッチ勾配降下法、linfa は argmin の L-BFGS）のに、同じ目的関数の最小値にたどり着いているということです。第 3 章の決定木では「浅い木だけ一致する」だったのに対し、こちらは完全に一致します。

実測した組み合わせは次のとおりです。

| 実装 | 設定 | 訓練データ | テストデータ | 自作との予測の差 |
|------|------|-----------|-------------|----------------|
| 自作 | 1000 回 | 0.9333 | 0.8889 | — |
| 自作 | 5000 回（既定） | 0.9333 | 0.8889 | — |
| 自作 | 20000 回 | 0.9333 | 0.8889 | — |
| linfa | 100 回・alpha = 1.0（既定） | 0.9143 | 0.9333 | 4 件 |
| linfa | 1000 回・alpha = 1.0 | 0.9143 | 0.9333 | 4 件 |
| linfa | 100 回・alpha ≈ 0 | 0.9333 | 0.8889 | **0 件** |
| linfa | 1000 回・alpha ≈ 0 | 0.9333 | 0.8889 | **0 件** |
| linfa | 10000 回・alpha ≈ 0 | 0.9333 | 0.8889 | **0 件** |

読み取れることは 2 つです。

- **繰り返し回数はどちらも足りている**。自作は 1000 回で、linfa は 100 回で結果が落ち着きます。Java 版では Tribuo の既定（5 エポック）が足りず、エポック数を上げると結果が変わりました。linfa の既定（100 回）は L-BFGS なので、同じ「既定」でも十分な回数です
- **違いを生んでいたのは正則化**。linfa の既定の L2（alpha = 1.0）は、この分割では訓練データの正解率を下げ（0.9333 → 0.9143）、テストデータの正解率を上げました（0.8889 → 0.9333）。正則化が過学習を抑えた、という教科書どおりの動きです。正則化そのものは第 12 章で扱います

「同じロジスティック回帰」でも、**既定の正則化があるかどうかで結果が変わります**。ライブラリと比べるときは、名前ではなく設定をそろえます。この点は Java 版（Tribuo のエポック数）・Kotlin 版と同じ教訓で、そろえるべき設定が違うだけです。

## 10.9 実データで突き合わせる

```bash
cargo run --bin chapters -- chapter10
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9524	0.9111
ロジスティック回帰	0.9333	0.8889
ランダムフォレスト（100 本）	1.0000	0.8889
ランダムフォレスト（100 本・深さ 2）	0.9429	0.9111
linfa ロジスティック回帰	0.9333	0.8889

ランダムフォレスト（100 本）の特徴量の重要度:
がく片長さ	0.1761
がく片幅	0.1432
花弁長さ	0.2092
花弁幅	0.4715
```

この表示は `tests/iris_models.rs` の `実行するとモデルごとの正解率と重要度を表示する` で固定しています。結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではない**。深さを制限しないランダムフォレスト（0.8889）は、第 3 章の深さ 2 の決定木（0.9111）より低くなりました。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。木の深さを 2 に制限した森は、決定木と同じ 0.9111 です。分割が違うので数値は Java 版と違いますが、**この傾向は Java 版・Kotlin 版と同じ** でした
- **木の本数や深さで結果が動く**。10 本にすると 0.9333、深さ 3 に制限すると 0.9333 でした。テストデータ 45 件では 1 件の違いが 0.0222 の差になるので、1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います
- **ランダムフォレストの重要度は、複数の特徴量に分散する**。木ごとに使える特徴量を 2 つに絞っているので、花弁幅を使えない木は別の特徴量で分割するためです。第 3 章の深さ 2 の木は、2 回とも花弁幅で分割していました。花弁幅が最も大きい（0.4715）という順位は Java 版と同じです

「深く育てた森より、浅くした森のほうがテストデータで強い」ことは、テストにも残しました。

```rust
#[test]
fn 森の木を深く育てるとテストデータの正解率が下がる() {
    // …深さ無制限と深さ 2 を比べる
    assert!((deep.train - 1.0).abs() < 1e-12);
    assert!(deep.test < shallow_score.test, "{deep:?} < {shallow_score:?}");
}
```

テストの実行結果です。

```bash
cargo test
```

第 9 章・第 10 章を足した時点で、`cargo test` は 117 件すべて通ります。データが無い環境（`ML_DATA_DIR=/nonexistent cargo test`）でも全部通り、実データのテストは理由を標準エラーに出して早期に戻ります。

## 10.10 Notebook で探索する

Rust 版では Notebook と可視化を扱いません。モデルごとの正解率のグラフ、ロジスティック回帰の損失の推移、モデル別の特徴量の重要度、森の大きさと正解率の関係は、[Python 版の 10.11 節](../python/10-logistic-regression-and-ensemble.md) と [Kotlin 版の 10.11 節](../kotlin/10-logistic-regression-and-ensemble.md) の可視化を参照してください。Rust 版の `LogisticRegression::losses()`（繰り返しごとの損失）と `forest_importances`（列の順に並んだ重要度）は、ほかの言語版と同じ形のデータを返すので、同じ観点で読めます。分割が違うので、値そのものは一致しません。

## 10.11 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、共通のトレイトで linfa と並べて評価しました。

| モデル | 自作したもの | 突き合わせたライブラリ |
|-------|------------|-------------------|
| ロジスティック回帰 | `LogisticRegression`（ソフトマックス + バッチ勾配降下法） | linfa-logistic の `MultiLogisticRegression`（L2 を外すと予測が完全に一致） |
| ランダムフォレスト | `RandomForest`・`FittedTree`・`majority_vote` | **linfa に無いので自作が最終**（ADR 009） |
| 特徴量の重要度 | `tree_importances`・`forest_importances` | — |

Rust らしさが出たのは次の 4 点です。

1. **数値として正しい実装** — ソフトマックスは、定義どおりでは大きな値で `NaN` になる。`f64` のオーバーフローはパニックしないので、境界の値のテストで見つけ、最大値を引く方法で直した
2. **トレイトを後から実装する** — トレイトが自分のクレートのものなら、既存の型に手を入れずに `impl Classifier for DecisionTree` と書ける。Java 版が必要としたアダプターのクラスが要らない
3. **`&mut self` と借用検査が設計を導く** — 学習中はモデルを読めないので、「全データの勾配を集めてから 1 回だけ動かす」というバッチ勾配降下法の形に自然になる
4. **「無い」を `Option` で表す** — 深さの制限を `Option<usize>` にすると、Java 版のような「−1 は制限なし」という約束事が要らない

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
