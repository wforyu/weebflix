// Extract API endpoints from DrakorKita's obfuscated a.js
// Strategy: Stub jQuery $.ajax to capture URLs, bypass anti-debugging

const fs = require('fs');
const path = require('path');

const aJsPath = path.join(__dirname, '.local', 'share', 'opencode', 'tool-output', 'tool_f9414da790017KPZ1vlRldkOJh');
const htmlPath = path.join(__dirname, '.local', 'share', 'opencode', 'tool-output', 'tool_f9411da680010TTQMIpOHOYjN9');

let aJs = fs.readFileSync(aJsPath, 'utf8');
let html = fs.readFileSync(htmlPath, 'utf8');

// Extract c_api_host from HTML if it's defined there
const cApiHostMatch = html.match(/var\s+c_api_host\s*=\s*['"]([^'"]+)['"]/);
if (cApiHostMatch) {
    console.log('c_api_host from HTML:', cApiHostMatch[1]);
}

// Extract GLOBAL_MOVIE_ID
const globalMatch = html.match(/GLOBAL_MOVIE_ID\s*=\s*['"]([^'"]*)['"]/);
if (globalMatch) {
    console.log('GLOBAL_MOVIE_ID from HTML:', globalMatch[1]);
}

// Extract is_mob, is_uc, c, t values from HTML
const varsToFind = ['is_mob', 'is_uc', 'is_m', 'c', 't'];
for (const v of varsToFind) {
    const re = new RegExp(`(?:var\\s+)?${v}\\s*=\\s*(['"][^'"]*['"]|\\d+)`);
    const m = html.match(re);
    if (m) console.log(`${v} from HTML:`, m[1]);
}

// Also check the a.js for c_api_host
const cApiHostInJs = aJs.match(/c_api_host\s*=\s*['"]([^'"]+)['"]/);
if (cApiHostInJs) {
    console.log('c_api_host from a.js:', cApiHostInJs[1]);
}

// Build stubs
const stubs = `
var window = { location: { href: 'https://xdrakor33.nicewap.sbs/', pathname: '/', origin: 'https://xdrakor33.nicewap.sbs' }, history: { pushState: function(){}, replaceState: function(){} }, innerWidth: 360, innerHeight: 640 };
var document = { 
    querySelector: function(s) { return { style: {}, classList: { add: function(){} }, addEventListener: function(){}, getAttribute: function(){ return ''; }, innerHTML: '', textContent: '', childNodes: [], appendChild: function(){}, removeChild: function(){} }; },
    querySelectorAll: function(s) { return []; },
    getElementById: function(s) { return { style: {}, innerHTML: '', textContent: '', value: '', classList: { add: function(){}, remove: function(){} } }; },
    createElement: function(s) { return { style: {}, setAttribute: function(){}, addEventListener: function(){}, src: '', innerHTML: '' }; },
    body: { appendChild: function(){}, removeChild: function(){}, style: {} },
    documentElement: { style: {} },
    cookie: ''
};
var navigator = { userAgent: 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36', platform: 'Linux', language: 'id' };
var location = window.location;
var history = window.history;
var XMLHttpRequest = function() { 
    this.open = function(){}; 
    this.send = function(){}; 
    this.setRequestHeader = function(){}; 
};
var setTimeout = function(fn, ms) { /* skip */ };
var setInterval = function() { return 1; };
var clearTimeout = function(){};
var clearInterval = function(){};
var console = { log: function(){}, error: function(){}, warn: function(){}, info: function(){} };
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

// jQuery stub - intercept $.ajax
var captured = [];
var $ = function(selector) {
    return {
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
        clone: function() { return this; },
        eq: function(i) { return this; },
        first: function() { return this; },
        last: function() { return this; },
        siblings: function() { return this; },
        children: function(s) { return this; },
        closest: function(s) { return this; },
        index: function() { return 0; },
        is: function(s) { return false; },
        not: function(s) { return this; },
        filter: function(s) { return this; },
        fadeOut: function() { return this; },
        fadeIn: function() { return this; },
        slideUp: function() { return this; },
        slideDown: function() { return this; },
        animate: function() { return this; },
        scrollTop: function(v) { return this; },
        scrollLeft: function(v) { return this; },
        offset: function() { return { top: 0, left: 0 }; },
        position: function() { return { top: 0, left: 0 }; },
        width: function() { return 360; },
        height: function() { return 640; },
        outerWidth: function() { return 360; },
        outerHeight: function() { return 640; },
        innerWidth: function() { return 360; },
        innerHeight: function() { return 640; },
        prop: function(k, v) { return this; },
        removeProp: function(k) { return this; },
        wrap: function(h) { return this; },
        unwrap: function() { return this; },
        after: function(c) { return this; },
        before: function(c) { return this; },
        replaceWith: function(c) { return this; },
        detach: function() { return this; },
        clone: function() { return this; },
        DOMContentLoaded: function() {}
    };
};
$.ajax = function(opts) {
    captured.push({ url: opts.url, method: opts.method || opts.type, dataType: opts.dataType, success: opts.success });
    console.log('AJAX INTERCEPTED:', opts.method || 'GET', opts.url);
    // Simulate a response that won't crash
    if (opts.success) {
        try {
            opts.success(JSON.stringify({}));
        } catch(e) {}
    }
    return { done: function(fn){ if(fn) fn(); return this; }, fail: function(fn){ return this; }, always: function(fn){ return this; } };
};
$.get = function(url, fn) {
    captured.push({ url: url, method: 'GET' });
    console.log('$.get INTERCEPTED:', url);
    if (fn) fn('{}');
    return { done: function(f){ if(f) f(); return this; }, fail: function(f){ return this; } };
};
$.post = function(url, data, fn) {
    captured.push({ url: url, method: 'POST', data: data });
    console.log('$.post INTERCEPTED:', url);
    if (fn) fn('{}');
    return { done: function(f){ if(f) f(); return this; }, fail: function(f){ return this; } };
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
`;

// Now extract the _0x1c23 array and decoder function from a.js
// First, find and remove the anti-debugging loops
let modifiedJs = aJs;

// Replace debugger statements
modifiedJs = modifiedJs.replace(/debugger\s*;/g, '/* debugger */');

// Now prepend our stubs
let fullScript = stubs + '\n' + modifiedJs + '\n';

// Append extraction code
fullScript += `
console.log('\\n=== EXTRACTED API ENDPOINTS ===');
console.log(JSON.stringify(captured, null, 2));
if (typeof c_api_host !== 'undefined') console.log('c_api_host =', c_api_host);
if (typeof GLOBAL_MOVIE_ID !== 'undefined') console.log('GLOBAL_MOVIE_ID =', GLOBAL_MOVIE_ID);
if (typeof is_mob !== 'undefined') console.log('is_mob =', is_mob);
if (typeof is_uc !== 'undefined') console.log('is_uc =', is_uc);
`;

const outPath = path.join(__dirname, 'drakor_intercept.js');
fs.writeFileSync(outPath, fullScript);
console.log('Script written to:', outPath);
console.log('Run with: node drakor_intercept.js');
