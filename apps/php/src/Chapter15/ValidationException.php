<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

use InvalidArgumentException;

/**
 * 要求が受け入れられない理由を並べて持つ例外。
 *
 * Elixir 版は「値と理由のマップ」を返したが、PHP には例外があるので、
 * 理由が 1 つでもあれば投げることにする。呼ぶ側は成功の値だけを受け取れる。
 */
final class ValidationException extends InvalidArgumentException
{
    /** @param list<string> $reasons 受け入れられない理由 */
    public function __construct(public readonly array $reasons)
    {
        parent::__construct(implode('、', $reasons));
    }
}
