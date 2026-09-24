<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

/** HTTP の応答を表す値。組み込みサーバーに送るのは呼ぶ側の仕事。 */
final readonly class Response
{
    /** @param array<string, string> $headers */
    public function __construct(
        public int $status,
        public array $headers,
        public string $body,
    ) {
    }

    /**
     * JSON の応答を作る。日本語とスラッシュはそのまま書く。
     *
     * @param array<string, mixed> $body
     * @param array<string, string> $headers
     */
    public static function json(int $status, array $body, array $headers = []): self
    {
        return new self(
            $status,
            ['Content-Type' => 'application/json; charset=utf-8', ...$headers],
            json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
        );
    }
}
