<?php

declare(strict_types=1);

namespace GettingStartedMl;

/**
 * 学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。
 *
 * readonly class にすると、作ったあとに値を書き換えられない。
 * Ruby 版の Data.define にあたる。
 */
final readonly class Person
{
    public function __construct(
        public int $height,
        public int $weight,
        public int $ageGroup,
        public string $faction,
    ) {
    }
}
