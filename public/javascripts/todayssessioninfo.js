$(function () {
    $('.getKnolxDetailsExpired').click(function () {
        var id = this.id;
        var sessionId = id.split('-');
        var json = document.getElementById(sessionId[1]).value;
        opener(json);
        expire();
    });

    $('.fillFeedback').click(function () {
        $('#feedbackAttendance-' + this.id).modal('show');
        $('#feed-message').html("");
    });

    $('.btn-success').click(function () {
        $('#feedbackAttendance-' + this.id).modal('hide');
        document.getElementById('display-feed-form').style.display = 'block';
        currentFeedbackProfile = this.value;
        formOpener(this.value);
        fetchResponse(this.id);
    });

    $('.btn-danger').click(function () {
        $('#feedbackAttendance-' + this.id).modal('hide');
        currentFeedbackProfile = this.value;
        submittedFeedbackFormForNotAttend(this.id);
    });

    $('.getKnolxDetailsActive').click(function () {
        var id = this.id;
        var sessionId = id.split('-');
        var json = document.getElementById(sessionId[1]).value;
        opener(json);
    });

    $('.submitFeedbackForm').click(function () {
        submittedFeedbackForm();
    });

    $('body').on('click', '#success_text_color-background-color', function () {
        $('#session-form-info').modal('hide');
    });

    $('body').on('click', '#failure_text_color-background-color', function () {
        window.location.reload();
    });

});

function fetchResponse(sessionId) {
    var form = document.getElementById(sessionId + "-form").value;
    var feedbackForm = JSON.parse(form);

    jsRoutes.controllers.FeedbackFormsResponseController.fetchFeedbackFormResponse(sessionId).ajax(
        {
            type: 'GET',
            processData: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                var responses = JSON.parse(data);
                loadFeedbackForm(feedbackForm, sessionId);
                fillFeedbackResponses(responses);
            },
            error: function (er) {
                loadFeedbackForm(feedbackForm, sessionId);
            }
        });
}

function fillFeedbackResponses(responses) {
    for (var questionNumber = 0; questionNumber < questions.length; questionNumber++) {
        var type = questions[questionNumber]['questionType'];
        if (type == "MCQ") {
            var options = questions[questionNumber]['options'];
            for (var optionNumber = 0; optionNumber < options.length; optionNumber++) {
                if (responses[questionNumber] === options[optionNumber]) {
                    document.getElementById("option-" + optionNumber + "-" + questionNumber).checked = true;
                }

            }
        }
        else {
            document.getElementById("option-" + questionNumber).value = responses[questionNumber];
        }
    }

}

function opener(value) {
    var details = JSON.parse(value);

    $('#session-topic').html(details.topic);
    $('#author').html(details.author);
    $('#session').html(details.session);
    $('#scheduled').html(details.scheduled);
    $('#expire').html(details.expire);
    $('#sessiontype').html(details.sessiontype);
    $('#sessionDetailInfo').modal('show');
}

function formOpener(value) {
    var details = JSON.parse(value);

    $('#form-session-topic').html(details.topic);
    $('#form-author').html(details.author);
    $('#form-session').html(details.session);
    $('#form-scheduled').html(details.scheduled);
    $('#form-expire').html(details.expire);
    $('#form-sessiontype').html(details.sessiontype);
}

function expire() {
    $('#feedback-info-modal-header').css('background-color', '#a8a8a8');
    $('#session-modal-footer-btn').css('background-color', '#a8a8a8');
}

var mandatoryelements = [];
var questions = [];
var feedbackFormId = "";
var currentFeedbackProfile = "";

function loadFeedbackForm(values, sessionId) {


    $('#mandatory-warning').css("display", "none");
    $('#feedback-response-form').html("");
    var optionsLoaded = "";
    questions = values['questions'];
    feedbackFormId = values['id'];
    for (var questionNumber = 0; questionNumber < questions.length; questionNumber++) {
        var options = questions[questionNumber]['options'];
        var type = questions[questionNumber]['questionType'];
        var required = questions[questionNumber]['mandatory'];
        if (required) {
            mandatoryelements.push(questionNumber);
        }
        if (type == "MCQ") {
            for (var optionNumber = 0; optionNumber < options.length; optionNumber++) {
            if(options[optionNumber] == "Did not attend") {
                continue;
            } else {
                optionsLoaded += "<div class='row questions-inlining'>" +
                    "<div class='col-md-1'></div>" +
                    "<div style='display: inline-flex; width:auto; text-align:center'>" +
                    "<p class='checkbox-text form-card-options' style='margin-top: 6px !important;'>" + options[optionNumber] +
                                        "</p>"+
                    "<label class='radio-button'>" +
                    "<input type='radio'  name='option-" + questionNumber + "' id='option-" + optionNumber + "-" + questionNumber + "' class='custom-checkbox' value='" + options[optionNumber] + "'/>" +
                    "<span class='lab_text'></span>" +
                    "</label>" +
                    "</div>" +
                    "</div>";
                }
            }
        }
        else {
            optionsLoaded += "<div class='row option-questions '>" +
                "<div class='col-md-1'></div>" +
                "<div class='col-md-10'>" +
                "​<textarea class='comments' rows='2' id='option-" + questionNumber + "' cols='70' placeholder='Edit Comment Here!'></textarea>" +
                "</div>";
            if (!required) {
                optionsLoaded += '<div id="parent" class="add-option-parent"><div>' +
                    '<label class="checkbox-outer">' +
                    '<span class="mendatory_text"></span>' +
                    '<p class="mandatory-text">Optional</p>' +
                    '</label>' +
                    '</div>' +
                    '</div></div>';
            }
            else {
                optionsLoaded += '</div>'
            }


        }

        $('#feedback-response-form').append(
            "<div class='question-card form-question-card' id='question-outer-" + questionNumber + "'>" +
            "<label class='card-questions-label'>" +
                        "<p id='question-" + questionNumber + "' class='card-questions-other'>" + questions[questionNumber]['question'] + "</p>" +
            "</label>" + optionsLoaded + "</div>"
        );

        document.getElementById('current-form').value = JSON.stringify(values);
        document.getElementById('current-session').value = sessionId;

        optionsLoaded = "";
    }

    $('#session-form-info').modal('show');
}

class FeedbackFormResponse {
    constructor(sessionId, feedbackFormId, responses, score) {
        this.sessionId = sessionId;
        this.feedbackFormId = feedbackFormId;
        this.responses = responses;
        this.score = score
    }
}

function submittedFeedbackForm() {
    var feedbackForm = JSON.parse(document.getElementById('current-form').value);
    var questions = feedbackForm['questions'];
    var questionCount = Object.keys(questions);
    var questionOptionInformation = [];
    var sessionId = document.getElementById("current-session").value;
    var score = 0;
    var mcqCount = 0;

    for (var questionNumber = 0; questionNumber < questionCount.length; questionNumber++) {
        var responseName = "option-" + questionNumber;
        var response = "";
        if (questions[questionNumber]["questionType"] == "MCQ") {
            response = getResponse(responseName);
            var noOfOptions = getNoOfOptions(responseName);
            var receivedScore = (getResponseValue(responseName) / noOfOptions) * 100;
            score = ((score * mcqCount) + receivedScore) / (mcqCount + 1);
            mcqCount++;
        } else {
            response = document.getElementById(responseName).value;
        }
        questionOptionInformation.push(response)
    }
    var feedbackFormWithResponse = new FeedbackFormResponse(sessionId, feedbackFormId, questionOptionInformation, score);

    if (isFormResponseValid(feedbackFormWithResponse)) {

        jsRoutes.controllers.FeedbackFormsResponseController.storeFeedbackFormResponse().ajax(
            {
                type: "POST",
                processData: false,
                contentType: 'application/json',
                data: JSON.stringify(feedbackFormWithResponse),
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;
                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (data) {
                    var currentFeedback = JSON.parse(currentFeedbackProfile);
                    ackMessage("success_text", "Thank you!", "for your valuable feedback", 'We\'ll let <strong>' + currentFeedback.author.split('@')[0].replace('.', '') + '</strong> know your views on this <strong>' + currentFeedback.sessiontype + '</strong> session, <strong>anonymously</strong>. Also you can change your response anytime until this feedback form is active', "okay", 'success_text_color', 'success_text_color-background-color');
                },
                error: function (er) {
                    ackMessage("failure_text", "Oops!", "Something went wrong", 'We are unable to process your request, refreshing this page may fix this issue, in case it keeps occurring please contact the administrator', "Refresh", 'failure_text_color', 'failure_text_color-background-color');
                }
            })
    }
}

function getResponse(name) {
    var group = document.getElementsByName(name);
    for (var i = 0; i < group.length; i++) {
        if (group[i].checked) {
            return group[i].value;
        }
    }
    return "";
}

function getResponseValue(name) {
    var group = document.getElementsByName(name);
    for (var i = 0; i < group.length; i++) {
        if (group[i].checked) {
            return (parseInt(group[i].id.split("-")[1]));
        }
    }
    return "";
}

function getNoOfOptions(name) {
    return document.getElementsByName(name).length;
}

function isFormResponseValid(filledForm) {
    var result = true;
    var response = filledForm['responses'];
    for (var questionNumber = 0; questionNumber < questions.length; questionNumber++) {
        if (mandatoryelements.includes(questionNumber)) {
            if (response[questionNumber] == "") {
                $("#question-outer-" + questionNumber).css("border-bottom", "6px solid #E74C3C");
                $('#mandatory-warning').css("display", "block");
                result = false;
            }
            else {
                $("#question-outer-" + questionNumber).css("border", "none");
            }
        }
    }

    return result;
}


function ackMessage(icon, greeting, tagline, ackMessage, btnText, colorClass, bgColorClass) {
    var message = '<div class="col-md-12 acknowledgement-message">' +
        '<div class="col-md-2 acknowledgement-icon">' +
        '<label class="radio-button">' +
        '<input type="radio"  class="custom-checkbox" checked="checked"/>' +
        '<span class="' + icon + ' ' + colorClass + '"></span>' +
        '</label>' +
        '</div>' +
        '<div class="col-md-10 acknowledgement-text">' +
        '<div class="col-md-2"></div>' +
        '<div class="col-md-5 ' + colorClass + '">' + greeting + '' +
        '<p class="acknowledgement-text_tag">' + tagline + '</p>' +
        '</div>' +
        '<div class="col-md-2"></div>' +
        '</div>' +
        '<div class="col-md-12">' +
        '<div class="col-md-2"></div>' +
        '<div class="col-md-8 acknowledgement-custom-message">' + ackMessage + '</div>' +
        '<div class="col-md-2"></div>' +
        '</div>' +
        '<div class="col-md-12">' +
        '<div class="col-md-2"></div>' +
        '<div class="col-md-8 acknowledgement-custom-message"><button type="button" id="' + bgColorClass + '" class="btn btn-default btn-lg submission-success-okay-btn">' + btnText + '</button></div>' +
        '<div class="col-md-2"></div>' +
        '</div>' +
        '</div>';

    document.getElementById('display-feed-form').style.display = 'none';
    $('#feed-message').html(message);
}

function submittedFeedbackFormForNotAttend(sessionId) {
    var form = document.getElementById(sessionId + "-form").value;
    var feedbackForm = JSON.parse(form);
    feedbackFormId = feedbackForm['id'];
    var questions = feedbackForm['questions'];
    var questionCount = Object.keys(questions);
    var questionOptionInformation = [];
    var score = 0;
    for (var questionNumber = 0; questionNumber < questionCount.length; questionNumber++) {
        questionOptionInformation.push("Did not attend")
    }
    var feedbackFormWithResponse = new FeedbackFormResponse(sessionId, feedbackFormId, questionOptionInformation, score);

    if (isFormResponseValid(feedbackFormWithResponse)) {

        jsRoutes.controllers.FeedbackFormsResponseController.storeFeedbackFormResponse().ajax(
            {
                type: "POST",
                processData: false,
                contentType: 'application/json',
                data: JSON.stringify(feedbackFormWithResponse),
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;
                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (data) {
                    var currentFeedback = JSON.parse(currentFeedbackProfile);
                    $('#session-form-info').modal('show');
                    ackMessage("success_text", "Thank you!", "for your valuable feedback", 'We\'ll let <strong>' + currentFeedback.author.split('@')[0].replace('.', '') + '</strong> know your views on this <strong>' + currentFeedback.sessiontype + '</strong> session, <strong>anonymously</strong>. Also you can change your response anytime until this feedback form is active', "okay", 'success_text_color', 'success_text_color-background-color');
                },
                error: function (er) {
                    ackMessage("failure_text", "Oops!", "Something went wrong", 'We are unable to process your request, refreshing this page may fix this issue, in case it keeps occurring please contact the administrator', "Refresh", 'failure_text_color', 'failure_text_color-background-color');
                }
            })
    }
}
