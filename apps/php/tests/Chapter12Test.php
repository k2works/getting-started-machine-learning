<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter02;
use GettingStartedMl\Chapter07;
use GettingStartedMl\Chapter12;
use GettingStartedMl\Chapter12\BostonData;
use GettingStartedMl\Chapter12\Experiment;
use GettingStartedMl\Dataset;
use GettingStartedMl\Table;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class Chapter12Test extends TestCase
{
    /** @var list<string> */
    private const array COLUMNS = ['a', 'b'];

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

    // ---- リッジ回帰 ----

    #[TestDox('alpha が 0 のリッジ回帰は第 7 章の最小二乗法と一致する')]
    public function testAlphaが0のリッジ回帰は第7章の最小二乗法と一致する(): void
    {
        $ridge = Chapter12::ridgeFit($this->squareX(), $this->squareT(), self::COLUMNS, 0.0);
        $least = Chapter07::fit($this->squareX(), $this->squareT(), self::COLUMNS);

        $this->assertEqualsWithDelta($least->intercept, $ridge->intercept, 1e-9);
        $this->assertEqualsWithDelta($least->coefficients, $ridge->coefficients, 1e-9);
    }

    #[TestDox('alpha を大きくすると係数は 0 に近づき切片は正解の平均に近づく')]
    public function testAlphaを大きくすると係数は0に近づき切片は正解の平均に近づく(): void
    {
        $fitted = Chapter12::ridgeFit($this->squareX(), $this->squareT(), self::COLUMNS, 1.0e9);

        $this->assertEqualsWithDelta([0.0, 0.0], $fitted->coefficients, 1e-6);
        $this->assertEqualsWithDelta(0.5, $fitted->intercept, 1e-6);
    }

    #[TestDox('alpha を大きくするほど係数の絶対値の合計は小さくなる')]
    public function testAlphaを大きくするほど係数の絶対値の合計は小さくなる(): void
    {
        $sums = array_map(
            fn (float $alpha): float => Chapter12::coefficientAbsSum(
                Chapter12::ridgeFit($this->squareX(), $this->squareT(), self::COLUMNS, $alpha),
            ),
            [0.0, 0.1, 1.0, 10.0, 100.0],
        );

        for ($i = 1; $i < count($sums); ++$i) {
            $this->assertLessThan($sums[$i - 1], $sums[$i]);
        }
    }

    #[TestDox('特徴量と正解の件数が違えばリッジ回帰を学習できない')]
    public function test特徴量と正解の件数が違えばリッジ回帰を学習できない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('特徴量と正解の件数が違います: 4 と 3');

        Chapter12::ridgeFit($this->squareX(), [1.0, 2.0, 3.0], self::COLUMNS, 1.0);
    }

    // ---- ラッソ回帰 ----

    #[TestDox('軟しきい値作用素は閾値の分だけ 0 のほうへ縮める')]
    public function test軟しきい値作用素は閾値の分だけ0のほうへ縮める(): void
    {
        $this->assertSame(1.0, Chapter12::softThreshold(3.0, 2.0));
        $this->assertSame(-1.0, Chapter12::softThreshold(-3.0, 2.0));
        $this->assertSame(0.0, Chapter12::softThreshold(1.5, 2.0));
        $this->assertSame(0.0, Chapter12::softThreshold(-1.5, 2.0));
    }

    #[TestDox('alpha が 0 に近いラッソ回帰は最小二乗法に近づく')]
    public function testAlphaが0に近いラッソ回帰は最小二乗法に近づく(): void
    {
        $lasso = Chapter12::lassoFit($this->squareX(), $this->squareT(), self::COLUMNS, 1.0e-8);
        $least = Chapter07::fit($this->squareX(), $this->squareT(), self::COLUMNS);

        $this->assertEqualsWithDelta($least->intercept, $lasso->intercept, 1e-6);
        $this->assertEqualsWithDelta($least->coefficients, $lasso->coefficients, 1e-6);
    }

    #[TestDox('alpha を大きくするとラッソ回帰の係数はちょうど 0 になる')]
    public function testAlphaを大きくするとラッソ回帰の係数はちょうど0になる(): void
    {
        $fitted = Chapter12::lassoFit($this->squareX(), $this->squareT(), self::COLUMNS, 1000.0);

        // リッジ回帰は 0 に「近づく」だけだが、ラッソ回帰は 0 そのものにする
        $this->assertSame([0.0, 0.0], $fitted->coefficients);
        $this->assertEqualsWithDelta(0.5, $fitted->intercept, 1e-12);
    }

    #[TestDox('値がすべて同じ列はラッソ回帰の係数が 0 のままになる')]
    public function test値がすべて同じ列はラッソ回帰の係数が0のままになる(): void
    {
        // 平均を引くと 0 だけの列になり、割る相手（2 乗の合計）が 0 になる。
        // 0 で割らずに飛ばすので、その列の係数は 0 のまま残る
        $x = [
            ['a' => 0.0, 'b' => 5.0],
            ['a' => 1.0, 'b' => 5.0],
            ['a' => 2.0, 'b' => 5.0],
            ['a' => 3.0, 'b' => 5.0],
        ];
        $fitted = Chapter12::lassoFit($x, [1.0, 3.0, 5.0, 7.0], self::COLUMNS, 1.0e-8);

        $this->assertEqualsWithDelta(2.0, $fitted->coefficient('a'), 1e-6);
        $this->assertSame(0.0, $fitted->coefficient('b'));
    }

    #[TestDox('特徴量と正解の件数が違えばラッソ回帰を学習できない')]
    public function test特徴量と正解の件数が違えばラッソ回帰を学習できない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('特徴量と正解の件数が違います: 4 と 3');

        Chapter12::lassoFit($this->squareX(), [1.0, 2.0, 3.0], self::COLUMNS, 1.0);
    }

    #[TestDox('係数が 0 になった特徴量の名前を列の順に返す')]
    public function test係数が0になった特徴量の名前を列の順に返す(): void
    {
        $fitted = Chapter12::lassoFit($this->squareX(), $this->squareT(), self::COLUMNS, 1000.0);

        $this->assertSame(['a', 'b'], Chapter12::zeroCoefficientNames($fitted));
        $this->assertSame(
            [],
            Chapter12::zeroCoefficientNames(
                Chapter12::lassoFit($this->squareX(), $this->squareT(), self::COLUMNS, 1.0e-8),
            ),
        );
    }

    // ---- 実験の記録とモデル選択 ----

    #[TestDox('検証データの決定係数が最も高い実験を選ぶ')]
    public function test検証データの決定係数が最も高い実験を選ぶ(): void
    {
        $experiments = [
            new Experiment(0.0, 0.9, 0.5, 10.0),
            new Experiment(1.0, 0.8, 0.7, 8.0),
            new Experiment(10.0, 0.7, 0.7, 5.0),
        ];

        // 同じ値なら先の実験を選ぶ
        $this->assertSame(1.0, Chapter12::bestExperiment($experiments)->alpha);
    }

    #[TestDox('実験が 1 件も無ければ選べない')]
    public function test実験が1件も無ければ選べない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('実験結果が 1 件もありません');

        Chapter12::bestExperiment([]);
    }

    // ---- 外れ値を除く ----

    #[TestDox('z スコアの絶対値がしきい値を超える行を除く')]
    public function testZスコアの絶対値がしきい値を超える行を除く(): void
    {
        // 件数が n のとき z スコアの上限は (n - 1) / sqrt(n) なので、
        // 10 件では 2.85 までしか届かず 3.0 のしきい値に当たらない。20 件にする
        $rows = array_fill(0, 19, ['v' => '1']);
        $rows[] = ['v' => '1000'];

        $table = Chapter12::removeOutliers(new Table(['v'], $rows), ['v'], 3.0);

        $this->assertCount(19, $table->rows);
    }

    #[TestDox('外れ値の判定に使う列が空欄なら失敗する')]
    public function test外れ値の判定に使う列が空欄なら失敗する(): void
    {
        $rows = [['v' => '1'], ['v' => '']];

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('値が空欄です: v');

        Chapter12::removeOutliers(new Table(['v'], $rows), ['v'], 3.0);
    }

    #[TestDox('特徴量に無い列を指定すればリッジ回帰を学習できない')]
    public function test特徴量に無い列を指定すればリッジ回帰を学習できない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('特徴量がありません: c');

        Chapter12::ridgeFit($this->squareX(), $this->squareT(), ['a', 'c'], 1.0);
    }

    #[TestDox('しきい値を超える値が無ければ 1 行も減らない')]
    public function testしきい値を超える値が無ければ1行も減らない(): void
    {
        $rows = [['v' => '1'], ['v' => '2'], ['v' => '3'], ['v' => '4']];

        $this->assertCount(4, Chapter12::removeOutliers(new Table(['v'], $rows), ['v'], 3.0)->rows);
    }

    // ---- Rubix ML との突き合わせ ----

    #[TestDox('Rubix ML の Ridge は同じ alpha なら自作のリッジ回帰と一致する')]
    public function testRubixMlのRidgeは同じAlphaなら自作のリッジ回帰と一致する(): void
    {
        foreach ([0.1, 1.0, 10.0, 100.0] as $alpha) {
            $mine = Chapter12::ridgeFit($this->squareX(), $this->squareT(), self::COLUMNS, $alpha);
            $theirs = Chapter12::rubixRidgeFit($this->squareX(), $this->squareT(), self::COLUMNS, $alpha);

            $this->assertEqualsWithDelta($mine->intercept, $theirs->intercept, 1e-9);
            $this->assertEqualsWithDelta($mine->coefficients, $theirs->coefficients, 1e-9);
        }
    }

    #[TestDox('Rubix ML の Ridge の既定の alpha は 1.0 なので省くと別のモデルになる')]
    public function testRubixMlのRidgeの既定のAlphaは1なので省くと別のモデルになる(): void
    {
        $withDefault = Chapter07::rubixFit($this->squareX(), $this->squareT(), self::COLUMNS, 1.0);
        $leastSquares = Chapter12::ridgeFit($this->squareX(), $this->squareT(), self::COLUMNS, 0.0);

        $this->assertEqualsWithDelta(
            Chapter12::ridgeFit($this->squareX(), $this->squareT(), self::COLUMNS, 1.0)->coefficients,
            $withDefault->coefficients,
            1e-9,
        );
        $this->assertGreaterThan(
            Chapter12::coefficientAbsSum($withDefault),
            Chapter12::coefficientAbsSum($leastSquares),
        );
    }

    // ---- 実データ ----

    private function boston(): BostonData
    {
        return Chapter12::prepareBoston(
            Dataset::path('Boston.csv'),
            Chapter12::TEST_SIZE,
            Chapter12::VALIDATION_SIZE,
            Chapter12::SEED,
        );
    }

    #[Group('data')]
    #[TestDox('外れ値を除いてから 3 つに分ける')]
    public function test外れ値を除いてから3つに分ける(): void
    {
        $total = count(Chapter02::loadTable(Dataset::path('Boston.csv'))->rows);
        $data = $this->boston();

        $this->assertSame(100, $total);
        $this->assertSame(98, $data->kept);
        $this->assertCount(47, $data->tTrain);
        $this->assertCount(21, $data->tValid);
        $this->assertCount(30, $data->tTest);
        $this->assertSame(
            ['RM', 'PTRATIO', 'LSTAT', 'RM^2', 'RM PTRATIO', 'RM LSTAT', 'PTRATIO^2', 'PTRATIO LSTAT', 'LSTAT^2'],
            $data->featureNames,
        );
    }

    #[Group('data')]
    #[TestDox('検証データで選んだリッジ回帰はテストデータで線形回帰を上回る')]
    public function test検証データで選んだリッジ回帰はテストデータで線形回帰を上回る(): void
    {
        $data = $this->boston();
        $best = Chapter12::bestExperiment(Chapter12::runRidgeExperiments($data, Chapter12::ALPHAS));

        $this->assertSame(10.0, $best->alpha);
        $this->assertGreaterThan(
            Chapter12::testScore($data, 0.0),
            Chapter12::testScore($data, $best->alpha),
        );
    }

    #[Group('data')]
    #[TestDox('ラッソ回帰は 9 列のうち 3 列の係数をちょうど 0 にする')]
    public function testラッソ回帰は9列のうち3列の係数をちょうど0にする(): void
    {
        $data = $this->boston();
        $lasso = Chapter12::lassoFit(
            $data->xTrain,
            $data->tTrain,
            $data->featureNames,
            Chapter12::LASSO_ALPHA,
        );

        $this->assertSame(
            ['PTRATIO^2', 'PTRATIO LSTAT', 'LSTAT^2'],
            Chapter12::zeroCoefficientNames($lasso),
        );
    }

    #[Group('data')]
    #[TestDox('実データでも自作のリッジ回帰は Rubix ML と一致する')]
    public function test実データでも自作のリッジ回帰はRubixMlと一致する(): void
    {
        $data = $this->boston();
        $mine = Chapter12::ridgeFit($data->xTrain, $data->tTrain, $data->featureNames, 10.0);
        $theirs = Chapter12::rubixRidgeFit($data->xTrain, $data->tTrain, $data->featureNames, 10.0);

        // 9 列の多項式特徴量は互いに強く相関するが、自作の LU 分解と Rubix ML の
        // 逆行列は 1e-12 まで一致する（Elixir 版が Scholar の特異値分解と 5e-6 しか
        // 合わなかったのと対照的）
        $this->assertEqualsWithDelta($mine->intercept, $theirs->intercept, 1e-12);
        $this->assertEqualsWithDelta($mine->coefficients, $theirs->coefficients, 1e-12);
    }

    #[Group('data')]
    #[TestDox('実行すると実験の表とモデル選択の結果を表示する')]
    public function test実行すると実験の表とモデル選択の結果を表示する(): void
    {
        $this->assertSame(
            "データ件数: 98（外れ値 2 件を除外）\n"
            . "訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件\n"
            . "特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2\n"
            . "alpha  訓練 R²  検証 R²  係数の絶対値の合計\n"
            . "  0.0  0.8827  0.7272  14.187\n"
            . "  0.1  0.8827  0.7274  14.104\n"
            . "  1.0  0.8823  0.7288  13.594\n"
            . " 10.0  0.8681  0.7349  11.573\n"
            . "100.0  0.6583  0.5985  5.684\n"
            . "検証データで選んだ alpha: 10.0\n"
            . "テストデータの決定係数: 線形回帰 0.5224, リッジ回帰 0.6243\n"
            . "Rubix ML のリッジ回帰との係数の最大の差: 4.4e-15\n"
            . "ラッソ回帰（alpha=23.5）で係数が 0 になった特徴量: PTRATIO^2, PTRATIO LSTAT, LSTAT^2\n",
            Chapter12::run(),
        );
    }
}
