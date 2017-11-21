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

 $("#sidebar").niceScroll({
     cursorcolor: '#53619d',
     cursorwidth: 2,
     cursorborder: 'none'
 });

 $("#sidebar.active").niceScroll({
      cursorcolor: '#53619d',
      cursorwidth: 2,
      cursorborder: 'none'
  });

  $('.IsBestAnswer').addClass('bestanswer').removeClass('IsBestAnswer');
});