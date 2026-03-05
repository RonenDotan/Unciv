"""
Downloads GIF candidates for each Unciv technology from Giphy.
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
import time

# ── Config ────────────────────────────────────────────────────────────────────

API_KEY = "jiekAaOVAMgsdfmIMxnoeoLfbTZgp29i"   # get free key at developers.giphy.com
GIFS_PER_TECH = 5                      # how many candidates to download per tech
OUTPUT_DIR = "gif_staging"
RATING = "g"                           # g / pg / pg-13 / r

# ── Custom search terms ────────────────────────────────────────────────────────
# For techs where the name alone gives bad results, override the search query.

SEARCH_OVERRIDES = {
    "Agriculture":        "farming harvest crops",
    "Animal Husbandry":   "animals farm livestock",
    "Archery":            "bow arrow archery",
    "The Wheel":          "wheel spinning",
    "Bronze Working":     "blacksmith forge metal",
    "Iron Working":       "blacksmith forge iron",
    "Metal Casting":      "metal casting molten",
    "Horseback Riding":   "horse riding gallop",
    "Drama and Poetry":   "theatre drama stage",
    "Civil Service":      "government politics",
    "Printing Press":     "printing press book",
    "Scientific Theory":  "science laboratory experiment",
    "Military Science":   "military strategy war",
    "Steam Power":        "steam engine industrial",
    "Replaceable Parts":  "factory assembly line",
    "Combined Arms":      "military combat battle",
    "Nuclear Fission":    "nuclear explosion atomic",
    "Nuclear Fusion":     "nuclear fusion reactor",
    "Advanced Ballistics":"missile launch ballistics",
    "Mobile Tactics":     "tank military vehicles",
    "Particle Physics":   "particle accelerator physics",
    "Future Tech":        "futuristic technology sci-fi",
    "Globalization":      "world globe earth",
    "Telecommunications": "satellite communication signal",
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

# ── Helpers ───────────────────────────────────────────────────────────────────

def search_giphy(query: str, limit: int) -> list[str]:
    """Returns list of GIF URLs from Giphy search."""
    params = urllib.parse.urlencode({
        "api_key": API_KEY,
        "q": query,
        "limit": limit,
        "rating": RATING,
        "lang": "en",
    })
    url = f"https://api.giphy.com/v1/gifs/search?{params}"
    try:
        with urllib.request.urlopen(url, timeout=10) as resp:
            data = json.loads(resp.read())
        return [item["images"]["original"]["url"] for item in data.get("data", [])]
    except Exception as e:
        print(f"  Giphy error: {e}")
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
    if API_KEY == "YOUR_GIPHY_API_KEY_HERE":
        print("ERROR: Set your Giphy API key in the API_KEY variable at the top of this script.")
        print("Get a free key at: https://developers.giphy.com")
        return

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    total = len(TECHS)

    for i, tech in enumerate(TECHS, 1):
        tech_dir = os.path.join(OUTPUT_DIR, tech)

        # Skip if already downloaded
        if os.path.isdir(tech_dir) and len(os.listdir(tech_dir)) >= GIFS_PER_TECH:
            print(f"[{i}/{total}] {tech} — already done, skipping")
            continue

        query = SEARCH_OVERRIDES.get(tech, tech)
        print(f"[{i}/{total}] {tech}  (searching: \"{query}\")")

        urls = search_giphy(query, GIFS_PER_TECH)
        if not urls:
            print(f"  No results found.")
            continue

        os.makedirs(tech_dir, exist_ok=True)
        downloaded = 0
        for j, url in enumerate(urls, 1):
            dest = os.path.join(tech_dir, f"{j}.gif")
            if download_file(url, dest):
                downloaded += 1
                print(f"  {j}.gif ✓")

        print(f"  → {downloaded}/{len(urls)} downloaded")
        time.sleep(0.25)  # be polite to the API

    print(f"\nDone! GIFs saved to: {os.path.abspath(OUTPUT_DIR)}/")
    print("Review them, then copy your picks to:")
    print("  android/assets/videos/TechResearched/{TechName}.gif")

if __name__ == "__main__":
    main()
