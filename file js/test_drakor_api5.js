const https = require('https');
const zlib = require('zlib');
const fs = require('fs');

function fetch(url) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        https.get({
            hostname: urlObj.hostname,
            path: urlObj.pathname + urlObj.search,
            headers: {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36',
                'Accept-Encoding': 'gzip, deflate',
            },
            timeout: 15000,
            rejectUnauthorized: false
        }, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                res.resume();
                let loc = res.headers.location;
                if (loc.startsWith('/')) loc = `https://${urlObj.host}${loc}`;
                return fetch(loc).then(resolve).catch(reject);
            }
            const chunks = [];
            res.on('data', c => chunks.push(c));
            res.on('end', () => {
                let body = Buffer.concat(chunks);
                if (res.headers['content-encoding'] === 'gzip') body = zlib.gunzipSync(body);
                else if (res.headers['content-encoding'] === 'deflate') body = zlib.inflateSync(body);
                resolve(body.toString('utf-8'));
            });
        }).on('error', reject);
    });
}

async function main() {
    // Step 1: Extract the decoder from the current page and run it
    const html = await fetch('https://drakor.kita.mobi/detail/the-husband-2026-v2e8/');
    
    const scripts = [...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/gi)];
    let bigScript = '';
    for (const s of scripts) {
        if (s[1].trim().length > 5000) {
            bigScript = s[1].trim();
            break;
        }
    }
    
    // The script structure in drakor sites:
    // 1. An IIFE that decodes the string array: (()=>{var K='...'.split("").reduce(...).split("z"); ...})
    // 2. Then more IIFEs that use the decoder
    
    // Let's find the pattern where loadEpisode URL is built
    // Look for patterns that build URL with api_host or c_api_host
    
    // Actually, the drakor site uses a COMPLETELY different obfuscation than what we had before
    // The old site used _0x1c23 / _0x5451, but the new drakor.kita.mobi uses a different scheme
    
    // Let's find the actual loadEpisode call in the decoded script
    // The function name 'loadEpisode' appears in the HTML onclick but may not appear literally in the JS
    
    // Let's try: extract the decoded K array from the script
    const kMatch = bigScript.match(/var K='([^']+)'\.split/);
    if (kMatch) {
        console.log(`Found K array: ${kMatch[1].length} chars`);
    }
    
    // Actually let me just look for the key patterns
    // The script decodes to strings like 'loadEpisode', 'c_api_host', 'ajax', etc.
    // Let's search for these decoded strings in the raw script
    
    // Search for common API-related strings
    const searchTerms = [
        'loadEpisode', 'c_api_host', 'api_host', 'file_host',
        'server.php', 'episode.php', 'abysscdn', 'hydrax',
        'nonton.bid', 'globalcdn', 'GLOBAL_MOVIE_ID'
    ];
    
    for (const term of searchTerms) {
        const idx = bigScript.indexOf(term);
        if (idx !== -1) {
            const start = Math.max(0, idx - 100);
            const end = Math.min(bigScript.length, idx + 200);
            console.log(`\nFound '${term}' at position ${idx}:`);
            console.log(bigScript.substring(start, end));
        }
    }
    
    // Let's try another approach: run the script in a vm context
    console.log('\n=== Trying VM execution ===');
    const vm = require('vm');
    
    const sandbox = {
        console: { log: (...args) => output.push(args.join(' ')), error: () => {}, warn: () => {} },
        window: { 
            location: { href: 'https://drakor.kita.mobi/detail/the-husband-2026-v2e8/', origin: 'https://drakor.kita.mobi', pathname: '/detail/the-husband-2026-v2e8/' },
            history: { pushState: function(){}, replaceState: function(){} },
            innerWidth: 360, innerHeight: 640,
            addEventListener: function(){}, removeEventListener: function(){},
            postMessage: function(){},
            performance: { now: () => Date.now() }
        },
        document: { 
            querySelector: function() { return { style: {}, classList: { add: ()=>{}, remove: ()=>{} }, addEventListener: ()=>{}, getAttribute: ()=>'', innerHTML: '', textContent: '', appendChild: ()=>{}, removeChild: ()=>{}, setAttribute: ()=>{} }; },
            querySelectorAll: function() { return []; },
            getElementById: function() { return { style: {}, innerHTML: '', textContent: '', value: '', classList: { add: ()=>{}, remove: ()=>{} }, setAttribute: ()=>{}, getAttribute: ()=>'', appendChild: ()=>{}, removeChild: ()=>{}, addEventListener: ()=>{} }; },
            createElement: function() { return { style: {}, setAttribute: ()=>{}, addEventListener: ()=>{}, src: '', innerHTML: '', appendChild: ()=>{}, contentWindow: { document: { body: { appendChild: ()=>{} } } } }; },
            body: { appendChild: ()=>{}, removeChild: ()=>{}, style: {}, getElementsByTagName: () => ({ length: 0 }) },
            documentElement: { style: {} },
            cookie: '',
            getElementsByTagName: () => ({ length: 0 }),
            createTextNode: () => ({}),
            createEvent: () => ({ initEvent: ()=>{} })
        },
        navigator: { userAgent: 'Mozilla/5.0', platform: 'Linux', language: 'id', cookieEnabled: true },
        location: { href: 'https://drakor.kita.mobi/detail/the-husband-2026-v2e8/', origin: 'https://drakor.kita.mobi' },
        history: { pushState: ()=>{}, replaceState: ()=>{} },
        XMLHttpRequest: function() {
            this.open = function(m, u) { this._url = u; this._method = m; };
            this.send = function(d) { output.push('XHR: ' + this._method + ' ' + this._url); };
            this.setRequestHeader = function(){};
            this.getResponseHeader = () => '';
            this.getAllResponseHeaders = () => '';
            this.readyState = 4; this.status = 200; this.responseText = '{}';
        },
        setTimeout: function(fn, ms) { if (ms < 1000) try { fn(); } catch(e) {} return 1; },
        setInterval: () => 1,
        clearTimeout: ()=>{},
        clearInterval: ()=>{},
        JSON, parseInt, parseFloat, RegExp, Array, Object, String, Number, Math, Date, Error, TypeError,
        encodeURIComponent, decodeURIComponent, escape, unescape,
        atob: (s) => Buffer.from(s, 'base64').toString('binary'),
        btoa: (s) => Buffer.from(s, 'binary').toString('base64'),
        fetch: () => Promise.resolve({ ok: true, json: () => Promise.resolve({}), text: () => Promise.resolve('') }),
        Promise, Map, Set, Symbol, Proxy, WeakMap, WeakSet, Reflect, BigInt, DataView, ArrayBuffer,
        Uint8Array, Int8Array, Uint16Array, Int16Array, Uint32Array, Int32Array, Float32Array, Float64Array,
        RegExp: RegExp,
        Image: function() { return { src: '', onload: null, onerror: null }; },
        Audio: function() { return { src: '', play: ()=>{}, pause: ()=>{} }; },
        Element: function() {},
        HTMLDivElement: function() {},
        HTMLIFrameElement: function() {},
        Node: function() {},
        MutationObserver: function() { return { observe: ()=>{}, disconnect: ()=>{} }; },
        event: { preventDefault: ()=>{} },
        addEventListener: function(){},
        removeEventListener: function(){},
    };
    
    // Add jQuery stubs
    const capturedAjax = [];
    const jqStub = function(sel) {
        return new Proxy({}, {
            get: function(target, prop) {
                if (prop === Symbol.toPrimitive) return () => '';
                if (prop === 'length') return 1;
                if (prop === '0') return null;
                return function() { return jqStub(); };
            }
        });
    };
    jqStub.ajax = function(opts) {
        capturedAjax.push(opts);
        output.push('AJAX: ' + (opts.type || opts.method || 'GET') + ' ' + opts.url);
        if (opts.success) try { opts.success(JSON.stringify({})); } catch(e) {}
        if (opts.complete) try { opts.complete(); } catch(e) {}
        return { done: function(f) { if (f) try { f(); } catch(e) {} return this; }, fail: function() { return this; }, always: function(f) { if (f) try { f(); } catch(e) {} return this; } };
    };
    jqStub.get = function(url, fn) {
        capturedAjax.push({ url: url, type: 'GET' });
        output.push('$.get: ' + url);
        if (fn) try { fn('{}'); } catch(e) {}
        return { done: function(f) { if (f) f(); return this; }, fail: function() { return this; } };
    };
    jqStub.post = function(url, data, fn) {
        if (typeof data === 'function') { fn = data; data = ''; }
        capturedAjax.push({ url: url, type: 'POST', data: data });
        output.push('$.post: ' + url);
        if (fn) try { fn('{}'); } catch(e) {}
        return { done: function(f) { if (f) f(); return this; }, fail: function() { return this; } };
    };
    jqStub.fn = {};
    jqStub.extend = (t) => t || {};
    jqStub.each = (o, fn) => o;
    jqStub.inArray = (v, a) => a ? a.indexOf(v) : -1;
    jqStub.isArray = (v) => Array.isArray(v);
    jqStub.type = (v) => typeof v;
    jqStub.parseJSON = (s) => JSON.parse(s);
    jqStub.trim = (s) => (s||'').trim();
    jqStub.param = () => '';
    jqStub.isFunction = (v) => typeof v === 'function';
    jqStub.isEmptyObject = () => true;
    jqStub.makeArray = (o) => Array.isArray(o) ? o : [o];
    jqStub.map = (a, fn) => (a || []).map(fn);
    jqStub.grep = (a, fn) => (a || []).filter(fn);
    jqStub.merge = (a, b) => (a || []).concat(b || []);
    jqStub.Deferred = () => ({ resolve: ()=>this, reject: ()=>this, done: ()=>this, fail: ()=>this, always: ()=>this, promise: ()=>this });
    jqStub.when = () => jqStub.Deferred();
    jqStub.css = () => {};
    jqStub.data = () => {};
    jqStub.event = { special: {} };
    sandbox.$ = jqStub;
    sandbox.jQuery = jqStub;
    
    const output = [];
    sandbox.console = { log: (...args) => output.push(args.join(' ')), error: () => {}, warn: () => {} };
    
    try {
        const context = vm.createContext(sandbox);
        vm.runInContext(bigScript, context, { timeout: 5000 });
    } catch(e) {
        output.push('VM Error: ' + e.message);
    }
    
    console.log(`\nVM output (${output.length} lines):`);
    for (const line of output) {
        if (line.length > 0) console.log(`  ${line}`);
    }
    
    if (capturedAjax.length > 0) {
        console.log(`\nCaptured ${capturedAjax.length} AJAX calls:`);
        for (const a of capturedAjax) {
            console.log(`  ${a.type || 'GET'} ${a.url}`);
            if (a.data) console.log(`  Data: ${typeof a.data === 'string' ? a.data : JSON.stringify(a.data)}`);
        }
    }
    
    console.log('\n=== DONE ===');
}

main().catch(console.error);
