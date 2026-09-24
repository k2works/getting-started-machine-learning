<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

use RuntimeException;

/**
 * 学習済みモデルが無いことを表す例外。
 *
 * この章のほかの失敗は `InvalidArgumentException` のままだが、これだけは別の型にする。
 * API が 503 に変えるために、ほかの失敗と見分けられなければならないからである。
 * メッセージにファイルのパスを含めないのは、503 の応答としてそのまま外に出るため。
 */
final class ModelNotFoundException extends RuntimeException
{
    public function __construct(public readonly string $model)
    {
        parent::__construct("学習済みモデル {$model} が見つかりません");
    }
}
