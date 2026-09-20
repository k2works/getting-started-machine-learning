---
type: Article
title: "第 14 章: K-means によるクラスタリング"
description: "正解ラベルの無いデータを K-means でグループに分け、エルボー法でクラスタ数を選ぶ。自作の実装を linfa-clustering と突き合わせ、局所解と乱数のクレートの版の問題を扱う。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-21T00:20:00Z }
---

# 第 14 章: K-means によるクラスタリング

## 14.1 はじめに

前章の主成分分析に続いて、**教師なし学習**の 2 つめです。今回は K-means でデータをグループ（クラスタ）に分けます。

ここまでの章では、データに必ず正解ラベルがありました。きのこ派かたけのこ派か、アヤメの種類、生存したかどうか、興行収入。正解があるから「当たった・外れた」を測れました。**クラスタリングには正解がありません。** 似たものどうしを集めるだけで、その集まりに意味があるかどうかは、人間が後から解釈します。

題材は [Python 版の第 14 章](../python/14-k-means-clustering.md) と同じ卸売業者の顧客データ（`Wholesale.csv`）です。440 件の顧客が、6 種類の商品にいくら使ったかという記録から、顧客をいくつかのグループに分けます。

Rust 版では、自作の K-means を作ってから **linfa-clustering の `KMeans`** と突き合わせます（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。この章では、ほかの言語版でも扱った「局所解」の問題に加えて、Rust 固有の論点が 1 つ出てきます。**乱数のクレートの版**です。

## 14.2 K-means の仕組み

K-means は、次の 2 つを繰り返すだけの単純なアルゴリズムです。

1. **割り当て** — 各点を、いちばん近い中心のクラスタに入れる
2. **更新** — クラスタごとに、集まった点の平均を新しい中心にする

中心が動かなくなったら終わりです。最初の中心（初期中心）は適当に選びます。

```text
初期中心をk個選ぶ
  ↓
各点を最も近い中心に割り当てる  ←─┐
  ↓                              │
クラスタごとの平均を新しい中心に  ─┘（中心が変わらなくなるまで）
  ↓
終わり
```

結果の良し悪しは **SSE（誤差平方和、Sum of Squared Errors）** で測ります。各点から、自分が属するクラスタの中心までの距離の 2 乗を全部足したものです。小さいほど「まとまっている」ことになります。

ただし SSE には落とし穴があります。**クラスタ数を増やせば必ず小さくなる**（極端には、点の数だけクラスタを作れば SSE は 0）ので、「SSE が最小のクラスタ数」を選んでも意味がありません。そこで使うのが**エルボー法**です。クラスタ数を増やしながら SSE を並べ、減り方が緩くなる「ひじ」のあたりを選びます。

## 14.3 題材とデータ

`Wholesale.csv` は 440 件の顧客の記録です。この章で使うのは、支出額の 6 列だけです。

| 列 | 意味 |
|----|------|
| Fresh | 生鮮食品 |
| Milk | 乳製品 |
| Grocery | 食料雑貨 |
| Frozen | 冷凍食品 |
| Detergents_Paper | 洗剤・紙類 |
| Delicassen | 惣菜 |

金額の単位はどの列も同じですが、列によって桁が違います（`Fresh` は数万、`Delicassen` は数千が多い）。距離を測るアルゴリズムなので、**標準化しないと、値が大きい列だけで距離が決まってしまいます**。第 9 章で作った標準化をそのまま使います。

## 14.4 TODO リストの作成

```markdown
- [ ] 支出額の 6 列を読み込む
- [ ] 列ごとに標準化する
- [ ] 各点を最も近い中心に割り当てる
- [ ] クラスタごとの平均を新しい中心にする
- [ ] SSE を計算する
- [ ] 中心が変わらなくなるまで繰り返す
- [ ] 初期中心をシードで選ぶ
- [ ] 初期中心を何通りか試して、SSE が最小の結果を選ぶ
- [ ] エルボー法でクラスタ数ごとの SSE を並べる
- [ ] linfa-clustering の KMeans と突き合わせる
- [ ] 実データでクラスタごとの特徴をまとめる
```

## 14.5 点の集まりを行列で表す

第 13 章と同じく、点の集まりは `ndarray` の `Array2<f64>`（1 件を 1 行とする行列）で表します。

```rust
/// K-means の結果。
#[derive(Debug, Clone, PartialEq)]
pub struct KMeansResult {
    /// 点ごとのクラスタ番号。
    pub labels: Vec<usize>,
    /// クラスタの中心を 1 行に 1 つずつ並べた行列。
    pub centers: Array2<f64>,
    /// 誤差平方和。
    pub sse: f64,
}
```

第 9 章の `Features`（列名と値）ではなく行列にするのは、linfa に渡すためと、行ごとの距離計算が素直に書けるためです。読み込みと標準化のところで変換します。

```rust
/// 第 9 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 行とする行列にする。
pub fn standardize(x: &[Features]) -> Result<Array2<f64>> {
    let standardized = Standardizer::fit(x)?.transform(x)?;
    let columns = standardized
        .first()
        .map_or(0, |features| features.values.len());
    let values: Vec<f64> = standardized
        .iter()
        .flat_map(|features| features.values.clone())
        .collect();

    Array2::from_shape_vec((standardized.len(), columns), values)
        .map_err(|error| Error::from(chapter02::Error::Library(error.to_string())))
}
```

`flat_map` で行ごとの値を 1 本のベクタにつなげ、`from_shape_vec` で形を与えます。**`Array2` は内部的に 1 次元のベクタ**なので、この変換に複製以上の費用はかかりません。

## 14.6 割り当てと更新を書く

### 各点を最も近い中心に割り当てる

```rust
/// 2 点間の距離の 2 乗。
pub fn squared_distance(a: &ArrayView1<f64>, b: &ArrayView1<f64>) -> f64 {
    a.iter()
        .zip(b)
        .map(|(left, right)| (left - right) * (left - right))
        .sum()
}

/// 各点を、最も近い中心のクラスタ番号に割り当てる。
pub fn assign_clusters(points: &Array2<f64>, centers: &Array2<f64>) -> Vec<usize> {
    points
        .rows()
        .into_iter()
        .map(|point| {
            let mut nearest = 0;
            // 中心ごとの距離は 1 回だけ求める。毎回求め直すと、点と中心の数だけ無駄が増える
            let mut shortest = squared_distance(&point, &centers.row(0));

            for cluster in 1..centers.nrows() {
                let distance = squared_distance(&point, &centers.row(cluster));

                if distance < shortest {
                    shortest = distance;
                    nearest = cluster;
                }
            }

            nearest
        })
        .collect()
}
```

平方根を取らないのがポイントです。距離の**大小を比べるだけ**なら 2 乗のままで足ります。SSE の定義も 2 乗和なので、最後まで平方根が出てきません。

引数の `&ArrayView1<f64>` は「行への参照」です。`points.rows()` が返すのはこの型で、**元の行列のデータを借りているだけ**なので複製されません。`Vec<Vec<f64>>` でデータを持っていたら、行を取り出すたびに複製するか、参照のライフタイムを気にすることになります。ndarray の view はその面倒を引き受けてくれます。

`min_by` を使わずに手で書いているのは、第 3 章と同じ理由です。`f64` は `Ord` を実装していないので `min_by_key` が使えず、`min_by(partial_cmp)` は同点のときに**最後**の要素を返します。「同点なら先のクラスタ」という挙動をほかの言語版とそろえるために、厳密な不等号で書きました。

### クラスタごとの平均を新しい中心にする

```rust
/// クラスタごとに、割り当てられた点の平均を新しい中心にする。
/// 点が 1 つも割り当てられなかったクラスタは、前の中心をそのまま使う。
pub fn update_centers(...) -> Array2<f64> { ... }
```

**点が 1 つも割り当てられなかったクラスタ**の扱いが要ります。平均を求めようとすると 0 で割ることになるので、前の中心をそのまま残します（scikit-learn は別の点を選び直しますが、ここでは単純な方を採りました）。

### 中心が変わらなくなるまで繰り返す

```rust
/// 中心が変わらなくなるか、更新の回数が max_iterations に達するまで、割り当てと中心の更新を繰り返す。
pub fn fit(
    points: &Array2<f64>,
    initial_centers: &Array2<f64>,
    max_iterations: usize,
) -> KMeansResult {
    let mut centers = initial_centers.clone();

    for _ in 0..max_iterations {
        let next = update_centers(points, &assign_clusters(points, &centers), &centers);

        if next == centers {
            break;
        }

        centers = next;
    }

    let labels = assign_clusters(points, &centers);
    let sse = sum_of_squared_errors(points, &labels, &centers);

    KMeansResult { labels, centers, sse }
}
```

`next == centers` で行列どうしを比べています。`Array2<f64>` は `PartialEq` を実装しているので、要素がすべて等しいかを比べられます。**浮動小数点の厳密な比較**ですが、ここでは正しい判断です。同じ入力から同じ平均を計算しているので、変化が無ければビット単位で同じ値になります（許容誤差を入れると、微小な振動で止まらなくなる方が困ります）。

上限 `max_iterations` を 300 にしているのは scikit-learn の既定に合わせたものです。中心が振動して止まらない場合の保険で、実データでは数十回で収束します。

## 14.7 初期中心と局所解

### シードで初期中心を選ぶ

```rust
/// シード付きの乱数で点を並べ替え、先頭から n_clusters 個を初期中心にする。
pub fn choose_initial_centers(
    points: &Array2<f64>,
    n_clusters: usize,
    seed: u64,
) -> Result<Array2<f64>> {
    if n_clusters == 0 || n_clusters > points.nrows() {
        return Err(Error::ClusterCount {
            requested: n_clusters,
            points: points.nrows(),
        });
    }

    let indices: Vec<usize> = (0..points.nrows()).collect();
    let mut centers = Array2::zeros((n_clusters, points.ncols()));

    for (cluster, index) in shuffle(&indices, seed).iter().take(n_clusters).enumerate() {
        centers.row_mut(cluster).assign(&points.row(*index));
    }

    Ok(centers)
}
```

第 2 章で作った `shuffle`（Fisher-Yates）をそのまま使います。点そのものではなく**添字を並べ替えて**先頭から取るので、点の複製は選んだぶんだけで済みます。

### 局所解に止まる

K-means は**初期中心によって結果が変わります**。運が悪いと、明らかに良くない分け方のまま中心が動かなくなります。これを局所解と言います。

テストで再現できます。1 次元に 3 組の点を並べ、3 つに分けます。

```rust
/// 1 次元に 3 組並べた点。3 つに分けるなら SSE は 0.5 × 3 = 1.5 が最小。
fn three_pairs() -> Array2<f64> {
    array![[0.0], [1.0], [10.0], [11.0], [20.0], [21.0]]
}

#[test]
fn 初期中心によっては局所解に止まる() {
    // 左の組から 2 点・中央の組から 1 点を初期中心にすると、右の 4 点が 1 つにまとまる
    let stuck = fit(&three_pairs(), &array![[0.0], [1.0], [10.0]], 300);
    let best = fit(&three_pairs(), &array![[0.0], [10.0], [20.0]], 300);

    assert!((best.sse - 1.5).abs() < 1e-12, "{best:?}");
    assert!(stuck.sse > best.sse, "{stuck:?}");
}
```

初期中心を左に寄せると、右の 4 点が 1 つのクラスタにまとまってしまい、SSE が大きいまま止まります。**アルゴリズムは正しく動いているのに、答えが悪い**という状態です。

対策は単純で、**初期中心を何通りか試して、SSE がいちばん小さい結果を採る**ことです。

```rust
/// シードを 1 ずつずらして初期中心を n_init 通り選び、SSE が最小の結果を返す。
pub fn fit_with_restarts(
    points: &Array2<f64>,
    n_clusters: usize,
    seed: u64,
    n_init: usize,
) -> Result<KMeansResult> {
    let mut best: Option<KMeansResult> = None;

    for offset in 0..n_init.max(1) {
        let centers = choose_initial_centers(points, n_clusters, seed + offset as u64)?;
        let result = fit(points, &centers, DEFAULT_MAX_ITERATIONS);

        if best.as_ref().is_none_or(|best| result.sse < best.sse) {
            best = Some(result);
        }
    }

    best.ok_or(Error::Empty)
}
```

```rust
#[test]
fn 何通りか試せば局所解から抜け出せる() {
    let result = fit_with_restarts(&three_pairs(), 3, 0, DEFAULT_N_INIT).unwrap();

    assert!((result.sse - 1.5).abs() < 1e-12, "{result:?}");
}
```

既定の 10 通り（`DEFAULT_N_INIT`、scikit-learn の `n_init` と同じ）で最適解に届きました。

`best.as_ref().is_none_or(...)` は「まだ何も無いか、今回のほうが良ければ」という意味です。`Option` のメソッドで「無い場合も真」を表せるので、`if best.is_none() || result.sse < best.unwrap().sse` のような二重の条件を書かずに済みます。

## 14.8 linfa-clustering と突き合わせる

### 乱数のクレートの版がそろっていないと渡せない

```rust
/// k-means++ で初期中心を選んで学習する。初期中心は n_runs 通り試す。
///
/// 乱数は `Xoshiro256Plus` を渡す。linfa 0.8 は rand 0.8 の `Rng` を求めるので、
/// rand 0.9 以降の乱数は「同じ名前の別の型」になって渡せない。
pub fn fit(
    points: &Array2<f64>,
    n_clusters: usize,
    seed: u64,
    n_runs: usize,
) -> Result<LinfaKMeans> {
    let rng = Xoshiro256Plus::seed_from_u64(seed);
    let dataset = DatasetBase::from(points.clone());
    let model = KMeans::params_with_rng(n_clusters, rng)
        .max_n_iterations(DEFAULT_MAX_ITERATIONS as u64)
        .n_runs(n_runs.max(1))
        .fit(&dataset)
        .map_err(|error| Error::from(Chapter02Error::Library(error.to_string())))?;
    ...
}
```

この章で Rust 固有の問題が出ます。`KMeans::params_with_rng` は乱数生成器を受け取りますが、**渡せるのは linfa が依存している rand 0.8 系の型だけ**です。最新の rand 0.9 の `SmallRng` を渡すと、こうなります。

```text
error[E0277]: the trait bound `SmallRng: rand::rng::Rng` is not satisfied
   = note: two types coming from two different versions of the same crate are different types
           even if they look the same
   = help: you can use `cargo tree` to explore your dependency tree
```

「見た目が同じでも、別の版のクレートから来た型は別の型」。第 5 章・第 7 章でも同じ問題（ndarray 0.16 と 0.17）に出会いました。Rust ではクレートの版が型の同一性の一部なので、**ライブラリに値を渡すときは、そのライブラリが依存している版にそろえる**必要があります。

ここでは `rand_xoshiro` 0.6 の `Xoshiro256Plus` を使いました。linfa-clustering が依存している版そのものです。Java 版・C# 版・Go 版には無い手間で、そのかわり「動くはずなのに動かない」という実行時の事故は起きません。

### 同じ定義で SSE を比べる

linfa の `KMeans` は SSE をそのまま返さないので、**自作と同じ関数で測り直します**。

```rust
let centers = model.centroids().to_owned();
let labels = kmeans::assign_clusters(points, &centers);

Ok(LinfaKMeans {
    sse: kmeans::sum_of_squared_errors(points, &labels, &centers),
    centers,
})
```

こうすると「中心の選び方の違い」だけを比べられます。ライブラリと突き合わせるときは、**指標の定義をこちらでそろえる**のが確実です（第 11 章・第 12 章でも同じことをしました）。

## 14.9 実データでクラスタリングする

### エルボー法

クラスタ数を 1 から 10 まで動かして SSE を並べます。自作と linfa を並べて表示します。

```text
$ cargo run --bin chapters -- chapter14
データ件数: 440（支出額 6 列）
クラスタ数ごとの SSE（初期中心 10 通りの最小値）:
クラスタ数	自作	linfa（k-means++）
1	2640.00	2640.00
2	1954.65	1954.18
3	1614.52	1614.52
4	1334.36	1325.96
5	1085.27	1062.63
6	983.55	924.53
7	890.81	828.11
8	785.54	750.25
9	670.90	680.12
10	618.17	616.96
```

読み取れることが 3 つあります。

1. **クラスタ数 1 の SSE 2640.00 は両者で完全に一致します。** クラスタが 1 つなら中心は全体の平均で、乱数も初期中心も関係ないからです。しかも 2640 = 440 × 6 で、これは偶然ではありません。**母標準偏差で標準化したデータの分散は列ごとに 1** なので、SSE は「件数 × 列数」になります。標準化が効いていることの確認になります
2. **クラスタ数 3 でも一致します**（1614.52）。データの構造がはっきりしていて、どちらの初期化でも同じ分け方に落ち着いたということです
3. **クラスタ数 4 以上では linfa のほうが小さいことが多い**（5 で 1085.27 対 1062.63、7 で 890.81 対 828.11）。linfa は **k-means++** で初期中心を選ぶからです。k-means++ は「既に選んだ中心から遠い点ほど選ばれやすい」方式で、こちらのランダムな選び方より良い初期値を引き当てます。ただし絶対ではなく、**クラスタ数 9 では自作のほうが小さい**（670.90 対 680.12）という逆転も起きています

減り方を見ると、5 あたりから緩やかになります。ここでは 5 を選びました。

### クラスタごとの特徴

```text
クラスタ数 5 のクラスタごとの件数と平均支出額:
クラスタ	件数	Fresh	Milk	Grocery	Frozen	Detergents_Paper	Delicassen
1	265	8909	2967	3804	2248	989	962
2	96	5509	10556	16478	1420	7199	1659
3	65	31117	4260	5374	7225	849	2286
0	10	15965	34708	48537	3055	24875	2943
4	4	52022	31696	18491	29826	2699	19656
```

平均は**元の金額の単位に戻して**表示しています。標準化した値のままでは「0.42」のような数字が並ぶだけで、解釈できないからです。

ここからは人間の仕事です。

- **クラスタ 1（265 件）** — すべての列で金額が小さい。小規模な顧客
- **クラスタ 2（96 件）** — `Grocery` 16478 と `Detergents_Paper` 7199 が目立つ。食料雑貨と日用品を扱う小売業でしょうか
- **クラスタ 3（65 件）** — `Fresh` 31117 が突出。生鮮食品中心の業態（レストランなど）
- **クラスタ 0（10 件）** — `Grocery` 48537、`Detergents_Paper` 24875。クラスタ 2 の大規模版
- **クラスタ 4（4 件）** — どの列も桁違いに大きい。数件の超大口顧客

**正解ラベルはありません。** これらの解釈が正しいかどうかは、データの背景を知っている人が判断することです。クラスタリングが返すのは「似たものの集まり」までで、**名前を付けるのは人間**です。

### 実データのテスト

```rust
#[test]
fn 実データで自作とlinfaの誤差平方和は同じ桁になる() { ... }
```

実データのテストでは、**自作と linfa の SSE が厳密に一致することを求めていません**。初期化の方式が違うので、一致するほうが偶然です。「同じ桁に収まること」「クラスタ数を増やすと SSE が減ること」といった、**アルゴリズムとして守られるべき性質**を確かめます。

第 3 章（決定木）では深さ 1・2 で予測が完全一致しましたが、そちらは決定的なアルゴリズムでした。**乱数が入るアルゴリズムでは、テストの書き方を変える**必要があります。

## 14.10 Notebook による探索と可視化

クラスタリングは散布図で見ると分かりやすくなります。第 13 章の主成分分析で 2 次元に落としてから、クラスタごとに色を変えて描く方法が定番です。

Rust 版では可視化の節を設けません。[Python 版の第 14 章](../python/14-k-means-clustering.md) と [Kotlin 版](../kotlin/14-k-means-clustering.md) の可視化の節を参照してください。

## 14.11 まとめ

この章では、K-means を自作し、linfa-clustering と突き合わせました。

1. **点の集まりは `Array2<f64>` で表す** — `rows()` が返す view は元のデータを借りているだけなので、行を取り出しても複製されない
2. **`f64` の比較は自分で書く** — `min_by_key` は使えず、`min_by` は同点で最後を返す。ほかの言語版と挙動をそろえるには厳密な不等号で書く
3. **中心の収束は厳密な比較でよい** — 同じ計算から同じ値が出るので、許容誤差を入れるとかえって止まらなくなる
4. **局所解は初期中心を変えて避ける** — テストで再現し、10 通り試せば抜け出せることを確かめた
5. **乱数のクレートの版をそろえる** — linfa に RNG を渡すには rand 0.8 系（`rand_xoshiro` 0.6）が要る。「見た目が同じでも別の版の型は別の型」は Rust 固有の論点
6. **突き合わせは指標の定義をこちらでそろえる** — linfa の中心を受け取って、自作と同じ SSE の式で測り直した。k-means++ の linfa が有利なことが多いが、クラスタ数 9 では自作が勝った
7. **正解が無いので、テストは性質を確かめる** — 厳密な値ではなく「クラスタ数を増やせば SSE が減る」「同じ桁に収まる」を確かめる

**TODO リスト（この章の完了時点）**: すべて完了しました。

次の章はシリーズの最後です。第 7・8 章で作ったモデルを axum の予測 API として公開し、層の分離・エラーの変換・状態の共有を扱います。
