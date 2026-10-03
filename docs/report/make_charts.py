"""Inline SVG charts for the status report.

Written as SVG with CSS custom properties for every colour so the charts follow
the page's theme rather than carrying a baked-in light palette.
"""
import os
OUT = os.environ.get("CHART_OUT", "docs/report/charts")
os.makedirs(OUT, exist_ok=True)

HEAD = ('<svg viewBox="0 0 {w} {h}" width="100%" role="img" '
        'aria-label="{alt}" font-family="IBM Plex Sans, system-ui, sans-serif">')
GRID = 'var(--viz-grid)'
TXT  = 'var(--viz-text)'
MUT  = 'var(--viz-muted)'

def txt(x, y, s, size=11, fill=MUT, anchor="start", weight="400", mono=False):
    fam = ' font-family="IBM Plex Mono, monospace"' if mono else ''
    return (f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" fill="{fill}" '
            f'text-anchor="{anchor}" font-weight="{weight}"{fam}>{s}</text>')

# ---------------------------------------------------------------- chart A
# The reference guitar is flat on every note by the same amount. One series.
notes = [("E2",-42.9),("F♯2",-37.9),("G2",-40.8),("G♯2",-30.9),
         ("A2",-35.8),("A♯2",-36.7),("B2",-30.3)]
w,h = 560,230
L,R,T,B = 44,16,22,42
pw, ph = w-L-R, h-T-B
lo,hi = -50.0, 0.0
def ay(v): return T + (v-hi)/(lo-hi)*ph
med = sorted(v for _,v in notes)[len(notes)//2]
s = [HEAD.format(w=w,h=h,alt="Every note on the reference guitar is flat by about the same amount")]
for v in (0,-10,-20,-30,-40,-50):
    y = ay(v)
    s.append(f'<line x1="{L}" y1="{y:.1f}" x2="{w-R}" y2="{y:.1f}" stroke="{GRID}" stroke-width="1"/>')
    s.append(txt(L-8, y+3.5, f"{v}", 10, MUT, "end", mono=True))
s.append(f'<line x1="{L}" y1="{ay(med):.1f}" x2="{w-R}" y2="{ay(med):.1f}" '
         f'stroke="var(--viz-s1)" stroke-width="2" stroke-dasharray="5 4"/>')
s.append(txt(w-R, ay(med)-8, f"median {med:.1f} cents", 10.5, "var(--viz-s1)", "end", "500", mono=True))
step = pw/len(notes)
for i,(n,v) in enumerate(notes):
    cx = L + step*(i+0.5)
    s.append(f'<line x1="{cx:.1f}" y1="{ay(0):.1f}" x2="{cx:.1f}" y2="{ay(v):.1f}" stroke="{GRID}" stroke-width="1"/>')
    s.append(f'<circle cx="{cx:.1f}" cy="{ay(v):.1f}" r="5" fill="var(--viz-s1)" '
             f'stroke="var(--viz-surface)" stroke-width="2"/>')
    s.append(txt(cx, h-24, n, 11, TXT, "middle", "500"))
    s.append(txt(cx, h-11, f"{v:.1f}", 9.5, MUT, "middle", mono=True))
s.append(txt(L-8, T-8, "cents", 10, MUT, "end"))
s.append("</svg>")
open(f"{OUT}/cents.svg","w").write("\n".join(s))

# ---------------------------------------------------------------- chart B
# Same tone, two audio sources, against the threshold that was in the code.
w,h = 560,180
L,R,T,B = 118,54,26,34
pw, ph = w-L-R, h-T-B
lo,hi = -70.0, 0.0
def bx(v): return L + (v-lo)/(hi-lo)*pw
rows = [("Through MIC", -25.9, "var(--viz-s1)"),
        ("Through UNPROCESSED", -59.4, "var(--viz-s2)")]
s = [HEAD.format(w=w,h=h,alt="The same tone measured 33 dB quieter through the audio source the app prefers")]
for v in (0,-10,-20,-30,-40,-50,-60,-70):
    x = bx(v)
    s.append(f'<line x1="{x:.1f}" y1="{T-6}" x2="{x:.1f}" y2="{T+ph+4}" stroke="{GRID}" stroke-width="1"/>')
    s.append(txt(x, T+ph+18, f"{v}", 9.5, MUT, "middle", mono=True))
bh = 26
for i,(lab,v,col) in enumerate(rows):
    y = T + i*(bh+18)
    x0 = bx(lo)
    s.append(f'<rect x="{x0:.1f}" y="{y}" width="{bx(v)-x0:.1f}" height="{bh}" rx="4" fill="{col}"/>')
    s.append(txt(L-12, y+bh/2+4, lab, 11.5, TXT, "end", "500"))
    s.append(txt(bx(v)+8, y+bh/2+4, f"{v} dBFS", 11, TXT, "start", "500", mono=True))
gx = bx(-48.0)
s.append(f'<line x1="{gx:.1f}" y1="{T-12}" x2="{gx:.1f}" y2="{T+ph+2}" '
         f'stroke="var(--viz-critical)" stroke-width="2"/>')
s.append(txt(gx, T-17, "old gate −48 dBFS", 10.5, "var(--viz-critical)", "middle", "500", mono=True))
s.append(txt(L-12, T+ph+18, "dBFS", 9.5, MUT, "end"))
s.append("</svg>")
open(f"{OUT}/sources.svg","w").write("\n".join(s))

# ---------------------------------------------------------------- chart C
# Windows counted in frames run shorter when the hardware rate is higher.
wins = [("hop",2048),("onset",3*2048),("fall trend",9*2048),
        ("release",10*2048),("floor window",110*2048)]
w,h = 560,250
L,R,T,B = 104,78,38,30
pw, ph = w-L-R, h-T-B
mx = max(n/44100 for _,n in wins)
def cx(v): return L + v/mx*pw
s = [HEAD.format(w=w,h=h,alt="Every window shortens by about eight per cent at 48 kHz")]
rh, gap = 13, 10
for i,(lab,n) in enumerate(wins):
    y = T + i*(rh*2+gap)
    a, b = n/44100, n/48000
    s.append(txt(L-12, y+rh, lab, 11, TXT, "end", "500"))
    s.append(f'<rect x="{L}" y="{y}" width="{cx(a)-L:.1f}" height="{rh}" rx="3" fill="var(--viz-s1)"/>')
    s.append(f'<rect x="{L}" y="{y+rh+2}" width="{cx(b)-L:.1f}" height="{rh}" rx="3" fill="var(--viz-s2)"/>')
    fmt = (lambda v: f"{v*1000:.0f} ms") if a < 1 else (lambda v: f"{v:.2f} s")
    s.append(txt(cx(a)+7, y+rh-2, fmt(a), 9.5, MUT, "start", mono=True))
    s.append(txt(cx(b)+7, y+rh*2+1, fmt(b), 9.5, MUT, "start", mono=True))
for i,(lab,col) in enumerate([("44.1 kHz — Android","var(--viz-s1)"),("48 kHz — typical browser","var(--viz-s2)")]):
    x = L + i*196
    s.append(f'<rect x="{x}" y="{T-26}" width="11" height="11" rx="2" fill="{col}"/>')
    s.append(txt(x+16, T-16, lab, 10.5, TXT, "start"))
s.append("</svg>")
open(f"{OUT}/samplerate.svg","w").write("\n".join(s))
print("charts written:", os.listdir(OUT))
