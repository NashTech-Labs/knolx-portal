class Question {
    constructor(question, options) {
        this.question = question;
        this.options = options;
    }
}

var optionsCount = 0;
var questionCount = 0;
var questions = new Map([]);
questions.set(0, 1);

function createForm() {
    var questionsValues = [];

    questions.forEach(function (options, question, obj) {
        var questionValue = document.getElementById('questionValue-' + question).value;
        var optionValues = [];

        for(i = 0; i < options; i++) {
            var optionValue = document.getElementById('optionValue-' + question + '-' + i).value;

            optionValues.push(optionValue)
        }

        questionsValues.push(new Question(questionValue, optionValues))
    });

    console.log(JSON.stringify(questionsValues));

    $('#errorMessage').remove();
    $('#successMessage').remove();

    jsRoutes.controllers.FeedbackFormsController.createFeedbackForm().ajax(
        {
            type: "POST",
            processData: false,
            contentType: 'application/json',
            data: JSON.stringify(questionsValues),
            success: function(data){
                $('#response').append('<span id="successMessage">' + data +'</span>')
            },
            error: function(er) {
                $('#response').append('<span id="errorMessage">' + er.responseText +'</span>')
            }
        })
}

function deleteOption(deleteElem) {
    var splitIds = deleteElem.id.split("-");
    var questionCountId = parseInt(splitIds[1]);
    var optionCountId = parseInt(splitIds[2]);

    var currentQuestionOptions = questions.get(questionCountId);

    questions.set(questionCountId, currentQuestionOptions - 1);
    var questionOptionsAfterDelete = questions.get(questionCountId);

    $('#option-' + questionCountId + '-' + optionCountId).remove();

    if(questionOptionsAfterDelete == 1) {
        $('#options-' + questionCountId).append('<a id="addOption-' + questionCountId + '-0" onclick="addOption(this)">Add option</a>')
    }
}

function addOption(addElem) {
    var splitIds = addElem.id.split("-");

    var questionCountId = parseInt(splitIds[1]);
    var optionCountId = parseInt(splitIds[2]) + 1;

    var currentQuestionOptions = questions.get(questionCountId);
    questions.set(questionCountId, currentQuestionOptions + 1);

    $('#options-' + questionCountId).append(
        '<div id="option-' + questionCountId + '-' + optionCountId + '">' +
        '   <input type="radio" disabled>' +
        '   <input id="optionValue-' + questionCountId + '-' + optionCountId + '" placeholder="Option" type="text">' +
        '   <a id="deleteOption-' + questionCountId + '-' + optionCountId + '" onclick="deleteOption(this)">X</a>' +
        '   <br>' +
        '   <a id="addOption-' + questionCountId + '-' + optionCountId + '" onclick="addOption(this)">Add option</a>' +
        '</div>'
    );

    $('#addOption-' + questionCountId + '-' + (optionCountId - 1)).remove();
}

function deleteQuestion(questionElem) {
    var splitIds = questionElem.id.split("-");

    var questionCountId = parseInt(splitIds[1]);

    questions.delete(questionCountId);

    $('#question-' + questionCountId).remove()
}

function addQuestion() {
    questionCount = questionCount + 1;
    questions.set(questionCount, 1);

    $('#questions').append(
        '<div id="question-' + questionCount + '">' +
        '   <input id="questionValue-' + questionCount + '" placeholder="Question" type="text">' +
        '   <a id="deleteQuestion-' + questionCount + '" onclick="deleteQuestion(this)">X</a>' +
        '   <br>' +
        '   <div id="options-' + questionCount + '">' +
        '       <div id="option-' + questionCount + '-' + optionsCount + '">' +
        '           <input type="radio" disabled>' +
        '           <input id="optionValue-' + questionCount + '-' + 0 + '" placeholder="Option" type="text">' +
        '       </div>' +
        '   </div>' +
        '   <br>' +
        '   <a id="addOption-' + questionCount + '-' + optionsCount + '" onclick="addOption(this)">Add option</a>' +
        '</div>')
}
