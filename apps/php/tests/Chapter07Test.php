<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter07;
use GettingStartedMl\Csv;
use GettingStartedMl\Dataset;
use GettingStartedMl\LinearModel;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class Chapter07Test extends TestCase
{
    /**
     * 答えの分かっている 4 点。切片 1.0、a の係数 2.0、b の係数 -3.0 で誤差なく当てはまる。
     *
     * @return list<array<string, float>>
     */
    private function squareX(): array
    {
        return [
            ['a' => 0.0, 'b' => 0.0],
            ['a' => 1.0, 'b' => 0.0],
            ['a' => 0.0, 'b' => 1.0],
            ['a' => 1.0, 'b' => 1.0],
        ];
    }

    /** @return list<float> */
    private function squareT(): array
    {
        return [1.0, 3.0, -2.0, 0.0];
    }

    #[TestDox('残差は実測値から予測値を引いた値')]
    public function test残差は実測値から予測値を引いた値(): void
    {
        $this->assertEqualsWithDelta([1.0, -2.0], Chapter07::residuals([3.0, 1.0], [2.0, 3.0]), 1e-12);
    }

    #[TestDox('件数が違えば残差を求められない')]
    public function test件数が違えば残差を求められない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('件数が違います: 2 と 1');

        Chapter07::residuals([1.0, 2.0], [1.0]);
    }

    #[TestDox('MAE は誤差の絶対値の平均')]
    public function testMaeは誤差の絶対値の平均(): void
    {
        $this->assertEqualsWithDelta(1.5, Chapter07::meanAbsoluteError([3.0, 1.0], [2.0, 3.0]), 1e-12);
    }

    #[TestDox('RMSE は誤差の 2 乗の平均の平方根')]
    public function testRmseは誤差の2乗の平均の平方根(): void
    {
        // (1² + 2²) / 2 = 2.5 の平方根。
        $this->assertEqualsWithDelta(sqrt(2.5), Chapter07::rootMeanSquaredError([3.0, 1.0], [2.0, 3.0]), 1e-12);
    }

    #[TestDox('完全に当たれば決定係数は 1')]
    public function test完全に当たれば決定係数は1(): void
    {
        $this->assertEqualsWithDelta(1.0, Chapter07::r2Score([1.0, 2.0, 3.0], [1.0, 2.0, 3.0]), 1e-12);
    }

    #[TestDox('平均を答え続けると決定係数は 0')]
    public function test平均を答え続けると決定係数は0(): void
    {
        $this->assertEqualsWithDelta(0.0, Chapter07::r2Score([1.0, 2.0, 3.0], [2.0, 2.0, 2.0]), 1e-12);
    }

    #[TestDox('実測値がすべて同じなら決定係数を求められない')]
    public function test実測値がすべて同じなら決定係数を求められない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('実測値がすべて同じ値です');

        Chapter07::r2Score([2.0, 2.0], [1.0, 3.0]);
    }

    #[TestDox('列名と係数の数が違うモデルは作れない')]
    public function test列名と係数の数が違うモデルは作れない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('列名と係数の数が違います: 2 と 1');

        new LinearModel(0.0, ['a', 'b'], [1.0]);
    }

    #[TestDox('係数は列名で読む')]
    public function test係数は列名で読む(): void
    {
        $model = new LinearModel(1.0, ['a', 'b'], [2.0, -3.0]);

        $this->assertSame(-3.0, $model->coefficient('b'));
    }

    #[TestDox('無い列の係数は読めない')]
    public function test無い列の係数は読めない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('係数がありません: c');

        (new LinearModel(1.0, ['a'], [2.0]))->coefficient('c');
    }

    #[TestDox('予測値は切片と係数の重み付きの和')]
    public function test予測値は切片と係数の重み付きの和(): void
    {
        $model = new LinearModel(1.0, ['a', 'b'], [2.0, -3.0]);

        // 列の並び順が違っても、列名で対応させるので同じ値になる。
        $this->assertEqualsWithDelta(0.0, $model->predictOne(['b' => 1.0, 'a' => 1.0]), 1e-12);
        $this->assertEqualsWithDelta([1.0, 3.0], $model->predict([['a' => 0.0, 'b' => 0.0], ['a' => 1.0, 'b' => 0.0]]), 1e-12);
    }

    #[TestDox('特徴量に無い列は予測に使えない')]
    public function test特徴量に無い列は予測に使えない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('特徴量がありません: b');

        (new LinearModel(0.0, ['b'], [1.0]))->predictOne(['a' => 1.0]);
    }

    #[TestDox('計画行列の先頭は 1 の列')]
    public function test計画行列の先頭は1の列(): void
    {
        $design = Chapter07::designMatrix([['a' => 2.0, 'b' => 3.0]], ['a', 'b']);

        $this->assertEqualsWithDelta([[1.0, 2.0, 3.0]], $design, 1e-12);
    }

    #[TestDox('正規方程式で切片と係数を求める')]
    public function test正規方程式で切片と係数を求める(): void
    {
        $model = Chapter07::fit($this->squareX(), $this->squareT(), ['a', 'b']);

        $this->assertEqualsWithDelta(1.0, $model->intercept, 1e-9);
        $this->assertEqualsWithDelta(2.0, $model->coefficient('a'), 1e-9);
        $this->assertEqualsWithDelta(-3.0, $model->coefficient('b'), 1e-9);
    }

    #[TestDox('訓練データが空なら学習できない')]
    public function test訓練データが空なら学習できない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('訓練データが空です');

        Chapter07::fit([], [], ['a']);
    }

    #[TestDox('特徴量と実測値の件数が違えば学習できない')]
    public function test特徴量と実測値の件数が違えば学習できない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('特徴量と実測値の件数が違います: 1 と 2');

        Chapter07::fit([['a' => 1.0]], [1.0, 2.0], ['a']);
    }

    #[TestDox('条件のよいデータなら Rubix ML のリッジと一致する')]
    public function test条件のよいデータならリッジと一致する(): void
    {
        $mine = Chapter07::fit($this->squareX(), $this->squareT(), ['a', 'b']);
        $theirs = Chapter07::rubixFit($this->squareX(), $this->squareT(), ['a', 'b']);

        $this->assertEqualsWithDelta($mine->intercept, $theirs->intercept, 1e-9);
        $this->assertEqualsWithDelta($mine->coefficient('a'), $theirs->coefficient('a'), 1e-9);
        $this->assertEqualsWithDelta($mine->coefficient('b'), $theirs->coefficient('b'), 1e-9);
    }

    #[TestDox('正則化を強めるとリッジの係数は 0 に近づく')]
    public function test正則化を強めるとリッジの係数は0に近づく(): void
    {
        $plain = Chapter07::rubixFit($this->squareX(), $this->squareT(), ['a', 'b']);
        $penalized = Chapter07::rubixFit($this->squareX(), $this->squareT(), ['a', 'b'], 10.0);

        $this->assertLessThan(abs($plain->coefficient('a')), abs($penalized->coefficient('a')));
        $this->assertLessThan(abs($plain->coefficient('b')), abs($penalized->coefficient('b')));
    }

    #[TestDox('外れ値は SNS2 が大きく興行収入が小さい行')]
    public function test外れ値の判定(): void
    {
        $table = Csv::parseTable("SNS2,sales\n1200,8000\n1200,9000\n900,8000\n");

        $this->assertCount(2, Chapter07::removeOutliers($table)->rows);
        // 列はそのまま残る。
        $this->assertSame(['SNS2', 'sales'], Chapter07::removeOutliers($table)->columns);
    }

    #[TestDox('前処理のあとに欠損値が残らない')]
    public function test前処理のあとに欠損値が残らない(): void
    {
        $path = tempnam(sys_get_temp_dir(), 'cinema') . '.csv';
        file_put_contents(
            $path,
            "SNS1,SNS2,actor,original,sales\n"
            . "10,100,1000,1,5000\n20,200,2000,0,6000\n,300,3000,1,7000\n40,400,4000,0,8000\n",
        );

        try {
            $split = Chapter07::prepareCinema($path, 0.25, 0);
            $values = array_merge(
                array_column($split['xTrain'], 'SNS1'),
                array_column($split['xTest'], 'SNS1'),
            );

            $this->assertCount(4, $values);
            $this->assertSame(4, count(array_filter($values, is_finite(...))));
        } finally {
            @unlink($path);
        }
    }

    #[Group('data')]
    #[TestDox('実データで自作とリッジの係数が一致する')]
    public function test実データで自作とリッジの係数が一致する(): void
    {
        if (!Dataset::exists('cinema.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('cinema.csv'));
        }

        $split = Chapter07::prepareCinema(Dataset::path('cinema.csv'), 0.2, 0);
        $columns = Chapter07::FEATURE_COLUMNS;
        $mine = Chapter07::fit($split['xTrain'], $split['tTrain'], $columns);
        $theirs = Chapter07::rubixFit($split['xTrain'], $split['tTrain'], $columns);

        // Rubix ML のリッジは自作と同じ正規方程式を解くので、桁の違う列が混ざっていても一致する。
        $this->assertEqualsWithDelta($mine->intercept, $theirs->intercept, 1e-6);

        foreach ($columns as $column) {
            $this->assertEqualsWithDelta($mine->coefficient($column), $theirs->coefficient($column), 1e-9);
        }
    }

    #[Group('data')]
    #[TestDox('実データで残差平方和が自作とリッジで同じ')]
    public function test実データで残差平方和が同じ(): void
    {
        if (!Dataset::exists('cinema.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('cinema.csv'));
        }

        $split = Chapter07::prepareCinema(Dataset::path('cinema.csv'), 0.2, 0);
        $columns = Chapter07::FEATURE_COLUMNS;
        $mine = Chapter07::fit($split['xTrain'], $split['tTrain'], $columns);
        $theirs = Chapter07::rubixFit($split['xTrain'], $split['tTrain'], $columns);

        $t = $split['tTrain'];
        $mineSse = Chapter07::sumOfSquares(Chapter07::residuals($t, $mine->predict($split['xTrain'])));
        $theirsSse = Chapter07::sumOfSquares(Chapter07::residuals($t, $theirs->predict($split['xTrain'])));

        // 最小二乗解はただ 1 つなので、両方が解に届いていれば残差平方和も一致する。
        $this->assertEqualsWithDelta($mineSse, $theirsSse, 1e-6);
    }

    #[Group('data')]
    #[TestDox('実データの学習と評価がほかの言語版と一致する')]
    public function test実データの学習と評価が一致する(): void
    {
        if (!Dataset::exists('cinema.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('cinema.csv'));
        }

        $output = Chapter07::run();

        $this->assertStringContainsString('データ件数: 100', $output);
        $this->assertStringContainsString('外れ値を除いた件数: 99', $output);
        $this->assertStringContainsString('訓練データ: 79 件, テストデータ: 20 件', $output);
        $this->assertStringContainsString('切片: 6114.60', $output);
        $this->assertStringContainsString(
            '係数: SNS1=1.3804, SNS2=0.5218, actor=0.2900, original=208.8827',
            $output,
        );
        $this->assertStringContainsString('テストデータの評価: R2=0.6184, MAE=302.20, RMSE=376.14', $output);
    }

    #[TestDox('訓練データが空ならリッジも学習できない')]
    public function test訓練データが空ならリッジも学習できない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('訓練データが空です');

        Chapter07::rubixFit([], [], ['a']);
    }

    #[TestDox('特徴量に無い列は計画行列に入れられない')]
    public function test特徴量に無い列は計画行列に入れられない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('特徴量がありません: b');

        Chapter07::designMatrix([['a' => 1.0]], ['a', 'b']);
    }

    #[TestDox('外れ値の判定に使う列が空欄なら失敗する')]
    public function test外れ値の判定に使う列が空欄なら失敗する(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('値が空欄です: SNS2');

        Chapter07::isOutlier(['SNS2' => '', 'sales' => '100']);
    }
}
