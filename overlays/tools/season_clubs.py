"""The season intros' clubs (src/season/season.js): each club's own scene, from what is on its crest or what it is known
by, by ESPN's team id. Writes season_clubs.json next to this file: python3 season_clubs.py

bg: the background (radial c1 to c2; stripes; split; sash; cross; band; tiger; tiles), ink: the name's colour,
accent: the line under the name; fx: what moves, each {m: a picture (e:<emoji code> from the open Twemoji set, or s:<a
shape drawn by the page>), a: how it moves (fly, bloom, grow, flank, swarm, rain, roar, sail, run, orbit, hop, stack),
n: how many, tint: one colour all over (a silhouette), ol: its outline's colour, s: size}.
"""
import json
import os

W = '#FFFFFF'
K = '#111111'


def r(c1, c2):
    return {'t': 'radial', 'c': [c1, c2]}


def st(c1, c2, n=9):
    return {'t': 'stripes', 'c': [c1, c2], 'n': n}


def sp(c1, c2):
    return {'t': 'split', 'c': [c1, c2]}


def fx(m, a, n=1, **k):
    d = {'m': m, 'a': a, 'n': n}
    d.update(k)
    return d


CLUBS = {
    # ---- Premier League
    '349': ('AFC BOURNEMOUTH', st('#DA291C', K), [fx('e:1f352', 'rain', 14)], W, '#FFFFFF'),
    '359': ('ARSENAL', r('#F0161E', '#5E0004'), [fx('s:cannon', 'flank', 2)], W, '#F6C342'),
    '362': ('ASTON VILLA', r('#8A1A44', '#33041A'), [fx('e:1f981', 'roar'), fx('e:2728', 'orbit', 6, tint='#95BFE5')], W, '#95BFE5'),
    '337': ('BRENTFORD', st('#E30613', '#F4F4F4', 7), [fx('e:1f41d', 'swarm', 14)], W, '#FFD400'),
    '331': ('BRIGHTON & HOVE ALBION', r('#1478E0', '#00205B'), [fx('s:gull', 'fly', 12)], W, '#FFFFFF'),
    '363': ('CHELSEA', r('#0A5BC4', '#021C4A'), [fx('e:1f981', 'flank', 2)], W, '#F5C518'),
    '388': ('COVENTRY CITY', r('#6CC5EE', '#1B5F8C'), [fx('e:1f418', 'run', 3)], W, '#FFFFFF'),
    '384': ('CRYSTAL PALACE', sp('#1B458F', '#C4122E'), [fx('e:1f985', 'flank', 2)], W, '#FFFFFF'),
    '368': ('EVERTON', r('#1F4DC2', '#001A5E'), [fx('s:tower', 'grow', 2), fx('e:2728', 'orbit', 6)], W, '#FFFFFF'),
    '370': ('FULHAM', r('#2E2E2E', '#050505'), [fx('e:1f3e1', 'grow'), fx('s:waves', 'sail', tint='#26303D')], W, '#CC0000'),
    '306': ('HULL CITY', {'t': 'tiger', 'c': ['#F5A12D', K]}, [fx('e:1f42f', 'roar')], W, K),
    '373': ('IPSWICH TOWN', r('#1E5BC6', '#062A6B'), [fx('e:1f40e', 'run', 3)], W, '#DE2C37'),
    '357': ('LEEDS UNITED', r('#1D428A', '#0B1E46'), [fx('s:rose', 'bloom', 8, s=1.3)], W, '#FFCD00'),
    '364': ('LIVERPOOL', r('#D00027', '#5E0010'), [fx('s:gull', 'fly', 10, tint='#F6EB61')], W, '#F6EB61'),
    '382': ('MANCHESTER CITY', r('#8FC8EE', '#2C6AA0'), [fx('e:26f5', 'sail', 2), fx('s:waves', 'sail', tint='#1C2C5B')], W, '#F2C230'),
    '360': ('MANCHESTER UNITED', r('#E2001A', '#4A0007'), [fx('s:trident', 'flank', 2, s=1.5), fx('e:1f608', 'bloom', 6, s=0.9)], W, '#FBE122'),
    '361': ('NEWCASTLE UNITED', st(K, '#F2F2F2', 9), [fx('e:1f426', 'fly', 9, tint=K, ol=W, alt=W)], W, '#41B6E6'),
    '393': ('NOTTINGHAM FOREST', r('#E1001A', '#6B0010'), [fx('e:1f333', 'grow', 5)], W, '#FFFFFF'),
    '366': ('SUNDERLAND', st('#EB172B', '#FFFFFF', 7), [fx('e:1f408-200d-2b1b', 'run', 3)], W, K),
    '367': ('TOTTENHAM HOTSPUR', r('#1E3A8A', '#0A1430'), [fx('e:1f413', 'flank', 2)], W, '#FFFFFF'),
    # ---- LaLiga
    '96': ('ALAVÉS', st('#0A3FAF', '#FFFFFF', 9), [fx('e:1f3f0', 'grow')], W, '#FFFFFF'),
    '93': ('ATHLETIC CLUB', st('#EE2523', '#FFFFFF', 9), [fx('e:1f981', 'flank', 2)], W, K),
    '1068': ('ATLÉTICO MADRID', st('#CB3524', '#FFFFFF', 9), [fx('e:1f43b', 'roar'), fx('e:1f353', 'rain', 12)], W, '#272E61'),
    '83': ('BARCELONA', sp('#004D98', '#A50044'), [fx('s:spires', 'grow', side=1), fx('e:2728', 'orbit', 6, tint='#EDBB00')], W, '#EDBB00'),
    '85': ('CELTA VIGO', r('#8AC3EE', '#1A5E9C'), [fx('e:1f41f', 'swarm', 12), fx('s:waves', 'sail', tint='#0E3E73')], W, '#E5254E'),
    '90': ('DEPORTIVO', st('#1B57C2', '#FFFFFF', 9), [fx('s:lighthouse', 'grow', side=1)], W, '#FFFFFF'),
    '3751': ('ELCHE', r('#19A04B', '#0A4D22'), [fx('e:1f334', 'grow', 3)], W, '#FFFFFF'),
    '88': ('ESPANYOL', st('#1F6FD1', '#FFFFFF', 9), [fx('e:1f99c', 'fly', 8)], W, '#FFFFFF'),
    '2922': ('GETAFE', r('#1E6FD9', '#062E66'), [fx('e:2708', 'fly', 4)], W, '#FFFFFF'),
    '1538': ('LEVANTE', st('#0B4FA3', '#B5121B', 9), [fx('e:1f438', 'hop', 6)], W, '#FFD400'),
    '99': ('MÁLAGA', st('#6EC1E4', '#FFFFFF', 9), [fx('e:2600', 'grow'), fx('e:1f334', 'bloom', 4)], W, '#FFFFFF'),
    '97': ('OSASUNA', r('#D91A21', '#5E0A0E'), [fx('e:1f402', 'run', 3)], W, '#0A2B6B'),
    '87': ('RACING SANTANDER', st('#1E8F3E', '#FFFFFF', 9), [fx('e:26f5', 'sail', 2), fx('s:waves', 'sail', tint='#0E5A2A')], W, '#FFFFFF'),
    '101': ('RAYO VALLECANO', {'t': 'sash', 'c': ['#F4F4F4', '#E30613']}, [fx('e:26a1', 'rain', 7)], W, '#E30613'),
    '244': ('REAL BETIS', st('#0BB363', '#FFFFFF', 9), [fx('e:1f33f', 'bloom', 10)], W, '#FFFFFF'),
    '86': ('REAL MADRID', r('#FFFFFF', '#C9B37E'), [fx('e:1f451', 'orbit', 5), fx('e:2728', 'bloom', 8)], '#1B1B3A', '#3E1F7A'),
    '89': ('REAL SOCIEDAD', st('#0067B1', '#FFFFFF', 9), [fx('e:1f41a', 'bloom', 8), fx('s:waves', 'sail', tint='#003E73')], W, '#FFFFFF'),
    '243': ('SEVILLA', r('#FFFFFF', '#E3E3E3'), [fx('e:1f34a', 'rain', 12)], '#C8102E', '#C8102E'),
    '94': ('VALENCIA', r('#3A3A3A', '#050505'), [fx('e:1f987', 'swarm', 12, ol=W, s=1.5)], W, '#EE7F00'),
    '102': ('VILLARREAL', r('#FFE667', '#E3B800'), [fx('s:submarine', 'sail', s=1.5), fx('s:waves', 'sail', tint='#005187')], '#0A2B6B', '#005187'),
    # ---- Serie A
    '103': ('AC MILAN', st('#E2001A', K, 9), [fx('e:1f608', 'bloom', 6, s=1.2)], W, '#FFFFFF'),
    '104': ('AS ROMA', r('#A6192E', '#4D0A14'), [fx('e:1f43a', 'roar')], W, '#F0BC42'),
    '105': ('ATALANTA', st('#1E71B8', K, 9), [fx('e:1f34e', 'rain', 12, tint='#F2C230')], W, '#F2C230'),
    '107': ('BOLOGNA', sp('#A21C26', '#1A2F48'), [fx('s:towers', 'grow', side=1)], W, '#FFFFFF'),
    '2925': ('CAGLIARI', sp('#A6192E', '#002350'), [fx('e:1f9a9', 'fly', 7, s=1.6)], W, '#FFFFFF'),
    '2572': ('COMO', r('#2F6BD6', '#0C2A66'), [fx('e:1f3d4', 'grow'), fx('e:26f5', 'sail', 1), fx('s:waves', 'sail', tint='#08224F')], W, '#FFFFFF'),
    '109': ('FIORENTINA', r('#6B2FA8', '#2A0D4D'), [fx('e:269c', 'bloom', 8, tint='#E2231A', ol=W)], W, '#FFFFFF'),
    '4057': ('FROSINONE', r('#FFD84D', '#E0A800'), [fx('e:1f424', 'fly', 9, s=1.4)], '#1A3D8F', '#1A3D8F'),
    '3263': ('GENOA', sp('#A6192E', '#0A2140'), [fx('e:1f985', 'flank', 2)], W, '#F2C230'),
    '110': ('INTER', st('#0068A8', K, 9), [fx('e:1f40d', 'flank', 2)], W, '#F2C230'),
    '111': ('JUVENTUS', st(K, '#FFFFFF', 9), [fx('e:1f993', 'run', 4)], W, '#FFFFFF'),
    '112': ('LAZIO', r('#87D8F7', '#2A7DB5'), [fx('e:1f985', 'roar')], W, '#FFFFFF'),
    '113': ('LECCE', st('#FFE500', '#D2152B', 9), [fx('e:1f43a', 'run', 3)], W, K),
    '4007': ('MONZA', sp('#E2001A', '#FFFFFF'), [fx('e:1f451', 'grow', tint='#B8BCC4')], W, '#FFFFFF'),
    '114': ('NAPOLI', r('#12A0D7', '#004B87'), [fx('e:1f30b', 'grow'), fx('e:1f355', 'rain', 10)], W, '#FFFFFF'),
    '115': ('PARMA', {'t': 'cross', 'c': ['#1A3D8F', '#FFD400']}, [fx('e:1f9c0', 'bloom', 8)], W, '#FFD400'),
    '3997': ('SASSUOLO', {'t': 'tiles', 'c': ['#00A752', K]}, [fx('e:2728', 'orbit', 7)], W, '#00A752'),
    '239': ('TORINO', r('#8A1E03', '#3D0D01'), [fx('e:1f402', 'roar')], W, '#FFFFFF'),
    '118': ('UDINESE', st('#FFFFFF', K, 11), [fx('e:1f993', 'flank', 2)], W, '#FFFFFF'),
    '17530': ('VENEZIA', r('#2F3A33', '#050505'), [fx('e:1f6f6', 'sail', 2, s=1.4), fx('s:waves', 'sail', tint='#00843D')], W, '#F37A1F'),
    # ---- Bundesliga
    '598': ('1. FC UNION BERLIN', r('#D4011D', '#5C0010'), [fx('e:2692', 'flank', 2)], W, '#FFD400'),
    '131': ('BAYER LEVERKUSEN', r('#E32221', '#3A0505'), [fx('e:2699', 'orbit', 5, s=1.7)], W, K),
    '132': ('FC BAYERN MÜNCHEN', r('#DC052D', '#6B0016'), [fx('s:rauten', 'grow'), fx('e:1f968', 'rain', 10)], W, '#0066B2'),
    '124': ('BORUSSIA DORTMUND', r('#FDE100', '#E0B000'), [fx('e:1f41d', 'swarm', 16)], K, K),
    '268': ('BORUSSIA MÖNCHENGLADBACH', r('#1A9E4B', '#0B4D24'), [fx('e:1f40e', 'run', 4)], W, '#FFFFFF'),
    '125': ('EINTRACHT FRANKFURT', r('#E1000F', '#1A0003'), [fx('e:1f985', 'roar')], W, '#FFFFFF'),
    '3841': ('FC AUGSBURG', sp('#BA3733', '#46793A'), [fx('s:pinecone', 'rain', 9, s=1.4)], W, '#FFFFFF'),
    '122': ('1. FC KÖLN', r('#ED1C24', '#6B0A0F'), [fx('e:1f410', 'hop', 3)], W, '#FFFFFF'),
    '127': ('HAMBURGER SV', r('#0A3EA8', '#04153D'), [fx('e:2693', 'rain', 8, tint=W, s=1.3), fx('s:waves', 'sail', tint='#021033')], W, '#FFFFFF'),
    '2950': ('1. FSV MAINZ 05', r('#ED1C24', '#6B0A0F'), [fx('e:1f3ad', 'bloom', 6), fx('e:1f389', 'rain', 8)], W, '#FFFFFF'),
    '11420': ('RB LEIPZIG', r('#FFFFFF', '#D5D9E0'), [fx('e:1f402', 'flank', 2)], '#0C2043', '#DD0741'),
    '126': ('SC FREIBURG', r('#D2122E', '#1A0306'), [fx('e:1f332', 'grow', 5)], W, '#FFFFFF'),
    '3307': ('SC PADERBORN 07', r('#0A50A1', '#04173D'), [fx('e:1f4a7', 'rain', 14)], W, '#FFFFFF'),
    '10388': ('SV ELVERSBERG', r('#2A2A2A', '#000000'), [fx('e:1f680', 'grow'), fx('e:2728', 'orbit', 6)], W, '#FFFFFF'),
    '133': ('SCHALKE 04', r('#004D9D', '#00163D'), [fx('e:26cf', 'flank', 2)], W, '#FFFFFF'),
    '7911': ('TSG HOFFENHEIM', r('#1961B5', '#062A5A'), [fx('e:1f33b', 'bloom', 8)], W, '#FFFFFF'),
    '134': ('VFB STUTTGART', {'t': 'band', 'c': ['#FFFFFF', '#E32219']}, [fx('e:1f98c', 'flank', 2)], '#E32219', '#E32219'),
    '137': ('WERDER BREMEN', r('#1D9053', '#0A4426'), [fx('s:musicians', 'stack'), fx('e:1f511', 'orbit', 5, tint='#E8E8E8')], W, '#FFFFFF'),
    # ---- Ligue 1
    '172': ('AJ AUXERRE', r('#FFFFFF', '#D6E2F2'), [fx('e:1f40c', 'run', 3, slow=1)], '#0F3D8F', '#0F3D8F'),
    '174': ('AS MONACO', sp('#E51B22', '#FFFFFF'), [fx('e:1f48e', 'rain', 10)], W, '#FFFFFF'),
    '7868': ('ANGERS SCO', st(K, '#FFFFFF', 9), [fx('e:1f3f0', 'grow')], W, '#FFFFFF'),
    '6997': ('STADE BRESTOIS', r('#E2001A', '#5E000B'), [fx('e:2693', 'flank', 2)], W, '#FFFFFF'),
    '3236': ('LE HAVRE AC', sp('#8CC8EC', '#0B2A63'), [fx('e:1f6a2', 'sail', 1), fx('s:waves', 'sail', tint='#06183B')], W, '#FFFFFF'),
    '2697': ('LE MANS FC', r('#D62B11', '#5E1205'), [fx('e:1f3c1', 'flank', 2), fx('e:1f3ce', 'run', 2)], W, '#FFD400'),
    '175': ('RC LENS', sp('#E51B22', '#FFD400'), [fx('e:26cf', 'rain', 8)], W, K),
    '166': ('LOSC LILLE', r('#E01E13', '#0B1F4D'), [fx('e:1f415', 'flank', 2)], W, '#FFFFFF'),
    '273': ('FC LORIENT', r('#F58113', '#8A3D00'), [fx('e:1f41f', 'swarm', 12)], W, K),
    '167': ('OLYMPIQUE LYONNAIS', r('#FFFFFF', '#D5DDEE'), [fx('e:1f981', 'roar')], '#14387F', '#DA0812'),
    '176': ('OLYMPIQUE DE MARSEILLE', r('#8FD1F0', '#1E7FB8'), [fx('e:2b50', 'orbit', 6), fx('s:waves', 'sail', tint='#0B4F7A')], W, '#FFFFFF'),
    '2502': ('OGC NICE', st('#E2001A', K, 9), [fx('e:1f985', 'fly', 5, s=1.6)], W, '#FFFFFF'),
    '6851': ('PARIS FC', r('#1B2A4A', '#070D1C'), [fx('e:1f950', 'rain', 10)], W, '#8FC8EE'),
    '160': ('PARIS SAINT-GERMAIN', r('#004170', '#001A33'), [fx('s:eiffel', 'grow', side=1), fx('e:269c', 'orbit', 6, tint=W)], W, '#DA291C'),
    '169': ('STADE RENNAIS', sp('#E2001A', K), [fx('e:1f95e', 'rain', 8)], W, '#FFFFFF'),
    '180': ('RC STRASBOURG', r('#0A5FD1', '#032A6B'), [fx('e:1f9a9', 'fly', 6, s=1.8)], W, '#FFFFFF'),
    '179': ('TOULOUSE FC', r('#6A1B9A', '#2A0846'), [fx('e:1f338', 'bloom', 10, tint='#C9A0F0')], W, '#FFFFFF'),
    '170': ('ESTAC TROYES', r('#1F5FD1', '#072A66'), [fx('e:1f3d8', 'grow')], W, '#FFFFFF'),
}

if __name__ == '__main__':
    out = {}
    for cid, (name, bg, fxs, ink, accent) in CLUBS.items():
        out[cid] = {'name': name, 'bg': bg, 'fx': fxs, 'ink': ink, 'accent': accent}
    p = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'season_clubs.json')
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(len(out), 'clubs ->', p)
