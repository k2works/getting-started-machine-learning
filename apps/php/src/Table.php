<?php

declare(strict_types=1);

namespace GettingStartedMl;

/** 列名の並びと行のリストを持つ表。 */
final readonly class Table
{
    /**
     * @param list<string>                 $columns
     * @param list<array<string, string>>  $rows
     */
    public function __construct(
        public array $columns,
        public array $rows,
    ) {
    }
}
