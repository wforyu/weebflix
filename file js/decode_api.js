// Stub jQuery
var results = [];
var $ = function(sel) { return { on: function() { return this; }, html: function() { return this; }, text: function() { return this; }, find: function() { return this; }, length: 0, addClass: function() { return this; }, removeClass: function() { return this; }, attr: function() { return this; }, show: function() { return this; }, hide: function() { return this; }, css: function() { return this; }, val: function() { return ''; }, append: function() { return this; }, parent: function() { return this; }, children: function() { return this; }, each: function() { return this; }, first: function() { return this; }; };
$.ajax = function(opts) { results.push(opts.url); };
$.parseJSON = function(s) { try { return JSON.parse(s); } catch(e) { return {}; } };
var window = global; window.location = { href: '', pathname: '/detail/the-husband-2026-v2e8/' }; window.history = { pushState: function() {}, replaceState: function() {} };
var document = { getElementById: function() { return { innerHTML: '' }; }, getElementsByTagName: function() { return []; }, createElement: function() { return { style: {} }; }, addEventListener: function() {}, cookie: '' };
var navigator = { userAgent: '' };
var localStorage = { getItem: function() { return null; }, setItem: function() {} };
var c_api_host = 'https://api.drakorkita.com'; var is_mob = 0; var is_uc = 0; var c = ''; var t = ''; var file_host = '';
var Playerjs = function() {};
try { eval(require('fs').readFileSync('C:/Users/pro021/.local/share/opencode/tool-output/tool_f9414da790017KPZ1vlRldkOJh', 'utf8')); } catch(e) {}
if (typeof loadEpisode === 'function') { console.log('FOUND: loadEpisode(' + loadEpisode.length + ' params)'); }
if (typeof initEpisodeList === 'function') { console.log('FOUND: initEpisodeList(' + initEpisodeList.length + ' params)'); }
if (typeof loadServer === 'function') { console.log('FOUND: loadServer(' + loadServer.length + ' params)'); }
console.log('API calls: ' + results.length);
results.forEach(function(u, i) { console.log('URL[' + i + ']: ' + u); });
