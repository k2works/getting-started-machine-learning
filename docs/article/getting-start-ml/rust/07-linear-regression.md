---
type: Article
title: "第 7 章: 線形回帰による数値予測"
description: "ndarray 0.16 の行列と自作の掃き出し法で正規方程式を解いて重回帰を TDD で実装し、linfa-linear の LinearRegression と切片・係数を突き合わせる。クレートの版が型を分けることを Java 版・Go 版と対比する。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T14:05:00Z }
---

# 第 7 章: 線形回帰による数値予測

## 7.1 はじめに

第 1〜3 章では、グループを当てる **分類** を扱いました。この章からは、数値そのものを当てる **回帰** に進みます。題材は、映画の SNS での反響や主演俳優の露出度から、興行収入を予測する問題です。

この章では、最も基本的な回帰モデルである **線形回帰** を自作します。[Java 版の第 7 章](../java/07-linear-regression.md) は `double[][]` を包む不変のクラスを、[Go 版](../go/07-linear-regression.md) は gonum の `mat` パッケージを使いました。Rust 版は **ndarray** の `Array2<f64>` を行列として使い、**連立方程式を解く手続きだけを自作** します。行列の積や転置はライブラリにあり、連立方程式を解く関数は ndarray に無いからです。

そのあと、linfa-linear の `LinearRegression` に置き換えて結果を突き合わせます（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。[Python 版の第 7 章](../python/07-linear-regression.md) と同じく、外れ値の除去と回帰の評価指標（MAE・RMSE・決定係数 R²）も実装します。この章の評価指標は第 11〜12 章でも使います。

この章で拾う Rust の論点は、**クレートの版が型を分ける** ことです。ndarray を 0.17 にすると、linfa が使う 0.16 の `Array2` とは「同じ名前の別の型」になり、コンパイルが通りません。Java の依存衝突（同じクラス名が 2 つの jar にある状態）に当たる問題が、Rust では **コンパイル時のエラー** として出ます。

## 7.2 線形回帰とは

### 特徴量の重み付きの和で予測する

線形回帰は、予測値を「切片」と「特徴量ごとの係数 × 特徴量」の和で表すモデルです。この章のデータに当てはめると次の式になります。

```text
予測値 = 切片 + 係数1 × SNS1 + 係数2 × SNS2 + 係数3 × actor + 係数4 × original
```

学習とは、訓練データに対して予測値と実測値のずれが最も小さくなるように、切片と係数を決めることです。ずれの大きさには「誤差の 2 乗の合計」を使います。これを最小にする方法を **最小二乗法** と呼びます。

### 正規方程式

最小二乗法の解は、行列を使うと 1 つの式で求められます。特徴量を行列 `X`、実測値をベクトル `t`、切片と係数をまとめたベクトルを `w` とすると、誤差の 2 乗の合計を最小にする `w` は次の **正規方程式** を満たします。

```text
(Xᵀ X) w = Xᵀ t
```

`X` の先頭には、すべての値が 1 の列を足しておきます（**計画行列**）。この列にかかる係数が切片になります。

やることは 3 つです。行列の積、転置、そして連立方程式を解くこと。前の 2 つは ndarray が持っています。3 つめは持っていないので、自分で書きます。

## 7.3 題材とデータ

この章で使うのは `cinema.csv` です。100 本の映画について、次の 6 列が記録されています。

| 列 | 内容 | 欠損値 |
|----|------|-------|
| cinema_id | 映画の ID | なし |
| SNS1 | SNS での反響の数（1 つ目の指標） | 1 件 |
| SNS2 | SNS での反響の数（2 つ目の指標） | なし |
| actor | 主演俳優のメディア露出の指標 | 1 件 |
| original | 原作の有無（0 または 1） | なし |
| sales | 興行収入 | なし |

「SNS1」「SNS2」「actor」「original」が特徴量、「sales」が正解ラベル（予測したい数値）です。`cinema_id` は映画を区別するための番号なので、特徴量には使いません。

このデータには、SNS2 の値が大きいのに興行収入が低い、傾向から外れた映画が 1 本含まれています。このような **外れ値** は、最小二乗法の結果を大きく引っ張ります。誤差を 2 乗して足し合わせるので、大きく外れた 1 点の影響が大きくなるためです。

散布図で外れ値を確かめる手順は、[Python 版](../python/07-linear-regression.md) と [Kotlin 版の Notebook](../kotlin/07-linear-regression.md) を参照してください。Rust 版には Notebook と可視化の節を設けません。

## 7.4 TODO リストの作成

**TODO リスト**:

- [ ] 連立方程式を解く
  - [ ] 2 元 1 次の連立方程式を解く
  - [ ] 先頭のピボットが 0 でも解く
  - [ ] 解が定まらなければ失敗を返す
- [ ] 評価指標を計算する（MAE・RMSE・R²）
- [ ] 正規方程式で線形回帰を学習する
  - [ ] 計画行列を作る
  - [ ] 直線上の点から切片と係数を求める
  - [ ] 複数の特徴量から切片と係数を求める
- [ ] 学習したモデルで予測する
- [ ] 外れ値を取り除く
  - [ ] SNS2 が 1000 を超え、興行収入が 8500 未満の行を取り除く
  - [ ] 条件の片方だけを満たす行は残す
- [ ] 外れ値の除去・分割・補完をまとめる
- [ ] linfa-linear と結果が一致することを確かめる
- [ ] 実データで学習・評価して表示する

Java 版は「行列の型を作る」から始めましたが、Rust 版は行列そのものを作らないので、いきなり「連立方程式を解く」から始めます。外れ値の条件「SNS2 が 1000 を超え、興行収入が 8500 未満」は、書籍『スッキリわかる Python による機械学習入門』の同じデータでの分析手順に合わせたものです。

## 7.5 クレートの版をそろえる

### なぜ ndarray を 0.16 に固定するのか

この章から ndarray を本格的に使います。ここで最初にぶつかるのが **版の選択** です。ndarray の最新は 0.17 系ですが、`Cargo.toml` には 0.16 と書きます。

```toml
[dependencies]
csv = "1.4"
linfa = "0.8"
linfa-linear = "0.8"
linfa-trees = "0.8"
ndarray = "0.16"
rand = "0.8"
```

理由は、linfa 0.8 が ndarray 0.16 に依存しているからです。Rust は「メジャー版が違う同じクレート」を 1 つの依存グラフに同居させられます。便利な仕組みですが、そのとき **`ndarray::Array2` という名前は 2 つの別の型** になります。試しに ndarray だけ 0.17 にして linfa にデータを渡すと、次のエラーになります。

```text
error[E0308]: arguments to this function are incorrect
 --> src/main.rs:8:19
  |
8 |     let dataset = Dataset::new(x, t);
  |                   ^^^^^^^^^^^^
  |
note: expected `ArrayBase<OwnedRepr<_>, Dim<[usize; 2]>>`, found `ArrayBase<OwnedRepr<f64>, ..., f64>`
note: there are multiple different versions of crate `ndarray` in the dependency graph
   --> ~/.cargo/registry/src/index.crates.io-.../ndarray-0.16.1/src/lib.rs:1280:1
    |
1280 | pub struct ArrayBase<S, D>
    | ^^^^^^^^^^^^^^^^^^^^^^^^^^ this is the expected type
    |
   ::: ~/.cargo/registry/src/index.crates.io-.../ndarray-0.17.2/src/lib.rs:1295:1
    |
1295 | pub struct ArrayBase<S, D, A = <S as RawData>::Elem>
    | ---------------------------------------------------- this is the found type
```

「there are multiple different versions of crate `ndarray` in the dependency graph」がこの問題の名前です。**Java 版でいう依存衝突（jar hell）に当たる状態が、実行時の `NoSuchMethodError` ではなくコンパイルエラーで出る** ところが Rust の性格です。壊れたものが動いてしまうことはありませんが、そのぶん「どの版を使うか」を最初に決めておく必要があります。

このシリーズでは、linfa 0.8 系・ndarray 0.16・rand 0.8 でそろえます。`cargo tree -d`（重複した依存を表示する）で、意図しない同居がないか確かめられます。

### Java 版・Go 版との違い

| | 行列 | 連立方程式 | 版の管理 |
|---|---|---|---|
| Java 版 | `double[][]` を包む不変クラスを自作 | 自作（ガウスの消去法） | Maven の依存解決が 1 つの版に収束させる |
| Go 版 | gonum の `mat.Dense` | gonum の `mat.Solve` | モジュールのメジャー版がパスに入る（`gonum.org/v1/gonum`） |
| Rust 版 | ndarray の `Array2<f64>` | 自作（掃き出し法） | 版が違えば別の型。ライブラリが使う版に合わせる |

Rust だけ「行列はライブラリ、解く手続きは自作」という中間になります。ndarray は行列の積（`dot`）と転置（`t()`）を持っていますが、**連立方程式を解く関数を持っていません**。解く機能は `ndarray-linalg` にありますが、これは LAPACK（Fortran の数値計算ライブラリ）を要求し、Nix の開発環境に C・Fortran のツールチェインを足すことになります。第 7 章で必要なのは「4×4 程度の正方行列を 1 回解く」ことだけなので、そのぶんだけを自作します。

## 7.6 連立方程式を解く

### Red: 解けることを先に書く

`src/chapter07/matrix.rs` に、テストから書きます。

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use ndarray::array;

    #[test]
    fn 二元一次の連立方程式を解く() {
        let a = array![[2.0, 1.0], [1.0, 3.0]];
        let b = array![5.0, 10.0];

        let w = solve(&a, &b).unwrap();

        assert!((w[0] - 1.0).abs() < 1e-12, "w = {w}");
        assert!((w[1] - 3.0).abs() < 1e-12, "w = {w}");
    }
}
```

`2x + y = 5`、`x + 3y = 10` の解は `x = 1`、`y = 3` です。掃き出しの処理を書かずに「拡大係数行列を組み立てて、最後の列を返す」だけの状態で走らせると、右辺がそのまま返ってきます。

```text
thread 'chapter07::matrix::tests::二元一次の連立方程式を解く' panicked at src/chapter07/matrix.rs:92:9:
w = [5, 10]
```

浮動小数点数の比較に `assert_eq!` を使わないのは、第 1 章から変えていません。`assert!` の第 2 引数にメッセージを書けるので、`w = {w}` のように実際の値を出しておくと、Red の段階で何が返っているかが分かります。

### Green: 部分ピボット選択つきの掃き出し法

```rust
/// 部分ピボット選択つきのガウスの消去法で `a w = b` を解く。
///
/// ndarray 0.16 には連立方程式を解く関数が無い（`ndarray-linalg` は LAPACK を要求する）ので、
/// 正規方程式に必要なぶんだけを自作する。
pub fn solve(a: &Array2<f64>, b: &Array1<f64>) -> Result<Array1<f64>> {
    let size = a.nrows();

    if a.ncols() != size {
        return Err(Error::LengthMismatch {
            left: size,
            right: a.ncols(),
        });
    }

    if b.len() != size {
        return Err(Error::LengthMismatch {
            left: size,
            right: b.len(),
        });
    }

    // 係数行列と右辺を並べた拡大係数行列にしてから掃き出す
    let mut work = Array2::zeros((size, size + 1));
    work.slice_mut(ndarray::s![.., ..size]).assign(a);
    work.slice_mut(ndarray::s![.., size]).assign(b);

    for pivot in 0..size {
        let row = pivot_row(&work, pivot);

        if work[[row, pivot]].abs() < 1e-12 {
            return Err(Error::Library("解けない連立方程式です".to_string()));
        }

        swap_rows(&mut work, pivot, row);
        eliminate(&mut work, pivot);
    }

    Ok(work.column(size).to_owned())
}
```

`ndarray::s![.., ..size]` は **スライスのマクロ** です。`work` の「すべての行・先頭から size 列まで」という部分ビューを作り、`assign` で `a` の中身を書き込みます。Rust の借用規則は「同時に 2 つの可変参照を持てない」ので、行を入れ替えるときは注意が要ります。ここでは要素ごとに交換しました。

```rust
/// 2 つの行を入れ替える。行ごと入れ替える関数は無いので、要素を 1 つずつ入れ替える。
fn swap_rows(work: &mut Array2<f64>, left: usize, right: usize) {
    if left == right {
        return;
    }

    for column in 0..work.ncols() {
        work.swap([left, column], [right, column]);
    }
}
```

`work.swap` は `&mut self` を 1 回だけ借りるので、借用検査を通ります。「2 つの行を同時に可変で借りる」書き方（`multi_slice_mut`）もありますが、ここでは読みやすさを取りました。

掃き出しの本体は、ピボットの行を 1 に正規化してから、ほかの行のピボット列を 0 にします。

```rust
/// ピボットの行で、ほかの行のピボット列を 0 にする（ガウス・ジョルダンの掃き出し）。
fn eliminate(work: &mut Array2<f64>, pivot: usize) {
    let divisor = work[[pivot, pivot]];
    work.row_mut(pivot).mapv_inplace(|value| value / divisor);

    let pivot_row = work.row(pivot).to_owned();

    for (index, mut row) in work.axis_iter_mut(Axis(0)).enumerate() {
        if index == pivot {
            continue;
        }

        let factor = row[pivot];

        if factor == 0.0 {
            continue;
        }

        row.zip_mut_with(&pivot_row, |value, above| *value -= factor * above);
    }
}
```

ここでも所有権が顔を出します。`work.axis_iter_mut` で行を可変で回しているあいだ、`work` 自体は借りられません。そこで、ピボットの行だけ `to_owned()` で **複製してから** ループに入ります。Java 版・Go 版では「同じ配列を読みながら書く」だけで済んだところに、Rust では「複製する」という明示的な 1 行が必要になります。代わりに、うっかり書き換えた行を読んでしまう類のバグは起こりません。

### 三角測量: ピボットの入れ替えと、解けない場合

```rust
    #[test]
    fn 先頭のピボットが零でも入れ替えて解ける() {
        let a = array![[0.0, 1.0], [1.0, 0.0]];
        let b = array![2.0, 3.0];

        let w = solve(&a, &b).unwrap();

        assert!((w[0] - 3.0).abs() < 1e-12, "w = {w}");
        assert!((w[1] - 2.0).abs() < 1e-12, "w = {w}");
    }

    #[test]
    fn 解が定まらなければ失敗する() {
        let a = array![[1.0, 2.0], [2.0, 4.0]];
        let b = array![3.0, 6.0];

        assert_eq!(
            solve(&a, &b).unwrap_err().to_string(),
            "ライブラリが失敗しました: 解けない連立方程式です"
        );
    }
```

2 行目が 1 行目の 2 倍になっている行列は、解が 1 つに定まりません。掃き出しの途中でピボットが 0 になるので、そこで失敗を返します。**パニックさせない** のはこのシリーズの規律です（`panic!` はテストの中だけ）。

失敗の型は第 2 章の `Error` をそのまま使います。第 7 章のためだけに新しい `enum` を作ると、`?` で上へ返すたびに変換が要るからです。

```rust
use crate::chapter02::{Error, Result};
```

Go 版は `errors.New` で新しいエラーを作り、`errors.Is` で見分けました。Rust では `enum` の列挙子がそのまま見分けの手段になり、`match` の網羅性をコンパイラが検査します。

## 7.7 評価指標を計算する

回帰の良し悪しは、分類の正解率と同じようには測れません。3 つの指標を実装します。

- **MAE（平均絶対誤差）** — 誤差の絶対値の平均。単位が元の値と同じで読みやすい
- **RMSE（二乗平均平方根誤差）** — 誤差を 2 乗してから平均し、平方根を取る。大きな誤差を重く見る
- **R²（決定係数）** — 1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。1 に近いほどよい

テストから書きます。値の性質がそのままテストの名前になります。

```rust
    #[test]
    fn 平均絶対誤差は誤差の絶対値の平均になる() {
        let mae = mean_absolute_error(&[1.0, 2.0], &[2.0, 4.0]).unwrap();

        assert!((mae - 1.5).abs() < 1e-12, "MAE = {mae}");
    }

    #[test]
    fn 完全に当たれば決定係数は一になる() {
        let r2 = r2_score(&[1.0, 2.0, 3.0], &[1.0, 2.0, 3.0]).unwrap();

        assert!((r2 - 1.0).abs() < 1e-12, "R2 = {r2}");
    }

    #[test]
    fn 平均を答え続けると決定係数は零になる() {
        let r2 = r2_score(&[1.0, 2.0, 3.0], &[2.0, 2.0, 2.0]).unwrap();

        assert!(r2.abs() < 1e-12, "R2 = {r2}");
    }
```

「平均を答え続けると R² は 0 になる」は、R² の意味そのものです。**平均を答えるだけのモデルより、どれだけましか** を測る指標なので、それに並ぶと 0 になります。

実装では、3 つの指標が共通して使う「残差」を切り出します。

```rust
/// 実測値と予測値の差を返す。件数が違えば失敗する。
fn residuals(t: &[f64], y: &[f64]) -> Result<Vec<f64>> {
    if t.len() != y.len() {
        return Err(Error::LengthMismatch {
            left: t.len(),
            right: y.len(),
        });
    }

    Ok(t.iter()
        .zip(y)
        .map(|(actual, predicted)| actual - predicted)
        .collect())
}

/// 決定係数（R²）。1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。
pub fn r2_score(t: &[f64], y: &[f64]) -> Result<f64> {
    let residual = sum_of_squares(&residuals(t, y)?);
    let mean: f64 = t.iter().sum::<f64>() / count(t);
    let deviations: Vec<f64> = t.iter().map(|value| value - mean).collect();

    Ok(1.0 - residual / sum_of_squares(&deviations))
}
```

引数が `&[f64]`（スライスの借用）なのは第 1 章からの方針どおりです。評価指標は値を読むだけなので、所有権を受け取る必要がありません。呼び出し側は `Vec<f64>` をそのまま渡せます。

件数を `f64` にする箇所は、clippy の `cast_precision_loss` に引っかかります。件数が 2^53 を超えることは無いので、理由を書いて 1 か所に閉じ込めました。

```rust
/// 件数を f64 にする。評価指標の割り算に使う。
#[allow(clippy::cast_precision_loss)]
fn count(values: &[f64]) -> f64 {
    values.len() as f64
}
```

## 7.8 正規方程式で線形回帰を学習する

### 計画行列を作る

特徴量の先頭に 1 の列を足します。

```rust
/// 先頭に 1 の列を足した特徴量の行列（計画行列）を作る。1 の列の係数が切片になる。
pub fn design_matrix(x: &[Features]) -> Result<Array2<f64>> {
    let columns = x.first().map_or(0, |features| features.values.len()) + 1;
    let mut values = Vec::with_capacity(x.len() * columns);

    for features in x {
        values.push(1.0);
        values.extend(features.values.iter().copied());
    }

    Array2::from_shape_vec((x.len(), columns), values)
        .map_err(|error| Error::Library(error.to_string()))
}
```

ndarray の `Array2` は、**1 本の `Vec` と形（行数・列数）** から作ります。行ごとの `Vec<Vec<f64>>` にしないので、Java 版の `double[][]` より詰まった表現です。形と要素数が合わなければ `from_shape_vec` が失敗するので、`Result` に乗せて返します。

### 係数に列名を付ける

学習の結果は「切片」と「列名つきの係数」です。Java 版は `LinkedHashMap` で列の順を保ちましたが、Rust の `HashMap` は順を保ちません。第 2 章の `Features` と同じく、**列名と値を同じ順のベクタで持って対応させます**。

```rust
/// 学習した線形回帰のモデル。切片と、列名の付いた係数を持つ。
///
/// Java 版は `LinkedHashMap` で列の順を保ったが、Rust の `HashMap` は順を保たないので、
/// 列名と係数を同じ順のベクタで持って対応させる（`Features` と同じ持ち方）。
#[derive(Debug, Clone, PartialEq)]
pub struct LinearModel {
    pub intercept: f64,
    pub columns: Vec<String>,
    pub coefficients: Vec<f64>,
}
```

`new` で列名と係数の数が合うことを確かめてから作ります。数が合わない `LinearModel` は存在できません。

```rust
    /// 切片と、同じ順に並んだ列名・係数からモデルを作る。
    pub fn new(
        intercept: f64,
        columns: Vec<String>,
        coefficients: Vec<f64>,
    ) -> Result<LinearModel> {
        if columns.len() != coefficients.len() {
            return Err(Error::LengthMismatch {
                left: columns.len(),
                right: coefficients.len(),
            });
        }

        Ok(LinearModel {
            intercept,
            columns,
            coefficients,
        })
    }
```

予測は列名で対応させるので、渡す特徴量の並び順は問いません。テストで確かめます。

```rust
    #[test]
    fn 列の並びが違っても同じ予測になる() {
        let model =
            LinearModel::new(1.0, vec!["a".to_string(), "b".to_string()], vec![2.0, -1.0]).unwrap();

        assert!(
            (model
                .predict_one(&features(&["b", "a"], &[1.0, 1.0]))
                .unwrap()
                - 2.0)
                .abs()
                < 1e-9
        );
    }
```

### 学習

正規方程式をそのまま書き下します。

```rust
/// (Xᵀ X) w = Xᵀ t を解いて、切片と係数を求める。列名は先頭の行の特徴量から取る。
pub fn fit(x: &[Features], t: &[f64]) -> Result<LinearModel> {
    if x.len() != t.len() {
        return Err(Error::LengthMismatch {
            left: x.len(),
            right: t.len(),
        });
    }

    let Some(first) = x.first() else {
        return Err(Error::AllMissing("特徴量".to_string()));
    };

    let design = design_matrix(x)?;
    let target = Array1::from(t.to_vec());
    let transposed = design.t();
    let weights = solve(&transposed.dot(&design), &transposed.dot(&target))?;

    LinearModel::new(
        weights[0],
        first.columns.clone(),
        weights.slice(ndarray::s![1..]).to_vec(),
    )
}
```

`design.t()` は転置の **ビュー** で、データを複製しません。`dot` は行列の積です。`let Some(first) = x.first() else { ... }` は let-else 構文で、空のデータを早い段階で弾きます。Java 版の `x.getFirst()` は空なら例外を投げますが、Rust では `Option` が返るので、失敗として扱うことを書かないとコンパイルが通りません。

テストは、答えが分かっている直線と平面から始めます。

```rust
    #[test]
    fn 直線上の点から切片と係数を求める() {
        // y = 1 + 2x
        let x = vec![
            features(&["SNS1"], &[0.0]),
            features(&["SNS1"], &[1.0]),
            features(&["SNS1"], &[2.0]),
        ];
        let t = vec![1.0, 3.0, 5.0];

        let model = fit(&x, &t).unwrap();

        assert!(
            (model.intercept - 1.0).abs() < 1e-9,
            "切片 = {}",
            model.intercept
        );
        assert!((model.coefficient("SNS1").unwrap() - 2.0).abs() < 1e-9);
    }

    #[test]
    fn 二つの特徴量でも係数を求める() {
        // y = 3 + 2a - b
        ...
    }
```

三角測量です。1 つの特徴量で通ったあと、2 つの特徴量で `y = 3 + 2a - b` を当てさせます。ここで初めて「行列を解いている」ことが効きます。

## 7.9 外れ値の除去・分割・補完をまとめる

### 外れ値を取り除く

```rust
/// SNS2 がこの値を超え、かつ興行収入が `OUTLIER_SALES` 未満の映画を外れ値とする。
const OUTLIER_SNS2: f64 = 1000.0;
const OUTLIER_SALES: f64 = 8500.0;

/// 外れ値かどうかを判定する。
fn is_outlier(row: &Row) -> Result<bool> {
    Ok(number(row, "SNS2")? > OUTLIER_SNS2 && number(row, TARGET)? < OUTLIER_SALES)
}
```

境界の扱いをテストで固定します。「超える」「未満」なので、ちょうど 1000 や 8500 の行は残ります。

```rust
    #[test]
    fn 話題になったのに売れなかった映画を外れ値として除く() {
        let table = table(&[("1200", "8000"), ("1200", "9000"), ("800", "8000")]);

        let cleaned = remove_outliers(&table).unwrap();

        assert_eq!(cleaned.rows.len(), 2);
    }

    #[test]
    fn 境界の値は外れ値にしない() {
        let table = table(&[("1000", "8000"), ("1200", "8500")]);

        assert_eq!(remove_outliers(&table).unwrap().rows.len(), 2);
    }
```

学習データの実際の行はテストに書きません。架空の値で境界だけを確かめます。

### 前処理を 1 つの関数にする

第 2 章の `prepare_iris` と同じ形で、映画のデータ用の `prepare` を書きます。順番が大事です。**外れ値を除く → 分割する → 訓練データの平均値で両方を補完する**。テストデータの値を平均に混ぜると、本番では知り得ない情報が訓練に漏れます（リーク）。

```rust
/// 外れ値を除き、分割してから訓練データの平均値で両方の欠損値を補完する。
pub fn prepare(csv_file: &Path, test_size: f64, seed: u64) -> Result<TrainTestSplit<Features, f64>> {
    let table = remove_outliers(&Table::load(csv_file)?)?;
    let columns = feature_columns();

    let mut t = Vec::with_capacity(table.rows.len());

    for row in &table.rows {
        t.push(number(row, TARGET)?);
    }

    let split = split_train_test(&table.rows, &t, test_size, seed)?;
    let means = column_means(&split.x_train, &columns)?;

    Ok(TrainTestSplit {
        x_train: fill_missing(&split.x_train, &columns, &means)?,
        x_test: fill_missing(&split.x_test, &columns, &means)?,
        t_train: split.t_train,
        t_test: split.t_test,
    })
}
```

第 2 章の `TrainTestSplit<X, T>` は型引数を 2 つ取るので、**正解ラベルが文字列（分類）でも `f64`（回帰）でも同じ型を使えます**。第 2 章で `TrainTestSplit<Features, String>` だったものが、ここでは `TrainTestSplit<Features, f64>` になるだけです。Java 版のジェネリクスと同じ発想ですが、Rust では単相化（使った型ごとに機械語を作る）されるので、実行時の箱詰め（boxing）がありません。

## 7.10 linfa-linear に置き換える

### 最小二乗解になるのはどれか

linfa-linear には `LinearRegression`（最小二乗法）と `TweedieRegressor`（一般化線形モデル）があります。この章で突き合わせるのは前者です。Java 版の Tribuo では、`SLMTrainer`（縮小推定）や `LARSTrainer` など複数のトレーナーのうちどれが最小二乗解になるかを確かめる必要がありましたが、linfa-linear の `LinearRegression::default()` は素の最小二乗法なので、そのまま比べられます。

### 特徴量を linfa のデータセットにする

```rust
/// linfa-linear で学習し、自作と同じ `LinearModel` にして返す。
pub fn fit(x: &[Features], t: &[f64]) -> Result<LinearModel> {
    let Some(first) = x.first() else {
        return Err(Error::AllMissing("特徴量".to_string()));
    };

    let dataset = Dataset::new(records(x)?, Array1::from(t.to_vec()));
    let model = LinfaLinearRegression::default()
        .fit(&dataset)
        .map_err(|error| Error::Library(error.to_string()))?;

    LinearModel::new(
        model.intercept(),
        first.columns.clone(),
        model.params().to_vec(),
    )
}

/// 特徴量を linfa に渡す行列にする。切片は linfa が自分で足すので、1 の列は入れない。
fn records(x: &[Features]) -> Result<Array2<f64>> {
    ...
}
```

注意点が 2 つあります。

1. **計画行列を渡さない** — linfa は切片を自分で扱う（`with_intercept` の既定が true）ので、1 の列を足すと「同じ列が 2 本ある」状態になります
2. **戻り値を自作と同じ型にする** — `LinearModel` に詰め替えておくと、突き合わせのテストが「同じ型どうしの比較」になり、読みやすくなります

`use linfa::prelude::*;` を書いているのは、`fit` メソッドが `linfa::traits::Fit` トレイトにあるからです。Rust では **トレイトがスコープに入っていないとメソッドを呼べません**。Java の static import や Go のインターフェースとは違う、「使う側が取り込む」方式です。

### 突き合わせる

```rust
    #[test]
    fn 自作とlinfaの係数はほぼ一致する() {
        let x = vec![
            features(&["a", "b"], &[0.0, 0.0]),
            features(&["a", "b"], &[1.0, 0.0]),
            features(&["a", "b"], &[0.0, 1.0]),
            features(&["a", "b"], &[1.0, 2.0]),
        ];
        let t = vec![3.0, 5.0, 2.2, 3.1];

        let ours = super::super::linearregression::fit(&x, &t).unwrap();
        let theirs = fit(&x, &t).unwrap();

        assert!((ours.intercept - theirs.intercept).abs() < 1e-6);

        for column in ["a", "b"] {
            assert!(
                (ours.coefficient(column).unwrap() - theirs.coefficient(column).unwrap()).abs()
                    < 1e-6,
                "{column} の係数が違う"
            );
        }
    }
```

第 3 章の決定木では、自作と linfa の結果が深さ 3 以上で分かれました。**分割の候補が同点のときにどちらを選ぶか** が実装で違ったためです。線形回帰は解が一意に決まるので、こういう分岐がありません。違いは計算方法（こちらは掃き出し法、linfa は別の分解）から来る丸め誤差だけで、1e-6 の許容で一致します。

テストの名前に空白を入れられないので、`自作と linfa の…` ではなく `自作とlinfaの…` と書きます。Rust の識別子には日本語を使えますが、空白は使えません。

## 7.11 実データで学習・評価する

### 結果を表示する

`run` で、件数・外れ値・係数・評価指標を表示します。

```rust
/// 映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let csv_file = dataset::current().join("cinema.csv");
    let table = Table::load(&csv_file)?;
    let cleaned = cinema::remove_outliers(&table)?;
    let split = cinema::prepare(&csv_file, TEST_SIZE, SEED)?;

    let model = linearregression::fit(&split.x_train, &split.t_train)?;
    let library = linfareg::fit(&split.x_train, &split.t_train)?;
    let y = model.predict(&split.x_test)?;
    ...
}
```

実行します。

```bash
cargo run --bin chapters -- chapter07
```

```text
データ件数: 100
外れ値を除いた件数: 99
訓練データ: 79 件, テストデータ: 20 件
切片: 6118.29
係数: SNS1=1.1354, SNS2=0.5438, actor=0.2980, original=206.5224
linfa の切片: 6118.29, 係数: SNS1=1.1354, SNS2=0.5438, actor=0.2980, original=206.5224
テストデータの評価: R2=0.8068, MAE=305.15, RMSE=380.11
```

### 係数を読む

原作のある映画（`original` が 1）は、そうでない映画より興行収入が 206 ほど高い、と読めます。SNS1 は 1 単位あたり 1.14、SNS2 は 0.54。SNS1 のほうが興行収入への効き目が大きい指標です。

ただし、**係数の大きさをそのまま「重要さ」と読むことはできません**。特徴量の単位が違うからです。単位をそろえて比べる方法（標準化）は第 12 章で扱います。

### 評価指標を読む

R² が 0.8068 なので、興行収入のばらつきのうち 8 割ほどを説明できています。MAE は 305、RMSE は 380。RMSE が MAE より大きいのは、大きく外した映画が少数あることを意味します（RMSE は大きな誤差を重く見るため）。

### ほかの言語版と数値が一致しない

Java 版・Go 版の第 7 章も同じデータ・同じ `testSize` 0.2・同じシード 0 ですが、係数も評価指標も一致しません。**訓練データとテストデータの分け方が違う** からです。第 2 章で見たとおり、Rust の `rand` の `StdRng` は Java の `java.util.Random` とも Go の `math/rand` とも別の乱数列を作ります。

同じことを確かめるには、数値そのものではなく **関係** をテストします。

```rust
#[test]
fn 自作とlinfaの係数は実データでも一致する() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let split = cinema::prepare(&csv_file, TEST_SIZE, SEED).expect("前処理できること");
    let ours = linearregression::fit(&split.x_train, &split.t_train).expect("学習できること");
    let theirs = linfareg::fit(&split.x_train, &split.t_train).expect("linfa で学習できること");

    assert!(
        (ours.intercept - theirs.intercept).abs() < 1e-6,
        "切片 {} と {}",
        ours.intercept,
        theirs.intercept
    );

    for column in cinema::FEATURES {
        let ours = ours.coefficient(column).expect("係数があること");
        let theirs = theirs.coefficient(column).expect("係数があること");

        assert!(
            (ours - theirs).abs() < 1e-6,
            "{column} の係数 {ours} と {theirs}"
        );
    }
}
```

「自作と linfa が一致すること」は言語をまたいでも成り立つ性質です。いっぽう「R² が 0.8068 であること」は、この言語・このシードでの実測値です。後者も固定しますが、ほかの言語版と比べるものではないとコメントに書いておきます。

実データのテストは、第 1 章と同じく **データが無ければ早期に戻る** 形にします。Rust の標準のテストにスキップの仕組みが無いためです。

```rust
/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
fn data_file() -> Option<PathBuf> {
    let csv_file = dataset::current().join("cinema.csv");

    if csv_file.exists() {
        Some(csv_file)
    } else {
        eprintln!("学習データ cinema.csv が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}
```

## 7.12 品質チェック

```bash
cargo fmt --check
cargo clippy --all-targets -- -D warnings
cargo test
cargo llvm-cov --summary-only
```

この章で clippy に言われたのは `cast_precision_loss`（`usize` を `f64` にすると精度が落ちうる）だけでした。件数を割り算に使う場所なので、理由を書いて `#[allow]` を付けています。抑えるときは **いちばん狭い範囲に** 付けるのが原則です。関数の 1 つ、できれば式 1 つに閉じ込めます。

`cargo test` は、学習データがある状態と無い状態の両方で通ることを確かめます。

```bash
ML_DATA_DIR=/nonexistent cargo test
```

リポジトリのルートからは `npx gulp apps:check:rust` で 4 つをまとめて実行できます。

## 7.13 まとめ

この章では、正規方程式による線形回帰を TDD で実装し、実データで R²=0.8068 を得ました。Rust に固有の論点は次のとおりです。

1. **クレートの版が型を分ける** — ndarray を 0.17 にすると linfa の 0.16 とは別の型になり、「multiple different versions of crate」でコンパイルが止まる。Java の依存衝突に当たる問題が実行時ではなくコンパイル時に出る
2. **ライブラリの守備範囲を見極める** — 行列の積・転置は ndarray にあるが、連立方程式を解く関数は無い。LAPACK を要求する `ndarray-linalg` を入れるより、必要なぶんだけ掃き出し法を書くほうが軽い
3. **借用が計算の書き方を変える** — 掃き出しでは、ピボットの行を `to_owned()` で複製してから可変のループに入る。「読みながら書く」ができない代わりに、読んでいるものが書き換わるバグが起きない
4. **トレイトはスコープに入れて使う** — `linfa::prelude::*` を書かないと `fit` メソッドが見えない
5. **型引数は分類と回帰で使い回せる** — `TrainTestSplit<Features, String>` と `TrainTestSplit<Features, f64>` は同じ型の別の使い方。単相化されるので箱詰めのコストが無い

**TODO リスト（この章の完了時点）**:

- [x] 連立方程式を解く
- [x] 評価指標を計算する（MAE・RMSE・R²）
- [x] 正規方程式で線形回帰を学習する
- [x] 学習したモデルで予測する
- [x] 外れ値を取り除く
- [x] 外れ値の除去・分割・補完をまとめる
- [x] linfa-linear と結果が一致することを確かめる
- [x] 実データで学習・評価して表示する

次の章では、分類に戻ってタイタニックの生存予測に取り組みます。欠損値の補完とダミー変数化を **前処理のパイプライン** にまとめ、学習済みのモデルをファイルに保存します。そこでは linfa に無い前処理を自作することと、**trait object を保存できない** という Rust 固有の制約が主題になります。
