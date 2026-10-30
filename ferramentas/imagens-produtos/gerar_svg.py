# Gera as ilustrações de catálogo dos produtos da Orden (4 vistas por produto) em SVG.
# Cada peça é desenhada uma vez por vista com a MESMA cor, modelagem e estampa. Saída: svg/<slug>/<vista>.svg
import os, math

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'svg')

def hexrgb(h):
    h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))
def rgbhex(c):
    return '#%02x%02x%02x' % tuple(max(0, min(255, int(round(v)))) for v in c)
def shade(h, f):
    r, g, b = hexrgb(h)
    if f >= 1: return rgbhex((r + (255 - r) * (f - 1), g + (255 - g) * (f - 1), b + (255 - b) * (f - 1)))
    return rgbhex((r * f, g * f, b * f))

def defs(base, weave):
    """Gradiente de volume, textura de tecido e padrões de trama."""
    light, dark = shade(base, 1.18), shade(base, 0.72)
    w = {
        'knit': '<pattern id="weave" width="6" height="6" patternUnits="userSpaceOnUse"><path d="M0 3h6" stroke="#000" stroke-opacity=".05" stroke-width="1"/></pattern>',
        'denim': '<pattern id="weave" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(35)"><path d="M0 0v7" stroke="#fff" stroke-opacity=".13" stroke-width="2"/></pattern>',
        'twill': '<pattern id="weave" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(-40)"><path d="M0 0v5" stroke="#fff" stroke-opacity=".06" stroke-width="1.6"/></pattern>',
        'linen': '<pattern id="weave" width="8" height="8" patternUnits="userSpaceOnUse"><path d="M0 2h8M0 6h8" stroke="#fff" stroke-opacity=".10" stroke-width="1"/><path d="M2 0v8M6 0v8" stroke="#000" stroke-opacity=".06" stroke-width="1"/></pattern>',
        'leather': '<pattern id="weave" width="10" height="10" patternUnits="userSpaceOnUse"><circle cx="3" cy="4" r=".8" fill="#000" fill-opacity=".12"/><circle cx="8" cy="8" r=".6" fill="#fff" fill-opacity=".05"/></pattern>',
        'rib': '<pattern id="weave" width="9" height="9" patternUnits="userSpaceOnUse"><path d="M4.5 0v9" stroke="#000" stroke-opacity=".14" stroke-width="3"/></pattern>',
        'floral': floral_pattern(base),
    }[weave]
    return f'''<defs>
  <radialGradient id="bg" cx="50%" cy="42%" r="75%"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#eeeeec"/></radialGradient>
  <linearGradient id="vol" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{light}"/><stop offset=".55" stop-color="{base}"/><stop offset="1" stop-color="{dark}"/></linearGradient>
  <linearGradient id="sheen" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#000" stop-opacity=".18"/><stop offset=".25" stop-color="#fff" stop-opacity=".06"/><stop offset=".6" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".22"/></linearGradient>
  <filter id="grain" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".85" numOctaves="2" seed="7"/><feColorMatrix type="saturate" values="0"/><feComponentTransfer><feFuncA type="table" tableValues="0 .10"/></feComponentTransfer><feComposite in2="SourceGraphic" operator="in"/></filter>
  <filter id="soft" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="14"/></filter>
  {w}
</defs>'''

def floral_pattern(base):
    petal = '#f6d7d9'; center = '#f2c14e'; leaf = '#2f6b45'
    flowers = ''
    for (x, y, r) in [(14, 16, 7), (46, 40, 6), (30, 62, 5), (62, 12, 4)]:
        for k in range(5):
            a = k * 2 * math.pi / 5
            flowers += f'<ellipse cx="{x + math.cos(a) * r * .9:.1f}" cy="{y + math.sin(a) * r * .9:.1f}" rx="{r * .6:.1f}" ry="{r * .38:.1f}" transform="rotate({math.degrees(a):.0f} {x + math.cos(a) * r * .9:.1f} {y + math.sin(a) * r * .9:.1f})" fill="{petal}"/>'
        flowers += f'<circle cx="{x}" cy="{y}" r="{r * .3:.1f}" fill="{center}"/>'
        flowers += f'<path d="M{x + r} {y + r * .4} q{r} {r * .2} {r * 1.4} {r}" stroke="{leaf}" stroke-width="1.6" fill="none"/>'
    return f'<pattern id="weave" width="76" height="76" patternUnits="userSpaceOnUse"><rect width="76" height="76" fill="{base}"/>{flowers}</pattern>'

def garment(path, floral=False, extra=''):
    """Peça com volume: cor/estampa + trama + grão + luz lateral."""
    fill = 'url(#weave)' if floral else 'url(#vol)'
    layers = f'<path d="{path}" fill="{fill}"/>'
    if not floral:
        layers += f'<path d="{path}" fill="url(#weave)"/>'
    layers += f'<path d="{path}" fill="#808080" filter="url(#grain)"/>'
    layers += f'<path d="{path}" fill="url(#sheen)"/>'
    return layers + extra

def stitch(d, color='#000', op=.35, w=2.2, dash='7 6'):
    return f'<path d="{d}" fill="none" stroke="{color}" stroke-opacity="{op}" stroke-width="{w}" stroke-dasharray="{dash}" stroke-linecap="round"/>'

def line(d, color='#000', op=.28, w=3):
    return f'<path d="{d}" fill="none" stroke="{color}" stroke-opacity="{op}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>'

def svg(body, base, weave, shadow=(500, 905, 300, 26)):
    sx, sy, rx, ry = shadow
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000" width="1000" height="1000">
{defs(base, weave)}
<rect width="1000" height="1000" fill="url(#bg)"/>
<ellipse cx="{sx}" cy="{sy}" rx="{rx}" ry="{ry}" fill="#000" fill-opacity=".16" filter="url(#soft)"/>
{body}
</svg>'''

def detail_frame(inner, base, weave):
    """Vista de detalhe: recorte aproximado do tecido, como uma foto macro."""
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000" width="1000" height="1000">
{defs(base, weave)}
<rect width="1000" height="1000" fill="url(#bg)"/>
<clipPath id="lens"><rect x="70" y="70" width="860" height="860" rx="36"/></clipPath>
<g clip-path="url(#lens)">{inner}</g>
<rect x="70" y="70" width="860" height="860" rx="36" fill="none" stroke="#000" stroke-opacity=".06" stroke-width="2"/>
</svg>'''

# ----------------------------------------------------------------------------------------------- camiseta

TEE_FRONT = 'M418 168 Q500 236 582 168 L700 198 L842 330 L784 404 L684 360 L688 832 Q500 846 312 832 L316 360 L216 404 L158 330 L300 198 Z'
TEE_BACK = 'M418 168 Q500 192 582 168 L700 198 L842 330 L784 404 L684 360 L688 832 Q500 846 312 832 L316 360 L216 404 L158 330 L300 198 Z'
TEE_SIDE = 'M455 170 Q500 160 548 172 L566 214 Q640 250 662 360 L612 392 L598 372 L602 832 Q500 844 402 832 L398 300 Q404 214 455 170 Z'

def tee(base, print_=False):
    folds = line('M372 520 q30 120 -6 250', op=.12, w=6) + line('M632 500 q-26 130 10 270', op=.12, w=6)
    front_extra = (stitch('M430 180 Q500 246 570 180', op=.3) + line('M418 168 Q500 236 582 168', '#000', .25, 10)
                   + stitch('M318 810 Q500 822 682 810') + stitch('M170 338 L224 388') + stitch('M830 338 L776 388') + folds)
    if print_:
        front_extra += ('<g opacity=".92"><circle cx="500" cy="430" r="92" fill="none" stroke="#e8e4da" stroke-width="10"/>'
                        '<path d="M430 470 L500 360 L570 470 Z" fill="#e8e4da"/><path d="M455 470 L500 400 L545 470 Z" fill="#c9562c"/>'
                        '<path d="M410 520 h180" stroke="#e8e4da" stroke-width="8"/><path d="M430 544 h140" stroke="#c9562c" stroke-width="6"/></g>')
    back_extra = line('M418 168 Q500 192 582 168', '#000', .25, 10) + stitch('M318 810 Q500 822 682 810') + folds
    side_extra = (line('M455 170 Q500 160 548 172', '#000', .25, 9) + line('M566 214 Q640 250 662 360 L612 392', '#000', .18, 3)
                  + stitch('M406 812 Q500 822 596 812') + line('M560 420 q10 200 -4 380', op=.12, w=5))
    if print_:
        side_extra += '<path d="M402 360 q30 30 40 120 q-22 10 -40 0 Z" fill="#e8e4da" opacity=".8"/>'
    rib = ''.join(f'<path d="M{x} 70 v330" stroke="#000" stroke-opacity=".12" stroke-width="7"/>' for x in range(80, 940, 16))
    detail = (f'<rect width="1000" height="1000" fill="url(#vol)"/><rect width="1000" height="1000" fill="url(#weave)"/>'
              f'<rect width="1000" height="1000" fill="#808080" filter="url(#grain)"/>'
              f'<path d="M-20 420 Q500 640 1020 420 L1020 300 Q500 520 -20 300 Z" fill="{shade(base, .9)}"/>'
              f'<g clip-path="url(#ribclip)">{rib}</g>'
              f'<clipPath id="ribclip"><path d="M-20 420 Q500 640 1020 420 L1020 300 Q500 520 -20 300 Z"/></clipPath>'
              + stitch('M-20 452 Q500 672 1020 452', op=.4, w=4, dash='12 9') + stitch('M-20 478 Q500 698 1020 478', op=.4, w=4, dash='12 9')
              + (('<g transform="translate(250 560) scale(2.4)"><circle cx="100" cy="60" r="92" fill="none" stroke="#e8e4da" stroke-width="10"/>'
                  '<path d="M30 100 L100 -10 L170 100 Z" fill="#e8e4da"/></g>') if print_ else '')
              + '<rect width="1000" height="1000" fill="url(#sheen)"/>')
    return {
        'frente': svg(garment(TEE_FRONT, extra=front_extra), base, 'knit'),
        'traseira': svg(garment(TEE_BACK, extra=back_extra), base, 'knit'),
        'lateral': svg(garment(TEE_SIDE, extra=side_extra), base, 'knit', (500, 905, 170, 22)),
        'detalhes': detail_frame(detail, base, 'knit'),
    }

# ----------------------------------------------------------------------------------------------- calças

def trousers(base, skinny, weave):
    hem = 92 if skinny else 118
    lx, rx = 380 - (0 if skinny else 8), 620 + (0 if skinny else 8)
    front = (f'M356 150 L644 150 L652 200 L{rx + 18} 520 L{rx - 2} 880 L{rx - 2 - hem} 880 L506 352 '
             f'L494 352 L{lx + 2 + hem} 880 L{lx + 2} 880 L{lx - 18} 520 L348 200 Z')
    waist = '<path d="M356 150 L644 150 L648 196 L352 196 Z" fill="#000" fill-opacity=".08"/>'
    loops = ''.join(f'<rect x="{x}" y="146" width="14" height="54" rx="3" fill="#000" fill-opacity=".14"/>' for x in (372, 446, 540, 614))
    topst = '#d99a3c' if weave == 'denim' else '#000'
    sop = .8 if weave == 'denim' else .3
    if skinny:
        pockets_front = stitch('M362 214 Q420 230 434 286', topst, sop, 3) + stitch('M638 214 Q580 230 566 286', topst, sop, 3)
    else:
        pockets_front = line('M366 206 L420 300', op=.3, w=4) + line('M634 206 L580 300', op=.3, w=4)
    fly = stitch('M500 200 L500 330 Q500 350 476 352 L476 210', topst, sop, 3)
    button = '<circle cx="500" cy="174" r="11" fill="#b9b9b9" stroke="#777" stroke-width="3"/>'
    rivets = ('<circle cx="430" cy="226" r="5" fill="#c8a35a"/><circle cx="570" cy="226" r="5" fill="#c8a35a"/>' if weave == 'denim' else '')
    hems = stitch(f'M{lx + 6} 866 L{lx + hem - 2} 866', topst, sop, 3) + stitch(f'M{rx - hem + 2} 866 L{rx - 6} 866', topst, sop, 3)
    creases = line(f'M{(lx + lx + hem) / 2 + 6} 380 L{(lx + lx + hem) / 2 + 4} 860', op=.12, w=5) + line(f'M{(rx + rx - hem) / 2 - 6} 380 L{(rx + rx - hem) / 2 - 4} 860', op=.12, w=5)
    front_svg = svg(garment(front, extra=waist + loops + pockets_front + fly + button + rivets + hems + creases), base, weave, (500, 905, 230, 22))

    yoke = stitch('M352 236 Q430 262 500 300 Q570 262 648 236', topst, sop, 3)
    if skinny:
        bp = lambda x: (f'<path d="M{x} 300 L{x + 104} 300 L{x + 100} 400 L{x + 52} 424 L{x + 4} 400 Z" fill="#000" fill-opacity=".07"/>'
                        + stitch(f'M{x + 6} 306 L{x + 98} 306 L{x + 94} 394 L{x + 52} 416 L{x + 10} 394 Z', topst, sop, 3)
                        + stitch(f'M{x + 24} 344 Q{x + 52} 368 {x + 80} 344', topst, sop, 3))
    else:
        bp = lambda x: line(f'M{x + 8} 312 L{x + 96} 312', op=.35, w=5) + f'<circle cx="{x + 52}" cy="300" r="6" fill="#222"/>'
    back_svg = svg(garment(front, extra=waist + loops + yoke + bp(382) + bp(514) + hems + creases), base, weave, (500, 905, 230, 22))

    side = f'M440 150 L560 150 L566 200 Q600 500 {560} 880 L{560 - hem - 10} 880 Q440 500 434 200 Z'
    side_svg = svg(garment(side, extra='<path d="M440 150 L560 150 L563 196 L437 196 Z" fill="#000" fill-opacity=".08"/>'
                           + stitch('M500 200 Q520 520 504 870', topst, sop, 3) + line('M548 210 Q560 260 540 300', op=.3, w=4)
                           + '<rect x="492" y="146" width="14" height="54" rx="3" fill="#000" fill-opacity=".14"/>'),
                   base, weave, (500, 905, 120, 18))

    if weave == 'denim':
        inner = (f'<rect width="1000" height="1000" fill="url(#vol)"/><rect width="1000" height="1000" fill="url(#weave)"/>'
                 f'<rect width="1000" height="1000" fill="#808080" filter="url(#grain)"/>'
                 + stitch('M80 300 Q500 380 940 300', topst, .9, 7, '22 14') + stitch('M80 330 Q500 410 940 330', topst, .9, 7, '22 14')
                 + stitch('M180 420 Q300 600 330 900', topst, .9, 7, '22 14')
                 + '<circle cx="330" cy="420" r="30" fill="#c8a35a"/><circle cx="330" cy="420" r="16" fill="#a7843d"/>'
                 + '<circle cx="650" cy="560" r="62" fill="#c4c4c4" stroke="#888" stroke-width="8"/><circle cx="650" cy="560" r="28" fill="#9a9a9a"/>'
                 + '<rect width="1000" height="1000" fill="url(#sheen)"/>')
    else:
        inner = (f'<rect width="1000" height="1000" fill="url(#vol)"/><rect width="1000" height="1000" fill="url(#weave)"/>'
                 f'<rect width="1000" height="1000" fill="#808080" filter="url(#grain)"/>'
                 '<path d="M220 420 L780 420 L780 448 L220 448 Z" fill="#000" fill-opacity=".35"/>'
                 + stitch('M220 400 L780 400', '#000', .5, 5, '14 10') + stitch('M220 470 L780 470', '#000', .5, 5, '14 10')
                 + '<circle cx="500" cy="330" r="34" fill="#1d1d1d" stroke="#000" stroke-width="5"/><circle cx="490" cy="322" r="5" fill="#555"/><circle cx="510" cy="338" r="5" fill="#555"/>'
                 + '<rect width="1000" height="1000" fill="url(#sheen)"/>')
    return {'frente': front_svg, 'traseira': back_svg, 'lateral': side_svg, 'detalhes': detail_frame(inner, base, weave)}

# ----------------------------------------------------------------------------------------------- boné

def cap(base):
    crown = 'M260 560 Q260 270 500 250 Q740 270 740 560 Z'
    seams = line('M500 252 L500 560', op=.25, w=4) + line('M500 252 Q390 330 360 560', op=.2, w=4) + line('M500 252 Q610 330 640 560', op=.2, w=4)
    visor = f'<path d="M250 560 Q500 520 750 560 Q760 640 500 660 Q240 640 250 560 Z" fill="{shade(base, .82)}"/>' + stitch('M280 600 Q500 580 720 600', '#fff', .25, 3) + stitch('M300 625 Q500 608 700 625', '#fff', .25, 3)
    eyelets = ''.join(f'<ellipse cx="{x}" cy="{y}" rx="9" ry="6" fill="#000" fill-opacity=".45"/>' for x, y in ((380, 380), (620, 380)))
    top = f'<circle cx="500" cy="252" r="16" fill="{shade(base, .9)}" stroke="#000" stroke-opacity=".3" stroke-width="3"/>'
    front = svg(garment(crown, extra=seams + eyelets + top) + visor, base, 'twill', (500, 700, 260, 22))
    opening = f'<path d="M430 560 Q430 470 500 462 Q570 470 570 560 Z" fill="#e9e9e7"/>'
    strap = '<rect x="410" y="500" width="180" height="26" rx="8" fill="#1c1c1c"/><rect x="470" y="494" width="44" height="38" rx="5" fill="#9a9a9a" stroke="#666" stroke-width="3"/>'
    back = svg(garment(crown, extra=seams + top + stitch('M266 552 Q500 540 734 552', '#fff', .25, 3)) + opening + strap, base, 'twill', (500, 640, 240, 20))
    side_crown = 'M300 560 Q300 290 520 270 Q700 290 700 560 Z'
    side_brim = f'<path d="M640 548 Q800 530 880 590 Q820 616 660 590 Z" fill="{shade(base, .8)}"/>' + stitch('M670 566 Q790 556 850 590', '#fff', .25, 3)
    side = svg(garment(side_crown, extra=line('M520 272 Q470 400 470 560', op=.22, w=4) + line('M520 272 Q600 400 620 560', op=.2, w=4)
                       + '<ellipse cx="560" cy="380" rx="9" ry="6" fill="#000" fill-opacity=".45"/>'
                       + f'<circle cx="520" cy="272" r="15" fill="{shade(base, .9)}"/>') + side_brim, base, 'twill', (560, 640, 260, 20))
    inner = (f'<rect width="1000" height="1000" fill="url(#vol)"/><rect width="1000" height="1000" fill="url(#weave)"/>'
             f'<rect width="1000" height="1000" fill="#808080" filter="url(#grain)"/>'
             f'<path d="M-20 560 Q500 470 1020 560 L1020 1020 L-20 1020 Z" fill="{shade(base, .8)}"/>'
             + ''.join(stitch(f'M-20 {620 + i * 46} Q500 {530 + i * 46} 1020 {620 + i * 46}', '#fff', .3, 5, '16 12') for i in range(6))
             + '<ellipse cx="500" cy="300" rx="60" ry="40" fill="#000" fill-opacity=".55"/><ellipse cx="500" cy="300" rx="36" ry="22" fill="#e9e9e7"/>'
             + line('M500 70 L500 260', op=.25, w=8) + '<rect width="1000" height="1000" fill="url(#sheen)"/>')
    return {'frente': front, 'traseira': back, 'lateral': side, 'detalhes': detail_frame(inner, base, 'twill')}

# ----------------------------------------------------------------------------------------------- cinto

def belt(base):
    strap = 'M250 470 L900 470 Q930 470 930 500 L930 530 Q930 560 900 560 L250 560 Z'
    buckle = ('<rect x="130" y="430" width="140" height="170" rx="20" fill="none" stroke="#b9b9b9" stroke-width="22"/>'
              '<rect x="130" y="430" width="140" height="170" rx="20" fill="none" stroke="#fff" stroke-opacity=".35" stroke-width="6"/>'
              '<path d="M200 515 L320 515" stroke="#a5a5a5" stroke-width="14" stroke-linecap="round"/>')
    holes = ''.join(f'<ellipse cx="{x}" cy="515" rx="9" ry="7" fill="#2a1a10"/>' for x in (700, 740, 780, 820, 860))
    keeper = f'<rect x="330" y="455" width="40" height="120" rx="8" fill="{shade(base, .85)}"/>'
    edge = stitch('M260 482 L900 482', '#f0e2c8', .55, 3) + stitch('M260 548 L900 548', '#f0e2c8', .55, 3)
    front = svg(garment(strap, extra=edge + holes) + keeper + buckle, base, 'leather', (520, 640, 420, 20))
    back_strap = 'M100 470 L750 470 L750 560 L100 560 Q70 560 70 530 L70 500 Q70 470 100 470 Z'
    back = svg(f'<path d="{back_strap}" fill="{shade(base, 1.25)}"/><path d="{back_strap}" fill="#808080" filter="url(#grain)"/>'
               + stitch('M100 482 L740 482', '#3a2414', .4, 3) + stitch('M100 548 L740 548', '#3a2414', .4, 3)
               + f'<rect x="630" y="455" width="40" height="120" rx="8" fill="{shade(base, .85)}"/>'
               + '<rect x="730" y="430" width="140" height="170" rx="20" fill="none" stroke="#b9b9b9" stroke-width="22"/>',
               base, 'leather', (480, 640, 420, 20))
    rings = ''.join(f'<circle cx="500" cy="500" r="{r}" fill="none" stroke="{shade(base, .55 + (r % 3) * .1)}" stroke-width="40"/>' for r in range(60, 300, 44))
    side = svg(f'<g>{rings}</g><circle cx="500" cy="500" r="40" fill="#e9e9e7"/>'
               + '<rect x="700" y="420" width="120" height="150" rx="18" fill="none" stroke="#b9b9b9" stroke-width="20"/>'
               + f'<circle cx="500" cy="500" r="300" fill="url(#sheen)" opacity=".7"/>', base, 'leather', (500, 830, 300, 22))
    inner = (f'<rect width="1000" height="1000" fill="#e9e9e7"/>'
             f'<rect x="-20" y="330" width="1040" height="340" fill="url(#vol)"/><rect x="-20" y="330" width="1040" height="340" fill="url(#weave)"/>'
             f'<rect x="-20" y="330" width="1040" height="340" fill="#808080" filter="url(#grain)"/>'
             + stitch('M-20 372 L1020 372', '#f0e2c8', .6, 6, '18 12') + stitch('M-20 628 L1020 628', '#f0e2c8', .6, 6, '18 12')
             + '<ellipse cx="780" cy="500" rx="26" ry="20" fill="#2a1a10"/>'
             + '<rect x="120" y="230" width="320" height="540" rx="50" fill="none" stroke="#bdbdbd" stroke-width="56"/>'
             + '<rect x="120" y="230" width="320" height="540" rx="50" fill="none" stroke="#fff" stroke-opacity=".4" stroke-width="12"/>'
             + '<path d="M280 500 L620 500" stroke="#a5a5a5" stroke-width="36" stroke-linecap="round"/>')
    return {'frente': front, 'traseira': back, 'lateral': side, 'detalhes': detail_frame(inner, base, 'leather')}

# ----------------------------------------------------------------------------------------------- meias

SOCK = 'M0 0 L110 0 L110 330 Q110 400 170 430 L250 470 Q300 500 280 545 Q262 585 205 570 L40 500 Q0 480 0 430 Z'

def sock(x, y, color, flip=False, heel_view=False):
    tr = f'translate({x} {y})' + (' scale(-1 1)' if flip else '')
    cuff = f'<rect x="0" y="0" width="110" height="90" fill="{shade(color, .9)}"/>' + ''.join(f'<path d="M{i} 0 v90" stroke="#000" stroke-opacity=".12" stroke-width="5"/>' for i in range(8, 110, 13))
    heel = f'<path d="M0 400 Q0 480 60 500 L80 430 Q30 420 20 380 Z" fill="{shade(color, .8)}"/>' if heel_view else f'<path d="M205 570 Q262 585 280 545 Q285 520 262 500 Q240 540 205 530 Z" fill="{shade(color, .82)}"/>'
    return (f'<g transform="{tr}"><path d="{SOCK}" fill="{color}"/><path d="{SOCK}" fill="#808080" filter="url(#grain)"/>{cuff}{heel}'
            f'<path d="{SOCK}" fill="url(#sheen)" opacity=".7"/></g>')

def socks(base):
    tones = [shade(base, 1.25), base, shade(base, .62)]
    front = svg(sock(250, 230, tones[0]) + sock(400, 250, tones[1]) + sock(550, 270, tones[2]), base, 'rib', (520, 880, 330, 22))
    back = svg(sock(750, 230, tones[0], True, True) + sock(600, 250, tones[1], True, True) + sock(450, 270, tones[2], True, True), base, 'rib', (480, 880, 330, 22))
    side = svg(sock(380, 230, tones[1]), base, 'rib', (520, 860, 200, 20))
    inner = (f'<rect width="1000" height="1000" fill="{tones[1]}"/>'
             + ''.join(f'<path d="M{x} -20 v640" stroke="#000" stroke-opacity=".18" stroke-width="22"/>' for x in range(20, 1000, 56))
             + f'<rect y="600" width="1000" height="420" fill="{tones[1]}"/>'
             + ''.join(f'<path d="M{x} 600 l14 26 l14 -26" fill="none" stroke="#000" stroke-opacity=".12" stroke-width="5" transform="translate(0 {k * 30})"/>' for x in range(0, 1000, 28) for k in range(14))
             + '<rect width="1000" height="1000" fill="#808080" filter="url(#grain)"/><rect width="1000" height="1000" fill="url(#sheen)"/>')
    return {'frente': front, 'traseira': back, 'lateral': side, 'detalhes': detail_frame(inner, base, 'rib')}

# ----------------------------------------------------------------------------------------------- vestidos

def dress(base, kind):
    floral = kind == 'floral'
    weave = 'floral' if floral else 'linen'
    if kind == 'linen':   # curto, alça larga, decote V, botões, faixa na cintura
        front = 'M410 170 L470 170 L500 250 L530 170 L590 170 L624 200 L612 420 Q600 450 612 470 L726 760 Q500 800 274 760 L388 470 Q400 450 388 420 L376 200 Z'
        back = 'M410 170 L590 170 L624 200 L612 420 Q600 450 612 470 L726 760 Q500 800 274 760 L388 470 Q400 450 388 420 L376 200 Z'
        side = 'M460 170 L540 170 L556 200 L548 430 L640 760 Q520 790 400 760 L452 430 L444 200 Z'
        hem = 'M286 748 Q500 786 714 748'
    else:                 # midi floral, decote quadrado, manga bufante, saia rodada
        front = 'M400 190 L600 190 L640 200 L718 250 L700 330 L632 300 L618 440 Q606 460 620 476 L770 900 Q500 940 230 900 L380 476 Q394 460 382 440 L368 300 L300 330 L282 250 L360 200 Z'
        back = front
        side = 'M460 190 L540 190 L560 210 L612 270 L590 330 L556 300 L550 440 L680 900 Q520 930 360 900 L450 440 L444 210 Z'
        hem = 'M244 888 Q500 928 756 888'
    waist_y = 452
    belt_ = f'<path d="M386 {waist_y - 14} L614 {waist_y - 14} L614 {waist_y + 14} L386 {waist_y + 14} Z" fill="{shade(base, .82) if not floral else "#7a1a20"}"/>'
    folds = ''.join(line(f'M{x} 520 Q{x + d} 680 {x + 2 * d} {770 if not floral else 880}', op=.13, w=6) for x, d in ((440, -30), (500, 0), (560, 30)))
    if kind == 'linen':
        neck = line('M470 170 L500 250 L530 170', '#000', .28, 6)
        buttons = ''.join(f'<circle cx="500" cy="{y}" r="9" fill="#f1ede4" stroke="#00000040" stroke-width="2"/>' for y in (290, 340, 390))
        extra_f = neck + buttons
        extra_b = line('M500 180 L500 440', '#000', .3, 4) + '<rect x="494" y="180" width="12" height="22" rx="3" fill="#b9b9b9"/>'
    else:
        neck = line('M400 190 L600 190', '#000', .3, 6) + line('M300 330 Q330 300 368 300', '#000', .2, 4) + line('M700 330 Q670 300 632 300', '#000', .2, 4)
        extra_f = neck
        extra_b = line('M500 192 L500 470', '#000', .35, 4) + '<rect x="494" y="192" width="12" height="22" rx="3" fill="#b9b9b9"/>'
    hemst = stitch(hem, '#000', .3, 3)
    f = svg(garment(front, floral, extra_f + belt_ + folds + hemst), base, weave, (500, 925 if floral else 815, 250, 22))
    b = svg(garment(back, floral, extra_b + belt_ + folds + hemst), base, weave, (500, 925 if floral else 815, 250, 22))
    s = svg(garment(side, floral, line('M500 200 L500 440', op=.18, w=4) + folds), base, weave, (520, 925 if floral else 815, 170, 20))
    if floral:
        inner = ('<g transform="scale(4.2)"><rect width="260" height="260" fill="url(#weave)"/></g>'
                 '<rect width="1000" height="1000" fill="#808080" filter="url(#grain)"/>' + stitch('M-20 760 Q500 700 1020 760', '#000', .4, 6, '18 12')
                 + '<rect width="1000" height="1000" fill="url(#sheen)"/>')
    else:
        slubs = ''.join(f'<path d="M{(i * 137) % 1000} {(i * 89) % 1000} h{40 + (i * 7) % 60}" stroke="#fff" stroke-opacity=".18" stroke-width="3"/>' for i in range(70))
        inner = ('<rect width="1000" height="1000" fill="url(#vol)"/><g transform="scale(3)"><rect width="340" height="340" fill="url(#weave)"/></g>'
                 + slubs + '<rect width="1000" height="1000" fill="#808080" filter="url(#grain)"/>'
                 + line('M500 -20 L500 1020', '#000', .22, 10) + ''.join(f'<circle cx="500" cy="{y}" r="44" fill="#f1ede4" stroke="#00000040" stroke-width="5"/><circle cx="488" cy="{y - 10}" r="6" fill="#00000050"/><circle cx="512" cy="{y + 10}" r="6" fill="#00000050"/>' for y in (260, 640))
                 + '<rect width="1000" height="1000" fill="url(#sheen)"/>')
    return {'frente': f, 'traseira': b, 'lateral': s, 'detalhes': detail_frame(inner, base, weave)}

# ----------------------------------------------------------------------------------------------- jaquetas

JACKET = 'M420 160 L580 160 L700 196 L800 330 L830 760 L760 770 L728 420 L700 410 L704 800 Q500 816 296 800 L300 410 L272 420 L240 770 L170 760 L200 330 L300 196 Z'

def jacket(base, kind):
    weave = 'denim' if kind == 'denim' else 'leather'
    collar = f'<path d="M420 160 L500 250 L580 160 L620 190 L540 300 L500 260 L460 300 L380 190 Z" fill="{shade(base, .85)}"/>'
    if kind == 'denim':
        top = '#d99a3c'
        closure = line('M500 260 L500 800', '#000', .3, 4) + ''.join(f'<circle cx="520" cy="{y}" r="10" fill="#c4c4c4" stroke="#888" stroke-width="3"/>' for y in (320, 420, 520, 620, 720))
        pockets = ''.join(stitch(f'M{x} 350 L{x + 90} 350 L{x + 90} 430 L{x + 45} 450 L{x} 430 Z', top, .8, 3) for x in (360, 550))
        extra = collar + closure + pockets + stitch('M300 300 Q500 330 700 300', top, .8, 3) + stitch('M300 760 L700 760', top, .8, 3)
        back_extra = collar + stitch('M300 330 Q500 380 700 330', top, .8, 3) + stitch('M420 380 L420 760', top, .8, 3) + stitch('M580 380 L580 760', top, .8, 3) + stitch('M300 760 L700 760', top, .8, 3)
    else:
        closure = '<path d="M540 280 L470 800" stroke="#9a9a9a" stroke-width="10"/>' + '<rect x="528" y="270" width="22" height="40" rx="4" fill="#c4c4c4"/>'
        pockets = line('M360 560 L440 540', '#000', .5, 6) + line('M640 560 L570 540', '#000', .5, 6)
        extra = collar + closure + pockets + line('M300 760 L700 760', '#000', .35, 5)
        back_extra = collar + line('M300 330 Q500 370 700 330', '#000', .3, 5) + line('M300 760 L700 760', '#000', .35, 5)
    side = 'M440 160 L560 160 L600 210 Q640 300 640 420 L620 800 Q500 816 380 800 L380 300 Q390 210 440 160 Z'
    side_extra = f'<path d="M560 230 L640 330 L660 770 L600 780 L570 420 Z" fill="{shade(base, .8)}"/>' + (stitch('M400 300 L620 300', '#d99a3c', .8, 3) if kind == 'denim' else line('M392 300 L610 300', '#000', .3, 5))
    if kind == 'denim':
        inner = (f'<rect width="1000" height="1000" fill="url(#vol)"/><rect width="1000" height="1000" fill="url(#weave)"/><rect width="1000" height="1000" fill="#808080" filter="url(#grain)"/>'
                 + stitch('M140 200 L860 200 L860 620 L500 760 L140 620 Z', '#d99a3c', .9, 7, '22 14')
                 + '<circle cx="500" cy="420" r="80" fill="#c4c4c4" stroke="#888" stroke-width="10"/><circle cx="500" cy="420" r="36" fill="#9a9a9a"/><rect width="1000" height="1000" fill="url(#sheen)"/>')
    else:
        teeth = ''.join(f'<rect x="{470 + (12 if k % 2 else -12)}" y="{k * 26}" width="34" height="16" rx="3" fill="#b0b0b0"/>' for k in range(40))
        inner = (f'<rect width="1000" height="1000" fill="url(#vol)"/><rect width="1000" height="1000" fill="url(#weave)"/><rect width="1000" height="1000" fill="#808080" filter="url(#grain)"/>'
                 + '<rect x="440" y="-20" width="120" height="1040" fill="#1a1a1a"/>' + teeth
                 + '<rect x="452" y="420" width="96" height="150" rx="16" fill="#c9c9c9" stroke="#8a8a8a" stroke-width="6"/><rect x="480" y="560" width="40" height="120" rx="14" fill="#bdbdbd"/>'
                 + '<rect width="1000" height="1000" fill="url(#sheen)"/>')
    return {'frente': svg(garment(JACKET, extra=extra), base, weave, (500, 830, 300, 22)),
            'traseira': svg(garment(JACKET, extra=back_extra), base, weave, (500, 830, 300, 22)),
            'lateral': svg(garment(side, extra=side_extra), base, weave, (510, 830, 170, 20)),
            'detalhes': detail_frame(inner, base, weave)}

# ----------------------------------------------------------------------------------------------- catálogo

PRODUTOS = {
    # código no sistema -> (pasta, gerador)
    'ACE-002-U-PT': ('bone_preto', lambda: cap('#262626')),
    'CAL-001-40-AZ': ('calca_jeans_azul', lambda: trousers('#3c5a85', True, 'denim')),
    'CAL-002-42-PT': ('calca_sarja_preta', lambda: trousers('#2a2a2a', False, 'twill')),
    'CAM-002-G-BR': ('camiseta_branca_g', lambda: tee('#f2f1ec')),
    'CAM-001-M-BR': ('camiseta_branca_m', lambda: tee('#f2f1ec')),
    'CAM-003-P-PT': ('camiseta_estampada_preta', lambda: tee('#222222', print_=True)),
    'ACE-001-U-MR': ('cinto_marrom', lambda: belt('#6b4226')),
    'ACE-003-U-CZ': ('kit_meias_cinza', lambda: socks('#8d8f93')),
    'VES-002-P-AZ': ('vestido_linho_azul', lambda: dress('#6f8fb8', 'linen')),
    'VES-001-M-VM': ('vestido_floral_vermelho', lambda: dress('#b3262e', 'floral')),
    'JAQ-001-M-PT': ('jaqueta_couro_preta', lambda: jacket('#1f1f1f', 'leather')),
    'JAQ-002-G-AZ': ('jaqueta_jeans_azul', lambda: jacket('#4a6a96', 'denim')),
}

if __name__ == '__main__':
    for code, (slug, gen) in PRODUTOS.items():
        os.makedirs(os.path.join(OUT, slug), exist_ok=True)
        for view, content in gen().items():
            with open(os.path.join(OUT, slug, view + '.svg'), 'w', encoding='utf-8') as fh:
                fh.write(content)
    print('ok', len(PRODUTOS), 'produtos')
