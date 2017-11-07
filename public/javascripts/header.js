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
     $('#sidebar').toggleClass('active');
 });

 function header(){
    var width= $(window).width();

    if (width<=768) {
       $('#sidebar').addClass('active');
    }
 }

 header();
});