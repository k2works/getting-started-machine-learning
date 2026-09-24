<?php

declare(strict_types=1);

namespace GettingStartedMl\Preprocessing;

use GettingStartedMl\Chapter02;
use GettingStartedMl\Table;
use InvalidArgumentException;

/**
 * 最頻値で欠損値を補完する。
 *
 * 同数のときは **値の順で前のもの** を選ぶ。ほかの言語版と同じ決め方にしないと、
 * 深い木で予測が食い違う。Rubix ML の `KMostFrequent` は `arsort` を使うので、
 * 同数のときは「先に現れたもの」になり、決め方が違う（第 8 章 8.12 節）。
 */
final readonly class MostFrequentImputer implements Step
{
    public function __construct(
        public string $column,
        public ?string $mostFrequent = null,
    ) {
    }

    public function fit(Table $x): self
    {
        $counts = [];

        foreach ($x->rows as $row) {
            if (!Chapter02::isMissing($row, $this->column)) {
                $value = Chapter02::text($row, $this->column);
                $counts[$value] = ($counts[$value] ?? 0) + 1;
            }
        }

        if ($counts === []) {
            throw new InvalidArgumentException("値がすべて空欄です: {$this->column}");
        }

        // 値の順に並べてから、厳密な不等号で畳む。同数なら値の順で前のものが残る。
        ksort($counts, SORT_STRING);
        $best = array_key_first($counts);

        foreach ($counts as $value => $count) {
            if ($count > $counts[$best]) {
                $best = $value;
            }
        }

        return new self($this->column, (string) $best);
    }

    public function apply(Table $x): Table
    {
        if ($this->mostFrequent === null) {
            throw new InvalidArgumentException("学習していません: {$this->column}");
        }

        $value = $this->mostFrequent;

        $rows = array_map(
            function (array $row) use ($value): array {
                if (Chapter02::isMissing($row, $this->column)) {
                    $row[$this->column] = $value;
                }

                return $row;
            },
            $x->rows,
        );

        return new Table($x->columns, $rows);
    }
}
