<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter12;

/** 正則化の強さを 1 つ試した結果。 */
final readonly class Experiment
{
    public function __construct(
        /** 試した正則化の強さ。 */
        public float $alpha,
        /** 訓練データの決定係数。 */
        public float $trainScore,
        /** 検証データの決定係数。 */
        public float $validationScore,
        /** 係数の絶対値の合計。正則化が強いほど小さくなる。 */
        public float $coefficientAbsSum,
    ) {
    }
}
