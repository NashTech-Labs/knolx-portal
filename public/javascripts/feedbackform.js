document.getElementById("feedbackFormCreate").addEventListener("click", createForm);
document.getElementById("addQuestionButton").addEventListener("click", addQuestion);
document.getElementById("addCommentButton").addEventListener("click", addComment);
document.getElementById("addOption-0-0").addEventListener("click", function () {
    addOption(this)
});

class FeedbackForm {
    constructor(name, questions) {
        this.name = name;
        this.questions = questions;
    }
}

class Question {
    constructor(question, options, questionType, mandatory) {
        this.question = question;
        this.options = options;
        this.questionType = questionType;
        this.mandatory = mandatory;
    }
}

var optionsCount = 0;
var questionCount = 0;
var questions = new Map([]);
questions.set(0, [0]);

function searchAndRemove(arr, elem) {
    for (var i = 0; i <= arr.length - 1; i++) {
        if (arr[i] === elem) {
            arr.splice(i, 1)
        }
    }
}

function createForm() {
    var questionsValues = [];

    var formName = document.getElementById('form-name').value;

    questions.forEach(function (options, question, obj) {

            var questionValueField = document.getElementById('questionValue-' + question);

            var optionValues = [];
            var mandatory = true;

            if (questionValueField != null) {
                var questionValue = questionValueField.value;
                var typeValue = 'MCQ';
                for (var i = 0; i <= options.length - 1; i++) {
                    var optionValue = document.getElementById('optionValue-' + question + '-' + options[i]).value;
                    optionValues.push(optionValue)

                }
            } else {
                questionValue = document.getElementById('questionCommentValue-' + question).value;
                typeValue = 'COMMENT';
                optionValue = document.getElementById('optionValue-' + question + '-' + options[0]).value;
                optionValues.push(optionValue);
                if (!document.getElementById("questionMandatoryValue-" + question).checked) {
                    mandatory = false;
                }

            }

            questionsValues.push(new Question(questionValue, optionValues, typeValue, mandatory))
        }
    );

    var feedbackForm = new FeedbackForm(formName, questionsValues);

    $('#errorMessage').remove();
    $('#successMessage').remove();

    jsRoutes.controllers.FeedbackFormsController.createFeedbackForm().ajax(
        {
            type: "POST",
            processData: false,
            contentType: 'application/json',
            data: JSON.stringify(feedbackForm),
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                window.location = jsRoutes.controllers.FeedbackFormsController.manageFeedbackForm(1)['url'];
                alert("Form Successfully Created !")
            },
            error: function (er) {
                $('#response').html(
                    "<div class='alert alert-danger alert-dismissable fade in'>" +
                    "<a href='#' class='close' data-dismiss='alert' aria-label='close'>&times;</a>" + er.responseText +
                    "</div>"
                )
            }
        });
}

function deleteOption(deleteElem) {
    var splitIds = deleteElem.id.split("-");
    var questionCountId = parseInt(splitIds[1]);
    var optionCountId = parseInt(splitIds[2]);

    searchAndRemove(questions.get(questionCountId), optionCountId);

    $('#option-' + questionCountId + '-' + optionCountId).fadeOut('slow', function () {
        $(this).remove();
    });
}


function addOption(addElem) {
    var splitIds = addElem.id.split("-");

    var questionCountId = parseInt(splitIds[1]);
    var optionCountId = parseInt(splitIds[2]) + 1;

    questions.get(questionCountId).push(optionCountId);

    $('#options-' + questionCountId).append(
        '<div class="row" id="option-' + questionCountId + '-' + optionCountId + '">' +
        '   <div class="col-md-1"></div>' +
        '   <div class="col-md-10">' +
        '       <label class="radio-button">' +
        '           <input type="radio" disabled name="radopt" id="" class="custom-checkbox" value="true"/>' +
        '           <span class="lab_text"></span>' +
        '           <p class="checkbox-text">' +
        '               <input id="optionValue-' + questionCountId + '-' + optionCountId + '" class="card-options" placeholder="Option" type="text"/>' +
        '           </p>' +
        '           <a class="fa fa-times-circle delete-option-button" id="deleteOption-' + questionCountId + '-' + optionCountId + '"></a>' +
        '       </label>' +
        '   </div>' +
        '   <div class="col-md-1" ></div>' +
        '</div>' +
        '<div id="parent" class="add-option-parent"><div>' +
        '<i class="fa fa-plus-circle add-option" aria-hidden="true" id="addOption-' + questionCountId + '-' + optionCountId + '"></i>' +
        '</div>' +
        '</div>'
    );

    document.getElementById("addOption-" + questionCountId + '-' + optionCountId).addEventListener("click", function () {
        addOption(this)
    });
    document.getElementById("deleteOption-" + questionCountId + '-' + optionCountId).addEventListener("click", function () {
        deleteOption(this)
    });

    $('#addOption-' + questionCountId + '-' + (optionCountId - 1)).remove();
}

function deleteQuestion(questionElem) {
    var splitIds = questionElem.id.split("-");

    var questionCountId = parseInt(splitIds[1]);

    questions.delete(questionCountId);

    $('#question-' + questionCountId).fadeOut('slow', function () {
        $(this).remove();
    });
}

function addQuestion() {
    questionCount = questionCount + 1;
    questions.set(questionCount, [0]);

    $('#questions').append(
        '<div class="question-card" id="question-' + questionCount + '">' +
        '   <label class="card-questions-label">' +
        '       <input id="questionValue-' + questionCount + '" class="card-questions-other" placeholder="Question ?" type="text">' +
        '       <i id="deleteQuestion-' + questionCount + '" class="fa fa-trash-o delQuestion"></i>' +
        '   </label>' +
        '   <div id="options-' + questionCount + '">' +
        '       <div class="row" id="option-' + questionCount + '-' + optionsCount + '">' +
        '           <div class="col-md-1" ></div>' +
        '           <div class="col-md-10" >' +
        '               <label class="radio-button">' +
        '                   <input type="radio" disabled name="radopt" id="" class="custom-checkbox" value="true"/>' +
        '                   <span class="lab_text"></span>' +
        '                   <p class="checkbox-text">' +
        '                       <input id="optionValue-' + questionCount + '-' + 0 + '"  class="card-options" placeholder="Option" type="text"/>' +
        '                   </p>' +
        '               </label>' +
        '           </div>' +
        '           <div class="col-md-1" > </div>' +
        '       </div>' +
        '   </div>' +
        '   <br>' +
        '   <div id="parent" class="add-option-parent"><div>' +
        '   <i class="fa fa-plus-circle add-option" aria-hidden="true" id="addOption-' + questionCount + '-' + optionsCount + '"></i>' +
        '   </div></div>' +
        '</div>');

    document.getElementById("addOption-" + questionCount + '-' + optionsCount).addEventListener("click", function () {
        addOption(this)
    });
    document.getElementById("deleteQuestion-" + questionCount).addEventListener("click", function () {
        deleteQuestion(this)
    });

    window.scrollTo(0, document.body.scrollHeight);
}


function addComment() {
    questionCount = questionCount + 1;
    questions.set(questionCount, [0]);

    $('#questions').append(
        "<div class ='question-card' id='question-" + questionCount + "'>" +
        "<div>" +
        "<div class='col-md-12'>" +
        "<input class='card-questions-other' id='questionCommentValue-" + questionCount + "' placeholder='Question ?' type='text'>" +
        "<i id='deleteQuestion-" + questionCount + "' class='fa fa-trash-o delQuestion'></i>" +
        "</div>" +
        "<br>" +
        "<div id='options-" + questionCount + "'>" +
        "<div class='row' id='option-" + questionCount + "-0'>" +
        "<div class='col-md-1' > </div>" +
        "<div class='col-md-10' >" +
        "<input type='text' id='optionValue-" + questionCount + "-" + 0 + "' class='comment-section' value='Comments Goes Here!' disabled/>" +
        "</div>" +
        "<div class='col-md-1' ></div>" +
        "</div>" +
        "</div>" +
        "</div>" +

        '<div id="parent" class="add-option-parent"><div>' +
        '<label class="checkbox-outer">' +
        "<input type='checkbox' id='questionMandatoryValue-" + questionCount + "' class='custom-checkbox'/>" +
        '<span class="pin_text"></span>' +
        '<p class="pin-checkbox-text">Mandatory</p>' +
        '</label>' +
        '</div>' +
        '</div>' +


        "</div>"
    );

    document.getElementById("deleteQuestion-" + questionCount).addEventListener("click", function () {
        deleteQuestion(this)
    });

    window.scrollTo(0, document.body.scrollHeight);
}

$(function () {
    $(".add-question-button").click(function () {

            $(".adjacent").fadeIn();
            $(".add-question-button").css({"color": "#5499C7", "text-shadow": "None"});
            window.scrollTo(0, document.body.scrollHeight);
        }
    );
});

$(document).mouseup(function (e) {
    var itself = $(".adjacent");
    var addBtn = $(".add-question-button");
    if (!addBtn.is(e.target) && !itself.is(e.target) && addBtn.has(e.target).length === 0) {
        itself.fadeOut();
        addBtn.css({"color": "#fff", "text-shadow": "0 0px 6px #999"})
    }
});

