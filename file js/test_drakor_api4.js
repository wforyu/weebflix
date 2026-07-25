const https = require('https');
const zlib = require('zlib');
const fs = require('fs');

function fetch(url) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const options = {
            hostname: urlObj.hostname,
            path: urlObj.pathname + urlObj.search,
            headers: {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
                'Accept-Encoding': 'gzip, deflate',
            },
            timeout: 15000,
            rejectUnauthorized: false
        };
        const req = https.get(options, (res) => {
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
        });
        req.on('error', reject);
    });
}

async function main() {
    console.log('=== Fetching episode page ===');
    const html = await fetch('https://drakor.kita.mobi/detail/the-husband-2026-v2e8/');
    console.log(`Page length: ${html.length}`);
    
    // Extract the big obfuscated script
    const scripts = [...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/gi)];
    let bigScript = '';
    for (const s of scripts) {
        if (s[1].length > 5000) {
            bigScript = s[1].trim();
            break;
        }
    }
    console.log(`Big script: ${bigScript.length} chars`);
    
    // Save it for analysis
    fs.writeFileSync('C:/Users/pro021/weebflix/file js/current_page_script.js', bigScript);
    console.log('Saved to current_page_script.js');
    
    // The script uses a custom encoding. Let's try to find and extract the decoder.
    // Structure: var _0x1c23 = function(){...}; function _0x5451(hex, key){...}
    // + IIFE: (function(_0x323382, _0x5d7501){...})(_0x1c23, 0xc3dd0);
    
    // But the current script might use different function names
    // Let's look for the pattern: function followed by hex strings array
    
    // Find array initialization (big string array)
    const arrayMatch = bigScript.match(/function\s+(\w+)\(\)\{var\s+\w+=\[['"]/);
    if (arrayMatch) {
        console.log(`Array function found: ${arrayMatch[1]}`);
    }
    
    // Find the decoder function (RC4 + base64)
    const decoderMatch = bigScript.match(/function\s+(\w+)\(_0x\w+,_0x\w+\)\{_0x\w+=_0x\w+-0x1e[bf]/);
    if (decoderMatch) {
        console.log(`Decoder function found: ${decoderMatch[1]}`);
    }
    
    // Let's try a different approach: extract the first 2000 chars of the script
    // to understand its structure
    console.log('\n=== Script structure (first 500 chars) ===');
    console.log(bigScript.substring(0, 500));
    
    console.log('\n=== Script structure (chars 500-1000) ===');
    console.log(bigScript.substring(500, 1000));
    
    // Find the IIFE pattern
    const iifeMatch = bigScript.match(/\(function\(\w+,\s*\w+\)\s*\{[\s\S]*?\}\)\(\w+,\s*(0x[a-f0-9]+)\)/);
    if (iifeMatch) {
        console.log(`\nIIFE found with argument: ${iifeMatch[1]}`);
    }
    
    // Try to find the actual function names by looking at the script beginning
    // The structure is usually: var _0xNNNN = [...]; function _0xMMMM(){...} function _0xPPPP(hex,key){...}; var _0xQQQQ=_0xPPPP; (function(){...})(_0xNNNN, 0xNNNNN);
    
    // Find variable declarations at the start
    const varDecls = bigScript.match(/^[\s;]*(var\s+\w+\s*=\s*(?:function|\[|'|"|\d))/);
    console.log(`\nStarts with: ${bigScript.substring(0, 100)}`);
    
    // The script starts with (()=>{var K='...'.split("").reduce(...)}) which is an anti-debug wrapper
    // Then the actual code follows
    
    // Let's find the actual code after the anti-debug
    const mainCodeStart = bigScript.indexOf('var _0x');
    if (mainCodeStart !== -1) {
        console.log(`\nMain code starts at position: ${mainCodeStart}`);
        console.log(`Context: ${bigScript.substring(mainCodeStart, mainCodeStart + 300)}`);
    }
    
    // Alternative: run the whole script with stubs and intercept $.ajax calls
    console.log('\n=== Attempting to run script with stubs ===');
    
    try {
        // Create a fake environment and run the script
        const fakeScript = `
var window = { 
    location: { href: 'https://drakor.kita.mobi/detail/the-husband-2026-v2e8/', pathname: '/detail/the-husband-2026-v2e8/', origin: 'https://drakor.kita.mobi', hash: '', search: '', host: 'drakor.kita.mobi', hostname: 'drakor.kita.mobi', protocol: 'https:' },
    history: { pushState: function(){}, replaceState: function(){} },
    innerWidth: 360, innerHeight: 640,
    addEventListener: function(){},
    removeEventListener: function(){},
    postMessage: function(){},
    performance: { now: function(){ return Date.now(); } }
};
var document = { 
    querySelector: function(s) { return { style: {}, classList: { add: function(){}, remove: function(){} }, addEventListener: function(){}, getAttribute: function(){ return ''; }, innerHTML: '', textContent: '', childNodes: [], appendChild: function(){}, removeChild: function(){}, setAttribute: function(){} }; },
    querySelectorAll: function(s) { return []; },
    getElementById: function(s) { return { style: {}, innerHTML: '', textContent: '', value: '', classList: { add: function(){}, remove: function(){} }, setAttribute: function(){}, getAttribute: function(){ return '' }, appendChild: function(){}, removeChild: function(){}, addEventListener: function(){} }; },
    createElement: function(s) { return { style: {}, setAttribute: function(){}, addEventListener: function(){}, src: '', innerHTML: '', appendChild: function(){}, contentWindow: { document: { body: { appendChild: function(){} } } } }; },
    body: { appendChild: function(){}, removeChild: function(){}, style: {}, getElementsByTagName: function(){ return []; } },
    documentElement: { style: {} },
    cookie: '',
    getElementsByTagName: function(s) { return []; },
    createTextNode: function(t) { return {}; },
    createEvent: function() { return { initEvent: function(){} }; }
};
var navigator = { userAgent: 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36', platform: 'Linux', language: 'id', cookieEnabled: true };
var location = window.location;
var history = window.history;
var XMLHttpRequest = function() { 
    this.open = function(m, u) { this._url = u; this._method = m; };
    this.send = function(d) { console.log('XHR: ' + this._method + ' ' + this._url + (d ? ' DATA:' + d : '')); };
    this.setRequestHeader = function(){};
    this.getResponseHeader = function(){ return ''; };
    this.getAllResponseHeaders = function(){ return ''; };
    this.readyState = 4;
    this.status = 200;
    this.responseText = '{}';
};
XMLHttpRequest.DONE = 4;
var setTimeout = function(fn, ms) { if (ms < 1000) try { fn(); } catch(e) {} return 1; };
var setInterval = function() { return 1; };
var clearTimeout = function(){};
var clearInterval = function(){};
var console = { log: function(){} };
var JSON_parse = JSON.parse;
var parseInt = parseInt;
var parseFloat = parseFloat;
var RegExp = RegExp;
var Array = Array;
var Object = Object;
var String = String;
var Number = Number;
var Math = Math;
var Date = Date;
var Error = Error;
var TypeError = TypeError;
var encodeURIComponent = encodeURIComponent;
var decodeURIComponent = decodeURIComponent;
var escape = escape;
var unescape = unescape;
var atob = function(s) { return Buffer.from(s, 'base64').toString('binary'); };
var btoa = function(s) { return Buffer.from(s, 'binary').toString('base64'); };
var fetch = function() { return Promise.resolve({ ok: true, json: function() { return Promise.resolve({}); }, text: function() { return Promise.resolve(''); } }); };

var ajaxUrls = [];
var $ = function(sel) {
    return {
        ready: function(fn) { try { fn(); } catch(e) {} return this; },
        html: function(v) { return this; },
        text: function(v) { return this; },
        val: function(v) { return this; },
        attr: function(k, v) { return this; },
        css: function(k, v) { return this; },
        show: function() { return this; },
        hide: function() { return this; },
        addClass: function(c) { return this; },
        removeClass: function(c) { return this; },
        append: function(c) { return this; },
        remove: function() { return this; },
        find: function(s) { return this; },
        parent: function() { return this; },
        length: 1,
        get: function(i) { return null; },
        each: function(fn) { return this; },
        on: function(e, fn) { return this; },
        off: function(e) { return this; },
        trigger: function(e) { return this; },
        data: function(k, v) { return this; },
        empty: function() { return this; },
        click: function(fn) { if (typeof fn === 'function') try { fn(); } catch(e) {} return this; },
        focus: function() { return this; },
        blur: function() { return this; },
        change: function() { return this; },
        keyup: function() { return this; },
        keydown: function() { return this; },
        submit: function(fn) { return this; },
        fadeIn: function() { return this; },
        fadeOut: function() { return this; },
        slideUp: function() { return this; },
        slideDown: function() { return this; },
        animate: function() { return this; },
        scrollTop: function() { return this; },
        scrollLeft: function() { return this; },
        offset: function() { return { top: 0, left: 0 }; },
        position: function() { return { top: 0, left: 0 }; },
        width: function() { return 360; },
        height: function() { return 640; },
        outerWidth: function() { return 360; },
        outerHeight: function() { return 640; },
        innerWidth: function() { return 360; },
        innerHeight: function() { return 640; },
        prop: function(k, v) { return this; },
        removeProp: function() { return this; },
        wrap: function() { return this; },
        unwrap: function() { return this; },
        after: function() { return this; },
        before: function() { return this; },
        replaceWith: function() { return this; },
        detach: function() { return this; },
        clone: function() { return this; },
        eq: function() { return this; },
        first: function() { return this; },
        last: function() { return this; },
        siblings: function() { return this; },
        children: function() { return this; },
        closest: function() { return this; },
        index: function() { return 0; },
        is: function() { return false; },
        not: function() { return this; },
        filter: function() { return this; },
        map: function() { return this; },
    };
};
$.ajax = function(opts) {
    ajaxUrls.push({ url: opts.url, method: opts.method || opts.type, data: opts.data, dataType: opts.dataType });
    console.log('AJAX: ' + (opts.method || 'GET') + ' ' + opts.url);
    if (opts.success) {
        try { opts.success(JSON.stringify({})); } catch(e) {}
    }
    if (opts.complete) {
        try { opts.complete(); } catch(e) {}
    }
    return { done: function(f) { if (f) try { f(); } catch(e) {} return this; }, fail: function(f) { return this; }, always: function(f) { if (f) try { f(); } catch(e) {} return this; } };
};
$.get = function(url, fn) {
    ajaxUrls.push({ url: url, method: 'GET' });
    console.log('$.get: ' + url);
    if (fn) fn('{}');
    return { done: function(f) { if (f) f(); return this; }, fail: function(f) { return this; } };
};
$.post = function(url, data, fn) {
    if (typeof data === 'function') { fn = data; data = ''; }
    ajaxUrls.push({ url: url, method: 'POST', data: data });
    console.log('$.post: ' + url);
    if (fn) fn('{}');
    return { done: function(f) { if (f) f(); return this; }, fail: function(f) { return this; } };
};
$.fn = {};
$.extend = function(target) { return target || {}; };
$.each = function(obj, fn) { return obj; };
$.inArray = function(v, arr) { return arr ? arr.indexOf(v) : -1; };
$.isArray = function(v) { return Array.isArray(v); };
$.type = function(v) { return typeof v; };
$.parseJSON = function(s) { return JSON.parse(s); };
$.trim = function(s) { return (s||'').trim(); };
$.param = function(obj) { return ''; };
$.isFunction = function(v) { return typeof v === 'function'; };
$.isEmptyObject = function(obj) { return true; };
$.makeArray = function(obj) { return Array.isArray(obj) ? obj : [obj]; };
$.map = function(arr, fn) { return (arr || []).map(fn); };
$.grep = function(arr, fn) { return (arr || []).filter(fn); };
$.merge = function(a, b) { return (a || []).concat(b || []); };
$.Deferred = function() { return { resolve: function(){ return this; }, reject: function(){ return this; }, done: function(){ return this; }, fail: function(){ return this; }, always: function(){ return this; }, promise: function(){ return this; } }; };
$.when = function() { return $.Deferred(); };
$.css = function() {};
$.data = function() {};
$.event = { special: {} };

var FrameBuilder = { build: function() { return { src: '' }; } };
`;
        
        // Run the script and capture output
        const output = [];
        const origLog = console.log;
        console.log = function() {
            output.push(Array.from(arguments).join(' '));
        };
        
        // Replace the console.log in fakeScript too
        const scriptToRun = fakeScript.replace('var console = { log: function(){} };', 
            'var console = { log: function() { output.push(Array.from(arguments).join(" ")); } };');
        
        // Can't eval in Node directly like this, let's use a different approach
        // Just extract the function names from the script
        
        // Find all function declarations
        const funcNames = [...bigScript.matchAll(/function\s+(_0x[a-f0-9]+)\s*\(/g)].map(m => m[1]);
        console.log(`\nFunctions found: ${[...new Set(funcNames)].join(', ')}`);
        
        // Find variable names that look like the decoder
        const varNames = [...bigScript.matchAll(/var\s+(_0x[a-f0-9]+)\s*=/g)].map(m => m[1]);
        console.log(`Variables found: ${[...new Set(varNames)].join(', ')}`);
        
    } catch(e) {
        console.log(`Script analysis error: ${e.message}`);
    }
    
    console.log('\n=== DONE ===');
}

main().catch(console.error);
