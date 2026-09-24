---
type: Article
title: "第 11 章: 評価指標と交差検証"
description: "混同行列・適合率・再現率・F 値・ROC 曲線と AUC・K 分割交差検証を PHP の TDD で自作し、Rubix ML の CrossValidation\\Metrics・MulticlassBreakdown・KFold と突き合わせる。Rubix ML の FBeta がクラスごとの平均であること、誤差の指標が符号を反転して返ること、fold が余りを捨てること、stratifiedFold が '0'・'1' のラベルを整数に化けさせて分類器が落ちることを実測で示す。"
tags: [article,getting-start-ml,php]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 11 章: 評価指標と交差検証

## 11.1 はじめに

ここまでの章では、モデルの良し悪しを **正解率** ひとつで測ってきました。第 3 章の決定木も、第 10 章のロジスティック回帰もランダムフォレストも、「テストデータの何割を当てたか」で比べています。

しかし正解率は、**どちらを間違えたか** を隠します。生存者を見逃したのか、死んだ人を生存と言ったのか、数字は区別しません。この章では、その内訳を見るための指標 — 混同行列・適合率・再現率・F 値・ROC 曲線と AUC — を自作します。

もうひとつの主題は **交差検証** です。第 2 章から使ってきた「1 回だけ訓練データとテストデータに分ける」やり方は、たまたまの分け方に結果が左右されます。データを K 個に分け、順番にテストデータの役を回す K 分割交差検証で、その振れ幅を減らします。

そしてこの章には、PHP 版ならではの収穫があります。Rubix ML には評価指標も交差検証も一式そろっているので、[Elixir 版](../elixir/11-evaluation-metrics-and-cross-validation.md) と同じく突き合わせができます。ただし **そろっていることと、そのまま比べられることは別** です。この章では Rubix ML の 4 つの癖を実測で見つけます。そのうちのひとつは、第 8 章で見つけた PHP の言語の癖が、**ライブラリの内部で牙を剥く** 例でした（11.10 節）。

Notebook による探索と可視化の節は設けません。ROC 曲線を描く手順は [Python 版](../python/11-evaluation-metrics-and-cross-validation.md) と [Kotlin 版](../kotlin/11-evaluation-metrics-and-cross-validation.md) を参照してください。

## 11.2 正解率だけでは足りない理由

100 人のうち 5 人だけが病気で、残り 95 人が健康だとします。「全員が健康」と答えるだけの検査は、正解率 0.95 です。数字はよく見えますが、病気の人を 1 人も見つけていません。

正解と予測の組み合わせは 4 通りあります。正例（この章では「生存」）をどう当てたか・外したかで並べたものが **混同行列** です。

| | 予測が正例 | 予測が負例 |
| :--- | :--- | :--- |
| **正解が正例** | 真陽性（tp） | 偽陰性（fn） |
| **正解が負例** | 偽陽性（fp） | 真陰性（tn） |

ここから 3 つの指標が出ます。

- **適合率（precision）** = tp / (tp + fp)。正例と予測したうち、本当に正例だった割合
- **再現率（recall）** = tp / (tp + fn)。本当の正例のうち、正例と予測できた割合
- **F 値（F1 score）** = 適合率と再現率の調和平均

先ほどの検査は、適合率も再現率も 0 です。正解率 0.95 が隠していたものが、ここで見えます。

## 11.3 TODO リストの作成

```text
- [ ] 混同行列を数える
- [ ] 適合率・再現率・F 値を求める
- [ ] 正解率と平均二乗誤差を求める
- [ ] ROC 曲線と AUC を求める
- [ ] K 分割交差検証の分け方を作る
- [ ] 分割ごとに学習して採点する
- [ ] Rubix ML の評価指標・レポート・KFold と突き合わせる
- [ ] 実データ（Survived・cinema）で交差検証する
```

## 11.4 混同行列を数える

### Red: 最初のテスト

答えの分かっている 6 件を用意します。

```php
    private function actual(): array
    {
        return ['はい', 'はい', 'はい', 'いいえ', 'いいえ', 'いいえ'];
    }

    private function predicted(): array
    {
        return ['はい', 'はい', 'いいえ', 'はい', 'いいえ', 'いいえ'];
    }

    #[TestDox('混同行列は正解と予測の組を 4 つのますに数える')]
    public function test混同行列は正解と予測の組を4つのますに数える(): void
    {
        $cm = Chapter11::confusionMatrix($this->actual(), $this->predicted(), 'はい');

        $this->assertSame([2, 1, 1, 2], [$cm->tp, $cm->fp, $cm->fn, $cm->tn]);
    }
```

### Green: `fn` はプロパティ名に使える

```php
final readonly class ConfusionMatrix
{
    public function __construct(
        public int $tp,
        public int $fp,
        public int $fn,
        public int $tn,
    ) {
    }
}
```

ここで PHP の小さな幸運があります。**`fn` は無名関数の記法（`fn () => …`）に使う語ですが、プロパティ名や名前付き引数としては普通に書けます。** `$cm->fn` も `new ConfusionMatrix(fn: 3)` も通ります。

[Elixir 版](../elixir/11-evaluation-metrics-and-cross-validation.md) は `fn` が予約語なので `cm.fn` と書けず、読むたびにパターンマッチで別の名前に束縛していました。同じ 4 文字が、言語によって書けたり書けなかったりします。

数えるところは `match (true)` で 4 通りを並べます。

```php
        foreach ($actual as $index => $label) {
            $isPositive = $label === $positive;
            $saidPositive = $predicted[$index] === $positive;

            match (true) {
                $isPositive && $saidPositive => ++$tp,
                $saidPositive => ++$fp,
                $isPositive => ++$fn,
                default => ++$tn,
            };
        }
```

最初は 4 つの条件をすべて書いていました（`!$isPositive && $saidPositive` など）。PHPStan レベル 9 が **「その否定は常に真である」** と指摘してきます（`booleanNot.alwaysTrue`）。`match (true)` は上から順に評価するので、2 つ目に来た時点で 1 つ目が偽だと確定しているからです。静的解析に言われて書き直した結果のほうが短くなりました。

### 件数が違うときは黙って切り詰めない

```php
    #[TestDox('正解と予測の件数が違えば混同行列を作れない')]
    public function test正解と予測の件数が違えば混同行列を作れない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('正解と予測の件数が違います: 2 と 1');

        Chapter11::confusionMatrix(['a', 'b'], ['a'], 'a');
    }
```

`foreach` は短いほうで止まるので、放っておくと **件数がずれたまま「それらしい数字」が出ます**。第 7 章の `residuals()` と同じ考え方で、先に件数を検査します。

## 11.5 適合率・再現率・F 値

### 明白な実装

3 つとも定義そのものなので、明白な実装で書きます。

```php
    public function precision(): float
    {
        return self::ratio($this->tp, $this->tp + $this->fp);
    }

    public function recall(): float
    {
        return self::ratio($this->tp, $this->tp + $this->fn);
    }

    public function f1Score(): float
    {
        $precision = $this->precision();
        $recall = $this->recall();

        return self::ratio(2.0 * $precision * $recall, $precision + $recall);
    }
```

### 分母が 0 になる場合

正例と 1 件も予測しなければ、適合率の分母は 0 です。PHP 8 では `1.0 / 0.0` も `DivisionByZeroError: Division by zero` を投げます（PHP 7 までは `INF` と警告でした）。第 10 章で「`exp(1000)` も `log(0)` も落ちずに `INF`・`-INF` を返す」ことを見つけたので、**PHP の数値演算はどれも静かだと思い込みかけていた** のですが、除算だけは違います。落ちるのも困るので、0 を返します。

```php
    public static function ratio(float $numerator, float $denominator): float
    {
        return $denominator === 0.0 ? 0.0 : $numerator / $denominator;
    }
```

ここで第 2 章の教訓が効きます。**`?:` や `if ($denominator)` で書くと、0.0 が偽として扱われるのは正しいのですが、`-0.0` も `0.0` も偽になる一方で「本当に比べたいのは 0 との等価性」** なので、`=== 0.0` と明示するほうが読み手に意図が伝わります。

```php
    #[TestDox('分母が 0 になる指標は NaN ではなく 0 を返す')]
    public function test分母が0になる指標はNanではなく0を返す(): void
    {
        $empty = new ConfusionMatrix(0, 0, 0, 3);

        $this->assertSame([0.0, 0.0, 0.0], [$empty->precision(), $empty->recall(), $empty->f1Score()]);
    }
```

`assertSame` を使っているので、**`0.0` が返ることまで** 確かめています。`assertEquals` だと `0` でも `false` でも通ってしまいます。

## 11.6 正解率と平均二乗誤差

正解率は 2 値に限りません。ラベルが何種類あっても「一致した割合」です。

```php
    public static function accuracy(array $actual, array $predicted): float
    {
        self::requireSameSize($actual, $predicted);

        $correct = 0;

        foreach ($actual as $index => $label) {
            if ($label === $predicted[$index]) {
                ++$correct;
            }
        }

        return ConfusionMatrix::ratio($correct, count($actual));
    }
```

回帰には **平均二乗誤差（MSE）** を足します。第 7 章で MAE と RMSE を書いたので、その部品を再利用します。

```php
    public static function meanSquaredError(array $actual, array $predicted): float
    {
        self::requireSameSize($actual, $predicted);

        return Chapter07::sumOfSquares(Chapter07::residuals($actual, $predicted)) / count($actual);
    }
```

### 学習用テスト: 外れた予測への敏感さ

MSE と MAE の違いを、テストで言葉にしておきます。

```php
    #[TestDox('MSE は大きく外れた 1 件に敏感で MAE より大きくなる')]
    public function testMseは大きく外れた1件に敏感でMaeより大きくなる(): void
    {
        $actual = [0.0, 0.0, 0.0, 0.0];
        $small = [1.0, 1.0, 1.0, 1.0];
        $large = [0.0, 0.0, 0.0, 4.0];

        // MAE はどちらも 1.0 で区別が付かないが、MSE は外れた 1 件を重く見る
        $this->assertEqualsWithDelta(1.0, Chapter07::meanAbsoluteError($actual, $small), 1e-12);
        $this->assertEqualsWithDelta(1.0, Chapter07::meanAbsoluteError($actual, $large), 1e-12);
        $this->assertEqualsWithDelta(1.0, Chapter11::meanSquaredError($actual, $small), 1e-12);
        $this->assertEqualsWithDelta(4.0, Chapter11::meanSquaredError($actual, $large), 1e-12);
    }
```

「1 ずつ 4 回外す」と「4 だけ 1 回外す」は、MAE では同じ 1.0 です。MSE は 1.0 と 4.0 に分かれます。**どちらが「悪い」かは問題によって違う** ので、指標を選ぶこと自体が設計です。

MSE の平方根が第 7 章の RMSE であることも、テストで結んでおきます。2 つの実装が同じ式の別の書き方であることを、実行できる形で残すためです。

## 11.7 ROC 曲線と AUC

### 閾値を動かすとどうなるか

分類器の多くは、まず「正例らしさ」のスコアを出し、それを閾値と比べて正例か負例かを決めます。閾値を下げれば再現率は上がりますが、偽陽性も増えます。**閾値を高いほうから下げていったときの（偽陽性率, 真陽性率）の軌跡** が ROC 曲線で、その下の面積が **AUC** です。

### 曲線の 1 点も readonly class

```php
final readonly class RocPoint
{
    public function __construct(
        public ?float $threshold,
        public float $falsePositiveRate,
        public float $truePositiveRate,
    ) {
    }
}
```

曲線の始まりは「どれも正例と予測しない」点で、そこには閾値がありません。Elixir 版は `:infinity` というアトムを置きました。PHP には `INF` がありますが、**「無限に大きい数」ではなく「閾値が無い」ことを表したい** ので `null` にしました。`?float` と書けば、静的解析も「ここは null がありうる」と知ります。

### 同じスコアは 1 つの点にまとめる

同じスコアの行が複数あるとき、そのあいだで線を引くと曲線が階段状に嘘をつきます。同じスコアはまとめて 1 点にします。

Elixir 版は `Enum.group_by(&elem(&1, 0))` でスコアをキーにグループ化しました。**PHP ではこれができません。**

```php
        // スコアは浮動小数点なので、配列の鍵にして数えることはできない（PHP は鍵を整数に丸める）。
        // 並べ替えてから、同じスコアが続くあいだ足し込む。
        usort($pairs, static fn (array $a, array $b): int => $b[0] <=> $a[0]);
```

PHP の配列のキーは整数か文字列だけで、`$group[0.9]` と書くと **キーが整数 0 に丸められます**。`$group[0.5]` も 0 です。実際に試すと `Deprecated: Implicit conversion from float 0.9 to int loses precision` が出て、**2 つのスコアが 1 つのキーに潰れます**。通知は出るものの、例外ではないので放っておけば通ってしまいます。第 3 章で「数字だけの文字列のキーが整数に化ける」ことを見つけ、第 8 章で「ラベルをキーにすると型が往復しない」ことに刺されました。**PHP の配列のキーは、3 度目の落とし穴** です。

そこで「キーにせず、並べ替えてから走る」書き方にしました。同じスコアが続くあいだ tp と fp を足し、スコアが変わる直前で 1 点を打ちます。

```php
        foreach ($pairs as $index => [$score, $isPositive]) {
            if ($isPositive) {
                ++$tp;
            } else {
                ++$fp;
            }

            $next = $pairs[$index + 1] ?? null;

            if ($next === null || $next[0] !== $score) {
                $curve[] = new RocPoint((float) $score, $fp / $negatives, $tp / $positives);
            }
        }
```

テストで性質を固定します。

```php
    #[TestDox('同じスコアの正例と負例は 1 つの点にまとめて AUC が 0.5 になる')]
    public function test同じスコアの正例と負例は1つの点にまとめてAucが05になる(): void
    {
        $curve = Chapter11::rocCurve([0.5, 0.5, 0.5, 0.5], [true, true, false, false]);

        $this->assertCount(2, $curve);
        $this->assertEqualsWithDelta(0.5, Chapter11::auc($curve), 1e-12);
    }
```

4 件すべて同じスコアなら、始点を含めて点は 2 つだけ。AUC はでたらめと同じ 0.5 になります。

### 台形則で面積を求める

```php
    public static function auc(array $curve): float
    {
        $area = 0.0;

        for ($i = 1; $i < count($curve); ++$i) {
            $left = $curve[$i - 1];
            $right = $curve[$i];
            $area += ($right->falsePositiveRate - $left->falsePositiveRate)
                * ($left->truePositiveRate + $right->truePositiveRate) / 2.0;
        }

        return $area;
    }
```

完全に分けられるスコアなら 1.0、順が逆なら 0.0 になることをテストで確かめました。

**Rubix ML には ROC 曲線も AUC もありません。** `CrossValidation\Metrics` に並ぶのは `Accuracy`・`FBeta`・`MCC`・`Informedness`・`BrierScore` などで、閾値を動かす指標は入っていません。この節の 2 つの関数は、第 3 章の決定木や第 8 章の前処理パイプラインと同じく **自作が最終実装** になります。Elixir 版が Scholar の `roc_auc_score/4` と突き合わせられたのとは逆の関係です。「ライブラリがそろっている版」でも、全部そろっているわけではありません。

## 11.8 K 分割交差検証

### なぜ分け方を入れ替えるのか

1 回だけ分けると、テストデータにたまたま難しい行が集まったかどうかで結果が変わります。K 個に分けて順番にテストデータの役を回せば、**すべての行がちょうど 1 回テストされ**、K 回の平均を取れます。

### 分け方も readonly class

```php
final readonly class Fold
{
    /**
     * @param list<int> $train 訓練データにする行の位置
     * @param list<int> $test  テストデータにする行の位置
     */
    public function __construct(
        public array $train,
        public array $test,
    ) {
    }
}
```

行そのものではなく **行の位置** を持ちます。こうすると、同じ分け方を特徴量にも正解ラベルにも使い回せます。

```php
    public static function kFold(int $nSamples, int $nSplits, int $seed): array
    {
        self::checkSplits($nSamples, $nSplits);

        return self::folds(Random::shuffle(range(0, $nSamples - 1), $seed), $nSplits);
    }
```

並べ替えには第 2 章で自作した `Random::shuffle()` を使います。`java.util.Random` と同じ線形合同法なので、**JVM の言語版・Elixir 版と同じ分け方** になります。ここをそろえておくと、この章の 6 つの数字がすべて他言語版と一致します。

余りの配り方も決めごとです。

```php
        for ($index = 0; $index < $nSplits; ++$index) {
            $size = intdiv($total, $nSplits) + ($index < $total % $nSplits ? 1 : 0);
```

10 件を 3 分割すれば `[4, 3, 3]` になります。余りを先頭から 1 件ずつ配るので、**どの行も必ず 1 回はテストされます**。この性質がテストで固定されていることが、11.10 節で効いてきます。

```php
    #[TestDox('K 分割はすべての行をちょうど 1 回テストデータにする')]
    public function testK分割はすべての行をちょうど1回テストデータにする(): void
    {
        $folds = Chapter11::kFold(10, 3, 0);
        $tested = [];

        foreach ($folds as $fold) {
            $tested = [...$tested, ...$fold->test];
        }

        sort($tested);
        $this->assertSame(range(0, 9), $tested);
    }
```

並べ替えない `kFoldSequential()` も用意しました。分け方の性質を確かめるテストでは、乱数が挟まらないほうが読みやすいからです。

## 11.9 評価関数もモデルも、ただの Closure

### 交差検証の手順を 1 つの関数にする

交差検証に必要なものは 3 つです。**分け方**、**訓練データから予測する関数を作るもの**、**正解と予測を採点するもの**。後ろの 2 つはどちらも関数なので、`Closure` のまま渡します。

```php
    public static function crossValidate(
        Closure $trainer,
        array $x,
        array $t,
        array $folds,
        Closure $metric,
    ): array {
        return array_map(
            static function (Fold $fold) use ($trainer, $x, $t, $metric): float {
                $predict = $trainer(self::pick($x, $fold->train), self::pick($t, $fold->train));

                return $metric(
                    self::pick($t, $fold->test),
                    array_map($predict, self::pick($x, $fold->test)),
                );
            },
            $folds,
        );
    }
```

インターフェースを 1 つも定義していません。「訓練データを受け取って、予測する関数を返す関数」という説明が、そのまま型になります。

### 型で書けるが、書き方に癖がある

PHPStan の型として書くとこうなります。

```php
     * @param Closure(list<array<string, float>>, list<TLabel>): (Closure(array<string, float>): TLabel) $trainer
```

最初は内側の `Closure` を括弧で囲まずに書いて、**PHPDoc の解析エラー**（`phpDoc.parseError`）で止められました。関数を返す関数の型は、戻り値側を括弧で包む必要があります。

もうひとつの癖は、**返す `Closure` の引数に型を書けない** ことです。PHP の型宣言に `list<string>` はないので `array` としか書けず、PHPStan は `array given` と怒ります。`return` 文に PHPDoc を付けても読んでくれませんでした。結局、本体の先頭で `@var` を書く形に落ち着きました。

```php
        return static function (array $x, array $t) use ($columns, $maxDepth): Closure {
            /** @var list<array<string, float>> $x */
            /** @var list<string> $t */
            $tree = DecisionTree::fit($x, $t, $columns, $maxDepth);

            return static fn (array $features): string => DecisionTree::predictOne($tree->tree, $features);
        };
```

Elixir 版は型を書かないので、この摩擦は起きませんでした。**型を使うと決めた版（ADR 013）の代償が、いちばんはっきり出た場所** です。得たものもあります。第 7 章の `Chapter07::fit()` に間違った形の配列を渡すことは、もう起こりません。

### 三角測量: 評価関数を差し替える、回帰も同じ関数で

同じ分け方・同じ分類器のまま、評価関数だけを差し替えます。

```php
    #[TestDox('評価関数を差し替えれば同じ分割で別の指標を測れる')]
    public function test評価関数を差し替えれば同じ分割で別の指標を測れる(): void
    {
        $folds = Chapter11::kFoldSequential(6, 3);
        $trainer = Chapter11::treeTrainer(['a'], 1);
        $recall = Chapter11::classificationMetric(
            static fn (ConfusionMatrix $cm): float => $cm->recall(),
            '大',
        );

        // 並べ替えない分割では、最初のテストデータが「小」2 件だけになる。
        // 正例が 1 件も無いので再現率は 0 で、正解率が 1.0 でも指標によって値が変わる
        $this->assertEqualsWithDelta(
            [0.0, 1.0, 1.0],
            Chapter11::crossValidate($trainer, $this->toyX(), $this->toyT(), $folds, $recall),
            1e-12,
        );
    }
```

このテストは、書いたときの期待（`[1.0, 1.0, 1.0]`）が外れて Red になりました。調べると、並べ替えない分割の 1 つ目のテストデータには正例が 1 件も入っていませんでした。**指標の定義どおりの正しい 0.0** です。11.2 節で述べた「正解率は内訳を隠す」が、そのまま自分のテストで起きたわけです。期待値を直し、なぜそうなるかをコメントに残しました。

回帰でも同じ `crossValidate()` が使えます。分類器を `linearTrainer()` に、指標を `meanSquaredError()` に差し替えるだけです。

### 遅延しない

Elixir 版の `cross_validate/5` は `Stream.map/2` を返すので、取り出した分だけ学習します。PHP の `array_map` はその場で全部走ります。遅延させたければ `Generator` を返す形にできますが、この章では分割が 5 個しかないので素直に配列にしました。**遅延の粒度は言語の道具立てで変わります** が、必要になってから変える判断でよい場所です。

## 11.10 Rubix ML の評価指標と突き合わせる

ここからが、この章の本題です。Rubix ML には評価指標が一式そろっています。**4 つの癖** を順に見つけました。

### 癖 1: 誤差の指標は符号が反転している

```php
    #[TestDox('Rubix ML の回帰の指標は符号を反転すると自作と一致する')]
    public function testRubixMlの回帰の指標は符号を反転すると自作と一致する(): void
    {
        $actual = [1.0, 2.0, 3.0, 4.0];
        $predicted = [1.5, 1.0, 4.0, 3.0];
        $theirs = Chapter11::rubixRegressionScores($actual, $predicted);

        $this->assertEqualsWithDelta(Chapter11::meanSquaredError($actual, $predicted), $theirs['mse'], 1e-12);
        $this->assertEqualsWithDelta(Chapter07::rootMeanSquaredError($actual, $predicted), $theirs['rmse'], 1e-12);
        $this->assertEqualsWithDelta(Chapter07::meanAbsoluteError($actual, $predicted), $theirs['mae'], 1e-12);
    }
```

ソースを読むと `MeanSquaredError::score()` の最後が `return -($error / count($predictions));` でした。**Rubix ML の `Metric` は「大きいほどよい」に統一されている** ので、誤差の指標は 0 以下の数になります。`RMSE` にいたっては `-sqrt(-parent::score(...))` という二重の反転です。

符号を戻せば 1e-12 まで一致します。知らずに「MSE は -142.3 でした」と報告してしまうのを防ぐために、符号の反転は関数の中に閉じ込めました。

```php
        return [
            'mse' => -(new MeanSquaredError())->score($predicted, $actual),
```

もうひとつ、引数の順にも注意が要ります。**`score(予測, 正解)` の順** です。本シリーズのほかの関数は「正解が先」で統一しているので、境界で入れ替えています。

### 癖 2: `FBeta` は 2 値でも「正例だけの F 値」ではない

```php
        $classes = array_unique(array_merge($predictions, $labels));
        …
        $precision = Stats::mean(array_map([self::class, 'precision'], $truePos, $falsePos));
        $recall = Stats::mean(array_map([self::class, 'recall'], $truePos, $falseNeg));
```

`FBeta` は **すべてのクラスの適合率と再現率をそれぞれ平均してから**、その平均どうしで調和平均を取ります（マクロ平均）。正例と負例の件数がちょうど釣り合っているときはたまたま一致しますが、偏ると別の数になります。

```php
        // 正例と負例の件数が偏ると差が出る
        $actual = ['はい', 'はい', 'はい', 'いいえ'];
        $predicted = ['はい', 'はい', 'いいえ', 'いいえ'];
        $binary = Chapter11::confusionMatrix($actual, $predicted, 'はい')->f1Score();

        // 自作は「はい」の tp=2, fp=0, fn=1 だけを見て 0.8。FBeta は「いいえ」の
        // 適合率 0.5・再現率 1.0 も平均に入れるので 0.7895 になる
        $this->assertEqualsWithDelta(0.8, $binary, 1e-12);
        $this->assertEqualsWithDelta(0.7894736842105263, Chapter11::rubixFBeta($actual, $predicted), 1e-12);
```

**0.8 と 0.7895 は、どちらも正しい F 値です。** 定義が違うだけです。このテストは「一致すること」ではなく **「一致しないことと、その理由」** を固定しています。ライブラリとの突き合わせでは、こういうテストのほうが役に立つ場面があります。

では、自作の 2 値の指標と比べられる相手はいないのでしょうか。います。

### 適合率・再現率・F 値は `MulticlassBreakdown` の正例の行と一致する

`CrossValidation\Reports\MulticlassBreakdown` は、**クラスごとに 2 値の指標** を出します。正例の行だけを読めば、自作と同じ定義です。

```php
    public static function rubixClassScores(array $actual, array $predicted, string $positive): array
    {
        $report = (new MulticlassBreakdown())->generate($predicted, $actual)->toArray();
        $classes = $report['classes'];
        …
        return [
            'precision' => $row['precision'],
            'recall' => $row['recall'],
            'f1Score' => $row['f1 score'],
        ];
    }
```

キーが `'f1 score'`（空白入り）なのは Rubix ML の流儀です。人工データでも、実データ（Survived の 1 つ目の分割）でも 1e-12 まで一致しました。

`Accuracy` も自作の正解率と一致します。ただし Rubix ML は `$prediction == $labels[$i]` と **ゆるい比較** で数えるので、`'1'` と `1` を同じと見なします。自作は `===` です。この章のデータでは差が出ませんでしたが、癖として覚えておく値打ちがあります。

### 癖 3: `ConfusionMatrix` レポートは行が予測、列が正解

```php
        foreach ($predictions as $i => $prediction) {
            ++$matrix[$prediction][$labels[$i]];
        }
```

外側のキーが **予測**、内側が **正解** です。混同行列は「行が正解、列が予測」と書く教科書のほうが多く、Scholar もそちらです。向きを取り違えると偽陽性と偽陰性が入れ替わるので、テストで向きを固定しました。

```php
        // [予測]['はい'] の行に、正解が「はい」だった件数（tp）と「いいえ」だった件数（fp）が並ぶ
        $this->assertSame($cm->tp, $theirs['はい']['はい']);
        $this->assertSame($cm->fp, $theirs['はい']['いいえ']);
```

### 癖 4: `fold()` は余りの行をどの分割にも入れない

```php
        $n = (int) floor($this->numSamples() / $k);
        …
        while (count($folds) < $k) {
            $folds[] = self::quick(
                array_splice($samples, 0, $n),
```

`floor(件数 / 分割数)` をすべての分割の大きさにするので、**余りの行はどの分割のテストデータにも入りません**。

```php
        $this->assertSame([4, 3, 3], $mine);
        $this->assertSame([3, 3, 3], Chapter11::rubixFoldTestSizes(10, 3));
```

**Elixir 版で Scholar の `k_fold_split/2` に見つけたのとまったく同じ振る舞い** です。2 つの言語の、無関係なライブラリが、同じ近道を選んでいました。「全部の行が必ず 1 回はテストされる」という交差検証の前提のほうが、実装の都合より優先されるとは限らないわけです。

### `KFold` にシードを渡す口が無い

`KFold::test()` は最初に `$dataset->randomize()` を呼びます。**シードを渡す引数はありません。** 第 3 章で `ClassificationTree` が `array_rand` を使うために決定的でなかったのと同じ形です。しかも返るのは **K 回の平均だけ** で、分割ごとのスコアは取り出せません。

数字そのものは固定できないので、テストでは範囲だけを押さえました。実データで 5 回走らせると `0.7819 0.7751 0.7763 0.7718 0.7797` と散らばります。自作の 0.7811 はその中に収まっています。**「ライブラリにある」ことと「毎回同じ答えが出る」ことも別** です。

### 第 8 章の落とし穴が、ライブラリの中で牙を剥いた

その `KFold` を Survived のデータに当てたところ、例外で止まりました。

```console
Rubix\ML\Exceptions\InvalidArgumentException: Classifiers require categorical labels, continuous given.
```

`Survived` 列のラベルは `'0'` と `'1'` の **文字列** で、`new Labeled(...)` を作った直後は確かに `categorical` と判定されます。ところが `KFold::test()` はラベルが categorical のとき `stratifiedFold()` を呼び、その中の `stratifyByLabel()` が **ラベルを配列のキーにしてグループ化します**。

ここで PHP の癖が出ます。**キーにした `'0'` と `'1'` は整数の 0 と 1 に化けます。** 折り返しで作り直されたデータセットのラベルは整数になり、`DataType::detect(0)` は「連続値」と答え、分類器が受け取りを拒みます。

```php
    #[Group('data')]
    #[TestDox('Rubix ML の KFold は 0 と 1 のラベルを連続値と見なして落ちる')]
    public function testRubixMlのKFoldは0と1のラベルを連続値と見なして落ちる(): void
    {
        ['x' => $x, 't' => $t] = $this->survivedData();

        // stratifiedFold がラベルを配列のキーにするので、'0' と '1' が整数 0 と 1 に化ける。
        // 整数のラベルは連続値と判定され、分類器が受け取れなくなる
        $this->expectException(RubixInvalidArgumentException::class);
        $this->expectExceptionMessage('Classifiers require categorical labels, continuous given.');

        Chapter11::rubixKFoldAccuracy($x, $t, Chapter11::SURVIVED_COLUMNS, Chapter11::TREE_DEPTH, 5);
    }
```

第 8 章で「ラベルを配列のキーにすると型が往復しない（`'1'` は整数 1 になる）」ことを自分のコードで見つけました。**同じ落とし穴に、ライブラリのほうが落ちていた** わけです。回避策は、ラベルを数字に見えない値に付け替えることです。

```php
        $named = array_map(static fn (string $label): string => $label === '1' ? '生存' : '死亡', $t);
```

これで動きます。第 3 章で書いた **「型の道具をどれだけ使っても、言語の癖は型では守れない」** が、今度は他人のコードで証明されました。PHPStan レベル 9 は `Labeled` のコンストラクタも `stratifiedFold()` も通します。見つけたのはテストです。

## 11.11 実データで評価する

### 簡略化した前処理

交差検証にかけるには、欠損値や文字列の列を先に数値にしておく必要があります。前処理パイプラインは第 8 章で扱ったので、この章ではそれを簡略化したものを使います。

```php
    public static function prepareSurvived(Table $table): array
    {
        $ageMean = Chapter02::columnMeans($table->rows, ['Age'])['Age'];

        return [
            'x' => array_map(
                static fn (array $row): array => [
                    'Pclass' => self::requiredNumber($row, 'Pclass'),
                    'Age' => Chapter02::number($row, 'Age') ?? $ageMean,
                    'male' => Chapter02::text($row, 'Sex') === 'male' ? 1.0 : 0.0,
                ],
                $table->rows,
            ),
            …
```

補完に使う平均値を **分割の前に全体から** 求めているので、厳密にはテストデータの情報が訓練に漏れています（リーク）。第 8 章のパイプラインは分割のあとで補完しました。ここは他の言語版と条件をそろえるために、あえて簡略化した手順に合わせています。

`Chapter02::number($row, 'Age') ?? $ageMean` は、第 2 章の `number()` が欠損値に `null` を返すことを利用しています。Elixir の `||`、Java の `Optional.orElse`、Scala の `getOrElse` にあたるものが、PHP では `??` です。**`?:` ではいけません。** 年齢 0.0 が欠損値と同じ扱いになってしまいます（第 2 章で刺された箇所です）。`??` は `null` だけを見るので安全です。

### 交差検証の実験

```console
$ php -r 'require "vendor/autoload.php"; echo GettingStartedMl\Chapter11::run();'
Survived（決定木、5 分割交差検証の平均）
  正解率: 0.7811
  適合率: 0.7759
  再現率: 0.6306
  F値: 0.6833
cinema（線形回帰、5 分割交差検証の平均）
  RMSE: 405.77
  MAE: 321.53
Rubix ML の fold のテストデータの件数（5 分割）
  891 件を自作: 179, 178, 178, 178, 178
  891 件を Rubix ML: 178, 178, 178, 178, 178
```

Survived の決定木は、正解率 0.7811 に対して再現率が 0.6306 です。**生存者のうち 4 割近くを見逃している** ことが、正解率だけでは見えませんでした。適合率 0.7759 は、「生存」と予測した人の 8 割近くが本当に生存していたことを表します。11.2 節で述べたとおり、正解率は「どちらを間違えたか」を隠します。

cinema の RMSE（405.77）は MAE（321.53）より大きく、11.6 節で見たとおり、大きく外れた予測があることを示します。第 7 章では 1 回の分割で RMSE が 376.14 でしたが、この章は外れ値を除かずに 5 回の平均を取っているので、単純には比べられません。

最後の 2 行が癖 4 を実データで見せています。891 件を 5 分割すると、自作は先頭の分割に 1 件多く配って 179 件にしますが、**Rubix ML は 891 番目の行をどの分割でもテストしません**。

**6 つの評価指標の数値は、Java 版・Scala 版・Clojure 版・Elixir 版の同じ節とすべて一致します。** 分割の並べ替えを `java.util.Random` と同じ線形合同法（`GettingStartedMl\Random`）で行い、余りの配り方も訓練データの並びもそろえたためです。乱数が別実装の Kotlin 版とは一致しません（Kotlin 版の正解率は 0.7677）。

### 実データのテスト

学習データが無い環境では、`#[Group('data')]` を付けたテストが `--exclude-group data` で外れます（第 1 章から続けている形です）。

```php
    #[Group('data')]
    #[TestDox('cinema の分割ごとの MSE は第 7 章の RMSE の 2 乗と一致する')]
    public function testCinemaの分割ごとのMseは第7章のRmseの2乗と一致する(): void
    {
        …
        $mse = Chapter11::crossValidate($trainer, $x, $t, $folds, Chapter11::meanSquaredError(...));
        $rmse = Chapter11::crossValidate($trainer, $x, $t, $folds, Chapter07::rootMeanSquaredError(...));

        foreach ($mse as $index => $value) {
            $this->assertEqualsWithDelta($value, $rmse[$index] ** 2, 1e-6);
        }
    }
```

同じ `$folds` を 2 回渡して、指標だけを差し替えています。11.9 節で「分割は 1 回だけ作って使い回す」と決めたことが、そのままテストの書きやすさになりました。

`Chapter11::meanSquaredError(...)` は **第一級callable記法** です。PHP 8.1 で入った書き方で、静的メソッドをそのまま `Closure` にできます。Elixir の `&C.mean_squared_error/2` にあたります。

表示は、第 1 章から続けている形のテストで固定します。値は実装を実データで動かして得たものなので、Red を経ていません。

## 11.12 品質チェック

```console
$ npx gulp apps:check:php
```

PHP-CS-Fixer → PHPStan レベル 9 → PHPUnit → カバレッジの順に走ります。

| ファイル | カバレッジ |
| :--- | :--- |
| `src/Chapter11.php` | 100%（237 行） |
| `src/Chapter11/ConfusionMatrix.php` | 100% |
| `src/Chapter11/Fold.php` | 100% |
| `src/Chapter11/RocPoint.php` | 100% |

100% に届いたのは、「値が空欄なら失敗する」「無い行の位置を指定すれば失敗する」といった **例外の道** にもテストを書いたからです。第 7 章では `bias()` が `null` を返す分岐を 1 行諦めましたが、この章の例外はどれも実際に起こしうるので、起こして確かめました。

PHPStan に止められたのは 3 種類でした。

1. **`match (true)` の否定が常に真** — 11.4 節。条件を削って短くなった
2. **関数を返す関数の PHPDoc** — 11.9 節。戻り値側を括弧で包む
3. **`max()` が非空の配列を要求する** — 第 12 章のコードですが、`max(array_map(...))` は引数が空かもしれないと指摘されます。`max([0.0, ...array_map(...)])` に書き換えました

## 11.13 可視化について

ROC 曲線を描く手順は [Python 版](../python/11-evaluation-metrics-and-cross-validation.md) と [Kotlin 版](../kotlin/11-evaluation-metrics-and-cross-validation.md) を参照してください。この章では曲線を **点の列として返す** ところまでを実装し、描画は扱いません。

## 11.14 まとめ

この章では、評価指標と交差検証を PHP の TDD で実装し、Rubix ML と突き合わせました。

1. **正解率は内訳を隠す** — 実データの決定木は正解率 0.7811 でも再現率 0.6306 で、生存者の 4 割近くを見逃していた。混同行列から出る 3 つの指標がそれを見せた
2. **`fn` はプロパティ名に使える** — Elixir では予約語で `cm.fn` と書けなかったところが、PHP では素直に書けた
3. **配列のキーに浮動小数点は使えない** — ROC 曲線のスコアのグループ化で、Elixir 版の `group_by` がそのままは移せなかった。並べ替えてから走る形に書き直した。**PHP の配列のキーに刺されたのは 3 度目**（第 3 章・第 8 章・この章）
4. **評価関数も分類器も `Closure` で足りる** — インターフェースを 1 つも定義せずに、指標も分類器も差し替えられた。代わりに、関数を返す関数の型を PHPDoc で書く癖に付き合うことになった
5. **Rubix ML の誤差の指標は符号が反転している** — `Metric` が「大きいほどよい」に統一されているため。符号を戻せば 1e-12 まで一致した
6. **`FBeta` は 2 値でもマクロ平均** — 自作の 0.8 と Rubix ML の 0.7895 は、どちらも正しい F 値。**一致しないことと、その理由** をテストに固定した。2 値の指標と比べられる相手は `MulticlassBreakdown` の正例の行だった
7. **`fold()` は余りを捨てる** — 891 件を 5 分割すると 891 番目の行がどの分割でもテストされない。**Elixir 版の Scholar とまったく同じ振る舞い**
8. **`KFold` にシードが無い** — 5 回走らせると 0.7718〜0.7819 に散らばった。第 3 章の `ClassificationTree` と同じ形
9. **第 8 章の落とし穴に、ライブラリのほうが落ちていた** — `stratifiedFold()` がラベルを配列のキーにするため、`'0'` と `'1'` が整数に化けて分類器が落ちる。**型の道具をどれだけ使っても、言語の癖は型では守れない**

**TODO リスト（この章の完了時点）**:

- [x] 混同行列を数える
- [x] 適合率・再現率・F 値を求める
- [x] 正解率と平均二乗誤差を求める
- [x] ROC 曲線と AUC を求める（**Rubix ML に無いので自作が最終実装**）
- [x] K 分割交差検証の分け方を作る
- [x] 分割ごとに学習して採点する
- [x] Rubix ML の評価指標・レポート・KFold と突き合わせる
- [x] 実データ（Survived・cinema）で交差検証する

次の章では、正則化を使って過学習を抑えます。リッジ回帰は Rubix ML の `Ridge` と突き合わせられますが、**ラッソ回帰は Rubix ML にありません**。この章の ROC 曲線と同じく、自作が最終実装になります。

PHP 版のほかの章は [PHP 版のトップ](index.md) から辿れます。
