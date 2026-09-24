<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter09;

/**
 * ボストンの住宅価格を訓練データとテストデータに分けた結果。
 *
 * 第 2 章の分割は連想配列で返したが、この章は列の並びも持ち回るので
 * readonly class にする。PHPStan の配列の形（array{...}）を関数のあいだで
 * 引き回すより、名前の付いた型のほうが読みやすい。
 */
final readonly class BostonSplit
{
    /**
     * @param list<string>               $columns
     * @param list<array<string, float>> $xTrain
     * @param list<array<string, float>> $xTest
     * @param list<float>                $tTrain
     * @param list<float>                $tTest
     */
    public function __construct(
        public array $columns,
        public array $xTrain,
        public array $xTest,
        public array $tTrain,
        public array $tTest,
    ) {
    }
}
