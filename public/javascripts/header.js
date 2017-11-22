/*
$(window).scroll(function () {
    if ($(document).scrollTop() > 40) {
        $('nav').addClass('shrink');
    } else {
        $('nav').removeClass('shrink');
    }
});*/
$(document).ready(function () {
    $('#sidebarCollapse').on('click', function () {
        $('#sidebar, #content').toggleClass("active");
        $('#collapse-button').toggleClass("fa-angle-double-left fa-angle-double-right");
    });

    /*$("#sidebar").niceScroll({
        cursorcolor: "#4285b0",
        cursoropacitymin: 0.3,
        background: "#cedbec",
        cursorborder: "0",
        autohidemode: true,
        cursorminheight: 30
    });

    $("#sidebar.active").getNiceScroll().resize();*/


});




$(document).ready(function() {

});
