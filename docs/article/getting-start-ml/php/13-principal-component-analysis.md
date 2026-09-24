---
type: Article
title: "第 13 章: 主成分分析による次元削減"
description: "中心化・分散共分散行列・固有値分解・符号・射影・寄与率を PHP の TDD で自作し、MathPHP の固有ベクトルが実データで落ちること、Rubix ML の PCA が寄与率も固有ベクトルも公開していないことを実測で確かめてから、公開されている 1 つの数と transform() だけで突き合わせる。"
tags: [article,getting-start-ml,php]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 13 章: 主成分分析による次元削減

## 13.1 はじめに

ここまでの章では、正解ラベルのあるデータで予測をしてきました。この章から扱う **教師なし学習** では、正解ラベルを使わずに、データそのものの構造を調べます。

最初に扱うのは **主成分分析（PCA）** です。住宅価格のデータには 15 個もの列がありますが、列どうしは互いに関係しあっていて、実際にはもっと少ない数の「軸」で大部分を説明できることがあります。主成分分析は、データのばらつきを最もよく説明する新しい軸を探し、たくさんの列を少数の軸に要約します。

[Python 版の第 13 章](../python/13-principal-component-analysis.md) は NumPy の固有値分解で主成分分析を自作し、scikit-learn の `PCA` と突き合わせました。PHP 版も同じ構成です。

ただし、この章の PHP 版は **ライブラリを 2 回あてにして 2 回とも外れた** 章になりました。

- **MathPHP は固有値を計算できるが、固有ベクトルは実データで計算できません。** `Eigenvalue::jacobiMethod()` は 15×15 の行列でもちゃんと固有値を返すのに、`Eigenvector::eigenvectors()` に同じ行列と同じ固有値を渡すと「それは固有値ではありません」と失敗します（13.6 節）。結局、固有値分解そのものを自作しました
- **Rubix ML の `PrincipalComponentAnalysis` には、寄与率も固有ベクトルも読み出す口がありません。** 公開されているのは `lossiness()` というたった 1 つの数と `transform()` だけです（13.10 節）。それでも突き合わせは諦めず、この 2 つから寄与率と主成分を **復元** しました

[Elixir 版](../elixir/13-principal-component-analysis.md) の Scholar は `:components`・`:mean`・`:explained_variance`・`:explained_variance_ratio` をすべて持っていて、「Elixir 版では数少ないライブラリのほうが充実している章」でした。**PHP 版はここだけ立場が逆になります。** 決定木もランダムフォレストもロジスティック回帰も持っている Rubix ML が、主成分分析では Scholar より狭いのです（[ADR 013](../../../adr/013-php-ml-libraries.md)）。

「ライブラリにその機能がある」ことと、「その結果を読み出せる」ことは別です。この章はそれを 2 度確かめます。

## 13.2 主成分分析とは

### ばらつきが最も大きい方向を探す

データの点がばらついている空間に、新しい軸を 1 本引くことを考えます。**第 1 主成分** は、その軸にデータを射影したときに、ばらつき（分散）が最も大きくなる向きです。第 2 主成分は、第 1 主成分と直交する向きのうち、ばらつきが最も大きい向きです。以下、列の数だけ同じように決めていきます。

### 分散共分散行列の固有ベクトル

この「ばらつきが最大になる向き」は、**分散共分散行列の固有ベクトル** として求まります。固有値が、その向きでのばらつきの大きさ（分散）です。

分散共分散行列は、対角に各列の分散、非対角に 2 列ずつの共分散を並べた正方行列で、必ず対称になります。対称行列の固有値分解は、非対称な行列より扱いやすく、実数の固有値と直交する固有ベクトルが得られます。

### 寄与率

固有値をすべて足すと、データ全体のばらつきの合計になります。ある主成分の固有値を合計で割った値が **寄与率** で、「この軸だけで全体の何割を説明できるか」を表します。寄与率を大きいほうから足していったものが **累積寄与率** です。累積寄与率が 0.8 に届くまでの主成分を採る、といった目安の決め方をします。

## 13.3 題材とデータ

ボストンの住宅価格（`Boston.csv`、100 件）を使います。[第 9 章](09-feature-engineering.md) では `PRICE` を予測する回帰の題材でしたが、この章では正解ラベルを使わないので、`PRICE` も含めたすべての列を主成分分析にかけます。`CRIME` はカテゴリ値なのでダミー変数にし、`RM` などの欠損値は列の平均値で補完します。

主成分分析は「ばらつきの大きさ」を見るので、単位の大きい列（`TAX` は数百、`NOX` は 0.5 前後）がそのままでは支配的になります。そこで、すべての列を平均 0・標準偏差 1 にそろえてから分析します。標準化は [第 9 章](09-feature-engineering.md) で作った `Standardizer` をそのまま呼びます（13.11 節）。

## 13.4 TODO リストの作成

```text
[ ] 列ごとの平均を求める
[ ] 各列から平均を引く（中心化）
[ ] 分散共分散行列を求める
[ ] MathPHP の固有値分解の振る舞いを学習用テストで確かめる
[ ] 分散共分散行列を固有値分解して主成分を求める
[ ] 固有ベクトルの符号をそろえる
[ ] 寄与率と累積寄与率を求める
[ ] データを主成分の向きに射影する
[ ] 累積寄与率がしきい値に届く主成分の数を求める
[ ] 主成分への影響が大きい列を求める
[ ] Rubix ML の PCA と突き合わせる
[ ] Boston を前処理して実データで要約する
```

## 13.5 分散共分散行列を求める

### Red: 分散と共分散

行列は「長さのそろった `list<float>` の `list`」で表します。第 9 章までと同じで、データフレームのライブラリは使いません。

2 列目が 1 列目のちょうど 2 倍になる、完全に相関する 3 件を架空のデータにします。分散は 1 と 4、共分散は 2 になるはずです。

```php
/** 架空の 3 件 2 列。2 列目は 1 列目のちょうど 2 倍で、完全に相関する。 */
private const array CORRELATED = [[1.0, 2.0], [2.0, 4.0], [3.0, 6.0]];

#[TestDox('分散共分散行列は対称で、対角に件数から 1 を引いた数で割った分散が並ぶ')]
public function test分散共分散行列は対称(): void
{
    $covariance = Chapter13::covarianceMatrix(self::CORRELATED);

    $this->assertEqualsWithDelta([[1.0, 2.0], [2.0, 4.0]], $covariance, 1.0e-12);
}
```

### Green: 中心化してから内積を取る

```php
/**
 * 列ごとの分散と、2 列ずつの共分散を並べた行列。件数から 1 を引いた数で割る。
 *
 * Rubix ML の Tensor は件数そのもので割るが、寄与率は割る数で変わらないので
 * 突き合わせには影響しない。ほかの言語版と分散の値をそろえるために n−1 にする。
 *
 * @param list<list<float>> $m
 *
 * @return list<list<float>>
 */
public static function covarianceMatrix(array $m): array
{
    if (count($m) < 2) {
        throw new InvalidArgumentException(
            sprintf('主成分分析には 2 件以上のデータが必要です（%d 件）', count($m)),
        );
    }

    $centered = self::transpose(self::center($m, self::columnMeans($m)));
    $n = count($m);

    return array_map(
        static fn (array $left): array => array_map(
            static fn (array $right): float => self::dot($left, $right) / ($n - 1),
            $centered,
        ),
        $centered,
    );
}
```

**割る数を n−1 にするか n にするかは、この章では結論に影響しません。** 寄与率は「固有値 ÷ 固有値の合計」なので、行列全体を定数倍しても変わらないからです。それでも n−1 を選んだのは、Java 版・Scala 版・Clojure 版・Elixir 版と分散（固有値）そのものの値をそろえるためです。あとで分かりますが、**Rubix ML の内部は n で割っています**（13.10 節）。それでも寄与率が一致するのは、この性質のおかげです。

## 13.6 固有値分解を自作する

### MathPHP をあてにする

PHP には MathPHP という数学ライブラリがあり、`Eigenvalue::jacobiMethod()` と `Eigenvector::eigenvectors()` を持っています（[ADR 013](../../../adr/013-php-ml-libraries.md)）。固有値分解のような「よく知られた数値計算」は自作しない、というのがこの連載の方針なので、まずこれを使いました。

小さい行列ではきれいに動きます。

```console
$ php -r '...'
Array ( [0] => 4.7320508075689 [1] => 3 [2] => 1.2679491924311 )
```

固有値は大きい順に返り、`Eigenvector::eigenvectors()` は固有ベクトルを **列に並べた** 行列を返します。対称でない行列を渡すと `BadDataException: Matrix must be symmetric` になります。

### 学習用テストで振る舞いを確かめる

[Elixir 版](../elixir/13-principal-component-analysis.md) では、`Nx.LinAlg.eigh` が **対称でない行列を黙って受け取り、上三角だけを見た別の行列の答えを返す** ことが分かりました。ライブラリに任せる前に、こういう振る舞いを学習用テストで確かめておきます。

```php
#[TestDox('対称でない行列は固有値分解できない')]
public function test対称でない行列は固有値分解できない(): void
{
    $this->expectException(InvalidArgumentException::class);

    Chapter13::eigenDecomposition([[1.0, 2.0], [3.0, 4.0]]);
}

#[TestDox('正方行列でなければ固有値分解できない')]
public function test正方行列でなければ固有値分解できない(): void
{
    $this->expectException(InvalidArgumentException::class);

    Chapter13::eigenDecomposition([[1.0, 2.0, 3.0], [2.0, 4.0, 5.0]]);
}
```

MathPHP は対称でない行列を断るので、Elixir 版のような「黙って別の答え」にはなりません。それでも対称かどうかは自分でも確かめます。失敗の種類を 1 つ（`InvalidArgumentException`）にそろえるためと、**ライブラリを差し替えても振る舞いが変わらないようにする** ためです。この判断が、次の節でそのまま効きました。

### 実データで「それは固有値ではありません」

小さい行列でうまく動いたので、15 列の実データに進みました。ここで止まりました。

```text
MathPHP\Exception\BadDataException: 6.1654181742072 is not an eigenvalue of this matrix
  vendor/markrogoyski/math-php/src/LinearAlgebra/Eigenvector.php:85
```

**`Eigenvalue::jacobiMethod()` が返したばかりの固有値を、同じ行列と一緒に `Eigenvector::eigenvectors()` に渡して、「それは固有値ではありません」と言われています。** 同じライブラリの中での不整合です。

原因は実装の方針の違いでした。`Eigenvector::eigenvectors()` は、固有値ごとに `A − λI` を作り、**掃き出し法（RREF）で零空間を探す** 実装になっています。零行が 1 本も出なければ「固有値ではない」と判断します。浮動小数点で 15×15 の掃き出しをすると、理論上は 0 になるはずの行がわずかに 0 でなくなり、零空間が見つかりません。

15 列の実データは、小さいテストを通り抜けた実装が落ちる場所でした。**「ライブラリにその機能がある」ことは、「そのデータで動く」ことを意味しません。**

### Jacobi 法なら固有値と固有ベクトルが同時に出る

ここで選択肢は 3 つありました。

| 案 | 評価 |
|----|------|
| 別のライブラリを足す | 主成分分析のためだけに依存を増やす。Rubix ML の中にある Tensor も、固有ベクトルを読み出す口を持っていない（13.10 節） |
| MathPHP の固有ベクトルを使わず、固有値から自分で零空間を解く | 落ちている実装と同じ方針をなぞることになる |
| **固有値分解そのものを Jacobi 法で自作する** | **固有値と固有ベクトルが同時に出る。掃き出し法を通らない** |

Jacobi 法は、対称行列の非対角成分を 2 次元の回転で 1 つずつ 0 にしていく手続きです。1 つ 0 にすると別のところがわずかに復活するので、全体が対角行列に十分近づくまで何巡もします。回転行列を掛け合わせたものが、そのまま固有ベクトルになります。「固有値を求めてから、それを使って固有ベクトルを探す」という 2 段構えにならないので、MathPHP が落ちた場所を通りません。

```php
/**
 * 対称行列を Jacobi 法で固有値分解し、固有値と固有ベクトルを大きい順に返す。
 *
 * 上三角の非対角成分を順に 2 次元の回転で 0 にしていき、全体が対角行列に近づくまで
 * 何巡もする。回転行列を掛け合わせたものが固有ベクトルになる。
 *
 * MathPHP は固有値（Eigenvalue::jacobiMethod）を持っているが、固有ベクトル
 * （Eigenvector::eigenvectors）は掃き出し法で零空間を探す実装で、実データの
 * 15×15 では「これは固有値ではない」と失敗する。固有値と固有ベクトルは
 * Jacobi 法なら同時に求まるので、回転をここで回す。
 *
 * @param list<list<float>> $m
 *
 * @return array{values: list<float>, vectors: list<list<float>>}
 */
public static function eigenDecomposition(array $m): array
```

回転そのものは短く書けます。

```php
$theta = ($a[$q][$q] - $a[$p][$p]) / (2.0 * $a[$p][$q]);
$sign = $theta >= 0.0 ? 1.0 : -1.0;
// tan の小さいほうの根を選ぶと、回転が小さくなって桁落ちしにくい。
$tangent = $sign / (abs($theta) + sqrt($theta * $theta + 1.0));
$cosine = 1.0 / sqrt($tangent * $tangent + 1.0);
$sine = $tangent * $cosine;
```

`$sign / (abs($theta) + sqrt(...))` は、2 次方程式の根のうち絶対値の小さいほうを、引き算を通さずに求める書き方です。素直に解の公式を書くと、$\theta$ が大きいときに近い数どうしの引き算になって桁落ちします。

### MathPHP は「突き合わせる相手」として残す

MathPHP の固有値は正しく計算できているので、**捨てずに突き合わせの相手として残しました**。

```php
/**
 * MathPHP の Jacobi 法で固有値だけを求める。自作の固有値と突き合わせるために使う。
 *
 * @param list<list<float>> $m
 *
 * @return list<float>
 */
public static function mathPhpEigenvalues(array $m): array
```

小さい行列では 1.0e-9 の桁まで一致します。ところが実データでは、思ったより早く差が開きました。

```php
#[Group('data')]
#[TestDox('実データでは MathPHP の固有値が 1e-6 の桁までしか合わない')]
public function test実データのMathPHPの固有値(): void
{
    // …
    // MathPHP は「対角行列とみなす」判定がゆるいので、自作より早く回転を止める
    $this->assertGreaterThan(1.0e-9, $gap);
    $this->assertLessThan(1.0e-5, $gap);
}
```

実測した差は **3.98e-6** でした。MathPHP の `jacobiMethod()` は「非対角成分がすべて `isDiagonal()` の許容誤差より小さくなったら止める」という止め方で、この許容誤差が固有値の大きさに対してゆるいためです。自作は「非対角成分の 2 乗和が 1.0e-30 未満」まで回すので、そのぶん深く収束します。

**「差があるからどちらかが間違っている」のではなく、止め方の約束が違うだけです。** この差がどこから来るのかを言えるようにしておくのが、テストに書く意味です。

## 13.7 主成分を求める

### 完全に相関する 2 列

完全に相関する 2 列なら、1 本の軸ですべてを説明できます。第 1 主成分の寄与率が 1、第 2 主成分が 0 になるはずです。

```php
#[TestDox('完全に相関する 2 列なら第 1 主成分の寄与率が 1 になる')]
public function test完全に相関する2列(): void
{
    $model = Chapter13::fit(self::CORRELATED, 2);

    $this->assertEqualsWithDelta(1.0, $model->explainedVarianceRatio[0], 1.0e-12);
    $this->assertEqualsWithDelta(0.0, $model->explainedVarianceRatio[1], 1.0e-12);
}
```

### 符号の規則を決める

固有ベクトルは、すべての要素の符号を反転しても同じ向きを表します。どちらを返すかはライブラリごとに違うので、**比べる前に規則を決めてそろえます**。この連載では「絶対値が最大の要素を正にする」で統一しています。

```php
/** @param list<float> $row */
private static function normalizeSign(array $row): array
{
    $largest = 0.0;

    foreach ($row as $value) {
        // 絶対値が同じなら前の要素を残すように、厳密な > で比べる。
        if (abs($value) > abs($largest)) {
            $largest = $value;
        }
    }

    return $largest < 0.0
        ? array_map(static fn (float $v): float => -$v, $row)
        : $row;
}
```

絶対値が同じ要素が並んだときにどちらを見るかで結果が変わるので、`>=` ではなく厳密な `>` にします。この章と [第 14 章](14-k-means-clustering.md) で、同じ理由の同じ書き方が 3 か所出てきます。

### 学習した結果は readonly class にする

主成分分析の結果は 4 つの値の組です。連想配列でも書けますが、`readonly class` にしておくと PHPStan が誤字を見つけてくれます。

```php
final readonly class Pca
{
    /**
     * @param list<float>       $mean                   列ごとの平均
     * @param list<list<float>> $components             主成分（1 行に 1 つ）
     * @param list<float>       $explainedVariance      主成分ごとの分散（固有値）
     * @param list<float>       $explainedVarianceRatio 主成分ごとの寄与率
     */
    public function __construct(
        public array $mean,
        public array $components,
        public array $explainedVariance,
        public array $explainedVarianceRatio,
    ) {
    }
}
```

第 9 章の `Standardizer`・`BostonSplit` と同じ形です。PHP の `readonly class` は、Ruby 版の `Data.define` や Elixir 版の構造体にあたるものを、**言語本体の機能** として書けます。

## 13.8 データを主成分の向きに射影する

```php
/**
 * 平均を引いてから、データを主成分の向きに射影する。
 *
 * @param list<list<float>> $m
 *
 * @return list<list<float>>
 */
public static function transform(Pca $model, array $m): array
{
    return array_map(
        static fn (array $row): array => array_map(
            static fn (array $component): float => self::dot($row, $component),
            $model->components,
        ),
        self::center($m, $model->mean),
    );
}
```

完全に相関する 3 件を 1 次元に落とすと、真ん中の点が 0、前後の点が同じ大きさで符号だけ逆になります。**具体的な値ではなく、この関係をテストにします。** 符号の規則を変えたときに落ちないテストになるからです。

```php
#[TestDox('主成分の向きに射影すると、完全に相関する 2 列が 1 列で表せる')]
public function test射影(): void
{
    $model = Chapter13::fit(self::CORRELATED, 1);
    $projected = Chapter13::transform($model, self::CORRELATED);

    $this->assertCount(3, $projected);
    $this->assertCount(1, $projected[0]);
    // 中心の行は 0 に、前後の行は同じ大きさで符号が逆になる
    $this->assertEqualsWithDelta(0.0, $projected[1][0], 1.0e-12);
    $this->assertEqualsWithDelta(-$projected[0][0], $projected[2][0], 1.0e-12);
}
```

## 13.9 必要な主成分の数と、影響が大きい列

### 累積寄与率がしきい値に届くまでの数

```php
public static function componentsNeeded(array $ratios, float $threshold): int
{
    $cumulative = 0.0;

    foreach ($ratios as $index => $ratio) {
        $cumulative += $ratio;

        if ($cumulative >= $threshold) {
            return $index + 1;
        }
    }

    return count($ratios);
}
```

しきい値に届かないまま終わったら、すべての主成分の数を返します。「見つからなかった」を `null` で表して呼び出し側に判断させるより、**この関数の中で決め切る** ほうが使う側が短くなります。

### 主成分への影響が大きい列

主成分の「意味」は、係数（ローディング）の大きい列から読みます。

```php
/**
 * 主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。
 *
 * usort は安定なので（PHP 8.0 から）、絶対値が同じなら元の列の順が残る。
 *
 * @param list<float>  $component
 * @param list<string> $columns
 *
 * @return list<array{string, float}>
 */
public static function topLoadings(array $component, array $columns, int $k): array
{
    $loadings = array_map(
        static fn (string $column, float $value): array => [$column, $value],
        $columns,
        $component,
    );
    usort($loadings, static fn (array $a, array $b): int => abs($b[1]) <=> abs($a[1]));

    return array_slice($loadings, 0, $k);
}
```

**PHP の `usort()` は 8.0 から安定ソートになりました。** それ以前は同じ順位の要素の並びが保証されず、この関数はバージョンによって違う結果を返していたはずです。「安定であること」に寄りかかるときは、テストに書いておきます。

```php
#[TestDox('係数の絶対値が同じなら元の列の順を残す')]
public function test係数が同点なら元の順(): void
{
    $this->assertSame(
        [['a', 0.5], ['b', -0.5]],
        Chapter13::topLoadings([0.5, -0.5, 0.1], ['a', 'b', 'c'], 2),
    );
}
```

## 13.10 Rubix ML の PCA と突き合わせる

### 寄与率も固有ベクトルも公開されていない

Rubix ML には `Rubix\ML\Transformers\PrincipalComponentAnalysis` があります。ところが、公開されているメソッドは次の 4 つだけでした。

| メソッド | 返すもの |
|---------|---------|
| `fit(Dataset $dataset)` | （何も返さない） |
| `transform(array &$samples)` | （引数を書き換える） |
| `fitted()` | 学習済みかどうか |
| `lossiness()` | **捨てた主成分の分散の割合**（1 つの `float`） |

固有ベクトル（`$eigenvectors`）も平均（`$mean`）も `protected` で、読み出す口がありません。寄与率にあたるプロパティはそもそも作られません。

**scikit-learn の `PCA` が持つ `components_`・`explained_variance_ratio_`・`mean_` が、1 つもないということです。** [Ruby 版](../ruby/13-principal-component-analysis.md) の Rumale は寄与率を持ちませんでしたが、主成分そのものは読めました。Rubix ML はそれより狭いことになります。

| 版 | ライブラリ | 主成分 | 寄与率 |
|----|----------|-------|-------|
| Python | scikit-learn | 読める | 読める |
| Elixir | Scholar | 読める | 読める |
| Ruby | Rumale | 読める | **無い** |
| Java・Scala・Clojure | Tribuo | **PCA が無い** | — |
| **PHP** | **Rubix ML** | **読めない** | **無い** |

ここで「突き合わせられません」と書いて章を終えることもできました。そうしなかったのは、**公開されている 2 つの口だけで、両方とも復元できる** と分かったからです。

### transform() に「平均 + 単位ベクトル」を通して主成分を取り出す

`transform()` の実装が何をするかは、ライブラリのソースから読めます。

```php
$samples = Matrix::build($samples)
    ->subtract($this->mean)
    ->matmul($this->eigenvectors)
    ->asArray();
```

つまり「平均を引いてから固有ベクトルの行列を掛ける」だけです。ということは、**平均に単位ベクトルを足した点を変換させれば、固有ベクトルの行がそのまま返ってきます**。$i$ 番目の座標だけ 1 だけずらした点を $d$ 個渡せば、固有ベクトルの行列全体が取り出せます。

```php
/**
 * Rubix ML の PCA が学習した主成分を取り出す。
 *
 * PrincipalComponentAnalysis は固有ベクトルを protected で持っていて、読み出す口が無い。
 * transform() が「平均を引いてから固有ベクトルを掛ける」ことだけは分かっているので、
 * 「平均 + 単位ベクトル」を変換させて、固有ベクトルの行を 1 本ずつ取り出す。
 *
 * @param list<list<float>> $m
 *
 * @return list<list<float>>
 */
public static function rubixComponents(array $m, int $nComponents): array
{
    $dimensions = count(self::columnMeans($m));
    $pca = new PrincipalComponentAnalysis($nComponents);
    $pca->fit(Unlabeled::quick($m));

    $mean = self::columnMeans($m);
    $probes = array_map(
        static fn (int $i): array => array_map(
            static fn (float $value, int $j): float => $i === $j ? $value + 1.0 : $value,
            $mean,
            array_keys($mean),
        ),
        range(0, $dimensions - 1),
    );

    $pca->transform($probes);

    // 行が元の列、列が主成分になっているので、転置して 1 行に 1 つの主成分にする。
    return self::normalizeSigns(self::transpose(array_map(
        static fn (array $row): array => array_map(self::toFloat(...), $row),
        $probes,
    )));
}
```

リフレクションで `protected` のプロパティをこじ開ける手もありますが、そうしませんでした。**リフレクションはライブラリの内部の名前に依存しますが、この方法が依存するのは「変換は線形である」という主成分分析そのものの性質だけ** だからです。`$eigenvectors` の名前が変わっても、この関数は動き続けます。

### lossiness() の差から寄与率を復元する

`lossiness()` は「採らなかった主成分の分散の割合」です。ということは `1 - lossiness()` が **採った主成分の累積寄与率** です。主成分の数を 1 つずつ増やして学習し、累積寄与率の差を取れば、主成分ごとの寄与率が出ます。

```php
/**
 * Rubix ML の PCA から、主成分ごとの寄与率を復元する。
 *
 * lossiness() は「捨てた主成分の分散の割合」なので、1 から引けば累積寄与率になる。
 * 主成分の数を 1 つずつ増やして学習し、累積寄与率の差を取れば主成分ごとの寄与率が出る。
 * ライブラリに explained_variance_ratio が無くても、公開されている 1 つの数から
 * ここまでは復元できる。
 *
 * @param list<list<float>> $m
 *
 * @return list<float>
 */
public static function rubixExplainedVarianceRatio(array $m): array
{
    $dimensions = count(self::columnMeans($m));
    $ratios = [];
    $previous = 0.0;

    foreach (range(1, $dimensions) as $nComponents) {
        $pca = new PrincipalComponentAnalysis($nComponents);
        $pca->fit(Unlabeled::quick($m));
        $cumulative = 1.0 - (float) $pca->lossiness();

        $ratios[] = $cumulative - $previous;
        $previous = $cumulative;
    }

    return $ratios;
}
```

$d$ 列なら $d$ 回学習し直すので効率は悪いのですが、**「ライブラリが 1 つの数しか見せてくれないときに、そこから何が復元できるか」** を示すには十分です。

### 割る数が違っても、寄与率は一致する

Rubix ML の中で分散共分散行列を作っているのは `Tensor\Matrix::covariance()` で、**件数そのもの（n）で割っています**。自作は n−1 です。

```php
return $b->matmul($b->transpose())
    ->divideScalar($this->n);
```

固有値は $\frac{n}{n-1}$ 倍だけずれますが、寄与率は「固有値 ÷ 固有値の合計」なので **この定数倍は約分されて消えます**。固有ベクトルも、行列を定数倍しても向きは変わりません。13.5 節で「この章では影響しない」と書いたのは、この意味です。

### 解き方は違うのに、答えは同じ

| 手順 | 自作 | Rubix ML |
|------|------|---------|
| 中心化 | `center()` | `covariance()` の中 |
| 分散共分散行列 | 件数 − 1 で割る | **件数で割る** |
| 固有値分解 | **Jacobi 法を自作** | `Tensor\Matrix::eig(true)` |
| 並べ替え | 固有値の降順 | `array_multisort(SORT_DESC)` |
| 符号 | `normalizeSigns()` | （規則なし。そのまま） |
| 射影 | `transform()` | `transform()` |
| 寄与率 | `fit()` の中 | **無い（`lossiness()` から復元）** |
| 主成分 | `fit()` の中 | **読めない（`transform()` から復元）** |
| 主成分の数の目安 | `componentsNeeded()` | （無い） |
| 係数の大きい列 | `topLoadings()` | （無い） |

それでも、実データで差は **寄与率 2.78e-16、主成分 1.97e-14** でした（13.12 節）。倍精度の丸め誤差の範囲です。

## 13.11 Boston を前処理する

前処理は、ダミー変数化・欠損値の補完・標準化の 3 つを並べるだけです。どれも [第 2 章](02-data-preprocessing-and-triangulation.md)・[第 9 章](09-feature-engineering.md) で書いたものをそのまま呼びます。

```php
/**
 * CRIME をダミー変数にし、欠損値を列の平均値で補完してから、すべての列を標準化する。
 *
 * @return array{columns: list<string>, x: list<array<string, float>>}
 */
public static function standardizeTable(Table $table): array
{
    $crimes = array_map(
        static fn (array $row): string => Chapter02::text($row, self::CATEGORY),
        $table->rows,
    );
    $encoded = Chapter09::encode($table, self::CATEGORY, Chapter09::categories($crimes));
    $means = Chapter02::columnMeans($encoded->rows, $encoded->columns);
    $filled = Chapter02::fillMissing($encoded->rows, $encoded->columns, $means);

    return [
        'columns' => $encoded->columns,
        'x' => Standardizer::fit($filled, $encoded->columns)->transformAll($filled),
    ];
}
```

**第 9 章の `Standardizer` をここで書き直さないことが、この章でいちばん大事な判断です。** 同じ標準化が 2 つあると、片方だけ直したときに気づけません。[Elixir 版](../elixir/13-principal-component-analysis.md) では第 13 章で標準化を重複実装してしまい、あとから直しました。

`Standardizer` は「件数で割る標準偏差」（母標準偏差）を使います。第 9 章で Rubix ML の `ZScaleStandardizer` と実測で一致することを確かめてあるので、ここでも定義の心配は要りません。

### 列の順は必ずリストで持ち回る

特徴量は「列名を鍵にした連想配列」です。PHP の配列は挿入順を保つので、Elixir のマップのように順序が化けることはありません。それでも、行列にするときは **列名のリストを別に持ち回って、その順で並べます**。

```php
/**
 * 特徴量の並びを、1 件を 1 行とする行列にする。列の順は必ずリストで持ち回る。
 *
 * @param list<array<string, float>> $x
 * @param list<string>               $columns
 *
 * @return list<list<float>>
 */
public static function toMatrix(array $x, array $columns): array
{
    return Standardizer::toRows($x, $columns);
}
```

中身は第 9 章の `Standardizer::toRows()` をそのまま呼ぶだけです。同じことをする関数を 2 つ作らず、**この章での呼び名だけを与えました**。

架空の 4 件（`CRIME` が 3 種類、`RM` に欠損値が 1 件）で、ダミー変数の列名と標準化の結果を確かめます。

```php
#[TestDox('欠損値を補完してから各列を平均 0・標準偏差 1 にそろえる')]
public function test標準化する(): void
{
    ['columns' => $columns, 'x' => $x] = Chapter13::standardizeTable($this->bostonLike());

    foreach (Chapter13::toMatrix($x, $columns) as $row) {
        $this->assertCount(4, $row);
    }

    foreach ($columns as $i => $column) {
        $values = array_column(Chapter13::toMatrix($x, $columns), $i);
        $mean = array_sum($values) / count($values);
        $variance = array_sum(array_map(static fn (float $v): float => ($v - $mean) ** 2, $values))
            / count($values);

        $this->assertEqualsWithDelta(0.0, $mean, 1.0e-12, "{$column} の平均");
        $this->assertEqualsWithDelta(1.0, sqrt($variance), 1.0e-12, "{$column} の標準偏差");
    }
}
```

`assertEqualsWithDelta()` の第 4 引数は、落ちたときのメッセージです。列名を入れておくと、どの列で落ちたかが出力に出ます。

## 13.12 実データで要約する

### 実データのテスト

実データのテストは `#[Group('data')]` を付け、ファイルが無ければ `markTestSkipped()` します（[第 1 章](01-machine-learning-and-first-test.md) から使っている仕組みです）。

```php
#[Group('data')]
#[TestDox('実データでも Rubix ML の主成分と寄与率が自作と一致する')]
public function test実データでRubixと一致する(): void
{
    $this->skipWithoutData();

    ['columns' => $columns, 'x' => $x] = Chapter13::loadBoston(Dataset::path('Boston.csv'));
    $gaps = Chapter13::rubixGaps(Chapter13::toMatrix($x, $columns), count($columns));

    $this->assertLessThan(1.0e-12, $gaps['ratio']);
    $this->assertLessThan(1.0e-9, $gaps['component']);
}
```

**自作の Jacobi 法と、Rubix ML の Tensor の固有値分解という違う実装を通ったのに、15 列の実データでも寄与率は 1.0e-12 未満、主成分は 1.0e-9 未満しか違いませんでした。**

### 実行して結果を表示する

```text
データ件数: 100, 列数: 15
寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581
累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）
第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328
第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405
Rubix ML の PCA との差: 寄与率 2.78e-16, 主成分 1.97e-14
```

表示のテストは、戻り値の文字列をそのまま比べるだけです。`run()` が標準出力に書かずに文字列を返す作りなので（第 1 章から一貫しています）、出力を横取りするヘルパーは要りません。

```php
$this->assertSame(
    <<<'TEXT'
        データ件数: 100, 列数: 15
        寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581
        累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）
        第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328
        第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405
        Rubix ML の PCA との差: 寄与率 2.78e-16, 主成分 1.97e-14

        TEXT,
    Chapter13::run(),
);
```

PHP の **nowdoc**（`<<<'TEXT'`）は変数を展開しないヒアドキュメントです。閉じ記号のインデントぶんが各行から取り除かれるので、テストの中に字下げしたまま書けます。

### ほかの言語版との一致

**寄与率も、必要な主成分の数も、係数の 3 桁目まで [Java 版](../java/13-principal-component-analysis.md)・[Scala 版](../scala/13-principal-component-analysis.md)・[Clojure 版](../clojure/13-principal-component-analysis.md)・[Elixir 版](../elixir/13-principal-component-analysis.md) と一致しました。** この章は分割も乱数も使わず、計算は中心化・積・固有値分解だけです。前処理（ダミー変数の作り方・平均値での補完・件数で割る標準偏差）も、符号の規則も同じにしたので、**固有値分解の実装だけが違っても同じ値になりました**。

Elixir 版は `Nx.LinAlg.eigh`、Java 版・Scala 版・Clojure 版は Tribuo から借りた固有値分解、PHP 版は自作の Jacobi 法です。3 通りの固有値分解が、4 桁の表示ではまったく同じ答えを返しています。

### 結果を読む

- **第 1 主成分だけで 4 割を説明する**: 15 列のばらつきのうち 41.1% を、1 本の軸で説明できます
- **15 列を 6 本の軸に要約できる**: 累積寄与率 0.8 を目安にすると、6 つの主成分で全体の 84.3% を説明できます
- **第 1 主成分は「産業化・都市化」の軸と読める**: 非小売業の土地の割合（INDUS）、窒素酸化物の濃度（NOX）、固定資産税率（TAX）が同じ向きに強く効いています
- **第 2 主成分は「住環境のよさ」の軸と読める**: 住宅価格（PRICE）、犯罪率が低い地区（CRIME_low）、部屋数（RM）が同じ向きに効いています

主成分の「意味」は計算で決まるものではなく、係数を見た人間の解釈です。符号の向きも `normalizeSigns()` の規則で決めたものなので、「値が大きいほど都市化している」のか「小さいほど」なのかは、係数の符号とあわせて読む必要があります。

## 13.13 Notebook による探索と可視化

PHP 版では Notebook と可視化の節を設けません。主成分の散布図、累積寄与率の折れ線（スクリープロット）、ローディングのヒートマップは、[Python 版の第 13 章](../python/13-principal-component-analysis.md) と [Kotlin 版の第 13 章](../kotlin/13-principal-component-analysis.md) の「Notebook による探索と可視化」の節を参照してください。

## 13.14 何が置き換えられて、何が置き換えられないのか

この章で、Rubix ML が自作を置き換えられた部分はありませんでした。**動くものは持っているのに、結果を渡してくれない** からです。

| 手順 | 自作 | Rubix ML で代われるか |
|------|------|-------------------|
| 中心化 | `center()` | 代われる（`transform()` の中で起きる） |
| 分散共分散行列 | `covarianceMatrix()` | 内部にあるが読めない |
| 固有値分解 | `eigenDecomposition()`（Jacobi 法） | 内部にあるが読めない |
| 符号をそろえる | `normalizeSigns()` | 規則が無い |
| 射影 | `transform()` | **代われる** |
| 寄与率 | `fit()` の中 | `lossiness()` から復元は要る |
| 主成分の数の目安 | `componentsNeeded()` | 無い |
| 係数の大きい列 | `topLoadings()` | 無い |

素直に置き換えられるのは射影だけです。ただし射影だけ借りても、主成分の意味を読むことはできません。

**「結果を読むための道具」（累積寄与率がしきい値に届く主成分の数、係数の大きい列）はどのライブラリにも無い**、というのは scikit-learn でも Scholar でも同じでした。これは自分で書く部分です。この章で PHP 版だけが違ったのは、**そこから一段手前の「学習した中身」まで自分で持つ必要があった** ところです。

## 13.15 品質チェック

`nix develop .#php` の中で、整形の検査・静的解析・テスト・カバレッジをまとめて実行します。

```console
$ npx gulp apps:check:php
Found 0 of 90 files that can be fixed in 1.200 seconds, 18.00 MB memory used
 [OK] No errors
…
OK (353 tests, 3752 assertions)
行カバレッジ: 99.00% (2388/2412), しきい値: 90.00%
```

第 13 章のテストは、部品のテスト 22 件と実データのテスト 3 件です。学習データが無い環境では `data` グループのテストがスキップされ、テスト全体は成功します。`src/Chapter13.php` の行カバレッジは 99.14% で、届かないのは Rubix ML から戻ってきた値が数値でなかったときの分岐です。

PHPStan のレベル 9 に 4 回止められました。すべて **「その配列は `list` とは限らない」** という同じ種類の指摘です。

```text
Method GettingStartedMl\Chapter13::rotate() should return
array{list<list<float>>, list<list<float>>} but returns
array{list<array<int, float>>, list<array<int, float>>}.
💡 array<int, float> might not be a list.
```

Jacobi 法の回転で `$a[$k][$p] = ...` と添字を指定して書き換えると、PHPStan から見て「0 から順に詰まった配列」である保証が消えます。実際には消えていないので、返すときに並べ直して事実を示しました。

```php
// 添字を書き換えたので、PHPStan に list であることを示すために並べ直す。
return [array_map(array_values(...), $a), array_map(array_values(...), $v)];
```

`@phpstan-ignore` で黙らせず、配列を作り直すほうを選んでいます。**この指摘は「PHP の配列は本当は順序つきのハッシュである」という言語の事実を思い出させてくれるもの** で、消すべきノイズではありません。単位ベクトルを作るところも、`$probe = $mean; $probe[$i] += 1.0;` と書いたら同じ指摘が出たので、`array_map()` で最初から作るように直しました。

この章で追加した依存はありません。MathPHP（第 7 章から）と Rubix ML（第 3 章から）をそのまま使っています。

## 13.16 まとめ

この章では、主成分分析を PHP の TDD で自作し、Rubix ML の `PrincipalComponentAnalysis` と突き合わせました。

1. **ライブラリの固有ベクトルは実データで落ちた** — MathPHP は固有値を返せるのに、同じ固有値を渡した固有ベクトルの計算が「それは固有値ではない」と失敗する。掃き出し法で零空間を探す実装が、15×15 の浮動小数点に耐えなかった
2. **Jacobi 法で固有値分解ごと自作した** — 回転を重ねるだけで固有値と固有ベクトルが同時に出るので、落ちた手順を通らない
3. **Rubix ML の PCA は寄与率も主成分も公開していない** — 読めるのは `lossiness()` という 1 つの数と `transform()` だけ
4. **それでも両方復元できた** — 「平均 + 単位ベクトル」を `transform()` に通して主成分を、主成分の数を変えた `lossiness()` の差から寄与率を取り出し、実データで 1e-12・1e-14 の桁まで一致した
5. **Java 版・Scala 版・Clojure 版・Elixir 版と数値が一致した** — 3 通りの違う固有値分解が、同じ前処理と同じ符号の規則のもとで同じ答えを返した

PHP 版ならではの学びもありました。

- **「ライブラリにある」の先に 2 つの段がある** — 「動くか」と「結果を読み出せるか」。第 3 章で `ClassificationTree` に「あるけれどそのままでは比べられない」を見たのに続いて、この章は「あるけれど落ちる」と「あるけれど見せてくれない」だった
- **リフレクションより、性質に依存する** — `protected` のプロパティをこじ開ける代わりに「変換は線形である」という性質を使った。ライブラリの内部の名前が変わっても壊れない
- **止め方の約束が数値の差になる** — MathPHP の固有値と自作の固有値は 3.98e-6 違う。どちらも間違っていない。収束の判定が違うだけで、それを言えるようにテストに書いた
- **`usort()` が安定なのは PHP 8.0 から** — 同じ順位の並びに寄りかかるなら、そのことをテストに書く
- **nowdoc は複数行の期待値に向く** — 変数を展開せず、閉じ記号のインデントぶんを取り除いてくれる
- **PHPStan の `list` の指摘は言語の事実である** — 添字を指定して書き換えた配列は、もう「詰まった配列」の保証を持たない。黙らせずに並べ直した

次の章では、同じ教師なし学習でも「行をグループに分ける」側、K-means によるクラスタリングを実装します。**Rubix ML の `KMeans` には初期中心を渡す口があります。** Elixir 版の Scholar に無かったものが、今度はこちらにあります。

PHP 版のほかの章は [PHP 版のトップ](index.md) から辿れます。
