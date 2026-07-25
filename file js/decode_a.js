// Minimal stubs to run a.js and capture API calls
const fs = require('fs');
const path = require('path');

const aJs = fs.readFileSync('C:\\Users\\pro021\\.local\\share\\opencode\\tool-output\\tool_f9414da790017KPZ1vlRldkOJh', 'utf8');

// Extract just the _0x1c23 array and _0x5451 function
// The a.js starts with: var _0x8730fc=_0x5451; ... (function(_0x1c23,0xc3dd0){...})(_0x1c23,0xc3dd0)
// _0x1c23 is defined somewhere in the file

// Let's extract the string array _0x1c23
const arrMatch = aJs.match(/var _0x1c23\s*=\s*(\[[\s\S]*?\]);/);
if (arrMatch) {
    console.log('Found _0x1c23 array, length:', arrMatch[1].substring(0, 200));
} else {
    console.log('Could not find _0x1c23 array');
}

// Try another pattern - the array might be defined differently
const arrMatch2 = aJs.match(/_0x1c23\s*=\s*(\[[^\]]*\])/);
if (arrMatch2) {
    console.log('Found _0x1c23 v2:', arrMatch2[1].substring(0, 200));
}

// Let's look for the _0x5451 function definition
const decoderMatch = aJs.match(/function _0x5451\([^)]*\)\s*\{[\s\S]*?return[\s\S]*?\}/);
if (decoderMatch) {
    console.log('Found _0x5451 function:', decoderMatch[0].substring(0, 300));
}

// Alternative: just eval with stubs
const stubs = `
var _0x1c23 = _0x1c23 || [];
var window = { location: { href: 'https://xdrakor33.nicewap.sbs/', pathname: '/', origin: 'https://xdrakor33.nicewap.sbs' }, history: { pushState: function(){}, replaceState: function(){} }, innerWidth: 360, innerHeight: 640 };
var document = { 
    querySelector: function(s) { return { style: {}, classList: { add: function(){} }, addEventListener: function(){}, getAttribute: function(){ return ''; }, innerHTML: '', textContent: '', childNodes: [], appendChild: function(){}, removeChild: function(){} }; },
    querySelectorAll: function(s) { return []; },
    getElementById: function(s) { return { style: {}, innerHTML: '', textContent: '', value: '', classList: { add: function(){}, remove: function(){} } }; },
    createElement: function(s) { return { style: {}, setAttribute: function(){}, addEventListener: function(){}, src: '', innerHTML: '' }; },
    body: { appendChild: function(){}, removeChild: function(){}, style: {} },
    documentElement: { style: {} },
    cookie: '',
    getElementsByTagName: function() { return []; }
};
var navigator = { userAgent: 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36', platform: 'Linux', language: 'id' };
var location = window.location;
var history = window.history;
var XMLHttpRequest = function() { this.open = function(){}; this.send = function(){}; this.setRequestHeader = function(){}; };
var setTimeout = function(fn, ms) { return 1; };
var setInterval = function() { return 1; };
var clearTimeout = function(){};
var clearInterval = function(){};
var console = { log: function(){}, error: function(){}, warn: function(){}, info: function(){} };
var parseInt = parseInt;
var parseFloat = parseFloat;
var JSON_parse = JSON.parse;
var atob = function(s) { return Buffer.from(s, 'base64').toString('binary'); };

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
    };
};
$.ajax = function(opts) {
    console.log('AJAX:', opts.method || 'GET', opts.url);
    if (opts.success) { try { opts.success(JSON.stringify({})); } catch(e) {} }
    return { done: function(f){ if(f) f(); return this; }, fail: function(f){ return this; }, always: function(f){ return this; } };
};
$.get = function(url, fn) {
    console.log('$.get:', url);
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
$.Deferred = function() { return { resolve: function(){ return this; }, reject: function(){ return this; }, done: function(){ return this; }, fail: function(){ return this; }, always: function(){ return this; }, promise: function(){ return this; } }; };
$.when = function() { return $.Deferred(); };
$.isFunction = function(v) { return typeof v === 'function'; };
$.isEmptyObject = function() { return true; };
$.makeArray = function(v) { return Array.isArray(v) ? v : [v]; };
$.map = function(arr, fn) { return (arr||[]).map(fn); };
$.grep = function(arr, fn) { return (arr||[]).filter(fn); };
$.merge = function(a, b) { return (a||[]).concat(b||[]); };
$.css = function() {};
$.data = function() {};
$.event = { special: {} };
var GLOBAL_MOVIE_ID = 'test123';
`;

// Anti-debug: replace debugger statements
let modified = aJs.replace(/debugger\s*;?/g, '/* debugger */');

// Write the full script
let script = stubs + '\n' + modified + '\n';
script += `
console.log('\\n=== RESULTS ===');
if (typeof c_api_host !== 'undefined') console.log('c_api_host =', c_api_host);
if (typeof GLOBAL_MOVIE_ID !== 'undefined') console.log('GLOBAL_MOVIE_ID =', GLOBAL_MOVIE_ID);
if (typeof is_mob !== 'undefined') console.log('is_mob =', is_mob);
if (typeof is_uc !== 'undefined') console.log('is_uc =', is_uc);
`;

fs.writeFileSync(path.join(__dirname, 'decode_test.js'), script);
console.log('Written decode_test.js');
