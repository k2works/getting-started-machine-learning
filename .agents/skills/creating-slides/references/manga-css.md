# Manga Slide CSS

Use this CSS pattern in Marp decks that mix text slides and manga images:

```css
section img {
  display: block;
  max-width: 100%;
  height: auto;
}
section .diagram {
  margin: 10px auto 14px;
  text-align: center;
}
section .diagram img {
  width: 100%;
  max-width: 980px;
  max-height: 420px;
  object-fit: contain;
  margin: 0 auto;
}
section .diagram.wide img {
  max-width: 1040px;
  max-height: 470px;
}
section .diagram.compact img {
  max-width: 760px;
  max-height: 320px;
}
section.manga-summary {
  padding: 24px 60px;
}
section.manga-summary h3 {
  margin: 0 0 0.2em;
}
section.manga-summary img {
  max-width: 100%;
  max-height: 600px;
  display: block;
  margin: 0 auto;
  object-fit: contain;
}
```

For tall summary comics, prefer 4-6 cropped slides over one full-height image. Verify each crop renders at roughly 800 px or wider on a 1280 px viewport.
