<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter03;

/**
 * 決定木の分割。特徴量の値が境界以下なら左、境界より大きければ右へ進む。
 *
 * impurity は、この分割で分けたときの左右のジニ不純度の重み付き平均。
 */
final readonly class Split
{
    public function __construct(
        public string $feature,
        public float $threshold,
        public float $impurity,
    ) {
    }

    /** 特徴量がこの分割で左へ進むかを返す。 */
    public function goesLeft(float $value): bool
    {
        return $value <= $this->threshold;
    }
}
