<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter14;

/** K-means が落ち着いたときのクラスタ番号・中心・誤差平方和。 */
final readonly class KMeansResult
{
    /**
     * @param list<int>         $labels  点ごとのクラスタ番号
     * @param list<list<float>> $centers クラスタごとの中心
     * @param float             $sse     誤差平方和
     */
    public function __construct(
        public array $labels,
        public array $centers,
        public float $sse,
    ) {
    }
}
