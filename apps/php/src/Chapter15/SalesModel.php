<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

/** 映画の特徴量から興行収入を返すモデル。 */
interface SalesModel
{
    public function predict(Movie $movie): float;
}
