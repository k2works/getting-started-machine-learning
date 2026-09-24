<?php

declare(strict_types=1);

namespace GettingStartedMl;

/**
 * 決定木の節。葉（Leaf）か分岐（Branch）のどちらかになる。
 *
 * Elixir 版はマップの鍵の有無で葉と分岐を見分けたが、PHP では型で分ける。
 * 分岐を忘れて `instanceof` を書き落とすと、PHPStan のレベル 9 が指摘してくれる。
 */
interface TreeNode
{
    /**
     * 1 件の特徴量からラベルを予測する。
     *
     * @param array<string, float> $features
     */
    public function predictOne(array $features): string;
}
