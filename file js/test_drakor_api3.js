const https = require('https');
const zlib = require('zlib');

// Step 2b: WebView-like approach
// Fetch the episode page, then try to simulate what the browser JS does:
// 1. Find the loadEpisode function in the obfuscated script
// 2. Try to decode the actual API URL by running the decoder
// 3. OR just look for direct embed URLs / video sources

function fetch(url, opts = {}) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const options = {
            hostname: urlObj.hostname,
            port: urlObj.port || (urlObj.protocol === 'https:' ? 443 : 80),
            path: urlObj.pathname + urlObj.search,
            headers: {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language': 'id-ID,id;q=0.9,en;q=0.8',
                'Accept-Encoding': 'gzip, deflate',
                ...opts.headers
            },
            timeout: 15000,
            rejectUnauthorized: false
        };
        const req = (urlObj.protocol === 'https:' ? https : require('http')).get(options, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                let loc = res.headers.location;
                if (loc.startsWith('/')) loc = `${urlObj.protocol}//${urlObj.host}${loc}`;
                res.resume();
                return fetch(loc, opts).then(resolve).catch(reject);
            }
            const chunks = [];
            res.on('data', c => chunks.push(c));
            res.on('end', () => {
                let body = Buffer.concat(chunks);
                if (res.headers['content-encoding'] === 'gzip') {
                    body = zlib.gunzipSync(body);
                } else if (res.headers['content-encoding'] === 'deflate') {
                    body = zlib.inflateSync(body);
                }
                resolve({ status: res.statusCode, headers: res.headers, body: body.toString('utf-8') });
            });
        });
        req.on('error', reject);
    });
}

async function main() {
    console.log('=== Fetching episode page (with gzip) ===');
    const page = await fetch('https://drakor.kita.mobi/detail/the-husband-2026-v2e8/');
    console.log(`Page length: ${page.body.length}`);
    
    // Find the big obfuscated script block (30k+ chars)
    const scripts = [...page.body.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/gi)];
    
    let bigScript = '';
    for (const s of scripts) {
        if (s[1].length > 5000) {
            bigScript = s[1].trim();
            console.log(`\nBig script: ${bigScript.length} chars`);
            break;
        }
    }
    
    if (!bigScript) {
        console.log('No big obfuscated script found!');
        return;
    }
    
    // The script has a string array that gets shuffled by an IIFE
    // The _0x5451 function decodes strings using RC4 + base64
    // We need to find the loadEpisode/loadServer function and extract the API URL pattern
    
    // Strategy 1: Find the decoded API path by looking at the script's structure
    // The script creates a URL like: c_api_host + '/' + decoded_path + '?' + params
    
    // Find all patterns of decoded string lookups near 'ajax' or 'post' or 'get'
    console.log('\n--- Looking for API call patterns ---');
    
    // Find $.ajax or $.post or XMLHttpRequest patterns
    const ajaxPatterns = [
        /\$\.ajax\s*\(\s*\{[^}]*url\s*:\s*([^,}]+)/g,
        /\$\.post\s*\(\s*([^,)]+)/g,
        /\$\.get\s*\(\s*([^,)]+)/g,
        /\.open\s*\(\s*['"](?:POST|GET)['"]\s*,\s*([^,)]+)/g,
    ];
    
    for (const pattern of ajaxPatterns) {
        const matches = [...bigScript.matchAll(pattern)];
        for (const m of matches) {
            console.log(`  AJAX pattern: ${m[0].substring(0, 200)}`);
        }
    }
    
    // Strategy 2: Find where loadEpisode function is defined
    console.log('\n--- loadEpisode function ---');
    const loadEpIdx = bigScript.indexOf('loadEpisode');
    if (loadEpIdx !== -1) {
        // Show context
        const start = Math.max(0, loadEpIdx - 200);
        const end = Math.min(bigScript.length, loadEpIdx + 500);
        console.log(`Context around loadEpisode:`);
        console.log(bigScript.substring(start, end));
    } else {
        console.log('loadEpisode not found directly - may be built from decoded strings');
    }
    
    // Strategy 3: Look for the string concatenation that builds the API URL
    // Pattern: something like api_host + '/server.php' or c_api_host + '/...'
    console.log('\n--- Looking for URL building patterns ---');
    
    // The script likely has patterns like: variable + '/' + decoded_string + '/...'
    // Let's look for the specific decoded paths
    
    // From the JS research, we know:
    // The path contains something like '/server.php' or '/4/...'
    // But it's obfuscated via _0x5451(hex, key)
    
    // Strategy 4: Try to extract the URL by looking for the $.ajax call configuration
    // The ajax call likely has: url: c_api_host + '/' + path, data: params, type: 'POST'
    
    // Let's look for the specific pattern where c_api_host is used
    const apiHostUsage = [...bigScript.matchAll(/c_api_host/g)];
    console.log(`\nc_api_host appears ${apiHostUsage.length} times`);
    for (const m of apiHostUsage) {
        const start = Math.max(0, m.index - 100);
        const end = Math.min(bigScript.length, m.index + 300);
        console.log(`\nContext at ${m.index}:`);
        console.log(bigScript.substring(start, end));
    }
    
    // Strategy 5: Try to use the JS decoder directly
    // Extract the _0x1c23 array, _0x5451 function, and IIFE from the script
    console.log('\n--- Extracting decoder functions ---');
    
    const decoderMatch = bigScript.match(/function _0x1c23\(\)\{[^}]+\}/);
    if (decoderMatch) {
        console.log(`Found _0x1c23: ${decoderMatch[0].length} chars`);
    }
    
    const d5451Match = bigScript.match(/function _0x5451\([^)]+\)\{[\s\S]+?\nfunction/);
    if (d5451Match) {
        console.log(`Found _0x5451: ${d5451Match[0].length} chars`);
    }
    
    // Strategy 6: Just try the known decoded values
    // From research: the decoded loadServer URL has path '/server.php'
    // But maybe it's actually a different path
    
    // Let's try different endpoint names with the known c/t
    const apiBase = 'https://api.nonton.bid/c_api';
    const known_c = 'bfb1';
    const known_t = '1784892473&ver=373iq';
    const movieId = 'yLpA1nCVmw';
    const epid = 'xyMtUFbMQifT';
    
    console.log('\n=== Exhaustive API endpoint testing ===');
    
    const paths = [
        'server.php', 'server', 'load_server', 'loadserver',
        'get_server', 'getserver', 'video.php', 'video',
        'play.php', 'play', 'stream.php', 'stream',
        'embed.php', 'embed', 'source.php', 'source',
        'link.php', 'link', 'get_link', 'getlink',
        'player.php', 'player', 'resolve.php', 'resolve',
    ];
    
    for (const path of paths) {
        const url = `${apiBase}/${path}`;
        const bodies = [
            `is_mob=0&is_uc=0&cat=hs&tag=ind&server_xid=${movieId}&c=${known_c}&t=${encodeURIComponent(known_t)}`,
            `movie_id=${movieId}&server=hs&lang=ind&c=${known_c}&t=${encodeURIComponent(known_t)}`,
            `movie_id=${movieId}&ep_id=${epid}&c=${known_c}&t=${encodeURIComponent(known_t)}`,
        ];
        
        for (const body of bodies) {
            try {
                const resp = await fetch(url, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        'Content-Length': Buffer.byteLength(body),
                        'Origin': 'https://drakor.kita.mobi',
                        'Referer': 'https://drakor.kita.mobi/detail/the-husband-2026-v2e8/',
                        'X-Requested-With': 'XMLHttpRequest',
                    },
                    body: body
                });
                if (resp.status !== 404) {
                    console.log(`\n${resp.status} ${url}`);
                    console.log(`  Body snippet: ${resp.body.substring(0, 300)}`);
                    if (resp.body.length > 10 && resp.body.includes('{')) {
                        console.log('  *** POSSIBLE JSON RESPONSE ***');
                    }
                }
            } catch (e) {}
        }
    }
    
    // Strategy 7: Try the loadEpisode AJAX directly
    // The loadEpisode function likely makes a $.ajax POST to some endpoint
    // Let's try calling it directly with the right format
    
    console.log('\n=== Trying direct loadEpisode simulation ===');
    const leUrl = `${apiBase}/?is_mob=0&is_uc=0&movie_id=${movieId}&cat=hs&tag=ind&c=${known_c}&t=${encodeURIComponent(known_t)}`;
    try {
        const resp = await fetch(leUrl);
        console.log(`GET ${leUrl}`);
        console.log(`Status: ${resp.status}, Response: ${resp.body.substring(0, 500)}`);
    } catch (e) {
        console.log(`Error: ${e.message}`);
    }
    
    // Try POST to root
    const leBody = `is_mob=0&is_uc=0&movie_id=${movieId}&cat=hs&tag=ind&c=${known_c}&t=${encodeURIComponent(known_t)}`;
    try {
        const resp = await fetch(apiBase, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Content-Length': Buffer.byteLength(leBody),
                'Origin': 'https://drakor.kita.mobi',
                'Referer': 'https://drakor.kita.mobi/detail/the-husband-2026-v2e8/',
                'X-Requested-With': 'XMLHttpRequest',
            },
            body: leBody
        });
        console.log(`\nPOST ${apiBase}`);
        console.log(`Status: ${resp.status}, Response: ${resp.body.substring(0, 500)}`);
    } catch (e) {
        console.log(`Error: ${e.message}`);
    }
    
    console.log('\n=== DONE ===');
}

main().catch(console.error);
