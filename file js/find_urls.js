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
  // Fetch homepage to find episode URLs
  console.log('Fetching homepage...');
  const home = await fetch(BASE);
  console.log(`Homepage: ${home.length} bytes`);
  
  // Find episode/movie links
  const linkRegex = /href=["'](https?:\/\/[^"']+nicewap[^"']*?)["']/gi;
  const links = new Set();
  let m;
  while ((m = linkRegex.exec(home)) !== null) {
    links.add(m[1]);
  }
  
  // Also find relative links
  const relRegex = /href=["'](\/[^"']{5,})["']/g;
  while ((m = relRegex.exec(home)) !== null) {
    links.add(m[1]);
  }

  console.log(`Found ${links.size} links`);
  
  // Find episode/play/watch links
  const epLinks = [...links].filter(l => l.includes('episode') || l.includes('watch') || l.includes('play') || l.includes('stream'));
  console.log(`\nEpisode-like links (${epLinks.length}):`);
  epLinks.slice(0, 10).forEach(l => console.log(`  ${l}`));
  
  // Find loadEpisode in homepage
  if (home.includes('loadEpisode')) {
    const idx = home.indexOf('loadEpisode');
    console.log(`\nloadEpisode found in homepage at ${idx}`);
    console.log(home.substring(Math.max(0, idx - 200), idx + 300));
  }

  // Also check for c_api_host in any scripts
  if (home.includes('c_api_host')) {
    const idx = home.indexOf('c_api_host');
    console.log(`\nc_api_host found in homepage!`);
    console.log(home.substring(Math.max(0, idx - 100), idx + 300));
  }

  // Find all script src URLs
  const srcRegex = /src=["']([^"']+\.js[^"']*)["']/gi;
  console.log('\nScript sources:');
  while ((m = srcRegex.exec(home)) !== null) {
    console.log(`  ${m[1]}`);
  }
}

main().catch(console.error);
