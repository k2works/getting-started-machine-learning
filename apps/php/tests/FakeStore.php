<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter15\Domain;
use GettingStartedMl\Chapter15\ModelNotFoundException;
use GettingStartedMl\Chapter15\ModelStore;
use GettingStartedMl\Chapter15\Movie;
use GettingStartedMl\Chapter15\Passenger;
use GettingStartedMl\Chapter15\SalesModel;
use GettingStartedMl\Chapter15\SurvivalModel;

/**
 * 第 15 章のテストで使う偽物の置き場。実データも学習も使わずに API とサービスを確かめる。
 *
 * Elixir 版は behaviour がモジュールへの約束なので偽物もモジュールになったが、
 * PHP の interface は値（オブジェクト）への約束なので、偽物もただのクラスでよい。
 */
final readonly class FakeStore implements ModelStore
{
    /** 偽物の置き場が返す興行収入。 */
    public const float FIXED_SALES = 4321.5;

    public function __construct(
        private bool $hasSales = true,
        private bool $hasSurvival = true,
    ) {
    }

    public function loadSalesModel(): SalesModel
    {
        if (!$this->hasSales) {
            throw new ModelNotFoundException(Domain::SALES_MODEL);
        }

        return new class () implements SalesModel {
            public function predict(Movie $movie): float
            {
                return FakeStore::FIXED_SALES;
            }
        };
    }

    public function loadSurvivalModel(): SurvivalModel
    {
        if (!$this->hasSurvival) {
            throw new ModelNotFoundException(Domain::SURVIVAL_MODEL);
        }

        return new class () implements SurvivalModel {
            public function predict(Passenger $passenger): bool
            {
                return true;
            }
        };
    }
}
