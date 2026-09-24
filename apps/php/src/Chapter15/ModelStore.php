<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

/**
 * 学習済みモデルの置き場の約束。
 *
 * Elixir 版の behaviour は「モジュールへの約束」だったので、置き場を
 * `{モジュール, 状態}` の組で表す必要があった。**PHP の interface は値への約束** なので、
 * 置き場はただのオブジェクトでよく、状態（ディレクトリの名前）はそのオブジェクトが持つ。
 *
 * interface が決めるのは **メソッドの名前と引数と戻り値の型** だけで、
 * 「読み込めなければ ModelNotFoundException を投げる」という取り決めは書けない
 * （テストの `StoreContract::check()` で、本物と偽物の両方に確かめる）。
 */
interface ModelStore
{
    /** 興行収入のモデルを読み込む。無ければ ModelNotFoundException を投げる。 */
    public function loadSalesModel(): SalesModel;

    /** 生存予測のモデルを読み込む。無ければ ModelNotFoundException を投げる。 */
    public function loadSurvivalModel(): SurvivalModel;
}
