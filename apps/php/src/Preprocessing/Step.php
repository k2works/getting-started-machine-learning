<?php

declare(strict_types=1);

namespace GettingStartedMl\Preprocessing;

use GettingStartedMl\Table;

/**
 * 前処理の 1 段。
 *
 * `fit` は訓練データから変換に必要な値を学び、**学習済みの新しい前処理を返す**。
 * 自分を書き換えないので、同じ前処理の定義を別のデータに使い回せる。
 *
 * Elixir 版は `:type` を持つマップとパターンマッチで振り分けたが、PHP では
 * インターフェースと実装クラスにする。どんな前処理があるかは
 * `src/Preprocessing/` のファイル一覧を見れば分かる。
 */
interface Step
{
    /** 訓練データから変換に必要な値を学び、学習済みの前処理を返す。 */
    public function fit(Table $x): self;

    /** 学習済みの前処理でデータを変換する。学習していなければ失敗する。 */
    public function apply(Table $x): Table;
}
