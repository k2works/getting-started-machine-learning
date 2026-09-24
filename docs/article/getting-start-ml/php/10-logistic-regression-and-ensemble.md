---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ソフトマックス関数・交差エントロピー・バッチ勾配降下法によるロジスティック回帰と、第 3 章の決定木を束ねたランダムフォレストを PHP の TDD で自作し、Rubix ML の SoftmaxClassifier・RandomForest と正解率と特徴量の重要度を突き合わせる。分類器を interface でそろえ、Rubix ML がシードを持たず大域の乱数を使うことを実測する。"
tags: [article,getting-start-ml,php]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。この章では `Classifier` と `Predictor` の 2 つの interface を用意して、**どのモデルも同じ `score()` に通せる** ようにします。

最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作し、Rubix ML と突き合わせます。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進め、[Java 版](../java/10-logistic-regression-and-ensemble.md)・[Clojure 版](../clojure/10-logistic-regression-and-ensemble.md)・[Elixir 版](../elixir/10-logistic-regression-and-ensemble.md) と数値を対比します。注目してほしいのは次の 4 点です。

- **Rubix ML にはランダムフォレストがあります。** [Elixir 版](../elixir/10-logistic-regression-and-ensemble.md) は Scholar に森が無く「自作が最終実装」でしたが、PHP 版は **正解率も特徴量の重要度も突き合わせられます**。第 3 波の 2 つの言語版が、ここではっきり対照をなします（[ADR 013](../../../adr/013-php-ml-libraries.md)）
- **`LogisticRegression` という名前のクラスは、3 品種を渡すと落ちます。** Rubix ML のそれは 2 クラス専用で、多クラスは `SoftmaxClassifier` のほうです。**自作したものと同じ名前のクラスが、同じものとは限りません**
- **Rubix ML のモデルはシードを受け取りません。** PHP の大域の乱数（`mt_rand`）から引くので、揃えたければ `mt_srand()` で処理系の側を種付けします。Rumale の `random_seed:` とも、自作の `Random` とも違う形です
- **自作の 8 個の正解率と、特徴量の重要度 4 個が Java 版・Clojure 版と完全に一致しました。** Elixir 版が重要度の 4 桁目でずれた原因（同点の分割をどちらに倒すか）を、PHP 版は踏みませんでした

データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `Chapter02::prepareIris()` で前処理します。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 合計が 1 になる確率にする
  - [ ] 大きな値でもあふれない
- [ ] 交差エントロピーで損失を測る
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 分けられるデータを正しく予測する
  - [ ] 学習した品種が名前の順に並ぶ
  - [ ] 繰り返すほど損失が小さくなる
- [ ] ランダムフォレストを作る
  - [ ] 多数決でまとめる
  - [ ] ブートストラップ標本を作る
  - [ ] 木ごとに使う特徴量を絞る
- [ ] 特徴量の重要度を求める
  - [ ] 決定木 1 本の重要度を求める
  - [ ] 森の重要度を求める
- [ ] すべてのモデルを同じ関数で評価する
- [ ] Rubix ML のモデルと突き合わせる

置き場です。

| ファイル | 中身 |
|---------|------|
| `src/Chapter10.php` | `softmax()`・`crossEntropy()`・`majorityVote()`・`bootstrapSample()`・重要度・`score()`・`run()` |
| `src/Chapter10/Classifier.php` | 「学習して `Predictor` を返す」interface |
| `src/Chapter10/Predictor.php` | 「特徴量からラベルを返す」interface |
| `src/Chapter10/FunctionPredictor.php` | クロージャを `Predictor` に包む器 |
| `src/Chapter10/LogisticRegression.php` | 自作のロジスティック回帰（モデル） |
| `src/Chapter10/RandomForest.php`・`ForestTree.php` | 自作のランダムフォレスト |
| `src/Chapter10/*Classifier.php` | 自作 3 種と Rubix ML 3 種の分類器 |

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和」（スコア）を求め、それを確率に変換します。変換に使うのが **ソフトマックス関数** です。各スコアの指数を取り、合計で割ります。

```php
    #[TestDox('同じスコアなら確率は均等になる')]
    public function test同じスコアなら確率は均等(): void
    {
        $this->assertEqualsWithDelta([0.5, 0.5], Chapter10::softmax([0.0, 0.0]), 1e-12);
    }

    #[TestDox('確率の合計は 1 になる')]
    public function test確率の合計は1(): void
    {
        $this->assertEqualsWithDelta(1.0, array_sum(Chapter10::softmax([1.0, 2.0, 3.0])), 1e-12);
    }
```

### 大きな値でもあふれない

素直に `exp($z)` を書くと、スコアが 1000 を超えたところで `INF` になります。最大値を引いてから指数を取れば、引いた分は分母と分子で打ち消し合うので、結果を変えずにあふれを防げます。

```php
    public static function softmax(array $z): array
    {
        if ($z === []) {
            throw new InvalidArgumentException('スコアが 1 つもありません');
        }

        $maximum = max($z);
        $exps = array_map(static fn (float $v): float => exp($v - $maximum), $z);
        $total = array_sum($exps);

        return array_map(static fn (float $v): float => $v / $total, $exps);
    }
```

```php
    #[TestDox('大きな値でもあふれずに確率になる')]
    public function test大きな値でもあふれない(): void
    {
        $probabilities = Chapter10::softmax([1000.0, 1001.0]);

        $this->assertEqualsWithDelta(1.0, array_sum($probabilities), 1e-12);
        $this->assertGreaterThan($probabilities[0], $probabilities[1]);
    }
```

PHP の `exp(1000.0)` は **例外にならず `INF` を返します**。JVM 系の言語版と同じで、Elixir の `:math.exp/1` が `ArithmeticError` で落ちるのとは違います。落ちてくれないぶん、あふれたことは `INF / INF = NAN` として後の計算に静かに混ざります。このテストが無ければ、正解率がおかしくなってから原因を探すことになります。

**TODO リスト**:

- [x] ソフトマックス関数で確率に変換する

## 10.4 ロジスティック回帰

### 交差エントロピー

学習の目標は「正解の品種の確率を大きくする」ことです。その良し悪しを測るのが **交差エントロピー** で、正解の品種の確率の対数の平均にマイナスを付けた値です。

```php
    public static function crossEntropy(array $probabilities, array $targets): float
    {
        if ($probabilities === []) {
            throw new InvalidArgumentException('確率が 1 件もありません');
        }

        $sum = 0.0;

        foreach ($probabilities as $i => $probability) {
            $sum += log($probability[$targets[$i]] + self::EPSILON);
        }

        return -($sum / count($probabilities));
    }
```

```php
    #[TestDox('五分五分の確率の交差エントロピーは log 2')]
    public function test交差エントロピーはlog2(): void
    {
        $this->assertEqualsWithDelta(
            log(2.0),
            Chapter10::crossEntropy([[0.5, 0.5]], [0]),
            1e-9,
        );
    }

    #[TestDox('確率が 0 でも無限大にならない')]
    public function test確率が0でも無限大にならない(): void
    {
        $loss = Chapter10::crossEntropy([[0.0, 1.0]], [0]);

        $this->assertTrue(is_finite($loss));
        $this->assertGreaterThan(0.0, $loss);
    }
```

確率がちょうど 0 になると `log(0)` が `-INF` になるので、`1e-12` を足しています。ここでも PHP は落ちずに `-INF` を返すので、テストで固定しておく価値があります。

### 学習と予測

**バッチ勾配降下法** で重みを学習します。全件の勾配を求めて 1 歩進める、を繰り返します。

```php
    public static function fit(
        array $x,
        array $t,
        array $columns,
        float $learningRate = self::DEFAULT_LEARNING_RATE,
        int $epochs = self::DEFAULT_EPOCHS,
    ): self {
        // ……
        $weights = array_fill(0, count($columns), array_fill(0, count($classes), 0.0));
        $bias = array_fill(0, count($classes), 0.0);

        for ($epoch = 0; $epoch < $epochs; ++$epoch) {
            $probabilities = array_map(
                static fn (array $row): array => Chapter10::softmax(self::scores($row, $weights, $bias)),
                $rows,
            );
            $losses[] = Chapter10::crossEntropy($probabilities, $targets);

            // 誤差は「確率 − 正解」。正解の品種だけ 1 を引く。
            $errors = [];

            foreach ($probabilities as $i => $probability) {
                $probability[$targets[$i]] -= 1.0;
                // 添字を書き換えたあとは list として扱えないので、包み直す。
                $errors[] = array_values($probability);
            }

            $weights = self::updateWeights($weights, $rows, $errors, $learningRate, $n);
            $bias = self::updateBias($bias, $errors, $learningRate, $n);
        }

        return new self($columns, $classes, $weights, $bias, $losses);
    }
```

重みは `weights[特徴量の番号][品種の番号]` で持ちます。**ループの順（特徴量が外、品種が内）を Java 版・Clojure 版・Elixir 版にそろえています。** 浮動小数点の足し算は順によって最後の 1 ビットが変わるので、数値を版間で一致させるにはここまで合わせる必要があります。

`$probability[$targets[$i]] -= 1.0;` のあと `array_values()` で包み直しているのは、第 2 章と第 9 章で繰り返し出てきた話です。**PHP の配列は添字を書き換えても連番のままですが、PHPStan は「list であり続ける」とは判断しません。** 実行時には無害でも、静的解析の目には一度 `array<int, float>` に落ちます。

`$rows` は `Chapter10::toRows($x, $columns)` で「列の順に並べた数値の行」に直してあります。連想配列の鍵を毎回引くより速く、ループの順も固定できます。

```php
    #[TestDox('分かれているデータを学習すると全部当てる')]
    public function testロジスティック回帰は全部当てる(): void
    {
        [$x, $t, $columns] = $this->toyData();

        $this->assertSame($t, LogisticRegression::fit($x, $t, $columns)->predict($x));
    }

    #[TestDox('損失は繰り返しごとに下がる')]
    public function test損失は下がる(): void
    {
        [$x, $t, $columns] = $this->toyData();
        $losses = LogisticRegression::fit($x, $t, $columns, 1.0, 50)->losses;

        $this->assertCount(50, $losses);
        $this->assertLessThan($losses[0], $losses[49]);
    }
```

テストのデータは架空の 2 品種です。**学習データの行はテストに書きません。**

```php
    private function toyData(): array
    {
        $x = [];
        $t = [];

        foreach ([0.0, 0.5, 1.0, 1.5] as $value) {
            $x[] = ['a' => $value, 'b' => 1.0];
            $t[] = 'ねこ';
        }

        foreach ([8.0, 8.5, 9.0, 9.5] as $value) {
            $x[] = ['a' => $value, 'b' => 1.0];
            $t[] = 'いぬ';
        }

        return [$x, $t, ['a', 'b']];
    }
```

`b` はすべて 1.0 で、手がかりになりません。この「効かない特徴量」が、10.6 節の重要度のテストでそのまま効きます。

予測は、スコアが最大の品種を選びます。同じ値なら先に現れたほうです。

```php
                foreach ($scores as $i => $score) {
                    if ($score > $scores[$best]) {
                        $best = $i;
                    }
                }
```

`>` であって `>=` ではないのが肝です。`>=` にすると同点で後ろが勝ち、Java 版と結果が変わります。

**TODO リスト**:

- [x] 交差エントロピーで損失を測る
- [x] ロジスティック回帰を学習して予測する

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、次の 2 つの「ランダム」で木に違いを作ります。

1. **ブートストラップ標本**: 訓練データから重複を許して同じ件数を引き直す
2. **特徴量の部分集合**: 木ごとに使える特徴量を絞る

そうして作った木の予測を、多数決でまとめます。

### 多数決とブートストラップ標本

多数決は、第 3 章の `DecisionTree::majority()` をそのまま使います。同数なら先に現れたラベルを選ぶという規則も第 3 章のままです。

```php
    public static function majorityVote(array $votes): array
    {
        if ($votes === []) {
            throw new InvalidArgumentException('予測が 1 件もありません');
        }

        return array_map(
            static fn (int $sample): string => DecisionTree::majority(array_column($votes, $sample)),
            range(0, count($votes[0]) - 1),
        );
    }
```

`array_column($votes, $sample)` で「木ごとの予測」の行列を列で切ります。**PHP の `array_column()` は連想配列だけでなく添字配列にも使えます。** 転置を自分で書かずに済みました。

```php
    #[TestDox('多数決はサンプルごとにいちばん多い予測を選ぶ')]
    public function test多数決(): void
    {
        $votes = [
            ['ねこ', 'いぬ'],
            ['ねこ', 'とり'],
            ['いぬ', 'いぬ'],
        ];

        $this->assertSame(['ねこ', 'いぬ'], Chapter10::majorityVote($votes));
    }
```

ブートストラップ標本は、**乱数生成器そのものを受け取ります**。

```php
    public static function bootstrapSample(int $size, Random $random): array
    {
        $rows = [];

        for ($i = 0; $i < $size; ++$i) {
            $rows[] = $random->nextInt($size);
        }

        return $rows;
    }
```

第 2 章の `Random::shuffle($items, $seed)` はシードから始めますが、森を作るときは **100 本ぶん状態を引き継ぐ** 必要があります。シードを渡す口だけでは、2 本目が 1 本目と同じ標本になってしまいます。

第 2 章で `Random` をクラスとして自作しておいたおかげで、ここでは `new Random($seed)` を 1 つ作って回すだけです。Elixir 版は不変の状態を `Enum.map_reduce/3` で持ち回りましたが、PHP では `java.util.Random` と同じ「オブジェクトが状態を持つ」形で書けます。**並びは同じなので、結果も同じになります。**

### 森を作る

```php
    public static function fit(
        array $x,
        array $t,
        array $columns,
        int $nEstimators,
        int $maxFeatures,
        ?int $maxDepth,
        int $seed,
    ): self {
        $random = new Random($seed);
        $trees = [];

        for ($i = 0; $i < $nEstimators; ++$i) {
            $rows = Chapter10::bootstrapSample(count($x), $random);
            $chosen = array_slice(self::shuffleWith($columns, $random), 0, $maxFeatures);
            // 列の順は元のまま残す。
            $treeColumns = array_values(
                array_filter($columns, static fn (string $c): bool => in_array($c, $chosen, true)),
            );
            // ……
            $trees[] = new ForestTree($treeColumns, $rows, DecisionTree::fit($sampleX, $sampleT, $treeColumns, $maxDepth));
        }

        return new self($trees);
    }
```

`shuffleWith()` は、第 2 章の Fisher-Yates と同じ手順を「シードではなく生成器」で行う private メソッドです。並べ替えてから先頭 `maxFeatures` 個を取り、**列の順は元のまま残します**。第 3 章の `bestSplit()` は同じ不純度なら列の順で前を選ぶので、ここで順が変わると木が変わります。

`ForestTree` は、木と一緒に「使った列」と「使った行番号」を覚えます。

```php
final readonly class ForestTree
{
    /**
     * @param list<string> $columns
     * @param list<int>    $rows
     */
    public function __construct(
        public array $columns,
        public array $rows,
        public DecisionTree $tree,
    ) {
    }
}
```

行番号を覚えているのは、10.6 節で重要度を求めるときに「その木が見た標本」をもう一度作るためです。標本そのものを持つと 100 本ぶんのデータを抱えることになるので、番号だけにしました。

森が決定的であることをテストで固定します。

```php
    #[TestDox('同じシードなら同じ森になる')]
    public function test同じシードなら同じ森(): void
    {
        [$x, $t, $columns] = $this->toyData();
        $first = RandomForest::fit($x, $t, $columns, 10, 1, null, 0);
        $second = RandomForest::fit($x, $t, $columns, 10, 1, null, 0);

        $this->assertEquals($first, $second);
        $this->assertSame($first->predict($x), $second->predict($x));
    }
```

**`assertEquals` で森そのものを比べられます。** 木も森も `readonly class` と配列だけでできているので、PHPUnit が中身を再帰的に見てくれます。`equals()` を書く必要も、予測で代用する必要もありません。`assertSame`（同一性）ではなく `assertEquals`（中身）を使うのは、第 1 章で書いたとおり PHP のオブジェクト比較が既定で参照の同一性だからです。

**TODO リスト**:

- [x] ランダムフォレストを作る

## 10.6 特徴量の重要度

### 計算方法

決定木の特徴量の重要度は、「その特徴量で分割したときに、不純度がどれだけ減ったか」を足し上げたものです。減った量は、その節に届いた件数で重み付けします。

```php
    private static function impurityDecreases(Leaf|Node $tree, array $x, array $t): array
    {
        if ($tree instanceof Leaf) {
            return [];
        }

        $split = $tree->split;
        // ……左右に振り分ける……

        return [
            [$split->feature, count($t) * (DecisionTree::gini($t) - $split->impurity)],
            ...self::impurityDecreases($tree->left, $leftX, $leftT),
            ...self::impurityDecreases($tree->right, $rightX, $rightT),
        ];
    }
```

第 3 章が木を `Leaf|Node` の **共用型** で表したので、`instanceof Leaf` で分けた残りが `Node` であることが PHPStan にも伝わります。`$tree->split` に赤線が出ないのはそのためです。Elixir 版がマップの鍵の有無で見分け、網羅性の検査を諦めたところとの違いが、ここでも出ます。

```php
    #[TestDox('手がかりにならない特徴量の重要度は 0 になる')]
    public function test重要度(): void
    {
        [$x, $t, $columns] = $this->toyData();
        $importances = Chapter10::treeImportances(
            Chapter03\DecisionTree::fit($x, $t, $columns),
            $x,
            $t,
            $columns,
        );

        $this->assertEqualsWithDelta(1.0, $importances['a'], 1e-12);
        $this->assertEqualsWithDelta(0.0, $importances['b'], 1e-12);
    }
```

`b` はすべて 1.0 なので分割に使えず、重要度は 0 になります。合計が 1 になるように正規化しています。

### 森の重要度

森の重要度は、木ごとの重要度の平均です。木が使わなかった特徴量は、その木では 0 として数えます。

```php
    public static function forestImportances(RandomForest $forest, array $x, array $t, array $columns): array
    {
        $totals = array_fill_keys($columns, 0.0);
        $count = count($forest->trees);

        foreach ($forest->trees as $tree) {
            [$sampleX, $sampleT] = self::sampleOf($tree, $x, $t);

            foreach (self::treeImportances($tree->tree, $sampleX, $sampleT, $tree->columns) as $feature => $value) {
                $totals[$feature] += $value / $count;
            }
        }

        return self::normalize($totals);
    }
```

`sampleOf()` が、覚えておいた行番号から標本を作り直します。**木を学習したときとまったく同じデータで重要度を求める** ためです。

**TODO リスト**:

- [x] 特徴量の重要度を求める

## 10.7 モデル共通の約束

モデルが 3 種類（自作）＋ 3 種類（Rubix ML）になったので、共通の口を決めます。

```php
interface Predictor
{
    /**
     * @param list<array<string, float>> $x
     *
     * @return list<string>
     */
    public function predict(array $x): array;
}

interface Classifier
{
    /**
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public function fit(array $x, array $t, array $columns): Predictor;
}
```

**「学習する側」と「学習済みの側」を分けたのは、設定を持つオブジェクトと結果を持つオブジェクトが別物だからです。** `ForestClassifier` は木の本数とシードを持ち、`RandomForest` は育った木を持ちます。

評価はこの 1 つの関数で済みます。

```php
    public static function score(Classifier $classifier, array $split, array $columns): array
    {
        $model = $classifier->fit($split['xTrain'], $split['tTrain'], $columns);

        return [
            'train' => Chapter01::accuracy($model->predict($split['xTrain']), $split['tTrain']),
            'test' => Chapter01::accuracy($model->predict($split['xTest']), $split['tTest']),
        ];
    }
```

### あとから interface を足せない

問題は、**第 3 章の `DecisionTree` も Rubix ML のモデルも、この `Predictor` を知らないまま書かれている** ことです。PHP には「あとからクラスに interface を足す」仕組み（Rust の trait 実装、Clojure の protocol の後付け、Scala の型クラス）がありません。そこで、クロージャを包む器を 1 つ用意しました。

```php
final readonly class FunctionPredictor implements Predictor
{
    /** @param Closure(list<array<string, float>>): list<string> $predict */
    public function __construct(private Closure $predict)
    {
    }

    public function predict(array $x): array
    {
        return ($this->predict)($x);
    }
}
```

これがあれば、包むのは 1 行で済みます。

```php
    public function fit(array $x, array $t, array $columns): Predictor
    {
        $tree = DecisionTree::fit($x, $t, $columns, $this->maxDepth);

        return new FunctionPredictor($tree->predict(...));
    }
```

`$tree->predict(...)` は PHP 8.1 の **第一級呼び出し可能構文** です。メソッドをそのまま `Closure` にできます。

この設計を、ほかの言語版と並べてみます。

| 言語 | モデルをそろえる方法 | 第 3 章の決定木を通すのに要るもの |
|------|-------------------|--------------------------|
| Java・Scala | `interface`・`trait` | アダプターのクラス |
| Elixir | 何もしない（関数を返す関数という約束だけ） | **何も要らない** |
| Ruby | ダックタイピング（`fit`・`predict` に応えればよい） | 何も要らない |
| **PHP** | **`interface`（実行時にも静的解析にも効く）** | **包む器 1 つ（`FunctionPredictor`）** |

**Elixir 版は「約束を `@moduledoc` にしか書けず、違反は実行するまで分からない」代わりに、包む器が要りませんでした。** PHP は逆で、器が 1 つ要る代わりに、約束を破ったクラスは PHPStan が実行前に見つけます。Ruby 版と比べると、同じ動的型付けの言語で正反対の側に立っていることがよく分かります。

そして、**器は 1 つで足ります**。クロージャを受け取る `FunctionPredictor` を 1 回書けば、決定木も Rubix ML の 3 つのモデルも同じ形で通せます。Java 版がモデルごとにアダプターを書いたのとは、この点だけ違います。

```php
    #[TestDox('どの分類器も同じ口で学習して予測できる')]
    public function testどの分類器も同じ口で扱える(): void
    {
        [$x, $t, $columns] = $this->toyData();

        $classifiers = [
            new TreeClassifier(null),
            new LogisticClassifier(),
            new ForestClassifier(10, 1, null, 0),
            new RubixSoftmaxClassifier(),
            // 2 品種なら Rubix ML の LogisticRegression も使える。
            new RubixBinaryLogisticClassifier(0.0, 1000, 1.0),
        ];

        foreach ($classifiers as $classifier) {
            $this->assertSame($t, $classifier->fit($x, $t, $columns)->predict($x));
        }
    }
```

**TODO リスト**:

- [x] すべてのモデルを同じ関数で評価する

## 10.8 Rubix ML のモデルを同じ形に包む

### 名前が同じでも中身は違う

最初、Rubix ML の `Rubix\ML\Classifiers\LogisticRegression` を使おうとしました。実行すると落ちます。

```text
Rubix\ML\Exceptions\InvalidArgumentException:
  Number of classes must be 2, 3 given.
```

**Rubix ML の `LogisticRegression` は 2 クラス専用です。** 出力層が `Binary` 層に固定されていて、3 品種のアヤメには使えません。多クラスを扱うのは **`SoftmaxClassifier`** のほうで、これは 10.3 節と 10.4 節で自作したもの（ソフトマックス関数＋交差エントロピー）と同じアルゴリズムです。

この事実はテストに残しました。

```php
    #[TestDox('Rubix ML の LogisticRegression は 3 品種を渡すと失敗する')]
    public function testRubixのロジスティック回帰は2クラス専用(): void
    {
        [$x, $t, $columns] = $this->toyData();
        $t[0] = 'とり';

        // 多クラスを扱うのは SoftmaxClassifier のほう。
        $this->expectException(RubixException::class);

        (new RubixBinaryLogisticClassifier())->fit($x, $t, $columns);
    }
```

Scholar も Rumale も「ロジスティック回帰」という名前のまま多クラスを扱えたので、**同じ名前のクラスが同じものとは限らない** ことを、シリーズで初めてこの版が踏みました。

### シードを受け取らないライブラリ

包んで走らせたら、今度は実行のたびに正解率が変わりました。

```text
softmax 0: 0.5333 0.4889
softmax 1: 0.6857 0.7556
softmax 2: 0.4857 0.4444
forest 0: 1.0000 0.9333
forest 1: 1.0000 0.9333
forest 2: 1.0000 0.9778
```

**Rubix ML のモデルはシードを引数で受け取りません。** 重みの初期値も、森の標本の抽出も、PHP の **大域の乱数**（`mt_rand`）から引きます。学習の直前に `mt_srand($seed)` を呼ぶと、同じ値が返るようになります。

```php
        // Rubix ML のモデルはシードを受け取らない。重みの初期値も標本の抽出も
        // PHP の大域の乱数（mt_rand）から引くので、揃えたければ処理系の側を種付けする。
        mt_srand($this->seed);

        $model = new SoftmaxClassifier(
            batchSize: count($x),
            optimizer: new Stochastic($this->learningRate),
            l2Penalty: $this->l2Penalty,
            epochs: $this->epochs,
        );
```

```php
    #[TestDox('Rubix ML のモデルは同じシードなら同じ結果になる')]
    public function testRubixは種付けすれば同じ結果になる(): void
    {
        [$x, $t, $columns] = $this->toyData();
        $classifier = new RubixForestClassifier(10, 1);

        $this->assertSame(
            $classifier->fit($x, $t, $columns)->predict($x),
            $classifier->fit($x, $t, $columns)->predict($x),
        );
    }
```

これはライブラリの設計上の選択で、良し悪しがあります。**種付けの単位が「モデル」ではなく「プロセス」になる** ので、同じプロセスで別のモデルを学習させると、順番を変えただけで結果が変わります。Rumale の `random_seed:`、scikit-learn の `random_state=`、自作の `Random($seed)` は、どれも「このモデルの乱数」を切り離しています。

`Chapter10::run()` の出力を安定させるために、分類器のコンストラクタにシードを持たせ、`fit()` の先頭で種付けすることにしました。**大域の状態に触る副作用をクラスの中に閉じ込め、外からは「シードを渡すモデル」に見えるようにする** という包み方です。

### 設定をそろえる

Rubix ML の `RandomForest` は、既定の `ratio` が 0.2（標本の 2 割）です。自作のブートストラップに近づけるため 1.0 にします。`maxFeatures` の意味も違います。

| 設定 | 自作 | Rubix ML |
|------|------|---------|
| 標本の大きさ | 全件（重複あり） | `ratio` × 全件。**既定は 0.2** |
| `maxFeatures` | **木ごと** に使う列の数 | **節ごと** に見る列の数 |
| シード | コンストラクタの引数 | **無し（大域の `mt_rand`）** |

**TODO リスト**:

- [x] Rubix ML のモデルと突き合わせる

## 10.9 実データで突き合わせる

### モデルを比べる

```bash
ML_DATA_DIR=<学習データの置き場> php -r 'require "vendor/autoload.php"; echo GettingStartedMl\Chapter10::run();'
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9333	0.9556
ロジスティック回帰	0.9143	0.9111
ランダムフォレスト（100 本）	1.0000	0.9333
ランダムフォレスト（100 本・深さ 2）	0.9429	0.9556
Rubix ML ソフトマックス（既定の学習率 0.01）	0.6762	0.6444
Rubix ML ソフトマックス（学習率 1.0）	0.9333	0.8889
Rubix ML ランダムフォレスト（100 本）	1.0000	0.9333

ランダムフォレスト（100 本）の特徴量の重要度:
特徴量	自作	Rubix ML
がく片長さ	0.1882	0.2446
がく片幅	0.1271	0.1554
花弁長さ	0.2708	0.2362
花弁幅	0.4140	0.2497
```

**自作のモデルの 8 個の正解率は、[Java 版の 10.10 節](../java/10-logistic-regression-and-ensemble.md)・[Clojure 版の 10.9 節](../clojure/10-logistic-regression-and-ensemble.md)・[Elixir 版の 10.9 節](../elixir/10-logistic-regression-and-ensemble.md) と完全に一致しました。** 分割（第 2 章の自作の線形合同法 + Fisher-Yates）・ブートストラップ標本・列の並べ替え・ループの順をすべて Java 版にそろえたからです。乱数生成器そのものを自作するという ADR 013 の決定が、ここでいちばん効きました。

結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではありません。** 深さを制限しないランダムフォレスト（0.9333）は、第 3 章の深さ 2 の決定木（0.9556）より低くなりました。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。木の深さを 2 に制限した森は、決定木と同じ 0.9556 です
- **テストデータ 45 件では、1 件の違いが 0.0222 の差になります。** 1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います
- **Rubix ML の森は自作と同じ 0.9333 でした。** 標本の作り方も `maxFeatures` の意味も違うのに、テストデータの正解率は一致しました。iris が「花弁長さと花弁幅だけでほぼ分けられる」データなので、森の作り方の違いが結果に出にくいのだと読めます

### 特徴量の重要度が Java 版・Clojure 版と一致した

**自作の重要度 4 個も、Java 版・Clojure 版と完全に一致しました。**

| 特徴量 | PHP 版 | Java 版・Clojure 版 | Elixir 版 |
|--------|-------|-------------------|----------|
| がく片長さ | 0.1882 | 0.1882 | 0.1882 |
| がく片幅 | **0.1271** | **0.1271** | 0.1265 |
| 花弁長さ | **0.2708** | **0.2708** | 0.2713 |
| 花弁幅 | 0.4140 | 0.4140 | 0.4140 |

[Elixir 版の 10.9 節](../elixir/10-logistic-regression-and-ensemble.md) は、ここで 4 桁目がずれました。原因は測定済みで、「ジニ不純度の和を取る順が言語のコレクションの実装で変わり、同点だった 2 つの分割に 1 ulp の差が付く」というものでした。

**PHP 版は踏みませんでした。** 第 3 章の `gini()` がラベルの出現数を数えるとき、PHP の連想配列は **挿入順を保ちます**。Clojure の小さなマップ（挿入順）と同じ順でたどるので、和も同じ順に積まれ、同点が同点のまま残ります。同点なら「先に見つけたほう」という規則が効いて、Java 版と同じ列が選ばれます。

Elixir のマップはキーの項順でたどるため、ここだけ別の順になりました。**「数値を版間で比べるときは、同点をどう倒すかまで決めておく」** という Elixir 版の結論は正しく、PHP 版はたまたま Java 版と同じ順だったので一致した、と読むのが正確です。

Rubix ML の重要度（0.2446・0.1554・0.2362・0.2497）は自作とかなり違います。森の作り方（標本の割合、`maxFeatures` の単位）が違ううえ、Rubix ML の `featureImportances()` が何を足し上げているかは自作と同じとは限らないので、一致しないほうが自然です。**Elixir 版はここを比べられませんでした**（Scholar に森が無いため）。比べられるようになって分かったのは、「重要度は正解率よりもずっと実装に敏感である」ということです。

### Rubix ML のソフトマックスは学習率で決まった

Rubix ML の `SoftmaxClassifier` は、既定の設定で **訓練 0.6762・テスト 0.6444** と、自作（0.9143・0.9111）よりずっと低い値になりました。設定を変えて実測します。

| 設定 | 訓練データ | テストデータ |
|------|-----------|-------------|
| 既定（`l2Penalty: 1e-4`・学習率 0.01・1000 回） | 0.6762 | 0.6444 |
| `l2Penalty: 0.0`（学習率 0.01 のまま） | 0.6762 | 0.6444 |
| 学習率 0.1 | 0.8667 | 0.8667 |
| **学習率 1.0** | **0.9333** | 0.8889 |
| 学習率 1.0・5000 回 | 0.9333 | 0.8889 |
| 学習率 1.0・20000 回 | 0.9333 | 0.8889 |
| （参考）自作・1000 回 | 0.9238 | 0.9111 |
| （参考）自作・5000 回 | 0.9143 | 0.9111 |

読み取れることは 3 つです。

1. **効いていたのは学習率です。** `l2Penalty` を 0 にしても値は 1 つも動かず（0.6762・0.6444 のまま）、学習率を 0.01 から 1.0 に上げたら訓練データの正解率が 0.6762 から 0.9333 になりました。**Elixir 版の Scholar は正則化が犯人でしたが、Rubix ML では正則化はほとんど効いていません。** `l2Penalty` の既定が 1e-4 と、Scholar の `alpha: 1.0` より 4 桁小さいからです
2. **繰り返し回数を増やしても動きません。** `minChange`（既定 1e-4）と `window`（既定 5）による早期打ち切りで、1000 回より前に止まっています。ここは Elixir 版の Scholar と同じ振る舞いでした
3. **訓練データでは自作を追い越し、テストデータでは届きません。** 学習率 1.0 の Rubix ML は訓練 0.9333 と自作（0.9143）より高いのに、テストは 0.8889 と自作（0.9111）に 1 件ぶん届きません。訓練データに寄ったぶん、未知のデータへの当てはまりを落としたと読めます。重みの初期値も違います（Rubix ML は乱数で初期化、自作は全部 0）

**同じ「ライブラリの既定値のせいで精度が出ない」という現象でも、どのつまみが原因かはライブラリごとに違いました。** Scholar は正則化、Rubix ML は学習率です。**名前ではなく設定をそろえて比べる**、という結論だけが共通です。

`run()` の表では、あえて既定のまま（0.6762・0.6444）と学習率 1.0 の両方を並べています。ライブラリを何も考えずに使ったときに何が起きるかが、この章でいちばん伝えたいことだからです。

この対比はテストで固定しました。

```php
    #[Group('data')]
    #[TestDox('Rubix ML のソフトマックスは学習率を上げると自作に近づく')]
    public function testRubixの学習率を上げる(): void
    {
        // ……
        $ours = Chapter10::score(new LogisticClassifier(), $split, $columns);
        $slow = Chapter10::score(new RubixSoftmaxClassifier(), $split, $columns);
        $fast = Chapter10::score(new RubixSoftmaxClassifier(0.0, 1000, 1.0), $split, $columns);

        // 既定の学習率 0.01 では、1000 回まわしても訓練データにすら当てはまらない。
        $this->assertLessThan(0.7, $slow['train']);
        // 学習率を 1.0 にすると自作と同じ水準になる（テストデータは 1 件ぶん届かない）。
        $this->assertGreaterThan($ours['train'], $fast['train']);
        $this->assertEqualsWithDelta($ours['test'], $fast['test'], 0.03);
    }
```

**値そのものではなく関係を書いています。** Rubix ML の初期値は乱数なので、版が上がれば数値は動きます。「既定では 0.7 に届かない」「学習率を上げれば自作と同じ水準になる」という関係のほうが、長く正しくあり続けます。

### 実データのテスト

自作の正解率は、`assertSame` で **浮動小数点数として完全に一致する** ところまで書けます。

```php
        // Java 版・Clojure 版・Elixir 版と一致する。
        $this->assertSame(
            ['train' => 0.9142857142857143, 'test' => 0.9111111111111111],
            Chapter10::score(new LogisticClassifier(), $split, $columns),
        );
        $this->assertSame(
            ['train' => 1.0, 'test' => 0.9333333333333333],
            Chapter10::score(new ForestClassifier(100, 2, null, 0), $split, $columns),
        );
```

正解率は「当たった件数 ÷ 全件数」なので、同じ予測をすれば浮動小数点数としても同じ値になります。**「近い」ではなく「同じ」と書けるテストは、それだけで強い主張になります。**

表示は、第 1 章から続けている形のテストで固定します。

```php
        $this->assertStringContainsString("決定木（深さ 2）\t0.9333\t0.9556", $output);
        $this->assertStringContainsString("ロジスティック回帰\t0.9143\t0.9111", $output);
        $this->assertStringContainsString("ランダムフォレスト（100 本）\t1.0000\t0.9333", $output);
        // 特徴量の重要度は Java 版・Clojure 版と一致する（Elixir 版だけ 4 桁目でずれた）。
        $this->assertStringContainsString("がく片長さ\t0.1882", $output);
        $this->assertStringContainsString("花弁幅\t0.4140", $output);
```

`Chapter10::run()` は文字列を返すので、標準出力を横取りする仕掛けは要りません。第 1 章で「表示する関数ではなく、文字列を返す関数にする」と決めたのが、モデルが 7 つに増えてもそのまま効いています。

## 10.10 Notebook で探索する

PHP 版では Notebook と可視化を扱いません。モデルごとの正解率のグラフ、ロジスティック回帰の損失の推移、モデル別の特徴量の重要度、森の大きさと正解率の関係は、[Python 版の 10.11 節](../python/10-logistic-regression-and-ensemble.md) と [Kotlin 版の 10.11 節](../kotlin/10-logistic-regression-and-ensemble.md) の可視化を参照してください。

`LogisticRegression` の `$losses`（繰り返しごとの損失の並び）と `Chapter10::forestImportances()`（特徴量ごとの重要度）は、ほかの版と同じ形のデータを返すので、同じ観点で読めます。

## 10.11 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、分類器を interface でそろえてまとめて評価しました。

| モデル | 自作したもの | 突き合わせたライブラリ |
|--------|------------|-------------------|
| ロジスティック回帰 | `softmax()`・`crossEntropy()`・`LogisticRegression` | Rubix ML の **`SoftmaxClassifier`**（学習率 1.0 で同じ水準。既定の 0.01 では届かない） |
| ランダムフォレスト | `bootstrapSample()`・`RandomForest`・`majorityVote()` | **Rubix ML の `RandomForest`**（テストデータの正解率が一致） |
| 特徴量の重要度 | `treeImportances()`・`forestImportances()` | **Rubix ML の `featureImportances()`**（定義が違うので一致せず） |

PHP 版ならではの学びは 6 つです。

1. **interface は要るが、器は 1 つで足りる** — PHP には「あとからクラスに interface を足す」仕組みが無いので、第 3 章の決定木も Rubix ML のモデルも包む必要がある。ただしクロージャを受ける `FunctionPredictor` を 1 つ書けば、4 種類のモデルすべてに使い回せる。Java 版がモデルごとにアダプターを書き、Elixir 版が何も書かずに済んだ、その中間
2. **同じ名前のクラスが同じものとは限らない** — Rubix ML の `LogisticRegression` は 2 クラス専用で、アヤメを渡すと落ちる。多クラスは `SoftmaxClassifier`。Scholar も Rumale も名前のまま多クラスを扱えたので、シリーズでここだけ違った
3. **シードがライブラリではなく処理系にある** — Rubix ML のモデルは乱数のシードを受け取らず、大域の `mt_rand` から引く。`mt_srand()` で種付けすれば揃うが、**単位がモデルではなくプロセス**になる。副作用を分類器のクラスに閉じ込めて、外からは「シードを渡すモデル」に見せた
4. **既定値の犯人はライブラリごとに違う** — Elixir 版の Scholar は正則化（`alpha: 1.0`）、Rubix ML は学習率（0.01）。Rubix ML の `l2Penalty` の既定は 1e-4 と 4 桁小さく、0 にしても値が 1 つも動かなかった。どちらも「既定のまま使うと精度が出ない」という同じ見え方をするが、つまみは別だった
5. **値が同じなら `assertEquals` で森ごと比べられる** — 木も森も `readonly class` と配列だけでできているので、「同じシードなら同じ森」が 1 行で書ける。ただし `assertSame` は参照の同一性なので、中身を比べるなら `assertEquals` を選ぶ
6. **連想配列の挿入順が版間の一致を救った** — Elixir 版が重要度の 4 桁目でずれた原因は、ジニ不純度の和を取る順だった。PHP の連想配列は挿入順を保つので Clojure 版と同じ順になり、同点が同点のまま残って Java 版と一致した。**たまたま一致したのであって、同点の倒し方を決めていない設計であることは変わらない**

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。

PHP 版のほかの章は [PHP 版のトップ](index.md) から辿れます。
