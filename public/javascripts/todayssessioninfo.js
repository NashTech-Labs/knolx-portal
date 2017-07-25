$(function () {
    $('#getKnolxDetailsExpired').click(function () {
        var json = document.getElementById('getKnolxDetailsJson').value;
        opener(json);
        expire();
    });

    $('#fillFeedback').click(function () {
        var json = document.getElementById('getKnolxDetailsJson').value;
        var form = document.getElementById('feedbackform').value;
        formOpener(json);
        loadFeedbackForm(form);
    });

    $('#getKnolxDetailsActive').click(function () {
        var json = document.getElementById('getKnolxDetailsJson').value;
        opener(json);
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
    $('#session-detail-info').modal('show');
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

function loadFeedbackForm(form) {
    var values = JSON.parse(form);
    $('#feedback-response-form').html("");
    var optionsLoaded = "";
    var questions = values['questions'];

    for (var questionNumber = 0; questionNumber < questions.length; questionNumber++) {
        var options = questions[questionNumber]['options'];

        for (var optionNumber = 0; optionNumber < options.length; optionNumber++) {
            optionsLoaded += "<div class='row'>" +
                "<div class='col-md-1'></div>" +
                "<div class='col-md-10'>" +
                "<label class='radio-button'>" +
                "<input type='radio'  name='radio-" + questionNumber + "' id='' class='custom-checkbox' value='true'/>" +
                "<span class='lab_text'></span>" +
                "<p class='checkbox-text form-card-options'>" + options[optionNumber] +
                "</p>" +
                "</label>" +
                "</div>" +
                "</div>";
        }

        $('#feedback-response-form').append(
            "<div class='question-card form-question-card' id='question'>" +
            "<label class='card-questions-label'>" +
            "<p id='questionValue' class='card-questions-other'>" + questions[questionNumber]['question'] + "</p>" +
            "</label>" + optionsLoaded + "</div>"
        );

        optionsLoaded = "";

    }

    $('#session-form-info').modal('show');
}

class FeedbackFormResponse {
    constructor(sessionId, questions) {
        this.sessionId = sessionId;
        this.questions = questions;
    }
}

class QuestionAndResponseInformation {
    constructor(question, options, response) {
        this.question = question;
        this.options = options;
        this.response = response;
    }
}
