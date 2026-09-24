<?php

declare(strict_types=1);

namespace GettingStartedMl;

use InvalidArgumentException;

/** 決定木の分岐。1 つの列を境界と比べて、左右どちらかへ進む。 */
final readonly class Branch implements TreeNode
{
    public function __construct(
        public string $feature,
        public float $threshold,
        public TreeNode $left,
        public TreeNode $right,
    ) {
    }

    /** @param array<string, float> $features */
    public function predictOne(array $features): string
    {
        return $this->goesLeft($features)
            ? $this->left->predictOne($features)
            : $this->right->predictOne($features);
    }

    /**
     * 境界以下なら左へ進む。
     *
     * @param array<string, float> $features
     */
    public function goesLeft(array $features): bool
    {
        if (!array_key_exists($this->feature, $features)) {
            throw new InvalidArgumentException("特徴量がありません: {$this->feature}");
        }

        return $features[$this->feature] <= $this->threshold;
    }
}
