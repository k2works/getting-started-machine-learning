<?php

declare(strict_types=1);

namespace GettingStartedMl;

use InvalidArgumentException;

/**
 * 乱数生成器を自作する。
 *
 * PHP の mt_rand はほかの言語版と並びが合わない。そこで java.util.Random と同じ
 * 48 ビットの線形合同法をそのまま書く。こうすると訓練データとテストデータの分割が
 * Java 版・Scala 版・Clojure 版・Elixir 版と一致するので、章をまたいで
 * 数値を突き合わせられる。
 *
 * PHP の整数は 64 ビットなので、48 ビットの状態をそのまま扱える。
 */
final class Random
{
    private const int MASK = 0xFFFFFFFFFFFF;
    private const int MULTIPLIER = 0x5DEECE66D;
    private const int INCREMENT = 0xB;

    private int $state;

    public function __construct(int $seed)
    {
        // java.util.Random の setSeed と同じ。
        $this->state = ($seed ^ self::MULTIPLIER) & self::MASK;
    }

    /**
     * 0 以上 bound 未満の整数を 1 つ返す。
     *
     * bound が 2 の冪のときだけ別の式を使うところまで java.util.Random に合わせる。
     */
    public function nextInt(int $bound): int
    {
        if ($bound <= 0) {
            throw new InvalidArgumentException("bound は正の数でなければなりません: {$bound}");
        }

        if (($bound & -$bound) === $bound) {
            return ($bound * $this->nextBits(31)) >> 31;
        }

        // 剰余の偏りを避けるため、範囲をはみ出す値は捨てて引き直す。
        do {
            $bits = $this->nextBits(31);
            $value = $bits % $bound;
        } while ($bits - $value + ($bound - 1) >= 0x80000000);

        return $value;
    }

    /**
     * Fisher-Yates で並べ替える。元の配列は変えない。
     *
     * 後ろから順に、まだ選んでいない範囲から 1 つ選んで交換する。
     *
     * @template T
     *
     * @param list<T> $items
     *
     * @return list<T>
     */
    public static function shuffle(array $items, int $seed): array
    {
        $random = new self($seed);

        for ($i = count($items) - 1; $i >= 1; --$i) {
            $j = $random->nextInt($i + 1);
            [$items[$i], $items[$j]] = [$items[$j], $items[$i]];
        }

        return array_values($items);
    }

    /** 上位ビットだけを使う。48 ビットの状態から欲しいビット数を取り出す。 */
    private function nextBits(int $bits): int
    {
        $this->state = self::advance($this->state);

        return $this->state >> (48 - $bits);
    }

    /**
     * 48 ビットの状態を 1 つ進める。
     *
     * state（48 ビット）に MULTIPLIER（35 ビット）を素直に掛けると 83 ビットになり、
     * PHP では 64 ビットを超えた整数が float に化けて精度を失う（Java のように
     * 折り返さない）。そこで state を上下 24 ビットに分け、それぞれ 59 ビットに
     * 収まる掛け算にしてから 48 ビットで足し合わせる。
     */
    private static function advance(int $state): int
    {
        $high = $state >> 24;
        $low = $state & 0xFFFFFF;

        $result = ($high * self::MULTIPLIER) & 0xFFFFFF;
        $result = ($result << 24) + $low * self::MULTIPLIER + self::INCREMENT;

        return $result & self::MASK;
    }
}
