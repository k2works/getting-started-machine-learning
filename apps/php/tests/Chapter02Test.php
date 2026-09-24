<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter02;
use GettingStartedMl\Csv;
use GettingStartedMl\Dataset;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class Chapter02Test extends TestCase
{
    /** @var list<string> 後始末するファイル */
    private array $paths = [];

    private function csv(string $contents): string
    {
        $path = tempnam(sys_get_temp_dir(), 'iris') . '.csv';
        file_put_contents($path, $contents);
        $this->paths[] = $path;

        return $path;
    }

    protected function tearDown(): void
    {
        foreach ($this->paths as $path) {
            @unlink($path);
        }

        $this->paths = [];
    }

    #[TestDox('空欄は欠損値として数える')]
    public function test空欄は欠損値として数える(): void
    {
        $table = Csv::parseTable("a,b\n1,\n,2\n3,4\n");

        $this->assertSame(['a' => 1, 'b' => 1], Chapter02::countMissing($table));
    }

    #[TestDox('空欄の数値は null になる')]
    public function test空欄の数値はnullになる(): void
    {
        $this->assertNull(Chapter02::number(['a' => ' '], 'a'));
        $this->assertSame(1.5, Chapter02::number(['a' => '1.5'], 'a'));
    }

    #[TestDox('数値として読めない値があれば失敗する')]
    public function test数値として読めない値があれば失敗する(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('a を数値として読めません: 高い');

        Chapter02::number(['a' => '高い'], 'a');
    }

    #[TestDox('列が無ければ読めない')]
    public function test列が無ければ読めない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('列がありません: b');

        Chapter02::text(['a' => '1'], 'b');
    }

    #[TestDox('欠損値を除いて平均値を求める')]
    public function test欠損値を除いて平均値を求める(): void
    {
        $rows = [['a' => '1'], ['a' => ''], ['a' => '3']];

        $this->assertEqualsWithDelta(['a' => 2.0], Chapter02::columnMeans($rows, ['a']), 1e-9);
    }

    #[TestDox('値がすべて空欄なら平均値を求められない')]
    public function test値がすべて空欄なら平均値を求められない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('値がすべて空欄です: a');

        Chapter02::columnMeans([['a' => ''], ['a' => '']], ['a']);
    }

    #[TestDox('欠損値を指定した値で補完する')]
    public function test欠損値を指定した値で補完する(): void
    {
        $filled = Chapter02::fillMissing([['a' => '1'], ['a' => '']], ['a'], ['a' => 9.0]);

        $this->assertEqualsWithDelta([['a' => 1.0], ['a' => 9.0]], $filled, 1e-9);
    }

    #[TestDox('補完する値が無ければ失敗する')]
    public function test補完する値が無ければ失敗する(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('補完する値がありません: a');

        Chapter02::fillMissing([['a' => '']], ['a'], []);
    }

    #[TestDox('正解ラベルの列を特徴量から外す')]
    public function test正解ラベルの列を特徴量から外す(): void
    {
        $table = Csv::parseTable("a,種類\n1,setosa\n");

        [$columns, $rows, $labels] = Chapter02::splitFeaturesAndTarget($table, Chapter02::TARGET);

        $this->assertSame(['a'], $columns);
        $this->assertCount(1, $rows);
        $this->assertSame(['setosa'], $labels);
    }

    #[TestDox('テストデータの割合は切り上げる')]
    public function testテストデータの割合は切り上げる(): void
    {
        $x = array_map(static fn (int $i): array => ['a' => (string) $i], range(1, 10));
        $t = array_map(static fn (int $i): string => "t{$i}", range(1, 10));

        $split = Chapter02::splitTrainTest($x, $t, 0.25, 0);

        // 10 件の 25% は 2.5 なので、切り上げて 3 件がテストデータになる。
        $this->assertCount(7, $split['xTrain']);
        $this->assertCount(3, $split['xTest']);
        $this->assertCount(7, $split['tTrain']);
        $this->assertCount(3, $split['tTest']);
    }

    #[TestDox('件数が違えば分割できない')]
    public function test件数が違えば分割できない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('件数が違います: 2 と 1');

        Chapter02::splitTrainTest([['a' => '1'], ['a' => '2']], ['t'], 0.5, 0);
    }

    #[TestDox('分割しても特徴量とラベルの組が崩れない')]
    public function test分割しても組が崩れない(): void
    {
        $x = array_map(static fn (int $i): array => ['a' => (string) $i], range(1, 10));
        $t = array_map(static fn (int $i): string => "t{$i}", range(1, 10));

        $split = Chapter02::splitTrainTest($x, $t, 0.3, 0);

        foreach ($split['xTrain'] as $i => $row) {
            $this->assertSame('t' . $row['a'], $split['tTrain'][$i]);
        }
    }

    #[TestDox('前処理のあとに空欄が残らない')]
    public function test前処理のあとに空欄が残らない(): void
    {
        $path = $this->csv("がく片長さ,種類\n1,setosa\n3,setosa\n,versicolor\n5,versicolor\n");

        $prepared = Chapter02::prepareIris($path, 0.25, 0);
        $values = array_merge(
            array_column($prepared['xTrain'], 'がく片長さ'),
            array_column($prepared['xTest'], 'がく片長さ'),
        );

        // 4 件すべてが数値になっている（補完されなかった行は元の値のまま）。
        $this->assertCount(4, $values);
        $this->assertSame(4, count(array_filter($values, is_finite(...))));
    }

    #[TestDox('補完に使う平均値は訓練データだけから求める')]
    public function test補完に使う平均値は訓練データだけから求める(): void
    {
        // 訓練データだけの平均（1 と 3 の平均＝2.0）で補完し、テストデータの 5 は混ぜない。
        $train = [['a' => '1'], ['a' => '3']];
        $means = Chapter02::columnMeans($train, ['a']);

        $this->assertEqualsWithDelta(['a' => 2.0], $means, 1e-9);
        $this->assertEqualsWithDelta(
            [['a' => 2.0]],
            Chapter02::fillMissing([['a' => '']], ['a'], $means),
            1e-9,
        );
    }

    #[Group('data')]
    #[TestDox('実データの前処理がほかの言語版と一致する')]
    public function test実データの前処理が一致する(): void
    {
        if (!Dataset::exists('iris.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('iris.csv'));
        }

        $path = Dataset::path('iris.csv');
        $table = Chapter02::loadTable($path);

        $this->assertCount(150, $table->rows);
        $this->assertSame(
            ['がく片長さ' => 2, 'がく片幅' => 1, '花弁長さ' => 2, '花弁幅' => 2, '種類' => 0],
            Chapter02::countMissing($table),
        );

        $split = Chapter02::prepareIris($path, 0.3, 0);

        $this->assertCount(105, $split['xTrain']);
        $this->assertCount(45, $split['xTest']);

        // Java 版・Scala 版・Clojure 版・Elixir 版と一致する。
        $values = array_column($split['xTrain'], 'がく片長さ');
        $this->assertEqualsWithDelta(
            0.4215384615384616,
            array_sum($values) / count($values),
            1e-15,
        );
    }

    #[Group('data')]
    #[TestDox('実データの前処理の結果を表示する')]
    public function test実データの前処理の結果を表示する(): void
    {
        if (!Dataset::exists('iris.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('iris.csv'));
        }

        $output = Chapter02::run();

        $this->assertStringContainsString('データ件数: 150', $output);
        $this->assertStringContainsString('訓練データ: 105 件, テストデータ: 45 件', $output);
    }
}
