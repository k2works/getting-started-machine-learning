---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "アヤメのデータを読み込み、欠損値を平均値で補完して訓練データとテストデータに分ける。Option で欠損値を表し、ジェネリクスで分割結果を表す Rust の書き方を Java 版・Go 版と対比する。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T15:20:00Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

この章では、アヤメ（iris）のデータを読み込み、欠損値を平均値で補完して、訓練データとテストデータに分けます。機械学習の本番はまだ先ですが、**データを整えるところが実務では最も時間を食う**ところです。

テスト駆動開発の技法としては、**三角測量**を扱います。1 つのテストだけでは仮実装（定数を返す）で通ってしまうとき、2 つめ・3 つめのテストを足して実装を一般化へ追い込む技法です。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と同じ題材・同じ TODO リストで進めます。Rust 版では次の 3 つと対比します。[Java 版](../java/02-data-preprocessing-and-triangulation.md)・[C# 版](../csharp/02-data-preprocessing-and-triangulation.md) はライブラリの揃った静的型付け言語という立場が同じで、[Go 版](../go/02-data-preprocessing-and-triangulation.md) は例外を持たない点が同じです。この章で Rust らしさが出るのは次の 4 つです。

1. **欠損値を `Option<f64>` で表す** — Go 版が「値と ok」の多値返却で表した部分
2. **`#[derive(PartialEq)]` だけでベクタどうしを比べられる** — Go 版の `reflect.DeepEqual` が要らない
3. **分割の結果をジェネリクスの `TrainTestSplit<X, T>` で表す**
4. **クレートの版が型を分ける** — `rand` を 0.8 に固定する理由

## 2.2 題材とデータ

### iris.csv

`iris.csv` は 150 件のアヤメのデータです。

| 列 | 意味 | 値 |
|----|------|-----|
| がく片長さ | がく片の長さ（正規化済み） | 0.0〜1.0 の小数 |
| がく片幅 | がく片の幅（正規化済み） | 0.0〜1.0 の小数 |
| 花弁長さ | 花弁の長さ（正規化済み） | 0.0〜1.0 の小数 |
| 花弁幅 | 花弁の幅（正規化済み） | 0.0〜1.0 の小数 |
| 種類 | アヤメの品種 | `Iris-setosa`・`Iris-versicolor`・`Iris-virginica` |

第 1 章の `KvsT.csv` と違って、**いくつかのセルが空欄**です。数えると、がく片長さが 2 件、がく片幅が 1 件、花弁長さが 2 件、花弁幅が 2 件でした（種類は欠けていません）。この空欄をどう扱うかがこの章の主題の 1 つです。

### 訓練データとテストデータ

学習に使ったデータで正解率を測っても、意味のある数字は得られません。丸暗記したモデルが満点を取るだけだからです。そこで、データを 2 つに分けます。

- **訓練データ**（train）— モデルに学習させるためのデータ。この章では 105 件
- **テストデータ**（test）— 学習に使わず、評価だけに使うデータ。この章では 45 件

150 件を 7:3 に分けた結果です。分け方は乱数で決めます。ここで、**シードを固定しても言語ごとに結果が変わる**という問題が出てきます。

### ほかの言語版と数値が変わる理由

第 1 章の正解率 0.7368 は、Python 版から Go 版まですべて一致しました。乱数を使わなかったからです。この章からは一致しません。

`rand` クレートの `StdRng` は、Java の `java.util.Random` とも Go の `math/rand` とも別の擬似乱数の作り方をします。同じシード 0 を渡しても、出てくる数列が違います。並べ替えの結果を比べると、次のようになりました。

| 言語版 | `shuffle(0..10, seed=0)` の並び |
|--------|------------------------------|
| Rust（`rand` 0.8 の `StdRng`） | `[9, 3, 6, 4, 8, 1, 5, 2, 0, 7]` |
| Java（`java.util.Random`） | `[4 8 9 6 3 5 2 1 7 0]` |
| Go（`math/rand`） | `[6 8 2 3 7 5 9 1 0 4]` |

**一致するのは件数だけ**です。訓練データ 105 件・テストデータ 45 件という分け方は同じでも、どの行が訓練側に入るかが違うので、この後で求める平均値も、第 3 章の正解率も一致しません。

これは不具合ではありません。「乱数で分ける」と決めた時点で、再現性はシードと乱数生成器の実装の組み合わせに依存します。**言語をまたいで同じ数値を出したければ、乱数生成器そのものを移植する**しかありません。本シリーズはそこまではせず、**一致しないことを記事とテストに明記する**方針を取ります。実装の `tests/iris_data.rs` にも、その理由をコメントとして残しました。

## 2.3 開発環境の準備

第 1 章と同じく `nix develop .#rust` に入ります。この章では `chapter02` モジュールを足します。

```text
apps/rust/
├── Cargo.toml
├── src/
│   ├── lib.rs
│   ├── dataset.rs
│   ├── chapter01.rs
│   ├── chapter02.rs          # 章のまとめ役（モジュール宣言とエラー型）
│   ├── chapter02/
│   │   ├── table.rs          # CSV の表と行
│   │   ├── preprocessing.rs  # 補完と分割
│   │   └── main.rs           # 章の実行
│   └── bin/chapters.rs
└── tests/
    ├── kvst_data.rs
    └── iris_data.rs          # 実データを使うテスト
```

第 1 章は `src/chapter01.rs` の 1 ファイルで足りましたが、この章からはファイルを分けます。Rust では **`src/chapter02.rs` と `src/chapter02/` を並べて置ける**（2018 edition 以降）ので、`mod.rs` は作りません。`src/chapter02.rs` が親モジュールで、その中で子を宣言します。

```rust
//! 第 2 章: データの前処理。表の読み込み・欠損値の補完・訓練データとテストデータへの分割。

pub mod preprocessing;
pub mod table;

mod main;

pub use main::run;
pub use preprocessing::{
    Features, TARGET, TrainTestSplit, column_means, fill_missing, prepare_iris, shuffle,
    split_features_and_target, split_train_test,
};
pub use table::{Missing, Row, Table};
```

`mod main;` だけ `pub` が付いていません。`run` は `pub use main::run;` で章の直下に再輸出しているので、`chapter02::main::run` という長い道は要らないからです。**公開する名前を親モジュールで並べて宣言できる**のは、Java の `module-info.java` や C# の名前空間にはない書き方で、「この章の外から使ってよいのはこれだけ」を 1 か所で読めます。

依存も 1 つ増えます。

```toml
[dependencies]
csv = "1.4"
rand = "0.8"
```

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] CSV を表として読み込む
  - [ ] 数値の列を読む
  - [ ] 空欄の列は欠損値になる
  - [ ] 数値として読めない列は失敗する
  - [ ] 列が無ければ失敗する
- [ ] 列ごとに欠損値を数える
- [ ] 平均値で欠損値を補完する
  - [ ] 欠損値を除いて列ごとの平均値を求める
  - [ ] 値がすべて空欄なら平均値を求められない
  - [ ] 欠損値を平均値で補完する
- [ ] 特徴量と正解ラベルに分ける
- [ ] 訓練データとテストデータに分ける
  - [ ] テストデータの割合で分ける
  - [ ] 同じシードなら同じ並びになる
  - [ ] 並べ替えても要素は変わらない
  - [ ] 件数が違えば分けられない
- [ ] 実データで前処理の結果を表示する

## 2.5 自作の表で読み込む

### データの表し方を決める

第 1 章では `Person` という「列が決まった構造体」に読み込みました。この章では列の数が増え、欠損値も入ります。しかも第 4 章以降でデータセットが変わるたびに構造体を書き直すのは辛いので、**列名で引ける汎用の表**を作ります。

Python 版・Kotlin 版は pandas の `DataFrame` を使う部分です。Rust には polars という選択肢もありますが、本シリーズでは使いません（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。学習の題材として、前処理の中身を自分の手で書くためです。

表し方はこう決めました。

| 対象 | 型 | 理由 |
|------|-----|------|
| 1 行 | `struct Row { cells: HashMap<String, String> }` | 列名でセルを引く。値は文字列のままで、読むときに解釈する |
| 表 | `struct Table { columns: Vec<String>, rows: Vec<Row> }` | 列の順を `Vec<String>` で別に保つ（`HashMap` は順を保たない） |
| 補完後の特徴量 | `struct Features { columns: Vec<String>, values: Vec<f64> }` | 欠損値を持てない型として分ける |

`Row` が `HashMap<String, String>` を持ち、`Table` が列の順を別に持つ理由は単純で、**`HashMap` の反復の順は保証されないから**です。Rust の `HashMap` は、同じプログラムの同じ実行でも順が変わりえます（ハッシュのシードがプロセスごとに変わる実装です）。「列ごとの欠損値の数」を CSV の列の順で出すには、順を別に持つしかありません。Go の `map` も同じ性質を持つので、Go 版でも同じ設計になりました。

### テストファースト

まずテストから書きます。この章のテストはすべて**架空の値**で書きます。学習データの行は書籍の配布データなので、記事にもテストにも書きません。

```rust
#[cfg(test)]
mod tests {
    use super::*;

    /// 列名と値から行を作る。
    pub(super) fn row(pairs: &[(&str, &str)]) -> Row {
        Row::new(
            pairs
                .iter()
                .map(|(name, value)| (name.to_string(), value.to_string()))
                .collect(),
        )
    }

    #[test]
    fn 数値の列を読む() {
        let row = row(&[("がく片長さ", "5.1")]);

        assert_eq!(row.number("がく片長さ").unwrap(), Some(5.1));
    }

    #[test]
    fn 空欄の列は欠損値になる() {
        let row = row(&[("がく片長さ", "")]);

        assert_eq!(row.number("がく片長さ").unwrap(), None);
        assert!(row.is_missing("がく片長さ").unwrap());
    }
}
```

ヘルパーの `row` は、`&[(&str, &str)]` を受け取って `HashMap<String, String>` に組み立てます。`.collect()` が `HashMap` を作れるのは、`(String, String)` の反復子から `HashMap<String, String>` への `FromIterator` が用意されているからです。**集める先の型は戻り値から推論されます**。ここでは `Row::new` の引数の型で決まります。

### Red: 失敗を確認する

`Row` も `number` もまだありません。

```text
$ cargo test
error[E0425]: cannot find type `Row` in this scope
error[E0422]: cannot find struct, variant or union type `Table` in this scope
```

第 1 章と同じく、静的型付け言語の Red は**コンパイルエラー**です。Rust の場合、テストだけでなく**同じ章のほかのファイルも一緒に落ちます**。`preprocessing.rs` が `use super::table::{Row, Table};` で参照しているからです。

```text
error[E0432]: unresolved imports `super::table::Row`, `super::table::Table`
```

1 つの型が無いだけで章全体がコンパイルできなくなるので、**Red のときは「今書いたテスト以外のエラーは無視する」という読み方**が要ります。Go 版・Java 版でも同じことが起きますが、Rust はモジュールの依存が `use` として明示されているぶん、どこが引きずられたかを追いやすくなっています。

### Green: 明白な実装

`Option` で欠損値を表します。

```rust
/// CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Row {
    cells: HashMap<String, String>,
}

impl Row {
    /// セルの対応表から行を作る。
    pub fn new(cells: HashMap<String, String>) -> Self {
        Row { cells }
    }

    /// 数値の列を読む。空欄なら `None` を返す。
    /// Go 版は「値と ok」の多値返却で表したが、Rust には `Option` があるので素直に書ける。
    pub fn number(&self, column: &str) -> Result<Option<f64>> {
        let cell = self.text(column)?;

        if cell.trim().is_empty() {
            return Ok(None);
        }

        cell.trim()
            .parse()
            .map(Some)
            .map_err(|_| Error::NotANumber {
                column: column.to_string(),
                value: cell.to_string(),
            })
    }

    /// 文字列の列を読む。列が無ければ失敗を返す。
    pub fn text(&self, column: &str) -> Result<&str> {
        self.cells
            .get(column)
            .map(String::as_str)
            .ok_or_else(|| Error::MissingColumn(column.to_string()))
    }

    /// セルが空欄かどうかを返す。
    pub fn is_missing(&self, column: &str) -> Result<bool> {
        Ok(self.text(column)?.trim().is_empty())
    }
}
```

### `Result<Option<f64>>` が意味するもの

戻り値の型が入れ子になっています。この型は、**3 つの状態を 1 つの値で表しています**。

| 値 | 意味 |
|----|------|
| `Ok(Some(5.1))` | 読めた。値は 5.1 |
| `Ok(None)` | 読めた。空欄だった（欠損値） |
| `Err(...)` | 読めなかった。列が無いか、数値として解釈できない |

ここが言語ごとにいちばん形の変わるところです。

| 言語版 | 欠損値の表し方 | 失敗の表し方 |
|--------|--------------|------------|
| Rust | `Option<f64>` の `None` | `Result` の `Err` |
| Go | 多値返却の `(value, ok)` で `ok == false` | 多値返却の `error` |
| Java | `OptionalDouble` または `Double` の `null` | 例外 |
| Python・Kotlin | `NaN`・`None`・`null` | 例外 |

Go 版の `Number(column string) (float64, bool, error)` は**戻り値が 3 つ**になりました。「値」「あるかどうか」「失敗したかどうか」が横に並ぶので、呼ぶ側は毎回 3 つを受け取って、どれを見るか考えることになります。Rust では `Option` と `Result` の入れ子で**縦に**表せるので、呼ぶ側は `?` で失敗だけ先に上へ逃がし、残った `Option` に集中できます。次の節の平均値の計算がその形です。

`cell.trim().parse()` が `f64` にパースされるのは、戻り値の型 `Result<Option<f64>>` から逆算されるからです。`.map(Some)` は「成功したら `Some` で包む」という意味で、`Some` を関数として渡しています。**列挙子のコンストラクタが関数として渡せる**のは Rust の便利なところで、`.map(|value| Some(value))` と書かなくて済みます。

### 空欄と `trim`

`cell.trim().is_empty()` の `trim` には理由があります。CSV の行末に余分な空白が入っていたり、BOM 以外の見えない文字が混ざったりしても、空欄として扱えるようにするためです。第 1 章で確かめたとおり、**BOM は csv クレートが取り除いてくれます**。ほかの言語版が毎回書いた BOM の除去は、Rust 版には出てきません。

Go 版では `strings.Split` で CSV を手書きで分解したために、「行末の空欄」が列の数として数えられないという落とし穴がありました。Rust では csv クレートがレコードの列数を保つので、この落とし穴には落ちません。**自分でパーサを書かないことで避けられる問題**です。

### 表を読み込む

```rust
/// CSV の表。列の順と行を持つ。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Table {
    pub columns: Vec<String>,
    pub rows: Vec<Row>,
}

impl Table {
    /// CSV を読み込んで表にする。csv クレートが BOM を取り除くので、自分で消さなくてよい。
    pub fn load(csv_file: &Path) -> Result<Table> {
        let mut reader = csv::Reader::from_path(csv_file)?;
        let columns: Vec<String> = reader.headers()?.iter().map(str::to_string).collect();

        let mut rows = Vec::new();

        for record in reader.records() {
            let record = record?;
            let cells = columns
                .iter()
                .cloned()
                .zip(record.iter().map(str::to_string))
                .collect();

            rows.push(Row::new(cells));
        }

        Ok(Table { columns, rows })
    }
}
```

`columns.iter().cloned().zip(...)` の `cloned()` が要る理由は所有権です。`Row` は自分の `HashMap<String, String>` を持つので、鍵になる列名も自分のものでなければなりません。`columns` は `Table` が持ち続けるので、行ごとに複製します。**150 行ぶん複製するのは無駄では？** と思うところで、実際そのとおりです。列名を `Rc<str>` で共有する、列ごとに値を縦に持つ（列指向にする）、といった手はあります。この章では読みやすさを優先し、複製する形のままにしました。**どこで複製するかを選べてしまう**のが、ほかの言語版に無い設計の余地です。

`let record = record?;` の 1 行は、`reader.records()` が返すのが `Result<StringRecord, csv::Error>` の反復子だからです。`?` で失敗を上へ返し、成功なら同じ名前で束縛し直しています。**同じ名前で束縛し直す（shadowing）のは Rust では普通の書き方**で、「`record` はもう `Result` ではない」ことが名前を変えずに表せます。

### 列ごとの欠損値の数

```rust
/// 列ごとの欠損値の数。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Missing {
    pub column: String,
    pub count: usize,
}

impl Table {
    /// 列ごとに欠損値の数を数える。列の順は表の列の順のまま。
    pub fn count_missing(&self) -> Result<Vec<Missing>> {
        let mut counts = Vec::with_capacity(self.columns.len());

        for column in &self.columns {
            let mut count = 0;

            for row in &self.rows {
                if row.is_missing(column)? {
                    count += 1;
                }
            }

            counts.push(Missing {
                column: column.clone(),
                count,
            });
        }

        Ok(counts)
    }
}
```

テストは、`Vec<Missing>` をそのまま比べます。

```rust
#[test]
fn 列ごとに欠損値を数える() {
    let table = Table {
        columns: vec!["がく片長さ".to_string(), "種類".to_string()],
        rows: vec![
            row(&[("がく片長さ", "5.1"), ("種類", "Iris-setosa")]),
            row(&[("がく片長さ", ""), ("種類", "Iris-setosa")]),
            row(&[("がく片長さ", ""), ("種類", "Iris-virginica")]),
        ],
    };

    assert_eq!(
        table.count_missing().unwrap(),
        vec![
            Missing {
                column: "がく片長さ".to_string(),
                count: 2
            },
            Missing {
                column: "種類".to_string(),
                count: 0
            },
        ]
    );
}
```

`assert_eq!` が構造体のベクタをそのまま比べられるのは、`Missing` に `PartialEq` を derive したからです。**`Vec<T>` の `PartialEq` は `T` の `PartialEq` から自動で導かれます**。Go 版ではここが `reflect.DeepEqual` になりました。Go の `==` は、スライスを含む構造体には使えないからです。Java 版では `record` の `equals` と `List.equals` に頼りました。Rust は derive 1 行で、しかも**コンパイル時に型ごとに展開される**ので、実行時のリフレクションは走りません。

`count_missing` が `Result` を返すのは、`is_missing` が失敗しうる（列が無いかもしれない）からです。自分の `columns` で引いているので実際には失敗しませんが、**「失敗しないはずだから `unwrap()` する」より「失敗を上へ返す」ほうを選びました**。`unwrap()` はパニックで、ライブラリのコードでパニックさせないというのがこの実装の方針です。

## 2.6 平均値で欠損値を補完する

### 欠損値をどう扱うか

選択肢は 3 つあります。

| やり方 | 長所 | 短所 |
|--------|------|------|
| 欠損のある行を捨てる | 簡単 | データが減る。偏るかもしれない |
| 平均値で埋める | データが減らない | 分散が小さくなる |
| 予測して埋める | 精度が良いこともある | 手間がかかる。過学習の危険 |

150 件のうち 7 件が欠けているだけなので行を捨てても良さそうですが、本シリーズは Python 版に合わせて**平均値で埋める**やり方を採ります。

### 平均値を求める

```rust
/// 欠損値を除いて、列ごとの平均値を求める。
pub fn column_means(rows: &[Row], columns: &[String]) -> Result<HashMap<String, f64>> {
    let mut means = HashMap::with_capacity(columns.len());

    for column in columns {
        let mut sum = 0.0;
        let mut count = 0;

        for row in rows {
            if let Some(value) = row.number(column)? {
                sum += value;
                count += 1;
            }
        }

        if count == 0 {
            return Err(Error::AllMissing(column.clone()));
        }

        means.insert(column.clone(), sum / f64::from(count));
    }

    Ok(means)
}
```

`if let Some(value) = row.number(column)?` の 1 行に、この章の 2 つの道具が同時に出ています。`?` が `Result` を剥がして `Option<f64>` にし、`if let Some(value)` が「値があるときだけ」の分岐を作ります。**欠損値を飛ばすのに、`NaN` の検査も `null` の検査も要りません。** 平均値を求める関数が欠損値の存在を知らずに済む、という形です。

`f64::from(count)` は、`count` が `i32` のときに使える変換です。`as` と違って**失敗しないことが型で保証されている**変換なので、使えるときはこちらを選びます。`usize` からは `f64::from` が使えないので、この関数では `count` を `i32` にしています。

失敗のケースもテストに書きます。

```rust
#[test]
fn 値がすべて空欄なら平均値を求められない() {
    let rows = vec![row(&[("がく片長さ", "")])];

    assert_eq!(
        column_means(&rows, &columns(&["がく片長さ"]))
            .unwrap_err()
            .to_string(),
        "値がすべて空欄です: がく片長さ"
    );
}
```

`unwrap_err().to_string()` でメッセージを確かめています。`Error` に `Display` を実装したので、`to_string()` が使えます。**エラーの種類ではなくメッセージを確かめる**のは一長一短で、メッセージを変えるとテストが落ちます。ここではメッセージも仕様の一部と考えて、あえて文字列で固定しました。種類だけを確かめたいなら `assert!(matches!(error, Error::AllMissing(_)))` と書けます。

### 補完して特徴量にする

補完した結果は `Row` ではなく `Features` にします。**`Features` は欠損値を持てない型**です。

```rust
/// 欠損値を持たない特徴量。列名と値を同じ順で持つ。
#[derive(Debug, Clone, PartialEq)]
pub struct Features {
    pub columns: Vec<String>,
    pub values: Vec<f64>,
}

impl Features {
    /// 列名と値から特徴量を作る。数が合わなければ失敗を返す。
    pub fn new(columns: Vec<String>, values: Vec<f64>) -> Result<Features> {
        if columns.len() != values.len() {
            return Err(Error::LengthMismatch {
                left: columns.len(),
                right: values.len(),
            });
        }

        Ok(Features { columns, values })
    }

    /// 列名で値を読む。
    pub fn value(&self, column: &str) -> Result<f64> {
        self.columns
            .iter()
            .position(|name| name == column)
            .map(|index| self.values[index])
            .ok_or_else(|| Error::MissingColumn(column.to_string()))
    }
}
```

`Row` の `number` が `Result<Option<f64>>` だったのに対し、`Features` の `value` は `Result<f64>` です。**`Option` が消えている**のが大事な点で、型を見るだけで「ここから先に欠損値は無い」と分かります。第 3 章の決定木は `Features` だけを受け取るので、決定木の実装に欠損値の扱いが混ざりません。

`Features` に `Eq` が付いていないのは `f64` を持つからです。`f64` は `NaN != NaN` なので、「すべての値が自分自身と等しい」という `Eq` の約束を守れません。`PartialEq` までです。`Row` や `Table` のように `String` だけを持つ型には `Eq` も付けました。**この区別を型で強制するのは Rust に固有の厳しさ**で、Java の `equals` や Go の `==` は浮動小数点数の事情をここまで明示しません。

補完の本体はこうです。

```rust
/// 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変えない。
pub fn fill_missing(
    rows: &[Row],
    columns: &[String],
    values: &HashMap<String, f64>,
) -> Result<Vec<Features>> {
    let mut filled = Vec::with_capacity(rows.len());

    for row in rows {
        let mut numbers = Vec::with_capacity(columns.len());

        for column in columns {
            let value = match row.number(column)? {
                Some(value) => value,
                None => *values
                    .get(column)
                    .ok_or_else(|| Error::NoFillValue(column.clone()))?,
            };

            numbers.push(value);
        }

        filled.push(Features::new(columns.to_vec(), numbers)?);
    }

    Ok(filled)
}
```

`rows: &[Row]` と借用で受け取り、新しい `Vec<Features>` を返します。**元の行は変えません。** 引数が `&[Row]`（不変の借用）である時点で、この関数が行を書き換えないことがコンパイラに保証されています。Java 版・C# 版では「変えていないこと」をコードを読んで確かめるしかありませんでしたが、Rust では**シグネチャが契約になります**。

`None =>` の腕にある `*` は参照外しです。`values.get(column)` は `Option<&f64>` を返すので、`?` で `&f64` を取り出し、`*` で `f64` にします。

### ベクタどうしをそのまま比べる

補完のテストは、`Vec<Features>` をそのまま比べます。

```rust
#[test]
fn 欠損値を平均値で補完する() {
    let rows = vec![row(&[("がく片長さ", "1.0")]), row(&[("がく片長さ", "")])];
    let means = HashMap::from([("がく片長さ".to_string(), 2.0)]);

    let filled = fill_missing(&rows, &columns(&["がく片長さ"]), &means).unwrap();

    assert_eq!(
        filled,
        vec![
            Features::new(columns(&["がく片長さ"]), vec![1.0]).unwrap(),
            Features::new(columns(&["がく片長さ"]), vec![2.0]).unwrap(),
        ]
    );
}
```

`Features` が `Vec<String>` と `Vec<f64>` を持つのに、`assert_eq!` がそのまま通ります。Go 版ではここが最も面倒でした。Go の構造体はスライスを含むと `==` で比べられないので、`reflect.DeepEqual` を呼ぶことになり、**リフレクションなので型の間違いがコンパイル時に見つかりません**。Rust の `#[derive(PartialEq)]` は、比較のコードを型ごとにコンパイル時に生成します。

| 言語版 | ベクタを含む値の比較 | コンパイル時の検査 |
|--------|-------------------|------------------|
| Rust | `#[derive(PartialEq)]` で `==`・`assert_eq!` | あり（型が違えば比較自体が書けない） |
| Go | `reflect.DeepEqual` | なし（`any` を 2 つ渡すだけ） |
| Java | `record` の `equals` と `List.equals` | あり |
| Python | `==`（`dataclass`） | なし |

## 2.7 特徴量と正解ラベルに分ける

```rust
/// アヤメのデータの正解ラベルの列。
pub const TARGET: &str = "種類";

/// 正解ラベルの列を取り出し、残りの列を特徴量の列にする。
pub fn split_features_and_target(
    table: &Table,
    target: &str,
) -> Result<(Vec<String>, Vec<Row>, Vec<String>)> {
    let columns: Vec<String> = table
        .columns
        .iter()
        .filter(|column| column.as_str() != target)
        .cloned()
        .collect();

    let mut labels = Vec::with_capacity(table.rows.len());

    for row in &table.rows {
        labels.push(row.text(target)?.to_string());
    }

    Ok((columns, table.rows.clone(), labels))
}
```

戻り値がタプルで 3 つです。「特徴量の列名」「行」「正解ラベル」を返します。**3 つ以上になったら構造体にすべきか**は迷うところですが、この関数を呼ぶのは前処理をまとめる `prepare_iris` だけなので、タプルのままにしました。呼ぶ側では分解して受け取ります。

```rust
let (columns, rows, labels) = split_features_and_target(&table, TARGET)?;
```

`.filter(|column| column.as_str() != target)` の `as_str()` が要るのは、`column` が `&&String`（反復子の要素 `&String` への参照）だからです。`&&String` と `&str` は直接比べられないので、`as_str()` で `&str` にそろえます。**参照の階層を意識させられる**のは Rust を書いていて煩わしい場面の 1 つですが、どこで借りていて誰が所有しているかが常に見えている、ということでもあります。

## 2.8 訓練データとテストデータに分ける

### 分割結果をジェネリクスで表す

分割の結果は 4 つのベクタになります。まとめる型を作ります。

```rust
/// 訓練データとテストデータ。X は特徴量の型、T は正解ラベルの型。
#[derive(Debug, Clone, PartialEq)]
pub struct TrainTestSplit<X, T> {
    pub x_train: Vec<X>,
    pub x_test: Vec<X>,
    pub t_train: Vec<T>,
    pub t_test: Vec<T>,
}
```

型引数を 2 つ取ります。`X` が特徴量、`T` が正解ラベルです。この章では `TrainTestSplit<Features, String>` として使いますが、分割する前の段階では `TrainTestSplit<Row, String>` として使っています。第 7 章の回帰では正解ラベルが数値になるので `TrainTestSplit<Features, f64>` になります。**同じ型で使い回せるのがジェネリクスの効きどころ**です。

Java 版は `record TrainTestSplit<X, T>(...)`、Go 版は `type TrainTestSplit[X any, T any] struct`、どれも同じ形です。Rust で違うのは、**`#[derive]` が型引数の制約を自動で足す**ことです。`#[derive(Clone)]` と書くと、生成されるのは「`X: Clone` かつ `T: Clone` のときだけ `TrainTestSplit<X, T>` も `Clone`」という実装です。`X` が複製できない型でも `TrainTestSplit<X, T>` 自体は作れます。

### 仮実装

最初のテストは 1 つだけです。

```rust
#[test]
fn テストデータの割合で分ける() {
    let x: Vec<usize> = (0..10).collect();
    let t: Vec<String> = x.iter().map(|value| value.to_string()).collect();

    let split = split_train_test(&x, &t, 0.3, 0).unwrap();

    assert_eq!(split.x_train.len(), 7);
    assert_eq!(split.x_test.len(), 3);
}
```

このテストは、先頭 7 件と末尾 3 件に切るだけの**仮実装**で通ります。

```rust
// 仮実装：先頭から 7 件と残り 3 件に切る
pub fn split_train_test<X: Clone, T: Clone>(
    x: &[X],
    t: &[T],
    _test_size: f64,
    _seed: u64,
) -> Result<TrainTestSplit<X, T>> {
    Ok(TrainTestSplit {
        x_train: x[..7].to_vec(),
        x_test: x[7..].to_vec(),
        t_train: t[..7].to_vec(),
        t_test: t[7..].to_vec(),
    })
}
```

**7 という定数が埋め込まれています。** ここから三角測量で追い込みます。

### 三角測量: 件数を一般化する

150 件を 7:3 に分ける実データを考えれば、7 が定数であってはいけないことは分かります。テストを足します。

```rust
#[test]
fn 特徴量と正解ラベルの件数が違えば分けられない() {
    let x: Vec<usize> = vec![1, 2];
    let t: Vec<usize> = vec![1];

    assert_eq!(
        split_train_test(&x, &t, 0.3, 0).unwrap_err().to_string(),
        "件数が違います: 2 と 1"
    );
}
```

件数が 2 件のデータを渡すと、仮実装は `x[..7]` で**パニックします**（範囲外の添字）。パニックはテストの失敗として現れるので、Red が取れます。ここで件数の検査と割合の計算を入れます。

```rust
if x.len() != t.len() {
    return Err(Error::LengthMismatch {
        left: x.len(),
        right: t.len(),
    });
}

#[allow(clippy::cast_precision_loss, clippy::cast_sign_loss)]
let test_count = (shuffled.len() as f64 * test_size).ceil() as usize;
let train_count = shuffled.len() - test_count;
```

`ceil()`（切り上げ）にしているのは Python 版の `train_test_split` に合わせたためです。150 件の 0.3 は 45.0 なので切り上げても 45 件ですが、端数の出る件数では 1 件ずれます。**どちらに寄せるかを決めて、全言語版でそろえる**必要がある部分です。

`as` による変換が 2 回出てきます。Rust は数値の変換を必ず明示させるので、`usize` → `f64` → `usize` と 2 回書くことになります。`#[allow(...)]` の 2 つは clippy の `pedantic` の lint で、`clippy::all` には含まれないため既定では警告になりません。それでも書いてあるのは、**「桁が落ちるかもしれないことを承知している」という宣言**です。件数が 2^53 を超えれば `f64` で表せませんが、150 件のデータでは起こりません。

### 三角測量: 並び順に頼らない分け方にする

件数が正しくても、**先頭から切る**実装のままです。アヤメのデータは種類ごとに並んでいるので、先頭 105 件を訓練データにすると `Iris-virginica` がほとんど訓練データに入りません。並べ替えが要ります。

並べ替えのテストを先に書きます。

```rust
#[test]
fn 同じシードなら同じ並びになる() {
    let items: Vec<usize> = (0..10).collect();

    assert_eq!(shuffle(&items, 0), shuffle(&items, 0));
    assert_ne!(shuffle(&items, 0), shuffle(&items, 1));
    assert_eq!(shuffle(&items, 0).len(), items.len());
}

#[test]
fn 並べ替えても要素は変わらない() {
    let items: Vec<usize> = (0..10).collect();

    let mut shuffled = shuffle(&items, 0);
    shuffled.sort_unstable();

    assert_eq!(shuffled, items);
}
```

このテストは**特定の並びを期待していません**。「同じシードなら再現する」「違うシードなら変わる」「要素は増えも減りもしない」の 3 つだけを言っています。特定の並びを期待するテストは、乱数生成器の実装が変われば落ちます。ここでは**性質を確かめるテスト**にしました。

「違うシードなら変わる」を `assert_ne!` で書いているのは厳密には危うく、たまたま同じ並びになる可能性はあります（10 件の並べ替えなので 1/10! 程度）。実際にシード 0 と 1 で違うことを確かめたうえで、この形にしています。

### Green: シード付きの乱数で並べ替える

```rust
/// シードを使って Fisher-Yates のシャッフルで並べ替える。元のスライスは変えない。
pub fn shuffle<E: Clone>(items: &[E], seed: u64) -> Vec<E> {
    let mut rng = StdRng::seed_from_u64(seed);
    let mut shuffled = items.to_vec();

    for i in (1..shuffled.len()).rev() {
        let j = next_index(&mut rng, i + 1);
        shuffled.swap(i, j);
    }

    shuffled
}

/// 0 以上 bound 未満の整数を返す。再現できる分割のための擬似乱数で、暗号用途ではない。
fn next_index(rng: &mut StdRng, bound: usize) -> usize {
    use rand::Rng;

    rng.gen_range(0..bound)
}
```

Fisher-Yates のシャッフルを自分で書いています。`rand` には `SliceRandom::shuffle` があるのですが、ほかの言語版（Java・Go・C#）と同じアルゴリズムを明示するために手で書きました。**実装が見えていれば、言語間で並びが違う理由を「乱数生成器の違いだけ」に絞り込めます**。

`items: &[E]` を借用で受け取り、`items.to_vec()` で複製してから並べ替えます。呼ぶ側の元データは変わりません。`&mut [E]` を受け取ってその場で並べ替える設計もできますが、元データを持ち続ける呼び出し側にとってはこちらが扱いやすいので、複製する形を選びました。

`fn shuffle<E: Clone>` の `E: Clone` は、**複製できる型だけを受け付ける**という制約です。`to_vec()` が `Clone` を要求するので、書かないとコンパイルが通りません。Go 版のジェネリクスが `[E any]` と書けたのに対し、Rust では**使う操作に必要な制約をすべて書く**ことになります。面倒ですが、関数のシグネチャを読むだけで「何を要求するか」が分かります。

`use rand::Rng;` が関数の中にあるのは、`gen_range` が `Rng` トレイトのメソッドだからです。**トレイトのメソッドを呼ぶには、そのトレイトがスコープに入っている必要があります。** Rust を書き始めて最初に戸惑うところで、「メソッドが無い」というエラーの半分はこれです。ここでは使う場所の直前に置いて、必要な理由を近くに見せました。

### `rand` 0.8 に固定する理由

`Cargo.toml` の `rand = "0.8"` には意味があります。`rand` の最新は 0.9 系ですが、**0.8 に固定しています**。

理由は第 3 章で使う linfa です。linfa 0.8.1 は内部で `rand` 0.8 系（`linfa-clustering` は `rand_xoshiro` 0.6）を使っています。ここに `rand` 0.9 を混ぜると、Cargo は**両方の版を同時にビルドします**。すると `rand 0.8 の Rng` と `rand 0.9 の Rng` という「同じ名前の別のトレイト」ができ、0.9 の乱数生成器を linfa の API に渡そうとすると、こんなエラーになります。

```text
error[E0308]: mismatched types
   = note: two types coming from two different versions of the same crate are different
           types even if they look the same
```

**「同じに見えても別の型」** というのは Rust に固有の論点です。ndarray でも同じことが起きます。linfa 0.8.1 が使うのは ndarray 0.16 で、ndarray の最新は 0.17 です。0.17 の `Array2<f64>` を作って linfa に渡すと、同じエラーが出ます。

| 言語版 | 同じライブラリの版が混ざったとき |
|--------|------------------------------|
| Rust | 両方がビルドされ、**型として別物**になる。コンパイルエラーで止まる |
| Java（Maven） | 依存の解決で 1 つに決まる（nearest-wins）。実行時に `NoSuchMethodError` が出ることがある |
| Go | モジュールごとに 1 つの版に解決される（minimal version selection） |
| Python | 1 つしか入らない。競合はインストール時に表面化する |

Rust のやり方は、**壊れ方がコンパイル時に分かる**点で安全です。実行時に `NoSuchMethodError` が飛んでくる世界と比べれば、`Cargo.toml` の版をそろえる手間のほうが安く付きます。本シリーズの Rust 版は、linfa 0.8 系・ndarray 0.16・rand 0.8・rand_xoshiro 0.6 で統一しています（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。

### 特徴量と正解ラベルの対応を保つ

並べ替えるとき、特徴量とラベルの対応が崩れてはいけません。組にしてから並べ替えます。

```rust
let pairs: Vec<(X, T)> = x.iter().cloned().zip(t.iter().cloned()).collect();
let shuffled = shuffle(&pairs, seed);
```

`(X, T)` のタプルにすれば、`shuffle` は中身を知らずに並べ替えられます。`shuffle<E: Clone>` の `E` に `(X, T)` が入るには `(X, T): Clone` が必要で、これは `X: Clone` と `T: Clone` から自動的に導かれます。関数のシグネチャに `X: Clone, T: Clone` と書いてあるので、そのまま通ります。

分ける部分はこうです。

```rust
for (index, (features, label)) in shuffled.into_iter().enumerate() {
    if index < train_count {
        split.x_train.push(features);
        split.t_train.push(label);
    } else {
        split.x_test.push(features);
        split.t_test.push(label);
    }
}
```

`for (index, (features, label)) in ...` の**入れ子のパターン**で、`enumerate` の `(usize, (X, T))` を 1 行で分解しています。`shuffled.into_iter()` は**所有権ごと**要素を取り出す反復子なので、`features` と `label` をそのまま `push` でき、複製が要りません。ここが `iter()` なら `&(X, T)` が来るので、`clone()` が必要になります。**`iter()` と `into_iter()` の使い分けが、そのまま複製の有無になります**。

### ほかの言語版と分け方が一致しない

テストに残しておきます。

```rust
#[test]
fn 並べ替えの並びはほかの言語版と違う() {
    let items: Vec<usize> = (0..10).collect();

    // Java 版は [4 8 9 6 3 5 2 1 7 0]、Go 版は [6 8 2 3 7 5 9 1 0 4]
    assert_eq!(shuffle(&items, 0), vec![9, 3, 6, 4, 8, 1, 5, 2, 0, 7]);
}
```

このテストは「性質」ではなく「特定の並び」を期待しています。先ほどの方針とは逆ですが、**意図が違います**。これは実装の仕様を固定するテストではなく、**「言語をまたぐと値が変わる」という事実の記録**です。`rand` を 0.9 に上げればこのテストは落ち、そのとき「なぜ落ちたか」をこのコメントが教えてくれます。実データのテスト（`tests/iris_data.rs`）に置いています。

## 2.9 前処理をまとめる

ここまでの部品を 1 つの関数にまとめます。

```rust
/// iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。
pub fn prepare_iris(
    csv_file: &std::path::Path,
    test_size: f64,
    seed: u64,
) -> Result<TrainTestSplit<Features, String>> {
    let table = Table::load(csv_file)?;
    let (columns, rows, labels) = split_features_and_target(&table, TARGET)?;
    let split = split_train_test(&rows, &labels, test_size, seed)?;
    let means = column_means(&split.x_train, &columns)?;

    Ok(TrainTestSplit {
        x_train: fill_missing(&split.x_train, &columns, &means)?,
        x_test: fill_missing(&split.x_test, &columns, &means)?,
        t_train: split.t_train,
        t_test: split.t_test,
    })
}
```

**順序が大事です。** 「分割してから補完する」のであって、逆ではありません。

平均値を求めるのは `split.x_train`、つまり**訓練データだけ**です。テストデータの補完にも訓練データの平均値を使います。全件の平均値で先に補完してしまうと、テストデータの情報が訓練データの前処理に混ざります。これを**データ漏洩**（data leakage）と呼びます。テストデータは「まだ見たことのないデータ」のつもりで評価するものなので、その中身が学習側に漏れると、評価が実力より良く出ます。

この順序が型に現れているのが面白いところです。`split_train_test` は `TrainTestSplit<Row, String>` を返し、`prepare_iris` は `TrainTestSplit<Features, String>` を返します。**`Row`（欠損値を持ちうる）から `Features`（持てない）への変換が、分割の後にある**ことが、戻り値の型から読み取れます。

戻り値を組み立てるとき、`t_train` と `t_test` は `split` から**そのまま移して**います。`split.t_train` と書くと `split` の中のベクタの所有権が移るので、複製は起きません。一方 `x_train` は `fill_missing(&split.x_train, ...)` と借用で渡しています。部分的に移動した `split` はもう全体としては使えませんが、この関数の最後なので問題ありません。**構造体のフィールドごとに所有権が動く**のは、Rust のコードを読むときの手がかりになります。

引数の `csv_file: &std::path::Path` はファイルの場所を借用で受け取ります。`PathBuf`（所有する）ではなく `&Path`（借りる）を取るのが、Rust で道を受け取るときの定石です。第 1 章の `load_people` も同じ形でした。

## 2.10 実データで前処理の結果を表示する

### 実データのテスト

実データを使うテストは `tests/iris_data.rs` に置きます。第 1 章と同じく、データが無ければ早期に戻ります。

```rust
/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
fn data_file() -> Option<PathBuf> {
    let csv_file = dataset::current().join("iris.csv");

    if csv_file.exists() {
        Some(csv_file)
    } else {
        eprintln!("学習データ iris.csv が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}

#[test]
fn 実データを百五件と四十五件に分けて欠損値を補完する() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let split = prepare_iris(&csv_file, 0.3, 0).expect("前処理できること");

    assert_eq!(split.x_train.len(), 105);
    assert_eq!(split.x_test.len(), 45);
    assert_eq!(split.t_train.len(), 105);
    assert_eq!(split.t_test.len(), 45);
}
```

件数の 105 と 45 は、**ほかの言語版と一致します**。分け方（どの行がどちらに入るか）は違いますが、150 件を 7:3 に切り上げで分ければ件数は同じだからです。

欠損値の数も一致します。これはデータそのものの性質なので、乱数とは無関係です。

```rust
#[test]
fn 実データの列ごとの欠損値の数を数える() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let table = Table::load(&csv_file).expect("CSV を読めること");

    assert_eq!(table.rows.len(), 150);
    assert_eq!(
        table.count_missing().expect("欠損値を数えられること"),
        vec![
            Missing { column: "がく片長さ".to_string(), count: 2 },
            Missing { column: "がく片幅".to_string(), count: 1 },
            Missing { column: "花弁長さ".to_string(), count: 2 },
            Missing { column: "花弁幅".to_string(), count: 2 },
            Missing { column: "種類".to_string(), count: 0 },
        ]
    );
}
```

平均値のほうは一致しません。訓練データに入る行が違うからです。ここも記録として残します。

```rust
#[test]
fn 訓練データの平均値は乱数の分け方で決まる() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let table = Table::load(&csv_file).expect("CSV を読めること");
    let (columns, rows, labels) =
        split_features_and_target(&table, TARGET).expect("列を分けられること");
    let split = split_train_test(&rows, &labels, 0.3, 0).expect("分割できること");
    let means = column_means(&split.x_train, &columns).expect("平均値を求められること");

    // rand の StdRng は Java の java.util.Random とも Go の math/rand とも乱数列が違うので、
    // 分かれる行と平均値はほかの言語版と一致しない（件数だけ一致する）
    for (column, want) in [
        ("がく片長さ", 0.406_285_714_285_714_36),
        ("がく片幅", 0.441_346_153_846_153_94),
        ("花弁長さ", 0.467_184_466_019_417_54),
        ("花弁幅", 0.415_384_615_384_615_3),
    ] {
        assert!(
            (means[column] - want).abs() < 1e-12,
            "{column} の平均値 = {}, want {want}",
            means[column]
        );
    }
}
```

3 つ、Rust らしい書き方が出ています。

1. **数値リテラルの `_` 区切り**。`0.406_285_714_285_714_36` は `0.40628571428571436` と同じです。clippy の `unreadable_literal` が長い数値リテラルに区切りを促します
2. **許容誤差付きの比較**。`assert_eq!` ではなく `(a - b).abs() < 1e-12` で比べています。平均値は割り算の結果なので、丸め誤差が入りえます。第 1 章の正解率 1.0・0.5 は厳密に表せる値だったので `assert_eq!` で済みました
3. **`assert!` の第 3 引数以降のメッセージ**。配列を回すテストでは、落ちたときにどの列かが分からないと困ります。`"{column} の平均値 = {}, want {want}"` のように、フォーマット文字列をそのまま書けます

`expect("CSV を読めること")` は、`unwrap()` にメッセージを付けたものです。**テストの中では失敗したらパニックしてよい**ので、「何が成り立つはずだったか」を日本語で書いておくと、落ちたときのログが読みやすくなります。

`let Some(csv_file) = data_file() else { return; };` の `let ... else` は第 1 章と同じです。Rust の標準のテストには「スキップ」が無いので、理由を標準エラーに出して早く戻ります。

### 結果を表示する

```rust
/// テストデータの割合。
const TEST_SIZE: f64 = 0.3;
/// 分割の乱数のシード。
const SEED: u64 = 0;

/// アヤメのデータの前処理の結果を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let csv_file = dataset::current().join("iris.csv");
    let table = Table::load(&csv_file)?;
    let counts = table.count_missing()?;
    let split = prepare_iris(&csv_file, TEST_SIZE, SEED)?;

    let formatted: Vec<String> = counts
        .iter()
        .map(|missing| format!("{}={}", missing.column, missing.count))
        .collect();

    writeln!(out, "データ件数: {}", table.rows.len())?;
    writeln!(out, "欠損値の数: {}", formatted.join(", "))?;
    writeln!(
        out,
        "訓練データ: {} 件, テストデータ: {} 件",
        split.x_train.len(),
        split.x_test.len()
    )?;
    writeln!(out, "特徴量: {}", split.x_train[0].columns.join(", "))?;

    Ok(())
}
```

第 1 章と同じく `out: &mut impl Write` で書き出し先を差し替えられるようにしています。`writeln!` は失敗しうるので `?` が付きます。**標準出力への書き出しが失敗する（パイプが閉じているなど）ことを型で表している**のは、Go の `fmt.Fprintln` が `error` を返すのと同じ思想です。Java の `System.out.println` は失敗を返しません。

`src/bin/chapters.rs` に章を足します。

```rust
let result = match args.get(1).map(String::as_str) {
    Some("chapter01") => chapter01::run(&mut out).map_err(|e| e.to_string()),
    Some("chapter02") => chapter02::run(&mut out).map_err(|e| e.to_string()),
    // ...
};
```

`.map_err(|e| e.to_string())` が増えました。第 1 章と第 2 章は**別のエラー型**（`chapter01::Error` と `chapter02::Error`）なので、`match` の腕の型をそろえる必要があります。どちらも `Display` を実装しているので、`String` にそろえました。`Box<dyn std::error::Error>` にそろえる手もありますが、ここでは表示するだけなので `String` で足ります。**章ごとにエラー型を分けたことの代償が、この 1 行**です。

実行します。

```text
$ cargo run --bin chapters -- chapter02
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
```

データ件数 150、欠損値 2・1・2・2・0、訓練 105 件・テスト 45 件は、**ほかの言語版と一致します**。一致しないのは、この出力には現れていない「どの行がどちらに入ったか」と、そこから決まる平均値です。

## 2.11 リファクタリング

### テストの重複をまとめる

テストの中で `Row` を作る式が何度も出てきたので、ヘルパーにまとめました。

```rust
/// 列名と値から行を作る。
fn row(pairs: &[(&str, &str)]) -> Row {
    Row::new(
        pairs
            .iter()
            .map(|(name, value)| (name.to_string(), value.to_string()))
            .collect(),
    )
}

/// 文字列のスライスを列名のベクタにする。
fn columns(names: &[&str]) -> Vec<String> {
    names.iter().map(|name| name.to_string()).collect()
}
```

`&[(&str, &str)]` を受け取る形にしたので、呼ぶ側は `row(&[("がく片長さ", "5.1")])` と短く書けます。**テストの読みやすさのためにテスト用のコードを書く**のは、本番のコードと同じだけ価値があります。テストが読みにくいと、落ちたときに直せません。

`table.rs` と `preprocessing.rs` の両方に `row` を置いているのは、`#[cfg(test)] mod tests` がファイルごとに独立しているからです。片方を `pub(super)` にして共有することもできますが、**テストのヘルパーを共有すると、片方の都合でもう片方が壊れます**。数行の重複を受け入れました。

### 整形と静的解析

```text
$ cargo fmt --check
$ cargo clippy --all-targets -- -D warnings
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 1m 47s
```

どちらも何も言わなければ合格です。`Cargo.toml` で `clippy::all` を `deny` にしているので、`needless_range_loop`（添字でしか使わないループ変数）のような指摘は警告ではなく**エラー**になります。この章の実装にはそもそも添字のループが 1 つもなく、`for row in rows`・`for column in columns` のように要素を直接回しています。

`cargo clippy --all-targets` の `--all-targets` は、`tests/` のコードや `#[cfg(test)]` のテストも検査対象にするという意味です。本体だけ綺麗でテストが汚い、という状態を避けられます。

数値リテラルの `_` 区切り（`0.406_285_714_285_714_36`）は、`clippy::pedantic` の `unreadable_literal` に対応する書き方です。`clippy::all` には含まれないので既定では指摘されませんが、読みやすさのために入れています。`#[allow(clippy::cast_precision_loss)]` も同じ立場で、**pedantic を有効にしたときに何を承知していたかが残る**ように書いてあります。

### カバレッジ

```text
$ cargo llvm-cov --summary-only
```

第 1 章と同じく、学習データの有無でカバレッジが変わります。実データのテストは「スキップ」ではなく「早期に戻る」形なので、成功として数えられます。**本当に走ったかはカバレッジの差で確かめる**、という第 1 章の方針をこの章でも続けています。

## 2.12 可視化について

Python 版・Kotlin 版では、この章でヒストグラムや散布図を描いてデータの分布を確かめました。Rust にも plotters という描画クレートがありますが、本シリーズの Rust 版では**可視化の節を作りません**。

理由は 2 つあります。1 つは、可視化の目的が「人がデータを見て理解する」ことなので、対話的に試せる環境（Jupyter Notebook）のほうが向いていること。もう 1 つは、Rust 版で伝えたいこと（所有権・`Result`・`enum`・クレートの版）と可視化が交わらないことです。

欠損値の分布や、品種ごとの特徴量の散布図は、[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と [Kotlin 版の第 2 章](../kotlin/02-data-preprocessing-and-triangulation.md) の「Notebook で探索する」の節を参照してください。訓練データに入る行は言語ごとに違いますが、「`Iris-setosa` は花弁長さ・花弁幅がほかの 2 品種よりはっきり小さい」という傾向は同じで、第 3 章の決定木はこの傾向を自動で見つけます。

## 2.13 まとめ

この章では、アヤメのデータを読み込み、欠損値を平均値で補完し、訓練データ 105 件とテストデータ 45 件に分けました。Rust に固有の論点は次のとおりです。

1. **欠損値は `Option`、失敗は `Result`** — `Result<Option<f64>>` の 1 つの型で「読めた／空欄だった／読めなかった」の 3 つを表す。Go 版の `(float64, bool, error)` が縦に並び替わった形。平均値を求める側は `if let Some(value) = row.number(column)?` の 1 行で欠損値を飛ばせる
2. **`#[derive(PartialEq)]` だけでベクタどうしを比べられる** — Go 版の `reflect.DeepEqual` は要らない。しかも比較のコードが型ごとにコンパイル時に生成されるので、型の取り違えはコンパイルエラーになる。`f64` を含む `Features` に `Eq` を付けられないことまで型が教えてくれる
3. **ジェネリクスの `TrainTestSplit<X, T>`** — `#[derive]` が型引数の制約を自動で足すので、`X` が複製できない型でも構造体自体は作れる
4. **クレートの版が型を分ける** — `rand` を 0.8 に固定するのは linfa 0.8 に合わせるため。混ぜると「同じに見えても別の型」になり、コンパイルで止まる。Java の `NoSuchMethodError` が実行時に飛ぶのと比べれば、止まってくれるほうが安い
5. **借用が契約になる** — `fill_missing(rows: &[Row], ...)` は「行を書き換えない」ことをシグネチャで保証する。`iter()` と `into_iter()` の選択がそのまま複製の有無になる
6. **乱数は言語をまたいで一致しない** — シードを固定しても、`StdRng`・`java.util.Random`・`math/rand` は別の数列を出す。一致するのは件数だけ。この事実をテストに書いて残した

**TODO リスト（この章の完了時点）**:

- [x] CSV を表として読み込む
- [x] 列ごとに欠損値を数える
- [x] 平均値で欠損値を補完する
- [x] 特徴量と正解ラベルに分ける
- [x] 訓練データとテストデータに分ける
- [x] 実データで前処理の結果を表示する

次の章では、この前処理の結果を使って決定木を実装します。`enum` と `Box` で木を表し、`match` の網羅性をコンパイラに検査させます。そして**自作の決定木を linfa の決定木と突き合わせ**、浅い木では一致し、深い木では分かれることを確かめます。
