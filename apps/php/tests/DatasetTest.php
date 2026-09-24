<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Dataset;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class DatasetTest extends TestCase
{
    #[TestDox('環境変数が無ければ既定の置き場を使う')]
    public function test環境変数が無ければ既定の置き場を使う(): void
    {
        $this->assertSame(Dataset::DEFAULT_DIR, Dataset::dir([]));
    }

    #[TestDox('環境変数が空なら既定の置き場を使う')]
    public function test環境変数が空なら既定の置き場を使う(): void
    {
        $this->assertSame(Dataset::DEFAULT_DIR, Dataset::dir([Dataset::ENV_NAME => '']));
    }

    #[TestDox('環境変数があればその置き場を使う')]
    public function test環境変数があればその置き場を使う(): void
    {
        $this->assertSame('/tmp/ml', Dataset::dir([Dataset::ENV_NAME => '/tmp/ml']));
    }

    #[TestDox('置き場とファイル名をつないで道を作る')]
    public function test置き場とファイル名をつないで道を作る(): void
    {
        $this->assertStringEndsWith('/KvsT.csv', Dataset::path('KvsT.csv'));
    }

    #[TestDox('無いファイルは存在しないと答える')]
    public function test無いファイルは存在しないと答える(): void
    {
        $this->assertFalse(Dataset::exists('no-such-file.csv'));
    }
}
