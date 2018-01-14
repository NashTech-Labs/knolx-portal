$( function () {
    getPendingSessions();
});

function getPendingSessions() {
    jsRoutes.controllers.CalendarController.pendingSessions().ajax(
        {
            type: "GET",
            success: function (data) {
                $("#pending-sessions-number").text(data);
                $(".number-circle").text(data);
            },
            error: function (er) {
                $("#pending-sessions-number").text('0');
            }
        }
    )
}
