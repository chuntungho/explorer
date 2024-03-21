$(document).ready(function() {
    let activePage = -1;
    let pageCount = 0;
    let pageData = {};

    const loading = function(pageId, show) {
         if (show) {
            $(`#tab-${pageId}`).find('.loading').removeClass('none');
         } else {
            $(`#tab-${pageId}`).find('.loading').addClass('none');
         }
    }

    const browse = function(url) {
      if (url.indexOf('//') == -1) {
         url = 'https://' + url;
       }

       // proxy url if needed
       if (url.indexOf(window.location.host) == -1) {
         url = "?url=" + encodeURIComponent(url);
       }

       // reset scale style before loading
       $("#iframe-" + activePage).removeAttr("style");
       // show loading icon
       loading(activePage, true);
       // start to load
       $("#iframe-" + activePage).attr("src", url);
    };

    const addTab = function(url) {
       $(".tab-container").removeClass("active");
       $("iframe").removeClass("active");

       activePage = pageCount ++;
       let page = {id: activePage, url: "" || url, title: 'New Tab', icon: 'logo.svg'};
       pageData[activePage] = page;

       $("#address input").attr('page-id', activePage).val(page.url);

       let $tab = $($("template.tab-tmpl").html());
       $tab.attr('page-id', activePage).attr('id', 'tab-' + activePage);
       $tab.insertBefore("#new-tab");

       let $menu = $($("template.menu-tmpl").html());
       $menu.attr('page-id', activePage).attr('id', 'menu-' + activePage);
       $menu.insertBefore("#opened-tabs .slice")

       let $iframe = $($("template.iframe-tmpl").html()).attr('page-id', activePage).attr('id', 'iframe-' + activePage);
        $(".page").append($iframe);

        if (url) {
          browse(url);
        } else {
          $("#address input").focus();
        }
    };

    const activateTab = function(tabId) {
        let $tab =  $('#tab-' + tabId);
        if ($tab.find(".tab-container").hasClass("active")) {
          return;
        }

        $(".tab-container").removeClass("active");
        $("iframe").removeClass("active");

        let page = pageData[tabId];

        $tab.find(".tab-container").addClass("active");
        $("#address input").attr('page-id', page.id).val(page.url);
        $("#iframe-" + tabId).addClass("active");
        activePage = tabId;
    };

    const closeTab = function(tab) {
       let $tab =  $('#tab-' + tab);
       let $focusTab = null;
       if ($tab.find('.tab-container').hasClass('active')) {
         $focusTab = $tab.next().hasClass('tab-frame') ? $tab.next() : $tab.prev();
       }

       $tab.remove();
       $("#menu-" + tab).remove();
       $("#iframe-" + tab).remove();
       delete pageData[tab];

       // activate the nearest tab container
       if ($focusTab && $focusTab.length == 1) {
         activateTab($focusTab.attr('page-id'));
       } else if ($('.tab-container').length == 0) {
         addTab();
       }
    };

    // render tab after loading page
    const renderTab = function (page, width) {
        if ($('#address input').attr('page-id') == page.id) {
            $('#address input').val(page.url);
        }

        let $tab = $("#tab-" + page.id);
        $tab.attr('title', page.title);
        $tab.find('.icon').attr('src', page.icon);
        $tab.find('.title').text(page.title);

        let $menu = $("#menu-" + page.id);
        $menu.find('.title .icon').attr('src', page.icon);
        $menu.find('.title div').text(page.title);

        let $iframe = $(`#iframe-${page.id}`);
        let $page = $('.page');
        // scale iframe for non-responsive page
        if (width && $page.width() < width) {
            let scale = $page.width() / width;
            $iframe.css({'transform-origin': 'top left', 'transform': `scale(${scale}, ${scale})`});
            $iframe.width(width).height(Math.floor($page.height() / scale));
        } else {
            // restore
            $iframe.css({'transform-origin': '', 'transform':'', width: '', height: ''});
        }

       // hide loading icon
       loading(page.id, false);
    };

    // listen iframe message
    window.addEventListener('message', function(e) {
        if (e.data.action == 'open') {
          addTab(e.data.url);
        } else if (e.data.action == 'load') {
            $('iframe').each(function(i, frame){
                if(frame.contentWindow == e.source) {
                    let pageId = $(frame).attr('page-id');
                    let page = {id: pageId, url: e.data.url, title: e.data.title, icon: e.data.icon || "logo.svg"};
                    pageData[pageId] = page;
                    renderTab(page, e.data.width);
                }
            });
        } else if (e.data.action == 'unload') {
            console.log('received unload');
            // show loading for new page
            $('iframe').each(function(i, frame){
                if(frame.contentWindow == e.source) {
                    loading($(frame).attr('page-id'), true);
                }
            });
        } else if (e.data.action == 'scroll') {
           // todo hide top bar smooth
        }
    });

    // select text on focus
    $("#address input").on('focus click', function() {
       $(this).select();
       $('#main-bar').addClass("selected");
       $('#address-bar').addClass("selected");
       $('#info').addClass("none");
    }).on('blur', function() {
       $('#main-bar').removeClass("selected");
       $('#address-bar').removeClass("selected");
       $('#info').removeClass("none");
    }).on("keydown", function(event){
        var id = event.which || event.keyCode || 0;
        if (id == 13) {
           let url = $(this).val();
           if(url) {
             browse(url);
           }
        }
    });

    // dropdown menu
    $(".dropdown").click(function(e){
        let $current = $(this).find('.dropdown-menu:first');
        if ($current.hasClass('none')) { // going to show
            // 1. hide all other dropdown menus except current or parents
            $('.dropdown-menu').not($(this).parents(".dropdown-menu")).addClass('none');
            // 2. show current dropdown menu
            $current.toggleClass('none');
            // 3. show iframe mask to receive window click event
            $('#iframe-mask').removeClass('none');
            // 4. not propagate event to parents or window
            e.stopPropagation();
        }
    });

    $(window).click(function(event) {
       $('.dropdown-menu').addClass('none');
       $('#iframe-mask').addClass('none');
    }).keydown(function(e){
        let id = event.which || event.keyCode || 0;
        if( id == 27){
           $('.dropdown-menu').addClass('none');
        }
    });

    // add new tab
    $("#new-tab, .add-tab").on("click", function(e) {addTab();});

    // activate tab
    $("#tabs").on("click", ".tab-container", function () {
       activateTab($(this).parent().attr("page-id"));
    });
    $("#opened-tabs").on("click", ".dropdown-item", function () {
       activateTab($(this).attr("page-id"));
    });

    // close tab
    $("#tabs").on("click", "div.close", function(e){
      closeTab($(this).closest(".tab-frame").attr('page-id'));
      // avoid to trigger activate tab
      e.stopPropagation();
    });
    $("#opened-tabs").on("click", "div.close", function(e){
      closeTab($(this).closest(".dropdown-item").attr('page-id'));
      // avoid to trigger activate tab
      e.stopPropagation();
    });
    $(".close-tab").on("click", function(e){
      closeTab(activePage);
    });

    $(".close-others").on("click", function(e){
      $('.tab-container').each(function(idx, e) {
        if(!$(e).hasClass('active')){
          closeTab($(e).parent().attr('page-id'));
        }
      });
    });

    $(".close-right").on("click", function(e){
        $('.tab-frame .active').parent().nextAll().each(function(i,e){
           closeTab($(e).attr('page-id'));
        });
    });

    // back
    $("back-button").on("click", function() {
      $("iframe.active").get(0).contentWindow.postMessage({action: 'back'}, "*");
    });

    // forward
    $("forward-button").on("click", function() {
      $("iframe.active").get(0).contentWindow.postMessage({action: 'forward'}, "*");
    });

    // refresh
    $("#refresh").on("click", function() {
        // $("iframe.active").attr("src", $("iframe.active").attr('src'));
        // show loading icon
        loading(activePage, true)
        if(pageData[activePage]) {
            browse(pageData[activePage].url);
        }
    });

    // init blank tab
    $("#bookmarks a").on('click', function(e) {
        if ($(this).attr('target') != '_blank') {
          browse($(this).attr('href'));
          e.preventDefault();
        }
    });

    addTab();
});