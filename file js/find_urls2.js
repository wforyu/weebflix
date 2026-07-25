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
  const home = await fetch(BASE);
  
  // Find ALL href values
  const allHrefs = [];
  const hrefRegex = /href=["']([^"']+)["']/gi;
  let m;
  while ((m = hrefRegex.exec(home)) !== null) {
    allHrefs.push(m[1]);
  }
  
  console.log(`Total hrefs: ${allHrefs.length}`);
  
  // Show unique href patterns (strip query params, sort)
  const unique = [...new Set(allHrefs)].sort();
  console.log('\nUnique hrefs:');
  unique.forEach(h => console.log(`  ${h}`));
  
  // Also search for any onclick or data attributes that might have loadEpisode
  const onclickRegex = /onclick=["']([^"']*loadEpisode[^"']*)["']/gi;
  while ((m = onclickRegex.exec(home)) !== null) {
    console.log(`\nloadEpisode onclick: ${m[1]}`);
  }
  
  // Search for any inline script with loadEpisode
  const inlineScriptRegex = /<script>([\s\S]*?)<\/script>/gi;
  while ((m = inlineScriptRegex.exec(home)) !== null) {
    if (m[1].includes('loadEpisode') || m[1].includes('server')) {
      console.log(`\nScript with loadEpisode/server:`);
      console.log(m[1].substring(0, 2000));
    }
  }
  
  // Check for detail links specifically
  const detailLinks = unique.filter(h => h.includes('detail') || h.includes('nonton') || h.includes('watch'));
  console.log(`\nDetail/nonton/watch links: ${detailLinks.length}`);
  detailLinks.forEach(h => console.log(`  ${h}`));
}

main().catch(console.error);
