<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

/**
 * 置き場からモデルを読み込んで予測するアプリケーション層。HTTP を知らない。
 *
 * 置き場は `ModelStore` を実装していれば何でもよいので、テストでは偽物を渡せる。
 * 自分では状態を持たない。
 */
final readonly class Service
{
    public function __construct(private ModelStore $store)
    {
    }

    /** 映画の特徴量から興行収入を予測する。 */
    public function predictSales(Movie $movie): float
    {
        return $this->store->loadSalesModel()->predict($movie);
    }

    /** 乗客の特徴量から生存するかどうかを予測する。 */
    public function predictSurvival(Passenger $passenger): bool
    {
        return $this->store->loadSurvivalModel()->predict($passenger);
    }

    /**
     * モデルごとに、読み込めるかどうかを返す。並びは Domain::MODEL_NAMES に従う。
     *
     * @return array<string, bool>
     */
    public function health(): array
    {
        return [
            Domain::SALES_MODEL => $this->ready($this->store->loadSalesModel(...)),
            Domain::SURVIVAL_MODEL => $this->ready($this->store->loadSurvivalModel(...)),
        ];
    }

    /**
     * モデルを読み込めるかどうか。読み込めない理由がほかにあれば、そのまま投げる。
     *
     * @param callable(): object $load
     */
    private function ready(callable $load): bool
    {
        try {
            $load();

            return true;
        } catch (ModelNotFoundException) {
            return false;
        }
    }
}
