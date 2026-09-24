<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

/**
 * HTTP の要求のうち、この章のハンドラーが見るところだけを持つ値。
 *
 * PHP の組み込みサーバーは要求を `$_SERVER` と `php://input` で渡すが、それを
 * ハンドラーの中で読むとテストからサーバーを起動しなければならなくなる。
 * 要求を値にしておけば、ハンドラーは「値を受け取って値を返す関数」になる。
 */
final readonly class Request
{
    public function __construct(
        public string $method,
        public string $path,
        public string $body,
    ) {
    }
}
