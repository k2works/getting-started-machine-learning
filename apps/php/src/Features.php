<?php

declare(strict_types=1);

namespace GettingStartedMl;

/** 判定の手がかりになる特徴量。正解ラベルを持たない。 */
final readonly class Features
{
    public function __construct(
        public int $height,
        public int $weight,
        public int $ageGroup,
    ) {
    }
}
