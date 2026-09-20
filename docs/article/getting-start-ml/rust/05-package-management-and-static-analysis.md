---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "Cargo（Cargo.toml・キャレット要件・cargo add・cargo tree・Cargo.lock）で依存を固定し、クレートの版が型を分けることを実験で確かめ、rustfmt・clippy・cargo-llvm-cov でコードの品質を機械的に検査する。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T14:20:00Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、何をコミットし、何をコミットしないかを決めました。この章では、コミットした情報から **同じ環境を作り直す** 仕組み（パッケージ管理）と、コードの品質を **機械的に確かめる** 仕組み（整形・静的解析・カバレッジ）を整えます。

Rust の道具立ては、Go 版に似て「ほとんど 1 つのコマンドに入っている」構成です。違うのは、整形（rustfmt）と静的解析（clippy）とカバレッジ（cargo-llvm-cov）が **cargo のサブコマンドとして外から足せる** ところです。

| 役割 | Rust | Go 版 | Java 版 |
|------|------|-------|---------|
| 依存の解決 | `cargo`（crates.io） | `go` コマンド（Go Modules） | Gradle |
| 依存の記録 | `Cargo.toml`・`Cargo.lock` | `go.mod`・`go.sum` | `gradle/libs.versions.toml` |
| 整形 | `cargo fmt`（rustfmt） | `gofmt` | Spotless + google-java-format |
| 型検査 | `cargo build`（rustc） | Go コンパイラ | Java コンパイラ |
| 静的解析 | `cargo clippy` | `go vet`・golangci-lint | Error Prone・PMD |
| テスト | `cargo test` | `go test` | JUnit（Gradle 経由） |
| カバレッジ | `cargo llvm-cov` | `go test -cover` | JaCoCo |
| 設定ファイル | `Cargo.toml` のみ | なし（`go.mod` のみ） | `build.gradle.kts` ほか 3 つ |

この章では、Rust 固有の論点を 1 つ、じっくり扱います。**クレートの版が型を分ける** ことです（5.4 節）。linfa 0.8 に ndarray 0.17 の行列を渡すと何が起きるかを、実際にコンパイルして確かめます。

## 5.2 Cargo によるパッケージ管理

### Cargo.toml

Rust のプロジェクトは `Cargo.toml` で始まります。第 3 章までの `apps/rust/Cargo.toml` はこれだけです。

```toml
[package]
name = "getting-started-ml"
version = "0.1.0"
edition = "2024"
publish = false

[dependencies]
csv = "1.4"
linfa = "0.8"
linfa-trees = "0.8"
ndarray = "0.16"
rand = "0.8"

[lints.clippy]
# 章ごとの実装は読みやすさを優先する。警告は抑えずに直すのが原則
all = { level = "deny", priority = -1 }
```

| 項目 | 意味 |
|------|------|
| `[package]` | パッケージの名前・版・edition |
| `edition = "2024"` | 言語の「版」。`match` の書き方や `unsafe` の扱いなど、後方互換を壊す変更の区切り |
| `publish = false` | crates.io に公開しない宣言。`cargo publish` の事故を防ぐ |
| `[dependencies]` | 依存するクレートと、受け入れる版の範囲 |
| `[lints.clippy]` | clippy の警告の扱い（5.6 節） |

Go 版の `go.mod` にはモジュールのパスが必要でしたが、Cargo は `name` だけです。クレートを使うときの名前がそのまま `use getting_started_ml::chapter01;`（`-` は `_` になります）になります。

### セマンティックバージョニングとキャレット要件

`csv = "1.4"` という書き方は、実は「1.4 を使う」という意味ではありません。省略された `^`（キャレット）が付いていて、**先頭の 0 でない数字を変えない範囲で新しいもの** を受け入れます。

| 書き方 | 意味する範囲 | 受け入れる例 |
|--------|------------|------------|
| `"1.4"`（= `^1.4`） | `>=1.4.0, <2.0.0` | 1.4.0、1.9.3 |
| `"0.8"`（= `^0.8`） | `>=0.8.0, <0.9.0` | 0.8.1、0.8.8 |
| `"0.0.3"` | `>=0.0.3, <0.0.4` | 0.0.3 のみ |
| `"=0.8.1"` | 0.8.1 だけ | 0.8.1 |
| `">=0.8, <0.9"` | 明示的な範囲 | 0.8.x |

0.x の扱いが肝心です。セマンティックバージョニングでは、メジャーバージョンが 0 の間は 0.8 → 0.9 が破壊的変更になりえます。Cargo はこれを知っているので、`"0.8"` は 0.9 を受け入れません。本シリーズが `linfa = "0.8"`・`ndarray = "0.16"`・`rand = "0.8"` と書いているのは、この性質を使って「互換性のある範囲だけ」を許すためです。

実際にどの版が選ばれたかは `cargo tree` で見ます。

```bash
cargo tree --depth 1
```

```text
getting-started-ml v0.1.0 (/path/to/apps/rust)
├── csv v1.4.0
├── linfa v0.8.1
├── linfa-trees v0.8.1
├── ndarray v0.16.1
└── rand v0.8.8
```

`linfa = "0.8"` と書いて 0.8.1 が、`rand = "0.8"` と書いて 0.8.8 が選ばれています。

### 依存を足す

`Cargo.toml` を手で編集しても構いませんが、`cargo add` を使うと版の範囲とフィーチャーの一覧を教えてくれます。使い捨てのパッケージで試します。

```bash
cargo init --name demo --vcs none
cargo add linfa@0.8
```

```text
    Updating crates.io index
      Adding linfa v0.8 to dependencies
             Features as of v0.8.0:
             - benchmarks
             - blas
             - criterion
             - intel-mkl-static
             - intel-mkl-system
             - ndarray-linalg
             - netlib-static
             - netlib-system
             - openblas-static
             - openblas-system
             - pprof
             - serde
             - serde_crate
    Updating crates.io index
     Locking 30 packages to latest Rust 1.91.1 compatible versions
      Adding sprs v0.11.2 (available: v0.11.5)
```

3 つのことが読み取れます。

1. **フィーチャー（feature）の一覧** — クレートの機能を選んで有効にする仕組みです。linfa の `blas`・`openblas-system` などは、行列演算に BLAS の実装を使うための選択肢です。本シリーズはどれも有効にしていません。BLAS なしの純 Rust（linfa-linalg）で足りるからです
2. **`Locking 30 packages`** — linfa 1 つで 30 個のパッケージが依存関係に入ります
3. **`Adding sprs v0.11.2 (available: v0.11.5)`** — 新しい 0.11.5 があるのに 0.11.2 が選ばれています。linfa 0.8.1 が `sprs` に対して `"0.11.2"` より狭い要求をしているか、ほかの制約と両立する最大の版がこれだからです。Cargo は「すべての制約を満たす中で最も新しい版」を選びます

`Cargo.toml` には、書いたとおりの 1 行だけが入ります。

```toml
[dependencies]
linfa = "0.8"
```

### Cargo.lock

解決の結果は `Cargo.lock` に書かれます。上の使い捨てのパッケージでは 31 個（自分自身を含む）、本シリーズの `apps/rust` では 48 個のパッケージが並びます。

```text
[[package]]
name = "linfa"
version = "0.8.1"
source = "registry+https://github.com/rust-lang/crates.io-index"
checksum = "87b84e47ca7a9d63f5be24c104e216c8263bfada38080cbdfe1082e611a81fd3"
dependencies = [
 "approx",
 "ndarray",
 "num-traits",
 "rand",
 "sprs",
 "thiserror",
]
```

`checksum` は crates.io にあるアーカイブのハッシュです。Go の `go.sum`、npm の `package-lock.json` の `integrity` に当たります。ダウンロードしたものがこのハッシュと違えば、Cargo はビルドを止めます。

lock ファイルがあると、`cargo build` も `cargo test` も **記録された版をそのまま使います**。新しい版を取りに行くのは `cargo update` を実行したときだけです。

```bash
cargo update --dry-run
```

```text
    Updating crates.io index
     Locking 0 packages to latest Rust 1.91.1 compatible versions
note: pass `--verbose` to see 1 unchanged dependencies behind latest
warning: not updating lockfile due to dry run
```

`--dry-run` を付けると、lock ファイルを書き換えずに「何が変わるか」だけを表示します。依存を上げるときは、まずこれで差分を見て、それから `cargo update` を実行し、テストを通してからコミットします。

| コマンド | 何をするか |
|---------|-----------|
| `cargo add <crate>` | `Cargo.toml` に依存を足し、lock を更新する |
| `cargo tree` | 依存の木を表示する。`--depth 1` で直接の依存だけ、`--duplicates` で版が重複しているクレートだけ |
| `cargo update` | `Cargo.toml` の範囲内で lock を最新にする |
| `cargo fetch` | lock に書かれた依存をダウンロードする（CI の準備に使う） |
| `cargo build` / `cargo test` | lock のとおりにビルドする。lock が無ければその場で作る |

### 本番依存と開発依存

Go 版の `go.mod` には本番依存と開発依存の区別がありませんでした。Cargo にはあります。

```toml
[dependencies]       # 本体のビルドに必要
[dev-dependencies]   # テスト・ベンチマーク・サンプルだけで使う
[build-dependencies] # ビルドスクリプト（build.rs）で使う
```

本シリーズの `Cargo.toml` に `[dev-dependencies]` が無いのは、テストを標準の `#[test]` だけで書いていて、追加のクレート（`approx` の近似比較や `proptest` など）を使っていないからです。テスト用のクレートを入れるときは `cargo add --dev` を使い、`[dev-dependencies]` に入れます。そうすれば、そのクレートは `cargo build` の成果物には含まれません。

## 5.3 Rust の版を固定する

`Cargo.toml` の `edition = "2024"` は、**言語の版** です。Go の `go.mod` の `go` 指令に当たりますが、Rust の edition には 1 つ面白い性質があります。**クレートごとに別の edition を選べて、混ぜて使える** ことです。edition 2015 のクレートと edition 2024 のクレートを同じビルドでリンクできます。

一方、**コンパイラの版** は `Cargo.toml` では決まりません。本シリーズは Nix の環境定義で固定しています。

```nix
  buildInputs = baseShell.buildInputs ++ (with packages; [
    rustc
    cargo
    rustfmt
    clippy
    rust-analyzer
    cargo-llvm-cov
    llvmPackages.libllvm
  ]);
```

```bash
nix develop .#rust
```

```text
Rust development environment activated
  - rustc: rustc 1.91.1 (ed61e7d7e 2025-11-07) (built from a source tarball)
  - cargo: cargo 1.91.0 (ea2d97820 2025-10-10)
  - cargo-llvm-cov: cargo-llvm-cov 0.6.20
```

Rust には `rust-toolchain.toml` というファイルで版を宣言する仕組みもありますが、これは rustup（Rust 公式のツールチェーン管理）が読むものです。本リポジトリは 14 言語版の環境を Nix でそろえているので、Rust だけ別の仕組みを増やさず、Nix に任せています。

## 5.4 クレートの版が型を分ける

ここからが Rust 固有の論点です。第 3 章で、自作の決定木と linfa の決定木を突き合わせました。linfa にデータを渡すときは ndarray の `Array2<f64>` を作ります。

```rust
use linfa::prelude::*;
use ndarray::{Array1, Array2};

let dataset = Dataset::new(records(x_train)?, Array1::from(encoded));
```

このとき、`Cargo.toml` の `ndarray = "0.16"` を `"0.17"` にしたらどうなるでしょうか。ndarray の最新は 0.17 で、新しいほうがよさそうに見えます。実際に、0.16（linfa が使う版）と 0.17 の両方を入れて、0.17 で作った行列を linfa に渡してみました。

```toml
[dependencies]
linfa = "0.8"
ndarray = "0.16"
ndarray_new = { package = "ndarray", version = "0.17" }
```

```rust
use linfa::Dataset;
use ndarray::Array1;
use ndarray_new::array;

pub fn mix() {
    let records = array![[1.0, 2.0], [3.0, 4.0]];
    let targets = Array1::from(vec![0usize, 1usize]);
    let _dataset = Dataset::new(records, targets);
}
```

```bash
cargo build
```

```text
error[E0308]: mismatched types
    --> src/zzver.rs:11:33
     |
  11 |     let _dataset = Dataset::new(records, targets);
     |                    ------------ ^^^^^^^ expected `ArrayBase<OwnedRepr<_>, Dim<[usize; 2]>>`, found `ArrayBase<..., ..., {float}>`
     |                    |
     |                    arguments to this function are incorrect
     |
note: two different versions of crate `ndarray` are being used; two types coming from two different versions of the same crate are different types even if they look the same
    --> /Users/.../ndarray-0.16.1/src/lib.rs:1280:1
     |
1280 | pub struct ArrayBase<S, D>
     | ^^^^^^^^^^^^^^^^^^^^^^^^^^ this is the expected type `ndarray::ArrayBase`
     |
    ::: /Users/.../ndarray-0.17.2/src/lib.rs:1295:1
     |
1295 | pub struct ArrayBase<S, D, A = <S as RawData>::Elem>
     | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ this is the found type `ndarray::ArrayBase`
     |
     = help: you can use `cargo tree` to explore your dependency tree
```

コンパイラの言葉をそのまま引くと、**「同じクレートの 2 つの版から来た 2 つの型は、見た目が同じでも違う型である」**。`ndarray::ArrayBase` という名前は同じでも、0.16 の `ArrayBase` と 0.17 の `ArrayBase` は別の型なので、片方をもう片方の関数に渡せません。

この状況は `cargo tree --duplicates` で見つけられます。

```bash
cargo tree --duplicates
```

```text
ndarray v0.16.1
├── getting-started-ml v0.1.0 (/path/to/apps/rust)
├── linfa v0.8.1
│   ├── getting-started-ml v0.1.0 (/path/to/apps/rust)
│   └── linfa-trees v0.8.1
├── linfa-trees v0.8.1 (*)
├── ndarray-rand v0.15.0
│   └── linfa-trees v0.8.1 (*)
└── sprs v0.11.2
    └── linfa v0.8.1 (*)

ndarray v0.17.2
└── getting-started-ml v0.1.0 (/path/to/apps/rust)
```

linfa の系統がすべて 0.16.1 を使い、自分のパッケージだけが 0.17.2 も使っている——という絵がそのまま出ます。

| 言語 | 同じライブラリの版が 2 つあるとき |
|------|--------------------------------|
| Rust | 両方をリンクでき、型は別物になる。渡そうとするとコンパイルエラー |
| Java | クラスパスに 1 つしか置けない。先に見つかったほうが勝ち、実行時に `NoSuchMethodError` になることがある |
| Go | モジュールごとに 1 つの版に解決される（MVS）。版が違う型は存在しない |
| TypeScript | `node_modules` の入れ子で両方を持てる。型は構造的に一致すれば通る |

Rust の挙動は、実行時に壊れる Java と比べれば安全です。壊れる組み合わせが **コンパイル時に分かる** からです。ただし、エラーメッセージを読まずに「型が合わない」とだけ受け取ると、原因にたどり着けません。`two different versions of the same crate` という一文を覚えておいてください。

**本シリーズの結論**: linfa 0.8.1 が使う版に全部そろえます（ADR 009）。

| クレート | 固定する版 | 理由 |
|---------|-----------|------|
| ndarray | 0.16 | linfa 0.8.1 が 0.16 を使う。0.17 は別の型になる |
| rand | 0.8 | linfa が 0.8 系を使う。0.9 の `SmallRng` を linfa に渡すと同じエラーになる |
| rand_xoshiro | 0.6 | linfa-clustering が使う版（第 14 章で K-means に RNG を渡すときに効く） |

「最新が正しい」とは限りません。**自分が使うライブラリが前提にしている版に合わせる** ほうが正しいことがあります。

## 5.5 整形 — rustfmt

`cargo fmt` は rustfmt を呼びます。設定項目は `rustfmt.toml` で変えられますが、本シリーズは既定のままです。Go の `gofmt` と同じく、スタイルを議論しないという選択です。

検査には `--check` を使います。整形されていなければ差分を表示し、終了コード 1 で終わります。わざと崩したファイルを置いて実行しました。

```rust
//! 整形の確認用。

pub fn badly(  ) {
  let x=1;
    println!( "{}", x );
}
```

```bash
cargo fmt --check
```

```text
Diff in /path/to/apps/rust/src/zzfmt.rs:1:
 //! 整形の確認用。

-pub fn badly(  ) {
-  let x=1;
-    println!( "{}", x );
+pub fn badly() {
+    let x = 1;
+    println!("{}", x);
 }
```

`gofmt -l` がファイル名しか出さず、しかも終了コード 0 を返したのとは対照的です。rustfmt は **差分を出し、失敗を終了コードで伝えます**。そのため CI もタスクも `cargo fmt --check` の 1 語で済みます（第 6 章）。直すときは `--check` を外して `cargo fmt` を実行します。

ここで、第 4 章で触れた改行コードの話に戻ります。CRLF の Rust ファイルを `--check` にかけても、何も言われません。

```bash
rustfmt --check --edition 2024 src/zzcrlf.rs
echo $?
```

```text
0
```

rustfmt の `newline_style` の既定は `Auto` で、ファイルごとに元の改行コードを見て判断するからです。Go 版では CRLF が検査を落としましたが、Rust ではそうならない——同じ「整形の検査」でも、こういうところが違います。だからこそ、**検査が何を見ていて何を見ていないのかは、実験で確かめる** 必要があります。

## 5.6 静的解析 — clippy

### clippy の位置づけ

Rust では、ほかの言語なら「リンターの仕事」とされる多くのことをコンパイラが引き受けます。使っていない変数、到達しないコード、`match` の網羅性、借用の誤り——すべて `cargo build` で分かります。その上にもう一段、**慣用的な書き方** を教えるのが clippy です。

clippy の lint は、名前空間 `clippy::` の下でグループに分かれています。

| グループ | 既定 | 内容 |
|---------|------|------|
| `clippy::correctness` | deny | ほぼ確実に誤りであるコード |
| `clippy::suspicious` | warn | 誤りの可能性が高い書き方 |
| `clippy::style` | warn | 慣用的でない書き方 |
| `clippy::complexity` | warn | 単純にできる複雑な書き方 |
| `clippy::perf` | warn | 遅い書き方 |
| `clippy::pedantic` | allow | 細かすぎることもある指摘。明示的に有効にする |
| `clippy::nursery` | allow | 開発中の lint |
| `clippy::cargo` | allow | `Cargo.toml` に関する lint |

`clippy::all` は上の 5 つ（correctness・suspicious・style・complexity・perf）をまとめたものです。本シリーズは `Cargo.toml` でこれを `deny` にしています。

```toml
[lints.clippy]
# 章ごとの実装は読みやすさを優先する。警告は抑えずに直すのが原則
all = { level = "deny", priority = -1 }
```

`priority = -1` は「この指定を先に適用する」という意味です。あとから個別の lint を `allow` にしたいときに、グループ全体の `deny` より優先されるようにするための書き方です。

Go 版が golangci-lint の「既定の 5 つの検査器」を使うと決めたのと同じ判断を、Rust では「`clippy::all` を使い、`pedantic` は入れない」という形で下しています。

### わざと違反を入れて、検査が効いていることを確かめる

検査は「動いているつもり」が一番危ないので、違反を入れて失敗することを確かめます。

```rust
//! 静的解析の確認用。記事のために違反を並べたモジュール。

/// 種類が setosa かどうかを返す。
pub fn has_setosa(name: &str) -> bool {
    if name == "setosa" { true } else { false }
}

/// 値を順に表示する。
pub fn show(values: &Vec<f64>) {
    for i in 0..values.len() {
        println!("{}", values[i]);
    }
}

/// 欠損値があるかどうかを返す。
pub fn is_missing(value: Option<f64>) -> bool {
    value == None
}

/// 合計を求める。
pub fn total(values: &[f64]) -> f64 {
    let mut sum = 0.0;
    let count = values.len();

    for value in values {
        sum += value;
    }

    sum
}
```

```bash
cargo clippy --all-targets -- -D warnings
```

```text
error: unused variable: `count`
  --> src/zzdemo.rs:23:9
   |
23 |     let count = values.len();
   |         ^^^^^
   |
   = note: `-D unused-variables` implied by `-D warnings`
   = help: to override `-D warnings` add `#[allow(unused_variables)]`
help: if this is intentional, prefix it with an underscore
   |
23 |     let _count = values.len();
   |         +

error: this if-then-else expression returns a bool literal
 --> src/zzdemo.rs:5:5
  |
5 |     if name == "setosa" { true } else { false }
  |     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ help: you can reduce it to: `name == "setosa"`
  |
  = help: for further information visit https://rust-lang.github.io/rust-clippy/master/index.html#needless_bool
  = note: `-D clippy::needless-bool` implied by `-D clippy::all`

error: writing `&Vec` instead of `&[_]` involves a new object where a slice will do
 --> src/zzdemo.rs:9:21
  |
9 | pub fn show(values: &Vec<f64>) {
  |                     ^^^^^^^^^ help: change this to: `&[f64]`
  |
  = help: for further information visit https://rust-lang.github.io/rust-clippy/master/index.html#ptr_arg
  = note: `-D clippy::ptr-arg` implied by `-D clippy::all`

error: the loop variable `i` is only used to index `values`
  --> src/zzdemo.rs:10:14
   |
10 |     for i in 0..values.len() {
   |              ^^^^^^^^^^^^^^^
   |
   = note: `-D clippy::needless-range-loop` implied by `-D clippy::all`
help: consider using an iterator
   |
10 -     for i in 0..values.len() {
10 +     for <item> in &values {
   |

error: binary comparison to literal `Option::None`
  --> src/zzdemo.rs:17:5
   |
17 |     value == None
   |     ^^^^^^^^^^^^^ help: use `Option::is_none()` instead: `value.is_none()`
   |
   = note: `-D clippy::partialeq-to-none` implied by `-D clippy::all`

error: could not compile `getting-started-ml` (lib) due to 5 previous errors
```

5 件が **error** として報告され、ビルドが止まりました。内訳を見ると、指摘の出どころが 2 つあることが分かります。

| 指摘 | 出どころ | 伝えていること |
|------|---------|--------------|
| `unused variable: count` | rustc（`-D warnings` で昇格） | 使っていない変数 |
| `needless_bool` | clippy::style | `if ... { true } else { false }` は式そのものでよい |
| `ptr_arg` | clippy::style | `&Vec<f64>` より `&[f64]` を受け取るほうが呼び出し側が自由 |
| `needless_range_loop` | clippy::style | 添字のためだけの `for i in 0..n` はイテレータにできる |
| `partialeq_to_none` | clippy::style | `x == None` より `x.is_none()` |

どの指摘にも `help:` で **直した形** が添えられています。`ptr_arg` は「`&Vec<f64>` を受け取ると、配列やスライスを持っている呼び出し側がわざわざ `Vec` を作らされる」という設計の話で、Rust を書き始めたときに最もよく出会う指摘の 1 つです。第 4 章で見た `shuffle` が `&[E]` を受け取っているのは、この指摘に従った形です。

### -D warnings とは何か

`cargo clippy -- -D warnings` の `--` より後ろは rustc への引数で、`-D warnings` は「すべての警告をエラーに昇格する」という指定です。`Cargo.toml` の `[lints.clippy]` で `all = "deny"` としているのと合わせて、二重の網になっています。

| 指定 | 効く範囲 |
|------|---------|
| `Cargo.toml` の `[lints.clippy]` | このパッケージの clippy の lint。エディタの表示にも効く |
| コマンドラインの `-D warnings` | rustc の警告も含めたすべて。CI とタスクで使う |

「警告は残してよい」としてしまうと、警告は必ず溜まります。溜まった警告の中では新しい警告が見えません。**検査を通す条件を「警告 0 件」にしておく** のが、この二重の網の目的です。

### 指摘を抑える — #[allow]

直すべきでない指摘もあります。第 2 章の `split_train_test` がその例です。

```rust
#[allow(clippy::cast_precision_loss, clippy::cast_sign_loss)]
let test_count = (shuffled.len() as f64 * test_size).ceil() as usize;
```

`usize` を `f64` にする（精度が落ちうる）、`f64` を `usize` にする（負なら意味が変わる）——どちらも一般には危ない変換で、clippy が指摘します。しかしここでは、件数が `f64` の精度を超えることも、`ceil()` の結果が負になることもありません。そこで、**その行だけ** 抑制しています。

抑制は 3 つの書き方があり、範囲が違います。

| 書き方 | 範囲 |
|--------|------|
| `#[allow(clippy::xxx)]` を文・関数・モジュールに付ける | 付けた対象の中 |
| `Cargo.toml` の `[lints.clippy]` に `xxx = "allow"` | パッケージ全体 |
| コマンドラインの `-A clippy::xxx` | その実行だけ |

いちばん狭い範囲で抑えるのが原則です。そして Go 版の `//nolint:` と同じで、**何を抑えるかより、なぜ抑えるか** を書き残すほうが大切です。上の例では、変換が安全である理由をコードの近くで説明できる形にしています。

## 5.7 コードカバレッジ — cargo-llvm-cov

Rust の標準のツールチェーンにカバレッジのコマンドはありません。rustc には計測の機構（`-C instrument-coverage`）があり、それを使いやすくする cargo のサブコマンドが `cargo-llvm-cov` です。ADR 009 では、もう 1 つの候補だった `cargo-tarpaulin` と比べ、rustc の計測機構をそのまま使う `cargo-llvm-cov` を選びました。

```bash
cargo llvm-cov --summary-only
```

```text
Filename                          Regions  Missed Regions  Cover  Functions  Missed  Executed  Lines  Missed Lines  Cover
-------------------------------------------------------------------------------------------------------------------------
src/bin/chapters.rs                    43              43  0.00%          4       4     0.00%     21            21   0.00%
src/chapter01.rs                      265              70 73.58%         28       6    78.57%    156            31  80.13%
src/chapter02.rs                       46              22 52.17%          3       2    33.33%     19            10  47.37%
src/chapter02/main.rs                  50              50  0.00%          2       2     0.00%     21            21   0.00%
src/chapter02/preprocessing.rs        436              16 96.33%         29       1    96.55%    218             5  97.71%
src/chapter02/table.rs                185               6 96.76%         16       0   100.00%    106             0 100.00%
src/chapter03/decisiontree.rs         453              14 96.91%         38       0   100.00%    246             2  99.19%
src/chapter03/linfatree.rs            132               7 94.70%         15       2    86.67%     66             2  96.97%
src/chapter03/main.rs                 100              13 87.00%          4       0   100.00%     45             1  97.78%
src/dataset.rs                         38               1 97.37%          8       0   100.00%     23             0 100.00%
-------------------------------------------------------------------------------------------------------------------------
TOTAL                                1758             252 85.67%        152      22    85.53%    926            98  89.42%
```

（表示を読みやすくするため、ファイル名のパスを短くしています）

Go の `go test -cover` が「文（statement）」を数えたのに対して、cargo-llvm-cov は **リージョン（region）・関数・行** の 3 つを出します。リージョンは LLVM がコードを分けた区間で、`if` の枝や `&&` の右辺のような分岐の単位に近いものです。行より厳しく、Java の JaCoCo の分岐カバレッジに近い数字になります。

### 数字の読み方に注意する

上の数字は、**学習データがある状態** で測ったものです。第 4 章で見たとおり、Rust の実データのテストは「データが無ければ早く戻る」形なので、データが無くてもテストは `ok` になります。その代わり、カバレッジがはっきり下がります。

```bash
ML_DATA_DIR=/nonexistent cargo llvm-cov --summary-only
```

| 測り方 | リージョン | 関数 | 行 |
|--------|----------|------|-----|
| データあり | 85.67% | 85.53% | 89.42% |
| データなし（CI と同じ条件） | 73.66% | 77.63% | 78.19% |

同じコードのまま 12 ポイント動きます。ファイル別（行のカバレッジ）に見ると、動いたのは実データを読むところです。

| ファイル | データなし | データあり |
|---------|----------|-----------|
| `src/chapter01.rs` | 60.26% | 80.13% |
| `src/chapter02/table.rs` | 86.79% | 100.00% |
| `src/chapter02/preprocessing.rs` | 91.28% | 97.71% |
| `src/chapter03/main.rs` | 0.00% | 97.78% |
| `src/chapter02/main.rs` | 0.00% | 0.00% |

`chapter03/main.rs` が 0.00% から 97.78% に跳ねているのは、`tests/iris_tree.rs` に「実行すると深さごとの正解率と決定木を表示する」というテストがあり、データがあるときだけ `run` を最後まで通すからです。逆に `chapter02/main.rs` はどちらも 0.00% で、第 2 章の `run` を呼ぶテストがまだ無いことが分かります。**カバレッジの差が、実データのテストが本当に走ったかどうかの目印になる**——これが第 4 章の最後に書いた「もう 1 つの見分け方」です。

カバレッジに下限を設けて CI で強制する運用にするなら、どちらの条件で測った数字なのかを決めておかないと意味がありません。本シリーズは CI に学習データを置けない（再配布できない）ので、下限は設けず、数字は傾向を見るために使います。

`--summary-only` を外すと、通らなかった行を色付きで表示します。`--html` を付けると HTML のレポートが `target/llvm-cov/html/` に出ます。どちらも `target/` の中なので、第 4 章の `.gitignore` の 1 行で除外済みです。

## 5.8 まとめ

この章では、Rust の依存と品質の道具立てを見ました。

1. **Cargo** — `Cargo.toml` に直接の依存を、`Cargo.lock` に解決済みの版とチェックサムを記録する。`cargo add`・`cargo tree`・`cargo update --dry-run` で依存を操作する
2. **キャレット要件** — `"0.8"` は `>=0.8.0, <0.9.0`。0.x ではマイナーの上がりが破壊的変更として扱われる
3. **本番依存と開発依存** — `[dependencies]` と `[dev-dependencies]` が分かれている。Go にはこの区別が無かった
4. **クレートの版が型を分ける** — 同じクレートの違う版は別の型になる。`two different versions of the same crate` というエラーが出たら `cargo tree --duplicates` を見る。linfa 0.8.1 に合わせて ndarray 0.16・rand 0.8 に固定する
5. **rustfmt** — `cargo fmt --check` は差分を出し、終了コードで失敗を伝える。CRLF は既定では指摘しない
6. **clippy** — `clippy::all`（correctness・suspicious・style・complexity・perf）を `deny` にし、`-D warnings` で rustc の警告も含めて 0 件を検査の条件にする。抑制は `#[allow]` を最も狭い範囲に付け、理由を残す
7. **cargo-llvm-cov** — リージョン・関数・行の 3 つを測る。学習データの有無で 85.67% と 73.66% に分かれるので、どの条件で測った数字かを明示する

次の章では、これらのコマンドを 1 つのタスクにまとめ、GitHub Actions で自動的に走らせます。
