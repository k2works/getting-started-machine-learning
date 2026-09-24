<?php

declare(strict_types=1);

// PHPUnit には最低カバレッジのしきい値の機能が無いので、clover の XML を読んで自分で判定する（ADR 013）。
//
// しきい値は「学習データが無くても保てる水準」にする。配布データのある手元では
// 99% 前後になるが、データの無い CI では実データのテストがスキップされて 78% 前後まで
// 落ちる。高いほうに合わせると CI が必ず落ちるので、低いほうを基準にする。
//
// 使い方: php tools/coverage-threshold.php build/clover.xml 75

$path = $argv[1] ?? 'build/clover.xml';
$threshold = (float) ($argv[2] ?? '90');

if (!is_file($path)) {
    fwrite(STDERR, "カバレッジの結果がありません: {$path}\n");
    exit(1);
}

$xml = simplexml_load_file($path);

if ($xml === false || !isset($xml->project->metrics)) {
    fwrite(STDERR, "カバレッジの結果を読めません: {$path}\n");
    exit(1);
}

$metrics = $xml->project->metrics;
$statements = (int) $metrics['statements'];
$covered = (int) $metrics['coveredstatements'];

if ($statements === 0) {
    fwrite(STDERR, "実行できる行がありません: {$path}\n");
    exit(1);
}

$rate = 100.0 * $covered / $statements;

printf("行カバレッジ: %.2f%% (%d/%d), しきい値: %.2f%%\n", $rate, $covered, $statements, $threshold);

if ($rate < $threshold) {
    fwrite(STDERR, sprintf("カバレッジがしきい値を下回りました: %.2f%% < %.2f%%\n", $rate, $threshold));
    exit(3);
}
