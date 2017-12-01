$(function () {

    slide("", 1, 10);

    $('#search-text').keyup(function () {
        var pageSize = $('#show-entries').val();
        slide(this.value, 1, pageSize);
    });

    $('#show-entries').on('change', function () {
        var keyword = $('#search-text').val();
        slide(keyword, 1, this.value);
    });
});

function slide(keyword, pageNumber, pageSize) {
    var email = keyword;

    var formData = new FormData();
    formData.append("email", email);
    formData.append("page", pageNumber);
    formData.append("pageSize", pageSize);

    jsRoutes.controllers.SessionsController.searchManageSession().ajax(
        {
            type: 'POST',
            processData: false,
            contentType: false,
            data: formData,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                var sessionInfo = JSON.parse(data);
                var sessions = sessionInfo["sessions"];
                var page = sessionInfo["page"];
                var pages = sessionInfo["pages"];
                var usersFound = "";
                if (sessions.length > 0) {
                    for (var session = 0; session < sessions.length; session++) {
                        usersFound += "<tr><td align='center'>" +
                            "<a href='" + jsRoutes.controllers.SessionsController.update(sessions[session].id)['url'] + "' class='btn btn-default'>" +
                            "<em class='fa fa-pencil'></em>" +
                            "</a> " +
                            "<a href='" + jsRoutes.controllers.SessionsController.deleteSession(sessions[session].id, sessionInfo['page'])['url'] + "' class='btn btn-danger delete'>" +
                            "<em class='fa fa-trash'></em>" +
                            "</a>" +
                            "</td>" +
                            "<td>" + sessions[session].dateString + "</td>" +
                            "<td>" + sessions[session].session + "</td>" +
                            "<td>" + sessions[session].topic + "</td>" +
                            "<td>" + sessions[session].email + "</td>";

                        if (sessions[session].meetup) {
                            usersFound += '<td><span class="label label-info meetup-session ">Meetup</span></td>';
                        } else {
                            usersFound += '<td><span class="label label-info knolx-session ">Knolx</span></td>';
                        }

                        if (sessions[session].cancelled) {
                            usersFound += "<td class='suspended'>Yes</td>";
                        } else {
                            usersFound += "<td class='active-status'>No</td>";
                        }

                        if (sessions[session].rating == "" || !sessions[session].expired) {
                            usersFound += "<td>N/A</td>";
                        } else {
                            usersFound += "<td>" + sessions[session].rating + "</td>";
                        }

                        if (sessions[session].completed && !sessions[session].cancelled) {
                            usersFound += "<td><div><span class='label label-success' >Completed</span></div></td>";
                        } else if(sessions[session].cancelled) {
                            usersFound += "<td title='The session has been cancelled'><span class='label label-warning cancelled-session'>Cancelled</span>";
                        } else {
                            if (sessions[session].feedbackFormScheduled) {
                                usersFound += "<td><div><span class='label label-success' >Scheduled</span><br/>" +
                                    "<a href='/session/" + sessions[session].id + "/cancel' class='cancel-red'>" +
                                    "Cancel</a>" +
                                    "</div></td>";
                            } else {
                                usersFound += "<td><div><span class='label label-warning' >Pending</span><br/>" +
                                    "<a href='/session/" + sessions[session].id + "/schedule' class='Schedule-green'>" +
                                    "Schedule</a>" +
                                    "</div></td>";
                            }
                        }

                        if (sessions[session].completed && !sessions[session].cancelled) {
                            usersFound += "<td  title='Click here for slides & videos' class='clickable-row'>" +
                                          "<a href='" + jsRoutes.controllers.SessionsController.shareContent(sessions[session].id)['url'] +
                                          "' style='text-decoration: none;'><span class='label more-detail-session'>Click here</span></a>";
                        } else if(sessions[session].cancelled) {
                            usersFound += "<td title='The session has been cancelled'><span class='label label-warning cancelled-session'>Cancelled</span>";
                        }
                        else if(!sessions[session].completed) {
                            usersFound += "<td title='Wait for session to be completed'><span class='label label-warning'>Pending</span>";
                        }
                            usersFound += "</td></tr>"
                    }

                    $('#user-found').html(usersFound);

                    var totalSessions = sessionInfo["totalSessions"];
                    var startingRange = (pageSize* (page-1)) + 1;
                    var endRange = (pageSize*(page-1))+ sessions.length;

                    $('#starting-range').html(startingRange);
                    $('#ending-range').html(endRange);
                    $('#total-range').html(totalSessions);

                    paginate(page, pages);

                    var paginationLinks = document.querySelectorAll('.paginate');

                    for (var i = 0; i < paginationLinks.length; i++) {
                        paginationLinks[i].addEventListener('click', function (event) {
                            var keyword = document.getElementById('search-text').value;
                            slide(keyword, this.id, pageSize);
                        });
                    }
                } else {
                    $('#user-found').html(
                        "<tr><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'><i class='fa fa-database' aria-hidden='true'></i><span class='no-record-found'>Oops! No Record Found</span></td><td align='center'></td><td align='center'></td><td align='center'></td></tr>"
                    );

                    $('.pagination').html("");
                }
            },
            error: function (er) {
                $('#user-found').html(
                    "<tr><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'>" + er.responseText + "</td><td align='center'></td><td align='center'></td><td align='center'></td></tr>"
                );
                $('.pagination').html("");
            }
        });
}
