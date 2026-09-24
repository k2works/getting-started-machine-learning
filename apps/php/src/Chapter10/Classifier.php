<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

/** 訓練データを受け取り、学習済みのモデルを返す分類器。 */
interface Classifier
{
    /**
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public function fit(array $x, array $t, array $columns): Predictor;
}
