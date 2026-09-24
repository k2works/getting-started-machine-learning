<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter03;

/** 予測するラベルを持つ葉。 */
final readonly class Leaf
{
    public function __construct(public string $label)
    {
    }
}
