<?php

declare(strict_types=1);

namespace GettingStartedMl;

use GettingStartedMl\Preprocessing\Step;

/** 学習済みの前処理の並びと、その後に置かれた決定木。 */
final readonly class FittedPipeline
{
    /**
     * @param list<Step>   $steps   学習済みの前処理（適用する順）
     * @param list<string> $columns 前処理のあとの列の並び
     */
    public function __construct(
        public array $steps,
        public array $columns,
        public TreeNode $tree,
    ) {
    }
}
