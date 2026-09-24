<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter13;
use GettingStartedMl\Dataset;
use GettingStartedMl\Table;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class Chapter13Test extends TestCase
{
    /** 架空の 3 件 2 列。2 列目は 1 列目のちょうど 2 倍で、完全に相関する。 */
    private const array CORRELATED = [[1.0, 2.0], [2.0, 4.0], [3.0, 6.0]];

    /** 架空の 4 件 2 列。ゆるく相関していて、2 つの固有値が離れている。 */
    private const array SPREAD = [[1.0, 2.0], [2.0, 3.5], [3.0, 3.0], [4.0, 6.0]];

    /** 学習データの行は書かない。架空の 4 件で前処理を確かめる。 */
    private function bostonLike(): Table
    {
        return new Table(
            ['CRIME', 'RM', 'PRICE'],
            [
                ['CRIME' => 'high', 'RM' => '6.0', 'PRICE' => '20.0'],
                ['CRIME' => 'low', 'RM' => '7.0', 'PRICE' => '30.0'],
                ['CRIME' => 'very_low', 'RM' => '', 'PRICE' => '40.0'],
                ['CRIME' => 'low', 'RM' => '5.0', 'PRICE' => '10.0'],
            ],
        );
    }

    // 13.5 分散共分散行列を求める

    #[TestDox('列ごとの平均を求める')]
    public function test列ごとの平均を求める(): void
    {
        $this->assertSame([2.0, 4.0], Chapter13::columnMeans(self::CORRELATED));
    }

    #[TestDox('中心化すると各列の平均が 0 になる')]
    public function test中心化すると各列の平均が0になる(): void
    {
        $centered = Chapter13::center(self::CORRELATED, Chapter13::columnMeans(self::CORRELATED));

        $this->assertSame([[-1.0, -2.0], [0.0, 0.0], [1.0, 2.0]], $centered);
    }

    #[TestDox('分散共分散行列は対称で、対角に件数から 1 を引いた数で割った分散が並ぶ')]
    public function test分散共分散行列は対称(): void
    {
        $covariance = Chapter13::covarianceMatrix(self::CORRELATED);

        $this->assertEqualsWithDelta([[1.0, 2.0], [2.0, 4.0]], $covariance, 1.0e-12);
    }

    #[TestDox('データが 1 件も無ければ平均を求められない')]
    public function test空のデータ(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Chapter13::columnMeans([]);
    }

    #[TestDox('1 件しかないと分散共分散行列を作れない')]
    public function test1件では分散共分散行列を作れない(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Chapter13::covarianceMatrix([[1.0, 2.0]]);
    }

    // 13.6 MathPHP の固有値分解を確かめる

    #[TestDox('固有値は大きい順に並び、固有ベクトルは長さ 1 になる')]
    public function test固有値は大きい順(): void
    {
        $eigen = Chapter13::eigenDecomposition([[4.0, 1.0], [1.0, 3.0]]);

        $this->assertGreaterThan($eigen['values'][1], $eigen['values'][0]);

        foreach ($eigen['vectors'] as $vector) {
            $this->assertEqualsWithDelta(1.0, sqrt($vector[0] ** 2 + $vector[1] ** 2), 1.0e-12);
        }
    }

    #[TestDox('MathPHP の Jacobi 法とほぼ同じ固有値になる')]
    public function testMathPhpの固有値と比べる(): void
    {
        $covariance = Chapter13::covarianceMatrix(self::SPREAD);
        $mine = Chapter13::eigenDecomposition($covariance)['values'];

        $this->assertEqualsWithDelta($mine, Chapter13::mathPhpEigenvalues($covariance), 1.0e-9);
    }

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

    // 13.7 主成分を求める

    #[TestDox('完全に相関する 2 列なら第 1 主成分の寄与率が 1 になる')]
    public function test完全に相関する2列(): void
    {
        $model = Chapter13::fit(self::CORRELATED, 2);

        $this->assertEqualsWithDelta(1.0, $model->explainedVarianceRatio[0], 1.0e-12);
        $this->assertEqualsWithDelta(0.0, $model->explainedVarianceRatio[1], 1.0e-12);
    }

    #[TestDox('主成分は絶対値が最大の要素が正になるようにそろえる')]
    public function test主成分の符号をそろえる(): void
    {
        $this->assertSame(
            [[0.8, -0.6], [0.8, 0.6]],
            Chapter13::normalizeSigns([[-0.8, 0.6], [0.8, 0.6]]),
        );
    }

    #[TestDox('主成分の数が列の数を超えると失敗する')]
    public function test主成分の数が範囲外(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Chapter13::fit(self::CORRELATED, 3);
    }

    // 13.8 データを主成分の向きに射影する

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

    // 13.9 必要な主成分の数と、影響が大きい列

    #[TestDox('累積寄与率がしきい値に届くまでの主成分の数を返す')]
    public function test累積寄与率がしきい値に届くまでの数(): void
    {
        $this->assertSame(2, Chapter13::componentsNeeded([0.5, 0.4, 0.1], 0.8));
        $this->assertSame(1, Chapter13::componentsNeeded([0.9, 0.1], 0.8));
    }

    #[TestDox('しきい値に届かなければすべての主成分の数を返す')]
    public function testしきい値に届かない(): void
    {
        $this->assertSame(2, Chapter13::componentsNeeded([0.5, 0.2], 0.8));
    }

    #[TestDox('係数の絶対値が大きい順に列名と係数を返す')]
    public function test係数の大きい列(): void
    {
        $this->assertSame(
            [['c', -0.7], ['a', 0.5]],
            Chapter13::topLoadings([0.5, 0.2, -0.7], ['a', 'b', 'c'], 2),
        );
    }

    #[TestDox('係数の絶対値が同じなら元の列の順を残す')]
    public function test係数が同点なら元の順(): void
    {
        $this->assertSame(
            [['a', 0.5], ['b', -0.5]],
            Chapter13::topLoadings([0.5, -0.5, 0.1], ['a', 'b', 'c'], 2),
        );
    }

    // 13.10 Rubix ML の PCA と突き合わせる

    #[TestDox('Rubix ML の主成分は自作と一致する')]
    public function testRubixの主成分と一致する(): void
    {
        $this->assertEqualsWithDelta(
            Chapter13::fit(self::SPREAD, 2)->components,
            Chapter13::rubixComponents(self::SPREAD, 2),
            1.0e-9,
        );
    }

    #[TestDox('Rubix ML は寄与率を持たないが、lossiness の差から復元できる')]
    public function testRubixの寄与率を復元する(): void
    {
        $this->assertEqualsWithDelta(
            Chapter13::fit(self::CORRELATED, 2)->explainedVarianceRatio,
            Chapter13::rubixExplainedVarianceRatio(self::CORRELATED),
            1.0e-9,
        );
    }

    // 13.11 Boston を前処理する

    #[TestDox('CRIME をダミー変数の列に置き換える')]
    public function testダミー変数の列に置き換える(): void
    {
        $this->assertSame(
            ['RM', 'PRICE', 'CRIME_low', 'CRIME_very_low'],
            Chapter13::standardizeTable($this->bostonLike())['columns'],
        );
    }

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

    // 13.12 実データで要約する

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

    #[Group('data')]
    #[TestDox('実データでは MathPHP の固有値が 1e-6 の桁までしか合わない')]
    public function test実データのMathPHPの固有値(): void
    {
        $this->skipWithoutData();

        ['columns' => $columns, 'x' => $x] = Chapter13::loadBoston(Dataset::path('Boston.csv'));
        $covariance = Chapter13::covarianceMatrix(Chapter13::toMatrix($x, $columns));
        $mine = Chapter13::eigenDecomposition($covariance)['values'];
        $theirs = Chapter13::mathPhpEigenvalues($covariance);
        $gap = 0.0;

        foreach ($mine as $i => $value) {
            $gap = max($gap, abs($value - $theirs[$i]));
        }

        // MathPHP は「対角行列とみなす」判定がゆるいので、自作より早く回転を止める
        $this->assertGreaterThan(1.0e-9, $gap);
        $this->assertLessThan(1.0e-5, $gap);
    }

    #[Group('data')]
    #[TestDox('実行すると寄与率と主成分の解釈を表示する')]
    public function test実行すると寄与率を表示する(): void
    {
        $this->skipWithoutData();

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
    }

    private function skipWithoutData(): void
    {
        if (!Dataset::exists('Boston.csv')) {
            $this->markTestSkipped('学習データがありません');
        }
    }
}
