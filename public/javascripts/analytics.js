$(function () {

    $('#demo').daterangepicker({
        "startDate": "10/24/2017 12:00 AM",
        "endDate": "10/30/2018 11:58 PM",
        "maxDate": "10/30/2018 11:59 PM"
    }, function(start, end, label) {
        var email = 'akshansh.jain@knoldus.in';

        var startDate = start.format('YYYY-MM-DD h:mm A');
        var endDate = end.format('YYYY-MM-DD h:mm A');
        console.log("New date range selected: " + startDate + ' to ' + endDate + ' (predefined range: ' + label);
        filterSession(email, startDate, endDate);
    });
});

function filterSession(email, startDate, endDate) {
    console.log("We are in filterSession function");
    var formData = new FormData();
    formData.append("email", email);
    formData.append("startDateString", startDate);
    formData.append("endDateString", endDate);
    console.log("email = " + email);
    console.log("email in formData = " + formData.get('email'));
    jsRoutes.controllers.SessionsController.filterInTimeRange().ajax({
        type: 'POST',
        processData: false,
        contentType: false,
        data: formData,
        beforeSend: function (request) {
            var csrfToken = document.getElementById('csrfToken').value;
            console.log("CSRF token = " + csrfToken);

            return request.setRequestHeader('CSRF-Token', csrfToken);
        },
        success: function (data) {
            var sessionFound = "";
            console.log("Data = " + data);
            var sessions = JSON.parse(data);
            console.log("Sessions = " + sessions);
            for(var session = 0; session < sessions.length; session++) {
                sessionFound += "<p>" + sessions[session].dateString   + "</p>" +
                                "<p>" + sessions[session].session + "</p>" +
                                "<p>" + sessions[session].topic + "</p>" +
                                "<p>" + sessions[session].email + "</p>";
            }
            $("#akshansh").html(sessionFound);
        },
        error: function (er) {
            $('#akshansh').html("No session found!");
        }
    });
}