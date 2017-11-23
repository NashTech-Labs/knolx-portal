$(document).ready(function () {

    $("#sidebar").niceScroll({
        cursorcolor: '#53619d',
        cursorwidth: 4,
        cursorborder: 'none'
    });

    $('#sidebarCollapse').on('click', function () {
        $('#sidebar, #content').toggleClass("active");

        if ($('#sidebar').hasClass("active")) {
            localStorage.removeItem("hasActiveClass");
            localStorage.hasActiveClass = "yes";
        } else {
            localStorage.removeItem("hasActiveClass");
            localStorage.hasActiveClass = "no"
        }

        $('#collapse-button').toggleClass("fa-angle-double-left fa-angle-double-right");
    });

    $('.IsBestAnswer').addClass('bestanswer').removeClass('IsBestAnswer');
});