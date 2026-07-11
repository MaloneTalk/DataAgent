/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

const REPORT_CSS = `
* { box-sizing: border-box; }
body {
  margin: 0; padding: 20px;
  background-color: #f3f4f6;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  color: #374151; line-height: 1.6;
}
.container {
  max-width: 900px; margin: 0 auto;
  background-color: #fff; padding: 40px;
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06);
}
h1 { font-size: 2.25rem; font-weight: 800; color: #1e3a8a; margin-top: 0; margin-bottom: 1.5rem; border-bottom: 2px solid #e5e7eb; padding-bottom: 0.5rem; }
h2 { font-size: 1.5rem; font-weight: 700; color: #2563eb; margin-top: 2.5rem; margin-bottom: 1rem; border-left: 5px solid #2563eb; padding-left: 12px; }
h3 { font-size: 1.25rem; font-weight: 600; color: #1f2937; margin-top: 1.5rem; margin-bottom: 0.75rem; }
p { margin-bottom: 1rem; }
ul, ol { margin-bottom: 1rem; padding-left: 1.5rem; }
li { margin-bottom: 0.25rem; }
code {
  background-color: #f1f5f9; padding: 0.2rem 0.4rem;
  border-radius: 0.25rem; font-size: 0.875em; color: #d946ef; font-family: monospace;
}
pre {
  background: #1e293b; color: #f8fafc; padding: 1rem;
  border-radius: 0.5rem; overflow-x: auto;
}
pre code { background: transparent; color: inherit; padding: 0; }
.chart-box {
  width: 100%; height: 450px; margin: 30px 0;
  border: 1px solid #e2e8f0; border-radius: 8px;
  background-color: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.chart-box img { max-width: 100%; max-height: 100%; }
.chart-error {
  display: flex; align-items: center; justify-content: center;
  height: 100%; color: #ef4444; background-color: #fef2f2;
  border: 1px dashed #ef4444; border-radius: 8px;
}
blockquote { border-left: 3px solid #2563eb; padding-left: 12px; margin: 8px 0; color: #6b7280; }
table { border-collapse: collapse; width: 100%; margin: 8px 0; }
th, td { border: 1px solid #e5e7eb; padding: 6px 12px; text-align: left; }
th { background: #f3f4f6; font-weight: 600; }
hr { border: none; border-top: 1px solid #e5e7eb; margin: 12px 0; }
`;

export function buildExportHtml(
  title: string,
  renderedHtml: string,
  chartImages: Map<string, string>,
): string {
  let html = renderedHtml;

  for (const [chartId, dataUrl] of chartImages) {
    const escapedId = chartId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    html = html.replace(
      new RegExp(`<div[^>]*id="${escapedId}"[^>]*class="chart-box"[^>]*>.*?</div>`, 's'),
      `<div class="chart-box"><img src="${dataUrl}" alt="图表" /></div>`,
    );
  }

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${escapeHtml(title)}</title>
<style>${REPORT_CSS}</style>
</head>
<body>
<div class="container">
<div class="markdown-body">${html}</div>
</div>
</body>
</html>`;
}

export function downloadHtml(filename: string, html: string) {
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
