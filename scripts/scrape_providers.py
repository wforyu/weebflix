#!/usr/bin/env python3
"""
Pre-scrape all providers and save as JSON for fast app loading.
Runs via GitHub Actions every 6 hours.
"""

import json
import time
import sys
import os
import requests
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed

DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data")

PROVIDERS = {
    "samehadaku": {
        "base_url": "https://v2.samehadaku.how",
        "home_path": "/",
        "latest_path": "/page/{page}/",
        "ongoing_path": "/status/ongoing/page/{page}/",
        "popular_path": "/popular/page/{page}/",
    },
    "drakorkita": {
        "base_url": "https://drakor.kita.mobi",
        "home_path": "/",
    },
    "oppadrama": {
        "base_url": "http://45.11.57.192",
        "home_path": "/?verify_human=1",
        "drama_korea_path": "/series/?country%5B%5D=south-korea&type=Drama&order=update&verify_human=1",
        "drama_china_path": "/series/?country%5B%5D=china&type=Drama&order=update&verify_human=1",
        "film_korea_path": "/series/?country%5B%5D=south-korea&type=Movie&order=update&verify_human=1",
        "netflix_path": "/network/netflix/?verify_human=1",
    },
}

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "id-ID,id;q=0.9,en;q=0.8",
}


def fetch_html(url, cookies=None):
    """Fetch HTML from URL with optional cookies."""
    try:
        resp = requests.get(url, headers=HEADERS, cookies=cookies, timeout=30, allow_redirects=True)
        if resp.status_code == 200:
            return resp.text, resp.cookies.get_dict()
        print(f"  [WARN] {url} returned {resp.status_code}")
        return None, cookies or {}
    except Exception as e:
        print(f"  [ERROR] {url}: {e}")
        return None, cookies or {}


def parse_cards(html):
    """Parse article.bs .bsx cards from HTML."""
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(html, "html.parser")
    items = []
    for article in soup.select("article.bs"):
        try:
            bsx = article.select_one(".bsx")
            if not bsx:
                continue
            a = bsx.select_one("a[href]")
            if not a:
                continue
            href = a.get("href", "")
            title_el = a.select_one("h2[itemprop='headline']") or a.select_one(".tt h2")
            title = title_el.get_text(strip=True) if title_el else a.get("title", "")
            img = a.select_one(".limit img.ts-post-image") or a.select_one("img.ts-post-image")
            image_url = ""
            if img:
                image_url = img.get("src", "") or img.get("data-src", "")
            type_el = a.select_one(".limit .typez")
            type_text = type_el.get_text(strip=True) if type_el else ""
            ep_el = a.select_one(".limit .epx")
            ep_text = ep_el.get_text(strip=True) if ep_el else ""
            if title:
                items.append({
                    "title": title,
                    "url": href,
                    "imageUrl": image_url,
                    "episode": ep_text,
                    "type": type_text,
                    "status": "",
                    "score": "",
                    "studio": "",
                    "season": "",
                    "synopsis": "",
                    "totalEpisodes": "",
                    "genres": [],
                    "latestUpdate": "",
                })
        except Exception as e:
            print(f"  [WARN] parse error: {e}")
    return items


def scrape_samehadaku():
    """Scrape Samehadaku home content."""
    print("[samehadaku] Scraping...")
    config = PROVIDERS["samehadaku"]
    base = config["base_url"]

    cookies = {}
    html, cookies = fetch_html(base, cookies)
    if not html:
        return {"hero": [], "latest": [], "cat1": [], "cat2": [], "cat3": [], "cat4": []}

    latest = parse_cards(html)

    ongoing_html, cookies = fetch_html(f"{base}/status/ongoing/", cookies)
    ongoing = parse_cards(ongoing_html) if ongoing_html else []

    popular_html, cookies = fetch_html(f"{base}/popular/", cookies)
    popular = parse_cards(popular_html) if popular_html else []

    hero = latest[:10] if latest else []
    print(f"[samehadaku] Done: hero={len(hero)}, latest={len(latest)}, ongoing={len(ongoing)}, popular={len(popular)}")
    return {"hero": hero, "latest": latest, "cat1": ongoing, "cat2": popular, "cat3": [], "cat4": []}


def scrape_drakorkita():
    """Scrape DrakorKita home content."""
    print("[drakorkita] Scraping...")
    config = PROVIDERS["drakorkita"]
    base = config["base_url"]

    html, cookies = fetch_html(base)
    if not html:
        return {"hero": [], "latest": [], "cat1": [], "cat2": [], "cat3": [], "cat4": []}

    from bs4 import BeautifulSoup
    soup = BeautifulSoup(html, "html.parser")

    episodes = parse_cards(html)

    movies_html, _ = fetch_html(f"{base}/series/?type=Movie&order=update")
    movies = parse_cards(movies_html) if movies_html else []

    series_html, _ = fetch_html(f"{base}/series/?type=TV+Show&order=update")
    series = parse_cards(series_html) if series_html else []

    hero = episodes[:10] if episodes else []
    print(f"[drakorkita] Done: hero={len(hero)}, episodes={len(episodes)}, movies={len(movies)}, series={len(series)}")
    return {"hero": hero, "latest": episodes, "cat1": movies, "cat2": series, "cat3": [], "cat4": []}


def scrape_oppadrama():
    """Scrape OppaDrama home content."""
    print("[oppadrama] Scraping...")
    config = PROVIDERS["oppadrama"]
    base = config["base_url"]

    cookies = {}
    html, cookies = fetch_html(f"{base}/?verify_human=1")
    if not html:
        return {"hero": [], "latest": [], "cat1": [], "cat2": [], "cat3": [], "cat4": []}

    episodes = parse_cards(html)

    dk_html, cookies = fetch_html(f"{base}{config['drama_korea_path']}", cookies)
    drama_korea = parse_cards(dk_html) if dk_html else []

    dc_html, cookies = fetch_html(f"{base}{config['drama_china_path']}", cookies)
    drama_china = parse_cards(dc_html) if dc_html else []

    fk_html, cookies = fetch_html(f"{base}{config['film_korea_path']}", cookies)
    film_korea = parse_cards(fk_html) if fk_html else []

    nf_html, cookies = fetch_html(f"{base}{config['netflix_path']}", cookies)
    netflix = parse_cards(nf_html) if nf_html else []

    hero = episodes[:10] if episodes else []
    print(f"[oppadrama] Done: hero={len(hero)}, episodes={len(episodes)}, dk={len(drama_korea)}, dc={len(drama_china)}, fk={len(film_korea)}, nf={len(netflix)}")
    return {"hero": hero, "latest": episodes, "cat1": drama_korea, "cat2": drama_china, "cat3": film_korea, "cat4": netflix}


def save_json(provider_id, data):
    """Save scraped data as JSON."""
    os.makedirs(DATA_DIR, exist_ok=True)
    data["timestamp"] = int(time.time() * 1000)
    filepath = os.path.join(DATA_DIR, f"{provider_id}_home.json")
    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    size_kb = os.path.getsize(filepath) / 1024
    print(f"  Saved {filepath} ({size_kb:.1f} KB)")


def main():
    print(f"=== Pre-scrape started at {datetime.utcnow().isoformat()} ===")
    print(f"Output dir: {DATA_DIR}")

    scrapers = {
        "samehadaku": scrape_samehadaku,
        "drakorkita": scrape_drakorkita,
        "oppadrama": scrape_oppadrama,
    }

    with ThreadPoolExecutor(max_workers=3) as executor:
        futures = {executor.submit(fn): pid for pid, fn in scrapers.items()}
        for future in as_completed(futures):
            pid = futures[future]
            try:
                data = future.result()
                save_json(pid, data)
            except Exception as e:
                print(f"[ERROR] {pid}: {e}")

    print(f"=== Pre-scrape finished at {datetime.utcnow().isoformat()} ===")


if __name__ == "__main__":
    main()
