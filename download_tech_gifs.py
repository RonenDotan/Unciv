"""
Downloads GIF candidates for each Unciv technology from Giphy.
Filters out GIFs shorter than MIN_DURATION_SECONDS.
Results go to: gif_staging/{TechName}/{1..N}.gif
Review them and copy the ones you like to:
  android/assets/videos/TechResearched/{TechName}.gif

Usage:
    py download_tech_gifs.py
"""

import urllib.request
import urllib.parse
import json
import os
import struct
import time

# ── Config ────────────────────────────────────────────────────────────────────

API_KEY = "jiekAaOVAMgsdfmIMxnoeoLfbTZgp29i"
GIFS_PER_TECH = 10          # keepers per tech (after duration filtering)
FETCH_PER_SEARCH = 25      # how many to fetch from Giphy per query (more = better filter pool)
MIN_DURATION_SECONDS = 3   # discard GIFs shorter than this (Giphy GIFs rarely exceed 5s)
OUTPUT_DIR = "gif_staging"
RATING = "pg-13"           # g / pg / pg-13 / r

# ── Custom search terms ───────────────────────────────────────────────────────
# Queries are kept factual/documentary — no "funny", "meme", "cartoon" etc.

SEARCH_OVERRIDES = {
    "Agriculture":        "farming harvest wheat field real",
    "Animal Husbandry":   "livestock cattle farm animals real",
    "Archery":            "archery bow arrow real",
    "The Wheel":          "ancient wheel pottery spinning real",
    "Bronze Working":     "blacksmith forge bronze metal",
    "Iron Working":       "blacksmith forge iron smelting",
    "Metal Casting":      "molten metal casting foundry",
    "Horseback Riding":   "horse riding gallop real",
    "Drama and Poetry":   "theatre stage performance drama",
    "Civil Service":      "government parliament political",
    "Printing Press":     "printing press old book",
    "Scientific Theory":  "science laboratory experiment real",
    "Military Science":   "military strategy army war real",
    "Steam Power":        "steam engine locomotive industrial",
    "Replaceable Parts":  "factory assembly line industrial",
    "Combined Arms":      "military tank battle combat real",
    "Nuclear Fission":    "nuclear explosion atomic bomb",
    "Nuclear Fusion":     "nuclear fusion plasma reactor",
    "Advanced Ballistics":"missile launch military rocket",
    "Mobile Tactics":     "military tank armored vehicles",
    "Particle Physics":   "particle accelerator cern physics",
    "Future Tech":        "futuristic technology robot sci-fi",
    "Globalization":      "world globe earth satellite view",
    "Telecommunications": "satellite dish signal communication",
    "Masonry":            "stone masonry construction ancient",
    "Construction":       "construction building crane",
    "Gunpowder":          "cannon explosion gunpowder fire",
    "Navigation":         "ship ocean sailing navigation",
    "Astronomy":          "telescope stars space observatory",
    "Industrialization":  "industrial revolution factory smoke",
    "Electricity":        "electricity lightning tesla coil",
    "Dynamite":           "explosion demolition dynamite",
    "Flight":             "airplane first flight wright brothers",
    "Railroads":          "steam train locomotive railroad",
    "Nuclear Fission":    "nuclear explosion mushroom cloud",
    "Rocketry":           "rocket launch space real",
    "Satellites":         "satellite space launch orbit",
    "Stealth":            "stealth fighter jet military",
}

# ── Tech list ─────────────────────────────────────────────────────────────────

TECHS = [
    "Agriculture", "Pottery", "Animal Husbandry", "Archery", "Mining",
    "Sailing", "Calendar", "Writing", "Trapping", "The Wheel", "Masonry",
    "Bronze Working", "Optics", "Horseback Riding", "Mathematics", "Construction",
    "Philosophy", "Drama and Poetry", "Currency", "Engineering", "Iron Working",
    "Theology", "Civil Service", "Guilds", "Metal Casting", "Compass", "Education",
    "Chivalry", "Machinery", "Physics", "Steel", "Astronomy", "Acoustics",
    "Banking", "Printing Press", "Gunpowder", "Navigation", "Architecture",
    "Economics", "Metallurgy", "Chemistry", "Archaeology", "Scientific Theory",
    "Industrialization", "Rifling", "Military Science", "Fertilizer", "Biology",
    "Electricity", "Steam Power", "Dynamite", "Refrigeration", "Radio",
    "Replaceable Parts", "Flight", "Railroads", "Plastics", "Electronics",
    "Ballistics", "Combustion", "Pharmaceuticals", "Atomic Theory", "Radar",
    "Combined Arms", "Ecology", "Nuclear Fission", "Rocketry", "Computers",
    "Telecommunications", "Mobile Tactics", "Advanced Ballistics", "Satellites",
    "Robotics", "Lasers", "Globalization", "Particle Physics", "Nuclear Fusion",
    "Nanotechnology", "Stealth", "Future Tech",
]

# ── GIF duration parser ───────────────────────────────────────────────────────

def get_gif_duration(path: str) -> float | None:
    """
    Parse GIF binary to sum all frame delays.
    Returns total duration in seconds, or None if the file can't be parsed.
    """
    try:
        with open(path, "rb") as f:
            data = f.read()

        if data[:6] not in (b"GIF87a", b"GIF89a"):
            return None

        total_cs = 0  # centiseconds
        i = 6

        # Logical Screen Descriptor
        packed = data[i + 2]
        has_gct = (packed & 0x80) != 0
        gct_size = 1 << ((packed & 0x07) + 1)
        i += 7
        if has_gct:
            i += gct_size * 3

        while i < len(data):
            block = data[i]
            i += 1

            if block == 0x3B:  # Trailer
                break

            elif block == 0x2C:  # Image Descriptor
                packed = data[i + 8]
                has_lct = (packed & 0x80) != 0
                lct_size = 1 << ((packed & 0x07) + 1)
                i += 9
                if has_lct:
                    i += lct_size * 3
                i += 1  # LZW min code size
                # Skip sub-blocks
                while i < len(data):
                    sz = data[i]; i += 1
                    if sz == 0:
                        break
                    i += sz

            elif block == 0x21:  # Extension
                ext = data[i]; i += 1
                if ext == 0xF9:  # Graphic Control Extension
                    sz = data[i]; i += 1  # block size (always 4)
                    delay = struct.unpack_from("<H", data, i + 1)[0]
                    total_cs += delay if delay > 0 else 10  # default 0.1s
                    i += sz
                    i += 1  # block terminator
                else:
                    # Skip sub-blocks
                    while i < len(data):
                        sz = data[i]; i += 1
                        if sz == 0:
                            break
                        i += sz

        return total_cs / 100.0

    except Exception:
        return None

# ── Helpers ───────────────────────────────────────────────────────────────────

def search_giphy(query: str, limit: int) -> list[str]:
    params = urllib.parse.urlencode({
        "api_key": API_KEY,
        "q":       query,
        "limit":   limit,
        "rating":  RATING,
        "lang":    "en",
    })
    url = f"https://api.giphy.com/v1/gifs/search?{params}"
    for attempt in range(5):
        try:
            with urllib.request.urlopen(url, timeout=10) as resp:
                data = json.loads(resp.read())
            return [item["images"]["original"]["url"] for item in data.get("data", [])]
        except urllib.error.HTTPError as e:
            if e.code == 429:
                wait = 10 * (2 ** attempt)
                print(f"  Rate limited, waiting {wait}s...")
                time.sleep(wait)
            else:
                print(f"  Giphy error: {e}")
                return []
        except Exception as e:
            print(f"  Giphy error: {e}")
            return []
    print(f"  Giving up after 5 retries.")
    return []

def download_file(url: str, dest: str) -> bool:
    try:
        urllib.request.urlretrieve(url, dest)
        return True
    except Exception as e:
        print(f"  Download error: {e}")
        return False

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    total = len(TECHS)

    for i, tech in enumerate(TECHS, 1):
        tech_dir = os.path.join(OUTPUT_DIR, tech)

        # Skip if we already have enough keepers
        existing = len(os.listdir(tech_dir)) if os.path.isdir(tech_dir) else 0
        if existing >= GIFS_PER_TECH:
            print(f"[{i}/{total}] {tech} — already done ({existing} files), skipping")
            continue

        query = SEARCH_OVERRIDES.get(tech, tech)
        print(f"[{i}/{total}] {tech}  (searching: \"{query}\")")

        urls = search_giphy(query, FETCH_PER_SEARCH)
        if not urls:
            print(f"  No results.")
            continue

        os.makedirs(tech_dir, exist_ok=True)
        keepers = existing
        checked = 0

        for url in urls:
            if keepers >= GIFS_PER_TECH:
                break

            tmp = os.path.join(tech_dir, f"_tmp.gif")
            if not download_file(url, tmp):
                continue

            checked += 1
            duration = get_gif_duration(tmp)

            if duration is None:
                os.remove(tmp)
                print(f"  skip — could not parse")
                continue

            if duration < MIN_DURATION_SECONDS:
                os.remove(tmp)
                print(f"  skip — {duration:.1f}s (too short)")
                continue

            keepers += 1
            final = os.path.join(tech_dir, f"{keepers}.gif")
            os.rename(tmp, final)
            print(f"  {keepers}.gif OK  ({duration:.1f}s)")

        if os.path.exists(tmp := os.path.join(tech_dir, "_tmp.gif")):
            os.remove(tmp)

        print(f"  >> {keepers} keepers from {checked} checked")
        time.sleep(1.5)

    print(f"\nDone! GIFs saved to: {os.path.abspath(OUTPUT_DIR)}/")
    print("Review them, then copy your picks to:")
    print("  android/assets/videos/TechResearched/{{TechName}}.gif")

if __name__ == "__main__":
    main()
