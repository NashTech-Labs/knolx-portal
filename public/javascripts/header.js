/*
$(window).scroll(function () {
    if ($(document).scrollTop() > 40) {
        $('nav').addClass('shrink');
    } else {
        $('nav').removeClass('shrink');
    }
});*/
$(document).ready(function () {

    if (localStorage.hasActiveClass === "yes") {
        $('#sidebar, #content').addClass("active")
    } else {
        $('#sidebar, #content').addClass("")
    }

    $('#sidebarCollapse').on('click', function () {
        $('#sidebar, #content').toggleClass("active");

        if ($('#sidebar').hasClass("active")) {
            localStorage.removeItem("hasActivClass");
            localStorage.hasActiveClass = "yes";
        } else {
            localStorage.removeItem("hasActivClass");
            localStorage.hasActiveClass = "no"
        }

        $('#collapse-button').toggleClass("fa-angle-double-left fa-angle-double-right");
    });

    $("#sidebar").niceScroll({
        cursorcolor: '#53619d',
        cursorwidth: 0,
        cursorborder: 'none'
    });

    $("#sidebar.active").niceScroll({
        cursorcolor: '#53619d',
        cursorwidth: 4,
        cursorborder: 'none'
    });

    $('.IsBestAnswer').addClass('bestanswer').removeClass('IsBestAnswer');
});