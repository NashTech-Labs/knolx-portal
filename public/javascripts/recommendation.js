$(function () {
    ko.applyBindings(new Recommendation());
});

function Recommendation() {

    $('.custom-checkbox').click(function () {
        var filter = $('input[name="user-recommend-filter"]:checked').val();
        FetchRecommendationList(1, filter);

    });

    $('#add-button').popover({
        html: true,
        content: function() {
            return $('#add-recommend').html();
        }
    });


    var self = this;
    self.recommendation = ko.observableArray([]);
    self.userRecommendationList = ko.observableArray([]);

    self.approvedOrDecline = function (id, checked) {
        if (checked) {
            approve(id);
        } else {
            decline(id);
        }
    };

    FetchRecommendationList(1, "all");

    function FetchRecommendationList(pageNumber, filter) {

        jsRoutes.controllers.RecommendationController.recommendationList(pageNumber, filter).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    self.recommendation(values);
                },
                error: function (er) {
                    console.log(er);
                }
            }
        )
    }

    function approve(id) {
        jsRoutes.controllers.RecommendationController.approveRecommendation(id).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    FetchRecommendationList();
                },
                error: function (er) {
                    console.log(er);
                }
            }
        )
    }

    function decline(id) {
        jsRoutes.controllers.RecommendationController.declineRecommendation(id).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    FetchRecommendationList();
                },
                error: function (er) {
                    console.log(er);
                }
            }
        )
    }
}
