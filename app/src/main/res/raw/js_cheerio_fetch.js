// Cheerio-like API backed by simple DOM parsing
function __cheerioLoad(html) {
    var docId = ++__docCounter;
    __documents[docId] = { html: html };

    function $(selector) {
        return createCheerioWrapper(docId, selector);
    }

    $.html = function() {
        return __documents[docId].html;
    };

    return $;
}

var __documents = {};
var __docCounter = 0;

function createCheerioWrapper(docId, selector) {
    var wrapper = {
        _docId: docId,
        _selector: selector,
        _html: __documents[docId].html,

        find: function(sel) {
            return createCheerioWrapper(docId, (selector ? selector + ' ' : '') + sel);
        },

        text: function() {
            return __nativeCheerioText(this._html, this._selector);
        },

        html: function() {
            return __nativeCheerioHtml(this._html, this._selector);
        },

        attr: function(name) {
            return __nativeCheerioAttr(this._html, this._selector, name);
        },

        first: function() {
            return createCheerioWrapper(docId, selector + ':first');
        },

        last: function() {
            return createCheerioWrapper(docId, selector + ':last');
        },

        eq: function(index) {
            return createCheerioWrapper(docId, selector + ':eq(' + index + ')');
        },

        each: function(callback) {
            var items = __nativeCheerioEach(this._html, this._selector);
            var parsed = JSON.parse(items || '[]');
            for (var i = 0; i < parsed.length; i++) {
                var item = {
                    _html: parsed[i],
                    _selector: '',
                    text: function() { return __nativeCheerioText(this._html, ''); },
                    html: function() { return this._html; },
                    attr: function(n) { return __nativeCheerioAttr(this._html, '', n); },
                    find: function(s) { return createCheerioWrapper(docId, s); },
                };
                callback.call(item, i, item);
            }
            return wrapper;
        },

        map: function(callback) {
            var results = [];
            this.each(function(i, el) {
                results.push(callback.call(el, i, el));
            });
            return { get: function() { return results; }, toArray: function() { return results; } };
        },

        toArray: function() {
            var items = __nativeCheerioEach(this._html, this._selector);
            var parsed = JSON.parse(items || '[]');
            return parsed.map(function(html) {
                return {
                    _html: html,
                    text: function() { return __nativeCheerioText(this._html, ''); },
                    attr: function(n) { return __nativeCheerioAttr(this._html, '', n); },
                };
            });
        },

        length: 0,

        parent: function() { return createCheerioWrapper(docId, selector); },
        children: function(sel) { return this.find(sel || '*'); },
        next: function() { return createCheerioWrapper(docId, selector); },
        prev: function() { return createCheerioWrapper(docId, selector); },
        hasClass: function(c) { return __nativeCheerioHasClass(this._html, this._selector, c); },
    };

    // Update length
    try {
        wrapper.length = __nativeCheerioLength(wrapper._html, selector);
    } catch(e) {
        wrapper.length = 0;
    }

    return wrapper;
}

// Cheerio native bridges using simple DOM parsing
function __nativeCheerioText(html, selector) {
    if (!selector) {
        // Strip all HTML tags and collapse whitespace
        var text = html.replace(/<[^>]*>/g, '');
        return text.replace(/\s+/g, ' ').trim();
    }
    var elements = __findElements(html, selector);
    var texts = [];
    for (var i = 0; i < elements.length; i++) {
        var text = elements[i].replace(/<[^>]*>/g, '');
        text = text.replace(/\s+/g, ' ').trim();
        if (text) texts.push(text);
    }
    return texts.join(' ');
}

function __nativeCheerioHtml(html, selector) {
    if (!selector) return html;
    var elements = __findElements(html, selector);
    return elements.length > 0 ? elements[0] : '';
}

function __nativeCheerioAttr(html, selector, name) {
    if (!selector) {
        var match = html.match(new RegExp(name + '="([^"]*)"'));
        if (!match) match = html.match(new RegExp(name + "='([^']*)'"));
        return match ? match[1] : null;
    }
    var elements = __findElements(html, selector);
    if (elements.length === 0) return null;
    var match = elements[0].match(new RegExp(name + '="([^"]*)"'));
    if (!match) match = elements[0].match(new RegExp(name + "='([^']*)'"));
    return match ? match[1] : null;
}

function __nativeCheerioEach(html, selector) {
    var elements = __findElements(html, selector);
    return JSON.stringify(elements);
}

function __nativeCheerioLength(html, selector) {
    var elements = __findElements(html, selector);
    return elements.length;
}

function __nativeCheerioHasClass(html, selector, className) {
    var elements = __findElements(html, selector);
    if (elements.length === 0) return false;
    var classAttr = elements[0].match(/class="([^"]*)"/);
    if (!classAttr) classAttr = elements[0].match(/class='([^']*)'/);
    if (!classAttr) return false;
    var classes = classAttr[1].split(/\s+/);
    for (var i = 0; i < classes.length; i++) {
        if (classes[i] === className) return true;
    }
    return false;
}

// Simple HTML element finder using indexOf and substring
function __findElements(html, selector) {
    var results = [];
    
    if (!selector || !html) return results;
    
    // Try to find opening and closing tags
    if (selector.indexOf('.') !== -1) {
        // Class selector - find tags with matching class
        var className = selector.split('.')[1].split('[')[0];
        var searchStr = 'class="' + className + '"';
        var idx = html.indexOf(searchStr);
        if (idx === -1) searchStr = "class='" + className + "'";
        
        while ((idx = html.indexOf(searchStr)) !== -1) {
            // Find the opening tag
            var tagStart = html.lastIndexOf('<', idx);
            if (tagStart === -1) break;
            
            var tagEnd = html.indexOf('>', idx);
            if (tagEnd === -1) break;
            
            var tagName = html.substring(tagStart + 1, html.indexOf(' ', tagStart));
            if (!tagName) tagName = html.substring(tagStart + 1, tagEnd);
            
            // Find matching closing tag
            var closeTagStr = '</' + tagName + '>';
            var closeIdx = html.indexOf(closeTagStr, tagEnd);
            if (closeIdx !== -1) {
                results.push(html.substring(tagStart, closeIdx + closeTagStr.length));
            }
            
            html = html.substring(closeIdx + 1);
        }
    } else if (selector.indexOf('#') !== -1) {
        // ID selector
        var id = selector.split('#')[1].split('[')[0];
        var searchStr = 'id="' + id + '"';
        var idx = html.indexOf(searchStr);
        if (idx === -1) searchStr = "id='" + id + "'";
        
        idx = html.indexOf(searchStr);
        if (idx !== -1) {
            var tagStart = html.lastIndexOf('<', idx);
            if (tagStart !== -1) {
                var tagEnd = html.indexOf('>', idx);
                if (tagEnd !== -1) {
                    var tagName = html.substring(tagStart + 1, html.indexOf(' ', tagStart));
                    if (!tagName) tagName = html.substring(tagStart + 1, tagEnd);
                    var closeTagStr = '</' + tagName + '>';
                    var closeIdx = html.indexOf(closeTagStr, tagEnd);
                    if (closeIdx !== -1) {
                        results.push(html.substring(tagStart, closeIdx + closeTagStr.length));
                    }
                }
            }
        }
    } else {
        // Tag selector
        var tag = selector.split('.')[0].split('#')[0].split('[')[0];
        var openTag = '<' + tag;
        var closeTag = '</' + tag + '>';
        var idx = 0;
        
        while ((idx = html.indexOf(openTag, idx)) !== -1) {
            var tagEnd = html.indexOf('>', idx);
            if (tagEnd === -1) break;
            
            var closeIdx = html.indexOf(closeTag, tagEnd);
            if (closeIdx !== -1) {
                results.push(html.substring(idx, closeIdx + closeTag.length));
                idx = closeIdx + 1;
            } else {
                break;
            }
        }
    }
    
    return results;
}
