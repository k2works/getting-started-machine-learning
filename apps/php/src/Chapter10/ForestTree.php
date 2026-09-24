<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

use GettingStartedMl\Chapter03\DecisionTree;

/**
 * 森の中の 1 本。学習に使った列と行番号を覚えておく。
 *
 * 特徴量の重要度を求めるときに、その木が見た標本をもう一度作るために要る。
 */
final readonly class ForestTree
{
    /**
     * @param list<string> $columns
     * @param list<int>    $rows
     */
    public function __construct(
        public array $columns,
        public array $rows,
        public DecisionTree $tree,
    ) {
    }
}
