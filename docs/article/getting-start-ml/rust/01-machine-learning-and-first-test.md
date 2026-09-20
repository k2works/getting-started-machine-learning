---
type: Article
title: "第 1 章: 機械学習とはじめてのテスト"
description: "機械学習とルールベースの違いを確認し、きのこ派・たけのこ派の判定を Rust の TDD で実装して正解率を測る。所有権・Result・enum によるエラーの表し方を Java 版・Go 版と対比する。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T13:30:00Z }
---

# 第 1 章: 機械学習とはじめてのテスト

## 1.1 はじめに

この章では、機械学習とは何かを確認したうえで、テスト駆動開発（TDD）で最初のプログラムを Rust で作ります。題材は「きのこの山派か、たけのこの里派か」を身長・体重・年代から判定する問題です。

この章ではまだ機械学習のアルゴリズムを使いません。人間が考えたルールで判定し、その正解率を測るところまで進みます。「ルールを人間が書く」とはどういうことかを先に体験しておくと、第 3 章で「ルールをデータから学ばせる」ことの意味がはっきりします。

[Python 版の第 1 章](../python/01-machine-learning-and-first-test.md) と同じ題材・同じ TODO リストで進めます。Rust 版では次の 3 つと対比します。1 つは [Java 版](../java/01-machine-learning-and-first-test.md) と [C# 版](../csharp/01-machine-learning-and-first-test.md) で、機械学習ライブラリ（Tribuo・ML.NET）が揃った静的型付け言語という立場が同じです。Rust にも linfa という揃ったライブラリがあり、第 3 章から本格的に使います（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。2 つめは [Go 版](../go/01-machine-learning-and-first-test.md) で、例外を持たない言語どうし、失敗をどう表すかを比べられます。3 つめは [F# 版](../fsharp/01-machine-learning-and-first-test.md) で、`Result` を型で扱う流儀が近いところです。

## 1.2 機械学習とは

### ルールを書くか、データに学ばせるか

従来のプログラミングでは、人間が判定のルールをコードとして書きます。

```rust
pub fn predict_by_rule(features: Features) -> &'static str {
    if features.age_group == KINOKO_AGE_GROUP {
        KINOKO
    } else {
        TAKENOKO
    }
}
```

この書き方では、ルールの良し悪しは人間の観察力に依存します。特徴量が 3 つなら何とかなりますが、20 個・100 個になると人間には手に負えません。

機械学習は、この「ルール」をデータから自動で作ります。人間が与えるのは「入力（特徴量）」と「正解（ラベル）」の組で、ルールそのものはアルゴリズムが決めます。第 3 章で決定木を実装すると、「年代が 20 ならきのこ」に相当する分岐が、データから自動で決まる様子を見られます。

| | 従来のプログラミング | 機械学習 |
|---|---|---|
| 人間が書くもの | ルール | データと、学習のさせ方 |
| 出力 | 判定結果 | ルール（モデル）と、それを使った判定結果 |
| 得意なこと | 仕様がはっきりしている問題 | 仕様を言葉にしにくい問題 |
| 説明のしやすさ | コードを読めば分かる | モデルによる（決定木は読める、ニューラルネットは難しい） |

### 機械学習のワークフロー

本シリーズを通して、次の流れを繰り返します。

1. **データを集める・読み込む** — この章で CSV を読み込みます
2. **前処理する** — 欠損値を埋め、訓練データとテストデータに分けます（第 2 章）
3. **モデルを学習させる** — アルゴリズムにルールを作らせます（第 3 章以降）
4. **評価する** — 正解率などの指標で測ります（この章で正解率を実装します）
5. **改善する** — 特徴量を足す、別のモデルを試す、正則化する（第 9〜12 章）

この章では 1・4 を、いちばん単純な形で通します。**端から端まで通す**のが目的で、精度は求めません。

### 分類と回帰

教師あり学習は、予測するものの型で 2 つに分かれます。

| 種類 | 予測するもの | 例 | 本シリーズで扱う章 |
|------|------------|-----|-----------------|
| 分類 | どのグループに属するか（離散値） | きのこ派／たけのこ派、アヤメの種類、生存／死亡 | 第 1・3・8・10・11 章 |
| 回帰 | 数値（連続値） | 映画の興行収入、住宅価格 | 第 7・9・12 章 |

この章は分類です。正解ラベルが「きのこ」「たけのこ」の 2 つなので、二値分類にあたります。

## 1.3 題材とデータ

### データの入手と配置

本シリーズの学習データは、書籍『スッキリわかる Python による機械学習入門』（インプレス, 2020）の配布データを使います。配布データは書籍購入者のみ利用できるため、リポジトリには含まれていません。[書籍サポートページ](https://sukkiri.jp/books/sukkiri_ml) から `sukkiri-ml-codes.zip` を入手し、リポジトリの `tmp/` に置いてから、リポジトリのルートで次を実行してください。

```bash
npx gulp data:setup
npx gulp data:check
```

`apps/data/sukkiri-ml/` に学習データが配置されます。このディレクトリは `.gitignore` の対象なので、誤ってコミットされることはありません。すべての言語版が同じデータを参照します。

### KvsT.csv

この章で使うのは `KvsT.csv` です。19 人分のデータが次の 4 列で記録されています。

| 列 | 意味 | 値 |
|----|------|-----|
| 身長 | 身長（cm） | 整数 |
| 体重 | 体重（kg） | 整数 |
| 年代 | 年代 | 10, 20, 30, 40 のいずれか |
| 派閥 | きのこの山派かたけのこの里派か | `きのこ` または `たけのこ` |

「身長」「体重」「年代」が特徴量、「派閥」が正解ラベルです。列名が日本語であることと、ファイルの先頭に BOM（バイトオーダーマーク）が付いていることに注意してください。**BOM については、Rust 版だけ事情が違います。** 後で確かめます。

## 1.4 TODO リストの作成

仕様をそのままコードにするには大きすぎるので、まず TODO リストに分解します。

> TODO リスト
>
> 何をテストすべきだろうか——着手する前に、必要になりそうなテストをリストに書き出しておこう。
>
> — テスト駆動開発

**TODO リスト**:

- [ ] CSV を読み込む
  - [ ] BOM 付き CSV の 1 行を人物として読み込む
  - [ ] 複数行を行の順に読み込む
- [ ] 特徴量と正解ラベルに分ける
- [ ] ルールで派閥を判定する
  - [ ] 20 代ならきのこ派と判定する
  - [ ] 20 代以外ならたけのこ派と判定する
- [ ] 正解率を計算する
- [ ] 実データで正解率を表示する

## 1.5 開発環境の準備

### プロジェクトの構成

実装は `apps/rust/` に置きます。Cargo のパッケージが 1 つ、その中に章ごとのモジュールを並べる構成です。

```text
apps/rust/
├── Cargo.toml
├── Cargo.lock
├── src/
│   ├── lib.rs          # モジュールの一覧
│   ├── dataset.rs      # 学習データの場所
│   ├── chapter01.rs    # 第 1 章
│   └── bin/
│       └── chapters.rs # 章を選んで実行するコマンド
└── tests/
    └── kvst_data.rs    # 実データを使うテスト
```

`Cargo.toml` は次のとおりです。

```toml
[package]
name = "getting-started-ml"
version = "0.1.0"
edition = "2024"
publish = false

[dependencies]
csv = "1.4"

[lints.clippy]
# 章ごとの実装は読みやすさを優先する。警告は抑えずに直すのが原則
all = { level = "deny", priority = -1 }
```

`publish = false` は、このパッケージを crates.io に公開しないという宣言です。書き忘れても実害はありませんが、意図を書いておくと `cargo publish` の事故を防げます。

`[lints.clippy]` で clippy の警告をエラー扱いにしています。Go 版が `golangci-lint` の設定ファイルで同じことをしたのと同じ考え方で、**警告を残したまま先へ進めない**ようにします。

Rust の開発環境は `nix develop .#rust` に入ると揃います。

```text
$ nix develop .#rust
Rust development environment activated
  - rustc: rustc 1.91.1 (ed61e7d7e 2025-11-07) (built from a source tarball)
  - cargo: cargo 1.91.0 (ea2d97820 2025-10-10)
  - cargo-llvm-cov: cargo-llvm-cov 0.6.20
```

edition は 2024 です。edition は言語の「版」で、`match` の書き方や `unsafe` の扱いなど、後方互換を壊す変更がここで区切られます。Java の言語レベル、C# の `LangVersion` に近い考え方ですが、**クレートごとに別の edition を選べて、混ぜて使える**点が違います。

### 環境確認テスト

いちばん最初に書くのは、環境が動くことを確かめるテストです。学習データの置き場を返す関数から始めます。

```rust
//! 学習データのディレクトリを求める。

use std::path::{Path, PathBuf};

/// 環境変数の名前。実データの置き場をテストや CI から差し替えるために使う。
pub const DATA_DIR_ENV: &str = "ML_DATA_DIR";

/// 学習データのディレクトリを返す。`ML_DATA_DIR` が無ければ `../data/sukkiri-ml` を使う。
/// 環境変数の読み出しは、テストで差し替えられるように引数で受け取る。
pub fn from(getenv: impl Fn(&str) -> Option<String>) -> PathBuf {
    match getenv(DATA_DIR_ENV) {
        Some(value) if !value.is_empty() => PathBuf::from(value),
        _ => Path::new("..").join("data").join("sukkiri-ml"),
    }
}

/// 実行中のプロセスの環境変数から学習データのディレクトリを返す。
pub fn current() -> PathBuf {
    from(|name| std::env::var(name).ok())
}
```

`match` の腕に `Some(value) if !value.is_empty()` と書けるのがガード付きのパターンマッチで、「値があって、かつ空文字列でない」を 1 行で表せます。Go 版では `if value, ok := getenv(...); ok && value != ""` と書いた部分です。

テストは**同じファイルの中**に書きます。

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 環境変数があればその値を使う() {
        let dir = from(|_| Some("/tmp/data".to_string()));

        assert_eq!(dir, PathBuf::from("/tmp/data"));
    }

    #[test]
    fn 環境変数が無ければ既定の場所を使う() {
        let dir = from(|_| None);

        assert_eq!(dir, Path::new("..").join("data").join("sukkiri-ml"));
    }

    #[test]
    fn 環境変数が空文字列なら既定の場所を使う() {
        let dir = from(|_| Some(String::new()));

        assert_eq!(dir, Path::new("..").join("data").join("sukkiri-ml"));
    }
}
```

Rust らしい点が 3 つあります。

1. **テストが実装と同じファイルにある。** `#[cfg(test)]` が付いたモジュールはテストのときだけコンパイルされ、リリースビルドには含まれません。Java・C#・Go がテストを別ファイル（別ディレクトリ）に置くのとは逆の流儀です。テスト対象がすぐ上にあるので、**非公開の関数もそのままテストできます**
2. **`use super::*;`** で親モジュール（つまり実装）のものを全部取り込みます。テストモジュールのお約束の 1 行です
3. **関数名に日本語が使える。** Rust は 1.53 から非 ASCII の識別子を受け付けます。Go 版はテスト名を `t.Run("...")` の文字列で日本語にしましたが、Rust は関数名そのものを日本語にできます

実行します。

```text
$ cargo test
test dataset::tests::環境変数があればその値を使う ... ok
test dataset::tests::環境変数が無ければ既定の場所を使う ... ok
test dataset::tests::環境変数が空文字列なら既定の場所を使う ... ok

test result: ok. 3 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s
```

## 1.6 ルールで派閥を判定する

TODO リストは CSV の読み込みから始まっていますが、**いちばん中心にある「判定」から**着手します。CSV の読み込みは外側の関心事で、判定の仕様とは独立だからです。

### Red: まだ無いものを呼ぶ

テストを先に書きます。

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 二十代はきのこ派と判定する() {
        assert_eq!(
            predict_by_rule(Features { height: 170, weight: 60, age_group: 20 }),
            "きのこ"
        );
    }
}
```

`predict_by_rule` も `Features` もまだありません。

```text
$ cargo test
error[E0422]: cannot find struct, variant or union type `Features` in this scope
 --> src/chapter01.rs:9:36
  |
9 |         assert_eq!(predict_by_rule(Features { height: 170, ... }), "きのこ");
  |                                    ^^^^^^^^ not found in this scope

error[E0425]: cannot find function `predict_by_rule` in this scope
 --> src/chapter01.rs:9:20
  |
9 |         assert_eq!(predict_by_rule(Features { height: 170, ... }), "きのこ");
  |                    ^^^^^^^^^^^^^^^ not found in this scope
```

Go 版・Java 版と同じく、**静的型付け言語の Red はコンパイルエラー**です。Python 版のように実行時エラーで失敗するのではなく、テストが走る前に止まります。

### Green: 仮実装

いちばん単純に、定数を返します。

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Features {
    pub height: i32,
    pub weight: i32,
    pub age_group: i32,
}

/// 仮実装：まず定数を返す
pub fn predict_by_rule(_features: Features) -> &'static str {
    "きのこ"
}
```

引数の名前を `_features` にしているのは、**使っていない引数に警告を出さない**ための書き方です。`features` のままだと clippy が「unused variable」と言い、`[lints.clippy]` の設定でエラーになります。Go 版で仮実装の定数が `unused` に怒られたのと同じ場面です。

`#[derive(...)]` は、決まった振る舞いを自動で実装させる指示です。

| derive | 何ができるようになるか | ほかの言語版だと |
|--------|--------------------|----------------|
| `Debug` | `{:?}` で表示できる（テストの失敗メッセージに使われる） | Java の `toString`、Go の `%+v` |
| `Clone` | 明示的に複製できる | Java のコピーコンストラクタ |
| `Copy` | 代入や引数渡しで自動的に複製される | C# の `struct` |
| `PartialEq, Eq` | `==` で比べられる | Java の `equals`、Go の構造体の `==` |

`Copy` を付けたので、`predict_by_rule(features)` と書いても `features` は呼び出し側に残ります。付けないと**所有権が関数に移って**、呼び出し側で使えなくなります。フィールドが整数 3 つだけの小さな型なので、複製の費用より使いやすさを取りました。

### 三角測量

仮実装を本実装に進めるために、もう 1 つテストを足します。

```rust
#[test]
fn 三十代はたけのこ派と判定する() {
    assert_eq!(predict_by_rule(features(160, 50, 30)), "たけのこ");
}
```

```text
$ cargo test
test chapter01::tests::三十代はたけのこ派と判定する ... FAILED

failures:

---- chapter01::tests::三十代はたけのこ派と判定する stdout ----

thread 'chapter01::tests::三十代はたけのこ派と判定する' panicked at src/chapter01.rs:30:9:
assertion `left == right` failed
  left: "きのこ"
 right: "たけのこ"
note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace

test result: FAILED. 4 passed; 1 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s
```

`left` が実際の値、`right` が期待値です。ファイル名と行番号も出ます。この失敗を受けて、本実装に進みます。

```rust
/// 「20 代ならきのこ派」というルールの年代。
const KINOKO_AGE_GROUP: i32 = 20;

/// きのこ派の呼び名。
pub const KINOKO: &str = "きのこ";
/// たけのこ派の呼び名。
pub const TAKENOKO: &str = "たけのこ";

/// 人間が決めたルールで派閥を判定する。
pub fn predict_by_rule(features: Features) -> &'static str {
    if features.age_group == KINOKO_AGE_GROUP {
        KINOKO
    } else {
        TAKENOKO
    }
}
```

戻り値の型 `&'static str` は「プログラムが終わるまで生きている文字列への参照」です。`KINOKO` も `TAKENOKO` もコンパイル時に決まる定数なので、**新しく文字列を作らずに参照を返せます**。`String` を返す設計にすると、呼ぶたびに確保と複製が起きます。Java の `String` リテラルがインターン（共有）されるのと似た話ですが、Rust では「所有する `String`」と「借りている `&str`」が**型として別**である点が違います。

テストのヘルパーも足しておきます。

```rust
/// テスト用の特徴量を作る。
fn features(height: i32, weight: i32, age_group: i32) -> Features {
    Features {
        height,
        weight,
        age_group,
    }
}
```

`Features { height, weight, age_group }` は、変数名とフィールド名が同じときに `height: height` を省ける書き方です（フィールドの省略記法）。

年代が 20 以外のすべてでたけのこ派になることも確かめます。

```rust
#[test]
fn 二十代以外はたけのこ派と判定する() {
    for age_group in [10, 30, 40, 50] {
        assert_eq!(predict_by_rule(features(170, 60, age_group)), TAKENOKO);
    }
}
```

配列をそのまま `for` で回せます。Go 版の表駆動テスト、Java 版の `@ParameterizedTest` にあたる部分を、Rust では配列のループで書きました。件数が増えて「どのケースで落ちたか」を知りたくなったら、`assert_eq!` の第 3 引数にメッセージを足します。

## 1.7 失敗を型で表す

CSV の読み込みに進む前に、**失敗をどう表すか**を決めます。Rust に例外はありません。失敗しうる関数は `Result<T, E>` を返します。

```rust
/// 第 1 章で起こりうる失敗。Rust には例外が無いので、失敗は型で表して `Result` で返す。
#[derive(Debug)]
pub enum Error {
    /// CSV を開けない・読めない。
    Io(std::io::Error),
    /// CSV の形が想定と違う。
    Csv(csv::Error),
    /// 列が無い。
    MissingColumn(String),
    /// 数値として読めない。
    NotANumber { column: String, value: String },
    /// 予測と正解ラベルの件数が違う。
    LengthMismatch { predictions: usize, labels: usize },
}
```

`enum` が Rust の判別共用体です。各バリアントが値を持てます。`Io(std::io::Error)` はタプル型、`NotANumber { column, value }` は構造体型のバリアントです。

ここが言語ごとにいちばん分かれるところなので、並べておきます。

| 言語版 | 失敗の表し方 | 網羅性の検査 |
|--------|------------|------------|
| Rust | `Result<T, Error>` と `enum Error` | `match` の網羅性をコンパイラが検査する |
| F#・Scala | `Result`・`Either` と判別共用体 | 同じく検査する |
| Go | `(値, error)` の多値返却と番兵のエラー | されない（`errors.Is` で実行時に判別） |
| Java | 検査例外 `throws` | 宣言はあるが、種類の網羅は検査されない |
| C#・Kotlin・Python・TypeScript | 例外 | されない |

`Display` を実装して、人が読むメッセージを決めます。

```rust
impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Io(error) => write!(f, "CSV を読めません: {error}"),
            Error::Csv(error) => write!(f, "CSV を読めません: {error}"),
            Error::MissingColumn(column) => write!(f, "列がありません: {column}"),
            Error::NotANumber { column, value } => {
                write!(f, "{column} を数値として読めません: {value}")
            }
            Error::LengthMismatch {
                predictions,
                labels,
            } => write!(
                f,
                "予測と正解ラベルの件数が違います: {predictions} と {labels}"
            ),
        }
    }
}

impl std::error::Error for Error {}
```

バリアントを 1 つ足して `Display` の `match` に書き忘れると、**コンパイルが通りません**。Go 版では型スイッチの既定の分岐でエラーを返して実行時に備えましたが、Rust ではコンパイラが網羅性を保証します。

`{error}` や `{column}` のように、フォーマット文字列の中に変数名を直接書けます（Rust 2021 以降）。

さらに 2 つ、変換を実装します。

```rust
impl From<std::io::Error> for Error {
    fn from(error: std::io::Error) -> Self {
        Error::Io(error)
    }
}

impl From<csv::Error> for Error {
    fn from(error: csv::Error) -> Self {
        Error::Csv(error)
    }
}
```

これが効くのは `?` 演算子です。`?` は「失敗なら、`From` で自分のエラー型に変換して、すぐ返す」という意味です。`From` を実装しておくと、標準ライブラリや csv クレートのエラーを自分の `Error` に自動で持ち上げられます。

最後に、この章専用の別名を決めます。

```rust
/// この章の結果の型。`?` 演算子で失敗を上へ返せる。
pub type Result<T> = std::result::Result<T, Error>;
```

`Result<Vec<Person>>` と書けば `std::result::Result<Vec<Person>, Error>` の意味になります。エラー型が 1 つに決まっているモジュールでよく使う書き方です。

## 1.8 CSV を読み込む

### csv クレートが BOM を取り除く

ここで、本シリーズを通しての「BOM の落とし穴」に決着が付きます。

Python 版・Kotlin 版・Java 版・C# 版・Go 版では、BOM 付きの CSV を素直に読むと、先頭の列名が `"﻿身長"` になり、「身長」で引けませんでした。言語によって `encoding='utf-8-sig'`・`UTF8Encoding`・自前の `TrimPrefix` で対処してきた部分です。

Rust の csv クレートは**自分で BOM を取り除きます**。確かめました。

```rust
#[test]
fn bomつきのcsvの列名() {
    // 先頭に BOM を置いた CSV を書き出す
    let mut f = std::fs::File::create(&file).unwrap();
    f.write_all("\u{feff}身長,体重,年代,派閥\n170,60,20,きのこ\n".as_bytes())
        .unwrap();

    let headers = headers_of(&file);

    println!("列名 = {headers:?}");
    println!("先頭の列名のバイト列 = {:?}", headers[0].as_bytes());
    assert_eq!(headers[0], "身長");
}
```

```text
列名 = ["身長", "体重", "年代", "派閥"]
先頭の列名のバイト列 = [232, 186, 171, 233, 149, 183]
test chapter01::tests::bomつきのcsvの列名 ... ok
```

バイト列に BOM（`239, 187, 191`）が含まれていません。**Rust 版だけ、BOM の除去を書かずに済みます。**

これは「Rust が偉い」のではなく、**csv クレートが気を利かせている**だけです。標準ライブラリの `std::fs::read_to_string` で読めば BOM はそのまま残ります。どの層が面倒を見てくれるかはライブラリごとに違う、という教訓のほうが持ち帰る価値があります。他人のライブラリの親切に頼るときは、**テストで確かめてから頼る**のが安全です（この節のテストがまさにそれです）。

### 読み込みの実装

```rust
/// 学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Person {
    pub height: i32,
    pub weight: i32,
    pub age_group: i32,
    pub faction: String,
}

/// BOM 付きの UTF-8 の CSV を読み込み、列名で値を取り出して人物のリストにする。
/// csv クレートは BOM を自分で取り除くので、ほかの言語版のような前処理は要らない。
pub fn load_people(csv_file: &Path) -> Result<Vec<Person>> {
    let mut reader = csv::Reader::from_path(csv_file)?;
    let headers = reader.headers()?.clone();

    let mut people = Vec::new();

    for record in reader.records() {
        people.push(to_person(&headers, &record?)?);
    }

    Ok(people)
}
```

`Person` には `Copy` を付けていません。`String` を持っているからです。`String` はヒープにデータを持つので、暗黙の複製（`Copy`）を許すと、見えないところで確保と複製が起きてしまいます。**`Copy` が付けられるかどうかで、その型が「値そのもの」か「所有するもの」かが分かる**のは、Rust を読むうえでの手がかりになります。

`reader.headers()?.clone()` の `clone()` には理由があります。`headers()` が返すのは reader が持っているヘッダーへの**参照**で、参照を持ったままでは `reader.records()` を呼べません（可変の借用と不変の借用は同時に持てない）。複製すれば reader から切り離せます。ここは借用のルールが設計に直接効く場面です。

行を `Person` にする部分はこうなります。

```rust
/// 1 行の値を人物にする。列が無い場合と数値でない場合は失敗を返す。
fn to_person(headers: &csv::StringRecord, record: &csv::StringRecord) -> Result<Person> {
    Ok(Person {
        height: number(headers, record, "身長")?,
        weight: number(headers, record, "体重")?,
        age_group: number(headers, record, "年代")?,
        faction: text(headers, record, "派閥")?.to_string(),
    })
}
```

`?` のおかげで、構造体のリテラルの中にそのまま書けます。Go 版では同じ処理が

```go
height, err := number(index, values, "身長")
if err != nil {
    return Person{}, err
}

weight, err := number(index, values, "体重")
if err != nil {
    return Person{}, err
}
// ...以下同様
```

と 4 回繰り返しになりました。**`?` の 1 文字が `if err != nil` の 3 行に相当します。** どちらも「失敗は値で返す」流儀ですが、書き心地はかなり違います。

列の取り出しは 2 段に分けます。

```rust
/// 列名で数値を読む。
fn number(headers: &csv::StringRecord, record: &csv::StringRecord, column: &str) -> Result<i32> {
    let cell = text(headers, record, column)?;

    cell.parse().map_err(|_| Error::NotANumber {
        column: column.to_string(),
        value: cell.to_string(),
    })
}

/// 列名でセルを読む。借用した文字列をそのまま返すので、複製しない。
fn text<'a>(
    headers: &csv::StringRecord,
    record: &'a csv::StringRecord,
    column: &str,
) -> Result<&'a str> {
    headers
        .iter()
        .position(|name| name == column)
        .and_then(|position| record.get(position))
        .ok_or_else(|| Error::MissingColumn(column.to_string()))
}
```

`text` の `<'a>` がライフタイムの注釈です。「返す `&str` は、引数の `record` と同じだけ生きる」と宣言しています。これを書くことで、**セルの文字列を複製せずに借用したまま返せます**。`headers` と `column` には `'a` が付いていないので、返り値はそれらとは無関係だと分かります。

`cell.parse()` は、変換先の型を戻り値から推論します。`Result<i32>` を返す関数の中なので `i32` にパースされます。型推論が戻り値の型から逆向きに効くのは、Rust を読み始めたときに戸惑うところです。

`.position(...).and_then(...).ok_or_else(...)` の連なりは、`Option` を `Result` に変える定型です。Go 版の「値と ok の多値返却」、Java 版の `Optional` に対応します。

## 1.9 特徴量と正解ラベルに分ける

```rust
/// 人物のリストを特徴量と正解ラベルに分ける。
pub fn split_features_and_labels(people: &[Person]) -> (Vec<Features>, Vec<String>) {
    let features = people
        .iter()
        .map(|person| Features {
            height: person.height,
            weight: person.weight,
            age_group: person.age_group,
        })
        .collect();

    let labels = people.iter().map(|person| person.faction.clone()).collect();

    (features, labels)
}
```

引数が `&[Person]`（スライスの借用）であることが大事です。`Vec<Person>` を値で受け取ると所有権が移り、呼び出し側で人物のリストを使えなくなります。**「読むだけなら借りる」** が Rust の基本です。C# 版・Java 版で `List<Person>` をそのまま渡していた部分が、Rust では「借りるか、渡すか」の選択になります。

`person.faction.clone()` の `clone()` は避けられません。`faction` は `String` で、`Person` が所有しています。ラベルのベクタも自分の `String` を持つ必要があるので、ここは複製します。`Vec<&str>` にすれば複製を避けられますが、その場合は元の `people` が生きている間しか使えない型になり、扱いが面倒になります。**どこで複製するかを決めるのが設計**というのが、ほかの言語版には無い論点です。

テストを書きます。

```rust
#[test]
fn 人物のリストを特徴量と正解ラベルに分ける() {
    let people = vec![
        Person {
            height: 170,
            weight: 60,
            age_group: 20,
            faction: KINOKO.to_string(),
        },
        Person {
            height: 160,
            weight: 50,
            age_group: 30,
            faction: TAKENOKO.to_string(),
        },
    ];

    let (x, t) = split_features_and_labels(&people);

    assert_eq!(x, vec![features(170, 60, 20), features(160, 50, 30)]);
    assert_eq!(t, labels(&[KINOKO, TAKENOKO]));
}
```

`assert_eq!` がベクタどうしをそのまま比べられるのは、`Features` に `PartialEq` を derive したからです。Go 版では `Features` がスライスを持つため `==` で比べられず `reflect.DeepEqual` が要りましたが、Rust では `#[derive(PartialEq)]` が構造体でもベクタでも同じように効きます。

## 1.10 正解率を計算する

```rust
/// 予測が正解ラベルと一致した割合を返す。件数が違えば失敗を返す。
pub fn accuracy(predictions: &[String], labels: &[String]) -> Result<f64> {
    if predictions.len() != labels.len() {
        return Err(Error::LengthMismatch {
            predictions: predictions.len(),
            labels: labels.len(),
        });
    }

    let correct = predictions
        .iter()
        .zip(labels)
        .filter(|(prediction, label)| prediction == label)
        .count();

    Ok(correct as f64 / labels.len() as f64)
}
```

`zip` で 2 つのスライスを組にし、一致するものを数えます。Python 版の `zip`、Java 版の `IntStream.range`、Go 版の添字ループに当たります。

`correct as f64` の `as` は、Rust では数値の変換を**必ず明示する**という決まりです。`usize` と `f64` を混ぜて割ることはできません。この厳しさは C# 版・Java 版の暗黙の変換に慣れていると煩わしく感じますが、整数の割り算で 0 になる事故は起きません。

テストは 3 つ書きます。

```rust
#[test]
fn 全部当たれば正解率は一になる() {
    let predictions = labels(&[KINOKO, TAKENOKO]);
    let truth = labels(&[KINOKO, TAKENOKO]);

    assert_eq!(accuracy(&predictions, &truth).unwrap(), 1.0);
}

#[test]
fn 半分当たれば正解率は零点五になる() {
    let predictions = labels(&[KINOKO, KINOKO]);
    let truth = labels(&[KINOKO, TAKENOKO]);

    assert_eq!(accuracy(&predictions, &truth).unwrap(), 0.5);
}

#[test]
fn 件数が違えば正解率を求められない() {
    let predictions = labels(&[KINOKO]);
    let truth = labels(&[KINOKO, TAKENOKO]);

    let error = accuracy(&predictions, &truth).unwrap_err();

    assert_eq!(
        error.to_string(),
        "予測と正解ラベルの件数が違います: 1 と 2"
    );
}
```

`unwrap()` は「成功しているはずなので、失敗ならパニックせよ」という意味です。**テストの中では `unwrap()` を使ってよい**というのが通例で、失敗すればテストが落ちて、それが望む挙動です。本体のコードでは `?` で上に返します。失敗を確かめるほうは `unwrap_err()` で中身を取り出します。

正解率の比較に `assert_eq!` をそのまま使えるのは、1.0 と 0.5 が浮動小数点で厳密に表せる値だからです。第 2 章以降、平均値のように誤差が出る値を比べるときは、許容誤差付きの比較に変えます。

## 1.11 実データで正解率を表示する

### データが無ければスキップする

実データを使うテストは `tests/` に置きます。`src/` の中のテストが**単体テスト**（非公開のものも見える）なのに対し、`tests/` の中は**結合テスト**で、公開された API だけを、外部のクレートと同じ立場から呼びます。

```rust
use getting_started_ml::chapter01::{
    accuracy, load_people, predict_by_rule, split_features_and_labels,
};
use getting_started_ml::dataset;
use std::path::PathBuf;

/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
/// Rust の標準のテストには「スキップ」が無いので、早く戻って理由を表示する。
fn data_file() -> Option<PathBuf> {
    let csv_file = dataset::current().join("KvsT.csv");

    if csv_file.exists() {
        Some(csv_file)
    } else {
        eprintln!("学習データ KvsT.csv が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}

#[test]
fn 実データを読み込んで正解率を求める() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let people = load_people(&csv_file).expect("CSV を読めること");

    assert_eq!(people.len(), 19);
    // ...
}
```

`let ... else` は「パターンに当てはまらなければ、この節を抜ける」という書き方です。当てはまった場合だけ後続に進めます。

ここで **Rust の標準のテストには「スキップ」がありません**。Go の `t.Skip`、JUnit の `assumeTrue`、xUnit の `Skip` に当たるものが無く、`#[ignore]` は実行前に静的に決める印なので、「データがあるときだけ走らせる」には使えません。代わりに、理由を標準エラーに出して早く戻ります。成功として数えられてしまうのが弱点で、**「本当に走ったのか」はカバレッジか出力で確かめる**ことになります。

もう 1 つ、Go 版との違いがあります。Go の `go test` はパッケージのディレクトリで走るので、既定の相対パス `../data/sukkiri-ml` が届かず、`ML_DATA_DIR` を渡す必要がありました。**Cargo のテストはパッケージのルート（`apps/rust/`）で走る**ので、相対パスがそのまま届きます。

### 結果を表示する

```rust
/// 実データでルールによる判定の正解率を表示する。
pub fn run(out: &mut impl std::io::Write) -> Result<()> {
    let people = load_people(&dataset::current().join("KvsT.csv"))?;
    let (x, t) = split_features_and_labels(&people);

    let predictions: Vec<String> = x
        .iter()
        .map(|features| predict_by_rule(*features).to_string())
        .collect();

    writeln!(out, "データ件数: {}", people.len())?;
    writeln!(
        out,
        "ルールによる判定の正解率: {:.4}",
        accuracy(&predictions, &t)?
    )?;

    Ok(())
}
```

引数を `&mut impl std::io::Write` にして、書き出し先を差し替えられるようにしています。Go 版の `io.Writer`、Java 版の `PrintStream` と同じ設計です。`impl Trait` は「`Write` を実装した何らかの型」という意味で、呼び出しごとに具体的な型に展開されます（動的なディスパッチではありません）。

`predict_by_rule(*features)` の `*` は参照を外す操作です。`x.iter()` が返すのは `&Features` で、`predict_by_rule` は `Features` を取るので、外して渡します。`Features` に `Copy` が付いているので、これで複製が起きます。

章を選んで実行するコマンドはこうなります。

```rust
//! 章を選んで実行する。使い方: cargo run --bin chapters -- chapter01

use getting_started_ml::chapter01;
use std::io::{self, Write};
use std::process::ExitCode;

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().collect();
    let stdout = io::stdout();
    let mut out = stdout.lock();

    let result = match args.get(1).map(String::as_str) {
        Some("chapter01") => chapter01::run(&mut out),
        _ => {
            let _ = writeln!(
                io::stderr(),
                "使い方: cargo run --bin chapters -- (chapter01)"
            );

            return ExitCode::FAILURE;
        }
    };

    if let Err(error) = result {
        let _ = writeln!(io::stderr(), "{error}");

        return ExitCode::FAILURE;
    }

    ExitCode::SUCCESS
}
```

`main` が `ExitCode` を返せます。`stdout.lock()` は、書き出しのたびにロックを取らないようにする定型です。

実行します。

```text
$ cargo run --bin chapters -- chapter01
データ件数: 19
ルールによる判定の正解率: 0.7368
```

**正解率 0.7368** は、Python 版・Kotlin 版・TypeScript 版・F# 版・Java 版・C# 版・Scala 版・Go 版と同じ値です。19 人中 14 人を正しく判定できました。乱数を使わない処理なので、言語が違っても値は一致します（第 2 章では、ここが崩れます）。

## 1.12 リファクタリング

### 整形と静的解析

```text
$ cargo fmt --check
$ cargo clippy --all-targets -- -D warnings
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 6.32s
```

どちらも何も言わなければ合格です。わざと崩すとどうなるかを見ておきます。

```rust
pub fn current() -> PathBuf {
    let unused = 1;
    let v = vec![1, 2, 3];
    for i in 0..v.len() { println!("{}", v[i]); }
        from(|name| std::env::var(name).ok())
}
```

```text
$ cargo fmt --check
Diff in /Users/.../apps/rust/src/dataset.rs:18:
 pub fn current() -> PathBuf {
     let unused = 1;
     let v = vec![1, 2, 3];
-    for i in 0..v.len() { println!("{}", v[i]); }
-        from(|name| std::env::var(name).ok())
+    for i in 0..v.len() {
+        println!("{}", v[i]);
+    }

$ cargo clippy --all-targets -- -D warnings
error: unused variable: `unused`
error: the loop variable `i` is only used to index `v`
error: useless use of `vec!`
error: could not compile `getting-started-ml` (lib) due to 3 previous errors
```

`rustfmt` はファイル名と行番号つきで差分を出します（Go 版の `gofmt -l` はファイル名だけでした）。clippy は「添字でしか使っていないループ変数」「`vec!` を使う必要がない」といった、`go vet` より踏み込んだ指摘をします。`-D warnings` で警告がエラーになるので、CI では先へ進めません。

`golangci-lint` が検査器の詰め合わせだったのに対し、clippy は**単体で 700 個以上の lint を持つ**道具です。この違いは第 5 章で詳しく扱います。

### カバレッジ

```text
$ cargo llvm-cov --summary-only
Filename              Regions  Missed Regions  Cover  Functions  Missed Functions  Executed  Lines  Missed Lines   Cover
bin/chapters.rs            28              28  0.00%          1                 1     0.00%     16            16   0.00%
chapter01.rs              265              70 73.58%         28                 6    78.57%    156            31  80.13%
dataset.rs                 38               1 97.37%          8                 0   100.00%     23             0 100.00%
TOTAL                     331              99 70.09%         37                 7    81.08%    195            47  75.90%
```

学習データを外すと下がります。

```text
$ ML_DATA_DIR=/nonexistent cargo llvm-cov --summary-only
TOTAL                     331             159 51.96%         37                13    64.86%    195            78  60.00%
```

**70.09% と 51.96%、同じコードで 18 ポイントの差**です。実データのテストがスキップされた分がそのまま落ちています。先ほど「スキップが成功として数えられる」と書きましたが、カバレッジを見れば走っていないことが分かります。

`cargo-llvm-cov` は rustc の計測機構をそのまま使うので、別のツールが再実装した近似値ではありません。Nix の環境では `llvm-tools-preview`（rustup の部品）が無いので、`LLVM_COV`・`LLVM_PROFDATA` に nixpkgs の LLVM を教えています（`ops/nix/environments/rust/shell.nix`）。

### まとめて検査する

リポジトリのルートで次を実行すると、CI と同じ順に検査できます。

```bash
npx gulp apps:check:rust
```

中身は `cargo fmt --check && cargo clippy --all-targets -- -D warnings && cargo test && cargo llvm-cov --summary-only` です。

## 1.13 まとめ

この章では、ルールによる判定と正解率を TDD で実装し、実データで 0.7368 という値を得ました。Rust に固有の論点は次のとおりです。

1. **テストは実装と同じファイルに書く** — `#[cfg(test)] mod tests` はテストのときだけコンパイルされ、非公開の関数にも手が届く。関数名に日本語をそのまま使える
2. **失敗は `Result` と `enum` で表す** — 例外が無いのは Go 版と同じだが、`?` 演算子で `if err != nil` の 3 行が 1 文字になり、`match` の網羅性はコンパイラが検査する
3. **所有権と借用が設計になる** — 読むだけなら `&[Person]` で借りる。`Copy` を付けられる型かどうかが「値そのもの」か「所有するもの」かの目印になる。どこで `clone()` するかを決めるのは、ほかの言語版に無い判断
4. **ライブラリの親切はテストで確かめてから頼る** — csv クレートは BOM を自動で取り除く。シリーズを通して書いてきた BOM の除去が、Rust 版だけ要らない
5. **スキップの仕組みが無い** — 実データのテストは早期に戻る形にし、本当に走ったかはカバレッジで確かめる

**TODO リスト（この章の完了時点）**:

- [x] CSV を読み込む
- [x] 特徴量と正解ラベルに分ける
- [x] ルールで派閥を判定する
- [x] 正解率を計算する
- [x] 実データで正解率を表示する

次の章では、アヤメのデータを読み込み、欠損値を補完して、訓練データとテストデータに分けます。そこで `Result` と所有権がもう少し複雑な形で効いてきます。そして、**乱数の実装が言語ごとに違う**ことが、ここではじめて数値の不一致として現れます。
