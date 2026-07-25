
<script>
    var c = 'bfb1';
    var t = '1784892473&ver=373iq';
    var is_mob = '0';
    var is_uc = '0';
    var api_host = 'https://api.nonton.bid/api';
    var c_api_host = 'https://api.nonton.bid/c_api';
    var file_host = 'https://d.load.my.id';
    var movie_title = 'The Husband (2026)';
    $(document).ready(function(){
        initEpisodeList('yLpA1nCVmw', 'hs', 'ind');    });
    
    $("#nonot").click(function() {
        get_link('yLpA1nCVmw','tv');
    });
    
$(function(){
    var collapsedHeight = 200;

    $(".mv-description").each(function() {
        var $thisDesc = $(this);
        var $wrap = $thisDesc.find(".desc-wrap");
        var $fade = $thisDesc.find(".desc-fade");
        var $openLink = $thisDesc.find(".open-link");
        var $link = $openLink.find("a");
        
        var fullHeight = $wrap.outerHeight();

        if (fullHeight <= collapsedHeight) {
            $openLink.hide();
            $fade.hide();
            $thisDesc.css({"height": "auto", "padding-bottom": "0"});
        } else {
            $thisDesc.addClass("has-expand");
        }
    });

    $(".open-link a").click(function() {
        var $btn = $(this);
        var $parent = $btn.closest(".mv-description");
        var $wrap = $parent.find(".desc-wrap");
        var $fade = $parent.find(".desc-fade");
        
        var currentFullHeight = $wrap.outerHeight() + 40;

        if (!$parent.hasClass("open")) {
            $parent.addClass("open").stop().animate({height: currentFullHeight}, 500);
            $fade.fadeOut();
            $btn.text("Ringkas");
        } else {
            $parent.removeClass("open").stop().animate({height: collapsedHeight}, 500);
            $fade.fadeIn();
            $btn.text("Selengkapnya");
        }
    });
});
</script>

