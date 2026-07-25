// Test DrakorKita API endpoints directly
const https = require('https');
const http = require('http');

const BASE = 'https://xdrakor33.nicewap.sbs';
// From HTML: loadEpisode('yLpA1nCVmw','hs','ind')
const movie_id = 'yLpA1nCVmw';
const cat = 'hs';
const tag = 'ind';

const endpoints = [
  // From decoded JS strings - mobile endpoints
  `/episode_mob.php?id=${movie_id}&cat=${cat}&tag=${tag}&is_uc=0`,
  `/server.php?id=${movie_id}&cat=${cat}&tag=${tag}&is_uc=0`,
  // Desktop equivalents
  `/episode.php?id=${movie_id}&cat=${cat}&tag=${tag}&is_uc=0`,
  `/episode.php?movie_id=${movie_id}&cat=${cat}&tag=${tag}&is_uc=0`,
  `/episode.php?id=${movie_id}&cat=${cat}&tag=${tag}`,
  // Common patterns for this type of streaming site
  `/ajax/episode?id=${movie_id}&cat=${cat}&tag=${tag}`,
  `/ajax/episode/servers?id=${movie_id}&cat=${cat}&tag=${tag}`,
  `/ajax/episode/servers?id=${movie_id}`,
  `/ajax/servers/${movie_id}`,
  `/api/episode?id=${movie_id}&cat=${cat}&tag=${tag}`,
  `/api/servers?id=${movie_id}&cat=${cat}&tag=${tag}`,
  // WordPress-style
  `/wp-json/doctorstream/v1/episode?id=${movie_id}&cat=${cat}&tag=${tag}`,
  // Load episode pattern
  `/load/episode?id=${movie_id}&cat=${cat}&tag=${tag}`,
  `/loadEpisode?id=${movie_id}&cat=${cat}&tag=${tag}`,
  // Another pattern
  `/episode/${movie_id}/${cat}/${tag}`,
  // Direct path
  `/${movie_id}/${cat}/${tag}`,
];

async function fetchUrl(url) {
  return new Promise((resolve, reject) => {
    const mod = url.startsWith('https') ? https : http;
    const req = mod.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': BASE,
      },
      timeout: 10000,
    }, (res) => {
      let data = '';
      // Handle redirects
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        resolve({ status: res.statusCode, redirect: res.headers.location, body: '' });
        res.resume();
        return;
      }
      res.on('data', chunk => data += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body: data }));
    });
    req.on('error', (e) => reject(e));
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
  });
}

async function testEndpoint(path) {
  const url = BASE + path;
  try {
    const res = await fetchUrl(url);
    const isJson = res.body.trim().startsWith('{') || res.body.trim().startsWith('[');
    const hasHtml = res.body.includes('<html') || res.body.includes('<!DOCTYPE');
    const preview = res.body.substring(0, 200).replace(/\n/g, ' ');
    console.log(`[${res.status}] ${path.substring(0, 60)}`);
    if (res.redirect) {
      console.log(`  -> REDIRECT: ${res.redirect}`);
    } else if (isJson) {
      console.log(`  -> JSON (${res.body.length} bytes): ${preview}`);
    } else if (hasHtml) {
      // Check if the HTML has server/player data
      const hasServer = res.body.includes('server') || res.body.includes('player') || res.body.includes('loadEpisode');
      const hasVideo = res.body.includes('.mp4') || res.body.includes('.m3u8') || res.body.includes('embed');
      console.log(`  -> HTML (${res.body.length} bytes) server=${hasServer} video=${hasVideo}`);
    } else {
      console.log(`  -> OTHER (${res.body.length} bytes): ${preview}`);
    }
  } catch (e) {
    console.log(`[ERR] ${path.substring(0, 60)} -> ${e.message}`);
  }
}

async function main() {
  console.log(`Testing ${endpoints.length} endpoints against ${BASE}\n`);
  for (const ep of endpoints) {
    await testEndpoint(ep);
  }
}

main();
