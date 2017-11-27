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
});


function activeSidebar() {
    if (localStorage.hasActiveClass === "yes") {
        $('#sidebar').addClass("active");
    } else {
        $('#sidebar').addClass("");
    }
}

function activeCollapseButton() {
    if (localStorage.hasActiveClass === "yes") {
        $('#collapse-button').removeClass("fa-angle-double-left").addClass("fa-angle-double-right");
    } else {
        $('#collapse-button').addClass("fa-angle-double-left").removeClass("fa-angle-double-right");
    }

    if (localStorage.hasActiveClass === "yes") {
        $('#content').addClass("active");

    } else {
        $('#content').addClass("");
    }
}
