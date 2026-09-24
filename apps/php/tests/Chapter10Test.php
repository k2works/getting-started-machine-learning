<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter02;
use GettingStartedMl\Chapter03;
use GettingStartedMl\Chapter10;
use GettingStartedMl\Chapter10\ForestClassifier;
use GettingStartedMl\Chapter10\LogisticClassifier;
use GettingStartedMl\Chapter10\LogisticRegression;
use GettingStartedMl\Chapter10\RandomForest;
use GettingStartedMl\Chapter10\RubixBinaryLogisticClassifier;
use GettingStartedMl\Chapter10\RubixForestClassifier;
use GettingStartedMl\Chapter10\RubixSoftmaxClassifier;
use GettingStartedMl\Chapter10\TreeClassifier;
use GettingStartedMl\Dataset;
use GettingStartedMl\Random;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;
use Rubix\ML\Exceptions\InvalidArgumentException as RubixException;

final class Chapter10Test extends TestCase
{
    /**
     * 架空の 2 品種のデータ。1 つめの特徴量だけで分かれ、2 つめは手がかりにならない。
     *
     * @return array{list<array<string, float>>, list<string>, list<string>}
     */
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

    // 10.3 ソフトマックス関数

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

    #[TestDox('大きな値でもあふれずに確率になる')]
    public function test大きな値でもあふれない(): void
    {
        $probabilities = Chapter10::softmax([1000.0, 1001.0]);

        $this->assertEqualsWithDelta(1.0, array_sum($probabilities), 1e-12);
        $this->assertGreaterThan($probabilities[0], $probabilities[1]);
    }

    #[TestDox('スコアが 1 つも無ければ失敗する')]
    public function testスコアが無ければ失敗する(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Chapter10::softmax([]);
    }

    // 10.4 交差エントロピー

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

    // 10.4 ロジスティック回帰

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

    #[TestDox('品種は名前の順に並べる')]
    public function test品種は名前の順に並べる(): void
    {
        [$x, $t, $columns] = $this->toyData();

        $this->assertSame(['いぬ', 'ねこ'], LogisticRegression::fit($x, $t, $columns, 1.0, 1)->classes);
    }

    // 10.5 ランダムフォレスト

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

    #[TestDox('ブートストラップ標本は重複を許して同じ件数を選ぶ')]
    public function testブートストラップ標本(): void
    {
        $rows = Chapter10::bootstrapSample(5, new Random(0));

        $this->assertCount(5, $rows);

        foreach ($rows as $row) {
            $this->assertGreaterThanOrEqual(0, $row);
            $this->assertLessThan(5, $row);
        }
    }

    #[TestDox('同じシードなら同じ森になる')]
    public function test同じシードなら同じ森(): void
    {
        [$x, $t, $columns] = $this->toyData();
        $first = RandomForest::fit($x, $t, $columns, 10, 1, null, 0);
        $second = RandomForest::fit($x, $t, $columns, 10, 1, null, 0);

        $this->assertEquals($first, $second);
        $this->assertSame($first->predict($x), $second->predict($x));
    }

    #[TestDox('森は分かれているデータを全部当てる')]
    public function test森は全部当てる(): void
    {
        [$x, $t, $columns] = $this->toyData();

        $this->assertSame($t, RandomForest::fit($x, $t, $columns, 10, 1, null, 0)->predict($x));
    }

    // 10.6 特徴量の重要度

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

    #[TestDox('重要度の合計は 1 になる')]
    public function test重要度の合計は1(): void
    {
        [$x, $t, $columns] = $this->toyData();
        $forest = RandomForest::fit($x, $t, $columns, 10, 1, null, 0);

        $this->assertEqualsWithDelta(
            1.0,
            array_sum(Chapter10::forestImportances($forest, $x, $t, $columns)),
            1e-12,
        );
    }

    // 10.7 モデル共通の約束

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

    #[TestDox('Rubix ML の LogisticRegression は 3 品種を渡すと失敗する')]
    public function testRubixのロジスティック回帰は2クラス専用(): void
    {
        [$x, $t, $columns] = $this->toyData();
        $t[0] = 'とり';

        // 多クラスを扱うのは SoftmaxClassifier のほう。
        $this->expectException(RubixException::class);

        (new RubixBinaryLogisticClassifier())->fit($x, $t, $columns);
    }

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

    // 実データ

    #[Group('data')]
    #[TestDox('実データのモデルごとの正解率がほかの言語版と一致する')]
    public function test実データの正解率が一致する(): void
    {
        if (!Dataset::exists('iris.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('iris.csv'));
        }

        $split = Chapter02::prepareIris(Dataset::path('iris.csv'), 0.3, 0);
        $columns = Chapter03::featureColumns();

        // Java 版・Clojure 版・Elixir 版と一致する。
        $this->assertSame(
            ['train' => 0.9142857142857143, 'test' => 0.9111111111111111],
            Chapter10::score(new LogisticClassifier(), $split, $columns),
        );
        $this->assertSame(
            ['train' => 1.0, 'test' => 0.9333333333333333],
            Chapter10::score(new ForestClassifier(100, 2, null, 0), $split, $columns),
        );
    }

    #[Group('data')]
    #[TestDox('Rubix ML のソフトマックスは学習率を上げると自作に近づく')]
    public function testRubixの学習率を上げる(): void
    {
        if (!Dataset::exists('iris.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('iris.csv'));
        }

        $split = Chapter02::prepareIris(Dataset::path('iris.csv'), 0.3, 0);
        $columns = Chapter03::featureColumns();

        $ours = Chapter10::score(new LogisticClassifier(), $split, $columns);
        $slow = Chapter10::score(new RubixSoftmaxClassifier(), $split, $columns);
        $fast = Chapter10::score(new RubixSoftmaxClassifier(0.0, 1000, 1.0), $split, $columns);

        // 既定の学習率 0.01 では、1000 回まわしても訓練データにすら当てはまらない。
        $this->assertLessThan(0.7, $slow['train']);
        // 学習率を 1.0 にすると自作と同じ水準になる（テストデータは 1 件ぶん届かない）。
        $this->assertGreaterThan($ours['train'], $fast['train']);
        $this->assertEqualsWithDelta($ours['test'], $fast['test'], 0.03);
    }

    #[Group('data')]
    #[TestDox('実データのモデルの比較を表示する')]
    public function test実データの結果を表示する(): void
    {
        if (!Dataset::exists('iris.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('iris.csv'));
        }

        $output = Chapter10::run();

        $this->assertStringContainsString("決定木（深さ 2）\t0.9333\t0.9556", $output);
        $this->assertStringContainsString("ロジスティック回帰\t0.9143\t0.9111", $output);
        $this->assertStringContainsString("ランダムフォレスト（100 本）\t1.0000\t0.9333", $output);
        $this->assertStringContainsString(
            "Rubix ML ランダムフォレスト（100 本）\t1.0000\t0.9333",
            $output,
        );
        // 特徴量の重要度は Java 版・Clojure 版と一致する（Elixir 版だけ 4 桁目でずれた）。
        $this->assertStringContainsString("がく片長さ\t0.1882", $output);
        $this->assertStringContainsString("花弁幅\t0.4140", $output);
    }
}
