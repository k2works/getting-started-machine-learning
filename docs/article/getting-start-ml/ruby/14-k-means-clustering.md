---
type: Article
title: "第 14 章: K-means によるクラスタリング"
description: "正解ラベルの無いデータを K-means でグループに分け、エルボー法でクラスタ数を選ぶ。Numo のブロードキャストで距離をまとめて求める K-means を Ruby の TDD で自作し、SSE も n_init も持たない Rumale の KMeans を同じ式で測り直して突き合わせる。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:53:13Z }
---

# 第 14 章: K-means によるクラスタリング

## 14.1 はじめに

前章の主成分分析に続いて、**教師なし学習** の 2 つめです。今回は K-means でデータをグループ（クラスタ）に分けます。

ここまでの章では、データに必ず正解ラベルがありました。きのこ派かたけのこ派か、アヤメの種類、生存したかどうか、興行収入。正解があるから「当たった・外れた」を測れました。**クラスタリングには正解がありません。** 似たものどうしを集めるだけで、その集まりに意味があるかどうかは、人間が後から解釈します。

題材は [Python 版の第 14 章](../python/14-k-means-clustering.md) と同じ卸売業者の顧客データ（`Wholesale.csv`）です。440 件の顧客が、6 種類の商品にいくら使ったかという記録から、顧客をいくつかのグループに分けます。

Ruby 版では、自作の K-means を作ってから **Rumale の `Clustering::KMeans`** と突き合わせます（[ADR 010](../../../adr/010-ruby-ml-libraries.md)）。[Rust 版](../rust/14-k-means-clustering.md) と対比しながら、次の 3 つの論点を拾います。

- **距離をまとめて求める**。Rust 版は点ごと・中心ごとの二重ループで距離を求めました。Ruby 版は Numo のブロードキャストで「点の数 × 中心の数」の距離の行列を 1 式で作ります
- **ライブラリが持っていないものは、こちらで用意する**。Rumale の `KMeans` は SSE（scikit-learn の `inertia_`）も、初期中心を何通りも試す仕組み（`n_init`）も持ちません。どちらも自作の関数で補って比べます
- **乱数を使う章では、言語版の数値は一致しない**。前章と違い、初期中心を乱数で選ぶので、Rust 版とは SSE もクラスタの分かれ方も変わります。それでも一致する部分がどこかを確かめます

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

ただし SSE には落とし穴があります。**クラスタ数を増やせば必ず小さくなる**（極端には、点の数だけクラスタを作れば SSE は 0）ので、「SSE が最小のクラスタ数」を選んでも意味がありません。そこで使うのが **エルボー法** です。クラスタ数を増やしながら SSE を並べ、減り方が緩くなる「ひじ」のあたりを選びます。

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

`Channel`（販売チャネル）と `Region`（地域）は区分を表す番号で、金額ではないので使いません。金額の単位はどの列も同じですが、列によって桁が違います。距離を測るアルゴリズムなので、**標準化しないと、値が大きい列だけで距離が決まってしまいます**。第 9 章で作った標準化をそのまま使います。

## 14.4 TODO リストの作成

**TODO リスト**:

- [ ] 支出額の 6 列を読み込む
- [ ] 列ごとに標準化する
- [ ] 各点を最も近い中心に割り当てる
- [ ] クラスタごとの平均を新しい中心にする
- [ ] SSE を計算する
- [ ] 中心が変わらなくなるまで繰り返す
- [ ] 初期中心をシードで選ぶ
- [ ] 初期中心を何通りか試して、SSE が最小の結果を選ぶ
- [ ] エルボー法でクラスタ数ごとの SSE を並べる
- [ ] Rumale の KMeans と突き合わせる
- [ ] 実データでクラスタごとの特徴をまとめる

この章のコードは `lib/getting_started_ml/chapter14/` に置きます。

| ファイル | 役割 |
|---------|------|
| `kmeans.rb` | 割り当て・更新・SSE・繰り返し・初期中心・何通りかの試行 |
| `spending.rb` | 支出額の読み込み・標準化・クラスタごとの要約 |
| `rumale_kmeans.rb` | Rumale の KMeans の呼び出しと、SSE の測り直し |

点の集まりは、第 13 章と同じく 1 件を 1 行とする `Numo::DFloat` の行列で表します。結果は `Data.define` で名前を付けます。

```ruby
# K-means の結果。点ごとのクラスタ番号、中心を 1 行に 1 つずつ並べた行列、誤差平方和。
KMeansResult = Data.define(:labels, :centers, :sse)
```

## 14.5 割り当てと更新を書く

### 各点を最も近い中心に割り当てる

テストは Rust 版と同じく、原点のまわりと (10, 10) のまわりに分かれた 4 点で書きます。

```ruby
def test_各点は最も近い中心に割り当てられる
  assert_equal [0, 0, 1, 1], C.assign_clusters(points, matrix([[0.0, 0.0], [10.0, 10.0]]))
end

def test_距離が同じなら番号の小さいクラスタに割り当てる
  assert_equal [0], C.assign_clusters(matrix([[0.0, 0.0]]), matrix([[1.0, 0.0], [-1.0, 0.0]]))
end
```

最初の実行は、`Chapter14` がまだ無いので `NameError`（`uninitialized constant GettingStartedMl::Chapter14`）で落ちました。実装です。

```ruby
# 各点を、最も近い中心のクラスタ番号に割り当てる。距離が同じなら番号の小さいクラスタにする。
# 点と中心の組ごとの距離の 2 乗を、ブロードキャストで「点の数 × 中心の数」の行列にまとめて求める。
def assign_clusters(points, centers)
  distances = ((points.expand_dims(1) - centers.expand_dims(0))**2).sum(axis: 2)

  distances.to_a.map { |row| row.each_index.min_by { |cluster| [row[cluster], cluster] } }
end
```

`points.expand_dims(1)` は「点の数 × 1 × 列の数」、`centers.expand_dims(0)` は「1 × 中心の数 × 列の数」の 3 次元の配列です。引き算をすると、長さ 1 の次元が相手に合わせて広がり、「点の数 × 中心の数 × 列の数」の差になります。2 乗して列の方向（`axis: 2`）に足せば、すべての組の距離の 2 乗が一度に求まります。Rust 版の「点ごとに、中心ごとに `squared_distance` を呼ぶ」二重ループが、この 1 式に当たります。

平方根を取らないのは Rust 版と同じです。距離の **大小を比べるだけ** なら 2 乗のままで足り、SSE の定義も 2 乗和です。

同点の扱いは、`min_by` に `[距離, 番号]` の組を渡して決めています。Rust 版は `f64` が `Ord` でないため厳密な不等号の手書きのループにしました。Ruby 版は `min_by` が同点のときにどちらを返すかを当てにせず、**番号を比較の鍵に入れます。そうしておけば、同点なら小さい番号、という約束がコードに残ります**。

### クラスタごとの平均を新しい中心にする

```ruby
# クラスタごとに、割り当てられた点の平均を新しい中心にする。
# 点が 1 つも割り当てられなかったクラスタは、前の中心をそのまま使う。
def update_centers(points, labels, previous)
  rows = Array.new(previous.shape[0]) do |cluster|
    members = labels.each_index.select { |index| labels[index] == cluster }
    members.empty? ? previous[cluster, true] : points[members, true].mean(axis: 0)
  end

  Numo::DFloat.cast(rows.map(&:to_a))
end
```

`points[members, true]` は、番号の配列で行を選ぶ書き方です。**点が 1 つも割り当てられなかったクラスタ** は、平均を求めると 0 で割ることになるので、前の中心をそのまま残します。Rumale の `KMeans` も、ソースを読むと `if assigned_bits.count.positive?` のときだけ中心を更新していて、同じ扱いでした。

SSE は、点ごとに「所属するクラスタの中心」を並べた行列を作ってから、差の 2 乗を全部足します。

```ruby
# 各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）。
def sum_of_squared_errors(points, labels, centers)
  ((points - centers[labels, true])**2).sum
end
```

### 中心が変わらなくなるまで繰り返す

```ruby
# 中心が変わらなくなるか、更新の回数が max_iterations に達するまで、割り当てと中心の更新を繰り返す。
def fit(points, initial_centers, max_iterations: DEFAULT_MAX_ITERATIONS)
  centers = initial_centers
  max_iterations.times do
    updated = update_centers(points, assign_clusters(points, centers), centers)
    break if updated == centers

    centers = updated
  end

  labels = assign_clusters(points, centers)
  KMeansResult.new(labels:, centers:, sse: sum_of_squared_errors(points, labels, centers))
end
```

`updated == centers` は、Numo では **すべての要素が等しいか** を `true`・`false` で返します（要素ごとの比較は `eq` で、こちらは `Numo::Bit` の配列を返します）。Rust 版と同じく浮動小数点の厳密な比較ですが、同じ入力から同じ平均を計算しているので、変化が無ければビット単位で同じ値になります。実データでクラスタ数 5・シード 0 の初期中心から数えると、10 回目の更新で中心が変わらなくなりました。

上限 `DEFAULT_MAX_ITERATIONS` は scikit-learn の既定に合わせて 300 にしています。テストでは 1 次元に 3 組並べた点を 2 つに分け、1 回で止めた結果が収束した結果と違うことを確かめます。

```ruby
def test_繰り返しの上限で止める
  # 1 次元の 3 組を 2 つに分けると、1 回目の更新では中心がまだ動く
  once = C.fit(three_pairs, matrix([[0.0], [1.0]]), max_iterations: 1)
  converged = C.fit(three_pairs, matrix([[0.0], [1.0]]))

  refute_equal converged.centers.to_a, once.centers.to_a
end
```

## 14.6 初期中心と局所解

### シードで初期中心を選ぶ

```ruby
# シード付きの乱数で点の番号を並べ替え、先頭から n_clusters 個の点を初期中心にする。
def choose_initial_centers(points, n_clusters, seed)
  count = points.shape[0]
  raise ArgumentError, "クラスタ数は 1 以上 #{count} 以下にしてください: #{n_clusters}" unless n_clusters.between?(1, count)

  # 第 2 章の shuffle（分割と同じ乱数の使い方）を再利用する。sample に替えると選ばれる点が変わる
  order = Chapter02.shuffle((0...count).to_a, seed)
  points[order.first(n_clusters), true].dup
end
```

第 2 章の `shuffle`（`Random.new(seed)` で並べ替える）をそのまま使います。最初は `Chapter02.shuffle(...).first(n_clusters)` と 1 行で書いていて、RuboCop の `Style/Sample` が「`sample` を使え」と指摘しました。第 10 章で見たとおり、`shuffle` と `sample` は乱数の使い方が違います。`Random.new(0)` で 440 件から 5 件を選ぶと、`shuffle(...).first(5)` は `[208, 349, 249, 319, 54]`、`sample(5, ...)` は `[172, 47, 118, 195, 327]` でした。ここでは、第 2 章の分割と同じ関数を使い回すことを優先し、並べ替えた結果をいったん変数に置く形にしました。

`.dup` は、選んだ行が元の点の行列の view にならないようにするためです。第 13 章で、view に書き込むと元の行列まで書き換わることを見ました。`fit` は中心を書き換えずに新しい行列を作りますが、初期中心を呼び出し側に返すメソッドなので、元の点と切り離しておきます。

### 局所解に止まる

K-means は **初期中心によって結果が変わります**。運が悪いと、明らかに良くない分け方のまま中心が動かなくなります。これを局所解と言います。Rust 版と同じテストで再現できます。

```ruby
# 1 次元に 3 組並べた点。3 つに分けるなら SSE は 0.5 × 3 = 1.5 が最小。
def three_pairs
  matrix([[0.0], [1.0], [10.0], [11.0], [20.0], [21.0]])
end
```

```ruby
def test_初期中心によっては局所解に止まる
  # 左の組から 2 点・中央の組から 1 点を初期中心にすると、右の 4 点が 1 つにまとまる
  stuck = C.fit(three_pairs, matrix([[0.0], [1.0], [10.0]]))
  best = C.fit(three_pairs, matrix([[0.0], [10.0], [20.0]]))

  assert_in_delta 1.5, best.sse, 1e-12
  assert_operator stuck.sse, :>, best.sse
end
```

対策は、**初期中心を何通りか試して、SSE がいちばん小さい結果を採る** ことです。

```ruby
# シードを 1 ずつずらして初期中心を n_init 通り選び、SSE が最小の結果を返す。同じなら先の結果を選ぶ。
def fit_with_restarts(points, n_clusters, seed:, n_init: DEFAULT_N_INIT)
  Array.new([n_init, 1].max) { |offset| fit(points, choose_initial_centers(points, n_clusters, seed + offset)) }
       .min_by.with_index { |result, index| [result.sse, index] }
end
```

`min_by.with_index` は、ブロックに要素と番号を渡す `min_by` です。ここでも `[SSE, 番号]` を鍵にして「同じなら先の結果」を約束にしています。Rust 版は `Option` を使って「まだ何も無いか、今回のほうが良ければ入れ替える」ループを書きましたが、Ruby 版は「全部作ってから最小を選ぶ」と書けます。10 通り分の結果を一度に持つのでメモリは余分に使いますが、440 件の点では問題になりません。

```ruby
def test_何通りか試せば局所解から抜け出せる
  assert_in_delta 1.5, C.fit_with_restarts(three_pairs, 3, seed: 0, n_init: 10).sse, 1e-12
end
```

## 14.7 Rumale の KMeans と突き合わせる

### Rumale の KMeans が持っているもの・いないもの

Rumale 2.2.0 の `Clustering::KMeans` のソースを読み、実測で確かめたことをまとめます。

| 項目 | Rumale の `KMeans` | 自作 |
|------|------------------|------|
| 初期中心の選び方（既定） | **k-means++**（`init: 'random'` も選べる） | シードで並べ替えて先頭から |
| 何通りか試す（`n_init`） | **無い**（1 回だけ学習する） | `fit_with_restarts` |
| 繰り返しの上限（既定） | `max_iter: 50` | 300 |
| 収束の判定（既定） | 中心の移動距離の平均が `tol: 1e-4` 以下 | 中心がビット単位で変わらない |
| SSE | **持たない**（学習後に持つのは `cluster_centers` だけ） | `sse` |
| シードを省いたとき | `random_seed || srand` でプロセス全体の乱数の種が入れ替わる | シードは必須 |

k-means++ は「すでに選んだ中心から遠い点ほど選ばれやすい」方式で、ランダムに選ぶより良い初期値を引きやすい方法です。Rumale の実装は、1 つめの中心を `sample` で選び、2 つめ以降を「最も近い中心までの距離の 2 乗」に比例した確率で選んでいました。

持っていないことも、テストに書いておきます。

```ruby
# Rumale の KMeans が学習後に持つのは中心だけで、SSE（scikit-learn の inertia_）は無い
def test_RumaleのKMeansはSSEを持たない
  model = Rumale::Clustering::KMeans.new(n_clusters: 3, random_seed: 0).fit(three_pairs)

  refute_respond_to model, :inertia
  refute_respond_to model, :sse
  assert_equal [3, 1], model.cluster_centers.shape
end
```

シードを省くと乱数の種が入れ替わることも、第 10 章のランダムフォレスト・第 13 章の PCA と同じでした。`srand(42)` のあとに `rand` を呼ぶ場合と、あいだに `Rumale::Clustering::KMeans.new` を挟む場合で値が変わりました。

### 足りないものを自作で補う

SSE は、Rumale の中心を受け取って **自作と同じ関数で測り直します**。`n_init` は、自作の `fit_with_restarts` と同じく、シードを 1 ずつずらして 10 回学習し、SSE が最小のものを選びます。

```ruby
# シードを 1 ずつずらして n_init 回学習し、SSE が最小の結果を返す。Rumale の KMeans は SSE を持たず、
# 初期中心を何通りも試す仕組み（scikit-learn の n_init）も無いので、どちらもこちらで用意する。
# 乱数で初期中心を選ぶので、シードは必ず渡す（省くと srand でプロセス全体の乱数の種が入れ替わる）。
def fit(points, n_clusters, seed:, n_init: DEFAULT_N_INIT, **options)
  Array.new([n_init, 1].max) { |offset| fit_once(points, n_clusters, seed + offset, options) }
       .min_by.with_index { |result, index| [result.sse, index] }
end

# 1 回だけ学習し、中心と、自作と同じ式で測り直した SSE を返す。
def fit_once(points, n_clusters, seed, options)
  model = Rumale::Clustering::KMeans.new(n_clusters:, random_seed: seed, **options).fit(points)
  centers = model.cluster_centers
  labels = Chapter14.assign_clusters(points, centers)

  KMeansResult.new(labels:, centers:, sse: Chapter14.sum_of_squared_errors(points, labels, centers))
end
```

`**options` は、`init:`・`max_iter:`・`tol:` のような Rumale のキーワード引数を、そのまま素通しで渡すためのものです。結果は自作と同じ `KMeansResult` にそろえたので、表示する側は自作か Rumale かを区別しません。

指標の定義をこちらでそろえると、「中心の選び方の違い」だけを比べられます。Rust 版が linfa の中心を受け取って同じ式で測り直したのと同じ考え方です。

## 14.8 実データでクラスタリングする

### エルボー法

```bash
bundle exec rake 'run[chapter14]'
```

```text
データ件数: 440（支出額 6 列）
クラスタ数ごとの SSE（初期中心 10 通りの最小値）:
クラスタ数	自作	Rumale（k-means++）
1	2640.00	2640.00
2	1954.18	1954.18
3	1614.00	1608.43
4	1345.54	1324.85
5	1085.74	1058.77
6	990.29	916.89
7	902.93	833.63
8	750.94	738.68
9	724.76	675.76
10	705.72	609.86
```

読み取れることが 3 つあります。

1. **クラスタ数 1 の SSE 2640.00 は、自作・Rumale・Rust 版のすべてで一致します。** クラスタが 1 つなら中心は全体の平均で、乱数も初期中心も関係ないからです。2640 = 440 × 6 で、**母標準偏差で標準化したデータの分散は列ごとに 1** なので、SSE は「件数 × 列数」になります。この性質は実データのテストに書きました
2. **クラスタ数 2 も両者で一致しました**（1954.18）。Rust 版の linfa（1954.18）とも同じ値で、2 つに分けるときは初期化の方式によらず同じ分け方に落ち着いたようです
3. **クラスタ数 3 以上では Rumale のほうが小さい**。k-means++ の初期値が効いています。Rust 版では「クラスタ数 9 では自作のほうが小さい」という逆転がありましたが、Ruby 版の 10 通りの最小値では逆転は起きませんでした

ただし、1 回ずつで比べると話が変わります。`n_init: 1`（シード 0 で 1 回だけ）にして測り直すと、次のようになりました。

| クラスタ数 | 3 | 4 | 5 | 6 | 7 | 8 |
|-----------|---:|---:|---:|---:|---:|---:|
| 自作（1 回） | 1655.05 | 1368.79 | 1252.97 | 990.38 | 902.93 | 844.20 |
| Rumale k-means++（1 回） | 1692.22 | 1450.13 | 1074.44 | 1118.73 | 858.91 | 739.88 |

クラスタ数 3・4・6 では、ランダムに選んだ自作のほうが小さくなりました。**k-means++ は良い初期値を引く確率を上げるだけで、1 回ごとの勝ちを約束するものではありません**。何通りか試す仕組みは、k-means++ を使う場合にも要ります。

### Rumale の既定の設定と初期化の方式

Rumale の既定は `max_iter: 50`・`tol: 1e-4` で、自作（300 回・変化が無くなるまで）よりゆるい設定です。`max_iter: 300`・`tol: 0.0` に変えて測り直しても、クラスタ数 1〜10 の 10 通りの最小値は **すべて同じ値** でした。このデータでは、既定の設定でも収束しきっていたことになります。第 13 章の PCA では既定の設定で答えが大きくずれたので、「既定値で足りるか」はアルゴリズムとデータごとに測るしかありません。

`init: "random"` にすると、次のとおりでした（10 通りの最小値）。

```text
2640.00 1954.80 1614.52 1345.47 1085.83 989.76 875.14 804.87 692.34 618.17
```

自作と同じランダムな初期化でも、選び方（Rumale は `sample`、自作は `shuffle`）が違うので値は一致しません。k-means++ の列と比べると、クラスタ数 3〜10 のすべてで k-means++ のほうが小さく、初期化の方式の差が SSE の差に表れています。

### クラスタごとの特徴

```text
クラスタ数 5 のクラスタごとの件数と平均支出額:
クラスタ	件数	Fresh	Milk	Grocery	Frozen	Detergents_Paper	Delicassen
3	257	8219	3040	3909	2209	1047	972
1	91	5342	10613	16833	1382	7404	1606
4	78	29666	4222	5165	6517	839	2141
0	10	15965	34708	48537	3055	24875	2943
2	4	52022	31696	18491	29826	2699	19656
```

平均は **元の金額の単位に戻して** 表示しています。標準化した値のままでは解釈できないからです。クラスタごとの要約は、第 9 章までと同じく配列の語彙で書けます。

```ruby
# クラスタごとの件数と、元の単位での列ごとの平均を、件数の多い順に並べる。件数が同じなら番号の順。
def summarize_clusters(x, labels)
  summaries = x.zip(labels).group_by(&:last).map { |cluster, members| summarize(cluster, members.map(&:first)) }

  summaries.sort_by { |summary| [-summary.count, summary.cluster] }
end

# 1 つのクラスタに属する特徴量の並びを要約する。
def summarize(cluster, members)
  means = members.map(&:values).transpose.map { |column| column.sum / column.size }

  ClusterSummary.new(cluster:, count: members.size, means:)
end
```

`group_by(&:last)` でクラスタ番号ごとに集め、`transpose` で「列ごとの値の並び」に組み替えて平均を取ります。Rust 版の「クラスタごとの合計と件数の配列を用意して足し込む」処理が、名前のついた操作の組み合わせになります。

ここからは人間の仕事です。

- **クラスタ 3（257 件）** — すべての列で金額が小さい。小規模な顧客
- **クラスタ 1（91 件）** — `Grocery` 16833 と `Detergents_Paper` 7404 が目立つ。食料雑貨と日用品を扱う小売業でしょうか
- **クラスタ 4（78 件）** — `Fresh` 29666 が突出。生鮮食品中心の業態（レストランなど）
- **クラスタ 0（10 件）** — `Grocery` 48537、`Detergents_Paper` 24875。クラスタ 1 の大規模版
- **クラスタ 2（4 件）** — どの列も桁違いに大きい。数件の超大口顧客

Rust 版と比べると、大きな 3 つのクラスタの件数は違います（Rust 版は 265・96・65 件）。初期中心の乱数が違えば、境界近くの顧客の行き先が変わるからです。一方、**10 件と 4 件の小さなクラスタは、平均支出額まで Rust 版と完全に一致しました**（15965・34708・…、52022・31696・…）。飛び抜けた顧客のまとまりは、初期値が違っても同じ集まりとして取り出されます。クラスタの **番号** は初期中心の順で決まるだけなので、Rust 版の 0・4 が Ruby 版では 0・2 になっていて、番号そのものに意味はありません。

**正解ラベルはありません。** これらの解釈が正しいかどうかは、データの背景を知っている人が判断することです。クラスタリングが返すのは「似たものの集まり」までで、**名前を付けるのは人間** です。

### 実データのテスト

出力は `test/wholesale_kmeans_test.rb` で固定しています。そのうえで、自作と Rumale の比較には、厳密な一致ではなく **アルゴリズムとして守られるべき性質** を使います。

```ruby
# 母標準偏差で標準化した列は分散が 1 なので、クラスタが 1 つなら SSE は「件数 × 列数」になる
def test_クラスタが一つなら誤差平方和は件数と列数の積になる
  assert_in_delta 440 * 6, C.fit_with_restarts(points, 1, seed: 0, n_init: 1).sse, 1e-9
end

# 初期化の方式が違うので、厳密な一致ではなく、アルゴリズムとして守られるべき性質を確かめる
def test_自作とRumaleの誤差平方和は同じ桁に収まる
  data = points
  ours = C.fit_with_restarts(data, 5, seed: 0).sse
  theirs = C::RumaleKMeans.fit(data, 5, seed: 0).sse

  assert_in_delta 1.0, ours / theirs, 0.1
end
```

学習データが無い環境では、これらのテストは Minitest の `skip` でスキップされます。

## 14.9 Notebook による探索と可視化

クラスタリングは散布図で見ると分かりやすくなります。第 13 章の主成分分析で 2 次元に落としてから、クラスタごとに色を変えて描く方法が定番です。

Ruby 版では Notebook と可視化を扱いません。[Python 版の第 14 章](../python/14-k-means-clustering.md) と [Kotlin 版](../kotlin/14-k-means-clustering.md) の可視化の節を参照してください。Ruby 版の `fit_with_restarts` の `labels` と第 13 章の `transform` を組み合わせれば、同じ形の散布図のデータが作れます。

## 14.10 リファクタリング

TODO リストを終えてから `bundle exec rake check` をかけると、RuboCop が 5 件を指摘しました。

- **`Style/Sample`** — 14.6 節で書いたとおり、`shuffle(...).first(n)` に `sample` を勧められました。直すと選ばれる初期中心が変わるので、第 2 章の `shuffle` を使い回したまま、並べ替えた結果を変数に置きました
- **`Metrics/AbcSize`・`Layout/LineLength`・`Style/MultilineBlockChain`** — `summarize_clusters` は最初、`group_by(...).map do ... end.sort_by { ... }` と複数行のブロックをつなげていて、3 つの指摘を同時に受けました（AbcSize は 17.8）。1 つのクラスタを要約する `summarize` を切り出すと、3 つとも消えました
- **`Style/IfUnlessModifier`** — クラスタ数の検査を 1 行の後置 `unless` にしました

第 2 章・第 9 章の部品は変更していません。この章を足した時点で、`bundle exec rake check` は 227 件のテストがすべて通り、データが無い環境（`ML_DATA_DIR=/nonexistent`）では 30 件がスキップになりました。自作のエルボー法（クラスタ数 1〜10 で 10 通りずつ、計 100 回の学習）は手元で約 3 秒、Rumale の同じ計算は約 0.8 秒でした。

## 14.11 まとめ

この章では、K-means を自作し、Rumale の `KMeans` と突き合わせました。

| 手順 | 自作したもの | 突き合わせた相手 | 分かったこと |
|------|------------|---------------|------------|
| 割り当て | `assign_clusters`（ブロードキャスト） | — | 同点の約束は比較の鍵に番号を入れて残す |
| 更新・繰り返し | `update_centers`・`fit` | Rumale の `KMeans#fit` | 空のクラスタは両者とも前の中心を残す。Numo の `==` は全要素の比較 |
| 初期中心 | `choose_initial_centers`・`fit_with_restarts` | Rumale の k-means++ | Rumale に `n_init` は無い。1 回ずつなら k-means++ が負けることもある |
| SSE | `sum_of_squared_errors` | Rumale の中心を同じ式で測り直す | Rumale は SSE を持たない。クラスタ数 1・2 は一致 |

Ruby らしさが出たのは次の 3 点です。

1. **距離をまとめて求める** — `expand_dims` とブロードキャストで「点の数 × 中心の数」の距離の行列を 1 式で作れました。Rust 版の二重ループに当たります
2. **足りないものはキーワード引数と `Data` で包む** — Rumale に無い SSE と `n_init` は、`**options` で設定を素通しする小さなモジュールで補い、結果を自作と同じ `KMeansResult` にそろえました
3. **「同じなら先」を約束として書く** — `min_by` に `[値, 番号]` を渡すと、実装の都合ではなく約束として同点の扱いがコードに残ります

そして、**乱数を使う章では、言語版の数値は一致しない** ことも確かめました。第 13 章の寄与率は言語をまたいで一致しましたが、この章で一致したのは、乱数に左右されないクラスタ数 1 の SSE と、飛び抜けた顧客の小さなクラスタだけでした。どこが一致し、どこが一致しないはずかを先に考えておくと、突き合わせの結果を読み違えません。

次の章はシリーズの最後です。第 7・8 章で作ったモデルを予測 API として公開します。
