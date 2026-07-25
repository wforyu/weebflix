const https = require('https');
const zlib = require('zlib');

function fetch(url) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        https.get({
            hostname: urlObj.hostname,
            path: urlObj.pathname + urlObj.search,
            headers: {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36',
                'Accept-Encoding': 'gzip, deflate',
                'Accept': '*/*',
                'Referer': 'https://drakor.kita.mobi/detail/the-husband-2026-v2e8/',
            },
            timeout: 15000,
            rejectUnauthorized: false
        }, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                res.resume();
                let loc = res.headers.location;
                if (loc.startsWith('/')) loc = `https://${urlObj.host}${loc}`;
                console.log(`  Redirect -> ${loc}`);
                return fetch(loc).then(resolve).catch(reject);
            }
            const chunks = [];
            res.on('data', c => chunks.push(c));
            res.on('end', () => {
                let body = Buffer.concat(chunks);
                if (res.headers['content-encoding'] === 'gzip') body = zlib.gunzipSync(body);
                else if (res.headers['content-encoding'] === 'deflate') body = zlib.inflateSync(body);
                resolve({ status: res.statusCode, headers: res.headers, body: body.toString('utf-8') });
            });
        }).on('error', reject);
    });
}

function post(url, body) {
    const urlObj = new URL(url);
    return new Promise((resolve, reject) => {
        const options = {
            hostname: urlObj.hostname,
            port: 443,
            path: urlObj.pathname + urlObj.search,
            method: 'POST',
            headers: {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36',
                'Content-Type': 'application/x-www-form-urlencoded',
                'Content-Length': Buffer.byteLength(body),
                'Origin': 'https://drakor.kita.mobi',
                'Referer': 'https://drakor.kita.mobi/detail/the-husband-2026-v2e8/',
                'X-Requested-With': 'XMLHttpRequest',
                'Accept': '*/*',
            },
            timeout: 15000,
            rejectUnauthorized: false
        };
        const req = https.request(options, (res) => {
            const chunks = [];
            res.on('data', c => chunks.push(c));
            res.on('end', () => {
                let data = Buffer.concat(chunks).toString('utf-8');
                resolve({ status: res.statusCode, headers: res.headers, body: data });
            });
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

async function main() {
    // The drakor site loads player via AJAX
    // Step 1: Get the episode page HTML
    console.log('=== Fetching episode page ===');
    const page = await fetch('https://drakor.kita.mobi/detail/the-husband-2026-v2e8/');
    console.log(`Page length: ${page.body.length}`);
    
    // The page has: onclick="loadEpisode('yLpA1nCVmw','hs','ind')"
    // This calls a function that does $.ajax POST to some endpoint
    // Let's find what endpoint by looking at all the script blocks
    
    // Extract ALL script content
    const scripts = [...page.body.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/gi)];
    console.log(`\nFound ${scripts.length} script blocks`);
    
    // Check each script for API URLs
    let allScriptContent = '';
    for (let i = 0; i < scripts.length; i++) {
        const content = scripts[i][1].trim();
        allScriptContent += content + '\n';
        if (content.length > 100 && content.length < 5000) {
            console.log(`\nScript #${i} (${content.length} chars):`);
            console.log(content.substring(0, 500));
        }
    }
    
    // The big obfuscated script has the API calls
    // But we can't run it directly
    // Let's try a completely different approach:
    // The page might have a "direct player" URL pattern
    // Some drakor sites use: /player/{movieId}/{server}/{lang}
    
    console.log('\n=== Trying direct player URLs ===');
    
    const playerUrls = [
        `https://drakor.kita.mobi/player/yLpA1nCVmw/hs/ind`,
        `https://drakor.kita.mobi/play/yLpA1nCVmw/hs/ind`,
        `https://drakor.kita.mobi/stream/yLpA1nCVmw/hs/ind`,
        `https://drakor.kita.mobi/embed/yLpA1nCVmw/hs/ind`,
        `https://drakor.kita.mobi/apicodes/yLpA1nCVmw/hs/ind`,
    ];
    
    for (const url of playerUrls) {
        try {
            const resp = await fetch(url);
            console.log(`\n${resp.status} ${url}`);
            if (resp.status === 200) {
                console.log(`  Length: ${resp.body.length}`);
                // Check for iframe, video, or hydrax references
                const iframeMatch = resp.body.match(/iframe[^>]+src=["']([^"']+)["']/);
                const videoMatch = resp.body.match(/src=["']([^"']+\.(?:mp4|m3u8|mpd)[^"']*)/);
                const abyssMatch = resp.body.match(/(?:abysscdn|hydrax|globalcdn)[^"'\s<>]*/);
                if (iframeMatch) console.log(`  iframe src: ${iframeMatch[1]}`);
                if (videoMatch) console.log(`  video src: ${videoMatch[1]}`);
                if (abyssMatch) console.log(`  abyss/hydrax: ${abyssMatch[0]}`);
            }
        } catch (e) {
            console.log(`  Error: ${e.message}`);
        }
    }
    
    // Step 2: Try the API with the correct approach
    // The API might need specific headers or cookies
    // Let's try the episode.php which DID work and see what data it gives us
    console.log('\n=== Fetching episode list via API ===');
    const epResp = await fetch('https://api.nonton.bid/c_api/episode.php?is_mob=0&is_uc=0&movie_id=yLpA1nCVmw&c=bfb1&t=1784892473%26ver%3D373iq');
    console.log(`episode.php status: ${epResp.status}`);
    if (epResp.status === 200) {
        try {
            const epJson = JSON.parse(epResp.body);
            console.log(`ptype: ${epJson.ptype}`);
            console.log(`server_xid: ${epJson.server_xid}`);
            console.log(`first_ep_id: ${epJson.first_ep_id}`);
            
            // Extract episode data attributes from HTML
            const epMatches = [...epResp.body.matchAll(/data-epid="([^"]+)"[^>]*class="[^"]*epz-(\d+)"/g)];
            console.log(`\nEpisodes found: ${epMatches.length}`);
            for (const m of epMatches.slice(0, 5)) {
                console.log(`  epid=${m[1]}, num=${m[2]}`);
            }
        } catch (e) {}
    }
    
    // Step 3: The p2p type suggests it uses a P2P player
    // Let's check if there's a different player endpoint for p2p
    console.log('\n=== Trying P2P player endpoints ===');
    
    const p2pUrls = [
        `https://drakor.kita.mobi/apicodes/yLpA1nCVmw/hs/ind`,
        `https://drakor.kita.mobi/apicodes/?movie_id=yLpA1nCVmw&cat=hs&tag=ind`,
    ];
    
    for (const url of p2pUrls) {
        try {
            const resp = await fetch(url);
            console.log(`\n${resp.status} ${url}`);
            if (resp.status === 200 && resp.body.length > 0) {
                console.log(`  Response (${resp.body.length} bytes): ${resp.body.substring(0, 500)}`);
                // Check for iframe
                const iframeMatch = resp.body.match(/iframe[^>]+src=["']([^"']+)["']/);
                if (iframeMatch) console.log(`  *** IFRAME: ${iframeMatch[1]} ***`);
            }
        } catch (e) {}
    }
    
    // Step 4: The HTML page has a div with id="apicodes-container"
    // This is where the player iframe goes after loadEpisode is called
    // The loadEpisode function probably does:
    //   $.post('/apicodes/', { movie_id, server, lang, ... })
    //   and puts the response HTML in #apicodes-container
    
    console.log('\n=== Trying POST to /apicodes/ ===');
    const apicodesBodies = [
        'movie_id=yLpA1nCVmw&cat=hs&tag=ind',
        'movie_id=yLpA1nCVmw&server=hs&lang=ind',
        'movie_id=yLpA1nCVmw&c=bfb1&t=1784892473%26ver%3D373iq',
        'is_mob=0&is_uc=0&movie_id=yLpA1nCVmw&cat=hs&tag=ind&c=bfb1&t=1784892473%26ver%3D373iq',
        'is_mob=0&is_uc=0&movie_id=yLpA1nCVmw&cat=hs&tag=ind&server_xid=f1&c=bfb1&t=1784892473%26ver%3D373iq',
    ];
    
    for (const body of apicodesBodies) {
        try {
            const resp = await post('https://drakor.kita.mobi/apicodes/', body);
            console.log(`\nPOST /apicodes/ (${body})`);
            console.log(`  Status: ${resp.status}`);
            if (resp.body.length > 0) {
                console.log(`  Response (${resp.body.length} bytes): ${resp.body.substring(0, 500)}`);
                const iframeMatch = resp.body.match(/iframe[^>]+src=["']([^"']+)["']/);
                if (iframeMatch) console.log(`  *** IFRAME FOUND: ${iframeMatch[1]} ***`);
                const abyssMatch = resp.body.match(/(?:abysscdn|hydrax|globalcdn|playhydrax|player-cdn)[^"'\s<>]*/gi);
                if (abyssMatch) console.log(`  *** ABYSS/HYDRAX: ${abyssMatch.join(', ')} ***`);
            }
        } catch (e) {
            console.log(`  Error: ${e.message}`);
        }
    }
    
    // Also try /ajax/ endpoint
    console.log('\n=== Trying /ajax/ endpoint ===');
    for (const body of apicodesBodies) {
        try {
            const resp = await post('https://drakor.kita.mobi/ajax/', body);
            console.log(`\nPOST /ajax/ (${body})`);
            console.log(`  Status: ${resp.status}`);
            if (resp.body.length > 0) {
                console.log(`  Response (${resp.body.length} bytes): ${resp.body.substring(0, 500)}`);
            }
        } catch (e) {}
    }
    
    console.log('\n=== DONE ===');
}

main().catch(console.error);
