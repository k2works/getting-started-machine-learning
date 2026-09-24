<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

/** 生存を予測する乗客の特徴量。年齢と乗船港は分からなくてよい。 */
final readonly class Passenger
{
    public function __construct(
        public int $pclass,
        public string $sex,
        public ?float $age,
        public int $sibSp,
        public int $parch,
        public float $fare,
        public ?string $embarked,
    ) {
    }
}
