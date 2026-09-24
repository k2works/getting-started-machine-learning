<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

use Throwable;

/**
 * 予測 API のプレゼンテーション層。
 *
 * ハンドラーは `Request` を受け取って `Response` を返す **ただのメソッド** である。
 * フレームワークもルーターも使わず、メソッドとパスの組を `match` で振り分ける。
 * テストはこのメソッドを直に呼べばよく、サーバーを起動しなくてよい。
 */
final readonly class Api
{
    /** パスごとに許しているメソッド。知っているパスに違うメソッドが来たら 405 にする。 */
    private const array ALLOWED_METHODS = [
        '/health' => 'GET',
        '/cinema/sales' => 'POST',
        '/survived' => 'POST',
    ];

    public function __construct(private Service $service)
    {
    }

    /** 要求を処理して応答を返す。 */
    public function handle(Request $request): Response
    {
        return match ("{$request->method} {$request->path}") {
            'GET /health' => $this->health(),
            'POST /cinema/sales' => $this->predict(fn (): array => [
                'sales' => $this->service->predictSales(
                    Validation::movie(Validation::readJson($request->body, Validation::MOVIE_TYPES)),
                ),
            ]),
            'POST /survived' => $this->predict(fn (): array => [
                'survived' => $this->service->predictSurvival(
                    Validation::passenger(Validation::readJson($request->body, Validation::PASSENGER_TYPES)),
                ),
            ]),
            default => $this->unmatched($request),
        };
    }

    private function health(): Response
    {
        $models = $this->service->health();
        $status = in_array(false, $models, true) ? 'degraded' : 'ok';

        return Response::json(200, ['status' => $status, 'models' => $models]);
    }

    /**
     * 要求を読んで検証し、予測する。失敗はステータスコードに変える。
     *
     * @param callable(): array<string, mixed> $run
     */
    private function predict(callable $run): Response
    {
        try {
            return Response::json(200, $run());
        } catch (ValidationException $e) {
            return Response::json(422, ['detail' => $e->reasons]);
        } catch (ModelNotFoundException $e) {
            return Response::json(503, ['detail' => $e->getMessage()]);
        } catch (Throwable) {
            // 例外のメッセージには内部の事情が入るので、応答には出さない
            return Response::json(500, ['detail' => '予測できませんでした']);
        }
    }

    private function unmatched(Request $request): Response
    {
        $allow = self::ALLOWED_METHODS[$request->path] ?? null;

        return $allow === null
            ? Response::json(404, ['detail' => '見つかりません'])
            : Response::json(405, ['detail' => '許していないメソッドです'], ['Allow' => $allow]);
    }
}
