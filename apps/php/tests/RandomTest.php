<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Random;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class RandomTest extends TestCase
{
    #[TestDox('java.util.Random と同じ並びを返す')]
    public function testJavaと同じ並びを返す(): void
    {
        $random = new Random(0);
        $values = [];

        for ($i = 0; $i < 5; ++$i) {
            $values[] = $random->nextInt(100);
        }

        // Java 版・Scala 版・Clojure 版・Elixir 版と同じ並び。
        $this->assertSame([60, 48, 29, 47, 15], $values);
    }

    #[TestDox('二の冪の範囲でも java.util.Random と同じ並びを返す')]
    public function test二の冪の範囲でも同じ並びを返す(): void
    {
        $random = new Random(42);
        $values = [];

        for ($i = 0; $i < 5; ++$i) {
            $values[] = $random->nextInt(16);
        }

        $this->assertSame([11, 0, 10, 0, 4], $values);
    }

    #[TestDox('範囲の中の値だけを返す')]
    public function test範囲の中の値だけを返す(): void
    {
        $random = new Random(7);

        for ($i = 0; $i < 1000; ++$i) {
            $value = $random->nextInt(10);

            $this->assertGreaterThanOrEqual(0, $value);
            $this->assertLessThan(10, $value);
        }
    }

    #[TestDox('範囲が正の数でなければ失敗する')]
    public function test範囲が正の数でなければ失敗する(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('bound は正の数でなければなりません: 0');

        (new Random(0))->nextInt(0);
    }

    #[TestDox('Fisher-Yates の並べ替えがほかの言語版と一致する')]
    public function testFisherYatesの並べ替えが一致する(): void
    {
        // Java 版・Elixir 版と同じ並び。
        $this->assertSame([4, 8, 9, 6, 3, 5, 2, 1, 7, 0], Random::shuffle(range(0, 9), 0));
    }

    #[TestDox('並べ替えは元の配列を変えない')]
    public function test並べ替えは元の配列を変えない(): void
    {
        $items = range(0, 9);
        Random::shuffle($items, 0);

        $this->assertSame(range(0, 9), $items);
    }

    #[TestDox('要素が一つ以下なら並べ替えても変わらない')]
    public function test要素が一つ以下なら変わらない(): void
    {
        $this->assertSame([], Random::shuffle([], 0));
        $this->assertSame([1], Random::shuffle([1], 0));
    }

    #[TestDox('同じシードなら同じ並びになる')]
    public function test同じシードなら同じ並びになる(): void
    {
        $this->assertSame(Random::shuffle(range(0, 20), 123), Random::shuffle(range(0, 20), 123));
    }
}
