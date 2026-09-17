---
name: creating-slides
description: Marp Markdown スライドデッキの作成・拡張・再生成を支援。PlantUML 図表、AI 画像バリアント、漫画画像バリアント、PPTX/HTML 出力に対応。`tmp/k2works-wiki/スライド/YYYYMMDD` などの日本語技術スライドフォルダの構築・更新、コードサンプル追加、ビジュアル要約スライド追加、`スライド.images.md` / `スライド.images.ai.md` / `スライド.images.ai.manga.md` の生成、Marp HTML/PPTX デッキの再生成時に使用。
---

# Creating Slides

## Core Workflow

1. Read the target slide folder and the reference slide Markdown first.
   - Check `スライド.md`, `スライド.images.md`, `スライド.images.ai.md`, and any manga variant if present.
   - Use `rg` for headings, image references, code sample lists, and reference links.
2. Gather source material before editing.
   - Prefer `docs/article` for conceptual content.
   - Use `apps` or language-specific app folders for concrete code samples.
   - Browse only when the user requests current external references or a live website.
3. Edit `スライド.md` as the canonical source.
   - Keep Japanese prose concise.
   - Keep Japanese and ASCII terms separated by a half-width space.
   - Add code slides near related language groups.
   - Update structure, comparison tables, and references when adding samples.
4. Regenerate derived Markdown and outputs.
   - Run `scripts/slide_tools.py build <slide-dir>`.
   - For AI image variants, ensure generated Markdown references `images-ai/diagram-ai-NN.png`.
   - For manga variants, ensure generated Markdown references `images-manga/*.png`.
5. Verify outputs.
   - Count PPTX slides with `unzip -Z1 '<deck>.pptx' 'ppt/slides/slide*.xml' | wc -l`.
   - Open rendered HTML with Playwright when visual layout matters.
   - Check representative slide screenshots, especially image-heavy and code-heavy slides.
   - Remove temporary `render-check-*.png` files before finishing.

## Commands

Use the bundled script from this skill directory:

```bash
python ~/.codex/skills/creating-slides/scripts/slide_tools.py build tmp/k2works-wiki/スライド/20260602
```

Useful subcommands:

```bash
python ~/.codex/skills/creating-slides/scripts/slide_tools.py extract <slide-dir>
python ~/.codex/skills/creating-slides/scripts/slide_tools.py make-ai <slide-dir>
python ~/.codex/skills/creating-slides/scripts/slide_tools.py make-manga <slide-dir>
python ~/.codex/skills/creating-slides/scripts/slide_tools.py marp <slide-dir>
python ~/.codex/skills/creating-slides/scripts/slide_tools.py count <slide-dir>
python ~/.codex/skills/creating-slides/scripts/slide_tools.py split-summary <slide-dir> images-manga/summary-manga.png
```

## Image Variants

Use the `imagegen` skill when creating new AI or manga bitmap assets.

Project-bound generated images must be copied into the slide folder, normally:

- `images-ai/diagram-ai-NN.png` for AI diagram replacements.
- `images-manga/manga-*.png` for manga story panels.
- `images-manga/summary-manga.png` and split files for tall summary comics.

For tall manga summaries, do not place the full vertical image on one 16:9 slide. Split it into section images and use multiple slides so text remains readable:

```bash
python ~/.codex/skills/creating-slides/scripts/slide_tools.py split-summary <slide-dir> images-manga/summary-manga.png --parts 5
```

Then replace a single summary image slide with numbered slides that reference the split files.

## Manga Markdown Pattern

When converting AI Markdown to a manga variant, preserve prose and replace only diagram image references with `figure.diagram` blocks:

```html
<figure class="diagram wide">
  <img src="images-manga/manga-01-overview.png" alt="...">
</figure>
```

Read `references/manga-css.md` when a manga variant needs CSS or layout tuning.

## Validation Notes

- Marp PPTX generation needs `--allow-local-files` when local images are referenced.
- `npx --yes @marp-team/marp-cli` is the expected renderer unless the project has a local renderer.
- PlantUML must be available for diagram rendering; if missing, report that diagram PNG generation could not be completed.
- If HTML keyboard navigation jumps unexpectedly during Playwright checks, inspect the DOM for `section` elements and image sizes directly.
