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

    jsRoutes.controllers.CalendarController.allSessionForAdmin().ajax(
        {
            type: 'POST',
            processData: false,
            contentType: false,
            data: formData,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (calendarSessionInfo) {

                var sessions = "";
                var calendarSessions = calendarSessionInfo["calendarSessions"];
                var page = calendarSessionInfo["page"];
                var pages = calendarSessionInfo["pages"];
                var mobilePendingSessionsFound = "";
                if (calendarSessions.length > 0) {
                    for (var session = 0; session < calendarSessions.length; session++) {

                        mobilePendingSessionsFound += "<tr class='session-topic'><td class='session-topic' colspan='2'>" +calendarSessions[session].topic+"</td></tr>"+
                            "<tr class='session-info'><td>" +
                            "<p>" + calendarSessions[session].email + "</p>" +
                            "<p>" + calendarSessions[session].dateString + "</p>" +
                            "</td>" + "<td>";

                        sessions += "<tr><td align='center'>" + calendarSessions[session].dateString + "</td>" +
                            "<td align='center'>" + calendarSessions[session].topic + "</td>" +
                            "<td align='center'>" + calendarSessions[session].email + "</td>";

                        if (calendarSessions[session].meetup) {
                            sessions += '<td align="center"><span class="label label-info meetup-session ">Meetup</span></td>';
                            mobilePendingSessionsFound += '<span class="label label-info meetup-session ">Meetup</span>';
                        } else {
                            sessions += '<td align="center"><span class="label label-info knolx-session ">Knolx</span></td>';
                            mobilePendingSessionsFound += '<span class="label label-info knolx-session ">Knolx</span>';
                        }

                        if (calendarSessions[session].approved) {
                            sessions += "<td align='center' class='active-status'>Yes</td>";
                        } else {
                            sessions += "<td align='center' class='suspended'>No</td>";
                        }

                        if (calendarSessions[session].decline) {
                            sessions += "<td align='center' class='active-status'>Yes</td>";
                        } else {
                            sessions += "<td align='center' class='suspended'>No</td>";
                        }
                        sessions += "</tr>"

                        mobilePendingSessionsFound += "</td><tr class='row-space'></tr>";
                    }

                    $('#pending-sessions').html(sessions);
                    $('#manage-pending-session-mobile-table').html(mobilePendingSessionsFound);


                    var totalSessions = calendarSessionInfo["totalSessions"];
                    var startingRange = (pageSize * (page - 1)) + 1;
                    var endRange = (pageSize * (page - 1)) + calendarSessions.length;

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

}
