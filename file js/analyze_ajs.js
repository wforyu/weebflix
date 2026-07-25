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
  const ajs = await fetch(`${BASE}/a.js?v=7iq2a`);
  console.log(`a.js length: ${ajs.length}`);

  // Find loadEpisode definition
  let idx = ajs.indexOf('loadEpisode');
  while (idx !== -1) {
    console.log(`\n=== loadEpisode at offset ${idx} ===`);
    console.log(ajs.substring(Math.max(0, idx - 200), Math.min(ajs.length, idx + 800)));
    idx = ajs.indexOf('loadEpisode', idx + 1);
  }

  // Find c_api_host
  idx = ajs.indexOf('c_api_host');
  while (idx !== -1) {
    console.log(`\n=== c_api_host at offset ${idx} ===`);
    console.log(ajs.substring(Math.max(0, idx - 200), Math.min(ajs.length, idx + 400)));
    idx = ajs.indexOf('c_api_host', idx + 1);
  }

  // Find initEpisodeList
  idx = ajs.indexOf('initEpisodeList');
  while (idx !== -1) {
    console.log(`\n=== initEpisodeList at offset ${idx} ===`);
    console.log(ajs.substring(Math.max(0, idx - 200), Math.min(ajs.length, idx + 600)));
    idx = ajs.indexOf('initEpisodeList', idx + 1);
  }

  // Find loadServer
  idx = ajs.indexOf('loadServer');
  while (idx !== -1) {
    console.log(`\n=== loadServer at offset ${idx} ===`);
    console.log(ajs.substring(Math.max(0, idx - 200), Math.min(ajs.length, idx + 600)));
    idx = ajs.indexOf('loadServer', idx + 1);
  }

  // Find episode.php or server.php references
  idx = ajs.indexOf('episode.php');
  while (idx !== -1) {
    console.log(`\n=== episode.php at offset ${idx} ===`);
    console.log(ajs.substring(Math.max(0, idx - 200), Math.min(ajs.length, idx + 400)));
    idx = ajs.indexOf('episode.php', idx + 1);
  }
  
  idx = ajs.indexOf('server.php');
  while (idx !== -1) {
    console.log(`\n=== server.php at offset ${idx} ===`);
    console.log(ajs.substring(Math.max(0, idx - 200), Math.min(ajs.length, idx + 400)));
    idx = ajs.indexOf('server.php', idx + 1);
  }

  // Find ajax
  idx = ajs.indexOf('ajax');
  let ajaxCount = 0;
  while (idx !== -1 && ajaxCount < 5) {
    console.log(`\n=== ajax at offset ${idx} ===`);
    console.log(ajs.substring(Math.max(0, idx - 100), Math.min(ajs.length, idx + 300)));
    idx = ajs.indexOf('ajax', idx + 1);
    ajaxCount++;
  }
}

main().catch(console.error);
