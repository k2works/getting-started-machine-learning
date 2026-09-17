'use strict';

import fs from 'fs';
import path from 'path';
import { marked } from 'marked';
import plantumlEncoder from 'plantuml-encoder';

/** 変換元（ユーザーマニュアルの Markdown）. */
const SRC_DIR = path.join(process.cwd(), 'docs', 'manual');
/** 変換先（静的マニュアルサイト）. */
const OUT_DIR = path.join(process.cwd(), 'apps', 'manual');
/** PlantUML レンダリングサーバ（mkdocs と同じ既定値）. */
const PLANTUML_SERVER = (
  process.env.PLANTUML_SERVER_URL || 'http://www.plantuml.com/plantuml'
).replace(/\/$/, '');
/** マニュアルのサイトタイトル（`.env` の MANUAL_TITLE で上書きする）. */
const MANUAL_TITLE = process.env.MANUAL_TITLE || 'ユーザーマニュアル';
/** フッターの著作権表示（未設定なら出力しない。`.env` の MANUAL_COPYRIGHT で指定する）. */
const MANUAL_COPYRIGHT = process.env.MANUAL_COPYRIGHT || '';
/** 上位ポータルへの戻り先（未設定ならヘッダーにリンクを出さない）. */
const MANUAL_PORTAL_URL = process.env.MANUAL_PORTAL_URL || '';

/**
 * 見出しテキストから HTML の id（アンカー）を生成する.
 *
 * マニュアル本文中の相互リンク（`#43-...` 等）と一致させるため、ドット・括弧・記号を除去し
 * 空白をハイフン、ASCII を小文字化する規則とする。
 * @param {string} text 見出しテキスト
 * @returns {string} アンカー id
 */
function slugify(text) {
  return text
    .trim()
    .replace(/[（）()【】「」、。，,：:・〜~/／]/g, '')
    .replace(/\./g, '')
    .replace(/\s+/g, '-')
    .toLowerCase();
}

/**
 * ```plantuml フェンスを PlantUML サーバの SVG 画像 img に置き換える.
 * @param {string} md Markdown 本文
 * @returns {string} 置換後の Markdown（img は生 HTML）
 */
function renderPlantuml(md) {
  return md.replace(/```plantuml\r?\n([\s\S]*?)```/g, (_match, code) => {
    const encoded = plantumlEncoder.encode(code.trim());
    return `<p class="plantuml"><img src="${PLANTUML_SERVER}/svg/${encoded}" alt="PlantUML 図" loading="lazy"></p>`;
  });
}

/**
 * 同一フォルダの `.md` リンクを `.html` に書き換える（外部相対リンク `../` は対象外）.
 * @param {string} html HTML 文字列
 * @returns {string} 書き換え後 HTML
 */
function rewriteLinks(html) {
  return html.replace(
    /href="([^"/]+)\.md(#[^"]*)?"/g,
    (_m, file, anchor) => `href="${file}.html${anchor || ''}"`,
  );
}

/**
 * 見出しに id 属性を付与する（marked 既定では付かないため後処理で注入）.
 * @param {string} html HTML 文字列
 * @returns {string} id 付与後 HTML
 */
function injectHeadingIds(html) {
  return html.replace(/<h([1-6])>([\s\S]*?)<\/h\1>/g, (_m, level, inner) => {
    const text = inner.replace(/<[^>]+>/g, '');
    return `<h${level} id="${slugify(text)}">${inner}</h${level}>`;
  });
}

/**
 * 本文 HTML をページテンプレートで包む.
 * @param {string} title ページタイトル
 * @param {string} bodyHtml 本文 HTML
 * @param {boolean} isIndex 目次ページかどうか
 * @returns {string} 完全な HTML ドキュメント
 */
function pageTemplate(title, bodyHtml, isIndex) {
  const tocLink = isIndex
    ? ''
    : '<a class="manual-nav-link" href="index.html">← マニュアル目次</a>';
  const footer = MANUAL_COPYRIGHT
    ? `&copy; ${new Date().getFullYear()} ${MANUAL_COPYRIGHT}. All rights reserved.`
    : MANUAL_TITLE;
  const portalLink = MANUAL_PORTAL_URL
    ? `\n    <a class="manual-portal" href="${MANUAL_PORTAL_URL}">ポータルへ戻る</a>`
    : '';
  return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title} | ${MANUAL_TITLE}</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <header class="manual-header">
    <a class="manual-home" href="index.html">${MANUAL_TITLE}</a>${portalLink}
  </header>
  <main class="manual-content">
    ${tocLink}
    ${bodyHtml}
    ${tocLink}
  </main>
  <footer class="manual-footer">${footer}</footer>
</body>
</html>
`;
}

/** 生成する CSS（読みやすさ重視のシンプルなスタイル）. */
const STYLE_CSS = `:root { --fg: #24292f; --muted: #57606a; --border: #d0d7de; --accent: #1a237e; --bg-soft: #f6f8fa; }
* { box-sizing: border-box; }
body { margin: 0; color: var(--fg); font-family: -apple-system, "Segoe UI", "Hiragino Kaku Gothic ProN", Meiryo, sans-serif; line-height: 1.8; background: #fff; }
.manual-header { display: flex; justify-content: space-between; align-items: center; gap: 1rem; padding: 0.75rem 1.5rem; background: var(--accent); color: #fff; position: sticky; top: 0; }
.manual-header a { color: #fff; text-decoration: none; }
.manual-home { font-weight: bold; }
.manual-portal { font-size: 0.85rem; opacity: 0.9; }
.manual-content { max-width: 900px; margin: 0 auto; padding: 2rem 1.5rem 4rem; }
.manual-nav-link { display: inline-block; margin: 0.5rem 0; color: var(--accent); text-decoration: none; font-size: 0.9rem; }
.manual-content h1 { font-size: 1.8rem; border-bottom: 2px solid var(--border); padding-bottom: 0.3rem; }
.manual-content h2 { font-size: 1.4rem; border-bottom: 1px solid var(--border); padding-bottom: 0.3rem; margin-top: 2.5rem; }
.manual-content h3 { font-size: 1.15rem; margin-top: 2rem; }
.manual-content h4 { font-size: 1rem; color: var(--muted); }
.manual-content a { color: #0969da; }
.manual-content table { border-collapse: collapse; width: 100%; margin: 1rem 0; font-size: 0.95rem; }
.manual-content th, .manual-content td { border: 1px solid var(--border); padding: 0.4rem 0.6rem; text-align: left; }
.manual-content th { background: var(--bg-soft); }
.manual-content code { background: var(--bg-soft); padding: 0.1rem 0.3rem; border-radius: 4px; font-size: 0.9em; }
.manual-content pre { background: var(--bg-soft); padding: 1rem; border-radius: 6px; overflow-x: auto; }
.manual-content pre code { background: none; padding: 0; }
.manual-content blockquote { margin: 1rem 0; padding: 0.5rem 1rem; border-left: 4px solid var(--border); background: var(--bg-soft); color: var(--muted); }
.manual-content img { max-width: 100%; height: auto; border: 1px solid var(--border); border-radius: 4px; }
.manual-content .plantuml img { border: none; }
.manual-footer { text-align: center; padding: 1.5rem; font-size: 0.8rem; color: var(--muted); border-top: 1px solid var(--border); }
`;

/**
 * docs/manual の Markdown を HTML へ変換し apps/manual へ出力する Gulp タスクを登録する.
 * @param {import('gulp').Gulp} gulp Gulp インスタンス
 */
export default function (gulp) {
  gulp.task('manual:build', (done) => {
    try {
      if (!fs.existsSync(SRC_DIR)) {
        throw new Error(`マニュアルのソースが見つかりません: ${SRC_DIR}`);
      }

      // 出力先をクリーンして再作成
      fs.rmSync(OUT_DIR, { recursive: true, force: true });
      fs.mkdirSync(OUT_DIR, { recursive: true });

      // 画像アセットをコピー
      const srcAssets = path.join(SRC_DIR, 'assets');
      if (fs.existsSync(srcAssets)) {
        const outAssets = path.join(OUT_DIR, 'assets');
        fs.mkdirSync(outAssets, { recursive: true });
        for (const file of fs.readdirSync(srcAssets)) {
          if (/\.(png|jpe?g|gif|svg)$/i.test(file)) {
            fs.copyFileSync(path.join(srcAssets, file), path.join(outAssets, file));
          }
        }
      }

      // Markdown を HTML へ変換
      const mdFiles = fs
        .readdirSync(SRC_DIR)
        .filter((f) => f.endsWith('.md'))
        .sort();

      let converted = 0;
      for (const mdFile of mdFiles) {
        const raw = fs.readFileSync(path.join(SRC_DIR, mdFile), 'utf8');
        const titleMatch = raw.match(/^#\s+(.+)$/m);
        const title = titleMatch ? titleMatch[1].trim() : path.basename(mdFile, '.md');

        const bodyHtml = injectHeadingIds(rewriteLinks(marked.parse(renderPlantuml(raw))));
        const isIndex = mdFile === 'index.md';
        const outName = path.basename(mdFile, '.md') + '.html';
        fs.writeFileSync(path.join(OUT_DIR, outName), pageTemplate(title, bodyHtml, isIndex), 'utf8');
        converted += 1;
      }

      // スタイルシートを出力
      fs.writeFileSync(path.join(OUT_DIR, 'style.css'), STYLE_CSS, 'utf8');

      console.log(`マニュアルを HTML 変換しました: ${converted} ページ → ${OUT_DIR}`);
      done();
    } catch (error) {
      done(error);
    }
  });
}
