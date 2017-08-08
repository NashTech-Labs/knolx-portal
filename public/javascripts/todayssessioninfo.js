$(function () {
    $('#getKnolxDetailsExpired').click(function () {
        var json = document.getElementById('getKnolxDetailsJson').value;
        opener(json);
        expire();
    });

    $('#fillFeedback').click(function () {
        var json = document.getElementById('getKnolxDetailsJson').value;
        var form = document.getElementById('feedbackForm').value;
        formOpener(json);
        loadFeedbackForm(form);
    });

    $('#getKnolxDetailsActive').click(function () {
        var json = document.getElementById('getKnolxDetailsJson').value;
        opener(json);
    });
    $('#submitFeedbackForm').click(function () {
        submittedFeedbackForm();
    });
});

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

function loadFeedbackForm(form) {
    var values = JSON.parse(form);
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
                optionsLoaded += "<div class='row'>" +
                    "<div class='col-md-1'></div>" +
                    "<div class='col-md-10'>" +
                    "<label class='radio-button'>" +
                    "<input type='radio'  name='option-" + questionNumber + "' id='' class='custom-checkbox' value='" + options[optionNumber] + "'/>" +
                    "<span class='lab_text'></span>" +
                    "<p class='checkbox-text form-card-options'>" + options[optionNumber] +
                    "</p>" +
                    "</label>" +
                    "</div>" +
                    "</div>";
            }
        }
        else {
            optionsLoaded += "<div class='row'>" +
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

        optionsLoaded = "";
    }

    $('#session-form-info').modal('show');
}

class FeedbackFormResponse {
    constructor(sessionId, feedbackFormId, responses) {
        this.sessionId = sessionId;
        this.feedbackFormId = feedbackFormId;
        this.responses = responses;
    }
}

function submittedFeedbackForm() {

    var feedbackForm = JSON.parse(document.getElementById('feedbackForm').value);
    var questions = feedbackForm['questions'];
    var questionCount = Object.keys(questions);
    var questionOptionInformation = [];
    var sessionId = document.getElementById("sessionId").value;

    for (var questionNumber = 0; questionNumber < questionCount.length; questionNumber++) {
        var responseName = "option-" + questionNumber;
        var response = "";
        if (questions[questionNumber]["questionType"] == "MCQ") {
            response = getResponse(responseName);
        } else {
            response = document.getElementById(responseName).value;
        }
        questionOptionInformation.push(response)
    }
    var feedbackFormWithResponse = new FeedbackFormResponse(sessionId, feedbackFormId, questionOptionInformation);

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
                    alert("success")
                },
                error: function (er) {
                    alert(er.responseText);
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
