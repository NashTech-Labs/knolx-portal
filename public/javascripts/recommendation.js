$(function () {
    ko.applyBindings(new Recommendation());
});

function Recommendation() {
    var page = 1;

    var readonly = "";
    var email = $("#user-email").val();

    if (email.length > 0) {
        readonly = "readonly";
    } else {
        readonly = "";
    }

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
        fetchRecommendationList(++page, filter, sort);
    };

    $('#add-button').confirm({
        title: 'Add Recommendation!',
        content: '' +
        '<form action="" class="formName">' +
        '<div class="form-group">' +
        '<input type="text" id="email" value="' + email + '" class="update-field login-second" placeholder="Email" ' + readonly + ' />' +
        '<input type="text" id="user-name" class="update-field login-second" placeholder="Name" required />' +
        '<textarea id="recommend-topic" class="recommendation-description-content" maxlength="140" rows="6" cols="10" placeholder="Recommendation Topic" required ></textarea>' +
        '<textarea id="recommend-text" class="recommendation-description-content" maxlength="280" rows="6" cols="10" placeholder="Recommendation Description" required ></textarea>' +
        '</div>' +
        '</form>',
        buttons: {
            formSubmit: {
                text: 'Add',
                btnClass: 'btn-blue',
                action: function () {
                    var username = this.$content.find('#user-name').val();
                    var recommendationTopic = this.$content.find('#recommend-topic').val();
                    var description = this.$content.find('#recommend-text').val();
                    if (!username) {
                        $.alert('Username must not be empty');
                        return false;
                    }
                    if (!recommendationTopic) {
                        $.alert('Recommendation Topic must not be empty');
                        return false;
                    }
                    if (recommendationTopic.length > 140) {
                        $.alert('Recommendation Topic must be of 140 characters or less');
                        return false;
                    }
                    if (!description) {
                        $.alert('Description must not be empty');
                        return false;
                    }
                    if (description.length > 280) {
                        $.alert('Description must be of 280 characters or less');
                        return false;
                    }
                    var formData = {
                        'email': $("#email").val(),
                        'name': $("#user-name").val(),
                        'topic': $("#recommend-topic").val(),
                        'description': $("#recommend-text").val()
                    };
                    jsRoutes.controllers.RecommendationController.addRecommendation().ajax(
                        {
                            type: "POST",
                            processData: false,
                            contentType: 'application/json',
                            data: JSON.stringify(formData),
                            beforeSend: function (request) {
                                var csrfToken = document.getElementById('csrfToken').value;

                                return request.setRequestHeader('CSRF-Token', csrfToken);
                            },
                            success: function (values) {
                                $.alert(values);
                                var filter = $('input[name="user-recommend-filter"]:checked').val();
                                var sort = $('#sort-entries').val();
                                fetchRecommendationList(page, filter, sort);
                                getNotificationCount();
                            },
                            error: function (er) {
                                $.alert(er.responseText);
                                console.log(er.responseText);
                            }
                        }
                    )
                }
            },
            cancel: function () {
                //close
            }
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
    fetchRecommendationList(page, "all", sort);

    $('.custom-checkbox').click(function () {
        var sort = $('#sort-entries').val();
        var filter = $('input[name="user-recommend-filter"]:checked').val();
        page = 1;
        fetchRecommendationList(page, filter, sort);
    });

    $('#sort-entries').on('change', function () {
        var filter = $('input[name="user-recommend-filter"]:checked').val();
        fetchRecommendationList(page, filter, this.value);
    });

    var self = this;
    self.recommendations = ko.observableArray([]);

    self.upVoteByUser = function (id) {

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
                    fetchRecommendationList(page, filter, sort);
                },
                error: function (er) {
                    console.log(er);
                }
            }
        )
    };

    self.downVoteByUser = function (id) {
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
                    fetchRecommendationList(page, filter, sort);
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
                    fetchRecommendationList(page, filter, sort);
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
                    fetchRecommendationList(page, filter, sort);
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
                    fetchRecommendationList(page, filter, sort);
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
                    fetchRecommendationList(page, filter, sort);
                },
                error: function (er) {
                    console.log(er);
                }
            }
        )
    };

    self.requestByUser = function (id) {
        if (typeof(Storage) !== "undefined") {
            sessionStorage.setItem("recommendationId", id);
        }
        window.location = jsRoutes.controllers.RecommendationController.scheduleSession().url
    };

    self.redirectToLogin = function (id, vote) {
        var form = document.createElement("form");

        form.method = "POST";
        var url = "";
        if(vote === "upvote") {
            url = jsRoutes.controllers.RecommendationController.upVote(id).url;
        } else {
            url = jsRoutes.controllers.RecommendationController.downVote(id).url;
        }
        form.action = url;
        form.style.display = "none";
        var csrfToken = $("#csrfToken").val();

        var input = document.createElement("input");
        input.type = "hidden";
        input.value = csrfToken;
        input.id = "csrfToken";
        input.name = "csrfToken";
        form.appendChild(input);

        document.body.appendChild(form);
        form.submit();
        document.body.removeChild(form);
    };

    function fetchRecommendationList(pageNumber, filter, sortBy) {

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
                    self.recommendations(values);
                },
                error: function (er) {
                    console.log(er);
                }
            }
        )
    }

}
