const https = require('https');

const BASE = 'https://xdrakor33.nicewap.sbs';
const movie_id = 'yLpA1nCVmw';
const cat = 'hs';
const tag = 'ind';

function request(url, options = {}) {
  return new Promise((resolve, reject) => {
    const urlObj = new URL(url);
    const reqOptions = {
      hostname: urlObj.hostname,
      path: urlObj.pathname + urlObj.search,
      port: urlObj.port || 443,
      method: options.method || 'GET',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
        'Accept': '*/*',
        'X-Requested-With': 'XMLHttpRequest',
        'Referer': BASE + '/detail/' + movie_id,
        'Origin': BASE,
        ...options.headers,
      },
      timeout: 15000,
    };

    const req = https.request(reqOptions, (res) => {
      const encoding = res.headers['content-encoding'];
      let stream = res;
      
      if (encoding === 'gzip') {
        stream = res.pipe(require('zlib').createGunzip());
      } else if (encoding === 'br') {
        stream = res.pipe(require('zlib').createBrotliDecompress());
      } else if (encoding === 'deflate') {
        stream = res.pipe(require('zlib').createInflate());
      }

      let data = '';
      stream.on('data', chunk => data += chunk);
      stream.on('end', () => resolve({ 
        status: res.statusCode, 
        headers: res.headers,
        body: data 
      }));
    });

    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
    
    if (options.body) req.write(options.body);
    req.end();
  });
}

async function main() {
  // Test 1: GET /episode.php with different params
  console.log('=== Test 1: GET /episode.php variations ===');
  const urls1 = [
    `${BASE}/episode.php?movie_id=${movie_id}&cat=${cat}&tag=${tag}`,
    `${BASE}/episode.php?movie_id=${movie_id}&cat=${cat}&tag=${tag}&is_uc=0`,
    `${BASE}/episode.php?id=${movie_id}&cat=${cat}&tag=${tag}`,
  ];
  for (const url of urls1) {
    try {
      const r = await request(url);
      console.log(`GET ${url.replace(BASE, '')} -> ${r.status} (${r.body.length}b) ${r.headers['content-type']}`);
      if (r.body.length > 0 && r.body.length < 500) console.log(`  Body: ${r.body}`);
      if (r.body.length > 500) console.log(`  Body preview: ${r.body.substring(0, 300)}`);
    } catch (e) { console.log(`  ERR: ${e.message}`); }
  }

  // Test 2: POST /episode.php
  console.log('\n=== Test 2: POST /episode.php ===');
  const postBody = `movie_id=${movie_id}&cat=${cat}&tag=${tag}`;
  try {
    const r = await request(`${BASE}/episode.php`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: postBody,
    });
    console.log(`POST /episode.php -> ${r.status} (${r.body.length}b) ${r.headers['content-type']}`);
    if (r.body.length > 0) console.log(`  Body: ${r.body.substring(0, 500)}`);
  } catch (e) { console.log(`  ERR: ${e.message}`); }

  // Test 3: POST with JSON body
  console.log('\n=== Test 3: POST /episode.php JSON ===');
  try {
    const r = await request(`${BASE}/episode.php`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ movie_id, cat, tag, is_uc: 0 }),
    });
    console.log(`POST JSON /episode.php -> ${r.status} (${r.body.length}b)`);
    if (r.body.length > 0) console.log(`  Body: ${r.body.substring(0, 500)}`);
  } catch (e) { console.log(`  ERR: ${e.message}`); }

  // Test 4: Try with Accept: application/json
  console.log('\n=== Test 4: GET /episode.php with JSON accept ===');
  try {
    const r = await request(`${BASE}/episode.php?movie_id=${movie_id}&cat=${cat}&tag=${tag}`, {
      headers: { 'Accept': 'application/json, text/javascript, */*; q=0.01' },
    });
    console.log(`GET (JSON accept) -> ${r.status} (${r.body.length}b) ${r.headers['content-type']}`);
    if (r.body.length > 0) console.log(`  Body: ${r.body.substring(0, 500)}`);
  } catch (e) { console.log(`  ERR: ${e.message}`); }

  // Test 5: Try the full page to see if episode.php is referenced
  console.log('\n=== Test 5: Check if episode.php is mentioned in the HTML ===');
  try {
    const r = await request(`${BASE}/detail/${movie_id}`);
    const matches = r.body.match(/episode\.php[^"'\s]*/g) || [];
    console.log(`HTML mentions of episode.php: ${matches.length}`);
    matches.forEach(m => console.log(`  ${m}`));
    
    // Also look for any AJAX/API patterns
    const apiPatterns = r.body.match(/(?:url|href|src)\s*[:=]\s*["'][^"']*(?:episode|server|ajax|api|load)[^"']*/gi) || [];
    console.log(`\nAPI/URL patterns in HTML: ${apiPatterns.length}`);
    apiPatterns.forEach(m => console.log(`  ${m}`));
  } catch (e) { console.log(`  ERR: ${e.message}`); }

  // Test 6: Check if /episode.php returns Content-Length or different behavior with cookies
  console.log('\n=== Test 6: GET /episode.php with verbose headers ===');
  try {
    const r = await request(`${BASE}/episode.php?movie_id=${movie_id}&cat=${cat}&tag=${tag}`);
    console.log('Status:', r.status);
    console.log('Headers:', JSON.stringify(r.headers, null, 2));
    console.log('Body length:', r.body.length);
    console.log('Body bytes:', Buffer.from(r.body).toString('hex').substring(0, 200));
  } catch (e) { console.log(`  ERR: ${e.message}`); }
}

main();
