class FeedbackForm {
    constructor(name, questions) {
        this.name = name;
        this.questions = questions;
    }
}

class Question {
    constructor(question, options) {
        this.question = question;
        this.options = options;
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
            var questionValue = document.getElementById('questionValue-' + question).value;
            var optionValues = [];

            for (var i = 0; i <= options.length - 1; i++) {
                var optionValue = document.getElementById('optionValue-' + question + '-' + options[i]).value;

                optionValues.push(optionValue)
            }

            questionsValues.push(new Question(questionValue, optionValues))
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
            success: function (data) {
                window.location = "/feedbackform/manage?pageNumber=1";
                alert("Form Successfully Created !")
            },
            error: function (er) {
                $('#response').html(
                    "<div class='alert alert-danger alert-dismissable fade in'>" +
                    "   <a href='#' class='close' data-dismiss='alert' aria-label='close'>&times;</a>" + er.responseText +
                    "</div>"
                )
            }
        })
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
        '           <a class="fa fa-times-circle delete-option-button" id="deleteOption-' + questionCountId + '-' + optionCountId + '" onclick="deleteOption(this)"></a>' +
        '       </label>' +
        '   </div>' +
        '   <div class="col-md-1" ></div>' +
        '</div>' +
        '<div id="parent" style="width: 100%; text-align:center;"><div>' +
        '<i class="fa fa-plus-circle add-option" aria-hidden="true" onclick="addOption(this)" id="addOption-' + questionCountId + '-' + optionCountId + '"></i>' +
        '</div>' +
        '</div>'
    );

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
        '       <i id="deleteQuestion-' + questionCount + '" onclick="deleteQuestion(this)" class="fa fa-trash-o delQuestion"></i>' +
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
        '   <div id="parent" style="width: 100%; text-align:center;"><div>' +
        '   <i class="fa fa-plus-circle add-option" aria-hidden="true" onclick="addOption(this)" id="addOption-' + questionCount + '-' + optionsCount + '"></i>' +
        '   </div></div>' +
        '</div>');

    window.scrollTo(0, document.body.scrollHeight);
}
