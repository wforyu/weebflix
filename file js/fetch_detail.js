const https = require('https');

const BASE = 'https://xdrakor33.nicewap.sbs';

function fetch(url) {
  return new Promise((resolve, reject) => {
    const urlObj = new URL(url);
    https.get({
      hostname: urlObj.hostname,
      path: urlObj.pathname + urlObj.search,
      headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml',
      },
      timeout: 15000,
    }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => resolve(data));
    }).on('error', reject);
  });
}

async function main() {
  // Fetch a detail page
  console.log('Fetching detail page...');
  const html = await fetch(`${BASE}/detail/the-husband-2026-v2e8/`);
  console.log(`Detail page: ${html.length} bytes`);

  // Find ALL scripts (inline and external)
  const scriptRegex = /<script[^>]*(?:src=["']([^"']+)["'])?[^>]*>([\s\S]*?)<\/script>/gi;
  let m;
  while ((m = scriptRegex.exec(html)) !== null) {
    const src = m[1];
    const content = m[2].trim();
    if (src) {
      console.log(`\n[External script] ${src}`);
    }
    if (content.length > 5) {
      console.log(`\n[Inline script] (${content.length} chars)`);
      if (content.length > 2000) {
        console.log(content.substring(0, 2000) + '\n... [truncated]');
      } else {
        console.log(content);
      }
    }
  }

  // Find ALL loadEpisode occurrences
  let idx = 0;
  while ((idx = html.indexOf('loadEpisode', idx)) !== -1) {
    console.log(`\n=== loadEpisode at position ${idx} ===`);
    console.log(html.substring(Math.max(0, idx - 150), idx + 200));
    idx += 11;
  }

  // Find server_lists
  idx = 0;
  while ((idx = html.indexOf('server_list', idx)) !== -1) {
    console.log(`\n=== server_list at position ${idx} ===`);
    console.log(html.substring(Math.max(0, idx - 100), idx + 300));
    idx += 11;
  }

  // Find c_api_host
  idx = 0;
  while ((idx = html.indexOf('c_api', idx)) !== -1) {
    console.log(`\n=== c_api at position ${idx} ===`);
    console.log(html.substring(Math.max(0, idx - 100), idx + 300));
    idx += 6;
  }

  // Find episode.php
  idx = 0;
  while ((idx = html.indexOf('episode.php', idx)) !== -1) {
    console.log(`\n=== episode.php at position ${idx} ===`);
    console.log(html.substring(Math.max(0, idx - 150), idx + 200));
    idx += 11;
  }

  // Find initEpisodeList
  idx = 0;
  while ((idx = html.indexOf('initEpisodeList', idx)) !== -1) {
    console.log(`\n=== initEpisodeList at position ${idx} ===`);
    console.log(html.substring(Math.max(0, idx - 150), idx + 300));
    idx += 15;
  }

  // Find all script src
  const srcRegex = /src=["']([^"']+\.js[^"']*)["']/gi;
  console.log('\n=== All JS sources ===');
  while ((m = srcRegex.exec(html)) !== null) {
    console.log(m[1]);
  }
}

main().catch(console.error);
