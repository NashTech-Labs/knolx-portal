$(function () {
    $('#disableDays').click(function () {
        disableDays();
    });

    $('#radio-days').click(function () {
        enableDays();
    });

    $('#upload-video-button').click(function () {
        console.log("Inside custom click function");
        var filePath = $("#browse-file").val();
        console.log(filePath);
        jsRoutes.controllers.SessionsController.uploadVideo(filePath).ajax(
             {
                 type: 'GET',
                 processData: false,
                 success: function (data) {
                     var responses = JSON.parse(data);
                     loadFeedbackForm(feedbackForm, sessionId);
                     fillFeedbackResponses(responses);
                 },
                 error: function (er) {
                     loadFeedbackForm(feedbackForm, sessionId);
                 }
             });
    });
});


function enableDays() {
    $('#days').prop('disabled', false);
}

function disableDays() {
    $('#days').prop('disabled', true);
}

$("#days").bind('keyup mouseup', function () {
    var days = $("#days").val();

    document.getElementById('radio-days').value = days;
});
