<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter13;

/**
 * 主成分分析の学習した結果。
 *
 * components は 1 行に 1 つの主成分を並べる。列の順は学習したときの列の順と同じ。
 */
final readonly class Pca
{
    /**
     * @param list<float>       $mean                   列ごとの平均
     * @param list<list<float>> $components             主成分（1 行に 1 つ）
     * @param list<float>       $explainedVariance      主成分ごとの分散（固有値）
     * @param list<float>       $explainedVarianceRatio 主成分ごとの寄与率
     */
    public function __construct(
        public array $mean,
        public array $components,
        public array $explainedVariance,
        public array $explainedVarianceRatio,
    ) {
    }
}
