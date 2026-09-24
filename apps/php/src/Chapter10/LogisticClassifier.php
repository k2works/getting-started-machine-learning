<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

/** 自作のロジスティック回帰の分類器。 */
final readonly class LogisticClassifier implements Classifier
{
    public function __construct(
        private float $learningRate = LogisticRegression::DEFAULT_LEARNING_RATE,
        private int $epochs = LogisticRegression::DEFAULT_EPOCHS,
    ) {
    }

    /**
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public function fit(array $x, array $t, array $columns): Predictor
    {
        return LogisticRegression::fit($x, $t, $columns, $this->learningRate, $this->epochs);
    }
}
