#!/usr/bin/env python3
"""Refresh the download and community counts.

1. Sums GitHub release downloads for the repos in config.json.
2. Reads the Telegram channel member count (Bot API if TELEGRAM_BOT_TOKEN is set, otherwise the public t.me page).
3. Never shows less than the minimums in config.json and never goes down from the last saved value.
4. Writes stats.json (used by the landing page) and redraws the stat pills inside the thin banner SVGs.

Needs: pip install uharfbuzz fonttools      Test without network: PM_FORCE_STATS=15000,1000 python tools/stats/update_stats.py
"""
import json, math, os, re, sys, urllib.request
from datetime import datetime, timezone
from pathlib import Path
import uharfbuzz as hb
from fontTools.pens.svgPathPen import SVGPathPen

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CFG = json.loads((HERE / 'config.json').read_text())
ICONS = json.loads((HERE / 'icons.json').read_text())


def http(url, headers=None, timeout=25):
    req = urllib.request.Request(url, headers={'User-Agent': 'pixelmusic-stats/1.0', **(headers or {})})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode('utf-8', 'replace')


def release_downloads(repo):
    headers = {'Accept': 'application/vnd.github+json'}
    if os.environ.get('GITHUB_TOKEN'):
        headers['Authorization'] = 'Bearer ' + os.environ['GITHUB_TOKEN']
    total, page = 0, 1
    while True:
        data = json.loads(http(f'https://api.github.com/repos/{repo}/releases?per_page=100&page={page}', headers))
        for rel in data:
            total += sum(a.get('download_count', 0) for a in rel.get('assets', []))
        if len(data) < 100:
            return total
        page += 1


def parse_count(text):
    """'12 345 subscribers' -> 12345, '1.2K subscribers' -> 1200."""
    m = re.search(r'([\d][\d\s\u00a0,.]*[KkMm]?)\s*(?:subscribers|members)', text)
    if not m:
        return None
    raw = m.group(1).strip().replace('\u00a0', ' ')
    mult = 1
    if raw[-1] in 'Kk': mult, raw = 1_000, raw[:-1]
    elif raw[-1] in 'Mm': mult, raw = 1_000_000, raw[:-1]
    if mult > 1:
        return int(float(raw.replace(' ', '').replace(',', '.')) * mult)
    digits = re.sub(r'\D', '', raw)
    return int(digits) if digits else None


def telegram_members(channel):
    token = os.environ.get('TELEGRAM_BOT_TOKEN')
    if token:
        try:
            d = json.loads(http(f'https://api.telegram.org/bot{token}/getChatMemberCount?chat_id=@{channel}'))
            if d.get('ok'):
                return int(d['result'])
        except Exception as e:
            print('Telegram Bot API failed:', e)
    html = http(f'https://t.me/{channel}')
    extra = re.search(r'tgme_page_extra[^>]*>(.*?)</div>', html, re.S)
    return parse_count(extra.group(1) if extra else html)


def label(n):
    if n >= 1_000_000: return f'{n // 1_000_000}M+'
    if n >= 1000: return f'{n // 1000}k+'
    if n >= 100: return f'{n // 100 * 100}+'
    if n >= 10: return f'{n // 10 * 10}+'
    return str(n)


def floor_n(n):
    """Round down to the milestone shown on the banner (15234 -> 15000), so stats.json only changes when a milestone is crossed."""
    for step in (1_000_000, 1000, 100, 10):
        if n >= step: return n // step * step
    return n


# ---------- drawing (same shapes and fonts as the banner generator) ----------
class Font:
    def __init__(self, path):
        self.face = hb.Face(hb.Blob.from_file_path(str(path))); self.font = hb.Font(self.face); self.upem = self.face.upem; self.cache = {}

    def run(self, s):
        b = hb.Buffer(); b.add_str(s); b.guess_segment_properties(); hb.shape(self.font, b, {'kern': True, 'liga': True})
        return b.glyph_infos, b.glyph_positions

    def width(self, s, size):
        return sum(p.x_advance for p in self.run(s)[1]) * size / self.upem

    def text(self, s, x, y, size, fill):
        infos, pos = self.run(s); k = size / self.upem; out = []
        for i, p in zip(infos, pos):
            if i.codepoint not in self.cache:
                pen = SVGPathPen(None); self.font.draw_glyph_with_pen(i.codepoint, pen); self.cache[i.codepoint] = pen.getCommands()
            d = self.cache[i.codepoint]
            if d:
                out.append(f'<path transform="translate({x + p.x_offset * k:.2f} {y - p.y_offset * k:.2f}) scale({k:.5f} {-k:.5f})" d="{d}"/>')
            x += p.x_advance * k
        return f'<g fill="{fill}">{"".join(out)}</g>'


FN, FL = Font(HERE / 'gflex-xbold.ttf'), Font(HERE / 'gflex-semi.ttf')


def rr(x, y, w, h, r):
    return (f'M{x+r},{y}H{x+w-r}A{r},{r} 0 0 1 {x+w},{y+r}V{y+h-r}A{r},{r} 0 0 1 {x+w-r},{y+h}'
            f'H{x+r}A{r},{r} 0 0 1 {x},{y+h-r}V{y+r}A{r},{r} 0 0 1 {x+r},{y}Z')


def cookie(t):  # 9-lobed Material 3 Expressive style cookie
    return (1 + .055 * math.cos(9 * (t + math.pi / 2))) / 1.055


def blob(cx, cy, r, rot=0, n=96):
    P = [(cx + r * cookie(2 * math.pi * i / n) * math.cos(2 * math.pi * i / n + rot),
          cy + r * cookie(2 * math.pi * i / n) * math.sin(2 * math.pi * i / n + rot)) for i in range(n)]
    d = f'M{P[0][0]:.1f},{P[0][1]:.1f}'
    for i in range(n):
        a, b, c, e = P[i - 1], P[i], P[(i + 1) % n], P[(i + 2) % n]
        d += f'C{b[0]+(c[0]-a[0])/6:.1f},{b[1]+(c[1]-a[1])/6:.1f} {c[0]-(e[0]-b[0])/6:.1f},{c[1]-(e[1]-b[1])/6:.1f} {c[0]:.1f},{c[1]:.1f}'
    return d + 'Z'


def pill(x, y, spec, num, h=50):
    r = h / 2; w = round(h + 4 + FN.width(num, 26) + 7 + FL.width(spec['label'], 20) + 20); nx = x + h + 2
    ic = ICONS[spec['icon']]; k = (h * .42) / ic['upem']; b = ic['bounds']
    svg = (f'<path d="{rr(x, y, w, h, r)}" fill="{spec["fill"]}"/><path d="{blob(x + r, y + r, r - 6, .3)}" fill="{spec["cont"]}"/>'
           f'<path fill="{spec["cfg"]}" transform="translate({x + r:.2f} {y + r:.2f}) scale({k:.5f} {-k:.5f}) '
           f'translate({-(b[0] + b[2]) / 2:.1f} {-(b[1] + b[3]) / 2:.1f})" d="{ic["d"]}"/>'
           + FN.text(num, nx, y + r + 9.5, 26, spec['fg']) + FL.text(spec['label'], nx + FN.width(num, 26) + 7, y + r + 7.5, 20, spec['fg']))
    return svg, w


def update_banners(labels):
    x0, y0 = CFG['origin']; x = x0; body = ''
    for spec in CFG['stats']:
        s, w = pill(x, y0, spec, labels[spec['key']]); body += s; x += w + 10
    changed = []
    for rel in CFG['banners']:
        p = ROOT / rel
        if not p.exists():
            print('skip (missing):', rel); continue
        text = p.read_text(encoding='utf-8')
        new = re.sub(r'<!--stats-->.*?<!--/stats-->', lambda m: f'<!--stats-->{body}<!--/stats-->', text, flags=re.S)
        if new != text:
            p.write_text(new, encoding='utf-8'); changed.append(rel)
    return changed, x - 10


def main():
    stats_file = ROOT / 'stats.json'
    prev = json.loads(stats_file.read_text()) if stats_file.exists() else {}
    forced = os.environ.get('PM_FORCE_STATS')
    dl = com = None
    if forced:
        dl, com = (int(v) for v in forced.split(','))
    else:
        try: dl = sum(release_downloads(r) for r in CFG['release_repos']) + CFG['downloads_offset']
        except Exception as e: print('release downloads failed, keeping last value:', e)
        try: com = telegram_members(CFG['telegram_channel'])
        except Exception as e: print('Telegram count failed, keeping last value:', e)
    downloads = max(floor_n(dl or 0), CFG['downloads_min'], prev.get('downloads', 0))
    community = max(floor_n(com or 0), CFG['community_min'], prev.get('community', 0))
    moved = (downloads, community) != (prev.get('downloads'), prev.get('community'))
    today = datetime.now(timezone.utc).strftime('%Y-%m-%d')
    out = {'downloads': downloads, 'community': community, 'downloads_label': label(downloads), 'community_label': label(community),
           'updated': today if moved else prev.get('updated', today)}
    if moved or not stats_file.exists():
        stats_file.write_text(json.dumps(out, indent=2) + '\n')
    print('raw counts (not saved):', {'release_downloads': dl, 'telegram_members': com})
    changed, end_x = update_banners({'downloads': out['downloads_label'], 'community': out['community_label']})
    print(json.dumps(out)); print('stats.json changed:', moved); print('banners updated:', changed or 'none', '| pills end at x =', round(end_x), '(keep under 570)')
    if end_x > 570: print('WARNING: stat pills are getting close to the pill columns in the banner')


if __name__ == '__main__':
    main()
