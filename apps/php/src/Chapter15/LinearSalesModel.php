<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

use GettingStartedMl\LinearModel;

/**
 * 第 7 章の線形回帰のモデルを、興行収入のモデルの約束に合わせるアダプター。
 *
 * 第 7 章のモデルは「列名 → 値の連想配列」を受け取るので、`Movie` を第 7 章の
 * 列名（`SNS1`・`SNS2`・`actor`・`original`）に読み替えるのがこのクラスの役目である。
 */
final readonly class LinearSalesModel implements SalesModel
{
    public function __construct(private LinearModel $model)
    {
    }

    public function predict(Movie $movie): float
    {
        return $this->model->predictOne([
            'SNS1' => $movie->sns1,
            'SNS2' => $movie->sns2,
            'actor' => $movie->actor,
            'original' => (float) $movie->original,
        ]);
    }
}
