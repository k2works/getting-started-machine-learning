//! 連立方程式を解く。行列そのものは ndarray の `Array2` を使い、解く手続きだけを自作する。

use ndarray::{Array1, Array2, Axis};

use crate::chapter02::{Error, Result};

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

/// 指定した列で絶対値が最大の行を、対角より下から選ぶ。
fn pivot_row(work: &Array2<f64>, pivot: usize) -> usize {
    (pivot..work.nrows())
        .max_by(|left, right| {
            work[[*left, pivot]]
                .abs()
                .total_cmp(&work[[*right, pivot]].abs())
        })
        .unwrap_or(pivot)
}

/// 2 つの行を入れ替える。行ごと入れ替える関数は無いので、要素を 1 つずつ入れ替える。
fn swap_rows(work: &mut Array2<f64>, left: usize, right: usize) {
    if left == right {
        return;
    }

    for column in 0..work.ncols() {
        work.swap([left, column], [right, column]);
    }
}

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

    #[test]
    fn 正方でない行列は解けない() {
        let a = array![[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]];
        let b = array![1.0, 2.0];

        assert_eq!(
            solve(&a, &b).unwrap_err().to_string(),
            "件数が違います: 2 と 3"
        );
    }

    #[test]
    fn 右辺の件数が合わなければ解けない() {
        let a = array![[2.0, 1.0], [1.0, 3.0]];
        let b = array![5.0];

        assert_eq!(
            solve(&a, &b).unwrap_err().to_string(),
            "件数が違います: 2 と 1"
        );
    }
}
