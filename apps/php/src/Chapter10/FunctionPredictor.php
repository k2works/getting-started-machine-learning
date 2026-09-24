<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

use Closure;

/**
 * 予測する処理だけを包んで Predictor にする。
 *
 * 第 3 章の決定木も Rubix ML のモデルも、こちらの interface を知らないまま書かれている。
 * PHP では「あとからクラスに interface を足す」ことができないので、包む器が要る。
 * Elixir 版が無名関数をそのまま渡せたのとの差がここに出る。
 */
final readonly class FunctionPredictor implements Predictor
{
    /** @param Closure(list<array<string, float>>): list<string> $predict */
    public function __construct(private Closure $predict)
    {
    }

    /**
     * @param list<array<string, float>> $x
     *
     * @return list<string>
     */
    public function predict(array $x): array
    {
        return ($this->predict)($x);
    }
}
