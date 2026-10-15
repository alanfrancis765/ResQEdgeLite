# Offline Lite Map Data Pack Guide

## Why This Exists

ResQ-Mesh Lite operates in offline disaster response environments where full vector tile maps (which can be hundreds of megabytes) are difficult to pre-provision or store. This lightweight offline basemap ("lite map pack") extracts key spatial features (water bodies, main/minor roads, settlements, and critical health/education amenities) into compact GeoJSON files totaling only a few megabytes.

---

## Overpass Query Template

Use [Overpass Turbo](https://overpass-turbo.eu/) to query and export GeoJSON data for your target disaster response areas.

```overpass
[out:json][timeout:90];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"](S,W,N,E);
  way["waterway"~"^(river|canal|stream)$"](S,W,N,E);
  nwr["natural"="water"](S,W,N,E);
  node["place"~"^(city|town|village|suburb|hamlet|locality)$"](S,W,N,E);
  nwr["amenity"~"^(hospital|clinic|school)$"](S,W,N,E);
);
out geom(S,W,N,E);
```

---

## Target Response Areas

Below are bounding boxes (`South, West, North, East`) and instantiated Overpass queries for key areas.

### 1. Kuttanad (`9.38,76.38,9.42,76.42`)
```overpass
[out:json][timeout:90];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"](9.38,76.38,9.42,76.42);
  way["waterway"~"^(river|canal|stream)$"](9.38,76.38,9.42,76.42);
  nwr["natural"="water"](9.38,76.38,9.42,76.42);
  node["place"~"^(city|town|village|suburb|hamlet|locality)$"](9.38,76.38,9.42,76.42);
  nwr["amenity"~"^(hospital|clinic|school)$"](9.38,76.38,9.42,76.42);
);
out geom(9.38,76.38,9.42,76.42);
```

### 2. Chengannur (`9.30,76.59,9.34,76.63`)
```overpass
[out:json][timeout:90];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"](9.30,76.59,9.34,76.63);
  way["waterway"~"^(river|canal|stream)$"](9.30,76.59,9.34,76.63);
  nwr["natural"="water"](9.30,76.59,9.34,76.63);
  node["place"~"^(city|town|village|suburb|hamlet|locality)$"](9.30,76.59,9.34,76.63);
  nwr["amenity"~"^(hospital|clinic|school)$"](9.30,76.59,9.34,76.63);
);
out geom(9.30,76.59,9.34,76.63);
```

### 3. Vaikom (`9.73,76.38,9.77,76.42`)
```overpass
[out:json][timeout:90];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"](9.73,76.38,9.77,76.42);
  way["waterway"~"^(river|canal|stream)$"](9.73,76.38,9.77,76.42);
  nwr["natural"="water"](9.73,76.38,9.77,76.42);
  node["place"~"^(city|town|village|suburb|hamlet|locality)$"](9.73,76.38,9.77,76.42);
  nwr["amenity"~"^(hospital|clinic|school)$"](9.73,76.38,9.77,76.42);
);
out geom(9.73,76.38,9.77,76.42);
```

### 4. Aluva (`10.09,76.33,10.13,76.37`)
```overpass
[out:json][timeout:90];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"](10.09,76.33,10.13,76.37);
  way["waterway"~"^(river|canal|stream)$"](10.09,76.33,10.13,76.37);
  nwr["natural"="water"](10.09,76.33,10.13,76.37);
  node["place"~"^(city|town|village|suburb|hamlet|locality)$"](10.09,76.33,10.13,76.37);
  nwr["amenity"~"^(hospital|clinic|school)$"](10.09,76.33,10.13,76.37);
);
out geom(10.09,76.33,10.13,76.37);
```

### 5. Chalakudy (`10.29,76.31,10.33,76.35`)
```overpass
[out:json][timeout:90];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"](10.29,76.31,10.33,76.35);
  way["waterway"~"^(river|canal|stream)$"](10.29,76.31,10.33,76.35);
  nwr["natural"="water"](10.29,76.31,10.33,76.35);
  node["place"~"^(city|town|village|suburb|hamlet|locality)$"](10.29,76.31,10.33,76.35);
  nwr["amenity"~"^(hospital|clinic|school)$"](10.29,76.31,10.33,76.35);
);
out geom(10.29,76.31,10.33,76.35);
```

### 6. Paravur (`10.13,76.20,10.17,76.24`)
```overpass
[out:json][timeout:90];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"](10.13,76.20,10.17,76.24);
  way["waterway"~"^(river|canal|stream)$"](10.13,76.20,10.17,76.24);
  nwr["natural"="water"](10.13,76.20,10.17,76.24);
  node["place"~"^(city|town|village|suburb|hamlet|locality)$"](10.13,76.20,10.17,76.24);
  nwr["amenity"~"^(hospital|clinic|school)$"](10.13,76.20,10.17,76.24);
);
out geom(10.13,76.20,10.17,76.24);
```

### 7. Ranni (`9.36,76.76,9.40,76.80`)
```overpass
[out:json][timeout:90];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential)$"](9.36,76.76,9.40,76.80);
  way["waterway"~"^(river|canal|stream)$"](9.36,76.76,9.40,76.80);
  nwr["natural"="water"](9.36,76.76,9.40,76.80);
  node["place"~"^(city|town|village|suburb|hamlet|locality)$"](9.36,76.76,9.40,76.80);
  nwr["amenity"~"^(hospital|clinic|school)$"](9.36,76.76,9.40,76.80);
);
out geom(9.36,76.76,9.40,76.80);
```

---

## How to Export

1. Open [overpass-turbo.eu](https://overpass-turbo.eu/).
2. Paste the query for your desired region in the left editor panel.
3. Click **Run** to execute the query.
4. Click **Export** in the top navigation bar.
5. Select **download as GeoJSON**.
6. Save the downloaded file to `app/src/main/assets/lite_map/<area>.geojson` (e.g., `kuttanad.geojson`).

---

## Checking File Sizes

- Keep each GeoJSON file under **3 MB** to ensure smooth rendering and rapid loading on budget devices.
- If a file exceeds 3 MB, narrow the bounding box `(S,W,N,E)` or remove high-density residential footways.

---

## Attribution and Licensing

Map data (c) [OpenStreetMap contributors](https://www.openstreetmap.org/copyright). Open Database License (ODbL).
