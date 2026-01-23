// Polyfills and native bridges
var console = {
    log: function() {},
    warn: function() {},
    error: function() {},
    info: function() {},
};

// Patch Date.prototype.toISOString to prevent crashes on invalid dates
var __originalToISOString = Date.prototype.toISOString;
Date.prototype.toISOString = function() {
    try {
        return __originalToISOString.call(this);
    } catch (e) {
        return "1970-01-01T00:00:00.000Z";
    }
};

// URL Polyfill
function URL(url, base) {
    if (base) {
        // Very simple base resolution
        if (url.startsWith('http')) return new URL(url);
        if (base.endsWith('/')) base = base.slice(0, -1);
        if (url.startsWith('/')) return new URL(base + url);
        return new URL(base + '/' + url);
    }
    
    this.href = url;
    this.protocol = '';
    this.host = '';
    this.hostname = '';
    this.port = '';
    this.pathname = '';
    this.search = '';
    this.hash = '';
    this.origin = '';
    
    // Simple parsing
    var match = url.match(/^(https?:)\/\/([^:\/?#]+)(?::(\d+))?(\/[^?#]*)?(\?[^#]*)?(#.*)?$/);
    if (match) {
        this.protocol = match[1] || '';
        this.host = match[2] + (match[3] ? ':' + match[3] : '');
        this.hostname = match[2] || '';
        this.port = match[3] || '';
        this.pathname = match[4] || '/';
        this.search = match[5] || '';
        this.hash = match[6] || '';
        this.origin = this.protocol + '//' + this.host;
    }
    
    this.toString = function() { return this.href; };
}

// URLSearchParams Polyfill
function URLSearchParams(init) {
    this._params = {};
    
    if (typeof init === 'string') {
        if (init.startsWith('?')) init = init.slice(1);
        var pairs = init.split('&');
        for (var i = 0; i < pairs.length; i++) {
            var pair = pairs[i].split('=');
            if (pair.length === 2) {
                this.append(decodeURIComponent(pair[0]), decodeURIComponent(pair[1]));
            }
        }
    } else if (init && typeof init === 'object') {
        for (var key in init) {
            if (init.hasOwnProperty(key)) {
                this.append(key, init[key]);
            }
        }
    }
}

URLSearchParams.prototype.append = function(name, value) {
    if (!this._params[name]) this._params[name] = [];
    this._params[name].push(String(value));
};

URLSearchParams.prototype.delete = function(name) {
    delete this._params[name];
};

URLSearchParams.prototype.get = function(name) {
    return this._params[name] ? this._params[name][0] : null;
};

URLSearchParams.prototype.getAll = function(name) {
    return this._params[name] || [];
};

URLSearchParams.prototype.has = function(name) {
    return this._params.hasOwnProperty(name);
};

URLSearchParams.prototype.set = function(name, value) {
    this._params[name] = [String(value)];
};

URLSearchParams.prototype.toString = function() {
    var query = [];
    for (var key in this._params) {
        if (this._params.hasOwnProperty(key)) {
            var values = this._params[key];
            for (var i = 0; i < values.length; i++) {
                query.push(encodeURIComponent(key) + '=' + encodeURIComponent(values[i]));
            }
        }
    }
    return query.join('&');
};

// NovelStatus enum
var NovelStatus = {
    Unknown: 'Unknown',
    Ongoing: 'Ongoing',
    Completed: 'Completed',
    Licensed: 'Licensed',
    PublishingFinished: 'Publishing Finished',
    Cancelled: 'Cancelled',
    OnHiatus: 'On Hiatus',
};

// FilterTypes enum
var FilterTypes = {
    TextInput: 'Text',
    Picker: 'Picker',
    CheckboxGroup: 'Checkbox',
    Switch: 'Switch',
    ExcludableCheckboxGroup: 'XCheckbox',
};

// Custom require function
function require(moduleName) {
    switch(moduleName) {
        case 'cheerio':
            // Return object with load function that returns the wrapper
            return {
                load: function(html) {
                    return __cheerioLoad(html);
                }
            };
        case 'htmlparser2':
            return { Parser: HtmlParser };
        case '@libs/fetch':
            return { fetchApi: fetchApi, fetchText: fetchText };
        case '@libs/novelStatus':
            return { NovelStatus: NovelStatus };
        case '@libs/filterInputs':
            return { FilterTypes: FilterTypes };
        case '@libs/defaultCover':
            return { defaultCover: '' };
        case '@libs/isAbsoluteUrl':
            return { isUrlAbsolute: function(url) { return url && (url.startsWith('http://') || url.startsWith('https://')); } };
        case '@libs/storage':
            return { storage: { get: function(k) { return null; }, set: function(k,v) {}, delete: function(k) {} }, localStorage: { get: function() { return {}; } }, sessionStorage: { get: function() { return {}; } } };
        case 'dayjs':
            return dayjs;
        case 'urlencode':
            return { encode: encodeURIComponent, decode: decodeURIComponent };
        default:
            return {};
    }
}

// Minimal dayjs
function dayjs(date) {
    var d = date ? new Date(date) : new Date();
    return {
        format: function(fmt) { return d.toISOString().split('T')[0]; },
        toISOString: function() { return d.toISOString(); },
        valueOf: function() { return d.getTime(); },
        isBefore: function(other) { return d.getTime() < (other ? new Date(other).getTime() : Date.now()); },
        isAfter: function(other) { return d.getTime() > (other ? new Date(other).getTime() : Date.now()); },
    };
}
dayjs.extend = function() { return dayjs; };
dayjs.utc = function() { return dayjs(); }; // Mock utc plugin

// HtmlParser2 implementation using simple regex-based parsing
function HtmlParser(options) {
    this.options = options || {};
    this._buffer = '';
    this._onopentag = options && options.onopentag;
    this._ontext = options && options.ontext;
    this._onclosetag = options && options.onclosetag;
}
HtmlParser.prototype.write = function(html) { this._buffer += html; };
HtmlParser.prototype.end = function() {
    if (this._buffer) {
        __nativeParseHtml(this._buffer, this._onopentag, this._ontext, this._onclosetag);
    }
};
HtmlParser.prototype.isVoidElement = function(name) {
    return ['area','base','br','col','embed','hr','img','input','link','meta','param','source','track','wbr'].indexOf(name.toLowerCase()) !== -1;
};

// Native HTML parser for htmlparser2 using simple regex-based parsing
function __nativeParseHtml(html, onopentag, ontext, onclosetag) {
    var tagRegex = /<(\/?)([a-zA-Z0-9]+)((?:\s+[a-zA-Z0-9\-]+(?:=(?:"[^"]*"|'[^']*'|[^\s>]+))?)*?)\s*(\/?)?>/g;
    var attrRegex = /([a-zA-Z0-9\-]+)(?:=(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g;
    var match;
    var lastIndex = 0;
    
    while ((match = tagRegex.exec(html)) !== null) {
        var fullMatch = match[0];
        var isClosing = match[1] === '/';
        var tagName = match[2].toLowerCase();
        var attribsStr = match[3];
        var isSelfClosing = match[4] === '/';
        
        // Extract text before this tag
        if (match.index > lastIndex && ontext) {
            var text = html.substring(lastIndex, match.index);
            if (text.trim()) {
                ontext(text);
            }
        }
        
        if (isClosing) {
            if (onclosetag) {
                onclosetag(tagName);
            }
        } else {
            // Parse attributes
            var attribs = {};
            var attrMatch;
            while ((attrMatch = attrRegex.exec(attribsStr)) !== null) {
                var attrName = attrMatch[1].toLowerCase();
                var attrValue = attrMatch[2] || attrMatch[3] || attrMatch[4] || '';
                attribs[attrName] = attrValue;
            }
            
            if (onopentag) {
                onopentag(tagName, attribs);
            }
            
            // Self-closing tags also trigger close callback
            if (isSelfClosing && onclosetag) {
                onclosetag(tagName);
            }
        }
        
        lastIndex = match.index + fullMatch.length;
    }
    
    // Handle remaining text
    if (lastIndex < html.length && ontext) {
        var text = html.substring(lastIndex);
        if (text.trim()) {
            ontext(text);
        }
    }
}

