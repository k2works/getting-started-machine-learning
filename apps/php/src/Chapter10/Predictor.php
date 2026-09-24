<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

/**
 * 学習済みのモデル。特徴量の並びからラベルの並びを返す。
 *
 * Elixir 版は「予測する関数」をそのまま渡し、Ruby 版は predict に応えるオブジェクトなら
 * 何でもよいことにした。PHP では interface として書ける。約束を破ったクラスは
 * 実行する前に PHPStan が見つける。
 */
interface Predictor
{
    /**
     * @param list<array<string, float>> $x
     *
     * @return list<string>
     */
    public function predict(array $x): array;
}
