<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter01;
use GettingStartedMl\Dataset;
use GettingStartedMl\Features;
use GettingStartedMl\Person;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class Chapter01Test extends TestCase
{
    /** 一時ファイルに CSV を書き、テストが終わったら消す。 */
    private function csv(string $contents): string
    {
        $path = tempnam(sys_get_temp_dir(), 'kvst') . '.csv';
        file_put_contents($path, $contents);
        $this->paths[] = $path;

        return $path;
    }

    /** @var list<string> 後始末するファイル */
    private array $paths = [];

    protected function tearDown(): void
    {
        foreach ($this->paths as $path) {
            @unlink($path);
        }

        $this->paths = [];
    }

    #[TestDox('二十代はきのこ派と判定する')]
    public function test二十代はきのこ派と判定する(): void
    {
        $this->assertSame(Chapter01::KINOKO, Chapter01::predictByRule(new Features(170, 60, 20)));
    }

    #[TestDox('二十代以外はたけのこ派と判定する')]
    public function test二十代以外はたけのこ派と判定する(): void
    {
        foreach ([10, 30, 40, 50] as $ageGroup) {
            $this->assertSame(Chapter01::TAKENOKO, Chapter01::predictByRule(new Features(170, 60, $ageGroup)));
        }
    }

    #[TestDox('全部当たれば正解率は一になる')]
    public function test全部当たれば正解率は一になる(): void
    {
        $this->assertEqualsWithDelta(
            1.0,
            Chapter01::accuracy([Chapter01::KINOKO, Chapter01::TAKENOKO], [Chapter01::KINOKO, Chapter01::TAKENOKO]),
            1e-9,
        );
    }

    #[TestDox('半分当たれば正解率は零点五になる')]
    public function test半分当たれば正解率は零点五になる(): void
    {
        $this->assertEqualsWithDelta(
            0.5,
            Chapter01::accuracy([Chapter01::KINOKO, Chapter01::KINOKO], [Chapter01::KINOKO, Chapter01::TAKENOKO]),
            1e-9,
        );
    }

    #[TestDox('件数が違えば正解率を求められない')]
    public function test件数が違えば正解率を求められない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('予測と正解ラベルの件数が違います: 1 と 2');

        Chapter01::accuracy([Chapter01::KINOKO], [Chapter01::KINOKO, Chapter01::TAKENOKO]);
    }

    #[TestDox('人物のリストを特徴量と正解ラベルに分ける')]
    public function test人物のリストを特徴量と正解ラベルに分ける(): void
    {
        $people = [
            new Person(170, 60, 20, Chapter01::KINOKO),
            new Person(160, 50, 30, Chapter01::TAKENOKO),
        ];

        [$x, $t] = Chapter01::splitFeaturesAndLabels($people);

        $this->assertEquals([new Features(170, 60, 20), new Features(160, 50, 30)], $x);
        $this->assertSame([Chapter01::KINOKO, Chapter01::TAKENOKO], $t);
    }

    #[TestDox('BOM 付きの CSV を列名で読み込む')]
    public function testBom付きのCsvを列名で読み込む(): void
    {
        $path = $this->csv("\u{FEFF}身長,体重,年代,派閥\n170,60,20,きのこ\n");

        $this->assertEquals([new Person(170, 60, 20, Chapter01::KINOKO)], Chapter01::loadPeople($path));
    }

    #[TestDox('数値でない値があれば読み込めない')]
    public function test数値でない値があれば読み込めない(): void
    {
        $path = $this->csv("身長,体重,年代,派閥\n高い,60,20,きのこ\n");

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('身長 を数値として読めません: 高い');

        Chapter01::loadPeople($path);
    }

    #[TestDox('列が無ければ読み込めない')]
    public function test列が無ければ読み込めない(): void
    {
        $path = $this->csv("身長,体重,年代\n170,60,20\n");

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('列がありません: 派閥');

        Chapter01::loadPeople($path);
    }

    #[TestDox('ファイルが無ければ読み込めない')]
    public function testファイルが無ければ読み込めない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('CSV を開けません: ');

        Chapter01::loadPeople(sys_get_temp_dir() . '/no-such-file.csv');
    }

    #[Group('data')]
    #[TestDox('実データでルールによる判定の正解率を求める')]
    public function test実データでルールによる判定の正解率を求める(): void
    {
        if (!Dataset::exists('KvsT.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('KvsT.csv'));
        }

        $people = Chapter01::loadPeople(Dataset::path('KvsT.csv'));
        [$x, $t] = Chapter01::splitFeaturesAndLabels($people);
        $predictions = array_map(Chapter01::predictByRule(...), $x);

        $this->assertEqualsWithDelta(0.7368, Chapter01::accuracy($predictions, $t), 1e-4);
    }

    #[TestDox('正解ラベルが無ければ正解率を求められない')]
    public function test正解ラベルが無ければ正解率を求められない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('正解ラベルがありません');

        Chapter01::accuracy([], []);
    }

    #[TestDox('空の CSV は読み込めない')]
    public function test空のCsvは読み込めない(): void
    {
        $path = $this->csv('');

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('CSV が空です: ');

        Chapter01::loadPeople($path);
    }

    #[TestDox('空行は読み飛ばす')]
    public function test空行は読み飛ばす(): void
    {
        $path = $this->csv("身長,体重,年代,派閥\n170,60,20,きのこ\n\n");

        $this->assertCount(1, Chapter01::loadPeople($path));
    }

    #[TestDox('件数と正解率を表示する')]
    public function test件数と正解率を表示する(): void
    {
        $path = $this->csv("身長,体重,年代,派閥\n170,60,20,きのこ\n160,50,30,きのこ\n");

        $this->assertSame("データ件数: 2\nルールによる判定の正解率: 0.5000\n", Chapter01::run($path));
    }
}
