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
        var sort = $('#sort-entries').val();
        FetchRecommendationList(++page, filter, sort);
    };

    $('#add-button').confirm({
        title: 'Add Recommendation!',
        content: '' +
        '<form action="" class="formName">' +
        '<div class="form-group">' +
        '<textarea id="recommend-text" class="textbox-recommend-main form-control" minlength="10" maxlength="140" rows="6" cols="10"></textarea>' +
        '</div>' +
        '</form>',
        buttons: {
            formSubmit: {
                text: 'Add',
                btnClass: 'btn-blue',
                action: function () {
                    var recommendation = this.$content.find('#recommend-text').val();
                    if (!recommendation) {
                        $.alert('Provide a valid Recommendation');
                        return false;
                    }
                    jsRoutes.controllers.RecommendationController.addRecommendation(recommendation).ajax(
                        {
                            type: "POST",
                            processData: false,
                            beforeSend: function (request) {
                                var csrfToken = document.getElementById('csrfToken').value;

                                return request.setRequestHeader('CSRF-Token', csrfToken);
                            },
                            success: function (values) {
                                $.alert(values);
                                var filter = $('input[name="user-recommend-filter"]:checked').val();
                                var sort = $('#sort-entries').val();
                                FetchRecommendationList(page, filter, sort);
                            },
                            error: function (er) {
                                console.log(er);
                            }
                        }
                    )
                }
            },
            cancel: function () {
                //close
            },
        },
        onContentReady: function () {
            var jc = this;
            this.$content.find('form').on('submit', function (e) {
                e.preventDefault();
                jc.$$formSubmit.trigger('click'); // reference the button and click it
            });
        }
    });

    var sort = $('#sort-entries').val();
    FetchRecommendationList(page, "all", sort);

    $('.custom-checkbox').click(function () {
        var sort = $('#sort-entries').val();
        var filter = $('input[name="user-recommend-filter"]:checked').val();
        page = 1;
        FetchRecommendationList(page, filter, sort);
    });

    $('#sort-entries').on('change', function () {
        var filter = $('input[name="user-recommend-filter"]:checked').val();
        FetchRecommendationList(page, filter, this.value);
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
                    var sort = $('#sort-entries').val();
                    FetchRecommendationList(page, filter, sort);
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
                    var sort = $('#sort-entries').val();
                    FetchRecommendationList(page, filter, sort);
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
                    var sort = $('#sort-entries').val();
                    FetchRecommendationList(page, filter, sort);
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
                    var sort = $('#sort-entries').val();
                    FetchRecommendationList(page, filter, sort);
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
                    var sort = $('#sort-entries').val();
                    FetchRecommendationList(page, filter, sort);
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
                    var sort = $('#sort-entries').val();
                    FetchRecommendationList(page, filter, sort);
                },
                error: function (er) {
                    console.log(er);
                }
            }
        )
    };


    function FetchRecommendationList(pageNumber, filter, sortBy) {

        jsRoutes.controllers.RecommendationController.recommendationList(pageNumber, filter, sortBy).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    $('.wrapper').height($('#content').height());
                    self.recommendation(values);
                },
                error: function (er) {
                    console.log(er);
                }
            }
        )
    }

}

