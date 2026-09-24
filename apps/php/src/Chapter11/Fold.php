<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter11;

/**
 * K 分割交差検証の 1 回分の分け方。行そのものではなく、行の位置だけを持つ。
 *
 * 位置で持つと、同じ分け方を特徴量にも正解ラベルにも使い回せる。
 */
final readonly class Fold
{
    /**
     * @param list<int> $train 訓練データにする行の位置
     * @param list<int> $test  テストデータにする行の位置
     */
    public function __construct(
        public array $train,
        public array $test,
    ) {
    }
}
