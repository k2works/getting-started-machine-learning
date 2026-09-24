<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

use GettingStartedMl\Chapter03\DecisionTree;

/**
 * 第 3 章の決定木の分類器。
 *
 * DecisionTree は Predictor を知らないまま書かれているので、包んで渡す。
 */
final readonly class TreeClassifier implements Classifier
{
    public function __construct(private ?int $maxDepth)
    {
    }

    /**
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public function fit(array $x, array $t, array $columns): Predictor
    {
        $tree = DecisionTree::fit($x, $t, $columns, $this->maxDepth);

        return new FunctionPredictor($tree->predict(...));
    }
}
