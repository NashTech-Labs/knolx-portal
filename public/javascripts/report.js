$('.custom-checkbox').click(function () {
        var filter = $('input[name="feedback-response-report"]:checked').val();
        var isCoreMember = filter.split('-')[0];
        var isSuperUser = filter.split('-')[1];
        var id = this.id;
        var sessionId = id.split('-');
        fetchUserResponse(isCoreMember,isSuperUser,sessionId[1]);
    });

function fetchUserResponse(isCoreMember,isSuperUser,sessionId) {

       jsRoutes.controllers.FeedbackFormsReportController.searchAllResponsesBySessionId(sessionId).ajax(
        {
            type: "GET",
            processData: false,
            /*contentType: 'application/json',*/
            success: function (data) {
            var values = JSON.parse(data);
            var userResponse = ""
            var responses = values["response"];

            if (isCoreMember == "all") {
                for(var response = 0;response < responses.length; response++) {
                   userResponse += "<tr><td>"+ (parseInt(response) + 1) + "</td>";
                   if (isSuperUser) {
                     userResponse += "<td>"+ responses[response].email +"</td>";
                     }
                       for(var question = 0; question < responses[response].questionResponse.length; question++) {
                              userResponse += "<td>" + responses[response].questionResponse[question].response
                                       + "</td></tr>";
                       }
                }
             $('#response-size').html(responses.length)
            }
            else {
                var sno = 0 ;
                for(var response = 0;response < responses.length; response++) {
                    if (responses[response].coreMember){
                            sno += (parseInt(sno) + 1)
                         userResponse += "<tr><td>"+ parseInt(sno) + "</td>";
                        if (isSuperUser) {
                          userResponse += "<td>"+ responses[response].email +"</td>";
                       }
                             for(var question = 0; question < responses[response].questionResponse.length; question++) {
                                        userResponse += "<td>" + responses[response].questionResponse[question].response
                                                 + "</td></tr>";
                             }
                    }
                }
             $('#response-size').html(parseInt(sno))
            }
            $('#user-response').html(userResponse);
          },error: function (er) {

                           }
        })
}
