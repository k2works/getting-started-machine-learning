<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter03;

/**
 * 分割と左右の部分木を持つ節。
 *
 * PHP には判別共用体（代数的データ型）がないが、共用型（union type）はある。
 * 部分木の型を `Leaf|Node` と書くと、実行時にも静的解析にも「木は葉か節の
 * どちらかである」ことが伝わる。Elixir 版・Ruby 版がマップや Data で表して
 * 網羅性の検査を諦めたところが、PHP では型として書ける。
 */
final readonly class Node
{
    public function __construct(
        public Split $split,
        public Leaf|Node $left,
        public Leaf|Node $right,
    ) {
    }
}
