<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter15\Domain;
use GettingStartedMl\Chapter15\ModelNotFoundException;
use GettingStartedMl\Chapter15\ModelStore;
use GettingStartedMl\Chapter15\Movie;
use GettingStartedMl\Chapter15\Passenger;
use PHPUnit\Framework\Assert;

/**
 * 置き場の約束。interface が書けない取り決めを、本物と偽物の両方に確かめる。
 *
 * PHPUnit の `Assert` のメソッドは static なので、`TestCase` を継承しない普通のクラスに
 * 約束を書ける。Elixir 版が `ExUnit.Assertions` を import したのと同じ形である。
 */
final class StoreContract
{
    /** 予測に使う架空の映画。 */
    public static function movie(): Movie
    {
        return new Movie(200.0, 500.0, 3000.0, 1);
    }

    /** 予測に使う架空の乗客（1 等客室の女性、年齢は分からない）。 */
    public static function passenger(): Passenger
    {
        return new Passenger(1, 'female', null, 0, 0, 50.0, null);
    }

    /**
     * 置き場の約束を確かめる。
     *
     * @param ModelStore $withModels    モデルを 2 つとも読み込める置き場
     * @param ModelStore $withoutModels どちらも読み込めない置き場
     */
    public static function check(ModelStore $withModels, ModelStore $withoutModels): void
    {
        // 約束: モデルがあれば予測するモデルを返し、同じ入力には同じ答えを返す。
        // 「float を返す」「bool を返す」は interface が型で約束しているので、
        // ここで書くと PHPStan に「常に真である」と言われる。型で守れるものは書かない。
        Assert::assertTrue(is_finite($withModels->loadSalesModel()->predict(self::movie())));
        Assert::assertSame(
            $withModels->loadSalesModel()->predict(self::movie()),
            $withModels->loadSalesModel()->predict(self::movie()),
        );
        Assert::assertSame(
            $withModels->loadSurvivalModel()->predict(self::passenger()),
            $withModels->loadSurvivalModel()->predict(self::passenger()),
        );

        // 約束: モデルが無ければ ModelNotFoundException を投げる
        $loads = [
            Domain::SALES_MODEL => static fn (): mixed => $withoutModels->loadSalesModel(),
            Domain::SURVIVAL_MODEL => static fn (): mixed => $withoutModels->loadSurvivalModel(),
        ];

        foreach ($loads as $model => $load) {
            try {
                $load();
                Assert::fail("モデルが無いのに読み込めました: {$model}");
            } catch (ModelNotFoundException $e) {
                Assert::assertSame($model, $e->model);
                Assert::assertSame("学習済みモデル {$model} が見つかりません", $e->getMessage());
            }
        }
    }
}
