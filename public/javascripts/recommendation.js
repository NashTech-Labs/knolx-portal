$(function () {
    ko.applyBindings(new Recommendation());
});

function Recommendation() {

    function successMessageBox() {
        $("#success-message").show();
        $("#failure-message").hide();
    }

    function failureMessageBox() {
        $("#success-message").hide();
        $("#failure-message").show();
    }

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
        var text = $('.popover-content #recommend-text').val();
        addRecommend(text);
    });


    var self = this;
    self.recommendation = ko.observableArray([]);

    self.upVoteByUser = function (id) {
        console.log("idddd" + id);

        jsRoutes.controllers.RecommendationController.upVote(id).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    document.getElementById("display-success-message").innerHTML = values;
                    successMessageBox();
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    FetchRecommendationList(1, filter);
                },
                error: function (er) {
                    document.getElementById("display-failure-message").innerHTML = er.responseText;
                    failureMessageBox();
                }
            }
        )
    };

    self.downVoteByUser = function (id) {
        console.log("idddd111" + id);
        jsRoutes.controllers.RecommendationController.downVote(id).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    document.getElementById("display-success-message").innerHTML = values;
                    successMessageBox();
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    FetchRecommendationList(1, filter);
                },
                error: function (er) {
                    document.getElementById("display-failure-message").innerHTML = er.responseText;
                    failureMessageBox();
                }
            }
        )
    };

    self.pendingByAdmin = function (id) {
        jsRoutes.controllers.RecommendationController.pendingRecommendation(id).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    document.getElementById("display-success-message").innerHTML = values;
                    successMessageBox();
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    FetchRecommendationList(1, filter);
                },
                error: function (er) {
                    document.getElementById("display-failure-message").innerHTML = er.responseText;
                    failureMessageBox();
                }
            }
        )
    };

    self.doneByAdmin = function (id) {
        jsRoutes.controllers.RecommendationController.doneRecommendation(id).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    document.getElementById("display-success-message").innerHTML = values;
                    successMessageBox();
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    FetchRecommendationList(1, filter);
                },
                error: function (er) {
                    document.getElementById("display-failure-message").innerHTML = er.responseText;
                    failureMessageBox();
                }
            }
        )
    };


    self.declineByAdmin = function (id) {
        jsRoutes.controllers.RecommendationController.declineRecommendation(id).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    document.getElementById("display-success-message").innerHTML = values;
                    successMessageBox();
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    FetchRecommendationList(1, filter);
                },
                error: function (er) {
                    document.getElementById("display-failure-message").innerHTML = er.responseText;
                    failureMessageBox();
                }
            }
        )
    };


    self.approveByAdmin = function (id) {
        jsRoutes.controllers.RecommendationController.approveRecommendation(id).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    document.getElementById("display-success-message").innerHTML = values;
                    successMessageBox();
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    FetchRecommendationList(1, filter);
                },
                error: function (er) {
                    document.getElementById("display-failure-message").innerHTML = er.responseText;
                    failureMessageBox();
                }
            }
        )
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
                    self.recommendation(values);
                },
                error: function (er) {
                    document.getElementById("display-failure-message").innerHTML = er.responseText;
                    failureMessageBox();
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
                    document.getElementById("display-success-message").innerHTML = values;
                    successMessageBox();
                    $('#add-button').popover('hide');
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    FetchRecommendationList(1, filter);
                },
                error: function (er) {
                    document.getElementById("display-failure-message").innerHTML = er.responseText;
                    failureMessageBox();
                }
            }
        )
    }
}

