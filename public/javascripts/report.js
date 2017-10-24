$('.custom-checkbox').click(function () {
    var filter = $('input[name="feedback-response-report"]:checked').val();
    var isCoreMember = filter.split('-')[0];
    var isSuperUser = filter.split('-')[1];
    var id = this.id;
    var sessionId = id.split('-');
    fetchUserResponse(isCoreMember, isSuperUser, sessionId[1]);
});

function fetchUserResponse(isCoreMember, isSuperUser, sessionId) {

    jsRoutes.controllers.FeedbackFormsReportController.searchAllResponsesBySessionId(sessionId).ajax(
        {
            type: "GET",
            processData: false,
            success: function (data) {
                var values = JSON.parse(data);
                var userResponse = "";
                var responses = values["response"];

                if (isCoreMember == "all") {
                    for (var response = 0; response < responses.length; response++) {
                        userResponse += "<tr><td>" + (parseInt(response) + 1) + "</td>";
                        if (isSuperUser) {
                            userResponse += "<td>" + responses[response].email + "</td>";
                        }
                        for (var question = 0; question < responses[response].questionResponse.length; question++) {
                            userResponse += "<td>" + responses[response].questionResponse[question].response
                                + "</td>";
                        }
                        userResponse += "</tr>";
                    }
                    $('#response-size').html(responses.length)
                } else {
                    var sno = 0;
                    for (var response = 0; response < responses.length; response++) {
                        if (responses[response].coreMember) {
                            sno += (parseInt(sno) + 1);
                            userResponse += "<tr><td>" + parseInt(sno) + "</td>";
                            if (isSuperUser) {
                                userResponse += "<td>" + responses[response].email + "</td>";
                            }
                            for (var question = 0; question < responses[response].questionResponse.length; question++) {
                                userResponse += "<td>" + responses[response].questionResponse[question].response
                                    + "</td>";
                            }
                            userResponse += "</tr>";
                        }
                    }
                    if (sno == '0') {
                    console.log(sno);
                    userResponse += "<tr><td align='center' colspan='100%'><i class='fa fa-database' aria-hidden='true'>"
                                  +"</i><span class='no-record-found'>Oops! No Response Found</span></td></tr>";
                    }
                }
                $('#user-response').html(userResponse);
            }, error: function (er) {

        }
        })
}
