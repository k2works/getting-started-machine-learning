---
type: Article
title: "第 3 章: 決定木と明白な実装"
description: "ジニ不純度で分割する決定木を Rust の enum と Box で実装し、linfa の決定木と突き合わせる。match の網羅性検査、max_by_key の同点の扱い、f64 が全順序でないことを Java 版・Go 版と対比する。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T15:20:00Z }
---

# 第 3 章: 決定木と明白な実装

## 3.1 はじめに

この章で、はじめて**機械学習のアルゴリズム**を実装します。決定木（decision tree）です。第 1 章では「20 代ならきのこ派」というルールを人間が書きました。決定木は、この種のルールを**データから自動で作ります**。

テスト駆動開発の技法としては、**明白な実装**（obvious implementation）を扱います。仮実装と三角測量で少しずつ追い込むのではなく、書き方がはっきり見えているときは一気に書く、という選択です。

[Python 版の第 3 章](../python/03-decision-tree-and-obvious-implementation.md) と同じ題材・同じ TODO リストで進めます。Rust 版の見どころは次の 4 つです。

1. **`enum Tree { Leaf, Node }` と `Box` で木を表す** — Go 版のようなインターフェースの工夫が要らない
2. **`match` の網羅性をコンパイラが検査する** — Go 版は既定の分岐でエラーを返すしかなかった
3. **`max_by_key` が同点のとき最後の要素を返す** — ほかの言語版とそろえるために使えなかった。テストが 2 件落ちて気付いた
4. **自作の決定木を linfa と突き合わせる** — 浅い木では完全に一致し、深い木では分かれる。その理由を linfa の既定の停止条件から説明する

## 3.2 決定木の仕組み

### データを 2 つに分けることを繰り返す

決定木は、「ある特徴量がある値以下か」という質問でデータを 2 つに分け、それを繰り返して木を作ります。実データで学習した深さ 2 の木は、こうなりました。

```text
花弁幅 <= 0.2950
  Iris-setosa
花弁幅 > 0.2950
  花弁幅 <= 0.6900
    Iris-versicolor
  花弁幅 > 0.6900
    Iris-virginica
```

読み方はこうです。花弁幅が 0.2950 以下なら `Iris-setosa`。そうでなくて 0.6900 以下なら `Iris-versicolor`。それより大きければ `Iris-virginica`。

**このルールを人間は書いていません。** 4 つの特徴量から「花弁幅」を選び、境界を 0.2950 と 0.6900 に決めたのはアルゴリズムです。第 1 章で「年代が 20 ならきのこ」と人間が決めた部分が、データから自動で決まっています。

決定木の良いところは、**学習した結果を人間が読めること**です。ニューラルネットワークのように重みの行列を眺めても分からない、ということがありません。第 11 章で扱うランダムフォレストは、この読みやすさと引き換えに精度を取ります。

### どこで分けるかをジニ不純度で決める

「良い分割」とは、**分けた後のグループがなるべく 1 種類に偏る**分割です。偏りの度合いを測る指標が**ジニ不純度**（Gini impurity）です。

$$
\mathrm{Gini}(S) = 1 - \sum_{k} p_k^2
$$

$p_k$ はグループ $S$ の中でラベル $k$ が占める割合です。

| グループ | ジニ不純度 | 意味 |
|---------|----------|------|
| すべて同じラベル | 0 | 完全に純粋 |
| 2 種類が半々 | 0.5 | 最も混ざっている（2 種類のとき） |
| 3 種類が均等 | 2/3 ≒ 0.667 | 最も混ざっている（3 種類のとき） |

分割の良さは、左右のジニ不純度を**件数で重み付けして平均**した値で測ります。この値が最も小さくなる（特徴量, 境界）の組を総当たりで探すのが、この章のアルゴリズムです。

## 3.3 TODO リストの作成

**TODO リスト**:

- [ ] ジニ不純度を計算する
  - [ ] 一種類だけなら 0 になる
  - [ ] 二種類が半々なら 0.5 になる
  - [ ] 三種類が均等なら 2/3 になる
- [ ] いちばん多いラベルを返す
  - [ ] 同数なら先に現れたほうを返す
- [ ] 最良の分割を探す
  - [ ] 分けられないときは分割を返さない
  - [ ] 不純度がいちばん小さくなる分割を選ぶ
- [ ] 決定木を学習して予測する
  - [ ] 深さを制限しなければ訓練データを全部当てる
  - [ ] 学習する前は予測できない
- [ ] 木の深さを制限する
- [ ] 学習した木を表示する
- [ ] linfa の決定木と突き合わせる
- [ ] 実データで深さと正解率を表示する

## 3.4 ジニ不純度を計算する

### 仮実装から始める

最初のテストは「1 種類だけなら 0」です。

```rust
#[test]
fn 一種類だけならジニ不純度は零になる() {
    assert!((gini(&labels(&["setosa", "setosa"]))).abs() < 1e-12);
}
```

`assert_eq!(gini(...), 0.0)` と書かずに、**許容誤差付きの比較**にしています。ジニ不純度は割り算と引き算の結果なので、`1.0 - 1.0` がちょうど 0.0 になる保証は一般にはありません。この場合は実際 0.0 になりますが、次のテスト（2/3）では必ず誤差が出るので、最初からそろえました。

このテストは `0.0` を返す仮実装で通ります。

### 三角測量

2 つめと 3 つめのテストを足します。

```rust
#[test]
fn 二種類が半々ならジニ不純度は零点五になる() {
    assert!((gini(&labels(&["setosa", "virginica"])) - 0.5).abs() < 1e-12);
}

#[test]
fn 三種類が均等ならジニ不純度は三分の二になる() {
    let value = gini(&labels(&["setosa", "versicolor", "virginica"]));

    assert!((value - 2.0 / 3.0).abs() < 1e-12);
}
```

`2.0 / 3.0` と書いているのが大事です。`0.6666666666666666` と書くと、期待値のほうに丸め誤差を持ち込んでしまいます。**式のまま書けば、同じ計算経路で同じ誤差が出ます。**

ここからは**明白な実装**です。式が決まっているので、仮実装から少しずつ進める意味がありません。

```rust
/// ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。
pub fn gini(labels: &[String]) -> f64 {
    if labels.is_empty() {
        return 0.0;
    }

    #[allow(clippy::cast_precision_loss)]
    let total = labels.len() as f64;

    let sum: f64 = counts(labels)
        .iter()
        .map(|(_, count)| {
            #[allow(clippy::cast_precision_loss)]
            let share = *count as f64 / total;

            share * share
        })
        .sum();

    1.0 - sum
}
```

`labels.is_empty()` で先に戻るのは、0 件で割らないためです。Rust の `f64` の 0 除算はパニックせず `NaN` を返すので、**気付かないまま木の全体に `NaN` が広がります**。整数の 0 除算ならパニックしてくれますが、浮動小数点数は静かに壊れます。入口で止めました。

`as f64` に `#[allow(clippy::cast_precision_loss)]` を付けています。`usize` から `f64` への変換は 2^53 を超える件数で桁が落ちますが、この題材では起こりません。第 2 章と同じく、**承知していることを書き残すための注釈**です。

### ラベルごとの件数を「現れた順」で数える

`counts` が返すのは `Vec<(String, usize)>` です。`HashMap` ではありません。

```rust
/// ラベルごとの件数を、最初に現れた順で返す。
fn counts(labels: &[String]) -> Vec<(String, usize)> {
    let mut order: Vec<String> = Vec::new();
    let mut counted: HashMap<&str, usize> = HashMap::new();

    for label in labels {
        if !counted.contains_key(label.as_str()) {
            order.push(label.clone());
        }

        *counted.entry(label.as_str()).or_insert(0) += 1;
    }

    order
        .into_iter()
        .map(|label| {
            let count = counted[label.as_str()];

            (label, count)
        })
        .collect()
}
```

順を別に持つ理由は、第 2 章の `Table` と同じです。**Rust の `HashMap` は反復の順を保証しません。** ジニ不純度の計算だけなら順は関係ありませんが、次の `majority`（いちばん多いラベル）では「同数のとき、どちらを選ぶか」が結果を左右します。順が不定だと、**実行のたびに違う木ができます**。

`*counted.entry(label.as_str()).or_insert(0) += 1;` は Rust の定型で、「鍵が無ければ 0 を入れてから、その場所を 1 増やす」を 1 回の探索で行います。Java の `merge(key, 1, Integer::sum)`、Go の `counted[label]++` に当たります。

`HashMap<&str, usize>` の鍵が `&str`（借用）であることに注目してください。`labels` が生きている間しかこの地図は使えませんが、関数の中で閉じているので問題ありません。**鍵のために `String` を複製せずに済みます。**

### `max_by_key` が使えなかった話

いちばん多いラベルを返す関数です。テストは 2 つ。

```rust
#[test]
fn いちばん多いラベルを返す() {
    assert_eq!(
        majority(&labels(&["setosa", "virginica", "setosa"])),
        "setosa"
    );
}

#[test]
fn 同数なら先に現れたラベルを返す() {
    assert_eq!(majority(&labels(&["virginica", "setosa"])), "virginica");
}
```

2 つめが効きます。ほかの言語版（Python・Java・Go）はすべて「同数なら先に現れたほう」を選ぶので、そろえないと木の形が変わります。

素直に書くなら、標準ライブラリの `max_by_key` を使いたくなります。

```rust
// これは望む挙動にならない
pub fn majority(labels: &[String]) -> String {
    counts(labels)
        .into_iter()
        .max_by_key(|(_, count)| *count)
        .map(|(label, _)| label)
        .unwrap_or_default()
}
```

実際に書いて走らせると、**2 件落ちます**。

```text
failures:

---- chapter03::decisiontree::tests::同数なら先に現れたラベルを返す stdout ----

thread 'chapter03::decisiontree::tests::同数なら先に現れたラベルを返す' panicked at src/chapter03/decisiontree.rs:339:9:
assertion `left == right` failed
  left: "setosa"
 right: "virginica"

---- chapter03::decisiontree::tests::木を字下げ付きの文字列にする stdout ----

thread 'chapter03::decisiontree::tests::木を字下げ付きの文字列にする' panicked at src/chapter03/decisiontree.rs:403:9:
assertion `left == right` failed
  left: "花弁幅 <= 0.8000\n  setosa\n花弁幅 > 0.8000\n  virginica\n"
 right: "花弁幅 <= 0.8000\n  setosa\n花弁幅 > 0.8000\n  versicolor\n"

test result: FAILED. 11 passed; 2 failed; 0 ignored; 0 measured; 25 filtered out
```

原因は `max_by_key` の仕様です。標準ライブラリのドキュメントにこう書かれています。

> If several elements are equally maximum, the last element is returned.

**同点のときは最後の要素**です。`min_by_key` は逆に最初の要素を返します。この非対称は、反復子を 1 回しか走査しない実装で「`>=` で更新するか `>` で更新するか」を選んだ結果です。

言語ごとに違います。

| 言語 | 同点のときに返るもの |
|------|------------------|
| Rust の `Iterator::max_by_key` | **最後**の要素 |
| Rust の `Iterator::min_by_key` | 最初の要素 |
| Java の `Stream.max(comparator)` | 実装依存（順序付きストリームでは最初のものが残る） |
| Python の `max` | 最初の要素 |
| Go | 標準の関数が無いので自分で書く |

落ちたテストを受けて、厳密な不等号で書き直しました。

```rust
/// いちばん多いラベルを返す。同数なら先に現れたほうを選ぶ。
///
/// `max_by_key` は同点のとき**最後**の要素を返すので使えない。
/// ほかの言語版と同じ「同数なら先に現れたほう」にするため、厳密な不等号で比べる。
pub fn majority(labels: &[String]) -> String {
    let mut best = String::new();
    let mut best_count = 0;

    for (label, count) in counts(labels) {
        if count > best_count {
            best_count = count;
            best = label;
        }
    }

    best
}
```

`count > best_count`（`>=` ではない）が、「同数なら先に現れたほうを残す」を表しています。9 行に増えましたが、**意図がコードに書いてあります**。`max_by_key` を使った 5 行版は短いものの、「同点のとき何が返るか」はドキュメントを読まないと分かりません。

この節は、**テストが仕様を守ってくれた**例でもあります。「同数なら先」という決まりは、単独で見れば些細です。しかし 2 件めの落ちたテスト（木の表示）が示すように、**木の形が変わり、最終的な正解率まで変わります**。ほかの言語版とそろえるという約束を、1 行のテストが守りました。

## 3.5 最良の分割を探す

### 分割を表す構造体

```rust
/// 決定木の分割。特徴量の値が境界以下なら左、境界より大きければ右へ進む。
#[derive(Debug, Clone, PartialEq)]
pub struct Split {
    pub feature: String,
    pub threshold: f64,
    pub impurity: f64,
}
```

Java 版は `record Split(String feature, double threshold, double impurity)` でした。Rust には `record` に当たる構文はありませんが、`#[derive(Debug, Clone, PartialEq)]` の 1 行でほぼ同じものが手に入ります。`Eq` が無いのは `f64` を持つからで、理由は第 2 章の `Features` と同じです。

### テスト

```rust
/// 3 種類に分かれる小さなデータ。
fn three_species() -> (Vec<Features>, Vec<String>) {
    (
        vec![
            column(0.2), column(0.3),
            column(1.3), column(1.5),
            column(2.3), column(2.5),
        ],
        labels(&[
            "setosa", "setosa",
            "versicolor", "versicolor",
            "virginica", "virginica",
        ]),
    )
}

#[test]
fn 分けられないときは分割を返さない() {
    let x = vec![column(0.2), column(0.3)];
    let t = labels(&["setosa", "setosa"]);

    assert_eq!(best_split(&x, &t).unwrap(), None);
}

#[test]
fn 不純度がいちばん小さくなる分割を選ぶ() {
    let (x, t) = three_species();

    let split = best_split(&x, &t).unwrap().expect("分割が見つかること");

    assert_eq!(split.feature, "花弁幅");
    assert!((split.threshold - 0.8).abs() < 1e-12);
}
```

`three_species` は架空の値です。実データの行は記事にもテストにも書きません。0.2・0.3 が setosa、1.3・1.5 が versicolor、2.3・2.5 が virginica という、実データの傾向だけを真似た 6 件です。

期待する境界 0.8 は、0.3 と 1.3 の**中点**です。境界を「隣り合う 2 つの値の中点」に置くのは、決定木の実装の定石です。片方の値そのものを境界にすると、`<=` と `<` のどちらで比べるかで結果が変わります。

`best_split` の戻り値は `Result<Option<Split>>` です。第 2 章の `Row::number` と同じ形で、「見つかった／見つからなかった／失敗した」の 3 つを表します。「分けられない」（すでに 1 種類しかない、または値がすべて同じ）は失敗ではないので、`Err` ではなく `Ok(None)` です。**エラーと「無い」を型で区別する**のは、Rust・F#・Scala の共通点です。Go 版では `(Split, bool, error)` の 3 つ組になりました。

### 実装

```rust
/// 左右の不純度の重み付き平均が最も小さくなる分割を返す。
/// 分けられなければ `None`。同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。
pub fn best_split(x: &[Features], t: &[String]) -> Result<Option<Split>> {
    if x.is_empty() || gini(t) == 0.0 {
        return Ok(None);
    }

    let mut best: Option<Split> = None;

    for feature in &x[0].columns {
        let sorted = sort_by_feature(x, t, feature)?;

        for i in 1..sorted.len() {
            if (sorted[i].value - sorted[i - 1].value).abs() < f64::EPSILON {
                continue;
            }

            let left: Vec<String> = sorted[..i].iter().map(|pair| pair.label.clone()).collect();
            let right: Vec<String> = sorted[i..].iter().map(|pair| pair.label.clone()).collect();

            #[allow(clippy::cast_precision_loss)]
            let impurity = (left.len() as f64 * gini(&left) + right.len() as f64 * gini(&right))
                / sorted.len() as f64;

            if best
                .as_ref()
                .is_none_or(|current| impurity < current.impurity)
            {
                best = Some(Split {
                    feature: feature.clone(),
                    threshold: (sorted[i - 1].value + sorted[i].value) / 2.0,
                    impurity,
                });
            }
        }
    }

    Ok(best)
}
```

`best.as_ref().is_none_or(|current| impurity < current.impurity)` が、「まだ何も見つかっていないか、今のより良いか」を 1 行で表しています。`is_none_or` は Rust 1.82 で入ったメソッドで、`map_or(true, ...)` と同じ意味です。`as_ref()` は `Option<Split>` を `Option<&Split>` に変えます。これが無いと `best` の所有権がクロージャに移ってしまい、次の行で `best = Some(...)` と代入できません。**「中を覗くだけ」を `as_ref()` で表す**のは、`Option` を扱うときの定型です。

ここでも**厳密な不等号**（`<`）です。`<=` にすると「同じ不純度なら後の分割」になり、`majority` と同じ落とし穴にはまります。列の順で前の特徴量を選ぶ、というのがほかの言語版と共通の決まりです。

### `f64` は全順序ではない

値で並べ替える部分に、Rust らしい制約が出ます。

```rust
/// 指定した列の値で安定に並べ替える。
fn sort_by_feature(x: &[Features], t: &[String], feature: &str) -> Result<Vec<ValueLabel>> {
    let mut pairs = Vec::with_capacity(x.len());

    for (features, label) in x.iter().zip(t) {
        pairs.push(ValueLabel {
            value: features.value(feature)?,
            label: label.clone(),
        });
    }

    // f64 は全順序ではないので partial_cmp を使う。値が NaN でないことは前処理で保証している
    pairs.sort_by(|a, b| a.value.partial_cmp(&b.value).expect("値が NaN でないこと"));

    Ok(pairs)
}
```

`pairs.sort()` とは書けません。`sort()` は要素が `Ord`（全順序）であることを要求しますが、**`f64` は `Ord` を実装していません**。`NaN` はどの値とも大小を比べられず、`NaN < 1.0` も `NaN > 1.0` も `NaN == 1.0` もすべて偽だからです。「どの 2 つを取っても大小が決まる」という `Ord` の約束を守れません。

`f64` が実装しているのは `PartialOrd` だけで、その比較メソッド `partial_cmp` は `Option<Ordering>` を返します。`None` になるのは、どちらかが `NaN` のときです。ここでは `expect` で「`NaN` は来ないはず」と表明しています。**第 2 章の前処理で `Features` から欠損値を除いてあるので、`NaN` は入りません。** 型が保証しているわけではないので、根拠はコメントと `expect` のメッセージに書きました。

| 言語版 | 浮動小数点数の並べ替え |
|--------|--------------------|
| Rust | `sort_by` に `partial_cmp` を渡す。`NaN` のときの扱いを書かされる |
| Java | `Comparator.comparingDouble`。`NaN` は最大として扱われる（`Double.compare` の仕様） |
| Go | `sort.Slice` に `a < b` を渡す。`NaN` があると並びが壊れるが、何も言われない |
| Python | `sorted(key=...)`。`NaN` があると並びが壊れるが、何も言われない |

**Rust だけが「`NaN` をどうするか」を明示的に決めさせます。** 煩わしい反面、「`NaN` が混ざると並べ替えが静かに壊れる」という、ほかの言語で踏みやすい地雷を避けられます。Rust 1.81 以降は `f64::total_cmp` もあり、`NaN` を含めた全順序が欲しいときはそちらを使えます。

`sort_by` は**安定なソート**です。値が同じ要素の相対順は変わりません。これも「同点なら先」の決まりを支えています。

### 浮動小数点数の落とし穴

`(sorted[i].value - sorted[i - 1].value).abs() < f64::EPSILON` は「同じ値なら分割点にしない」という判定です。同じ値の間に境界を引くと、同じ値のデータが左右に分かれてしまい、予測が値だけでは決まらなくなります。

`==` で比べずに `f64::EPSILON` との比較にしているのは、浮動小数点数の等値比較を避けるためです。ただし `f64::EPSILON`（約 2.2e-16）は「1.0 の次に表せる値との差」であって、任意の大きさの数に使える許容誤差ではありません。正規化済み（0.0〜1.0）のデータを扱うこの題材では十分です。**一般のデータに使うなら、相対誤差で比べるか、値の大きさに応じた許容誤差を決める**必要があります。

なお linfa の決定木は、同じ目的の判定を `1e-5` という固定の閾値で行っています。後で結果が分かれる原因の 1 つです。

## 3.6 決定木を学習して予測する

### 木を `enum` と `Box` で表す

決定木は「葉」か「節」のどちらかです。Rust には**判別共用体**があるので、そのまま書けます。

```rust
/// 決定木。葉か節のどちらか。
/// Rust には判別共用体があるので、Go 版のようなインターフェースの工夫は要らない。
/// 再帰する型なので、部分木は `Box` で包む。
#[derive(Debug, Clone, PartialEq)]
pub enum Tree {
    /// 予測するラベルを持つ葉。
    Leaf { label: String },
    /// 分割と左右の部分木を持つ節。
    Node {
        split: Split,
        left: Box<Tree>,
        right: Box<Tree>,
    },
}
```

`Box<Tree>` の `Box` が必要な理由は、**型の大きさをコンパイル時に決めるため**です。`Tree` が `Tree` を直接持つと、大きさが「`Tree` の大きさ + α」になって無限に膨らみます。`Box` はヒープへのポインタなので、大きさが 8 バイトに固定されます。`Box` を外すと、コンパイラがこう言います。

```text
error[E0072]: recursive type `Tree` has infinite size
help: insert some indirection (e.g., a `Box`, `Rc`, or `&`) to break the cycle
```

Java・C#・Go では、オブジェクトの参照が暗黙にポインタなので、この問題は表に出ません。Rust は**値がその場に置かれる**のが既定なので、再帰する型では「間接参照を入れる」と自分で書くことになります。代わりに、`Box` が 1 つも無い型は**ヒープを使わない**ことが読んで分かります。

言語ごとの表し方を並べると、この章でいちばん形が分かれるところです。

| 言語版 | 木の表し方 | 網羅性の検査 |
|--------|----------|------------|
| Rust | `enum Tree { Leaf { .. }, Node { .. } }` + `Box` | **コンパイラが検査する** |
| Java 21 | `sealed interface Tree` + `record Leaf` / `record Node` | 検査する（`switch` のパターンマッチ） |
| Scala・F# | `sealed trait` / 判別共用体 | 検査する |
| Go | `interface{ isTree() }` + 型スイッチ | **されない**（既定の分岐でエラーを返すしかない） |
| Python・TypeScript | クラス 2 つ、または辞書 | されない（TypeScript は判別可能ユニオンで近いことができる） |

### `match` の網羅性をコンパイラが検査する

予測は再帰で書きます。

```rust
/// 木をたどって 1 件のラベルを予測する。
pub fn predict_one(tree: &Tree, features: &Features) -> Result<String> {
    match tree {
        Tree::Leaf { label } => Ok(label.clone()),
        Tree::Node { split, left, right } => {
            if goes_left(split, features)? {
                predict_one(left, features)
            } else {
                predict_one(right, features)
            }
        }
    }
}
```

**`_ =>` の腕がありません。** `Tree` は `Leaf` と `Node` の 2 つしかなく、両方を書いたので、コンパイラは「網羅した」と認めます。ここに 3 つめの列挙子（たとえば深さ制限で作る `Pruned`）を足すと、`predict_one` も、木を表示する `write_tree` も、`match` を書いたすべての場所がコンパイルエラーになります。**直すべき場所をコンパイラが全部教えてくれます。**

Go 版ではここが型スイッチになり、こう書かざるをえませんでした。

```go
switch node := tree.(type) {
case *Leaf:
    return node.Label, nil
case *Node:
    // ...
default:
    return "", fmt.Errorf("未知の木の種類: %T", tree)
}
```

`default` の分岐は**永遠に実行されないコード**です。実行されないのにテストのカバレッジには現れ、カバーしようとすると木の種類を偽装するテストを書くことになります。Rust ではこの分岐そのものが要りません。

`Tree::Node { split, left, right }` のパターンで、3 つのフィールドを同時に取り出しています。`left` の型は `&Box<Tree>` ですが、`predict_one(left, ...)` にそのまま渡せます。`Box<T>` から `&T` への**自動的な参照外し**（deref coercion）が働くからです。

### 木を組み立てる

```rust
/// 深さの上限まで分割を繰り返して木を作る。`max_depth` が `None` なら上限なし。
pub fn build(x: &[Features], t: &[String], max_depth: Option<usize>) -> Result<Tree> {
    if max_depth == Some(0) {
        return Ok(Tree::Leaf { label: majority(t) });
    }

    let Some(split) = best_split(x, t)? else {
        return Ok(Tree::Leaf { label: majority(t) });
    };

    let mut left_x = Vec::new();
    let mut left_t = Vec::new();
    let mut right_x = Vec::new();
    let mut right_t = Vec::new();

    for (features, label) in x.iter().zip(t) {
        if goes_left(&split, features)? {
            left_x.push(features.clone());
            left_t.push(label.clone());
        } else {
            right_x.push(features.clone());
            right_t.push(label.clone());
        }
    }

    let next_depth = max_depth.map(|depth| depth - 1);

    Ok(Tree::Node {
        left: Box::new(build(&left_x, &left_t, next_depth)?),
        right: Box::new(build(&right_x, &right_t, next_depth)?),
        split,
    })
}
```

**止まる条件が 2 つ**あります。深さを使い切ったとき（`max_depth == Some(0)`）と、分割が見つからないとき（`best_split` が `None`）です。どちらも `majority(t)` を予測とする葉になります。

`let Some(split) = best_split(x, t)? else { ... };` の `let ... else` が効いています。`best_split` は `Result<Option<Split>>` を返すので、`?` で `Option<Split>` になり、`None` なら葉を返して抜けます。**2 段の入れ子が 1 行で解けています。** Go 版では `if !ok { return ... }` が別行になり、Java 版では `Optional` の `isEmpty()` を挟みました。

`max_depth.map(|depth| depth - 1)` で深さを 1 つ減らします。`None`（上限なし）のときは `map` が何もしないので `None` のままです。**「上限なし」と「あと何段」を 1 つの型で扱えている**のがここの読みどころで、番兵の値（`-1` や `Integer.MAX_VALUE`）を使う必要がありません。

最後の構造体リテラルで、`left`・`right` を先に書いて `split` を最後に置いているのは偶然ではありません。`goes_left(&split, features)?` で `split` を借用している間は `split` を移動できないので、借用が終わってから `split` をフィールドに移しています。順番を入れ替えると借用検査に引っかかります。**所有権が式の順序に現れる**場面です。

`features.clone()` と `label.clone()` の複製は避けられません。1 件のデータが左右どちらかの部分木に入り、部分木は自分のデータを持つ必要があるからです。添字のベクタ（`Vec<usize>`）だけを再帰に渡せば複製を避けられます（linfa は実際そうしています）が、この章では読みやすさを優先しました。

### `DecisionTree` で包む

scikit-learn の `fit` / `predict` に合わせた分類器で包みます。

```rust
/// 自作の決定木の分類器。`fit` で学習してから `predict` で予測する。
#[derive(Debug, Clone)]
pub struct DecisionTree {
    max_depth: Option<usize>,
    tree: Option<Tree>,
}

impl DecisionTree {
    /// 訓練データから木を作る。
    pub fn fit(&mut self, x: &[Features], t: &[String]) -> Result<&mut Self> {
        self.tree = Some(build(x, t, self.max_depth)?);

        Ok(self)
    }

    /// 特徴量ごとのラベルを予測する。
    pub fn predict(&self, x: &[Features]) -> Result<Vec<String>> {
        let tree = self.tree.as_ref().ok_or(Error::NotFitted)?;

        x.iter()
            .map(|features| predict_one(tree, features))
            .collect()
    }
}
```

`tree: Option<Tree>` が「まだ学習していない」を表します。`predict` は `ok_or(Error::NotFitted)?` で、学習前の呼び出しを失敗にします。

```rust
#[test]
fn 学習する前は予測できない() {
    let model = DecisionTree::with_max_depth(1);

    assert_eq!(
        model.predict(&[column(0.2)]).unwrap_err().to_string(),
        "学習してから予測してください"
    );
}
```

**「学習してから予測する」を型で強制できないか**、とは考えました。`UnfittedTree` と `FittedTree` を別の型にして、`fit` が `FittedTree` を返す設計です（型状態パターン）。そうすれば学習前の `predict` は**コンパイルエラー**になり、`Error::NotFitted` も `Option<Tree>` も要りません。この章では scikit-learn 風の API にそろえることを優先して、実行時の検査にしました。型で防げるものを実行時に回しているので、**意図した妥協**です。

`fit` が `Result<&mut Self>` を返すのは、`model.fit(&x, &t)?.predict(&x)?` と繋げて書けるようにするためです。

`x.iter().map(...).collect()` が `Result<Vec<String>>` になるのは、**`Result` のベクタを「ベクタの `Result`」に畳む `FromIterator` がある**からです。1 件でも失敗すればその場で `Err` が返り、全件成功すれば `Ok(Vec<String>)` になります。Java の `Stream` では同じことをするのに例外か特別な collector が要り、Go では明示的なループになります。Rust の中で好きな部分の 1 つです。

## 3.7 木の深さを制限する

深さを制限しなければ、決定木は訓練データを**完全に暗記します**。

```rust
#[test]
fn 深さを制限しなければ訓練データを全部当てる() {
    let (x, t) = three_species();

    let mut model = DecisionTree::unlimited();
    let predictions = model.fit(&x, &t).unwrap().predict(&x).unwrap();

    assert_eq!(predictions, t);
}
```

訓練データの正解率 1.0 は嬉しい数字に見えますが、**過学習**（overfitting）の典型です。実データでも深さ制限なしの訓練データ正解率は 1.0000 になりました。テストデータでは 0.9111 です。

深さ 1 の木が 2 つの葉になることも確かめます。

```rust
#[test]
fn 深さ一なら二つの葉になる() {
    let (x, t) = three_species();

    let mut model = DecisionTree::with_max_depth(1);
    model.fit(&x, &t).unwrap();

    match model.tree().expect("学習していること") {
        Tree::Node { left, right, .. } => {
            assert!(matches!(**left, Tree::Leaf { .. }));
            assert!(matches!(**right, Tree::Leaf { .. }));
        }
        Tree::Leaf { .. } => panic!("節になること"),
    }
}
```

3 つの書き方が出ています。

1. **`..` で残りのフィールドを無視する。** `Tree::Node { left, right, .. }` は `split` を取り出しません
2. **`matches!` マクロ。** `matches!(**left, Tree::Leaf { .. })` は「このパターンに当てはまるか」を `bool` で返します。`if let` を書くほどでもないときに便利です
3. **`**left` の 2 つの `*`。** `left` は `&Box<Tree>` なので、1 つめで `Box<Tree>`、2 つめで `Tree` になります

`Tree::Leaf { .. } => panic!("節になること")` の腕を書いているので、ここでも `_ =>` は要りません。**網羅性の検査がテストにも効いています。**

### 作り方に名前を付ける

```rust
impl DecisionTree {
    /// 深さを制限しない決定木を作る。
    pub fn unlimited() -> Self {
        DecisionTree { max_depth: None, tree: None }
    }

    /// 深さの上限を指定した決定木を作る。
    pub fn with_max_depth(max_depth: usize) -> Self {
        DecisionTree { max_depth: Some(max_depth), tree: None }
    }
}
```

`DecisionTree::new(None)` と `DecisionTree::new(Some(3))` でも同じことができますが、**呼び出し側に `Some` / `None` を書かせません**。`DecisionTree::unlimited()` は読んで意味が分かります。Rust には Java の `static` ファクトリと同じ発想の関連関数があり、`new` という名前にこだわらないのが慣習です（`String::from`・`Vec::with_capacity` など）。

Rust には**名前付き引数も既定値もありません**。引数の意味を名前で伝えたければ、関数名に込めるか、設定用の構造体を作る（ビルダーパターン）ことになります。linfa の `DecisionTree::params().max_depth(Some(5)).min_weight_leaf(2.0)` がまさにビルダーです。

## 3.8 学習した木を表示する

```rust
/// 木を字下げ付きの文字列にする。
pub fn format(tree: &Tree) -> String {
    let mut out = String::new();
    write_tree(&mut out, tree, "");

    out
}

/// 木を再帰的に書き出す。
fn write_tree(out: &mut String, tree: &Tree, indent: &str) {
    match tree {
        Tree::Leaf { label } => {
            let _ = writeln!(out, "{indent}{label}");
        }
        Tree::Node { split, left, right } => {
            let deeper = format!("{indent}  ");

            let _ = writeln!(out, "{indent}{} <= {:.4}", split.feature, split.threshold);
            write_tree(out, left, &deeper);

            let _ = writeln!(out, "{indent}{} > {:.4}", split.feature, split.threshold);
            write_tree(out, right, &deeper);
        }
    }
}
```

ファイルの先頭に `use std::fmt::Write as _;` があります。`writeln!` を `String` に対して使うには `std::fmt::Write` トレイトがスコープに要るからです。`as _` は「トレイトとしては使うが、名前は持ち込まない」という書き方で、`std::io::Write`（第 2 章の `run` で使うほう）と名前がぶつかるのを避けています。**同名のトレイトが 2 つある**のは Rust の標準ライブラリの紛らわしいところで、`io::Write` はバイト列に、`fmt::Write` は文字列に書きます。

`let _ = writeln!(...)` の `let _ =` は、戻り値の `Result` を意図的に捨てています。`String` への書き込みはメモリ確保が失敗しない限り失敗しないので、ここでは無視してよいものです。**捨てていることを `let _ =` で明示する**のが Rust の作法で、そのまま書くと `unused_must_use` の警告（この設定ではエラー）になります。Go の `_ = fmt.Fprintf(...)` と同じ考え方です。

`{:.4}` は小数点以下 4 桁の書式です。`{indent}{label}` のように変数名を直接埋め込めます。

テストは出力の文字列をそのまま固定します。

```rust
#[test]
fn 木を字下げ付きの文字列にする() {
    let (x, t) = three_species();

    let mut model = DecisionTree::with_max_depth(1);
    model.fit(&x, &t).unwrap();

    assert_eq!(
        format(model.tree().unwrap()),
        "花弁幅 <= 0.8000\n  setosa\n花弁幅 > 0.8000\n  versicolor\n"
    );
}
```

このテストが、`majority` を `max_by_key` で書いたときに落ちた 2 件めです。**木の表示は、木の形を丸ごと検査するテスト**として働きます。分割の選び方が変わっても、同点の扱いが変わっても、ここが落ちます。

## 3.9 linfa の決定木と突き合わせる

ここまでは自作です。Rust には linfa という機械学習のクレート群があり、決定木も入っています（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。**自作したものをライブラリと突き合わせる**のが、本シリーズの Rust 版の進め方です。Java 版が Tribuo と、C# 版が ML.NET と突き合わせたのと同じ立場です。

### ラベルを整数に符号化する

linfa にデータを渡すには、`ndarray` の行列にする必要があります。

```rust
use linfa::prelude::*;
use linfa_trees::DecisionTree as LinfaDecisionTree;
use ndarray::{Array1, Array2};

/// linfa の決定木で学習して予測する。ラベルは文字列のままでは渡せないので、
/// 出現順に番号を振ってから渡し、予測の結果を文字列に戻す。
pub fn predict(
    x_train: &[Features],
    t_train: &[String],
    x_test: &[Features],
    max_depth: Option<usize>,
) -> Result<Vec<String>> {
    let (encoded, classes) = encode(t_train);
    let dataset = Dataset::new(records(x_train)?, Array1::from(encoded));

    let model = LinfaDecisionTree::params()
        .max_depth(max_depth)
        .fit(&dataset)
        .map_err(|error| Error::Library(error.to_string()))?;

    let predicted = model.predict(&records(x_test)?);

    Ok(predicted
        .iter()
        .map(|index| classes[*index].clone())
        .collect())
}
```

`use linfa_trees::DecisionTree as LinfaDecisionTree;` と名前を変えているのは、自作の `DecisionTree` と衝突するからです。**`as` による改名**は、こういうときのための機能です。

ラベルの符号化はこうします。

```rust
/// ラベルに出現順の番号を振る。戻り値は（番号の列, 番号から文字列への対応）。
fn encode(t: &[String]) -> (Vec<usize>, Vec<String>) {
    let mut classes: Vec<String> = Vec::new();
    let mut encoded = Vec::with_capacity(t.len());

    for label in t {
        let index = match classes.iter().position(|name| name == label) {
            Some(index) => index,
            None => {
                classes.push(label.clone());

                classes.len() - 1
            }
        };

        encoded.push(index);
    }

    (encoded, classes)
}
```

linfa の `Label` トレイトは `String` にも実装されているので、実は文字列のままでも渡せます。それでも整数にしているのは、**`Array1<L>` の要素として複製の費用が小さいから**と、第 8 章以降で混同行列やロジスティック回帰に渡すときに整数のほうが扱いやすいからです。`classes` が「番号 → 文字列」の対応表になるので、予測の結果はそれで戻します。

`match ... { Some(index) => index, None => { ...; classes.len() - 1 } }` の `None` の腕はブロックで、最後の式がその腕の値になります。**ブロックが値を持つ**のは Rust の式指向の現れで、`let index = if ... { } else { };` も同じように書けます。

行列への変換はこうです。

```rust
/// 特徴量を linfa に渡す行列にする。
fn records(x: &[Features]) -> Result<Array2<f64>> {
    let columns = x.first().map_or(0, |features| features.values.len());
    let values: Vec<f64> = x
        .iter()
        .flat_map(|features| features.values.iter().copied())
        .collect();

    Array2::from_shape_vec((x.len(), columns), values)
        .map_err(|error| Error::Library(error.to_string()))
}
```

`flat_map` で全行の値を 1 本のベクタに平らにし、`from_shape_vec` で（行数, 列数）の形に組み直します。`ndarray` の行列は**内部的には 1 本のベクタ**なので、この変換に複製以上の費用はかかりません。

`.map_err(|error| Error::Library(error.to_string()))` で、linfa と ndarray のエラーを自分の `Error` に変換しています。第 1 章では `From` を実装して `?` に自動変換させましたが、ここでは**メッセージだけ取り出して包んでいます**。外部のクレートのエラー型を自分の `enum` のバリアントに持つと、そのクレートが自分の公開 API の一部になってしまうからです（linfa の版を上げるとエラー型も変わりうる）。`Error::Library(String)` にすれば、その影響が `linfatree.rs` の中に閉じます。

`x.first().map_or(0, |features| features.values.len())` は、「空なら 0、あれば最初の行の列数」です。`map_or` は「`None` なら既定値、`Some` なら関数の結果」を 1 行で書けます。

### 単体テスト

```rust
#[test]
fn linfaの決定木で学習して予測する() {
    let x: Vec<Features> = [0.2, 0.3, 2.3, 2.5]
        .iter()
        .map(|value| features(&[*value]))
        .collect();
    let t = labels(&["setosa", "setosa", "virginica", "virginica"]);

    let predictions = predict(&x, &t, &x, Some(1)).unwrap();

    assert_eq!(predictions, t);
}
```

テストの関数名が `linfaの決定木で学習して予測する` と、`linfa` の直後に助詞が続いています。**Rust の識別子に空白は使えない**ので、`linfa の決定木で…` とは書けません。日本語のテスト名を使うときの小さな制約です。

### 浅い木では完全に一致する

実データで突き合わせます。

```rust
#[test]
fn 浅い木では自作とlinfaの予測が一致する() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let split = prepare_iris(&csv_file, 0.3, 0).expect("前処理できること");

    // 深さ 1・2 では予測が完全に一致する。
    // 深さ 3 は正解率が同じ（0.9111）でも予測が 2 件分かれ、深さ 4 以上は正解率も分かれる
    for max_depth in [1, 2] {
        let mut model = DecisionTree::with_max_depth(max_depth);
        let ours = model
            .fit(&split.x_train, &split.t_train)
            .expect("学習できること")
            .predict(&split.x_test)
            .expect("予測できること");

        let theirs = linfatree::predict(
            &split.x_train,
            &split.t_train,
            &split.x_test,
            Some(max_depth),
        )
        .expect("linfa で予測できること");

        assert_eq!(ours, theirs, "深さ {max_depth} で一致すること");
    }
}
```

**深さ 1 と 2 では、45 件のテストデータの予測が 1 件も違いません。** 正解率がたまたま同じなのではなく、予測そのものが完全に一致します。自作の実装が linfa と同じアルゴリズムを実装できている、という強い証拠です。

### 深い木では分かれる理由

深さ 3 以上では分かれます。実測はこうでした。

| 深さ | 自作（テストデータ） | linfa（テストデータ） | 予測の一致 |
|-----|-----------------|------------------|----------|
| 1 | 0.5556 | 0.5556 | 完全に一致 |
| 2 | 0.9111 | 0.9111 | 完全に一致 |
| 3 | 0.9111 | 0.9111 | **2 件違う**（正解率は同じ） |
| 4 | 0.9111 | 0.8444 | 違う |
| 5 | 0.9111 | 0.8444 | 違う |
| 制限なし | 0.9111 | 0.8444 | 違う |

深さ 3 が面白いところです。**正解率は同じ 0.9111 なのに、間違えている 4 件の中身が違います。** 正解率という 1 つの数字だけを見ていると、この違いに気付けません。

理由は、linfa の決定木が持っている**既定の停止条件**です。`DecisionTree::params()` が作る既定値を、linfa 0.8.1 の `hyperparams.rs` から引くとこうなっています。

```rust
Self(DecisionTreeValidParams {
    split_quality: SplitQuality::Gini,
    max_depth: None,
    min_weight_split: 2.0,
    min_weight_leaf: 1.0,
    min_impurity_decrease: F::cast(0.00001),
    label_marker: PhantomData,
})
```

自作の実装と違うのは、次の 4 つです。

| 項目 | linfa 0.8.1 の既定 | 自作 | 効き方 |
|------|------------------|------|-------|
| `min_weight_split` | 2.0（2 件未満の節は分割しない） | 制限なし | 1 件の節をそれ以上分けない |
| `min_weight_leaf` | 1.0（葉が 1 件未満になる分割はしない） | 制限なし | 実質的に同じ（0 件の葉は作れない） |
| `min_impurity_decrease` | 0.00001 | 制限なし | **不純度が十分に下がらない分割を却下する** |
| 同じ値とみなす差 | 1e-5 | `f64::EPSILON`（約 2.2e-16） | 近い値を同じとみなす範囲が広い |

深さ 3 以上で効くのは、主に `min_impurity_decrease` です。linfa は「分割して減る不純度が 0.00001 未満なら、分割せずに葉にする」という**事前の枝刈り**を既定で行います。自作の実装にはこれが無いので、ほんのわずかしか不純度が下がらない分割も実行します。深くなるほど「わずかにしか下がらない分割」が増えるので、差が開きます。

もう 1 つ、linfa の実装には**学習と予測で比較演算子が違う**という性質があります。学習時に左右へ振り分けるのは `records[(i, feature)] <= split_value`、予測時に左へ進む条件は `x[feature_idx] < split_value` です（`algorithm.rs`）。境界とちょうど等しい値が来たとき、学習時は左、予測時は右へ行きます。境界は 2 つの値の中点なので当たることは稀ですが、**自作と完全に一致させたいときには無視できない差**です。

`max_depth` を明示的に渡しているので、深さの上限だけは同じです。**同じ上限を渡しても結果が違う**のは、上限以外の停止条件が違うからです。

ここから持ち帰れる教訓は 2 つあります。

1. **ライブラリの既定値は仕様である。** `params()` を呼んだ時点で 5 つの決まりごとを受け入れている。何を受け入れたのかは、ドキュメントかソースを読まないと分からない
2. **正解率が同じでも、同じことをしているとは限らない。** 深さ 3 の一致は偶然で、予測を 1 件ずつ比べてはじめて違いが見える

linfa に `min_impurity_decrease(0.0)` を渡せば自作に近づけられるか、とも考えましたが、linfa 0.8.1 は `f64::EPSILON` 未満の値を拒みます。

```text
Minimum impurity decrease should be greater than zero, but was 0
```

`fit` を呼ぶ前のパラメータ検査（`ParamGuard::check_ref`）で `Err` になります。**枝刈りを完全に切ることはできない**、というのも 1 つの事実です。

## 3.10 実データで深さと正解率を表示する

### 深さと正解率

```rust
/// 正解率を比べる深さ。
const MAX_DEPTHS: [usize; 5] = [1, 2, 3, 4, 5];

/// 深さごとの正解率と、深さ 2 の決定木を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let split = prepare_iris(&dataset::current().join("iris.csv"), TEST_SIZE, SEED)?;

    writeln!(out, "深さ\t訓練データ\tテストデータ\tlinfa")?;

    for max_depth in MAX_DEPTHS {
        print_accuracy(
            out,
            &max_depth.to_string(),
            DecisionTree::with_max_depth(max_depth),
            Some(max_depth),
            &split,
        )?;
    }

    print_accuracy(out, "制限なし", DecisionTree::unlimited(), None, &split)?;

    let mut shallow = DecisionTree::with_max_depth(TREE_DEPTH_TO_SHOW);
    shallow.fit(&split.x_train, &split.t_train)?;

    writeln!(out)?;
    writeln!(out, "深さ {TREE_DEPTH_TO_SHOW} の決定木:")?;
    write!(out, "{}", format(shallow.tree().expect("学習していること")))?;

    Ok(())
}
```

`const MAX_DEPTHS: [usize; 5]` の `[usize; 5]` は「長さ 5 の配列」です。Rust では**長さが型の一部**なので、要素を 1 つ足すと `[usize; 6]` に書き換える必要があります。煩わしければ `&[usize]`（スライス）にもできますが、定数としては配列のほうが素直です。

実行します。

```text
$ cargo run --bin chapters -- chapter03
深さ	訓練データ	テストデータ	linfa
1	0.7143	0.5556	0.5556
2	0.9524	0.9111	0.9111
3	0.9619	0.9111	0.9111
4	0.9714	0.9111	0.8444
5	0.9810	0.9111	0.8444
制限なし	1.0000	0.9111	0.8444

深さ 2 の決定木:
花弁幅 <= 0.2950
  Iris-setosa
花弁幅 > 0.2950
  花弁幅 <= 0.6900
    Iris-versicolor
  花弁幅 > 0.6900
    Iris-virginica
```

読み取れることが 3 つあります。

1. **深さ 1 では足りない。** 2 つの葉しか作れないので、3 種類は分けられません。テストデータ 0.5556
2. **深さ 2 で一気に上がる。** 0.9111。花弁幅だけで 3 種類がほぼ分かれます。第 2 章の可視化で `Iris-setosa` がきれいに分かれていたことと符合します
3. **深くしても、テストデータの正解率は上がらない。** 訓練データは 0.9524 → 1.0000 と上がり続けるのに、テストデータは 0.9111 のままです。**これが過学習です**

訓練データの正解率とテストデータの正解率が離れていく様子が、表の 2 列目と 3 列目にそのまま出ています。第 9 章でこの現象を正面から扱います。

深さ 2 の木は、テストデータ 45 件のうち 41 件を正しく分類しました。テストにも書いています。

```rust
#[test]
fn 深さ二の決定木はテストデータの四十五件中四十一件を正しく分類する() {
    // ...
    assert!((accuracy(&predictions, &split.t_test) - 41.0 / 45.0).abs() < 1e-12);
}
```

期待値を `0.9111` ではなく `41.0 / 45.0` と書いているのは、第 3.4 節の `2.0 / 3.0` と同じ理由です。**丸めた値をテストに書かない**、という規律です。

### ほかの言語版と数値が一致しない

この章の正解率は、**ほかの言語版と一致しません**。第 2 章で書いたとおり、訓練データとテストデータの分け方が違うからです。木の境界（0.2950・0.6900）も、言語版ごとに違う値になります。

一致するのは次の点です。

- 深さ 1 では足りず、深さ 2 で一気に上がること
- 訓練データの正解率だけが上がり続けること（過学習）
- 深さ 2 の木が「花弁幅」を 2 回使って 3 種類に分けること

**数値ではなく、現象が一致します。** 機械学習の学習という観点では、こちらのほうが大事です。

`run` の出力を固定するテストも置いています。

```rust
#[test]
fn 実行すると深さごとの正解率と決定木を表示する() {
    if data_file().is_none() {
        return;
    }

    let mut out = Vec::new();
    run(&mut out).expect("実行できること");

    // rand の分け方がほかの言語版と違うので、正解率も木の境界も一致しない
    assert_eq!(
        String::from_utf8(out).expect("UTF-8 であること"),
        "深さ\t訓練データ\tテストデータ\tlinfa\n\
         1\t0.7143\t0.5556\t0.5556\n\
         // ...
    );
}
```

`run(&mut out)` の `out` が `Vec<u8>` です。`Vec<u8>` は `std::io::Write` を実装しているので、**標準出力の代わりにそのまま渡せます**。第 1 章で `out: &mut impl Write` にした設計が、ここで効きました。Java 版の `ByteArrayOutputStream`、Go 版の `bytes.Buffer` と同じ役割です。

文字列リテラルの行末の `\` は、**改行とその後の字下げを無視する**書き方です。長い期待値を読みやすく折り返せます。字下げの空白そのものを残したいところでは `\x20`（空白の文字コード）を使っています。

## 3.11 可視化について

Python 版・Kotlin 版では、この章で決定木を図として描きました。Rust 版では[第 2 章](02-data-preprocessing-and-triangulation.md) と同じ理由で、可視化の節を作りません。

linfa には `export_to_tikz()` という、決定木を LaTeX の TikZ 形式に書き出すメソッドがあります。本シリーズでは使いませんが、学習した木を論文や資料に載せたいときには便利です。

木の図がどう見えるかは、[Python 版の第 3 章](../python/03-decision-tree-and-obvious-implementation.md) と [Kotlin 版の第 3 章](../kotlin/03-decision-tree-and-obvious-implementation.md) の「Notebook で探索する」の節を参照してください。この章の `format` が出力する字下げ付きのテキストも、同じ木を表しています。

## 3.12 まとめ

この章では、ジニ不純度で分割する決定木を実装し、linfa の決定木と突き合わせました。Rust に固有の論点は次のとおりです。

1. **`enum` と `Box` で木を表す** — 判別共用体があるので、Go 版のようなインターフェースの工夫は要らない。再帰する型は `Box` で包む。`Box` が無い型はヒープを使わないと読んで分かる
2. **`match` の網羅性をコンパイラが検査する** — `_ =>` の腕が要らない。列挙子を足すと、直すべき場所を全部教えてくれる。Go 版の `default` にあった「永遠に実行されない分岐」が消える
3. **`max_by_key` は同点のとき最後の要素を返す** — ほかの言語版の「同数なら先」とそろえるために使えなかった。テストが 2 件落ちて気付いた。短い標準の関数より、意図を書いた 9 行を選んだ
4. **`f64` は全順序ではない** — `sort()` は使えず、`sort_by` に `partial_cmp` を渡す。`NaN` をどうするかを書かされる。Go 版・Python 版が黙って壊れるところで、Rust は考えさせる
5. **ライブラリの既定値は仕様である** — linfa の `min_impurity_decrease`（0.00001）と `min_weight_split`（2.0）が、深さ 3 以上で自作との差を生む。深さ 3 は**正解率が同じでも予測が 2 件違う**。数字の一致を「同じことをしている」と読んではいけない

機械学習としては、次を確かめました。

- 決定木はルールを**データから作る**。第 1 章で人間が書いた「20 代ならきのこ」に当たる分岐が、花弁幅の 0.2950 と 0.6900 として自動で決まった
- 深くするほど訓練データの正解率は上がる（1.0000 まで）が、テストデータは 0.9111 で頭打ちになる。**過学習**

**TODO リスト（この章の完了時点）**:

- [x] ジニ不純度を計算する
- [x] いちばん多いラベルを返す
- [x] 最良の分割を探す
- [x] 決定木を学習して予測する
- [x] 木の深さを制限する
- [x] 学習した木を表示する
- [x] linfa の決定木と突き合わせる
- [x] 実データで深さと正解率を表示する

次の章からは、別のデータセットで前処理の引き出しを増やします。この章で作った `DecisionTree` と第 2 章の `Features`・`TrainTestSplit` は、以降の章でも使い続けます。
