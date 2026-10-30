# Confere o contraste (WCAG 2.1) de cada par de cores dos quatro modos de aparência (claro oficial, escuro, e os dois com
# alto contraste), e também dos tons do Dashboard "Ice" (.cores-dashboard) em cada modo.
# Uso: python verificar-contraste.py   (acrescente -v para listar também os pares que passam).
# Os valores abaixo ESPELHAM crm-loja-frontend/src/presentation/styles/tokens.css: mudou uma cor lá, mude aqui e rode de novo.
#
# Regras: texto precisa de 4,5:1 (7:1 nos modos de alto contraste); borda de campo digitável, 3:1. A borda das divisórias é
# discreta de propósito e fica fora da conferência.
# O visual padrão (claro oficial, incluindo o verde, o amarelo e o vermelho do Dashboard Ice) é DEFINIDO pelo Douglas e não é
# alterado por esta conferência: os pares abaixo do mínimo nele aparecem como "INFO" (não contam como falha); quem precisa de
# mais contraste usa "Alto contraste" em Aparência, que passa em tudo.
import sys


def lum(h):
    h = h.lstrip('#')
    r, g, b = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    f = lambda c: c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)


def ratio(a, b):
    la, lb = lum(a), lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


# Base de cada modo (neutra, sem o Dashboard Ice).
BASE = {
    'claro': dict(bg='#f6f6f6', surface='#ffffff', surface2='#f0f0f0', borderinput='#858585', text='#111111', muted='#5c5c5c',
                  primary='#111111', onprimary='#ffffff', primarysoft='#e4e4e4', success='#1f1f1f', successsoft='#e8e8e8',
                  warning='#3d3d3d', warningsoft='#ededed', error='#000000', onerror='#ffffff', errorsoft='#dcdcdc',
                  info='#4a4a4a', infosoft='#eeeeee'),
    'escuro': dict(bg='#161616', surface='#1f1f1f', surface2='#2a2a2a', borderinput='#8f8f8f', text='#f2f2f2', muted='#b8b8b8',
                   primary='#f2f2f2', onprimary='#111111', primarysoft='#333333', success='#dcdcdc', successsoft='#2e2e2e',
                   warning='#cfcfcf', warningsoft='#333333', error='#ffffff', onerror='#111111', errorsoft='#3d3d3d',
                   info='#c4c4c4', infosoft='#2e2e2e'),
    'claro+contraste': dict(bg='#ffffff', surface='#ffffff', surface2='#f0f0f0', borderinput='#1a1a1a', text='#000000', muted='#2e2e2e',
                            primary='#000000', onprimary='#ffffff', primarysoft='#e4e4e4', success='#000000', successsoft='#e8e8e8',
                            warning='#1a1a1a', warningsoft='#ededed', error='#000000', onerror='#ffffff', errorsoft='#dcdcdc',
                            info='#1a1a1a', infosoft='#eeeeee'),
    'escuro+contraste': dict(bg='#0b0b0b', surface='#121212', surface2='#1c1c1c', borderinput='#d9d9d9', text='#ffffff', muted='#e6e6e6',
                             primary='#ffffff', onprimary='#000000', primarysoft='#333333', success='#ffffff', successsoft='#2e2e2e',
                             warning='#ffffff', warningsoft='#333333', error='#ffffff', onerror='#000000', errorsoft='#3d3d3d',
                             info='#ffffff', infosoft='#2e2e2e'),
}

# O que o Dashboard Ice (.cores-dashboard) troca em cima da base de cada modo.
ICE = {
    'claro': dict(primary='#0c4a6e', onprimary='#ffffff', primarysoft='#e3f4f6', success='#259948', successsoft='#e3f1e6',
                  warning='#ab7f2a', warningsoft='#f7efdc', error='#b9463a', errorsoft='#f6e2df', info='#217e8f', infosoft='#e3f4f6'),
    'escuro': dict(primary='#86e5d8', onprimary='#00183d', primarysoft='#12343a', success='#6fd38d', successsoft='#16301f',
                   warning='#e0b84f', warningsoft='#33290f', error='#ff9a8d', onerror='#111111', errorsoft='#3a1c18',
                   info='#7fd0dd', infosoft='#12343a'),
    'claro+contraste': dict(primary='#00183d', onprimary='#ffffff', primarysoft='#e3f4f6', success='#0d5a26', successsoft='#e3f1e6',
                            warning='#6b4a00', warningsoft='#f7efdc', error='#8a1f12', errorsoft='#f6e2df', info='#0c4a6e', infosoft='#e3f4f6'),
    'escuro+contraste': dict(primary='#b6f0e8', onprimary='#00183d', primarysoft='#12343a', success='#b5f0c6', successsoft='#16301f',
                             warning='#ffe08a', warningsoft='#33290f', error='#ffc2b8', onerror='#111111', errorsoft='#3a1c18',
                             info='#b6e8f0', infosoft='#12343a'),
}

PARES = [('text', 'bg'), ('text', 'surface'), ('text', 'surface2'), ('muted', 'bg'), ('muted', 'surface'), ('muted', 'surface2'),
         ('primary', 'surface'), ('primary', 'bg'), ('onprimary', 'primary'), ('text', 'primarysoft'), ('primary', 'primarysoft'),
         ('success', 'successsoft'), ('warning', 'warningsoft'), ('error', 'errorsoft'), ('info', 'infosoft'),
         ('onerror', 'error'), ('error', 'surface'), ('success', 'surface'), ('warning', 'surface'), ('info', 'surface')]
BORDA = [('borderinput', 'surface'), ('borderinput', 'bg')]

falhas = 0
infos = 0
for modo, base in BASE.items():
    alto = 'contraste' in modo
    for rotulo, cores in (('', base), (' + Dashboard Ice', {**base, **ICE[modo]})):
        padrao_ice = (modo == 'claro' and rotulo != '')   # o visual oficial do dia a dia: só informa
        print('==', modo + rotulo)
        for fg, bg in PARES:
            minimo = 7.0 if alto else 4.5
            r = ratio(cores[fg], cores[bg])
            if r < minimo:
                if padrao_ice:
                    infos += 1
                    print(f'  INFO   {fg:>11} sobre {bg:<12} {r:5.2f}  (paleta oficial; o alto contraste cobre)')
                else:
                    falhas += 1
                    print(f'  FALHA  {fg:>11} sobre {bg:<12} {r:5.2f}  (precisa {minimo})')
            elif '-v' in sys.argv:
                print(f'  ok     {fg:>11} sobre {bg:<12} {r:5.2f}')
        if rotulo == '':
            for fg, bg in BORDA:
                r = ratio(cores[fg], cores[bg])
                if r < 3.0:
                    falhas += 1
                    print(f'  FALHA  {fg:>11} sobre {bg:<12} {r:5.2f}  (precisa 3.0)')
print('falhas:', falhas, '| informativos (paleta oficial):', infos)
