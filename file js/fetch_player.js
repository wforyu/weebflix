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
        'Accept': '*/*',
        'Referer': BASE,
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
  // Fetch mobl.js
  console.log('=== mobl.js ===');
  const mobl = await fetch(`${BASE}/player/mobl.js?v=7iq2a`);
  console.log(`Length: ${mobl.length}`);
  console.log(mobl);
  
  // Also fetch a.js header (first 500 chars) to understand structure
  console.log('\n=== a.js (first 2000 chars) ===');
  const ajs = await fetch(`${BASE}/a.js?v=7iq2a`);
  console.log(`Length: ${ajs.length}`);
  console.log(ajs.substring(0, 2000));
}

main().catch(console.error);
