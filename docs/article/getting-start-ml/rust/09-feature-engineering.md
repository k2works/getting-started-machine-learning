---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・外れ値検出・Shift_JIS の表の結合を Rust の TDD で自作し、linfa-preprocessing の LinearScaler::standard() と突き合わせて母標準偏差であることを実測する。章ごとの失敗を enum で表し、第 2 章の失敗を From で包む。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T15:20:00Z }
---

# 第 9 章: 特徴量エンジニアリング

## 9.1 はじめに

モデルの性能は、アルゴリズムよりも「どんな特徴量を渡すか」で大きく変わることがあります。元のデータから、モデルが学びやすい特徴量を作り出す作業を **特徴量エンジニアリング** と呼びます。

この章では、ボストンの住宅価格データを題材に、次の 5 つの技法を TDD で自作します。

- カテゴリ値をダミー変数（0 と 1 の列）にする
- 特徴量を標準化する
- 2 乗の項と交互作用の項（多項式特徴量）を作る
- 外れ値を検出する
- 別の表を結合して特徴量を増やす

標準化は linfa-preprocessing の `LinearScaler::standard()` と突き合わせます。最後に、作った特徴量で線形回帰の決定係数がどう変わるかを実データで測ります。

[Python 版の第 9 章](../python/09-feature-engineering.md) と同じ TODO リストで進め、[Java 版](../java/09-feature-engineering.md)・[Go 版](../go/09-feature-engineering.md) と対比します。Rust 版で拾う論点は次の 3 つです。

- **章ごとの失敗を `enum` で表す**。この章では「指定した文字コードで読めない」「正規方程式を解けない」という、第 2 章には無い失敗が出てきます。`enum Error` を章に足し、第 2 章の失敗は `From` で包んで `?` で素通しします
- **文字コードは `enum` で選ばせる**。`Encoding::Utf8` と `Encoding::ShiftJis` の 2 つだけを受け取る型にすると、呼び出し側は文字列を書き間違えられません
- **Shift_JIS のファイルを UTF-8 として読むと失敗する**。Rust の `String` は UTF-8 であることが型の保証なので、`String::from_utf8` は `Err` を返します。Go の `golang.org/x/text/encoding/japanese` が黙って U+FFFD に置き換えるのと対照的です

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

| ファイル | 内容 | 区切り文字 | 文字コード（実データで確認） |
|---------|------|----------|------------------------|
| `Boston.csv` | 地域ごとの住宅価格。100 件、14 列。CRIME は `high`・`low`・`very_low` のカテゴリ値、NOX と RAD に欠損値 | カンマ | ASCII の範囲だけ（BOM なし） |
| `bike.tsv` | 自転車シェアの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）。731 件 | **タブ** | ASCII の範囲だけ |
| `weather.csv` | 天気 ID と天気の名前（晴れ・曇り・雨）。3 件 | カンマ | **Shift_JIS**（UTF-8 としては読めない） |

`Boston.csv` は第 2 章の `Table::load`（csv クレート）でそのまま読めます。`bike.tsv` と `weather.csv` は、この章で区切り文字と文字コードを指定できる読み込みを足します（第 2 章の `Table` は変更しません）。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
  - [ ] 先頭を除いたカテゴリを辞書順に求める
  - [ ] カテゴリごとに 0 と 1 の列を作る
  - [ ] カテゴリに無い値はすべての列を 0 にする
- [ ] 特徴量を標準化する
  - [ ] 訓練データから平均と標準偏差を求める
  - [ ] 訓練データの平均と標準偏差で別のデータを標準化する
  - [ ] linfa の `LinearScaler::standard()` と突き合わせる
- [ ] 多項式特徴量を作る
  - [ ] 2 乗の項を加える
  - [ ] 2 つの列の積（交互作用の項）を加える
- [ ] IQR で外れ値を検出する
- [ ] タブ区切り・Shift_JIS のファイルを読み込んで結合する
- [ ] 特徴量の組み合わせごとに決定係数を測る

この章のモジュールは `src/chapter09/` に置き、技法ごとにファイルを分けます。

| モジュール | 役割 |
|-----------|------|
| `dummies` | ダミー変数 |
| `standardizer` | 標準化 |
| `linfascaler` | linfa の標準化の呼び出し |
| `polynomial` | 多項式特徴量 |
| `outliers` | 外れ値の検出 |
| `delimited`・`bikeweather` | 区切り文字・文字コードを指定した読み込みと、表の結合 |
| `linear`・`boston` | 正規方程式による線形回帰、決定係数、Boston データの前処理 |

### 章の失敗を enum で表す

第 3 章までは、第 2 章の `chapter02::Error` をそのまま使ってきました。この章では新しい失敗が 3 つ出てくるので、章の `Error` を作り、第 2 章の失敗を包みます。

```rust
/// 第 9 章で起こりうる失敗。第 2 章の失敗を包み、この章だけの失敗を足す。
#[derive(Debug)]
pub enum Error {
    /// 第 2 章の表・前処理の失敗。
    Chapter02(crate::chapter02::Error),
    /// 指定した文字コードで読めない。
    Decode { file: String, encoding: &'static str },
    /// 正規方程式を解けない（列が互いに独立でない）。
    Singular,
    /// 値が 1 件も無い。
    Empty(&'static str),
}

impl From<crate::chapter02::Error> for Error {
    fn from(error: crate::chapter02::Error) -> Self {
        Error::Chapter02(error)
    }
}
```

`From` を書いておくと、第 2 章の関数を呼んだ行に `?` を付けるだけで、失敗が自動でこの章の `Error` に変換されて上に返ります。Java 版のチェック例外なら `catch` して包み直すところ、Go 版なら `if err != nil { return fmt.Errorf("...: %w", err) }` と書くところが、Rust では `?` の 1 文字に収まります。

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

CRIME のようなカテゴリ値は、そのままでは線形回帰に渡せません。カテゴリごとに「その値なら 1、それ以外は 0」の列を作ります。3 つのカテゴリなら 2 列あれば区別できる（2 列とも 0 なら残りの 1 つ）ので、Python 版の `pd.get_dummies(drop_first=True)` と同じく、辞書順で先頭のカテゴリを除きます。

```rust
#[test]
fn 先頭を除いたカテゴリを辞書順に返す() {
    assert_eq!(
        categories(&strings(&["low", "high", "very_low", "low"])),
        strings(&["low", "very_low"])
    );
}

#[test]
fn 欠損値はカテゴリに数えない() {
    assert_eq!(categories(&strings(&["low", "", "high"])), strings(&["low"]));
}
```

```rust
/// 欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す
/// （pandas の `get_dummies(drop_first=True)` と同じ）。
pub fn categories(values: &[String]) -> Vec<String> {
    let mut found: Vec<String> = values
        .iter()
        .map(|value| value.trim())
        .filter(|value| !value.is_empty())
        .map(str::to_string)
        .collect();

    found.sort_unstable();
    found.dedup();

    found.into_iter().skip(1).collect()
}
```

Java 版の `distinct().sorted().skip(1)` に当たるのが `sort_unstable()` → `dedup()` → `skip(1)` です。Rust の `dedup` は「隣り合う重複だけ」を消すので、**先に並べ替える必要があります**。並べ替えずに `dedup` を呼ぶと重複が残るという、順序に依存する API です。

### 表に列を加える

ダミー変数の列は、表の末尾に「列名_カテゴリ」という名前で加えます。

```rust
#[test]
fn カテゴリごとに0と1の列を作り元の列を取り除く() {
    let encoded = encode(
        &crime_table(&["low", "high", "very_low"]),
        "CRIME",
        &strings(&["low", "very_low"]),
    );

    assert_eq!(encoded.columns, strings(&["RM", "CRIME_low", "CRIME_very_low"]));
    // …各行の CRIME_low が "1", "0", "0"、CRIME_very_low が "0", "0", "1" になる
}
```

`Table` は列の並び `Vec<String>` と行 `Vec<Row>` を持つ構造体です。元の表を借りて、新しい表を返します。

```rust
/// 列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。
/// 値が一致すれば "1"、それ以外は "0"。
pub fn encode(table: &Table, column: &str, categories: &[String]) -> Table {
    let dummy_columns: Vec<String> = categories
        .iter()
        .map(|category| format!("{column}_{category}"))
        .collect();

    let columns: Vec<String> = table
        .columns
        .iter()
        .filter(|name| name.as_str() != column)
        .cloned()
        .chain(dummy_columns.iter().cloned())
        .collect();

    let rows = table
        .rows
        .iter()
        .map(|row| encode_row(row, column, categories, &columns))
        .collect();

    Table { columns, rows }
}
```

引数の `table` は `&Table`（借用）なので、呼び出し側の表は変わりません。Java 版・Kotlin 版は「record は変更できない」という約束で同じことを実現していますが、Rust では **借用している限り書き換えられない** ことをコンパイラが保証します。

セルは文字列のまま `"1"`・`"0"` にしておきます。こうすると、第 2 章の `split_features_and_target`・`column_means`・`fill_missing` を、ダミー変数の列にもそのまま使えます。カテゴリを引数で受け取るのは、訓練データで決めたカテゴリをテストデータにも当てはめ、列をそろえるためです。

## 9.5 特徴量を標準化する

### 標準化とは

列ごとに平均を引いて標準偏差で割り、平均 0・標準偏差 1 にそろえることを **標準化** と呼びます。単位や桁の違う列（部屋数と税率など）を同じ尺度で比べられるようになります。平均と標準偏差は訓練データだけで求め、テストデータにも同じ値を使います。テストデータの平均を使うと、テストデータの情報が学習に漏れるからです。

### Red: まだ無い型を呼ぶ

```rust
#[test]
fn 訓練データから列ごとの平均と標準偏差を求める() {
    let standardizer =
        Standardizer::fit(&[row(1.0, 10.0), row(2.0, 10.0), row(3.0, 40.0)]).unwrap();

    assert!((standardizer.mean("RM").unwrap() - 2.0).abs() < 1e-12);
    assert!((standardizer.mean("LSTAT").unwrap() - 20.0).abs() < 1e-12);
    // 件数（3）で割る標準偏差（母標準偏差）。scikit-learn の StandardScaler と同じ定義
    assert!((standardizer.std("RM").unwrap() - (2.0_f64 / 3.0).sqrt()).abs() < 1e-12);
    assert!((standardizer.std("LSTAT").unwrap() - 200.0_f64.sqrt()).abs() < 1e-12);
}
```

RM の標準偏差を `√(2/3)` としているのは、件数（3）で割る標準偏差だからです。実行すると、まだ `Standardizer` が無いのでコンパイルで止まります。

```text
error[E0433]: failed to resolve: use of undeclared type `Standardizer`
  --> src/chapter09/standardizer.rs:17:13
   |
17 |             Standardizer::fit(&[row(1.0, 10.0), row(2.0, 10.0), row(3.0, 40.0)]).unwrap();
   |             ^^^^^^^^^^^^ use of undeclared type `Standardizer`
```

`error[E0433]` のように番号が付くので、`rustc --explain E0433` で解説を読めます。Java 版の「シンボルを見つけられません」より、次に何をすべきかが分かりやすい表示です。

### Green: 列ごとの平均と標準偏差

Java 版は列名から平均・標準偏差への `Map` を 2 つ持つ record にしました。Rust 版は「列名の並び」と「値の並び」を対応させた 3 本の `Vec` にします。`HashMap` にすると列の順が失われ、表示のたびに並べ直す必要が出るからです。

```rust
/// 列ごとの平均と標準偏差。訓練データで `fit` し、同じ値で訓練データとテストデータの両方を
/// `transform` する。列の順は特徴量の列の順のまま。
#[derive(Debug, Clone, PartialEq)]
pub struct Standardizer {
    pub columns: Vec<String>,
    pub means: Vec<f64>,
    /// 標準偏差。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする
    pub stds: Vec<f64>,
}

impl Standardizer {
    /// 特徴量のすべての列について、平均と標準偏差を求める。
    pub fn fit(x: &[Features]) -> Result<Standardizer> {
        let first = x.first().ok_or(Error::Empty("特徴量"))?;
        let columns = first.columns.clone();

        let mut means = Vec::with_capacity(columns.len());
        let mut stds = Vec::with_capacity(columns.len());

        for column in &columns {
            let values: Vec<f64> = x
                .iter()
                .map(|features| features.value(column))
                .collect::<crate::chapter02::Result<_>>()?;

            let mean = average(&values);
            let variance = average(
                &values
                    .iter()
                    .map(|value| (value - mean) * (value - mean))
                    .collect::<Vec<f64>>(),
            );
            let std = variance.sqrt();

            means.push(mean);
            stds.push(if std == 0.0 { 1.0 } else { std });
        }

        Ok(Standardizer { columns, means, stds })
    }
}
```

`x.first()` は `Option<&Features>` を返すので、`ok_or` で「特徴量が 1 件もありません」という失敗にしてから `?` で返します。Java 版が `if (x.isEmpty()) throw new IllegalArgumentException(...)` と書いた場所です。Rust には例外が無いので、**空かどうかを確かめ忘れると、そもそもコンパイルが通りません**。

`collect::<crate::chapter02::Result<_>>()?` は、`Result` を返すイテレータを「全部成功なら `Vec`、1 つでも失敗ならその失敗」にまとめる書き方です。`?` のところで `From` が効いて、第 2 章の失敗がこの章の `Error::Chapter02` になります。

分散 0 の列（すべて同じ値）は標準偏差を 1 に置き換えます。そうしないと 0 で割って `NaN`（もしくは `inf`）になります。Rust の浮動小数点の 0 除算はパニックにならず静かに `NaN` を返すので、ここは自分で気づいてテストを書くしかありません。

```rust
#[test]
fn すべて同じ値の列は標準化すると0になる() {
    let standardizer = Standardizer::fit(&[row(1.0, 5.0), row(3.0, 5.0)]).unwrap();

    assert!((standardizer.std("LSTAT").unwrap() - 1.0).abs() < 1e-12);
    // …LSTAT を標準化すると 0 になる
}
```

### linfa の標準化と突き合わせる

linfa-preprocessing には `LinearScaler::standard()` があります。ADR 009 に書いたとおり **母標準偏差（n で割る）** のはずなので、実測で確かめます。

```rust
/// 訓練データの値から平均と標準偏差を求め、別の値を標準化する。
/// linfa には 1 列だけを渡す API が無いので、1 列の行列にしてから渡す。
pub fn standardize(train: &[f64], values: &[f64]) -> Result<Vec<f64>> {
    let scaler = LinearScaler::standard()
        .fit(&dataset(train)?)
        .map_err(|error| Chapter02Error::Library(error.to_string()))?;

    let scaled = scaler.transform(dataset(values)?);

    Ok(scaled.records().column(0).to_vec())
}

/// 1 列の行列に正解ラベルの入れ物を付けて、linfa のデータセットにする。
fn dataset(values: &[f64]) -> Result<DatasetBase<Array2<f64>, Array1<()>>> {
    let records = Array2::from_shape_vec((values.len(), 1), values.to_vec())
        .map_err(|error| Chapter02Error::Library(error.to_string()))?;

    Ok(DatasetBase::from(records))
}
```

linfa は「特徴量の行列」と「正解ラベル」の組（`DatasetBase`）を受け取る設計なので、正解ラベルの要る場面でなくても入れ物が必要です。`DatasetBase::from(records)` を使うと、正解ラベルの型が `Array1<()>`（中身の無いタプル）になります。**「値を持たない型」を型として書けるのが Rust らしいところ**で、Java 版で `Object` や `null` を渡すような曖昧さがありません。

```rust
#[test]
fn linfaの標準化は母標準偏差で割る() {
    // 平均 2.5、母標準偏差 sqrt(1.25)。標本標準偏差なら sqrt(5/3) になる
    let scaled = standardize(&[1.0, 2.0, 3.0, 4.0], &[1.0]).unwrap();

    let population = (1.0 - 2.5) / 1.25_f64.sqrt();

    assert!((scaled[0] - population).abs() < 1e-12, "{scaled:?}");
}
```

このテストは通りました。**linfa の `LinearScaler::standard()` は母標準偏差（n で割る）で、scikit-learn の `StandardScaler` と同じ定義です。** 標本標準偏差（n − 1 で割る）を使う Go 版の `stat.StdDev`、Java 版の Tribuo（`MeanStdDevTransformation` は不偏）とはここが違います。実データでも 30 件すべてが 10⁻¹² より近い値になることを、`tests/boston_features.rs` で確かめています。

```rust
// linfa の LinearScaler::standard() は母標準偏差（n で割る）なので、自作と一致する
for (index, (ours, theirs)) in ours.iter().zip(&theirs).enumerate() {
    assert!(
        (ours - theirs).abs() < 1e-12,
        "{index} 件目: 自作 {ours}, linfa {theirs}"
    );
}
```

| 言語版 | ライブラリ | 標準偏差の定義 | 自作と一致するか |
|-------|-----------|--------------|----------------|
| Rust | linfa-preprocessing `LinearScaler::standard()` | 母標準偏差（n） | **一致する** |
| Python | scikit-learn `StandardScaler` | 母標準偏差（n） | 一致する |
| Java | Tribuo `MeanStdDevTransformation` | 標本標準偏差（n − 1） | しない（定義が違う） |
| Go | gonum `stat.StdDev` | 標本標準偏差（n − 1） | しない（定義が違う） |

「標準化」という同じ名前でも、ライブラリごとに定義が違います。ライブラリと数値を比べるときは、名前ではなく定義をそろえます。

## 9.6 多項式特徴量を作る

### 2 乗の項と交互作用の項

同じ列を 2 回掛ければ 2 乗の項、違う列どうしを掛ければ **交互作用の項** です。列の組は「重複を許して 2 つ選ぶ」組み合わせで、scikit-learn の `PolynomialFeatures` と同じ順に並べます。

```rust
/// 2 つの列の組。左と右が同じなら 2 乗の項を表す。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Pair {
    pub left: String,
    pub right: String,
}

impl Pair {
    /// 項の名前。scikit-learn の `get_feature_names_out` と同じ形（"RM^2"・"RM LSTAT"）にする。
    pub fn name(&self) -> String {
        if self.left == self.right {
            format!("{}^2", self.left)
        } else {
            format!("{} {}", self.left, self.right)
        }
    }
}

/// 重複を許して 2 つの列を選ぶ組を、scikit-learn の `PolynomialFeatures` と同じ順に並べる。
pub fn pairs_with_replacement(columns: &[String]) -> Vec<Pair> {
    let mut pairs = Vec::new();

    for (i, left) in columns.iter().enumerate() {
        for right in columns.iter().skip(i) {
            pairs.push(Pair { left: left.clone(), right: right.clone() });
        }
    }

    pairs
}
```

`columns.iter().skip(i)` は Java 版の `for (int j = i; j < n; j++)` に当たります。clippy は「ループ変数が添字にしか使われていない」ときに指摘してくるので、Rust ではイテレータで書くのが既定の作法です。

```rust
#[test]
fn 二乗の項と交互作用の項を加える() {
    let expanded = expand(&[row(2.0, 3.0)], &strings(&["RM", "LSTAT"])).unwrap();

    assert_eq!(
        expanded[0].columns,
        strings(&["RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2"])
    );
    assert_eq!(expanded[0].values, vec![2.0, 3.0, 4.0, 6.0, 9.0]);
}
```

日本語のテスト名は関数名になるので、`2乗の項…` のように **数字で始めることはできません**（識別子の規則）。`二乗の項…` と書きました。

`expand` で作った項から、使う項だけを `select` で選びます。無い項を選ぶと第 2 章の「列がありません」がそのまま返ります。

```rust
#[test]
fn 無い列は選べない() {
    assert_eq!(
        select(&[row(2.0, 3.0)], &strings(&["RM^2"])).unwrap_err().to_string(),
        "列がありません: RM^2"
    );
}
```

`Error::Chapter02(error)` の `Display` は中の失敗をそのまま表示するようにしたので、呼び出し側から見ると第 2 章のメッセージがそのまま出ます。包んでも読みやすさが落ちません。

## 9.7 外れ値を検出する

**四分位範囲（IQR）** は、第 3 四分位数 Q3 と第 1 四分位数 Q1 の差です。Q1 − 1.5 × IQR より小さい値と、Q3 + 1.5 × IQR より大きい値を外れ値とみなします。

```rust
/// 分位数を求める。位置が値の間にあれば前後の値から線形補間する
/// （pandas の `quantile` の既定と同じ）。
pub fn quantile(values: &[f64], q: f64) -> Result<f64> {
    if values.is_empty() {
        return Err(Error::Empty("値"));
    }

    let mut sorted = values.to_vec();
    sorted.sort_by(f64::total_cmp);

    let position = (sorted.len() - 1) as f64 * q;
    let lower = position.floor() as usize;
    let upper = position.ceil() as usize;
    let fraction = position - lower as f64;

    Ok(sorted[lower] + (sorted[upper] - sorted[lower]) * fraction)
}
```

`f64` は `NaN` があるため全順序ではなく、`sort()` をそのまま呼べません（`Ord` を実装していない）。`sort_by(f64::total_cmp)` を使うと、`NaN` も含めた全順序で並べ替えられます。Java 版の `stream().sorted()` が `Double.compare` で黙って動くのに対し、Rust は **「f64 は素朴には並べ替えられない」ことを型で知らせてくる** ところが違います。

```rust
#[test]
fn 分位数を線形補間で求める() {
    let values = vec![1.0, 2.0, 3.0, 4.0];

    // 位置 = 3 * 0.25 = 0.75 なので、1 と 2 の間を 0.75 の割合で補間する
    assert!((quantile(&values, 0.25).unwrap() - 1.75).abs() < 1e-12);
    assert!((quantile(&values, 0.75).unwrap() - 3.25).abs() < 1e-12);
}

#[test]
fn 四分位範囲から離れた値を外れ値とする() {
    let values = vec![1.0, 2.0, 3.0, 4.0, 100.0];

    assert_eq!(
        iqr_outliers(&values, DEFAULT_K).unwrap(),
        vec![false, false, false, false, true]
    );
}
```

外れ値を **訓練データからだけ** 取り除く関数も足します。テストデータは現実に来るデータなので、外れ値を含んだまま評価します。

```rust
/// 訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。
pub fn remove_target_outliers(
    split: &TrainTestSplit<Features, f64>,
) -> Result<TrainTestSplit<Features, f64>> {
    let outliers = iqr_outliers(&split.t_train, DEFAULT_K)?;

    let kept: Vec<usize> = outliers
        .iter()
        .enumerate()
        .filter(|(_, outlier)| !**outlier)
        .map(|(index, _)| index)
        .collect();

    Ok(TrainTestSplit {
        x_train: kept.iter().map(|index| split.x_train[*index].clone()).collect(),
        x_test: split.x_test.clone(),
        t_train: kept.iter().map(|index| split.t_train[*index]).collect(),
        t_test: split.t_test.clone(),
    })
}
```

`&&bool` を剥がす `!**outlier` は見慣れない形ですが、`iter()` で `&bool` になり、`filter` の引数でもう 1 枚参照が付くためです。

## 9.8 表を結合して特徴量を増やす

### タブ区切りと Shift_JIS

区切り文字と文字コードを引数で受け取る読み込みを書きます。文字コードは **`enum` で 2 つだけ** に絞ります。

```rust
/// 読み込むファイルの文字コード。`enum` にすると、呼び出し側は 2 つのどちらかしか渡せない。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Encoding {
    /// UTF-8。
    Utf8,
    /// Shift_JIS。
    ShiftJis,
}
```

Java 版は `Charset.forName("Shift_JIS")` と文字列で指定するので、打ち間違えると実行時に `UnsupportedCharsetException` になります。`enum` にすると、**打ち間違いはコンパイルエラー** になり、`match` の網羅性もコンパイラが検査してくれます。

```rust
/// 1 行目を列名として読み込む。指定した文字コードで読めなければ失敗を返す。
pub fn load(file: &Path, encoding: Encoding, delimiter: char) -> Result<Table> {
    let bytes = std::fs::read(file)?;
    let (text, _, had_errors) = encoding.encoding().decode(&bytes);

    if had_errors {
        return Err(Error::Decode {
            file: file.display().to_string(),
            encoding: encoding.name(),
        });
    }

    let mut lines = text.lines().filter(|line| !line.trim().is_empty());
    let columns: Vec<String> = match lines.next() {
        Some(header) => header.split(delimiter).map(str::to_string).collect(),
        None => return Err(Error::Empty("行")),
    };

    // …残りの行をセルの対応表にして Row にする
}
```

encoding_rs の `decode` は `(Cow<str>, 使った文字コード, 化けたか)` の 3 つ組を返します。3 つめの `had_errors` が `true` なら、置換文字（U+FFFD）が混ざっているということなので、失敗にします。

### UTF-8 として読むと Err になる

`weather.csv` を UTF-8 として読むとどうなるかを、実データのテストで確かめました。

```rust
#[test]
fn weather_csvはutf8として読めない() {
    let Some(csv_file) = data_file("weather.csv") else {
        return;
    };

    let bytes = std::fs::read(&csv_file).expect("読めること");

    // Go 版の japanese デコーダは黙って U+FFFD に置き換えるが、
    // Rust の String::from_utf8 は Err を返す
    assert!(String::from_utf8(bytes).is_err());
    assert!(load(&csv_file, Encoding::Utf8, ',').is_err());
    assert!(load(&csv_file, Encoding::ShiftJis, ',').is_ok());
}
```

`String::from_utf8` は `Result<String, FromUtf8Error>` を返します。Rust の `String` は「中身が正しい UTF-8 である」ことを型で保証しているので、**そうでないバイト列からは `String` を作れません**。

| 言語版 | Shift_JIS を UTF-8 として読むと |
|-------|------------------------------|
| Rust | `String::from_utf8` が `Err`。encoding_rs の `decode` は `had_errors` が `true` |
| Java | `Files.readAllLines` が `MalformedInputException` を投げる |
| Go | `golang.org/x/text/encoding/japanese` が黙って U+FFFD に置き換える（例外にならない） |

Go 版は「化けたことに気づくテストを自分で書く」必要がありましたが、Rust と Java は言語・ライブラリの側が教えてくれます。Rust はさらに、失敗が `Result` として **戻り値の型に現れる** ので、呼び出し側が無視できません。

### 対応表を作って結合する

天気 ID をキーにした `HashMap<&str, &Row>` を作り、自転車の表を 1 行ずつ引きます（内部結合）。天気の表に無い ID の行は残しません。

```rust
let mut by_id: HashMap<&str, &Row> = HashMap::with_capacity(weather.rows.len());

for row in &weather.rows {
    by_id.insert(row.text(KEY)?, row);
}
```

キーも値も **借用** なので、対応表を作るためにデータを複製しません。Java 版の `Collectors.toMap(row -> row.text(KEY), Function.identity())` は参照を入れますが、Rust ではそれが「`weather` が生きているあいだだけ有効」だとコンパイラに伝わり、`weather` を先に捨てるコードが書けなくなります。

天気ごとの平均は、`Vec<(String, f64)>` に集計してから多い順に並べます。`HashMap` を使うと順が失われるので、最初から順のある入れ物を使います。

```rust
means.sort_by(|left, right| right.1.total_cmp(&left.1));
```

## 9.9 特徴量の効果を測る

### Boston データの前処理

`Boston::prepare` は、CRIME をダミー変数にしてから、第 2 章の関数で「特徴量と正解に分ける → シード付きで分割する → 訓練データの平均値で両方の欠損値を補完する」を行います。

第 2 章の `split_features_and_target` は正解ラベルを文字列で返すので、`PRICE` は `f64` に読み直します。

```rust
let prices: Vec<f64> = labels
    .iter()
    .map(|label| {
        label
            .trim()
            .parse::<f64>()
            .map_err(|_| chapter02::Error::NotANumber {
                column: Self::TARGET.to_string(),
                value: label.clone(),
            })
    })
    .collect::<chapter02::Result<_>>()?;
```

`parse::<f64>()` の失敗（`ParseFloatError`）は情報が少ないので、どの列のどの値かが分かる第 2 章の `NotANumber` に置き換えています。

### 線形回帰と決定係数

決定係数を測るために、Java 版・Kotlin 版と同じく、この章に正規方程式を解く最小の `LinearModel` を置きました。Java 版は Tribuo の `DenseMatrix` のコレスキー分解を借りましたが、Rust 版はここで依存を増やさず、ndarray で行列の積を作り、**コレスキー分解と前進・後退代入を自分で書きます**（linfa が内部で使う linfa-linalg を直接の依存にすることもできますが、重回帰そのものは第 7 章で linfa-linear と突き合わせているので、この章は決定係数を測る道具に絞りました）。

```rust
/// 対称正定値の連立方程式 A x = b を、コレスキー分解 A = L Lᵀ で解く。
/// 対角が 0 以下になれば「列が互いに独立でない」として失敗を返す。
fn solve_cholesky(a: &Array2<f64>, b: &Array1<f64>) -> Result<Vec<f64>> {
    let n = a.nrows();
    let mut l = Array2::<f64>::zeros((n, n));

    for i in 0..n {
        for j in 0..=i {
            let sum: f64 = (0..j).map(|k| l[[i, k]] * l[[j, k]]).sum();

            if i == j {
                let diagonal = a[[i, i]] - sum;

                if diagonal <= 0.0 {
                    return Err(Error::Singular);
                }

                l[[i, j]] = diagonal.sqrt();
            } else {
                l[[i, j]] = (a[[i, j]] - sum) / l[[j, j]];
            }
        }
    }

    // …L y = b を前進代入、Lᵀ x = y を後退代入で解く
}
```

Java 版は `choleskyFactorization()` が `Optional` を返すので `orElseThrow` で例外にしましたが、Rust 版は分解の途中で対角が 0 以下になった時点で `Err(Error::Singular)` を返します。「解けない」ことを **例外ではなく戻り値** で表すので、呼び出し側は `?` で上に返すか `match` で処理するかを選ばされます。

```rust
#[test]
fn 同じ値の列が2つあれば解けない() {
    let rows = vec![vec![1.0, 1.0], vec![2.0, 2.0], vec![3.0, 3.0]];
    let t = vec![1.0, 2.0, 3.0];

    assert_eq!(
        LinearModel::fit(&rows, &t).unwrap_err().to_string(),
        "特徴量の列が互いに独立でないため、正規方程式を解けません"
    );
}
```

特徴量の組ごとの評価は `Boston::score_feature_set` にまとめました。訓練データとテストデータのそれぞれで多項式特徴量を作って項を選び、**訓練データで** `Standardizer` を `fit` し、両方を `transform` してから学習します。

```rust
pub fn score_feature_set(
    split: &TrainTestSplit<Features, f64>,
    columns: &[String],
    terms: &[String],
) -> Result<Scores> {
    let train = polynomial::select(&polynomial::expand(&split.x_train, columns)?, terms)?;
    let test = polynomial::select(&polynomial::expand(&split.x_test, columns)?, terms)?;

    let standardizer = Standardizer::fit(&train)?;
    let x_train = to_rows(&standardizer.transform(&train)?);
    let x_test = to_rows(&standardizer.transform(&test)?);

    let model = LinearModel::fit(&x_train, &split.t_train)?;

    Ok(Scores {
        train: r_squared(&split.t_train, &model.predict(&x_train))?,
        test: r_squared(&split.t_test, &model.predict(&x_test))?,
    })
}
```

価格を `3 × RM² + 1` にした架空のデータで、「2 乗の項が無いと当てきれない」「2 乗の項を加えると訓練データもテストデータも決定係数が 1 になる」ことを確かめてから、実データに進みました。

### 実データで測る

```bash
cargo run --bin chapters -- chapter09
```

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
決定係数:
  元の特徴量（3 列）: 訓練 0.6972, テスト 0.5239
  2 乗の項を追加（6 列）: 訓練 0.8629, テスト 0.6804
  交互作用の項も追加（9 列）: 訓練 0.8658, テスト 0.6828
訓練データの PRICE の外れ値: 4 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.7250, テスト 0.5882
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

「標準化した訓練データの RM」は、標準化した訓練データにもう一度 `Standardizer::fit` を当て、その平均と標準偏差を表示しています。

- **2 乗の項** を加えると、テストデータの決定係数が 0.5239 から 0.6804 に上がりました
- **交互作用の項** も加えると、訓練データもテストデータもごくわずかに上がりました（0.6804 → 0.6828）。Java 版はここでテストデータが下がり（0.8628 → 0.8213）、Kotlin 版はほとんど変わりませんでした。100 件のデータでは、分け方によって結論が揺れる判断だと分かります
- **外れ値** を除いて学習すると、テストデータの決定係数は 0.5882 に下がりました。テストデータには外れ値が残っているので、外れ値を学ばなかったモデルはそれを当てられません。これは Java 版・Kotlin 版と同じ向きです
- **天気ごとの平均利用者数** は Java 版・Go 版と同じ値になりました。集計は乱数を使わないので、言語をまたいで一致します

決定係数の値が Java 版（2 乗の項でテスト 0.8628）と違うのは、第 2 章と同じく乱数の違いです。Rust 版は `rand` の `StdRng::seed_from_u64(0)` と Fisher-Yates で並べ替えるので、同じシードでも訓練データとテストデータに入る行が Java 版・Go 版と違います。**言語版をまたいで比べられるのは「向き」であって「値」ではありません。**

この出力は `tests/boston_features.rs` で固定しています。学習データが無い環境では、ファイルの有無を確かめて早期に戻ります（Rust の標準のテストにスキップの仕組みは無いため）。

## 9.10 Notebook による探索と可視化

Rust 版では Notebook と可視化の節を設けません。標準化の前後の分布、特徴量と価格の関係、外れ値、天気ごとの利用者数のグラフは、[Python 版の第 9 章](../python/09-feature-engineering.md) と [Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) の「Notebook による探索と可視化」の節を参照してください。

## 9.11 リファクタリング

TODO リストをすべて終えてから、`cargo fmt` と `cargo clippy --all-targets -- -D warnings` をかけました。

- **`as` による変換の指摘** — 件数（`usize`）を `f64` にする `as` は、clippy の `cast_precision_loss` に引っかかります。平均を求めるためには必要なので、その行だけに `#[allow(clippy::cast_precision_loss)]` を付け、理由が分かる位置に置きました
- **テスト名の識別子** — `2乗の項…` は「識別子は数字で始められない」のでコンパイルエラーになります。`二乗の項…` に直しました
- **スネークケースの警告** — `シフトJISのファイルを読み込む` のように **ASCII の大文字を含む** 日本語のテスト名は `non_snake_case` の警告になります（日本語だけの名前は対象外）。`shift_jisのファイルを読み込む` に直しました
- **表示の桁数** — `-0.00` と表示されないように、`1e-9` より小さい値を 0 とみなす `zeroed` を用意しました

### 第 14 章から使う API

第 14 章（K-means）でも標準化を使うので、`Standardizer` は次の形で公開しています。

| API | 内容 |
|-----|------|
| `Standardizer::fit(&[Features])` | すべての列の平均と標準偏差（件数で割る）を求める |
| `standardizer.transform(&[Features])` | リストを標準化する |
| `standardizer.transform_one(&Features)` | 1 件を標準化する。平均を持たない列はそのまま残す |
| `standardizer.mean(column)`・`std(column)` | 列名で平均・標準偏差を読む |

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を Rust の TDD で自作し、標準化を linfa と突き合わせました。

| 技法 | 自作したもの | 突き合わせたライブラリ | 落とし穴 |
|------|------------|-------------------|---------|
| ダミー変数 | `dummies::categories`・`encode` | —（linfa-preprocessing に無い） | 並べ替えてから `dedup` する。訓練データとテストデータで列をそろえる |
| 標準化 | `Standardizer` | linfa-preprocessing の `LinearScaler::standard()` | 標準偏差の定義がライブラリで違う。分散 0 の列。テストデータの平均を使わない |
| 多項式特徴量 | `polynomial::expand`・`pairs_with_replacement` | — | 列数が急に増えて過学習しやすい |
| 外れ値の検出 | `outliers::quantile`・`iqr_outliers` | — | `f64` は `Ord` ではないので `total_cmp` で並べる |
| 表の結合 | `delimited::load`・`bikeweather::join_weather` | encoding_rs | 区切り文字と文字コード、内部結合で消える行 |

Rust らしさが出たのは次の 3 点です。

1. **失敗を `enum` で足し、`From` で包む** — 章ごとに `Error` を持たせても、`?` の 1 文字で下の層の失敗を素通しできる。包んでもメッセージは中身がそのまま出る
2. **選択肢を `enum` で型にする** — 文字コードを `Encoding::ShiftJis` にすると、打ち間違いがコンパイルエラーになり、`match` の網羅性も検査される
3. **UTF-8 であることが `String` の型の保証** — Shift_JIS のバイト列から `String` は作れない。Go のデコーダのように黙って化けることがない

次の章では、ロジスティック回帰とランダムフォレストを自作し、トレイトでモデルを共通化します。
