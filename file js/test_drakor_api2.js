const https = require('https');

// Step 2: Extract c/t from obfuscated script, then try server.php

function fetch(url, opts = {}) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const mod = urlObj.protocol === 'https:' ? https : require('http');
        const options = {
            hostname: urlObj.hostname,
            port: urlObj.port,
            path: urlObj.pathname + urlObj.search,
            headers: {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
                'Accept': '*/*',
                'Accept-Encoding': 'identity',
                ...opts.headers
            },
            timeout: 15000,
            rejectUnauthorized: false
        };
        const req = (urlObj.protocol === 'https:' ? https : require('http')).get(options, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                return fetch(res.headers.location, opts).then(resolve).catch(reject);
            }
            let data = '';
            res.on('data', c => data += c);
            res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: data }));
        });
        req.on('error', reject);
    });
}

function post(url, body) {
    const urlObj = new URL(url);
    return new Promise((resolve, reject) => {
        const options = {
            hostname: urlObj.hostname,
            port: urlObj.port,
            path: urlObj.pathname + urlObj.search,
            method: 'POST',
            headers: {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36',
                'Content-Type': 'application/x-www-form-urlencoded',
                'Content-Length': Buffer.byteLength(body),
                'Origin': 'https://drakor.kita.mobi',
                'Referer': 'https://drakor.kita.mobi/detail/the-husband-2026-v2e8/',
                'Accept': '*/*',
                'X-Requested-With': 'XMLHttpRequest',
            },
            timeout: 15000,
            rejectUnauthorized: false
        };
        const req = (urlObj.protocol === 'https:' ? https : require('http')).request(options, (res) => {
            let data = '';
            res.on('data', c => data += c);
            res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: data }));
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

async function main() {
    console.log('=== Fetching episode page ===');
    const page = await fetch('https://drakor.kita.mobi/detail/the-husband-2026-v2e8/');
    console.log(`Page length: ${page.body.length}`);
    
    // Find the big obfuscated script block
    const scripts = [...page.body.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/gi)];
    console.log(`Found ${scripts.length} script blocks`);
    
    for (let i = 0; i < scripts.length; i++) {
        const content = scripts[i][1].trim();
        if (content.length > 1000) {
            console.log(`\nScript #${i}: ${content.length} chars`);
            
            // Look for c and t definitions
            const cMatch = content.match(/c\s*=\s*['"]([a-f0-9]{3,})['"]/);
            const tMatch = content.match(/t\s*=\s*['"](\d+[^'"]*)['"]/);
            const apiHostMatch = content.match(/c_api_host\s*=\s*['"]([^'"]+)['"]/);
            
            if (cMatch) console.log(`  c = ${cMatch[1]}`);
            if (tMatch) console.log(`  t = ${tMatch[1]}`);
            if (apiHostMatch) console.log(`  api_host = ${apiHostMatch[1]}`);
            
            // Look for string patterns that could be c/t
            // c is typically a 4-char hex string like 'bfb1'
            const hex4Matches = [...content.matchAll(/[='"]\s*([a-f0-9]{4})\s*['"]/g)];
            if (hex4Matches.length > 0 && hex4Matches.length < 20) {
                console.log(`  4-char hex strings: ${hex4Matches.map(m => m[1]).join(', ')}`);
            }
            
            // Look for epoch-like timestamps (10 digits starting with 1)
            const timeMatches = [...content.matchAll(/['"](\d{10})\d*[^'"]*['"]/g)];
            if (timeMatches.length > 0 && timeMatches.length < 20) {
                console.log(`  Timestamp-like strings: ${timeMatches.map(m => m[1]).join(', ')}`);
            }
        }
    }
    
    // Now try the approach: extract from the big obfuscated script
    // The script uses a string array _0x1c23 that gets shuffled
    // c and t are set AFTER the IIFE runs
    // But the values are embedded in the HTML's obfuscated code
    
    // Alternative: look for them in meta tags, hidden inputs, or data attributes
    const metaMatches = [...page.body.matchAll(/<meta[^>]+content=["']([^"']+)["'][^>]*name=["']([^"']+)["']/gi)];
    for (const m of metaMatches) {
        // skip
    }
    
    // Try to find the specific obfuscated pattern
    // From the JS research: c = 'bfb1' and t = '1784892473&ver=373iq'
    // These might be in the response as encoded values
    
    // Let's look for the specific string 'nonton.bid' in the scripts
    for (let i = 0; i < scripts.length; i++) {
        const content = scripts[i][1].trim();
        if (content.includes('nonton') || content.includes('api_host') || content.includes('loadEpisode')) {
            console.log(`\nScript #${i} contains API references (${content.length} chars)`);
            // Show surrounding context of 'nonton' if found
            const idx = content.indexOf('nonton');
            if (idx !== -1) {
                console.log(`  Context: ...${content.substring(Math.max(0, idx - 100), idx + 200)}...`);
            }
        }
    }
    
    // The key insight: the obfuscated script is one huge block
    // It computes c and t at runtime using the _0x5451 decoder
    // We need to run the decoder with the correct hex/key pairs
    
    // From our research JS files, the values were:
    // c_api_host = 'https://api.nonton.bid/c_api'  (decoded)
    // c = 'bfb1' (decoded)
    // t = '1784892473&ver=373iq' (decoded)
    
    // But these might be session-specific. Let me try with these known values
    // first to see if server.php works
    
    const known_c = 'bfb1';
    const known_t = '1784892473&ver=373iq';
    
    console.log('\n=== Testing with known c/t values ===');
    const params = `is_mob=0&is_uc=0&cat=hs&tag=ind&server_xid=yLpA1nCVmw&c=${known_c}&t=${encodeURIComponent(known_t)}`;
    console.log(`POST server.php: ${params}`);
    
    try {
        const resp = await post('https://api.nonton.bid/c_api/server.php', params);
        console.log(`Status: ${resp.status}`);
        console.log(`Content-Type: ${resp.headers['content-type']}`);
        console.log(`Response (${resp.body.length} bytes): ${resp.body.substring(0, 1000)}`);
        
        if (resp.status === 200 && resp.body.length > 0) {
            try {
                const json = JSON.parse(resp.body);
                console.log('\nJSON parsed successfully!');
                console.log(JSON.stringify(json, null, 2).substring(0, 2000));
                
                // Look for hydrax/abyss URLs in the response
                const str = JSON.stringify(json);
                if (str.includes('abyss') || str.includes('hydrax')) {
                    console.log('\n*** FOUND HYDRAX/ABYSS URL ***');
                }
                if (str.includes('.mp4') || str.includes('.m3u8') || str.includes('globalcdn')) {
                    console.log('\n*** FOUND DIRECT VIDEO URL ***');
                }
            } catch (e) {
                console.log(`Not JSON: ${e.message}`);
            }
        }
    } catch (e) {
        console.log(`Error: ${e.message}`);
    }
    
    // Also try with different movie IDs from the episode page
    console.log('\n=== Trying episode.php to get all episode IDs ===');
    try {
        const resp = await fetch(`https://api.nonton.bid/c_api/episode.php?is_mob=0&is_uc=0&movie_id=yLpA1nCVmw&c=${known_c}&t=${encodeURIComponent(known_t)}`);
        console.log(`Status: ${resp.status}`);
        const json = JSON.parse(resp.body);
        console.log(`ptype: ${json.ptype}`);
        console.log(`server_xid: ${json.server_xid}`);
        console.log(`first_ep_id: ${json.first_ep_id}`);
        
        // Now try server.php with server_xid
        console.log('\n=== Retrying server.php with server_xid ===');
        const params2 = `is_mob=0&is_uc=0&cat=hs&tag=ind&server_xid=f1&c=${known_c}&t=${encodeURIComponent(known_t)}`;
        const resp2 = await post('https://api.nonton.bid/c_api/server.php', params2);
        console.log(`Status: ${resp2.status}`);
        console.log(`Response: ${resp2.body.substring(0, 1000)}`);
    } catch (e) {
        console.log(`Error: ${e.message}`);
    }
    
    console.log('\n=== DONE ===');
}

main().catch(console.error);
