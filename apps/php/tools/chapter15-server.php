<?php

declare(strict_types=1);

/**
 * 第 15 章の予測 API を、PHP の組み込みサーバーで公開するスクリプト。
 *
 *     php -S 127.0.0.1:8015 tools/chapter15-server.php
 *
 * 組み込みサーバーは要求ごとにこのスクリプトを最初から走らせる。プロセスに状態が
 * 残らないので、置き場は要求ごとにファイルからモデルを読み直すことになる。
 */

require __DIR__ . '/../vendor/autoload.php';

use GettingStartedMl\Chapter15;
use GettingStartedMl\Chapter15\Api;
use GettingStartedMl\Chapter15\FileStore;
use GettingStartedMl\Chapter15\Request;
use GettingStartedMl\Chapter15\Service;

$modelDir = getenv('ML_MODEL_DIR');
$store = new FileStore($modelDir === false || $modelDir === '' ? Chapter15::MODEL_DIR : $modelDir);

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$uri = $_SERVER['REQUEST_URI'] ?? '/';
$path = parse_url(is_string($uri) ? $uri : '/', PHP_URL_PATH);
$body = file_get_contents('php://input');

$response = (new Api(new Service($store)))->handle(new Request(
    is_string($method) ? $method : 'GET',
    is_string($path) ? $path : '/',
    $body === false ? '' : $body,
));

http_response_code($response->status);

foreach ($response->headers as $name => $value) {
    header("{$name}: {$value}");
}

echo $response->body;
