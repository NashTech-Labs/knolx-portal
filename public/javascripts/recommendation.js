$(function () {
    ko.applyBindings(new Recommendation());
});

function Recommendation() {

    FetchRecommendationList(1, "all");

    $('.custom-checkbox').click(function () {
        var filter = $('input[name="user-recommend-filter"]:checked').val();
        FetchRecommendationList(1, filter);
    });

    $('#add-button').popover({
        html: true,
        content: function () {
            return $('#add-recommend').html();
        }
    });

    $('body').on("click", '#add-recommend-button', function () {
        console.log("aaaaaaa");
        var text = $('.popover-content #recommend-text').val();
        console.log("-------->" + text);
        addRecommend(text);
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
                    console.log(values);
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

    function addRecommend(text) {
        jsRoutes.controllers.RecommendationController.addRecommendation(text).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    $('#add-button').popover('hide');
                    FetchRecommendationList(1, "all");
                },
                error: function (er) {
                    console.log(er);
                }
            }
        )
    }
}

