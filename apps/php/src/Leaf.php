<?php

declare(strict_types=1);

namespace GettingStartedMl;

/** 決定木の葉。これ以上分けずに、1 つのラベルを答える。 */
final readonly class Leaf implements TreeNode
{
    public function __construct(public string $label)
    {
    }

    /** @param array<string, float> $features */
    public function predictOne(array $features): string
    {
        return $this->label;
    }
}
