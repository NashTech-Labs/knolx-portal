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
     $('#sidebar, #content').toggleClass('active');
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

 function header(){
    var width= $(window).width();

    if (width<=768) {
       $('#sidebar').addClass('active');
    }
 }
 header();
});