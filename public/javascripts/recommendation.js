$(function () {
    ko.applyBindings(new Recommendation());
});

function Recommendation() {

    var page = 1;

    function getDocumentHeight() {
        const body = document.body;
        const html = document.documentElement;

        return Math.max(
            body.scrollHeight, body.offsetHeight,
            html.clientHeight, html.scrollHeight, html.offsetHeight
        );
    }

    function getScrollTop() {
        return (window.pageYOffset !== undefined) ? window.pageYOffset : (document.documentElement || document.body.parentNode || document.body).scrollTop;
    }

    window.onscroll = function () {
        if (getScrollTop() < getDocumentHeight() - window.innerHeight) return;
        var filter = $('input[name="user-recommend-filter"]:checked').val();
        FetchRecommendationList(++page, filter);
    };

    FetchRecommendationList(page, "all");

    $('.custom-checkbox').click(function () {
        var filter = $('input[name="user-recommend-filter"]:checked').val();
        page = 1;
        FetchRecommendationList(page, filter);
    });

    $('#add-button').popover({
        html: true,
        content: function () {
            var tempScrollTop = $(window).scrollTop;
            $(window).scrollTop(tempScrollTop);
            console.log("aaaaaaaa" + $(window).scrollTop);
            /*window.scrollTo(0, document.body.scrollHeight);*/
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
                      var filter = $('input[name="user-recommend-filter"]:checked').val();
                    FetchRecommendationList(page, filter);
                },
                error: function (er) {
                    console.log(er);
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
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    FetchRecommendationList(page, filter);
                },
                error: function (er) {
                    console.log(er);
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
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    page = 1;
                    FetchRecommendationList(page, filter);
                },
                error: function (er) {
                    console.log(er);
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
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    page = 1;
                    FetchRecommendationList(page, filter);
                },
                error: function (er) {
                    console.log(er);
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
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    page = 1;
                    FetchRecommendationList(page, filter);
                },
                error: function (er) {
                    console.log(er);
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
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    page = 1;
                    FetchRecommendationList(page, filter);
                },
                error: function (er) {
                    console.log(er);
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
                    var filter = $('input[name="user-recommend-filter"]:checked').val();
                    FetchRecommendationList(page, filter);
                },
                error: function (er) {
                    console.log(er);
                }
            }
        )
    }
}

