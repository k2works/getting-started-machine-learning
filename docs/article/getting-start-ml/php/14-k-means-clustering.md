---
type: Article
title: "第 14 章: K-means によるクラスタリング"
description: "割り当て・中心の更新・SSE の小さな関数から K-means を PHP の TDD で組み立て、局所解とエルボー法を実データで確かめ、初期中心を渡せるのに同じ答えにならない Rubix ML の KMeans（ミニバッチ K-means）と同じ定義の SSE で比べる。"
tags: [article,getting-start-ml,php]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 14 章: K-means によるクラスタリング

## 14.1 はじめに

[前章](13-principal-component-analysis.md) の主成分分析は、教師なし学習のうち「列を要約する」手法でした。この章で扱う **クラスタリング** は「行をグループに分ける」手法です。正解ラベルが無いまま、似たもの同士を同じグループにまとめます。

最も基本的なアルゴリズムが **K-means** です。あらかじめ決めたクラスタ数だけ「中心」を置き、各点を最も近い中心に割り当て、割り当てられた点の平均を新しい中心にする、という手続きを中心が動かなくなるまで繰り返します。

[Python 版の第 14 章](../python/14-k-means-clustering.md) は NumPy で K-means を自作し、scikit-learn の `KMeans` と突き合わせました。PHP 版も同じく自作してから、`Rubix\ML\Clusterers\KMeans` と比べます。

前章とは逆に、**ここでは Rubix ML のほうが口が広いところがあります**。

- **初期中心をそのまま渡せます。** `Seeders\Preset` という「あらかじめ決めた中心から選ぶ種まき係」があります。[Elixir 版](../elixir/14-k-means-clustering.md) の Scholar は `:init` にアトム 2 つしか受け取らず、Tribuo にも同じ口はありませんでした。**シリーズで初めて、ライブラリに初期中心を渡せた版です**
- **それでも同じ答えにはなりません。** Rubix ML の `KMeans` は教科書どおりの K-means（Lloyd 法）ではなく **ミニバッチ K-means** だからです（14.13 節）

「渡せる口があるのに一致しない」のは、「渡せる口が無いから一致しない」のとは別の学びです。この章はそこを実測します。

## 14.2 K-means の仕組み

1. クラスタ数 $k$ を決め、初期中心を $k$ 個選ぶ
2. **割り当て**: 各点を、最も近い中心のクラスタに入れる
3. **更新**: クラスタごとに、割り当てられた点の平均を新しい中心にする
4. 中心が動かなくなるまで 2 と 3 を繰り返す

うまくいくかどうかは **初期中心の選び方** で決まります。K-means が最小化しているのは **SSE（誤差平方和）**、つまり「各点と、所属するクラスタの中心との距離の 2 乗の合計」ですが、この手続きは全体の最小値（大域解）に届く保証がありません。初期中心が悪いと、どこにも動けない **局所解** で止まります（14.12 節）。

クラスタ数そのものも決めなければなりません。$k$ を増やせば SSE はいくらでも小さくできる（極端には点の数だけクラスタを作れば SSE は 0）ので、**SSE の減り方が緩やかになる折れ目**（エルボー）を探します。

## 14.3 題材とデータ

卸売業者の顧客ごとの年間支出額（`Wholesale.csv`、440 件）を使います。列は `Channel`（販売チャネル）・`Region`（地域）と、6 つの商品カテゴリごとの支出額です。`Channel` と `Region` は区分を表す番号で支出額ではないので、この章では除きます。

支出額はカテゴリによって桁が違う（`Fresh` は数万、`Delicassen` は数千）ので、[第 9 章](09-feature-engineering.md) の標準化で列ごとに平均 0・標準偏差 1 にそろえてから距離を測ります。

## 14.4 TODO リストの作成

```text
[ ] 支出額の列を読み込む
[ ] 列ごとに標準化する
[ ] 2 点間の距離の 2 乗を求める
[ ] 各点を最も近い中心に割り当てる
[ ] クラスタごとに中心を更新する
[ ] SSE を計算する
[ ] 中心が変わらなくなるまで繰り返す
[ ] 初期中心をシードで選ぶ
[ ] クラスタ数ごとの SSE を求める（エルボー法）
[ ] 初期中心を複数試して最小の SSE を選ぶ
[ ] Rubix ML の KMeans と比べる
[ ] クラスタごとの特徴をまとめる
```

## 14.5 支出額の列を読み込む

`Channel` と `Region` を落とし、残りの列を `float` にします。この章は距離を測るので、**空欄があったら補完せずに失敗させます**。平均値で埋めた点を「その場所にある顧客」として扱うと、クラスタの中心が静かに歪むからです。

```php
/**
 * Channel と Region を除いた支出額の列を読み込む。欠損値があれば失敗する。
 *
 * @return array{columns: list<string>, x: list<array<string, float>>}
 */
public static function spending(Table $table): array
{
    $columns = array_values(array_filter(
        $table->columns,
        static fn (string $c): bool => !in_array($c, self::NON_SPENDING, true),
    ));
    // …
}
```

`array_filter()` はキーを保つので `array_values()` で詰め直します。第 2 章から何度も出てきた PHP の癖で、PHPStan のレベル 9 が `list<string>` にならないことを指摘してくれます。

```php
#[TestDox('支出額に空欄があれば失敗する')]
public function test空欄があれば失敗する(): void
{
    $this->expectException(InvalidArgumentException::class);

    Chapter14::spending(new Table(['Fresh'], [['Fresh' => '']]));
}
```

## 14.6 列ごとに標準化する

[第 9 章](09-feature-engineering.md) の `Standardizer` をそのまま呼びます。

```php
/**
 * 第 9 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 つの点にする。
 *
 * @param list<array<string, float>> $x
 * @param list<string>               $columns
 *
 * @return list<list<float>>
 */
public static function standardize(array $x, array $columns): array
{
    return Standardizer::toRows(Standardizer::fit($x, $columns)->transformAll($x), $columns);
}
```

第 13 章と同じく、標準化を書き直しません。3 行で済むのは、第 9 章の時点で「学習した平均と標準偏差を持つ `readonly class` を返す」形にしておいたからです。

**この章では「件数で割る標準偏差」であることが、テストできる性質になります。** 各列の分散がちょうど 1 になるので、クラスタ数 1 の SSE は「件数 × 列数」に一致します（14.14 節）。不偏標準偏差（n−1 で割る）だったら、この値にはなりません。

## 14.7 各点を最も近い中心に割り当てる

距離は **2 乗のまま** 扱います。平方根を取っても大小関係は変わらないので、比べるだけなら不要です。

```php
/**
 * 各点を、最も近い中心のクラスタ番号に割り当てる。
 *
 * 距離が同じなら先に並ぶ中心を選ぶように、厳密な < で比べる。
 *
 * @param list<list<float>> $points
 * @param list<list<float>> $centers
 *
 * @return list<int>
 */
public static function assignClusters(array $points, array $centers): array
{
    if ($centers === []) {
        throw new InvalidArgumentException('中心が 1 つもありません');
    }

    return array_map(
        static function (array $point) use ($centers): int {
            $label = 0;
            $best = INF;

            foreach ($centers as $k => $center) {
                $distance = self::squaredDistance($point, $center);

                if ($distance < $best) {
                    $label = $k;
                    $best = $distance;
                }
            }

            return $label;
        },
        $points,
    );
}
```

**`<=` ではなく `<` にすることが、ほかの言語版と数値をそろえる条件です。** 距離が同じ中心が並んだときに、先に並ぶほうを選びます。Java 版の `min(Comparator)`（先に見つけたほうを残す）と同じ振る舞いです。

```php
#[TestDox('距離が同じなら先に並ぶ中心を選ぶ')]
public function test同距離なら先の中心(): void
{
    $this->assertSame([0], Chapter14::assignClusters([[0.0]], [[-1.0], [1.0]]));
}
```

PHP の `INF` は組み込みの定数で、どんな `float` より大きい値として比較できます。最初の中心と比べる前の初期値にそのまま置けます。

## 14.8 中心を更新する

点が 1 つも割り当てられなかったクラスタは、**前の中心をそのまま残します**。空のクラスタの平均は定義できないので、どこかで決めなければならない仕様です。

```php
/**
 * クラスタごとに、割り当てられた点の平均を新しい中心にする。
 *
 * 点が 1 つも割り当てられなかったクラスタは、前の中心をそのまま残す。
 *
 * @param list<list<float>> $points
 * @param list<int>         $labels
 * @param list<list<float>> $previous
 *
 * @return list<list<float>>
 */
public static function updateCenters(array $points, array $labels, array $previous): array
{
    $members = [];

    foreach ($points as $i => $point) {
        $members[$labels[$i]][] = $point;
    }

    return array_map(
        static fn (int $k): array => array_key_exists($k, $members)
            ? self::meanPoint($members[$k])
            : $previous[$k],
        array_keys($previous),
    );
}
```

`$members[$labels[$i]][] = $point;` は、鍵が無ければ空の配列を作ってから追加します。PHP ではこの書き方が警告なしに通ります。ただし **`$members` は「0 から詰まった配列」ではありません**（空のクラスタの番号が抜けます）。そこで `array_keys($previous)` を回して、クラスタ番号の順に必ず値を作ります。

```php
#[TestDox('点が割り当てられなかったクラスタは前の中心をそのまま残す')]
public function test空のクラスタ(): void
{
    $this->assertSame(
        [[5.25, 5.25], [99.0, 99.0]],
        Chapter14::updateCenters(self::TWO_GROUPS, [0, 0, 0, 0], [[0.0, 0.0], [99.0, 99.0]]),
    );
}
```

## 14.9 SSE を計算する

```php
/**
 * 各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）。
 *
 * @param list<list<float>> $points
 * @param list<int>         $labels
 * @param list<list<float>> $centers
 */
public static function sumOfSquaredErrors(array $points, array $labels, array $centers): float
{
    $sum = 0.0;

    foreach ($points as $i => $point) {
        $sum += self::squaredDistance($point, $centers[$labels[$i]]);
    }

    return $sum;
}
```

`array_map()` と `array_sum()` でも書けますが、`foreach` で順に足しています。**浮動小数点の足し算は順序で結果が変わる** ので、ほかの言語版と同じ順（点の並び順）で足すことをはっきりさせるためです。

## 14.10 中心が変わらなくなるまで繰り返す

```php
public static function fit(
    array $points,
    array $initialCenters,
    int $maxIterations = self::DEFAULT_MAX_ITERATIONS,
): KMeansResult {
    $centers = $initialCenters;

    for ($i = 0; $i < $maxIterations; ++$i) {
        $next = self::updateCenters($points, self::assignClusters($points, $centers), $centers);

        if ($next === $centers) {
            break;
        }

        $centers = $next;
    }

    $labels = self::assignClusters($points, $centers);

    return new KMeansResult(
        $labels,
        $centers,
        self::sumOfSquaredErrors($points, $labels, $centers),
    );
}
```

止まる条件は `$next === $centers` です。**PHP の配列の `===` は「同じ鍵が同じ順で、値が同じ型で同じ値」を再帰的に比べる** ので、入れ子の配列でもそのまま書けます。Java 版が `Arrays.deepEquals` を呼び、写し（`clone()`）を取って比べる必要があったところが、PHP では配列が値としてコピーされるので悩まずに済みます。

**初期中心は引数で受け取ります。** 乱数で選ぶ処理と分けておくと、テストで初期中心を固定できます。この分け方が、次の節の局所解のテストをそのまま可能にします。

```php
#[TestDox('繰り返しの上限が 0 なら初期中心のまま返す')]
public function test上限0(): void
{
    $this->assertSame(
        [[0.0, 0.0], [10.0, 10.0]],
        Chapter14::fit(self::TWO_GROUPS, [[0.0, 0.0], [10.0, 10.0]], 0)->centers,
    );
}
```

結果は `readonly class` にまとめます。

```php
/** K-means が落ち着いたときのクラスタ番号・中心・誤差平方和。 */
final readonly class KMeansResult
{
    /**
     * @param list<int>         $labels  点ごとのクラスタ番号
     * @param list<list<float>> $centers クラスタごとの中心
     * @param float             $sse     誤差平方和
     */
    public function __construct(
        public array $labels,
        public array $centers,
        public float $sse,
    ) {
    }
}
```

## 14.11 初期中心をシードで選ぶ

[第 2 章](02-data-preprocessing-and-triangulation.md) で自作した乱数（`java.util.Random` と同じ 48 ビットの線形合同法）で点の番号を並べ替え、先頭から $k$ 個を選びます。

```php
/**
 * シード付きの乱数で点を並べ替え、先頭から nClusters 個を初期中心にする。
 *
 * GettingStartedMl\Random は java.util.Random と同じ線形合同法なので、
 * Java 版・Scala 版・Clojure 版・Elixir 版と同じ点を選ぶ。
 *
 * @param list<list<float>> $points
 *
 * @return list<list<float>>
 */
public static function chooseInitialCenters(array $points, int $nClusters, int $seed): array
{
    if ($nClusters < 1 || $nClusters > count($points)) {
        throw new InvalidArgumentException(
            sprintf('クラスタ数は 1 以上 %d 以下にしてください: %d', count($points), $nClusters),
        );
    }

    return array_map(
        static fn (int $index): array => $points[$index],
        array_slice(Random::shuffle(range(0, count($points) - 1), $seed), 0, $nClusters),
    );
}
```

**`mt_srand()` を使わないのがこの章の肝です。** PHP の `mt_rand` の並びは、どの言語版とも一致しません（[ADR 013](../../../adr/013-php-ml-libraries.md)）。第 2 章で線形合同法を自作しておいたので、**初期中心の選び方が Java 版・Scala 版・Clojure 版・Elixir 版と 1 点も違わず一致します**。これが 14.12 節の表と 14.14 節の SSE の完全一致につながります。

## 14.12 局所解と複数回の試行

### 実データで起きたこと

ここまでの関数で、標準化した実データのエルボー法を試してみました。使い捨てのスクリプトで、初期中心 1 通りで $k$ ごとの SSE を求め、シードを変えて表示した結果の一部です（小数第 2 位まで。確かめたあとでスクリプトは削除しました）。

| k | シード 0 | シード 1 | シード 5 |
|---|---------|---------|---------|
| 2 | 2267.09 | 1954.18 | 1956.12 |
| 4 | 1345.47 | 1533.99 | 1345.47 |
| 6 | 993.26 | 947.20 | 1015.81 |
| 7 | 934.29 | 952.13 | 908.79 |
| 9 | 719.53 | 758.35 | 793.95 |
| 10 | 754.63 | 618.17 | 877.40 |

シード 0 では $k = 2$ の SSE が 2267.09 で、シード 1 の 1954.18 より大きくなりました。シード 0 では $k = 9$ の 719.53 から $k = 10$ の 754.63 へ、シード 1 では $k = 6$ の 947.20 から $k = 7$ の 952.13 へ、シード 5 では $k = 9$ の 793.95 から $k = 10$ の 877.40 へ、**$k$ を増やしたのに SSE が増えています**。理論上そんなことは起きないはずで、起きているのは局所解に落ちているからです。

**この表は [Java 版](../java/14-k-means-clustering.md)・[Scala 版](../scala/14-k-means-clustering.md)・[Clojure 版](../clojure/14-k-means-clustering.md)・[Elixir 版](../elixir/14-k-means-clustering.md) の同じ表と 1 桁も違いません。** 初期中心の選び方（自作の線形合同法による Fisher-Yates）も、中心の更新と SSE の足し算の順序もそろえたからです。

### 局所解をテストで再現する

1 次元に 3 組の点を並べた例で確かめます。3 つのクラスタに分けるなら、0 と 1、10 と 11、20 と 21 に分けるのが最適で、SSE は 0.5 × 3 = 1.5 です。ところが、左の組から 2 点・中央の組から 1 点を初期中心にすると、0 と 1 が別々のクラスタのまま、残りの 4 点が 1 つのクラスタにまとめられて止まります。

```php
/** 架空の 6 件。1 次元に 3 組の点が並ぶ。 */
private const array THREE_PAIRS = [[0.0], [1.0], [10.0], [11.0], [20.0], [21.0]];

#[TestDox('初期中心によっては局所解に陥る')]
public function test局所解(): void
{
    $this->assertEqualsWithDelta(
        101.0,
        Chapter14::fit(self::THREE_PAIRS, [[0.0], [1.0], [10.0]])->sse,
        1.0e-9,
    );
}
```

初期中心を引数にしておいたので、**局所解を「たまたま起きること」ではなく「決まった入力に対する決まった出力」としてテストできます**。

### 複数の候補から最小を選ぶ

```php
public static function best(array $points, array $initialCenterCandidates): KMeansResult
{
    if ($initialCenterCandidates === []) {
        throw new InvalidArgumentException('初期中心の候補が 1 つもありません');
    }

    $best = null;

    foreach ($initialCenterCandidates as $candidate) {
        $result = self::fit($points, $candidate);

        // 同じ SSE が並んだら先に見つけたほうを残すように、厳密な < で比べる。
        if ($best === null || $result->sse < $best->sse) {
            $best = $result;
        }
    }

    return $best;
}
```

ここも厳密な `<` です。14.7 節の `assignClusters()`、第 13 章の `normalizeSign()` とあわせて、**同じ理由で同じ書き方をする場所がこの連載に 3 か所** あります。

シードをずらして候補を作る処理は別の関数にしました。

```php
public static function fitWithRestarts(
    array $points,
    int $nClusters,
    int $seed,
    int $nInit = self::DEFAULT_N_INIT,
): KMeansResult {
    return self::best($points, array_map(
        static fn (int $i): array => self::chooseInitialCenters($points, $nClusters, $seed + $i),
        range(0, $nInit - 1),
    ));
}
```

`$nInit` の既定値 10 は、scikit-learn の `KMeans` の `n_init` と同じです。**PHP には既定値付きの引数があるので、Java 版のようなオーバーロードは要りません。** 既定値を `self::DEFAULT_N_INIT` と定数で書けるのも、「10 という数がどこから来たか」を残すのに向いています。

### エルボー法でクラスタ数を選ぶ

```php
/**
 * クラスタ数ごとに、初期中心を nInit 通り試した最小の SSE を返す。
 *
 * @param list<list<float>> $points
 * @param list<int>         $clusterCounts
 *
 * @return list<array{int, float}>
 */
public static function sseByClusterCount(
    array $points,
    array $clusterCounts,
    int $seed,
    int $nInit = self::DEFAULT_N_INIT,
): array {
    return array_map(
        static fn (int $n): array => [$n, self::fitWithRestarts($points, $n, $seed, $nInit)->sse],
        $clusterCounts,
    );
}
```

戻り値は `[[1, 2640.0], [2, 1954.18], …]` の形です。**「クラスタ数 → SSE」の連想配列にはしませんでした。** PHP の配列は挿入順を保つので順序の問題は起きませんが、数字のクラスタ数を鍵にすると `array{int, float}` のような形で型を表せなくなります。第 3 章で「PHP の配列は数字だけの文字列の鍵を整数に変える」という落とし穴を踏んでいるので、**鍵にするより組の並びにする** ほうを選びました。

## 14.13 Rubix ML の KMeans と比べる

### 初期中心は渡せる

`Rubix\ML\Clusterers\KMeans` のコンストラクタの最後の引数は `?Seeder $seeder` で、`Rubix\ML\Clusterers\Seeders` には 4 つの種まき係があります。

| Seeder | 初期中心の選び方 |
|--------|---------------|
| `PlusPlus` | k-means++（既定） |
| `Random` | データの点からランダムに |
| `KMC2` | k-means++ のマルコフ連鎖による近似 |
| **`Preset`** | **あらかじめ渡した中心から選ぶ** |

`Preset` があるので、初期中心そのものを渡せます。

```php
/**
 * Rubix ML の KMeans に初期中心そのものを渡して学習し、中心を返す。
 *
 * Seeders\Preset が「あらかじめ決めた中心から選ぶ」種まき係なので、初期中心を
 * 指定できる。Elixir 版の Scholar には無かった口。
 *
 * @param list<list<float>> $points
 * @param list<list<float>> $initialCenters
 *
 * @return list<list<float>>
 */
public static function rubixCentersFrom(array $points, array $initialCenters): array
{
    $model = new KMeans(count($initialCenters), seeder: new Preset($initialCenters));
    $model->train(Unlabeled::quick($points));
    // …
}
```

**この連載で、ライブラリに初期中心を渡せたのは PHP 版が初めてです。** Scholar（Elixir）は `:init` にアトム 2 つ、Tribuo（Java・Scala・Clojure）は k-means++ 固定でした。前章で Rubix ML のほうが狭かったのと、ちょうど裏返しになっています。

### それでも同じ中心にはならない

同じ初期中心から始めたのだから、同じ答えになるはず——とはなりませんでした。

```php
#[TestDox('Rubix ML には初期中心を渡す口がある')]
public function testRubixに初期中心を渡せる(): void
{
    $centers = Chapter14::rubixCentersFrom(self::TWO_GROUPS, [[0.0, 0.0], [10.0, 10.0]]);

    $this->assertCount(2, $centers);
    // 同じ初期中心から始めても、ミニバッチの更新なので自作と同じ中心にはならない
    $this->assertNotSame(Chapter14::fit(self::TWO_GROUPS, [[0.0, 0.0], [10.0, 10.0]])->centers, $centers);
}
```

理由は `train()` の中身にあります。Rubix ML の `KMeans` は **ミニバッチ K-means** です。

```php
$batches = $dataset->randomize()->batch($this->batchSize);
// …
$weight = 1.0 / (1 + $this->sizes[$cluster]);

foreach ($centroid as $i => &$mean) {
    $mean = (1.0 - $weight) * $mean + $weight * $means[$i];
}
```

教科書どおりの K-means（Lloyd 法）は「全点を割り当ててから、クラスタの平均をそのまま新しい中心にする」ものです。ミニバッチ K-means は、データを小さな束に切り、**束ごとに中心を平均のほうへ少しずつ動かします**。動かす量 `$weight` はそのクラスタが受け持った累計の件数で決まり、学習が進むほど小さくなります。さらに毎エポックでデータを並べ替えるので、束の切れ目も変わります。

**同じ場所から出発しても、道が違えば着く場所も違います。** この章で比べられるのは、着いた場所の良し悪し——つまり SSE の大きさです。

### 大域の mt_rand から引く

`$dataset->randomize()` は PHP の `shuffle()` を呼び、`PlusPlus` の種まきも `rand()` を使います。**Rubix ML のモデルはシードを引数で受け取らず、大域の乱数の状態から引きます。** 第 10 章のランダムフォレストとまったく同じ事情なので、同じ対処をします。

```php
/**
 * Rubix ML の KMeans を既定の k-means++ で学習し、中心を返す。
 *
 * Rubix ML のモデルはシードを受け取らず大域の mt_rand から引くので、
 * mt_srand で種を固定してから学習する（第 10 章と同じ）。
 *
 * @param list<list<float>> $points
 *
 * @return list<list<float>>
 */
public static function rubixCenters(array $points, int $nClusters, int $seed): array
{
    mt_srand($seed);

    $model = new KMeans($nClusters);
    $model->train(Unlabeled::quick($points));
    // …
}
```

「種を固定すれば同じ結果になる」ことそのものをテストにします。ライブラリの更新で乱数の使い方が変わったときに気づけます。

```php
#[TestDox('Rubix ML の k-means++ はシードを固定すれば同じ中心を返す')]
public function testRubixはシードで決まる(): void
{
    $this->assertSame(
        Chapter14::rubixCenters(self::TWO_GROUPS, 2, 0),
        Chapter14::rubixCenters(self::TWO_GROUPS, 2, 0),
    );
}
```

**`mt_srand()` は大域の状態を書き換えます。** テストの実行順によってほかのテストが影響を受けうる書き方で、本来なら避けたいところです。この連載では第 10 章から「Rubix ML を呼ぶ直前に必ず自分で種を蒔く」と決めて、影響の範囲をその 1 行の直後だけに閉じています。

### 同じ定義の SSE で比べる

Rubix ML の `KMeans` は `losses()` でエポックごとの損失を返しますが、これは「ミニバッチの平均距離」であって、自作の SSE とは定義が違います。そのまま並べても比べたことになりません。**Rubix ML が学習した中心に、自作の割り当てをやり直してから SSE を求めます。**

```php
/**
 * Rubix ML が学習した中心に自作の割り当てをして、自作と同じ定義の SSE を求める。
 *
 * @param list<list<float>> $points
 */
public static function rubixSse(array $points, int $nClusters, int $seed): float
{
    $centers = self::rubixCenters($points, $nClusters, $seed);

    return self::sumOfSquaredErrors($points, self::assignClusters($points, $centers), $centers);
}
```

こうすると「中心の置き方」だけの勝負になります。割り当ても足し算も同じ関数を通るので、定義の違いが紛れ込みません。

### 速さは逆転しなかった

[Elixir 版](../elixir/14-k-means-clustering.md) では、リストで書いた自作より Scholar のほうが 30 倍以上遅いという逆転が起きました。Nx の既定のバックエンドが純粋な Erlang だったためです。PHP 版では逆転しませんでした。

| 処理 | 時間 |
|------|------|
| 自作（$k$ = 1〜10、初期中心 10 通り） | 2.0 秒 |
| Rubix ML（$k$ = 1〜10、k-means++ 10 通りは内部で行わない） | 0.7 秒 |

ただし **同じ量の計算をしているわけではありません**。自作は $k$ ごとに 10 通りの初期中心を試して 10 回収束させますが、Rubix ML の `KMeans` には `n_init` にあたる引数が無く、1 回しか学習しません。**この差がそのまま次の表の差になります。**

## 14.14 実データでクラスタリングする

### クラスタごとの特徴をまとめる

標準化した値のままでは読めないので、元の単位に戻した平均を、件数の多い順に並べます。

```php
// 件数の降順、同数ならクラスタ番号の昇順。
usort(
    $summaries,
    static fn (array $a, array $b): int => [$b['count'], $a['cluster']] <=> [$a['count'], $b['cluster']],
);
```

**PHP の `<=>` は配列どうしも比べられます。** 要素数が同じなら、先頭から順に比べて最初に差がついたところで決まります。「件数の降順、同数ならクラスタ番号の昇順」を、比較関数を 2 つ書かずに 1 行で表せました。Elixir 版がタプルで書いたのと同じ形です。

```php
#[TestDox('件数が同じならクラスタ番号の昇順に並べる')]
public function test同数ならクラスタ番号順(): void
{
    $summaries = Chapter14::summarizeClusters(
        [['Fresh' => 10.0], ['Fresh' => 20.0]],
        ['Fresh'],
        [1, 0],
    );

    $this->assertSame([0, 1], array_column($summaries, 'cluster'));
}
```

### `%.0f` は 0.5 を偶数側に丸める

平均支出額を整数で表示するところで、ほかの言語版と 1 だけ違う値が出ました。

```text
3	10	15965	34708	48537	3055	24875	2943   ← PHP の sprintf('%.0f')
3	10	15965	34709	48537	3055	24875	2943   ← Java 版・Elixir 版
```

`Milk` の平均を 10 桁まで出すと **34708.5000000000** で、ちょうど境目でした。

**PHP の `sprintf('%.0f')` は C ライブラリの丸め（偶数への丸め）に従うので 34708 になります。** Java の `String.format("%.0f")` や Elixir の `round/1` は 0.5 を切り上げるので 34709 です。同じ書式指定子に見えて、境目の振る舞いが違います。

ほかの言語版と数値をそろえるほうを選び、`round()` を通してから整数にしました。

```php
...array_map(
    // sprintf('%.0f') はちょうど 0.5 のとき偶数側に丸めるので、
    // ほかの言語版（0.5 を切り上げる round）と食い違う。round を通してから整数にする。
    static fn (string $c): string => (string) (int) round($summary['means'][$c]),
    $columns,
),
```

PHP の `round()` は既定で「0.5 は 0 から遠いほうへ」（`PHP_ROUND_HALF_UP`）なので、Java・Elixir と同じ側です。**同じ言語の中に、境目の扱いが違う 2 つの丸めがある** ということです。

### 実データのテスト

440 件・6 列になること、標準化したデータのクラスタ数 1 の SSE が「件数 × 列数」になること、クラスタ数を増やすほど SSE が減ることを確かめます。

```php
#[Group('data')]
#[TestDox('標準化したデータのクラスタ数 1 の SSE は件数と列数の積になる')]
public function testクラスタ数1のSSE(): void
{
    $this->skipWithoutData();

    // 件数で割る標準偏差で標準化したので、列ごとの分散は 1 になる
    ['columns' => $columns, 'x' => $x] = Chapter14::loadSpending(Dataset::path('Wholesale.csv'));
    $sse = Chapter14::sseByClusterCount(Chapter14::standardize($x, $columns), [1], 0);

    $this->assertEqualsWithDelta(440.0 * 6, $sse[0][1], 1.0e-6);
}

#[Group('data')]
#[TestDox('クラスタ数を増やすほど SSE が小さくなる')]
public function testSSEは単調に減る(): void
{
    $this->skipWithoutData();

    ['columns' => $columns, 'x' => $x] = Chapter14::loadSpending(Dataset::path('Wholesale.csv'));
    $sse = array_column(
        Chapter14::sseByClusterCount(Chapter14::standardize($x, $columns), range(1, 10), 0),
        1,
    );

    foreach (array_slice($sse, 1) as $i => $value) {
        $this->assertLessThan($sse[$i], $value);
    }
}
```

1 つめは、標準化が「件数で割る標準偏差」なのでちょうど 2640 になるという性質です（不偏標準偏差なら 2634 になります）。2 つめは `array_slice($sse, 1)` で 1 つずらした並びを回し、隣り合う 2 つを比べています。**`array_slice()` は添字を詰め直す** ので、`$i` は「1 つ前の要素の添字」になります。初期中心を 10 通り試すようにしたので、14.12 節で見た「$k$ を増やすと SSE が増える」現象は起きません。

### 実行して結果を表示する

```text
データ件数: 440（支出額 6 列）
クラスタ数ごとの SSE（初期中心 10 通りの最小値）:
クラスタ数	自作	Rubix ML（k-means++）
1	2640.00	2664.04
2	1954.18	2296.29
3	1614.52	1705.52
4	1334.36	1605.84
5	1085.27	1373.32
6	947.20	1296.48
7	888.22	970.24
8	775.24	795.53
9	690.81	836.08
10	618.17	694.81

クラスタ数 5 のクラスタごとの件数と平均支出額:
クラスタ	件数	Fresh	Milk	Grocery	Frozen	Detergents_Paper	Delicassen
2	265	8909	2967	3804	2248	989	962
1	96	5509	10556	16478	1420	7199	1659
0	65	31117	4260	5374	7225	849	2286
3	10	15965	34709	48537	3055	24875	2943
4	4	52022	31696	18491	29826	2699	19656
```

### 結果を読む

**自作の列（SSE）も、クラスタごとの件数と平均支出額も、[Java 版](../java/14-k-means-clustering.md)・[Scala 版](../scala/14-k-means-clustering.md)・[Clojure 版](../clojure/14-k-means-clustering.md)・[Elixir 版](../elixir/14-k-means-clustering.md) と完全に一致しました。** 初期中心の乱数（自作の線形合同法による Fisher-Yates）と、中心の更新・SSE の足し算の順序をそろえたからです。

**Rubix ML の列は、10 のクラスタ数すべてで自作より大きくなりました。** これは Elixir 版（$k$ = 4 以降は Scholar のほうが小さかった）とも Clojure 版とも違う結果です。理由は 2 つあります。

- **`n_init` にあたる引数が無い**: 自作は 10 通りの初期中心を試して最小を採りますが、Rubix ML は 1 回しか学習しません。Scholar の `:num_runs` や Tribuo と違い、複数回試す仕組みがモデルの中にありません
- **ミニバッチである**: 中心を少しずつ動かすので、Lloyd 法のように「そのクラスタの厳密な平均」に落ち着きません

**いちばん分かりやすいのは $k$ = 1 の行です。** クラスタが 1 つなら、答えは全点の平均に決まっていて、SSE は 2640.00 です（列の分散が 1 なので 440 × 6）。ところが Rubix ML は 2664.04 を返します。**最適解が一意に決まる問題でも、ミニバッチの更新は中心をそこに置き切れません。** 「$k$ = 1 なら誰がやっても同じはず」という直感が外れる、いちばん短い実例です。

**これは Rubix ML の欠陥ではありません。** ミニバッチ K-means は、メモリに載らないほど大きなデータのために作られた手法です。440 件のデータでは、その利点が出ないまま精度だけが落ちています。**手法の想定より小さいデータに使うと損をする**、という形の食い違いです。

**エルボー法で読むと**、自作の SSE の減り方は $k$ = 3 → 4 で 280.16、$k$ = 4 → 5 で 249.09、$k$ = 5 → 6 で 138.07、$k$ = 6 → 7 で 58.98 と小さくなっていきますが、$k$ = 7 → 8 では 112.98 と再び大きくなり、はっきりした肘は見えません。ここでは Python 版・Java 版・Scala 版・Clojure 版・Elixir 版と同じくクラスタ数を 5 にしました。エルボーが 1 点に決まらないときは、クラスタを解釈できるかどうかも合わせて判断します。

クラスタ数 5 の結果は、次のように読めます。

- **265 件のクラスタ**: どの支出額も少なめの、最も多いグループ
- **96 件のクラスタ**: Grocery・Milk・Detergents_Paper が多めのグループ
- **65 件のクラスタ**: Fresh が突出して多いグループ
- **10 件のクラスタ**: Milk・Grocery・Detergents_Paper が非常に多いグループ
- **4 件のクラスタ**: Fresh・Milk・Frozen・Delicassen がどれも極端に多い顧客。グループというより外れ値に近い存在です

K-means は外れ値にも中心を 1 つ割いてしまうことが、この結果から分かります。

## 14.15 Notebook による探索と可視化

PHP 版では Notebook と可視化の節を設けません。エルボー法の折れ線、主成分に射影した散布図へのクラスタの色付け、クラスタごとの支出額の箱ひげ図は、[Python 版の第 14 章](../python/14-k-means-clustering.md) と [Kotlin 版の第 14 章](../kotlin/14-k-means-clustering.md) の「Notebook による探索と可視化」の節を参照してください。

## 14.16 品質チェック

```console
$ npx gulp apps:check:php
Found 0 of 90 files that can be fixed in 1.200 seconds, 18.00 MB memory used
 [OK] No errors
…
OK (353 tests, 3752 assertions)
行カバレッジ: 99.00% (2388/2412), しきい値: 90.00%
```

第 14 章のテストは、部品のテスト 24 件と実データのテスト 3 件です。学習データが無い環境では `data` グループのテストがスキップされ、テスト全体は成功します。`src/Chapter14.php` の行カバレッジは 99.33% で、届かないのは Rubix ML から戻ってきた中心が数値でなかったときの分岐です。

TDD の途中で済ませた設計の判断は次のとおりです。

- **素の配列で通す** — 点も中心も `list<float>`。Rubix ML の `Unlabeled` にするのは、渡す直前の 1 行だけ
- **初期中心は引数で受け取る** — 乱数で選ぶ処理と分けたので、局所解をテストで再現できた
- **既定値付きの引数で既定値を表す** — `fit()` の上限回数と、`fitWithRestarts()`・`sseByClusterCount()` の `nInit`。Java 版のオーバーロードは要らない
- **定義の共有** — Rubix ML との比較でも、SSE は自作の `assignClusters()` と `sumOfSquaredErrors()` で求めた
- **標準化は第 9 章から借りる** — 件数で割る標準偏差の標準化を 2 度書かず、`Standardizer` を呼んだ。第 13 章の `toMatrix()` も同じ `Standardizer::toRows()` を指している

第 2 章の乱数、第 9 章の標準化、第 13 章の前処理は変更していません。この章で追加した依存もありません。

## 14.17 まとめ

この章では、K-means を割り当て・更新・SSE の小さな関数から組み立て、Rubix ML の `KMeans` と比べました。

1. **初期中心を引数で受け取る** — 乱数で選ぶ処理と分けたので、テストでは初期中心を固定して結果を確かめられた。局所解もテストで再現できた
2. **初期中心 1 通りではエルボー法を読めない** — 実データでシードごとに SSE の曲線が変わり、$k$ を増やして SSE が増えることもあった。10 通り試した最小の SSE で比べた
3. **Rubix ML には初期中心を渡す口がある** — `Seeders\Preset`。この連載で初めて、ライブラリに初期中心をそのまま渡せた
4. **渡せても一致はしない** — Rubix ML の `KMeans` はミニバッチ K-means で、中心を少しずつ動かす。同じ場所から出発しても違う場所に着く
5. **$k$ = 1 でも最適解に届かなかった** — 答えが全点の平均に決まっている問題で 2640.00 対 2664.04。ミニバッチという手法の性質が、440 件のデータでは損にしかならない
6. **Java 版・Scala 版・Clojure 版・Elixir 版と数値が完全に一致した** — 初期中心の乱数と、中心の更新・SSE の足し算の順序をそろえたので、シードごとの SSE の表も、最終的な SSE もクラスタごとの平均も同じ値になった

PHP 版ならではの学びもありました。

- **配列の `===` で収束を判定できる** — 入れ子の配列でも、鍵と順序と型を再帰的に比べてくれる。Java 版の `Arrays.deepEquals` と `clone()` に相当する悩みが、値としてコピーされる配列では起きない
- **`<=>` は配列どうしも比べる** — 「件数の降順、同数ならクラスタ番号の昇順」が 1 行で書けた
- **`sprintf('%.0f')` と `round()` の丸めが違う** — 前者は 0.5 を偶数側へ、後者は 0 から遠いほうへ。同じ言語の中に 2 つの約束がある。ちょうど 0.5 の平均が実データに 1 つあって初めて気づいた
- **`INF` をそのまま初期値に置ける** — 最も近い中心を探す畳み込みの初期値に使える
- **`mt_srand()` は大域の状態を書き換える** — Rubix ML のモデルはシードを受け取らないので、呼ぶ直前に種を蒔いて影響を 1 行の直後に閉じる（第 10 章から同じ）
- **鍵にするより組の並びにする** — クラスタ数を配列の鍵にすると、PHP が数字らしい鍵を整数に変える癖と、型で形を表せない問題の両方に当たる

次の章では、ここまでに作ったモデルを Web API として公開します。

PHP 版のほかの章は [PHP 版のトップ](index.md) から辿れます。
