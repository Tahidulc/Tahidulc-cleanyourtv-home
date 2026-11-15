"""The players' flags for the studio line-ups: python3 -I tools/flags.py OUT_DIR

Fetches each country's flag from the open Twemoji set (Twitter, Inc and other contributors, CC-BY 4.0; the jdecked fork,
by jsDelivr) and saves it as OUT_DIR/<code>.svg under the three-letter code FotMob gives a player's country (FIFA's:
ENG, GER, DEN...), with a few ISO codes as well where FIFA's differ. England, Scotland and Wales have their own flags;
Northern Ireland has none in the set (no flag is drawn for it). Already fetched files are kept.
"""
import os
import sys
import time
import urllib.request

BASE = 'https://cdn.jsdelivr.net/gh/jdecked/twemoji@15.1.0/assets/svg/'
UK = {'ENG': '1f3f4-e0067-e0062-e0065-e006e-e0067-e007f', 'SCO': '1f3f4-e0067-e0062-e0073-e0063-e0074-e007f',
      'WAL': '1f3f4-e0067-e0062-e0077-e006c-e0073-e007f'}
# FIFA's codes (and a few others FotMob may use) to ISO 3166's two letters
CODES = {
    'AFG': 'AF', 'ALB': 'AL', 'ALG': 'DZ', 'ASA': 'AS', 'AND': 'AD', 'ANG': 'AO', 'AIA': 'AI', 'ATG': 'AG', 'ARG': 'AR',
    'ARM': 'AM', 'ARU': 'AW', 'AUS': 'AU', 'AUT': 'AT', 'AZE': 'AZ', 'BAH': 'BS', 'BHR': 'BH', 'BAN': 'BD', 'BRB': 'BB',
    'BLR': 'BY', 'BEL': 'BE', 'BLZ': 'BZ', 'BEN': 'BJ', 'BER': 'BM', 'BHU': 'BT', 'BOL': 'BO', 'BIH': 'BA', 'BOT': 'BW',
    'BRA': 'BR', 'VGB': 'VG', 'BRU': 'BN', 'BUL': 'BG', 'BFA': 'BF', 'BDI': 'BI', 'CAM': 'KH', 'CMR': 'CM', 'CAN': 'CA',
    'CPV': 'CV', 'CAY': 'KY', 'CTA': 'CF', 'CHA': 'TD', 'CHI': 'CL', 'CHN': 'CN', 'TPE': 'TW', 'COL': 'CO', 'COM': 'KM',
    'CGO': 'CG', 'COD': 'CD', 'COK': 'CK', 'CRC': 'CR', 'CRO': 'HR', 'CUB': 'CU', 'CUW': 'CW', 'CYP': 'CY', 'CZE': 'CZ',
    'DEN': 'DK', 'DJI': 'DJ', 'DMA': 'DM', 'DOM': 'DO', 'ECU': 'EC', 'EGY': 'EG', 'SLV': 'SV', 'EQG': 'GQ', 'ERI': 'ER',
    'EST': 'EE', 'SWZ': 'SZ', 'ETH': 'ET', 'FRO': 'FO', 'FIJ': 'FJ', 'FIN': 'FI', 'FRA': 'FR', 'GAB': 'GA', 'GAM': 'GM',
    'GEO': 'GE', 'GER': 'DE', 'GHA': 'GH', 'GIB': 'GI', 'GRE': 'GR', 'GRN': 'GD', 'GUM': 'GU', 'GUA': 'GT', 'GUI': 'GN',
    'GNB': 'GW', 'GUY': 'GY', 'HAI': 'HT', 'HON': 'HN', 'HKG': 'HK', 'HUN': 'HU', 'ISL': 'IS', 'IND': 'IN', 'IDN': 'ID',
    'IRN': 'IR', 'IRQ': 'IQ', 'ISR': 'IL', 'ITA': 'IT', 'CIV': 'CI', 'JAM': 'JM', 'JPN': 'JP', 'JOR': 'JO', 'KAZ': 'KZ',
    'KEN': 'KE', 'PRK': 'KP', 'KOR': 'KR', 'KVX': 'XK', 'KOS': 'XK', 'XKX': 'XK', 'KUW': 'KW', 'KGZ': 'KG', 'LAO': 'LA',
    'LVA': 'LV', 'LIB': 'LB', 'LES': 'LS', 'LBR': 'LR', 'LBY': 'LY', 'LIE': 'LI', 'LTU': 'LT', 'LUX': 'LU', 'MAC': 'MO',
    'MAD': 'MG', 'MWI': 'MW', 'MAS': 'MY', 'MDV': 'MV', 'MLI': 'ML', 'MLT': 'MT', 'MTN': 'MR', 'MRI': 'MU', 'MEX': 'MX',
    'MDA': 'MD', 'MNG': 'MN', 'MNE': 'ME', 'MSR': 'MS', 'MAR': 'MA', 'MOZ': 'MZ', 'MYA': 'MM', 'NAM': 'NA', 'NEP': 'NP',
    'NED': 'NL', 'NCL': 'NC', 'NZL': 'NZ', 'NCA': 'NI', 'NIG': 'NE', 'NGA': 'NG', 'MKD': 'MK', 'NOR': 'NO', 'OMA': 'OM',
    'PAK': 'PK', 'PLE': 'PS', 'PAN': 'PA', 'PNG': 'PG', 'PAR': 'PY', 'PER': 'PE', 'PHI': 'PH', 'POL': 'PL', 'POR': 'PT',
    'PUR': 'PR', 'QAT': 'QA', 'IRL': 'IE', 'ROU': 'RO', 'RUS': 'RU', 'RWA': 'RW', 'SKN': 'KN', 'LCA': 'LC', 'VIN': 'VC',
    'SAM': 'WS', 'SMR': 'SM', 'STP': 'ST', 'KSA': 'SA', 'SEN': 'SN', 'SRB': 'RS', 'SEY': 'SC', 'SLE': 'SL', 'SIN': 'SG',
    'SVK': 'SK', 'SVN': 'SI', 'SOL': 'SB', 'SOM': 'SO', 'RSA': 'ZA', 'SSD': 'SS', 'ESP': 'ES', 'SRI': 'LK', 'SDN': 'SD',
    'SUR': 'SR', 'SWE': 'SE', 'SUI': 'CH', 'SYR': 'SY', 'TAH': 'PF', 'TJK': 'TJ', 'TAN': 'TZ', 'THA': 'TH', 'TLS': 'TL',
    'TOG': 'TG', 'TGA': 'TO', 'TRI': 'TT', 'TUN': 'TN', 'TUR': 'TR', 'TKM': 'TM', 'TCA': 'TC', 'UGA': 'UG', 'UKR': 'UA',
    'UAE': 'AE', 'USA': 'US', 'URU': 'UY', 'VIR': 'VI', 'UZB': 'UZ', 'VAN': 'VU', 'VEN': 'VE', 'VIE': 'VN', 'YEM': 'YE',
    'ZAM': 'ZM', 'ZIM': 'ZW', 'GUF': 'GF', 'GLP': 'GP', 'MTQ': 'MQ', 'SXM': 'SX', 'REU': 'RE', 'MYT': 'YT', 'BES': 'BQ',
    # ISO's own three letters where FIFA's differ
    'DEU': 'DE', 'DNK': 'DK', 'GRC': 'GR', 'NLD': 'NL', 'PRT': 'PT', 'CHE': 'CH', 'HRV': 'HR', 'BGR': 'BG', 'ZAF': 'ZA',
    'DZA': 'DZ', 'AGO': 'AO', 'HTI': 'HT', 'HND': 'HN', 'IRL': 'IE', 'NIC': 'NI', 'NER': 'NE', 'PRY': 'PY', 'PHL': 'PH',
    'SAU': 'SA', 'SGP': 'SG', 'SLB': 'SB', 'CHL': 'CL', 'CRI': 'CR', 'CPV': 'CV', 'GIN': 'GN', 'GTM': 'GT', 'LBN': 'LB',
    'LSO': 'LS', 'MDG': 'MG', 'MYS': 'MY', 'MMR': 'MM', 'OMN': 'OM', 'TZA': 'TZ', 'TGO': 'TG', 'URY': 'UY', 'VNM': 'VN',
    'ZMB': 'ZM', 'ZWE': 'ZW', 'IDN': 'ID', 'IRN': 'IR', 'KWT': 'KW', 'LVA': 'LV', 'MNE': 'ME', 'SVK': 'SK', 'GMB': 'GM',
    'BFA': 'BF', 'TCD': 'TD', 'CAF': 'CF', 'GNQ': 'GQ', 'COG': 'CG', 'BHS': 'BS', 'BRN': 'BN', 'KHM': 'KH', 'TWN': 'TW',
}


def cp(two):
    return '-'.join('%x' % (0x1F1E6 + ord(ch) - ord('A')) for ch in two)


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    got = 0
    jobs = [(k, UK[k]) for k in UK] + [(k, cp(v)) for k, v in sorted(CODES.items())]
    for code, name in jobs:
        path = os.path.join(out, code + '.svg')
        if os.path.exists(path) and os.path.getsize(path) > 0:
            continue
        req = urllib.request.Request(BASE + name + '.svg', headers={'User-Agent': 'Mozilla/5.0 (flags for a TV overlay)'})
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                data = r.read()
            if not data.lstrip().startswith(b'<svg'):
                print('not a picture:', code)
                continue
            with open(path, 'wb') as f:
                f.write(data)
            got += 1
        except Exception as e:  # noqa: BLE001
            print('none for', code, e)
        time.sleep(0.05)
    print(got, 'fetched,', len(os.listdir(out)), 'in', out)


if __name__ == '__main__':
    main()
