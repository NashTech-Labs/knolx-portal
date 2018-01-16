var total = 0;

$(function () {
    getPendingSessions();
    getPendingRecommendations();
});

function getPendingSessions() {
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
                total += 0;
                $(".number-circle").text(total);
            }
        }
    )
}

function getPendingRecommendations() {
    jsRoutes.controllers.RecommendationController.allPendingRecommendations().ajax(
        {
            type: 'GET',
            success: function (data) {
                console.log("Data --> " + data);
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