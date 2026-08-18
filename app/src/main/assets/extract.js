(function () {
    function findAweme(obj, depth) {
        if (depth > 10 || obj == null) return null;
        if (typeof obj !== 'object') return null;
        if (obj.aweme_id || obj.awemeId) return obj;
        if (Array.isArray(obj)) {
            for (var i = 0; i < obj.length; i++) {
                var r = findAweme(obj[i], depth + 1);
                if (r) return r;
            }
        } else {
            for (var k in obj) {
                try {
                    var r = findAweme(obj[k], depth + 1);
                    if (r) return r;
                } catch (e) {}
            }
        }
        return null;
    }

    function tryGetData() {
        if (window._ROUTER_DATA && typeof window._ROUTER_DATA === 'object') {
            return JSON.stringify(window._ROUTER_DATA);
        }
        if (window.RENDER_DATA) {
            try { return decodeURIComponent(window.RENDER_DATA); } catch (e) {}
        }
        var scripts = document.querySelectorAll('script');
        for (var i = 0; i < scripts.length; i++) {
            var t = scripts[i].textContent || '';
            if (t.indexOf('_ROUTER_DATA') !== -1) {
                var m = t.match(/window\._ROUTER_DATA\s*=\s*(\{[\s\S]*?\})\s*;?\s*<\/?/);
                if (!m) m = t.match(/window\._ROUTER_DATA\s*=\s*(\{[\s\S]*\})/);
                if (m) {
                    try { JSON.parse(m[1]); return m[1]; } catch (e) {}
                }
            }
            if (t.indexOf('RENDER_DATA') !== -1) {
                var m2 = t.match(/RENDER_DATA\s*=\s*["']([^"']+)["']/);
                if (m2) {
                    try { return decodeURIComponent(m2[1]); } catch (e) {}
                }
            }
        }
        return null;
    }

    function metaContent(prop) {
        var el = document.querySelector('meta[property="' + prop + '"]') ||
            document.querySelector('meta[name="' + prop + '"]');
        return el ? (el.getAttribute('content') || '') : '';
    }

    function strList(arr) {
        var out = [];
        if (Array.isArray(arr)) {
            for (var i = 0; i < arr.length; i++) {
                if (typeof arr[i] === 'string') out.push(arr[i]);
            }
        }
        return out;
    }

    function coverFromVideo(v) {
        var out = [];
        if (!v) return out;
        if (v.cover && v.cover.url_list) out = out.concat(strList(v.cover.url_list));
        if (v.origin_cover && v.origin_cover.url_list) out = out.concat(strList(v.origin_cover.url_list));
        if (v.dynamic_cover && v.dynamic_cover.url_list) out = out.concat(strList(v.dynamic_cover.url_list));
        return out;
    }

    function process(raw) {
        var parsed;
        try { parsed = JSON.parse(raw); } catch (e) {
            return null;
        }
        var aweme = findAweme(parsed, 0);
        if (!aweme) return null;

        var video = aweme.video;
        var videoUrls = (video && video.play_addr && video.play_addr.url_list)
            ? strList(video.play_addr.url_list) : [];
        if (videoUrls.length === 0 && video && video.play_addr && video.play_addr.uri) {
            videoUrls.push('https://aweme.snssdk.com/aweme/v1/play/?video_id=' + video.play_addr.uri + '&ratio=1080p&line=0');
        }

        var coverUrls = coverFromVideo(video);
        if (coverUrls.length === 0 && aweme.video_cover && aweme.video_cover.url_list) {
            coverUrls = strList(aweme.video_cover.url_list);
        }

        var imageUrls = [];
        if (aweme.images && aweme.images.length) {
            for (var i = 0; i < aweme.images.length; i++) {
                var im = aweme.images[i];
                var u = im && im.url_list && im.url_list[0];
                if (typeof u === 'string') imageUrls.push(u);
            }
        }

        var author = aweme.author || {};
        var stats = aweme.statistics || {};

        return JSON.stringify({
            ok: true,
            url: location.href,
            awemeId: String(aweme.aweme_id || aweme.awemeId || ''),
            desc: String(aweme.desc || aweme.title || ''),
            authorName: String(author.nickname || ''),
            authorUniqueId: String(author.unique_id || ''),
            videoUrlList: videoUrls,
            coverUrlList: coverUrls,
            images: imageUrls,
            digg: stats.digg_count || 0,
            comment: stats.comment_count || 0,
            share: stats.share_count || 0
        });
    }

    function domData() {
        var video = document.querySelector('video');
        var src = video ? (video.src || video.currentSrc || '') : '';
        var idMatch = location.href.match(/\/(?:video|note)\/(\d+)/);
        var awemeId = idMatch ? idMatch[1] : '';
        var desc = metaContent('og:title') || metaContent('description') || '';
        if (!desc) {
            var d = document.querySelector('h1') || document.querySelector('[class*="desc"]') || document.querySelector('[class*="title"]');
            if (d) desc = d.textContent.trim();
        }
        if (!desc) desc = document.title;
        var images = [];
        var seen = {};
        var ogImage = metaContent('og:image');
        if (ogImage) images.push(ogImage);
        var imgs = document.querySelectorAll('img');
        for (var i = 0; i < imgs.length && images.length < 20; i++) {
            var u = imgs[i].src || '';
            if (!u || u.indexOf('douyinpic') === -1) continue;
            if (u.indexOf('aweme-avatar') !== -1 || u.indexOf('/100x100/') !== -1) continue;
            if (u.indexOf('~tplv') !== -1 || /(\?|&)x-expires/.test(u)) continue;
            if (seen[u]) continue;
            seen[u] = 1;
            images.push(u);
        }
        return JSON.stringify({
            ok: true,
            url: location.href,
            awemeId: awemeId,
            desc: desc,
            authorName: '',
            authorUniqueId: '',
            videoUrlList: src ? [src] : [],
            coverUrlList: [],
            images: images,
            digg: 0,
            comment: 0,
            share: 0
        });
    }

    var raw = tryGetData();
    if (raw) {
        var r = process(raw);
        if (r) return r;
    }
    return domData();
})();