$(document).ready(function () {

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

    if (localStorage.hasActiveClass === "yes") {
        $('#collapse-button').removeClass("fa-angle-double-left").addClass("fa-angle-double-right");
        $('#content').addClass("active");
    } else {
        $('#collapse-button').addClass("fa-angle-double-left").removeClass("fa-angle-double-right");
        $('#content').addClass("");
    }

    getPendingSessions();
});

function getPendingSessions() {
    jsRoutes.controllers.CalendarController.getPendingSessions().ajax(
        {
            type: "GET",
            success: function(data) {
                $("#pending-sessions-number").text(data);
                $(".number-circle").text(data);
            },
            error: function(er) {
                $("#pending-sessions-number").text('0');
            }
        }
    )
}