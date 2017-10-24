
$(window).scroll(function () {
    if ($(document).scrollTop() > 40) {
        $('nav').addClass('shrink');
    } else {
        $('nav').removeClass('shrink');
    }
});

