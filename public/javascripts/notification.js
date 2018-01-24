$(function () {
    getNotificationCount();
});

function getNotificationCount() {
    var total = 0;
    jsRoutes.controllers.CalendarController.pendingSessions().ajax(
        {
            type: "GET",
            success: function (data) {
                $("#pending-sessions-number").text(data);
                total += data;
                $(".number-circle").text(total);
            },
            error: function (er) {
                $("#pending-sessions-number").text('0');
                $(".number-circle").text(total);
            },
            complete: function () {
                jsRoutes.controllers.RecommendationController.allPendingRecommendations().ajax(
                    {
                        type: 'GET',
                        success: function (data) {
                            $("#pending-recommendations-number").text(data);
                            total += data;
                            $(".number-circle").text(total);
                        },
                        error: function (er) {
                            $("#pending-recommendations-number").text('0');
                            total += 0;
                            $(".number-circle").text(total);
                        }
                    }
                )
            }
        }
    )
}