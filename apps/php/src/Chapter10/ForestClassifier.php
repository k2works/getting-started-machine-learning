<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

/** 自作のランダムフォレストの分類器。 */
final readonly class ForestClassifier implements Classifier
{
    public function __construct(
        private int $nEstimators,
        private int $maxFeatures,
        private ?int $maxDepth,
        private int $seed,
    ) {
    }

    /**
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public function fit(array $x, array $t, array $columns): Predictor
    {
        return RandomForest::fit(
            $x,
            $t,
            $columns,
            $this->nEstimators,
            $this->maxFeatures,
            $this->maxDepth,
            $this->seed,
        );
    }
}
