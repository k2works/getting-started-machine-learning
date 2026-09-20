---
type: Article
title: "第 11 章: 評価指標と交差検証"
description: "混同行列・適合率・再現率・F 値・ROC 曲線・AUC・K 分割交差検証を Rust の TDD で自作し、linfa の confusion_matrix・roc・area_under_curve・cross_validate_single と突き合わせる。linfa の混同行列は呼ぶ向きで適合率と再現率が入れ替わること、linfa の交差検証は行を並べ替えないことを実測する。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T14:45:16Z }
---

# 第 11 章: 評価指標と交差検証

## 11.1 はじめに

第 10 章の最後で、iris のテストデータ 45 件で測った正解率はモデルごとに数件しか違わず、「どのモデルが優れているか」を言い切れませんでした。理由は 2 つあります。

- **正解率という 1 つの数字では、間違い方の違いが見えない**。生存者を見逃したのか、死亡者を生存と言い過ぎたのかが、同じ正解率に潰れてしまいます
- **1 回の分け方で測った値は、たまたまで動く**。分け方を変えれば順位が入れ替わることがあります

この章では、その 2 つに手を入れます。前半は **混同行列** から求める適合率・再現率・F 値と、閾値を動かしながら性能を見る **ROC 曲線・AUC** です。後半は、データを k 個に分けて全部を一度ずつテストデータにする **K 分割交差検証** です。

どれも linfa 0.8 に同じものがあります（`confusion_matrix`・`roc`・`area_under_curve`・`cross_validate_single`）。ADR 009 のとおり、まず自作してから linfa と突き合わせます。この章の突き合わせでは、**linfa の API の「向き」と「既定」がこちらの思い込みと違う** ところが 2 つ見つかりました。実測して記録します。

## 11.2 正解率だけでは足りない理由

`Survived.csv` はタイタニック号の乗客のデータで、生存（`Survived` が 1）した人は死亡（0）した人より少なくなっています。正解ラベルの数が偏っていると、全員を「死亡」と予測するだけのモデルでも正解率はそれなりに高く出ます。生存者を 1 人も見つけられないのに、正解率だけを見ると当たっているように見えます。

そこで、予測の当たり外れを 4 つに分けて数える **混同行列** を使います。ここでは「生存」を正例（見つけたいほう）とします。

| | 正例と予測 | 負例と予測 |
|---|-----------|-----------|
| **実際は正例** | TP（真陽性） | FN（偽陰性） |
| **実際は負例** | FP（偽陽性） | TN（真陰性） |

混同行列から、目的に応じた指標を求めます。

| 指標 | 式 | 意味 |
|------|-----|------|
| 適合率（precision） | TP / (TP + FP) | 正例と予測したうち、本当に正例だった割合 |
| 再現率（recall） | TP / (TP + FN) | 本当の正例のうち、正例と予測できた割合 |
| F 値（F1） | 2 × 適合率 × 再現率 / (適合率 + 再現率) | 適合率と再現率の調和平均 |

回帰の評価指標（RMSE・MAE・R²）は第 7 章の `chapter07::metrics` で作ったので、この章ではそのまま使い回します。

## 11.3 TODO リストの作成

**TODO リスト**:

- [ ] 混同行列を数える
  - [ ] どちらのラベルを正例にするかを指定できる
  - [ ] 正解と予測の件数が違えば失敗を返す
- [ ] 適合率・再現率・F 値を求める
  - [ ] 分母が 0 のときは 0 にする
- [ ] 評価関数を差し替えられるようにする
- [ ] ROC 曲線と AUC を求める
  - [ ] 完全に分けられれば 1、逆順なら 0 になる
  - [ ] 正例か負例が片方しか無ければ失敗を返す
- [ ] K 分割のテストデータを作る
  - [ ] ほぼ均等な件数に分ける
  - [ ] どの行もちょうど一度だけテストデータになる
  - [ ] 並べ替えあり（シード付き）と並べ替えなしの両方を作る
- [ ] 交差検証で分割ごとのスコアを求める
- [ ] 分類と回帰を同じ交差検証にかけられるようにする
- [ ] linfa の `confusion_matrix`・`roc`・`area_under_curve` と突き合わせる
- [ ] linfa の `cross_validate_single` と突き合わせる
- [ ] 実データで交差検証の平均を表示する

## 11.4 混同行列を数える

### Red: 数え方のテスト

正解 `[1 1 1 0 0]` に対して予測 `[1 1 0 0 1]` なら、TP=2・FN=1・FP=1・TN=1 です。

```rust
/// 正解 [1 1 1 0 0]、予測 [1 1 0 0 1] の混同行列。TP=2, FN=1, TN=1, FP=1。
fn sample() -> ConfusionMatrix {
    ConfusionMatrix::of(
        &labels(&["1", "1", "1", "0", "0"]),
        &labels(&["1", "1", "0", "0", "1"]),
        &"1".to_string(),
    )
    .unwrap()
}

#[test]
fn 混同行列は四つの数を数える() {
    let matrix = sample();

    assert_eq!(matrix.true_positive, 2);
    assert_eq!(matrix.false_negative, 1);
    assert_eq!(matrix.false_positive, 1);
    assert_eq!(matrix.true_negative, 1);
}
```

### Green: bool の組を match で網羅する

フィールド名に注意が要ります。`fn` は Rust のキーワードなので `fn` という名前は付けられません。略さず `true_positive`・`false_negative` と書きます。

```rust
/// 2 値分類の混同行列。正例（見つけたいほう）を決めて、予測の当たり外れを 4 つに分けて数える。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ConfusionMatrix {
    /// 実際は正例で、正例と予測した件数（真陽性）。
    pub true_positive: usize,
    /// 実際は負例で、正例と予測した件数（偽陽性）。
    pub false_positive: usize,
    /// 実際は正例で、負例と予測した件数（偽陰性）。
    pub false_negative: usize,
    /// 実際は負例で、負例と予測した件数（真陰性）。
    pub true_negative: usize,
}
```

数える処理は、`if` を並べる代わりに **2 つの `bool` の組を `match` で 4 通り全部書きます**。

```rust
for (truth, prediction) in actual.iter().zip(predicted) {
    // 「実際が正例か」「予測が正例か」の 2 つの bool の組を match で 4 通り全部書く。
    // どれか 1 つを書き忘れるとコンパイルが通らないので、数え落としが起きない
    match (truth == positive, prediction == positive) {
        (true, true) => matrix.true_positive += 1,
        (false, true) => matrix.false_positive += 1,
        (true, false) => matrix.false_negative += 1,
        (false, false) => matrix.true_negative += 1,
    }
}
```

Java 版は `if (isPositive && predictedPositive) … else if (predictedPositive) … else if (isPositive) … else …` と書きました。読み手は「この `else` はどの場合を指すのか」を頭の中で補う必要があり、1 本落としても静かに動きます。Rust の `match` は網羅性をコンパイラが検査するので、`(false, false)` を書き忘れるとコンパイルが通りません。**4 つの場合を 4 行で並べて書けて、しかも落ちていないことが保証される** のがこの章で最初に効いた Rust らしさです。

`(bool, bool)` の組に対する網羅性検査が働くのは、`bool` が「2 つの値しか取らない型」だとコンパイラが知っているからです。Go 版のように `if` を並べる言語では、同じ安心は得られません。

### 件数が違うときは黙って切り詰めない

`zip` は短いほうで止まるので、件数が違っても静かに動いてしまいます。これは第 7 章の評価指標と同じ落とし穴なので、同じように先に確かめます。

```rust
/// 件数が同じでなければ失敗を返す。短いほうに合わせて黙って切り詰めない。
fn require_same_size<T>(actual: &[T], predicted: &[T]) -> Result<()> {
    if actual.len() != predicted.len() {
        return Err(Error::LengthMismatch {
            left: actual.len(),
            right: predicted.len(),
        });
    }

    Ok(())
}
```

失敗の型は第 2 章の `Error` をそのまま使います。この章で新しく起こる失敗は「件数が違う」（`LengthMismatch`）と「分割の数が正しくない」（`Library`）だけで、どちらもすでにあるからです。章ごとに `enum` を増やすかどうかは、**新しい失敗が本当に増えたか** で決めます。

```rust
#[test]
fn 件数が違えば失敗する() {
    let error = ConfusionMatrix::of(&labels(&["1"]), &labels(&["1", "0"]), &"1".to_string())
        .unwrap_err();

    assert_eq!(error.to_string(), "件数が違います: 1 と 2");
}
```

## 11.5 適合率・再現率・F 値

### 分母が 0 になる場合

Rust の `f64` は、`0.0 / 0.0` を `NaN` にします。パニックにはなりません。正例を 1 件も予測しなければ `TP + FP` が 0 になるので、割り算を包んでおきます。

```rust
/// 分母が 0 なら 0 を返す割り算。
fn ratio(numerator: f64, denominator: f64) -> f64 {
    if denominator == 0.0 {
        0.0
    } else {
        numerator / denominator
    }
}
```

指標はどれも 1 行です。

```rust
/// 適合率。正例と予測したうち、本当に正例だった割合。
pub fn precision(&self) -> f64 {
    ratio(
        count(self.true_positive),
        count(self.true_positive + self.false_positive),
    )
}

/// F 値。適合率と再現率の調和平均。
pub fn f1_score(&self) -> f64 {
    let precision = self.precision();
    let recall = self.recall();

    ratio(2.0 * precision * recall, precision + recall)
}
```

`usize` から `f64` への変換は `as` です。clippy の `cast_precision_loss` が「精度が落ちるかもしれない」と警告するので、件数を `f64` にする小さな関数を 1 つ作り、そこにだけ `#[allow]` を付けます。

```rust
/// 件数を f64 にする。評価指標の割り算に使う。
#[allow(clippy::cast_precision_loss)]
fn count(value: usize) -> f64 {
    value as f64
}
```

「抑制を 1 か所に閉じ込める」のは第 9 章と同じやり方です。変換の理由をその 1 か所に書いておけば、あとから読む人が `as` を見るたびに考え直さずに済みます。

```rust
#[test]
fn 正例と予測した件数が零なら適合率は零になる() {
    let matrix =
        ConfusionMatrix::of(&labels(&["1", "0"]), &labels(&["0", "0"]), &"1".to_string()).unwrap();

    assert!(matrix.precision().abs() < 1e-12);
    assert!(matrix.f1_score().abs() < 1e-12);
}
```

## 11.6 評価関数を Box\<dyn Fn\> で渡す

交差検証は「分割ごとに学習して、テストデータを採点する」手順です。採点の中身（正解率か、適合率か、RMSE か）は差し替えたいので、**評価関数を値として渡します**。

Java 版は `ToDoubleBiFunction<List<T>, List<T>>` に `Metric<T>` という名前を付けた関数型インターフェースを作りました。Rust に「関数型インターフェース」はありませんが、`Fn` トレイトがあります。

```rust
/// 正解と予測から 1 つのスコアを求める評価関数。
///
/// Java 版は `@FunctionalInterface` を 1 つ宣言してメソッド参照を渡したが、Rust では
/// 「呼べるもの」を `Box<dyn Fn ...>` で持つ。正例のラベルを捕まえた閉包も同じ型に入る。
pub type Metric<T> = Box<dyn Fn(&[T], &[T]) -> Result<f64>>;
```

`Box<dyn Fn>` にする理由は、**関数ごとに型が違う** からです。Rust では閉包 1 つ 1 つが匿名の別の型なので、「正解率を求める関数」と「適合率を求める閉包」をそのまま同じ `Vec` に入れられません。`dyn Fn` にして `Box` に入れると、呼べることだけを約束した 1 つの型にそろいます。第 10 章の `Box<dyn Classifier>` とまったく同じ仕組みです。

混同行列の指標は、正例のラベルを決めれば評価関数になります。

```rust
/// 混同行列から求める指標を、正例を決めて評価関数に変える。
///
/// 返す閉包は `positive` を持ち去る（move する）ので、呼び出し側の変数より長生きできる。
pub fn classification_metric<T: PartialEq + Clone + 'static>(
    score: fn(&ConfusionMatrix) -> f64,
    positive: T,
) -> Metric<T> {
    Box::new(move |actual, predicted| Ok(score(&ConfusionMatrix::of(actual, predicted, &positive)?)))
}
```

`move` が要るのは所有権のためです。`positive` は関数の引数なので、関数から戻ると消えます。`move` を付けると閉包が `positive` を **持ち去って** 自分の中に持つので、戻り値の閉包はあとから安全に呼べます。Java 版なら「実質 final なローカル変数はラムダに捕まえられる」で済む話ですが、Rust では「誰がその値を持っているか」を書き手が選びます。

`score` の型が `fn(&ConfusionMatrix) -> f64` であることにも意味があります。`ConfusionMatrix::precision` のようなメソッドは **関数ポインタ** としてそのまま渡せるので、ここは `Box<dyn Fn>` にしなくて済みます。

```rust
#[test]
fn 評価関数にすると正解と予測から直に採点できる() {
    let metric = classification_metric(ConfusionMatrix::precision, "1".to_string());
    let score = metric(
        &labels(&["1", "1", "1", "0", "0"]),
        &labels(&["1", "1", "0", "0", "1"]),
    )
    .unwrap();

    assert!((score - 2.0 / 3.0).abs() < 1e-12);
}
```

第 7 章の `root_mean_squared_error`・`mean_absolute_error` は、シグネチャが `fn(&[f64], &[f64]) -> Result<f64>` なので `Box::new(root_mean_squared_error)` と書くだけで `Metric<f64>` になります。**関数をそのまま値にできる** ので、アダプターを書かずに前の章の資産をつなげられます。

## 11.7 ROC 曲線と AUC

### 閾値を動かす

適合率と再現率は「正例と予測するかどうか」を決めたあとの指標です。確率を返すモデルなら、「どこで線を引くか」（閾値）自体を動かせます。閾値を高くすれば適合率が上がって再現率が下がり、低くすればその逆になります。

**ROC 曲線** は、閾値を高いほうから下げながら、横軸に偽陽性率（FPR = FP / 負例の数）、縦軸に真陽性率（TPR = TP / 正例の数）を取った曲線です。左上に寄るほどよいモデルで、その下の面積が **AUC** です。完全に分けられれば 1、当てずっぽうなら 0.5、順序が逆なら 0 になります。

### Red: 3 つの極端な場合

```rust
#[test]
fn 完全に分けられればaucは一になる() {
    let curve = roc_curve(&[0.9, 0.8, 0.2, 0.1], &[true, true, false, false]).unwrap();

    assert!((auc(&curve) - 1.0).abs() < 1e-12, "AUC = {}", auc(&curve));
}

#[test]
fn 順が逆ならaucは零になる() {
    let curve = roc_curve(&[0.1, 0.2, 0.8, 0.9], &[true, true, false, false]).unwrap();

    assert!(auc(&curve).abs() < 1e-12, "AUC = {}", auc(&curve));
}

#[test]
fn 混ざっているとaucは中間の値になる() {
    // 正例のスコア 0.9・0.4、負例のスコア 0.6・0.1。組は 4 つで、正例が上なのは 3 つ
    let curve = roc_curve(&[0.9, 0.6, 0.4, 0.1], &[true, false, true, false]).unwrap();

    assert!((auc(&curve) - 0.75).abs() < 1e-12, "AUC = {}", auc(&curve));
}
```

3 つ目は **三角測量** です。AUC は「正例と負例を 1 つずつ選んだとき、正例のスコアのほうが高い確率」と等しいので、手で数えた 3/4 と一致するはずだ、という別の道筋から答えを出しています。

### Green: スコアの降順に 1 件ずつ足す

```rust
/// ROC 曲線の 1 点。`threshold` 以上を正例と予測したときの偽陽性率と真陽性率。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct RocPoint {
    pub threshold: f64,
    /// 偽陽性率（FPR）。負例のうち、誤って正例と予測した割合。
    pub false_positive_rate: f64,
    /// 真陽性率（TPR）。正例のうち、正しく正例と予測できた割合。
    pub true_positive_rate: f64,
}
```

並べ替えで `f64` の比較が出てきます。`f64` は `NaN` があるせいで `Ord` を実装していないので、`sort_by` に渡す比較を自分で選ぶ必要があります。ここでは `total_cmp` を使います（第 9 章の分位数と同じ判断です）。

```rust
// スコアの降順。同じスコアなら入力の順のまま（安定ソート）
let mut order: Vec<usize> = (0..scores.len()).collect();
order.sort_by(|left, right| scores[*right].total_cmp(&scores[*left]));
```

あとは高いほうから 1 件ずつ正例に加え、スコアが変わるたびに点を打ちます。

```rust
for index in order {
    // スコアが変わる直前までを 1 つの閾値としてまとめる
    if scores[index] != previous && (true_positive > 0 || false_positive > 0) {
        curve.push(RocPoint {
            threshold: previous,
            false_positive_rate: count(false_positive) / count(negatives),
            true_positive_rate: count(true_positive) / count(positives),
        });
    }

    previous = scores[index];

    if labels[index] {
        true_positive += 1;
    } else {
        false_positive += 1;
    }
}
```

面積は台形則です。

```rust
/// ROC 曲線の下の面積（AUC）を台形則で求める。
pub fn auc(curve: &[RocPoint]) -> f64 {
    curve
        .windows(2)
        .map(|pair| {
            let width = pair[1].false_positive_rate - pair[0].false_positive_rate;

            width * (pair[0].true_positive_rate + pair[1].true_positive_rate) / 2.0
        })
        .sum()
}
```

`windows(2)` は「隣り合う 2 つ」を順に借用として渡すメソッドです。添字を 2 つ使って `curve[i]` と `curve[i + 1]` と書くより、**範囲外を踏む余地が無い** ぶん安全で、意図も読み取りやすくなります。

### 片方しか無ければ失敗を返す

正例か負例が 0 件だと、割り算の分母が 0 になって曲線を描けません。`NaN` を返すのではなく、失敗にします。

```rust
if positives == 0 || negatives == 0 {
    return Err(Error::Library(
        "正例と負例が両方ないと ROC 曲線を描けません".to_string(),
    ));
}
```

## 11.8 K 分割交差検証

### 分け方は 2 つ用意する

K 分割は「行の位置を k 個のかたまりに分け、1 つをテストデータ、残りを訓練データにする」だけです。第 2 章の `shuffle`（rand 0.8 の Fisher-Yates）を位置のベクタに使い回します。

```rust
/// 交差検証の 1 回分の分け方。行の位置（0 始まり）を訓練データとテストデータに分けて持つ。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Fold {
    pub train: Vec<usize>,
    pub test: Vec<usize>,
}
```

この章では **並べ替えあり** と **並べ替えなし** の 2 つを作ります。理由は 11.11 節で分かります。linfa の交差検証は行を並べ替えないので、突き合わせるには「並べ替えない自作」が要るのです。

```rust
/// 並べ替えずに、先頭から順に k 個のかたまりに分ける。
///
/// 件数が割り切れないときは、余りを先頭の分割から 1 件ずつ配る。linfa の `cross_validate` は
/// この分け方（並べ替えなし）なので、突き合わせるときはこちらを使う。
pub fn k_fold_sequential(n_samples: usize, n_splits: usize) -> Result<Vec<Fold>> { … }

/// シード付きの乱数で行を並べ替えてから k 個のかたまりに分ける。
pub fn k_fold(n_samples: usize, n_splits: usize, seed: u64) -> Result<Vec<Fold>> { … }
```

余りの配り方は `usize::from(bool)` で書けます。

```rust
let size = positions.len() / n_splits + usize::from(index < positions.len() % n_splits);
```

Rust は `bool` を数値として使えませんが、`usize::from(true)` は 1 です。Java 版の `(i < n % k ? 1 : 0)` と同じことを、「変換をしている」と明示して書けます。

### 性質をテストで固定する

具体的な分け方そのものより、**満たすべき性質** をテストにします。

```rust
#[test]
fn テストデータは重ならず全体を覆う() {
    let folds = k_fold(10, 5, 0).unwrap();
    let mut all: Vec<usize> = folds.iter().flat_map(|fold| fold.test.clone()).collect();
    all.sort_unstable();

    assert_eq!(all, (0..10).collect::<Vec<usize>>());
}

#[test]
fn 訓練データとテストデータは交わらない() {
    for fold in k_fold(10, 5, 0).unwrap() {
        assert_eq!(fold.train.len(), 8);

        for position in &fold.test {
            assert!(!fold.train.contains(position));
        }
    }
}
```

分割の数が 2 未満、または件数より多いときは失敗にします。

```rust
#[test]
fn 分割の数が少なすぎると失敗する() {
    let error = k_fold(10, 1, 0).unwrap_err();

    assert_eq!(
        error.to_string(),
        "ライブラリが失敗しました: 分割の数は 2 以上 10 以下にしてください: 1"
    );
}
```

### 交差検証の手順

```rust
/// 分割ごとに新しいモデルを作って訓練データで学習し、テストデータの予測を評価関数で採点する。
///
/// Java 版は `Supplier<Model<T>>` を受け取ったが、Rust では「モデルを作る閉包」を
/// `&dyn Fn() -> Box<dyn Model<T>>` で受け取る。分割ごとに新しいモデルを作るので、
/// 前の分割で学習した重みが残らない。
pub fn cross_validate<T: Clone>(
    make_model: &dyn Fn() -> Box<dyn Model<T>>,
    x: &[Features],
    t: &[T],
    folds: &[Fold],
    metric: &Metric<T>,
) -> Result<Vec<f64>> {
    folds
        .iter()
        .map(|fold| {
            let mut model = make_model();

            model.fit(&pick(x, &fold.train), &pick(t, &fold.train))?;

            let predicted = model.predict(&pick(x, &fold.test))?;

            metric(&pick(t, &fold.test), &predicted)
        })
        .collect()
}
```

戻り値が `Result<Vec<f64>>` であることに注目してください。`map` の中は `Result<f64>` を返しますが、`collect()` は `Vec<Result<f64>>` にも `Result<Vec<f64>>` にもできます。後者を型で指定すると、**どれか 1 つでも失敗した時点で全体が失敗になり、残りは評価されません**。Java 版の `DoubleStream` は例外で同じことをしますが、Rust では「失敗しうる」ことが戻り値の型に出ています。

Java 版は `DoubleStream` で遅延評価にして「取り出した分だけ学習する」ことを見せました。Rust でも `Iterator` は遅延ですが、`collect()` した時点で全部回ります。遅延のまま返したければ `impl Iterator` を返せますが、`Result` と組み合わせると読みにくくなるので、ここでは全部回して `Vec` で返しています。**遅延評価は目的ではなく手段** なので、必要になってから入れます。

## 11.9 モデルをトレイトで共通化する

分類（正解ラベルが `String`）と回帰（`f64`）を、同じ交差検証にかけたいので、トレイトをジェネリックにします。

```rust
/// 訓練データで学習し、特徴量からラベルを予測するモデル。
pub trait Model<T> {
    /// 訓練データで学習する。
    fn fit(&mut self, x: &[Features], t: &[T]) -> Result<()>;

    /// 特徴量ごとのラベルを予測する。
    fn predict(&self, x: &[Features]) -> Result<Vec<T>>;
}
```

第 3 章の決定木は、**型に手を入れずに** このトレイトを実装できます。

```rust
/// 第 3 章の決定木を、変更せずにこの章のトレイトに合わせる。
///
/// トレイトがこのクレートのものなので、`DecisionTree` に手を入れずに実装を足せる。
/// Java 版・C# 版では `implements` を型の宣言に書けないので、アダプターのクラスが要った。
impl Model<String> for DecisionTree {
    fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()> {
        DecisionTree::fit(self, x, t)?;

        Ok(())
    }

    fn predict(&self, x: &[Features]) -> Result<Vec<String>> {
        DecisionTree::predict(self, x)
    }
}
```

Java 版は `DecisionTreeModel implements Model<String>` というアダプターのクラスを書きました（`LinearRegressionModel` も同じ）。Rust では、**トレイトか型のどちらかが自分のクレートのものなら** あとから `impl` を足せます（孤児ルール）。第 10 章の `Classifier` と同じ話ですが、章をまたいで 2 つ目のトレイトを同じ型に足せることが、この設計の効きどころです。

第 7 章の線形回帰は、`fit` が「学習したモデルを返す関数」なので、学習の前後を `Option` で表す薄い型で包みます。

```rust
#[derive(Debug, Clone, Default)]
pub struct LinearRegressionModel {
    model: Option<LinearModel>,
}

impl Model<f64> for LinearRegressionModel {
    fn fit(&mut self, x: &[Features], t: &[f64]) -> Result<()> {
        self.model = Some(fit(x, t)?);

        Ok(())
    }

    fn predict(&self, x: &[Features]) -> Result<Vec<f64>> {
        self.model.as_ref().ok_or(Error::NotFitted)?.predict(x)
    }
}
```

Java 版は「`model` が `null` なら `IllegalStateException`」でした。Rust では `null` が無いので `Option<LinearModel>` で「まだ無い」を表し、`ok_or` で `Error::NotFitted` に変えます。**「無い」が型に出ているので、確かめ忘れるとコンパイルが通りません。**

```rust
#[test]
fn 学習する前に予測すると失敗する() {
    let model = LinearRegressionModel::new();

    assert_eq!(
        Model::predict(&model, &[column(1.0)])
            .unwrap_err()
            .to_string(),
        "学習してから予測してください"
    );
}
```

呼び出しが `Model::predict(&model, …)` になっているのは、`LinearRegressionModel` に固有のメソッドとトレイトのメソッドが同じ名前になったときに、どちらを呼ぶかを明示するためです。Rust では名前が衝突してもエラーにならず、**呼ぶ側が選べます**。

## 11.10 linfa の評価指標と突き合わせる

### 混同行列は「呼ぶ向き」で適合率と再現率が入れ替わる

linfa の `confusion_matrix` は、`linfa::prelude::*` を `use` すると `Array1` に生えるメソッドです。

```rust
let matrix = truth
    .confusion_matrix(&prediction)
    .map_err(|error| Error::Library(error.to_string()))?;
```

最初は linfa のドキュメントの例どおりに `prediction.confusion_matrix(&truth)` と書きました。単体テストは通ったのに、実データでこうなりました。

```text
適合率: 自作 0.7907 / linfa 0.5312
```

0.5312 は 34 / (34 + 30) で、自作の **再現率** です。linfa の実装を読むと、混同行列は「レシーバを行、引数を列」として数え、`precision()` は `matrix[(0,0)] / (matrix[(0,0)] + matrix[(1,0)])`、つまり **1 列目の合計で割った値** です。したがって予測をレシーバにすると、`precision()` が返すのは適合率ではなく再現率になります。linfa のドキュメントの例は `prediction.confusion_matrix(&ground_truth)` と書いているので、例のまま使うと名前と意味がずれます。

単体テストが通ってしまったのは、テストに使った正解と予測で適合率と再現率がたまたま同じ値（どちらも 2/3）だったからです。**対称な例だけでは向きの間違いを捕まえられません。** そこで、2 つが食い違う例を足しました。

```rust
#[test]
fn linfaの適合率と再現率は自作と一致する() {
    // 正解 [1 1 1 1 0 0]、予測 [1 1 0 0 0 1]。TP=2, FP=1, FN=2 で適合率と再現率が食い違う
    let actual = labels(&["1", "1", "1", "1", "0", "0"]);
    let predicted = labels(&["1", "1", "0", "0", "0", "1"]);
    let scores = linfa_scores(&actual, &predicted).unwrap();
    let matrix =
        super::super::metrics::ConfusionMatrix::of(&actual, &predicted, &"1".to_string()).unwrap();

    assert!((scores.precision - matrix.precision()).abs() < 1e-6);
    assert!((scores.recall - matrix.recall()).abs() < 1e-6);
    assert!((scores.precision - 2.0 / 3.0).abs() < 1e-6);
    assert!((scores.recall - 0.5).abs() < 1e-6);
}
```

正例がどちらになるかにも決まりがあります。linfa はラベルを並べ替えてから、2 値のときだけ逆順にします。つまり **大きいほうのラベルが正例** です。`Survived` は `"0"` と `"1"` なので `"1"`（生存）が正例になり、自作と同じ向きになりました。ラベルが `"生存"`・`"死亡"` のような文字列だと、辞書順の大きいほうが正例になります。

もう 1 つ、linfa の混同行列は **`f32` で数えます**。`precision()` などの戻り値も `f32` なので、`f64::from` で広げて持ち、突き合わせは `1e-6` の許容で行います。

```rust
/// linfa の混同行列から取り出したスコア。linfa は f32 で返すので f64 に広げて持つ。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LinfaScores {
    pub accuracy: f64,
    pub precision: f64,
    pub recall: f64,
    pub f1_score: f64,
}
```

### ROC は Pr 型と &[bool] で受け取る

linfa の `roc` は、確率を `linfa::dataset::Pr`、正解を `&[bool]` で受け取ります。`Pr` は「0 以上 1 以下の `f32`」を表す型で、`Pr::new` で包みます。

```rust
#[allow(clippy::cast_possible_truncation)]
let probabilities: Vec<Pr> = scores.iter().map(|score| Pr::new(*score as f32)).collect();

let curve = probabilities
    .as_slice()
    .roc(labels)
    .map_err(|error| Error::Library(error.to_string()))?;

Ok(f64::from(curve.area_under_curve()))
```

`f64` の確率をそのまま渡せない、というのがクレートの版が型を分ける話の一種です。linfa が使う数値の型は `f32` で、こちらの計算は `f64` です。**型が違えば渡せない** ので、変換を書く場所が 1 か所に決まります。黙って落ちないぶん、どこで精度が落ちるかが読めます。

linfa の ROC は内部でスコアの昇順に数えていて、曲線の座標もこちらとは向きが違います。それでも面積は一致するはずだ、という仮説を三角測量にしました。

```rust
#[test]
fn linfaのaucは自作と同じ値になる() {
    let scores = [0.9, 0.6, 0.4, 0.1];
    let truth = [true, false, true, false];
    let curve = super::super::roc::roc_curve(&scores, &truth).unwrap();

    assert!((linfa_auc(&scores, &truth).unwrap() - super::super::roc::auc(&curve)).abs() < 1e-6);
}
```

### ROC を描くための確率をどこから取るか

決定木はラベルしか返さないので、ROC 曲線のための「正例らしさ」がありません。この章の主題は評価指標なので、確率はモデル側から借ります。linfa-logistic の 2 値ロジスティック回帰に `predict_probabilities` があるので、それを使います。

ここにも「向き」の罠がありました。linfa は **件数の多いほうのクラスを正例** にします（`label_classes` が多数派を `pos` にする）。`Survived` では死亡が多数派なので、そのままでは「死亡らしさ」が返ります。学習したモデルに聞いて、望む向きと違えば裏返します。

```rust
let probabilities = model.predict_probabilities(&records(x_test)?);
let flip = model.labels().pos.class != positive;

Ok(probabilities
    .iter()
    .map(|probability| if flip { 1.0 - probability } else { *probability })
    .collect())
```

## 11.11 linfa の交差検証と突き合わせる

### 3 つの違い

linfa の `cross_validate_single` を使ってみて、自作と違う点が 3 つありました。

| 論点 | 自作 | linfa |
|------|------|-------|
| 行の並べ替え | シード付きで並べ替えてから分ける | **並べ替えない**（先頭から順にかたまりにする） |
| 戻り値 | 分割ごとのスコア（`Vec<f64>`） | **モデルごとの平均**（`Array1<f64>`、長さはモデルの数） |
| データの持ち方 | 借用（`&[Features]`） | `&mut self`（中で入れ替えながら分けるため） |

```rust
/// linfa の交差検証で、分割ごとの RMSE の平均を求める。
///
/// linfa の `cross_validate_single` は**行を並べ替えず**、先頭から順に k 個のかたまりに分ける。
/// また、分割ごとのスコアではなく**平均だけ**を返す（返り値の長さはモデルの数）。自作の
/// 交差検証と突き合わせるときは `k_fold_sequential`（並べ替えなし）のスコアの平均と比べる。
///
/// データを内部で入れ替えながら分割するので、`Dataset` は `mut` で持たなければならない。
/// 「借用している間はほかから触れない」ことをコンパイラが保証するぶん、呼び出し側の書き方が決まる。
pub fn linfa_cross_validate_rmse(x: &[Features], t: &[f64], n_splits: usize) -> Result<f64> {
    let mut dataset = Dataset::new(records(x)?, Array1::from(t.to_vec()));
    let models = vec![LinfaLinearRegression::default()];

    let scores: Array1<f64> = dataset
        .cross_validate_single(n_splits, &models, |prediction, truth| {
            let squared: f64 = prediction
                .iter()
                .zip(truth.iter())
                .map(|(predicted, actual)| (predicted - actual) * (predicted - actual))
                .sum();

            #[allow(clippy::cast_precision_loss)]
            let size = truth.len() as f64;

            Ok((squared / size).sqrt())
        })
        .map_err(|error: linfa_linear::LinearError<f64>| Error::Library(error.to_string()))?;

    scores
        .first()
        .copied()
        .ok_or_else(|| Error::Library("交差検証の結果がありません".to_string()))
}
```

`&mut` が要るのは、linfa が `Dataset` の中身を入れ替えながら分割を回すからです（コピーを作らないぶん速い）。Rust では「可変の借用は同時に 1 つだけ」なので、借りている間はこちらから覗けません。Java 版なら黙って同じ配列を書き換えられるところですが、Rust は **「今この値は貸し出し中」** をコンパイル時に知らせてくれます。

`map_err` のクロージャで `error: linfa_linear::LinearError<f64>` と型を書いているのも理由があります。`cross_validate_single` の失敗の型は「`linfa::Error` から変換できる何か」としか決まっていないので、型推論だけでは決まりません。**どのクレートの失敗を受け取るか** をこちらが指定します。

### 同じ分け方なら一致する

並べ替えなしの自作と linfa を、実データで突き合わせます。

```rust
#[test]
fn 実データの交差検証は並べ替えなしならlinfaと一致する() {
    // …（cinema.csv、5 分割）
    assert!((own - 410.42).abs() < 0.01, "RMSE = {own}");
    assert!((own - library).abs() < 1e-6, "自作 {own} / linfa {library}");
}
```

## 11.12 実データで評価する

### 簡略化した前処理

`Survived.csv` は、客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、`Survived` の文字列を正解ラベルにします。年齢の欠損値は **全体の** 平均で補います。

```rust
for row in &table.rows {
    let values = vec![
        number(row, "Pclass")?,
        row.number("Age")?.unwrap_or(age_mean),
        f64::from(u8::from(row.text("Sex")? == "male")),
    ];

    x.push(Features::new(columns.clone(), values)?);
    t.push(row.text(SURVIVED_TARGET)?.to_string());
}
```

本来は分割ごとに訓練データだけで補完すべきですが（第 2 章・第 9 章で扱ったリーク）、交差検証そのものを見せるために手順を短くしています。モジュールのドキュメントコメントにその断りを書いておきます。

`row.number("Age")?` が `Option<f64>` を返すので、補完は `unwrap_or(age_mean)` の 1 行です。Java 版の `row.number(AGE).orElse(ageMean)` と同じ形で、Go 版のように「値と ok」を受けて `if` を書く必要はありません。

`Sex` を 0/1 にするところは `f64::from(u8::from(…))` と 2 段の変換になります。Rust には `bool` から `f64` への直接の変換が無いからです。冗長に見えますが、**数値への変換を暗黙にしない** 方針の一貫した結果です。

### 実行結果

```bash
cargo run --bin chapters -- chapter11
```

```text
Survived（決定木・深さ 2）
  件数: 891
  正解率（5 分割の平均）: 0.7755
  適合率（5 分割の平均）: 0.8052
  再現率（5 分割の平均）: 0.5649
  F値（5 分割の平均）: 0.6541
  ROC 曲線の点の数: 122
  AUC（自作）: 0.8503
  AUC（linfa）: 0.8503
  混同行列（1 つ目の分割）: TP=34 FP=9 FN=30 TN=106
  適合率: 自作 0.7907 / linfa 0.7907

cinema（線形回帰）
  件数: 100
  RMSE（5 分割の平均）: 409.05
  MAE（5 分割の平均）: 324.82
  RMSE（並べ替えなし）: 自作 410.42 / linfa 410.42
```

この表示は `tests/evaluation_metrics.rs` の `第十一章の実行結果が固定される` で固定しています。読み取れることは次のとおりです。

- **正解率 0.7755 だけでは「見逃しの多さ」が見えない**。適合率 0.8052 に対して再現率 0.5649 なので、このモデルは「生存」と言い切るのに慎重で、生存者の 4 割以上を見逃しています。救命の優先順位を決める用途なら、再現率を上げる方向に調整すべきだと分かります
- **正解率と AUC は別のことを言う**。同じデータで、閾値を動かせるモデル（ロジスティック回帰）の AUC は 0.8503 でした。順位付けとしては悪くないので、「閾値 0.5 で切る」ことが足を引っ張っている可能性があります
- **自作と linfa は AUC も交差検証も一致した**。AUC は表示の桁で完全に一致（実際の差は `1e-6` 未満）、並べ替えなしの RMSE も 410.42 で一致しました。混同行列は、11.10 節の「向き」を直してから一致しました
- **分け方を変えると値が動く**。cinema の RMSE は並べ替えありで 409.05、並べ替えなしで 410.42 でした。1 回の分割で測った差がこの程度なら、モデルの優劣とは言えません

分割が違うので、Java 版の値（正解率 0.7811、適合率 0.7759、再現率 0.6306）とは一致しません。**「適合率が再現率よりはっきり高い」という傾向は Java 版と同じ** でした。

### 実データのテスト

```rust
#[test]
fn 実データの交差検証で適合率と再現率が食い違う() {
    // …
    // 深さ 2 の決定木は「生存」と言い切るのに慎重で、適合率は高いが再現率は低い
    assert!((precision - 0.8052).abs() < 1e-4, "適合率 = {precision}");
    assert!((recall - 0.5649).abs() < 1e-4, "再現率 = {recall}");
    assert!(precision > recall);
}
```

実データのテストは、ファイルが無ければ理由を標準エラーに出して早期に戻ります（Rust の標準のテストにスキップが無いため）。

```rust
/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
fn data_file(name: &str) -> Option<PathBuf> {
    let csv_file = dataset::current().join(name);

    if csv_file.exists() {
        Some(csv_file)
    } else {
        eprintln!("学習データ {name} が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}
```

### 品質チェック

```bash
cargo fmt --check
cargo clippy --all-targets -- -D warnings
cargo test
```

第 11 章を足した時点で、`cargo test` は 223 件（ライブラリの単体テスト 187 件と、実データのテスト 36 件）すべて通ります。データが無い環境（`ML_DATA_DIR=/nonexistent cargo test`）でも全部通り、この章の実データのテスト 5 件は理由を標準エラーに出して早期に戻ります。

clippy の指摘は 1 つだけありました。`Box::new(|actual, predicted| accuracy(actual, predicted))` に `redundant_closure`（閉包で包む必要がない）が出たので、`Box::new(accuracy)` に直しました。関数をそのまま値として渡せることを、静的解析が教えてくれた形です。

## 11.13 Notebook で探索する

Rust 版では Notebook と可視化を扱いません。混同行列のヒートマップ、ROC 曲線、分割ごとのスコアのばらつきの可視化は、[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と [Kotlin 版の第 11 章](../kotlin/11-evaluation-metrics-and-cross-validation.md) の可視化の節を参照してください。Rust 版の `roc_curve` は `RocPoint`（閾値・偽陽性率・真陽性率）のベクタを返すので、そのまま曲線として読めます。分割が違うので値そのものは一致しません。

## 11.14 まとめ

この章では、モデルを多面的に、偶然に左右されにくく評価する方法を Rust の TDD で実装しました。

| 作ったもの | 自作 | 突き合わせたライブラリ |
|-----------|------|-------------------|
| 混同行列・適合率・再現率・F 値 | `ConfusionMatrix` | linfa の `confusion_matrix`（**呼ぶ向きに注意**） |
| ROC 曲線・AUC | `roc_curve`・`auc` | linfa の `roc`・`area_under_curve`（一致） |
| K 分割交差検証 | `k_fold`・`k_fold_sequential`・`cross_validate` | linfa の `cross_validate_single`（**並べ替えないので `k_fold_sequential` と比べる**） |

Rust らしさが出たのは次の 5 点です。

1. **`match` の網羅性が数え落としを防ぐ** — 混同行列の 4 つの場合を `(bool, bool)` の組で並べて書ける。1 つ落とすとコンパイルが通らない
2. **閉包を値として持つ** — 評価関数は `Box<dyn Fn>`、モデルを作る手続きは `&dyn Fn() -> Box<dyn Model<T>>`。関数ポインタで済むところは `fn(&ConfusionMatrix) -> f64` にする。`move` で所有権を閉包に渡すので、戻り値の閉包が安全に生き残る
3. **トレイトを後から足せる** — `impl Model<String> for DecisionTree` と書くだけで、第 3 章の型に手を入れずに交差検証にかけられる。Java 版のアダプターのクラスが要らない
4. **「まだ無い」を `Option` で表す** — 学習前の線形回帰は `Option<LinearModel>`。`null` の確認を忘れることが起きない
5. **`&mut` が「貸し出し中」を知らせる** — linfa の `cross_validate_single` はデータを入れ替えながら分けるので `&mut` が要る。借用検査が、内部で書き換わることを呼び出し側に伝える

ライブラリとの突き合わせでは、**API の名前ではなく実装が答え** でした。linfa の `confusion_matrix` はドキュメントの例のまま使うと適合率と再現率が入れ替わり、対称なテストでは気づけませんでした。**ライブラリと突き合わせるテストは、2 つの指標が食い違う例で書く** というのが、この章で得た教訓です。

Survived の決定木は、5 分割交差検証で正解率 0.7755、適合率 0.8052、再現率 0.5649 でした。正解率だけでは分からなかった「見逃しの多さ」が、再現率で見えるようになりました。次の章では、正則化によって過学習を抑え、検証データを使ってモデルの設定を選ぶ方法を学びます。
