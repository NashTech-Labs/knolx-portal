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
        var type = questions[questionNumber]['questionType'];
       if(type=="MCQ") {
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
       else{
           optionsLoaded += "<div class='row'>" +
               "<div class='col-md-1'></div>" +
               "<div class='col-md-10'>" +
               "​<textarea class='comments' rows='2' name='option-" + questionNumber + "' cols='70' placeholder='Edit Comment Here!'></textarea>"+
               "</div>" +
               "</div>";
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
    constructor(sessionId, questionsAndResponses) {
        this.sessionId = sessionId;
        this.questionsAndResponses = questionsAndResponses;
    }
}

class QuestionAndResponseInformation {
    constructor(question, options, response) {
        this.question = question;
        this.options = options;
        this.response = response;
    }
}

function submittedFeedbackForm(){

    var feedbackForm =  JSON.parse(document.getElementById('feedbackform').value);
    var questions = feedbackForm['questions'];
    var questionCount = Object.keys(questions);
    var questionOptionInformation =[];
    var sessionId = document.getElementById("sessionId").value;

    for(var questionNumber =0 ;questionNumber < questionCount.length ; questionNumber++ ){
        var question =  questions[questionNumber]['question'];
        var options  =  questions[questionNumber]['options'];
        var responseName = "option-"+questionNumber;
        var response =  getResponse(responseName);
        questionOptionInformation.push(new QuestionAndResponseInformation(question,options,response))
    }
    var feedbackFormWithResponse = new FeedbackFormResponse(sessionId,questionOptionInformation);
   alert(JSON.stringify(feedbackFormWithResponse));
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
alert("failure")
            }
        })
}

function getResponse(name) {
    var group = document.getElementsByName(name);
    for (var i=0;i<group.length;i++) {
        if (group[i].checked) {
            return group[i].value;
        }
    }
    return '';
}

