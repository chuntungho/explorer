(function () {
  let src = document.currentScript.getAttribute("src");
  const proxyUrl = src.substring(0, src.indexOf('/', src.indexOf('://') + 3));
  const proxyHost = proxyUrl.substring(proxyUrl.indexOf('://') + 3);

  console.log("Proxy URL: " + proxyUrl);

  const wrapUrl = function (url, direct) {
    if (!url || isWrapped(url) || url.startsWith('data:')) {
        return url;
    }

    if (url && url.indexOf("//") > -1) {
      url = proxyUrl + "?" + (direct ? "direct=true&" : "") + "url=" + encodeURIComponent(url);
      console.debug("wrapped: " + url);
    }
    return url;
  };

  const isWrapped = function (url) {
    return url && url.indexOf(proxyHost) > -1;
  };

  const getHandler = function (obj, key) {
    if (!key in obj) return undefined;
    // console.log('getting ' + key);
    let value = obj[key];
    if (typeof value === "function") {
      // if wrapped, use the function in proxy
      value = this[key] || value;
      return (...args) => value.apply(obj, args);
    } else {
      //return properties
      return value;
    }
  };
  const setHandler = function (o, p, v, r) {
    if (p === "src") {
      v = wrapUrl(v);
    }
    o[p] = v;
    return true;
  };

  const __fetch = window.fetch;
  window.fetch = function() {
    let request = arguments[0];
    if(typeof request === 'object' && 'url' in request) {
        // clone to overwrite url as url is read-only
        arguments[0] = new Request(wrapUrl(request.url), request);
    } else if (typeof request === 'string') {
        arguments[0] = wrapUrl(request);
    }
    return __fetch.apply(this, arguments);
  }

  const __open = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url, async, username = null, password = null) {
    arguments[1] = wrapUrl(arguments[1]);
    __open.apply(this, arguments);
  }

  const observerHandler = (mutationList) => {
    for (const mutation of mutationList) {
      if (mutation.type === "attributes" && mutation.attributeName === "src") {
        let src = mutation.target.src;
          let wrappedUrl = wrapUrl(src, mutation.target.tagName.toLowerCase() != 'iframe');
          console.log("wrapped src: " + wrappedUrl);
          if (src != wrappedUrl) {
            mutation.target.src = wrappedUrl;
          }
      }
    }
  };
  const observer = new MutationObserver(observerHandler);

  // intercept image object created by script
  const NativeImage = window.Image;
  const nodeHandler = {get: getHandler, set: setHandler};
  window.Image = function () {
    const img = new NativeImage();
    const p = new Proxy(img, nodeHandler);
    p["__target"] = img;
    console.debug("created image: " + img);
    return p;
  };

  const clickHandler = function (e) {
    let target = e.currentTarget || e.target || e.srcElement;
    if (target.tagName === 'A') {
        let href = target.getAttribute('href');
        let embedded = window.parent != window.self;
        if (embedded && target.getAttribute("target") == "_blank") {
          // console.log("new tab: " + href);
          if (href.indexOf('//') == -1) {
            href = new URL(href, document.baseURI).href;
          }
          window.parent.postMessage({action: 'open', url: href}, "*");
          e.preventDefault();
        } else {
          // wrap url
          if (href) {
            target.setAttribute("href", wrapUrl(href));
          }
        }
    }
  };

  const callback = function(win, action) {
    let remoteUrl = document.querySelector('meta[name="remote-url"]');
    let favicon = document.querySelector("link[rel*='icon']");
    win.postMessage({
        action: action,
        url: remoteUrl ? remoteUrl.content : '',
        icon: favicon ? favicon.href : '',
        title: document.title,
        width: document.body.scrollWidth,
        height: document.body.scrollHeight
        }, '*');
  };

  const scroll = function() {
    let scrollTop = document.body.scrollTop || document.documentElement.scrollTop;
    // todo add scroll to top button fixed at right bottom
  };
  window.onscroll = scroll;

  // listen parent message to back or forward
  window.addEventListener('message', function(e) {
    if ("back" == e.data.action) {
      window.history.back();
    } else if ("forward") {
      window.history.forward();
    }
  });

  // send unload event to show loading
  window.addEventListener("beforeunload", function(e) {
    window.parent.postMessage({action: 'unload'}, '*');
  });

  // observe existing image when content loaded
  document.addEventListener('DOMContentLoaded',function(){

    // send load event message after loaded
    callback(window.parent, 'load');

    const observerTarget = document.querySelectorAll("img,video,iframe");
    console.log("Observing nodes: " + observerTarget.length);
    observerTarget.forEach((x) => {
        if(x.src && !isWrapped(x.src)) {
            x.setAttribute('src', wrapUrl(x.src));
        }
        observer.observe(x, {
          subtree: true,
          attributeOldValue: true
        });
    });

    // intercept all links click
     if(window.jQuery){
        jQuery('body').on('click', 'a', function(e){
           clickHandler(e);
        });
     } else if (document.addEventListener) {
        document.addEventListener('click', clickHandler);
    } else if (document.attachEvent) {
        document.attachEvent('onclick', clickHandler);
    }

  });

  // observe new node created by dom
  const __write = document.write;
  const __createElement = document.createElement;
  const __appendChild = Node.prototype.appendChild;
  const __insertBefore = Node.prototype.insertBefore;
  const __removeChild = Node.prototype.removeChild;

  // https://developer.chrome.com/blog/removing-document-write
  document.write = function(markup) {
    // only allow to write style
    if (markup.indexOf('<link') > -1 || markup.indexOf('<style') > -1) {
        __write.call(this, markup);
    } else{
        console.debug('Intercepted document write: ' + markup);
    }
  };

  document.createElement = function (tagName, options) {
    let node = __createElement.call(this, tagName, options);
    let tag_name = tagName.toLowerCase();
    if (
      "img" === tag_name ||
      "video" === tag_name ||
      "script" === tag_name ||
      "iframe" === tag_name
    ) {
      observer.observe(node, {
        subtree: true,
        attributeOldValue: true
      });
    }
    return node;
  };

  // https://stackoverflow.com/questions/61785138/cannot-pass-proxy-around-html-element-to-appendchild
  const unproxy = function (el) {
    let node = el["__target"] ? el["__target"] : el;
    if(node.src && !isWrapped(node.src)) {
       node.src = wrapUrl(node.src);
    }
    return node;
  };
  Node.prototype.appendChild = function (el) {
    return __appendChild.call(this, unproxy(el));
  };
  Node.prototype.removeChild = function (el) {
    return __removeChild.call(this, unproxy(el));
  };
  Node.prototype.insertBefore = function (el, referenceNode) {
    return __insertBefore.call(this, unproxy(el), referenceNode);
  };

})();