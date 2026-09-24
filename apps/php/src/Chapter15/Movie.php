<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

/** 興行収入を予測する映画の特徴量。HTTP にも保存の形式にも依存しない。 */
final readonly class Movie
{
    public function __construct(
        public float $sns1,
        public float $sns2,
        public float $actor,
        public int $original,
    ) {
    }
}
