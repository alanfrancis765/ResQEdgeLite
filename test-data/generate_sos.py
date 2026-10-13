import random
import os
import time

CENTERS = [
    ("Kuttanad", 9.40, 76.40),
    ("Chengannur", 9.32, 76.61),
    ("Aluva", 10.11, 76.35),
    ("Chalakudy", 10.31, 76.33),
    ("Ranni", 9.38, 76.78),
    ("Vaikom", 9.75, 76.40)
]

def urgency_for(medical_flags, water_level):
    u = 1
    if (medical_flags & 1) != 0:
        u += 2
    vulnerable = 2 | 4 | 8
    if (medical_flags & vulnerable) != 0:
        u += 1
    if water_level >= 3:
        u += 1
    return min(u, 5)

def generate_csv(filepaths):
    header = 'packetId,senderId,latitude,longitude,accuracyM,headcount,medicalFlags,waterLevel,urgency,timestamp,ttl,hopCount,area'
    rows = [header]
    random.seed(42)
    now = int(time.time())

    for i in range(1, 201):
        pid = 1000000000 + i
        sid = 500000 + random.randint(1, 100)

        area, center_lat, center_lon = random.choice(CENTERS)
        lat = round(center_lat + random.uniform(-0.0006, 0.0006), 6)
        lon = round(center_lon + random.uniform(-0.0006, 0.0006), 6)

        acc = random.randint(5, 25)
        hc = random.randint(1, 8)
        mf = random.choice([0, 1, 2, 3, 4, 5, 8, 9, 16, 17])
        wl = random.randint(0, 4)
        urg = urgency_for(mf, wl)
        ts = now - (i * 180)

        hop_count = random.choice([0, 0, 1, 1, 2, 3])
        ttl = max(0, 7 - hop_count)

        rows.append(f'{pid},{sid},{lat},{lon},{acc},{hc},{mf},{wl},{urg},{ts},{ttl},{hop_count},{area}')

    content = '\n'.join(rows)
    for path in filepaths:
        try:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f'Successfully generated {path}')
        except Exception as e:
            print(f'Warning writing {path}: {e}')

if __name__ == '__main__':
    generate_csv([
        'test-data/synthetic_sos_200.csv',
        'android/app/src/main/assets/synthetic_sos_200.csv'
    ])
