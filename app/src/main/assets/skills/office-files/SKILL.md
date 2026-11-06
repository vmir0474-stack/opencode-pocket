---
name: Office Files
description: Use when creating, reading, editing, converting, or validating office documents (DOCX, XLSX, PPTX, PDF, ODT, CSV) without proprietary skills.
metadata:
  author: local
  version: "1.0"
---

# Office files with open tools

Prefer scripted, reproducible generation over manual GUI work. Always keep the
original file and write the result to a new path unless the user explicitly
asks to overwrite.

## Toolchain

Check what is available before choosing an approach:

```bash
command -v pandoc soffice pdftotext pdftoppm qpdf magick convert python3
python3 -c 'import docx, openpyxl, pptx, reportlab, pypdf, PIL; print("ok")'
```

Typical locations:

- **DOCX** — `python-docx`; convert with `pandoc` or
  `soffice --headless --convert-to pdf`.
- **XLSX** — `openpyxl`; formulas, styles, charts, freeze panes.
- **PPTX** — `python-pptx`; slides, placeholders, images, speaker notes.
- **PDF** — `reportlab` to create; `pypdf`/`qpdf` to merge, split, rotate;
  `pdftotext`/`pdftoppm` (poppler) to extract text/images.
- **Markdown/HTML → DOCX/ODT** — `pandoc`.
- **Images** — Pillow or ImageMagick (`magick`; in Debian 6 it is `convert`).

## Workflow

1. **Inspect first.** Read the source file, page/slide/sheet count, and existing
   styles. Never regenerate a complex document blindly.
2. **Choose the smallest tool.** Use `python-docx`/`openpyxl`/`python-pptx` for
   structure; `pandoc` for prose conversions; `reportlab` for new PDFs.
3. **Preserve formatting.** Reuse the source template when editing. For DOCX,
   edit the existing file rather than recreating it.
4. **Cyrillic and fonts.** Use `Noto Sans`, `DejaVu Sans`, or `Liberation`
   fonts. Embed or substitute fonts explicitly when converting.
5. **Validate.** Re-open the output with the same library, count pages/slides,
   check for empty placeholders, and render a preview if possible:

   ```bash
   python3 -c 'from docx import Document; d=Document("out.docx"); print(len(d.paragraphs))'
   pdftotext out.pdf - | head
   pdftoppm -png -r 80 -f 1 -singlefile out.pdf /tmp/preview
   ```

6. **Report** the output path, page/sheet/slide count, file size, and any
   conversion warnings.

## Rules

- Do not use the proprietary Anthropic `docx`/`pdf`/`pptx`/`xlsx` skills; use
  this open toolchain.
- Do not silently drop images, comments, tracked changes, or formulas. State
  what was preserved and what was not.
- Prefer `/tmp` for previews and keep deliverables in the user's working
  directory.
- Large files: stream or process page-by-page; do not load everything into
  memory at once on this phone.
