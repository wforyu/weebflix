#!/usr/bin/env python3
"""
Pre-scrape all providers and save as JSON for fast app loading.
Runs via GitHub Actions every 6 hours.

Card structures (verified against live sites, 2026-07):
- Samehadaku latest : ul > li[itemscope] > h2.entry-title a
- Samehadaku grids  : .animposx a (ongoing=/daftar-anime-2/, popular=?order=popular)
- DrakorKita        : .bungkus (link from a[href*='detail/'], img.poster)
- OppaDrama         : article.bs .bsx
"""

import json
import time
import os
import requests
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed

DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data")

PROVIDERS = {
    "samehadaku": {
        "base_url": "https://v2.samehadaku.how",
        "home_path": "/",
        "ongoing_path": "/daftar-anime-2/",
        "popular_path": "/daftar-anime-2/?order=popular",
    },
    "drakorkita": {
        "base_url": "https://drakor.kita.mobi",
        "home_path": "/",
        "movies_path": "/all?media_type=movie",
        "series_path": "/all?media_type=tv",
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


def make_item(title, url, image_url, episode="", type_text=""):
    return {
        "title": title,
        "url": url,
        "imageUrl": image_url,
        "episode": episode,
        "type": type_text,
        "status": "",
        "score": "",
        "studio": "",
        "season": "",
        "synopsis": "",
        "totalEpisodes": "",
        "genres": [],
        "latestUpdate": "",
    }


def parse_samehadaku_latest(html):
    """Home latest episodes: ul > li[itemscope] > h2.entry-title a."""
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(html, "html.parser")
    items = []
    for li in soup.select("ul > li[itemscope]"):
        try:
            a = li.select_one("h2.entry-title a")
            if not a:
                continue
            href = a.get("href", "")
            title = a.get_text(strip=True)
            img = li.select_one("img.npws") or li.select_one("img")
            image_url = img.get("src", "") if img else ""
            if title and href:
                items.append(make_item(title, href, image_url))
        except Exception as e:
            print(f"  [WARN] samehadaku latest parse error: {e}")
    return items


def parse_samehadaku_grid(html):
    """Ongoing/popular grids: .animposx a."""
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(html, "html.parser")
    items = []
    for card in soup.select(".animposx"):
        try:
            a = card.select_one("a[href]")
            if not a:
                continue
            href = a.get("href", "")
            title_el = a.select_one("h2")
            title = title_el.get_text(strip=True) if title_el else a.get("title", "")
            img = card.select_one("img")
            image_url = img.get("src", "") if img else ""
            if title and href:
                items.append(make_item(title, href, image_url))
        except Exception as e:
            print(f"  [WARN] samehadaku grid parse error: {e}")
    return items


def parse_drakorkita(html, base):
    """.bungkus cards: title = first text node of .titit, url = a[href*='detail/']."""
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(html, "html.parser")
    items = []
    for card in soup.select(".bungkus"):
        try:
            link = None
            parent = card.parent
            if parent is not None:
                parent = parent.parent
            if parent is not None:
                link = parent.select_one("a[href*='detail/']")
            if link is None:
                link = card.select_one("a[href*='detail/']")
            if link is None:
                continue
            href = link.get("href", "")
            if href.startswith("http"):
                url = href
            else:
                url = base + ("/" + href if not href.startswith("/") else href)
            titit = card.select_one(".titit")
            title = ""
            if titit:
                for child in titit.contents:
                    if child is not None and getattr(child, "strip", None) and child.strip():
                        title = child.strip()
                        break
                if not title:
                    title = titit.get_text(strip=True)
            if not title:
                title = link.get("title", "")
            img = card.select_one("img.poster")
            image_url = img.get("src", "") if img else ""
            if not image_url and img:
                image_url = img.get("data-src", "")
            ep_el = card.select_one(".rate") or card.select_one(".type")
            ep_text = ep_el.get_text(strip=True) if ep_el else ""
            type_el = card.select_one(".type")
            type_class = type_el.get("class", []) if type_el else []
            type_text = "Movie" if type_class and "Movie" in type_class else ("TV" if type_class else "")
            if title and url:
                items.append(make_item(title, url, image_url, ep_text, type_text))
        except Exception as e:
            print(f"  [WARN] drakorkita parse error: {e}")
    return items


def parse_oppadrama(html):
    """article.bs .bsx cards."""
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
                items.append(make_item(title, href, image_url, ep_text, type_text))
        except Exception as e:
            print(f"  [WARN] oppadrama parse error: {e}")
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

    latest = parse_samehadaku_latest(html)

    ongoing_html, cookies = fetch_html(base + config["ongoing_path"], cookies)
    ongoing = parse_samehadaku_grid(ongoing_html) if ongoing_html else []

    popular_html, cookies = fetch_html(base + config["popular_path"], cookies)
    popular = parse_samehadaku_grid(popular_html) if popular_html else []

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
    episodes = []
    heading = None
    for h in soup.select(".col-lg-8 h4.heading1"):
        if "eps terbaru" in h.get_text(strip=True).lower():
            heading = h
            break
    if heading is not None:
        row = heading.find_next_sibling()
        if row is not None:
            episodes = parse_drakorkita(str(row), base)

    movies_html, _ = fetch_html(base + config["movies_path"], cookies)
    movies = parse_drakorkita(movies_html, base) if movies_html else []

    series_html, _ = fetch_html(base + config["series_path"], cookies)
    series = parse_drakorkita(series_html, base) if series_html else []

    hero = episodes[:10] if episodes else []
    print(f"[drakorkita] Done: hero={len(hero)}, episodes={len(episodes)}, movies={len(movies)}, series={len(series)}")
    return {"hero": hero, "latest": episodes, "cat1": movies, "cat2": series, "cat3": [], "cat4": []}


def scrape_oppadrama():
    """Scrape OppaDrama home content."""
    print("[oppadrama] Scraping...")
    config = PROVIDERS["oppadrama"]
    base = config["base_url"]

    cookies = {}
    html, cookies = fetch_html(base + config["home_path"], cookies)
    if not html:
        return {"hero": [], "latest": [], "cat1": [], "cat2": [], "cat3": [], "cat4": []}

    episodes = parse_oppadrama(html)

    dk_html, cookies = fetch_html(base + config["drama_korea_path"], cookies)
    drama_korea = parse_oppadrama(dk_html) if dk_html else []

    dc_html, cookies = fetch_html(base + config["drama_china_path"], cookies)
    drama_china = parse_oppadrama(dc_html) if dc_html else []

    fk_html, cookies = fetch_html(base + config["film_korea_path"], cookies)
    film_korea = parse_oppadrama(fk_html) if fk_html else []

    nf_html, cookies = fetch_html(base + config["netflix_path"], cookies)
    netflix = parse_oppadrama(nf_html) if nf_html else []

    hero = episodes[:10] if episodes else []
    print(f"[oppadrama] Done: hero={len(hero)}, episodes={len(episodes)}, dk={len(drama_korea)}, dc={len(drama_china)}, fk={len(film_korea)}, nf={len(netflix)}")
    return {"hero": hero, "latest": episodes, "cat1": drama_korea, "cat2": drama_china, "cat3": film_korea, "cat4": netflix}


def save_json(provider_id, data):
    """Save scraped data as JSON."""
    total = sum(len(v) for k, v in data.items() if isinstance(v, list))
    if not data.get("latest") or total == 0:
        print(f"  [SKIP] {provider_id}: scraped data empty (latest={len(data.get('latest', []))}), keeping existing file")
        return
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
