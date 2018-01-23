var total = 0;
var sessions = 0;
var recommendations = 0;

$(function () {
    getNotificationCount();
});

function getNotificationCount() {
    jsRoutes.controllers.CalendarController.pendingSessions().ajax(
        {
            type: "GET",
            success: function (data) {
                console.log("pending session ->>> " + data);
                $("#pending-sessions-number").text(data);
                sessions = data;
                total += sessions;
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
                            recommendations = data;
                            total = sessions + recommendations;
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
