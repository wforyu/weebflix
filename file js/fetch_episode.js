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
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'id-ID,id;q=0.9,en;q=0.8',
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
  // Fetch the episode page
  const html = await fetch(`${BASE}/the-husband-season-1-episode-1/`);
  
  // Find all script tags with inline JS
  const scriptRegex = /<script[^>]*>([\s\S]*?)<\/script>/gi;
  let match;
  while ((match = scriptRegex.exec(html)) !== null) {
    const content = match[1].trim();
    if (content.length > 10 && content.length < 5000 && !content.includes('var _0x')) {
      console.log(`\n=== Inline script (${content.length} chars) ===`);
      console.log(content.substring(0, 1500));
      console.log('...');
    }
    if (content.includes('loadEpisode') || content.includes('c_api_host') || content.includes('initEpisodeList')) {
      console.log(`\n=== CRITICAL SCRIPT (${content.length} chars) ===`);
      console.log(content);
    }
  }

  // Find loadEpisode calls
  const loadEpRegex = /loadEpisode\(([^)]+)\)/g;
  let m;
  console.log('\n=== loadEpisode calls ===');
  while ((m = loadEpRegex.exec(html)) !== null) {
    console.log(`loadEpisode(${m[1]})`);
    // Show surrounding context
    const start = Math.max(0, m.index - 200);
    const end = Math.min(html.length, m.index + m[0].length + 200);
    console.log(`Context: ...${html.substring(start, end)}...\n`);
  }

  // Find server_lists div
  const serverListsIdx = html.indexOf('server_lists');
  if (serverListsIdx >= 0) {
    console.log('\n=== server_lists context ===');
    console.log(html.substring(Math.max(0, serverListsIdx - 100), serverListsIdx + 500));
  }

  // Find c_api_host
  const apiHostIdx = html.indexOf('c_api_host');
  if (apiHostIdx >= 0) {
    console.log('\n=== c_api_host context ===');
    console.log(html.substring(Math.max(0, apiHostIdx - 100), apiHostIdx + 300));
  }

  // Find all external script sources
  console.log('\n=== External scripts ===');
  const srcRegex = /<script[^>]+src=["']([^"']+)["']/gi;
  while ((m = srcRegex.exec(html)) !== null) {
    console.log(m[1]);
  }
}

main().catch(console.error);
