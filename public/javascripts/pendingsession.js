$(function getAllPendingSession() {

    jsRoutes.controllers.CalendarController.getAllSessionForAdmin().ajax(

        {
            type: 'POST',
            processData: false,
            contentType: false,
            /*data: JSON.stringify(formData),*/
            success: function(sessionInfo) {

                var sessions = "";
                if(sessionInfo.length > 0) {
                    for (var session = 0; session < sessionInfo.length; session++) {
                        sessions +="<tr><td align='center'>" + sessionInfo[session].dateString + "</td>" +
                                   "<td align='center'>" + sessionInfo[session].topic + "</td>" +
                                   "<td align='center'>" + sessionInfo[session].email + "</td>";

                        if (sessionInfo[session].meetup) {
                            sessions += '<td align="center"><span class="label label-info meetup-session ">Meetup</span></td>';
                        } else {
                            sessions += '<td align="center"><span class="label label-info knolx-session ">Knolx</span></td>';
                        }

                        if (sessionInfo[session].approved) {
                            sessions += "<td align='center' class='suspended'>Yes</td>";
                        } else {
                            sessions += "<td align='center' class='active-status'>No</td>";
                        }

                        if (sessionInfo[session].decline) {
                            sessions += "<td align='center' class='suspended'>Yes</td>";
                        } else {
                            sessions += "<td align='center' class='active-status'>No</td>";
                        }
                        sessions += "</tr>"
                    }

                    $('#pending-sessions').html(sessions)
                } else {
                    $('#pending-sessions').html(
                        "<tr><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'><i class='fa fa-database' aria-hidden='true'></i><span class='no-record-found'>Oops! No Record Found</span></td><td align='center'></td><td align='center'></td></tr>"
                    );

                    $('.pagination').html("");

                }
            },
            error: function (er) {
                $('#pending-sessions').html(
                    "<tr><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'>" + er.responseText + "</td><td align='center'></td><td align='center'></td></tr>"
                );

                $('.pagination').html("");
            }
        })

});

