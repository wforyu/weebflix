# Pre-scraped Provider Data

This directory contains pre-scraped JSON data for all providers, updated every 6 hours by GitHub Actions.

These files are used by the WeebFlix Android app for instant home screen loading via the `GitHubDataFetcher` fallback chain.

## File Structure
- `samehadaku_home.json` — Samehadaku latest episodes, ongoing, popular
- `drakorkita_home.json` — DrakorKita episodes, movies, series
- `oppadrama_home.json` — OppaDrama episodes, drama korea/china, film korea, netflix

## Update Schedule
- Automated: Every 6 hours via GitHub Actions (`scrape-providers.yml`)
- Manual: Trigger workflow dispatch from GitHub Actions tab
