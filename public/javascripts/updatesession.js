var cancel = false;
var uploading = false;

$(function () {
    var sessionId = $('input[name^="sessionId"]').val();
    console.log("sessionId = " + sessionId);
    $("#upload-success-message").hide();

    jsRoutes.controllers.YoutubeController.checkIfUploading(sessionId).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function (data) {
            console.log("Coming here");
                $("#upload-success-message").hide();
                $("#already-upload").hide();
                $("#no-upload-cancel").hide();
                $("#cancel-message").hide();
                $("#show-progress").show();
                uploading = true;
                showProgress(sessionId);
            }
        });

    $("#uploadVideo").click( function () {
        if(!uploading) {
            uploading = true;
            cancel = false;
            console.log("Calling uploadVideo");
            $("#upload-success-message").hide();
            $("#already-upload").hide();
            $("#no-upload-cancel").hide();
            uploadVideo(sessionId);
        } else {
            uploading = false;
            $("#already-upload").show();
        }
    });

    $("#cancelVideo").click( function () {
        if(uploading) {
            cancel = true;
            uploading = false;
            $("#upload-success-message").hide();
            cancelVideo(sessionId);
        } else {
            $("#upload-success-message").hide();
            $("#cancel-message").hide();
            $("#no-upload-cancel").show();
        }
    });
});

function uploadVideo(sessionId) {
    console.log("Inside uploadVideo function")
    var formData = new FormData();
    var fileSize = 0;
    jQuery.each($('input[name^="file"]')[0].files, function(i, file) {
        fileSize = file.size;
        console.log("File size = " + fileSize);
        formData.append("file", file);
    });
    console.log("Calling ajax now")

    jsRoutes.controllers.YoutubeController.upload(sessionId, fileSize).ajax(
        {
            type: 'POST',
            processData: false,
            contentType: false,
            data: formData,
            success: function(data) {
                console.log("data in uploadVideo function  = " + data);
                $("#cancel-message").hide();
                $("#show-progress").show();
                showProgress(sessionId);
            },
            error: function(er) {
                $("#cancel-message").hide();
                $("#upload-failure-message").show();
            }
        });
}

function showProgress(sessionId) {
    jsRoutes.controllers.YoutubeController.getUploader(sessionId).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function(data) {
                if(!cancel) {
                    if(data == "Upload Completed!") {
                        $("#upload-success-message").show();
                        $("#show-progress").hide();
                        $("#progress").width('0%');
                        $("#progress").text('0%');
                        uploading = false;
                        fillYoutubeEmbedURL(sessionId);
                    } else {
                    var percentageUploaded = data*100;
                    console.log("percentageUploaded = " + percentageUploaded);
                    $("#progress").width(percentageUploaded + '%');
                    $("#progress").text(Math.ceil(percentageUploaded) * 1  + '%');
                    showProgress(sessionId);
                    }
                }
            },
            error: function(er) {
                console.log("showProgress failed");
            }
        });
}

function cancelVideo(sessionId) {

    jsRoutes.controllers.YoutubeController.cancel(sessionId).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function(data) {
                $("#upload-success-message").hide();
                $("#upload-failure-message").hide();
                $("#show-progress").hide();
                $("#progress").width('0%');
                $("#progress").text('0%');
                $("#cancel-message").show();
            },
            error: function(er) {
                $("#upload-success-message").hide();
                $("#upload-failure-message").hide();
                $("#show-progress").hide();
                $("#progress").width('0%');
                $("#progress").text('0%');
                $("#cancel-message").show();
            }
        })
}

function fillYoutubeEmbedURL(sessionId) {
    console.log("Coming inside the function of fillYoutubeEmbedURL");
    jsRoutes.controllers.YoutubeController.getVideoId(sessionId).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function(data) {
                console.log("Setting embedded URL for youtube")
                $("#youtubeURL").val("www.youtube.com/embed/" + JSON.parse(data));
            },
            error: function(er) {
                console.log("An error was encountered = " + er);
            }
        });
}