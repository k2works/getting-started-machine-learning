---
type: Article
title: "第 8 章: 実践的な分類と前処理パイプライン"
description: "タイタニックの生存予測を題材に、グループ中央値・最頻値による補完とダミー変数化を自作し、クラスの重みを付けた決定木とつないでパイプラインにする。学習済みモデルは列挙型と serde で JSON に保存し、trait object を保存できないことを Java 版・Go 版と対比する。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T14:20:00Z }
---

# 第 8 章: 実践的な分類と前処理パイプライン

## 8.1 はじめに

第 3 章では、きれいに整ったアヤメのデータで決定木を作りました。この章では、**実務に近い汚れたデータ** を扱います。題材はタイタニック号の乗客データで、乗客の属性から生死を予測します。

この章で新しく出てくるのは 4 つです。

1. **欠損値の賢い補完** — 年齢は「客室等級と性別のグループごとの中央値」、乗船した港は「最頻値」で埋める
2. **カテゴリ値のダミー変数化** — `male`・`female` のような文字列を 0 と 1 の列にする
3. **クラスの重み** — 死亡者のほうが多い偏ったデータで、少数派の生存者を見つけられるようにする
4. **前処理のパイプライン** — 上の前処理とモデルを 1 つにまとめ、ファイルに保存して再利用する

ここで Rust 版の立場がはっきりします。**linfa-preprocessing には、グループごとの中央値による補完も、ダミー変数化もありません**（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。第 3 章・第 7 章では「自作してから linfa と突き合わせる」構成でしたが、この章の前処理は **自作が最終的な実装** です。突き合わせられるのは決定木の部分だけで、しかもそこにも落とし穴があります（8.12 節）。

もう 1 つの主題は **学習済みモデルの保存** です。Java 版はオブジェクトのシリアライズ、[Go 版](../go/08-classification-and-preprocessing-pipeline.md) は `encoding/gob` を使いました。Rust の標準ライブラリにはシリアライズの仕組みが無いので serde を使いますが、そこで「trait object は保存できない」という設計上の制約にぶつかります。この制約が、パイプラインの設計そのものを変えます。

## 8.2 題材とデータ

### Survived.csv

891 人分の乗客データです。この章で使う列は次のとおりです。

| 列 | 内容 | 欠損値 |
|----|------|-------|
| Survived | 生死（1 が生存、0 が死亡）。正解ラベル | なし |
| Pclass | 客室の等級（1・2・3） | なし |
| Sex | 性別（male・female） | なし |
| Age | 年齢 | 177 件 |
| SibSp | 同乗した兄弟・配偶者の数 | なし |
| Parch | 同乗した親・子の数 | なし |
| Fare | 運賃 | なし |
| Embarked | 乗船した港（C・Q・S） | 2 件 |

`PassengerId`・`Name`・`Ticket`・`Cabin` は使いません。ID と名前は乗客を区別するためのもの、`Cabin` は欠損が多すぎるからです。

このファイルは **BOM 付き** で始まります。ほかの言語版では BOM を取り除く処理を書きましたが、Rust 版では要りません。csv クレートが自動で取り除くからです（第 1 章 1.8 節）。第 2 章の `Table::load` がそのまま使えます。

### 年齢はグループごとの中央値で補完する

第 2 章では、欠損値を列全体の平均値で埋めました。この章ではもう一歩進めます。年齢は客室の等級と性別で傾向が違う（1 等客室の乗客のほうが年上）ので、**同じ等級・同じ性別のグループの中央値** で埋めます。平均ではなく中央値なのは、極端な値に引っ張られないためです。

港は文字列なので平均も中央値も取れません。いちばん多い値（最頻値）で埋めます。

## 8.3 TODO リストの作成

**TODO リスト**:

- [ ] 特徴量の列と正解ラベルを決める
- [ ] 年齢をグループごとの中央値で補完する
  - [ ] グループを指定しなければ全体の中央値で埋める
  - [ ] グループごとに違う中央値で埋める
  - [ ] 訓練データで求めた中央値を別のデータに使う
  - [ ] 訓練データに無いグループは全体の中央値で埋める
- [ ] 乗船した港を最頻値で補完する
- [ ] カテゴリ値をダミー変数にする
  - [ ] 最初のカテゴリを除いた列を作る
  - [ ] 別のデータにも訓練データと同じ列を作る
- [ ] クラスの重みを付けた決定木を作る
  - [ ] 重み付きのジニ不純度
  - [ ] balanced の重み
- [ ] 前処理とモデルをパイプラインにつなぐ
- [ ] 学習済みのパイプラインを保存して読み込む
- [ ] 評価する（正解率と、見つけられた生存者の数）
- [ ] 実データでクラスの重みの効果を確かめる

## 8.4 前処理をどう表すか

### Java 版のインターフェースを Rust に移すと

Java 版は前処理を 2 つのインターフェースで表しました。`Transformer`（`fit` して学習済みの前処理を返す）と `FittedTransformer`（`transform` してデータを変える）です。学習済みのパイプラインは `List<FittedTransformer>` を持ち、それをまるごとシリアライズしました。

Rust に素直に移すと、trait と `Box<dyn FittedTransformer>` になります。書いてみます。

```rust
pub trait FittedTransformer {
    fn transform(&self, x: Vec<f64>) -> Vec<f64>;
}

#[derive(Serialize, Deserialize)]
pub struct FittedPipeline {
    steps: Vec<Box<dyn FittedTransformer>>,
}
```

コンパイルは通りません。

```text
error[E0277]: the trait bound `dyn FittedTransformer: serde::Serialize` is not satisfied
 --> src/main.rs:7:10
  |
7 | #[derive(Serialize, Deserialize)]
  |          ^^^^^^^^^ the trait `Serialize` is not implemented for `dyn FittedTransformer`
8 | pub struct FittedPipeline {
9 |     steps: Vec<Box<dyn FittedTransformer>>,
  |     ----- required by a bound introduced by this call

error[E0277]: the trait bound `dyn FittedTransformer: serde::Deserialize<'de>` is not satisfied
 --> src/main.rs:9:12
  |
9 |     steps: Vec<Box<dyn FittedTransformer>>,
  |            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ the trait `Deserialize<'_>` is not implemented for `dyn FittedTransformer`
```

`Serialize` のほうは、トレイトに `Serialize` を継承させれば何とかなります。問題は `Deserialize` です。JSON を読むとき、**どの型に戻すのかをコンパイル時に決めておく必要があります**。`dyn FittedTransformer` は「そのトレイトを実装した何か」でしかないので、戻し先が決まりません。

Java 版では、保存したバイト列にクラス名が入っていて、実行時にそのクラスを探して復元しました。だから逆に「読み込むクラスを制限する」フィルタが必要でした（Java 版 8.10 節）。Go 版の `gob` も `gob.Register` でインターフェースの実装を登録する必要がありました。Rust は **その仕組みを持たない代わりに、危険もありません**。

### 列挙型にする

答えは、前処理の種類を **列挙型** にすることです。取りうる種類が閉じていれば、`match` で全部を書けます。

```rust
/// 学習前の前処理。`fit` で訓練データから必要な値を求める。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Step {
    /// 数値の列の欠損値を、同じグループ（`by` の列の値の組）の中央値で補完する。
    GroupMedian { column: String, by: Vec<String> },
    /// 文字列の列の欠損値を、訓練データで最も多い値（最頻値）で補完する。
    MostFrequent { column: String },
    /// カテゴリ値の列を、最初のカテゴリを除いた 0 と 1 の列（ダミー変数）にする。
    Dummy { columns: Vec<String> },
}

/// 学習済みの前処理。`fit` で求めた値を持ち、どのデータにも同じ変換をする。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum FittedStep {
    GroupMedian {
        column: String,
        by: Vec<String>,
        medians: Vec<(Vec<String>, f64)>,
        overall: f64,
    },
    MostFrequent {
        column: String,
        most_frequent: String,
    },
    Dummy {
        dummies: Vec<(String, Vec<String>)>,
    },
}
```

`fit` と `transform` は、トレイトのメソッドではなく `match` になります。

```rust
impl Step {
    /// 訓練データから変換に必要な値を求める。
    pub fn fit(&self, x: &Table) -> Result<FittedStep> {
        match self {
            Step::GroupMedian { column, by } => fit_group_median(x, column, by),
            Step::MostFrequent { column } => fit_most_frequent(x, column),
            Step::Dummy { columns } => fit_dummy(x, columns),
        }
    }
}
```

**得たもの**: JSON に保存できる。`match` の網羅性をコンパイラが検査するので、種類を足したときに書き忘れた分岐がコンパイルエラーになる。動的ディスパッチが無いので速い。

**失ったもの**: 利用者が自分の前処理を足せない。Java 版なら `Transformer` を実装したクラスを外から渡せますが、この設計では `enum` に列挙子を足す必要があります。

前処理の種類が固定なら列挙型、拡張したいなら trait object、という判断です。この章のパイプラインは 3 種類で固定なので、列挙型を選びます。第 15 章で API を設計するときに、この判断をもう一度扱います。

## 8.5 年齢をグループごとの中央値で補完する

### Red: まず全体の中央値で

いちばん単純な場合から始めます。グループを指定しなければ、全体の中央値です。

```rust
    #[test]
    fn 欠損した年齢を全体の中央値で埋める() {
        let x = passengers(&[
            ("1", "female", "10"),
            ("1", "female", "20"),
            ("1", "female", ""),
        ]);

        let fitted = Step::GroupMedian {
            column: "Age".to_string(),
            by: Vec::new(),
        }
        .fit(&x)
        .unwrap();

        let filled = fitted.transform(&x).unwrap();

        assert_eq!(filled.rows[2].number("Age").unwrap(), Some(15.0));
    }
```

10 と 20 の中央値は 15 です（偶数個なら中央の 2 つの平均）。

### 三角測量: グループごとに違う中央値

```rust
    #[test]
    fn グループごとに違う中央値で埋める() {
        let x = passengers(&[
            ("1", "female", "10"),
            ("1", "female", "20"),
            ("3", "male", "40"),
            ("3", "male", "60"),
            ("3", "male", ""),
            ("1", "female", ""),
        ]);

        let fitted = Step::GroupMedian {
            column: "Age".to_string(),
            by: columns(&["Pclass", "Sex"]),
        }
        .fit(&x)
        .unwrap();

        let filled = fitted.transform(&x).unwrap();

        assert_eq!(filled.rows[4].number("Age").unwrap(), Some(50.0));
        assert_eq!(filled.rows[5].number("Age").unwrap(), Some(15.0));
    }
```

3 等客室の男性は 50、1 等客室の女性は 15。グループごとに違う値で埋まります。

実装では、グループを「`by` の列の値を並べたベクタ」で表します。

```rust
/// 行の属するグループ。`by` の列の値を並べたベクタ。
fn group_of(row: &Row, by: &[String]) -> Result<Vec<String>> {
    by.iter()
        .map(|column| row.text(column).map(str::to_string))
        .collect()
}
```

`Result` を返す `map` を `collect()` で `Result<Vec<_>>` にまとめるのは Rust の定型です。1 つでも失敗すればそこで止まり、全体が失敗になります。Go 版のように途中で `if err != nil` を書く必要がありません。

集計は `HashMap` ではなく `Vec<(Vec<String>, Vec<f64>)>` で持ちます。グループは数個しかないので線形探索で足りますし、**先に現れた順** が保たれるので、保存した JSON も実行のたびに同じになります。

```rust
/// グループごとの中央値と、全体の中央値を求める。グループは先に現れた順に並べる。
fn fit_group_median(x: &Table, column: &str, by: &[String]) -> Result<FittedStep> {
    let mut groups: Vec<(Vec<String>, Vec<f64>)> = Vec::new();
    let mut all = Vec::new();

    for row in &x.rows {
        let Some(value) = row.number(column)? else {
            continue;
        };

        all.push(value);

        let group = group_of(row, by)?;

        match groups.iter_mut().find(|(key, _)| *key == group) {
            Some((_, values)) => values.push(value),
            None => groups.push((group, vec![value])),
        }
    }
    ...
}
```

`let Some(value) = ... else { continue; };` は let-else です。「値があるときだけ続ける」を 1 行で書けます。第 2 章の `Row::number` が `Result<Option<f64>>` を返すので、`?` で失敗を上へ返してから `Option` を外す、という二段構えになります。

### 訓練データで求めた値を別のデータに使う

前処理でいちばん大事な性質です。テストデータの中央値を使ってはいけません。

```rust
    #[test]
    fn 訓練データで求めた中央値を別のデータに使う() {
        let train = passengers(&[("1", "female", "10"), ("1", "female", "20")]);
        let test = passengers(&[("1", "female", "")]);

        let filled = Step::GroupMedian {
            column: "Age".to_string(),
            by: columns(&["Pclass", "Sex"]),
        }
        .fit(&train)
        .unwrap()
        .transform(&test)
        .unwrap();

        assert_eq!(filled.rows[0].number("Age").unwrap(), Some(15.0));
    }
```

`fit` と `transform` を分けている理由がこれです。`fit` は訓練データだけを見て値を決め、`transform` はその値をどのデータにも使います。

### 訓練データに無いグループ

本番では、訓練データに無かった組み合わせが来ます。そのときは全体の中央値に落とします。

```rust
    #[test]
    fn 訓練データに無いグループは全体の中央値で埋める() {
        let train = passengers(&[("1", "female", "10"), ("1", "female", "30")]);
        let test = passengers(&[("3", "male", "")]);
        ...
        assert_eq!(filled.rows[0].number("Age").unwrap(), Some(20.0));
    }
```

実装は `map_or` の 1 行です。

```rust
let median = medians
    .iter()
    .find(|(key, _)| *key == group)
    .map_or(*overall, |(_, median)| *median);
```

Java 版の `medians.getOrDefault(groupOf(row, by), overallMedian)` に当たります。`Option` に既定値を与える書き方が `map_or` です。

### 行を書き換えるには

第 2 章の `Row` はセルの対応表を非公開で持っていて、外から取り出せません。**第 2 章のコードは変えない** という制約があるので、表の列をたどって新しい対応表を組み立てます。

```rust
/// 行の値を列名で引ける対応表にする。表の列だけを写す。
fn cells(columns: &[String], row: &Row) -> Result<HashMap<String, String>> {
    columns
        .iter()
        .map(|column| Ok((column.clone(), row.text(column)?.to_string())))
        .collect()
}
```

Java 版の `Rows.with`（元の行を複製して 1 つの列だけ差し替える）に当たりますが、**表の列に載っていない列は落ちます**。この章では `Survived` などの列を前処理に通さない（正解ラベルは別に取り出す）ので、これで困りません。元の行を変えない点は同じです。

## 8.6 乗船した港を最頻値で補完する

同じ形です。数え上げて、いちばん多い値を選びます。同数なら先に現れたほうにするのは、第 3 章の `majority` と同じ規律です。

```rust
    #[test]
    fn 最頻値が同数なら先に現れた値を選ぶ() {
        let x = Table {
            columns: columns(&["Embarked"]),
            rows: vec![row(&[("Embarked", "C")]), row(&[("Embarked", "S")])],
        };

        assert_eq!(
            Step::MostFrequent {
                column: "Embarked".to_string(),
            }
            .fit(&x)
            .unwrap(),
            FittedStep::MostFrequent {
                column: "Embarked".to_string(),
                most_frequent: "C".to_string(),
            }
        );
    }
```

`assert_eq!` で列挙型どうしを比べられるのは、`#[derive(Debug, PartialEq)]` を書いてあるからです。失敗したときは `Debug` の表示で中身が出ます。

実装では `fold` を使います。`max_by_key` は同点のとき **最後** の要素を返すので使えません（第 3 章で同じ罠に会いました）。

```rust
    let most_frequent = counts
        .into_iter()
        .fold(None::<(String, usize)>, |best, candidate| match best {
            Some(best) if best.1 >= candidate.1 => Some(best),
            _ => Some(candidate),
        })
        .map(|(value, _)| value)
        .ok_or_else(|| Error::AllMissing(column.to_string()))?;
```

`Some(best) if best.1 >= candidate.1` は **ガード付きのパターン** です。「すでに選んだほうが同じか多ければそれを残す」を、条件式ではなくパターンで書けます。

## 8.7 カテゴリ値をダミー変数にする

`Sex` は `male`・`female` の 2 種類です。これを `Sex_male` という 1 つの列（male なら 1、female なら 0）にします。カテゴリが n 種類なら n-1 列です。全部を列にすると、1 つの列がほかの列から決まってしまい（多重共線性）、線形モデルで問題になります。

```rust
    #[test]
    fn カテゴリ値を最初のカテゴリを除いたダミー変数にする() {
        let x = Table {
            columns: columns(&["Sex", "Age"]),
            rows: vec![
                row(&[("Sex", "male"), ("Age", "20")]),
                row(&[("Sex", "female"), ("Age", "30")]),
            ],
        };

        let encoded = Step::Dummy {
            columns: columns(&["Sex"]),
        }
        .fit(&x)
        .unwrap()
        .transform(&x)
        .unwrap();

        assert_eq!(encoded.columns, columns(&["Age", "Sex_male"]));
        assert_eq!(encoded.rows[0].number("Sex_male").unwrap(), Some(1.0));
        assert_eq!(encoded.rows[1].number("Sex_male").unwrap(), Some(0.0));
    }
```

`Sex` が消えて `Sex_male` が末尾に足されます。カテゴリは並べ替えてから先頭を落とすので、`female` が落ちて `male` が残ります。

ここでも「訓練データで決めたものを別のデータに使う」性質が要ります。テストデータに `Q` の港が 1 人もいなくても、訓練データにいたなら `Embarked_Q` の列を作らなければ、モデルに渡す特徴量の形が変わってしまいます。

```rust
    #[test]
    fn 別のデータにも訓練データと同じダミー変数の列を作る() {
        let train = ... // C, Q, S
        let test = ...  // S だけ

        let encoded = Step::Dummy { ... }.fit(&train).unwrap().transform(&test).unwrap();

        assert_eq!(encoded.columns, columns(&["Embarked_Q", "Embarked_S"]));
        assert_eq!(encoded.rows[0].number("Embarked_Q").unwrap(), Some(0.0));
        assert_eq!(encoded.rows[0].number("Embarked_S").unwrap(), Some(1.0));
    }
```

0 と 1 の変換は `i32::from(bool)` で書けます。`if ... { 1 } else { 0 }` より短く、意図がはっきりします。

```rust
let flag = i32::from(&value == category);
```

## 8.8 クラスの重みを付けた決定木を作る

### なぜ自作するのか

891 人のうち生存者は 342 人、死亡者は 549 人です。何も考えずに学習すると、モデルは多数派（死亡）に寄ります。全員を「死亡」と予測するだけで 6 割強の正解率が出てしまうからです。

これを直すのが **クラスの重み** です。少数派の 1 件を、多数派の 1 件より重く数えます。scikit-learn の `class_weight="balanced"` に当たります。linfa-trees にはこの機能がありません。第 3 章の決定木を作り直して、1 件ごとの重みを扱えるようにします。

### 重み付きのジニ不純度

第 3 章のジニ不純度は「ラベルごとの件数の割合」で計算しました。重み付きでは、件数の代わりに **重みの合計** を使います。

```rust
/// 重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。
pub fn weighted_gini(labels: &[i32], weights: &[f64]) -> f64 {
    let total: f64 = weights.iter().sum();

    if total == 0.0 {
        return 0.0;
    }

    1.0 - weight_sums(labels, weights)
        .iter()
        .map(|(_, weight)| (weight / total).powi(2))
        .sum::<f64>()
}
```

テストで、重みを等しくすれば第 3 章と同じ値になることを確かめます。

```rust
    #[test]
    fn 重みが等しければ普通のジニ不純度になる() {
        let gini = weighted_gini(&[0, 1], &[1.0, 1.0]);

        assert!((gini - 0.5).abs() < 1e-12, "gini = {gini}");
    }

    #[test]
    fn 重みが偏ると不純度も偏る() {
        let gini = weighted_gini(&[0, 1], &[3.0, 1.0]);

        assert!((gini - 0.375).abs() < 1e-12, "gini = {gini}");
    }
```

`1 - (0.75² + 0.25²) = 0.375` です。重みが偏るほど「純度が高い」と見なされます。

### balanced の重み

重みの決め方は scikit-learn に合わせます。「全体の件数 ÷（クラスの数 × そのクラスの件数）」です。

```rust
    #[test]
    fn 少数派のクラスに大きな重みが付く() {
        let weights = balanced_weights(&[0, 0, 0, 1]);

        assert!((weights[0] - 4.0 / 6.0).abs() < 1e-12, "{weights:?}");
        assert!((weights[3] - 2.0).abs() < 1e-12, "{weights:?}");
    }
```

3 件ある 0 は 4 ÷ (2 × 3) = 0.667、1 件しかない 1 は 4 ÷ (2 × 1) = 2。クラスごとの重みの合計はどちらも 2 になり、釣り合います。

重みの付け方は列挙型にします。

```rust
/// クラスの重みの付け方。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ClassWeight {
    /// 重みを付けない（すべて 1）。
    None,
    /// クラスの件数に反比例する重みを付ける。
    Balanced,
}
```

`Copy` を付けられるのは、中身を持たない列挙子だけでできているからです。関数に渡しても所有権が移らず、あとでも使えます。第 1 章で見た「値そのもの」の型です。

### 第 3 章の木を使い回さない理由

木の型（`Tree`）も新しく作ります。第 3 章の `Tree` を使い回せない理由が 2 つあります。

1. ラベルが `String` ではなく `i32`（生死を 0・1 で表す）
2. JSON に保存するため `Serialize`・`Deserialize` を導出する必要がある

2 つめが効きます。第 3 章の `Tree` に `#[derive(Serialize, Deserialize)]` を足せば使い回せますが、**この章のために前の章のコードを変える** ことになります。章ごとに独立させる方針なので、新しく定義します。

```rust
/// 重み付きの決定木。葉か節のどちらか。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum Tree {
    /// 予測するラベルを持つ葉。
    Leaf { label: i32 },
    /// 分割と左右の部分木を持つ節。
    Node {
        feature: String,
        threshold: f64,
        left: Box<Tree>,
        right: Box<Tree>,
    },
}
```

再帰する型なので部分木は `Box` で包みます（第 3 章と同じ）。`Box<Tree>` は serde がそのまま扱えます。**具体的な型なら箱に入っていても保存できる** — 保存できないのは `Box<dyn Trait>` のほうだ、ということがここで対比できます。

## 8.9 前処理とモデルをパイプラインにつなぐ

`Pipeline` は前処理の列とモデルを持ち、`fit` で学習済みの `FittedPipeline` を返します。

```rust
    /// 訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで `fit` する。
    pub fn fit(&self, x: &Table, t: &[i32]) -> Result<FittedPipeline> {
        let mut fitted = Vec::with_capacity(self.steps.len());
        let mut prepared = x.clone();

        for step in &self.steps {
            let step = step.fit(&prepared)?;
            prepared = step.transform(&prepared)?;
            fitted.push(step);
        }

        Ok(FittedPipeline {
            model: self.model.fit(&to_features(&prepared)?, t)?,
            steps: fitted,
        })
    }
```

大事なのは **前の前処理で変換したあとのデータで次の前処理を `fit` する** ことです。ダミー変数化は、年齢と港が埋まったあとのデータを見る必要があります。

Survived.csv 用の組み立てはこうなります。

```rust
    /// Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。
    pub fn build(max_depth: Option<usize>, class_weight: ClassWeight) -> Pipeline {
        Pipeline {
            steps: vec![
                Step::GroupMedian {
                    column: "Age".to_string(),
                    by: vec!["Pclass".to_string(), "Sex".to_string()],
                },
                Step::MostFrequent {
                    column: "Embarked".to_string(),
                },
                Step::Dummy {
                    columns: vec!["Sex".to_string(), "Embarked".to_string()],
                },
            ],
            model: DecisionTreeClassifier::new(max_depth, class_weight),
        }
    }
```

Java 版の `FittedTransformer.andThen` による関数合成に当たるものは、Rust では単なる `for` ループです。合成した関数を持ち回るとクロージャになり、クロージャは保存できません。ここでも「保存できること」が設計を決めています。

前処理の済んだ表を特徴量にするとき、欠損値が残っていれば失敗させます。

```rust
    #[test]
    fn 欠損値が残った表は特徴量にできない() {
        ...
        assert_eq!(
            to_features(&x).unwrap_err().to_string(),
            "補完する値がありません: Age"
        );
    }
```

パイプラインが正しく組まれていれば起きませんが、順番を間違えたときにここで止まります。

## 8.10 モデルを保存して読み込む

### serde で JSON にする

`#[derive(Serialize, Deserialize)]` を書いた型は、そのまま JSON にできます。

```rust
/// パイプライン全体（前処理で求めた値とモデル）を JSON で保存する。
pub fn save(pipeline: &FittedPipeline, model_file: &Path) -> Result<()> {
    if let Some(parent) = model_file.parent() {
        fs::create_dir_all(parent).map_err(|error| failed(model_file, &error))?;
    }

    let json =
        serde_json::to_string_pretty(pipeline).map_err(|error| Error::Library(error.to_string()))?;

    fs::write(model_file, json).map_err(|error| failed(model_file, &error))?;

    Ok(())
}

/// 保存したパイプラインを読み込む。JSON の形が違えば失敗する。
pub fn load(model_file: &Path) -> Result<FittedPipeline> {
    let json = fs::read_to_string(model_file).map_err(|error| failed(model_file, &error))?;

    serde_json::from_str(&json).map_err(|error| Error::Library(error.to_string()))
}
```

`from_str` の戻り先の型は、関数の戻り値から推論されます。**どの型に戻すかをコードに書いてある** ので、JSON の中身がどうであれ、それ以外の型になることはありません。

Java 版では「保存したバイト列に書かれたクラスを実行時に読み込む」ため、`ObjectInputFilter` でクラスを制限する必要がありました。信頼できないファイルを読むと任意のクラスが復元されうるからです。Rust の serde にはその入り口がありません。**表現力を 1 つ諦めた（trait object を保存できない）代わりに、攻撃面も無くなっています**。

3 つの言語を並べます。

| | 仕組み | 書く量 | 多相を保存できるか | 危険 |
|---|---|---|---|---|
| Java 版 | 組み込みのシリアライズ | `implements Serializable` だけ | できる（クラス名が入る） | 読み込むクラスの制限が要る |
| Go 版 | `encoding/gob` | `gob.Register` で実装を登録 | できる（登録したものだけ） | 登録漏れが実行時エラー |
| Rust 版 | serde + serde_json | `#[derive(Serialize, Deserialize)]` | できない（列挙型で代用） | 無し（型が合わなければ失敗） |

### JSON のキーは文字列に限られる

グループごとの中央値を `HashMap<Vec<String>, f64>` で持つと、JSON にできません。JSON のオブジェクトのキーは文字列だけだからです。そこで **組のベクタ** にします。

```rust
/// `Vec<(Vec<String>, f64)>` で中央値を持つのは、JSON のオブジェクトのキーが文字列に限られ、
/// グループ（列の値の組）をそのままキーにできないため。並びも保てる。
```

JSON では次のようになります。配列の配列なので、キーの型に制限がありません。

```json
{"GroupMedian": {"column": "Age", "by": ["Pclass", "Sex"],
                 "medians": [[["1", "female"], 35.0], [["3", "male"], 25.0]], "overall": 28.5}}
{"MostFrequent": {"column": "Embarked", "most_frequent": "S"}}
{"Dummy": {"dummies": [["Sex", ["male"]], ["Embarked", ["Q", "S"]]]}}
```

（実際に保存される `model/survived.json` から、前処理の 3 つを抜き出して詰めたものです。保存は
`to_string_pretty` なので、ファイルでは 1 要素ずつ改行されます。）

副産物として、**並びが保たれます**。`HashMap` を JSON にすると順が実行ごとに変わり、保存したファイルの差分が読めなくなります。

### 保存して読み込めることをテストする

```rust
    #[test]
    fn 保存して読み込むと同じパイプラインになる() {
        let dir = std::env::temp_dir().join("getting-started-ml-chapter08");
        let model_file = dir.join("survived.json");

        save(&pipeline(), &model_file).unwrap();

        assert_eq!(load(&model_file).unwrap(), pipeline());

        fs::remove_dir_all(&dir).unwrap();
    }
```

`PartialEq` を導出してあるので、読み込んだものと元のものを `assert_eq!` で比べられます。「同じ予測をすること」より強い検査です。

形の違う JSON を読ませたときに失敗することも確かめます。

```rust
    #[test]
    fn 形の違うジェイソンは読み込めない() {
        ...
        fs::write(&model_file, "{\"steps\": []}").unwrap();

        assert!(
            load(&model_file)
                .unwrap_err()
                .to_string()
                .starts_with("ライブラリが失敗しました: ")
        );
    }
```

関数名が `形の違うジェイソン…` とカタカナなのは、clippy の `non_snake_case` が「`JSON`」の大文字を嫌うからです。

```text
error: function `形の違うJSONは読み込めない` should have a snake case name
  --> src/chapter08/modelfile.rs:80:8
   |
80 |     fn 形の違うJSONは読み込めない() {
   |        ^^^^^^^^^^^^^^^^^^^^^^^^^^ help: convert the identifier to snake case: `形の違う_jsonは読み込めない`
```

日本語の識別子に英大文字を混ぜると、この検査に引っかかります。`#[allow]` を足すより、名前をカタカナにするほうが素直でした。

## 8.11 評価する

正解率だけでは、偏ったデータのモデルを評価できません。**テストデータの生存者のうち何人を生存と予測できたか**（再現率に当たる）も見ます。

```rust
/// 訓練データとテストデータの正解率と、テストデータの生存者のうち生存と予測できた人数。
#[derive(Debug, Clone, PartialEq)]
pub struct Evaluation {
    pub train_accuracy: f64,
    pub test_accuracy: f64,
    pub found_survivors: usize,
    pub survivors: usize,
}
```

評価指標の体系的な話（適合率・再現率・F 値・混同行列）は第 11 章で扱います。ここでは「正解率だけを見ると判断を誤る」ことを実地で確かめるために、数え上げだけを実装します。

## 8.12 実データでクラスの重みの効果を確かめる

### 実行する

```bash
cargo run --bin chapters -- chapter08
```

```text
データ件数: 891（生存 342, 死亡 549）
訓練データ: 712 件, テストデータ: 179 件
classWeight=none: 訓練 0.854, テスト 0.827, 生存者 66 人中 49 人を発見
classWeight=balanced: 訓練 0.847, テスト 0.821, 生存者 66 人中 53 人を発見
保存したモデル: survived.json
架空の乗客の予測: [1, 0]
```

**正解率は 0.827 から 0.821 へわずかに下がり、見つけられた生存者は 49 人から 53 人へ増えました**。これがクラスの重みの効果です。全体の正解率を少し犠牲にして、少数派を取りこぼさないようにしています。

どちらがよいかは目的によります。救命ボートの配備を考えるなら「生存できたはずの人を見逃さない」ほうが大事でしょうし、統計の報告なら全体の正解率かもしれません。**モデルの良し悪しは、指標だけでは決まりません**。

最後の「架空の乗客の予測」は、1 等客室の女性（運賃 50、C 港）と 3 等客室の男性（運賃 8、S 港）です。どちらも年齢が分かりません。保存したモデルを読み込んで予測し、`[1, 0]` — 女性は生存、男性は死亡と出ました。**年齢が空欄でも予測できる** のは、学習済みの補完（1 等客室の女性の中央値）がモデルと一緒に保存されているからです。

### 関係をテストする

数値はほかの言語版と一致しません（分割が違うため）。それでも、**関係** は固定できます。

```rust
#[test]
fn 重みを付けると見つかる生存者が増える() {
    let (Some(none), Some(balanced)) = (
        evaluation_of(ClassWeight::None),
        evaluation_of(ClassWeight::Balanced),
    ) else {
        return;
    };

    assert_eq!(none.survivors, 66);
    assert_eq!(none.found_survivors, 49);
    assert_eq!(balanced.found_survivors, 53);

    // 見つかる生存者は増えるが、全体の正解率はわずかに下がる
    assert!(balanced.test_accuracy < none.test_accuracy);
}
```

最後の 1 行が、この章で確かめたかったことそのものです。

### linfa-trees と突き合わせると値が揺れる

決定木の部分は linfa-trees と比べられるはずでした。自作の前処理で作った特徴量をそのまま linfa に渡します。

```rust
/// linfa の決定木で学習して予測し、整数のラベルに戻す。
pub fn predict(
    x_train: &[Features],
    t_train: &[i32],
    x_test: &[Features],
    max_depth: Option<usize>,
) -> Result<Vec<i32>> {
    let labels: Vec<String> = t_train.iter().map(i32::to_string).collect();
    let predicted = linfatree::predict(x_train, &labels, x_test, max_depth)?;
    ...
}
```

ところが、**実行するたびに正解率が変わりました**。

```text
linfa の決定木（重みなし）: テスト 0.788
linfa の決定木（重みなし）: テスト 0.810
linfa の決定木（重みなし）: テスト 0.804
linfa の決定木（重みなし）: テスト 0.788
linfa の決定木（重みなし）: テスト 0.788
```

同じデータ・同じ深さ・同じ前処理で 0.788〜0.810 のあいだを動きます。第 3 章のアヤメでは何度実行しても同じ値だったので、データによる違いです。Survived.csv は **同じ不純度になる分割の候補が多く**（ダミー変数は 0 と 1 しか取らないので、分割の位置が重なります）、linfa-trees はそこで一定の選び方をしません。

そのため、この章の `run` の出力に linfa の行を入れるのはやめました。出力を固定するテストが実行のたびに落ちてしまうからです。代わりに、範囲で確かめるテストを置きます。

```rust
    // linfa-trees は同じ不純度の分割が並ぶと選び方が一定しないので、
    // Survived.csv では実行のたびに正解率が変わる（0.78〜0.81）。値を固定するテストは書けない
    let predictions =
        linfacompare::predict(&x_train, &split.t_train, &x_test, MAX_DEPTH).expect("予測できること");
    let value = accuracy(&predictions, &split.t_test).expect("評価できること");

    assert!((0.75..0.85).contains(&value), "linfa の正解率 = {value}");
```

`(0.75..0.85).contains(&value)` は範囲の `contains` です。自作の実装は毎回同じ値を返すので、**自作のほうが再現性が高い** という逆転がここで起きます。ライブラリに寄せるほど結果が安定する、とは限りません。

これは ADR 009 に書き足すべき発見です。「linfa-trees は同点の分割で非決定的なので、出力を固定するテストの対象にしない」。

## 8.13 探索と可視化

年齢の分布や、客室等級ごとの生存率をグラフで見る手順は [Python 版](../python/08-classification-and-preprocessing-pipeline.md) と [Kotlin 版](../kotlin/08-classification-and-preprocessing-pipeline.md) の Notebook を参照してください。Rust 版には Notebook と可視化の節を設けません。

## 8.14 まとめ

この章では、汚れたデータの前処理をパイプラインにまとめ、クラスの重みを付けた決定木とつないで、JSON に保存しました。Rust に固有の論点は次のとおりです。

1. **trait object は保存できない** — `Deserialize` は戻し先の型をコンパイル時に決める必要がある。前処理を列挙型にすると保存でき、`match` の網羅性も得られる。代わりに外から種類を足せなくなる
2. **保存できることが設計を決める** — Java 版の関数合成（`andThen`）はクロージャになるので保存できず、Rust 版では素直な `for` ループになる
3. **JSON のキーは文字列だけ** — グループをキーにした対応表は組のベクタにする。副産物として並びが保たれ、保存したファイルの差分が読める
4. **serde は安全なぶん制約がある** — Java 版で必要だった「読み込むクラスの制限」が要らない。型が合わなければ失敗するだけ
5. **ライブラリのほうが不安定なこともある** — linfa-trees は同点の分割で選び方が一定せず、Survived.csv では実行ごとに正解率が変わる。自作のほうが再現できる
6. **clippy は日本語の識別子にも口を出す** — 大文字を含む名前は `non_snake_case` に引っかかる

**TODO リスト（この章の完了時点）**:

- [x] 特徴量の列と正解ラベルを決める
- [x] 年齢をグループごとの中央値で補完する
- [x] 乗船した港を最頻値で補完する
- [x] カテゴリ値をダミー変数にする
- [x] クラスの重みを付けた決定木を作る
- [x] 前処理とモデルをパイプラインにつなぐ
- [x] 学習済みのパイプラインを保存して読み込む
- [x] 評価する（正解率と、見つけられた生存者の数）
- [x] 実データでクラスの重みの効果を確かめる

次の章では、既存の列から新しい特徴量を作る **特徴量エンジニアリング** に進みます。
