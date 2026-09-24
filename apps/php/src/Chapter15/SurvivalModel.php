<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

/** 乗客の特徴量から生存するかどうかを返すモデル。 */
interface SurvivalModel
{
    public function predict(Passenger $passenger): bool;
}
