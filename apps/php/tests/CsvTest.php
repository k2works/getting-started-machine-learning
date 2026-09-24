<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Csv;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class CsvTest extends TestCase
{
    #[TestDox('BOM を取り除いて列名にする')]
    public function testBomを取り除いて列名にする(): void
    {
        $table = Csv::parseTable("\u{FEFF}身長,体重\n170,60\n");

        $this->assertSame(['身長', '体重'], $table->columns);
        $this->assertSame([['身長' => '170', '体重' => '60']], $table->rows);
    }

    #[TestDox('空行を読み飛ばす')]
    public function test空行を読み飛ばす(): void
    {
        $table = Csv::parseTable("身長,体重\n170,60\n\n160,50\n");

        $this->assertCount(2, $table->rows);
    }

    #[TestDox('タブ区切りも読める')]
    public function testタブ区切りも読める(): void
    {
        $table = Csv::parseTable("身長\t体重\n170\t60\n", "\t");

        $this->assertSame(['身長', '体重'], $table->columns);
    }

    #[TestDox('対応していない区切り文字は読めない')]
    public function test対応していない区切り文字は読めない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('区切り文字に対応していません: ;');

        Csv::parseTable("身長;体重\n170;60\n", ';');
    }

    #[TestDox('列の数が違う行があれば読めない')]
    public function test列の数が違う行があれば読めない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('列の数が違います: 3 と 2');

        Csv::parseTable("身長,体重\n170,60,20\n");
    }

    #[TestDox('空の CSV は読めない')]
    public function test空のCsvは読めない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('CSV が空です');

        Csv::parseTable('');
    }

    #[TestDox('無いファイルは読めない')]
    public function test無いファイルは読めない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('CSV を開けません: ');

        Csv::readTable(sys_get_temp_dir() . '/no-such-file.csv');
    }

    #[TestDox('行のリストだけを取り出せる')]
    public function test行のリストだけを取り出せる(): void
    {
        $path = tempnam(sys_get_temp_dir(), 'csv') . '.csv';
        file_put_contents($path, "身長,体重\n170,60\n");

        try {
            $this->assertSame([['身長' => '170', '体重' => '60']], Csv::read($path));
        } finally {
            @unlink($path);
        }
    }
}
