const https = require('https');
const http = require('http');
const zlib = require('zlib');

// Test DrakorKita API endpoint resolution
// Step 1: Fetch episode page → extract c/t values
// Step 2: Call API endpoint → get Hydrax embed URL
// Step 3: Resolve Hydrax → direct video URL

const BASE = 'https://drakor.kita.mobi';
const EPISODE_PATH = '/detail/the-husband-2026-v2e8/';
const MOVIE_ID = 'yLpA1nCVmw';
const SERVER = 'hs';
const LANG = 'ind';

function fetch(url, opts = {}) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const mod = urlObj.protocol === 'https:' ? https : http;
        const options = {
            hostname: urlObj.hostname,
            path: urlObj.pathname + urlObj.search,
            port: urlObj.port,
            headers: {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                'Accept-Language': 'id-ID,id;q=0.9,en;q=0.8',
                'Accept-Encoding': 'identity',
                ...opts.headers
            },
            timeout: 15000,
            ...(urlObj.protocol === 'https:' ? { rejectUnauthorized: false } : {})
        };
        mod.get(options, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                console.log(`  Redirect ${res.statusCode} -> ${res.headers.location}`);
                return fetch(res.headers.location, opts).then(resolve).catch(reject);
            }
            let data = '';
            res.on('data', c => data += c);
            res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: data }));
        }).on('error', reject);
    });
}

function post(url, body, contentType = 'application/x-www-form-urlencoded') {
    const urlObj = new URL(url);
    const mod = urlObj.protocol === 'https:' ? https : http;
    return new Promise((resolve, reject) => {
        const options = {
            hostname: urlObj.hostname,
            port: urlObj.port,
            path: urlObj.pathname + urlObj.search,
            method: 'POST',
            headers: {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
                'Content-Type': contentType,
                'Content-Length': Buffer.byteLength(body),
                'Origin': urlObj.origin,
                'Referer': BASE + EPISODE_PATH,
                'Accept': '*/*',
                'X-Requested-With': 'XMLHttpRequest',
                ...(urlObj.protocol === 'https:' ? {} : {})
            },
            timeout: 15000,
            rejectUnauthorized: false
        };
        const req = mod.request(options, (res) => {
            let data = '';
            res.on('data', c => data += c);
            res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: data }));
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

function extractPattern(html, pattern) {
    const match = html.match(pattern);
    return match ? match[1] : null;
}

async function main() {
    console.log('=== Step 1: Fetch episode page ===');
    console.log(`URL: ${BASE}${EPISODE_PATH}`);
    
    const page = await fetch(BASE + EPISODE_PATH);
    console.log(`Status: ${page.status}`);
    console.log(`Body length: ${page.body.length}`);
    
    // Extract c and t values
    const cMatch = page.body.match(/var\s+c\s*=\s*['"]([^'"]+)['"]/);
    const tMatch = page.body.match(/var\s+t\s*=\s*['"]([^'"]+)['"]/);
    const movieIdMatch = page.body.match(/GLOBAL_MOVIE_ID\s*=\s*['"]([^'"]*)['"]/);
    const apiHostMatch = page.body.match(/var\s+c_api_host\s*=\s*['"]([^'"]+)['"]/);
    
    console.log(`\nc = ${cMatch ? cMatch[1] : 'NOT FOUND'}`);
    console.log(`t = ${tMatch ? tMatch[1] : 'NOT FOUND'}`);
    console.log(`GLOBAL_MOVIE_ID = ${movieIdMatch ? movieIdMatch[1] : 'NOT FOUND'}`);
    console.log(`c_api_host = ${apiHostMatch ? apiHostMatch[1] : 'NOT FOUND'}`);
    
    // Also try to find them in a.js or inline scripts
    const cMatch2 = page.body.match(/c\s*=\s*['"]([a-f0-9]{4,})['"]/);
    const tMatch2 = page.body.match(/t\s*=\s*['"](\d+[^'"]*)['"]/);
    if (!cMatch) console.log(`Alt c = ${cMatch2 ? cMatch2[1] : 'NOT FOUND'}`);
    if (!tMatch) console.log(`Alt t = ${tMatch2 ? tMatch2[1] : 'NOT FOUND'}`);
    
    const c_val = cMatch ? cMatch[1] : (cMatch2 ? cMatch2[1] : '');
    const t_val = tMatch ? tMatch[1] : (tMatch2 ? tMatch2[1] : '');
    const apiHost = apiHostMatch ? apiHostMatch[1] : 'https://api.nonton.bid/c_api';
    
    if (!c_val || !t_val) {
        console.log('\nWARNING: Could not extract c/t values, trying defaults...');
    }
    
    // Find the actual loadEpisode calls
    const loadEpCalls = [...page.body.matchAll(/loadEpisode\('([^']+)'\s*,\s*'([^']*)'\s*,\s*'([^']*)'\)/g)];
    console.log(`\nFound ${loadEpCalls.length} loadEpisode calls:`);
    for (const m of loadEpCalls) {
        console.log(`  loadEpisode('${m[1]}', '${m[2]}', '${m[3]}')`);
    }
    
    // Find all server buttons
    const serverButtons = [...page.body.matchAll(/onclick="loadEpisode\(([^)]+)\)"[^>]*>([^<]+)</g)];
    console.log(`\nServer buttons:`);
    for (const m of serverButtons) {
        console.log(`  ${m[2].trim()} -> loadEpisode(${m[1]})`);
    }

    console.log('\n=== Step 2: Try API endpoints ===');
    
    // Try multiple API endpoint patterns
    const endpoints = [
        `${apiHost}/server.php`,
        `${apiHost}/loadServer`,
        `${apiHost}/getServer`,
        `${apiHost}/get_server`,
    ];
    
    const params = [
        `is_mob=0&is_uc=0&cat=${SERVER}&tag=${LANG}&server_xid=${MOVIE_ID}&c=${c_val}&t=${encodeURIComponent(t_val)}`,
        `is_mob=0&is_uc=0&movie_id=${MOVIE_ID}&server=${SERVER}&lang=${LANG}&c=${c_val}&t=${encodeURIComponent(t_val)}`,
        `movie_id=${MOVIE_ID}&server=${SERVER}&lang=${LANG}&c=${c_val}&t=${encodeURIComponent(t_val)}`,
    ];
    
    for (const endpoint of endpoints) {
        for (const paramSet of params) {
            console.log(`\nTrying POST ${endpoint}`);
            console.log(`  Body: ${paramSet}`);
            try {
                const resp = await post(endpoint, paramSet);
                console.log(`  Status: ${resp.status}`);
                console.log(`  Content-Type: ${resp.headers['content-type'] || 'unknown'}`);
                if (resp.body.length > 0) {
                    console.log(`  Response (${resp.body.length} bytes): ${resp.body.substring(0, 500)}`);
                }
                if (resp.status === 200 && resp.body.length > 10 && resp.body.length < 10000) {
                    console.log('  *** POTENTIAL SUCCESS ***');
                }
            } catch (e) {
                console.log(`  Error: ${e.message}`);
            }
        }
    }
    
    // Also try GET requests
    console.log('\n=== Step 2b: Try GET requests ===');
    const getEndpoints = [
        `${apiHost}/server.php?is_mob=0&is_uc=0&cat=${SERVER}&tag=${LANG}&server_xid=${MOVIE_ID}&c=${c_val}&t=${encodeURIComponent(t_val)}`,
        `${apiHost}/episode.php?is_mob=0&is_uc=0&movie_id=${MOVIE_ID}&c=${c_val}&t=${encodeURIComponent(t_val)}`,
    ];
    
    for (const url of getEndpoints) {
        console.log(`\nTrying GET ${url}`);
        try {
            const resp = await fetch(url);
            console.log(`  Status: ${resp.status}`);
            console.log(`  Response (${resp.body.length} bytes): ${resp.body.substring(0, 500)}`);
        } catch (e) {
            console.log(`  Error: ${e.message}`);
        }
    }
    
    console.log('\n=== Step 3: Try Hydrax/Abyss resolution ===');
    
    // First check if there's an abysscdn or hydrax URL in the page
    const abyssMatches = [...page.body.matchAll(/(?:abysscdn\.com|playhydrax\.com|player-cdn\.com)[^\s"'<>]*/gi)];
    console.log(`Found ${abyssMatches.length} Abyss/Hydrax URLs in page:`);
    for (const m of abyssMatches) {
        console.log(`  ${m[0]}`);
    }
    
    // Also check for video IDs that look like hydrax (9 chars)
    const videoIdPattern = /[?&]v=([A-Za-z0-9_-]{9})/g;
    const videoIds = [...page.body.matchAll(videoIdPattern)];
    console.log(`\nFound ${videoIds.length} potential video IDs:`);
    for (const m of videoIds) {
        console.log(`  ${m[1]}`);
    }
    
    // Try a sample hydrax resolution
    console.log('\n--- Testing Hydrax resolution with a sample ID ---');
    // Try the GLOBAL_MOVIE_ID as potential hydrax ID
    const testIds = [MOVIE_ID, ...videoIds.map(m => m[1])];
    
    for (const testId of testIds) {
        console.log(`\nTrying abysscdn.com/?v=${testId}`);
        try {
            const resp = await fetch(`https://abysscdn.com/?v=${testId}`);
            console.log(`  Status: ${resp.status}, Length: ${resp.body.length}`);
            
            // Check for PLAYER(atob("...")) pattern
            const atobMatch = resp.body.match(/PLAYER\(\s*atob\(\s*["']([^"']+)["']/);
            if (atobMatch) {
                console.log('  *** FOUND atob pattern! ***');
                try {
                    const decoded = Buffer.from(atobMatch[1], 'base64').toString('utf-8');
                    console.log(`  Decoded config: ${decoded.substring(0, 500)}`);
                    
                    // Parse JSON
                    try {
                        const config = JSON.parse(decoded);
                        console.log(`  domain: ${config.domain}`);
                        console.log(`  id: ${config.id}`);
                        console.log(`  sources: ${JSON.stringify(config.sources)}`);
                        
                        // Construct direct URL (1080p)
                        if (config.domain && config.id) {
                            const directUrl = `https://${config.domain}/whw${config.id}`;
                            console.log(`\n  DIRECT VIDEO URL: ${directUrl}`);
                            
                            // Verify it's accessible
                            console.log('  Verifying URL...');
                            try {
                                const vidResp = await fetch(directUrl, {
                                    headers: {
                                        'Referer': 'https://abysscdn.com/?v=' + testId
                                    }
                                });
                                console.log(`  Video URL status: ${vidResp.status}`);
                                console.log(`  Content-Type: ${vidResp.headers['content-type'] || 'unknown'}`);
                                console.log(`  Content-Length: ${vidResp.headers['content-length'] || 'unknown'}`);
                                if (vidResp.status === 200 || vidResp.status === 206) {
                                    console.log('  *** VIDEO URL WORKS! ***');
                                }
                            } catch (e) {
                                console.log(`  Video verification error: ${e.message}`);
                            }
                        }
                    } catch (e) {
                        console.log(`  JSON parse error: ${e.message}`);
                    }
                } catch (e) {
                    console.log(`  Base64 decode error: ${e.message}`);
                }
            } else {
                // Check for other patterns
                const otherPatterns = [
                    resp.body.match(/var\s+player\s*=\s*\{[^}]+\}/),
                    resp.body.match(/file\s*:\s*["'](https?:\/\/[^"']+)/),
                    resp.body.match(/src\s*:\s*["'](https?:\/\/[^"']+)/),
                ];
                for (const p of otherPatterns) {
                    if (p) console.log(`  Found pattern: ${p[0].substring(0, 200)}`);
                }
                if (!otherPatterns.some(p => p)) {
                    console.log(`  No hydrax config found (page length: ${resp.body.length})`);
                    if (resp.body.length < 500) {
                        console.log(`  Full response: ${resp.body}`);
                    }
                }
            }
        } catch (e) {
            console.log(`  Error: ${e.message}`);
        }
    }
    
    console.log('\n=== DONE ===');
}

main().catch(console.error);
