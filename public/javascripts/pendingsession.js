function getAllPendingSession() {

    jsRoutes.controllers.CalendarController.getPendingSessions().ajax(

        {
            type: 'POST',
            processData: false,
            contentType: false,
            /*data: JSON.stringify(formData),*/
            success: function(data) {
                var sessionInfo = JSON.parse(data);

                var sessions = "";

                for (var session = 0; session < sessions.length; session++) {
                    sessions +="<td>" + sessions[session].dateString + "</td>" +
                               "<td>" + sessions[session].topic + "</td>" +
                               "<td>" + sessions[session].email + "</td>";

                    if (sessions[session].meetup) {
                        sessions += '<td><span class="label label-info meetup-session ">Meetup</span></td>';
                    } else {
                        sessions += '<td><span class="label label-info knolx-session ">Knolx</span></td>';
                    }

                    if (sessions[session].approved) {
                        sessions += "<td class='suspended'>Yes</td>";
                    } else {
                        sessions += "<td class='active-status'>No</td>";
                    }

                    if (sessions[session].declined) {
                        sessions += "<td class='suspended'>Yes</td>";
                    } else {
                        sessions += "<td class='active-status'>No</td>";
                    }
                }

                $('#pending-sessions').html(sessions)

                }
            }
        }
    )
}