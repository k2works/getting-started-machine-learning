<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter09;

/** 正規方程式で求めた線形回帰の切片と重み。 */
final readonly class LinearModel
{
    /** @param list<float> $weights */
    public function __construct(
        public float $intercept,
        public array $weights,
    ) {
    }
}
