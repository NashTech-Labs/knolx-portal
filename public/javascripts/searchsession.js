$(function () {
    $('#search-text').keyup(function () {
        slide(this.value, 1);
    });
});

function slide(keyword, pageNumber) {
    var email = keyword;

    var formData = new FormData();
    formData.append("email", email);
    formData.append("page", pageNumber);

    jsRoutes.controllers.SessionsController.searchSessions().ajax(
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
                        usersFound += "<tr>" +
                            "<td>" + sessions[session].dateString + "</td>" +
                            "<td>" + sessions[session].session + "</td>" +
                            "<td>" + sessions[session].topic + "</td>" +
                            "<td>" + sessions[session].email + "</td>";

                        if (sessions[session].meetup) {
                            usersFound +=  '<td><span class="label label-success ">Meetup</span></td>';
                        } else {
                            usersFound +=  '<td><span class="label label-warning ">Knolx</span></td>';
                        }

                        if (sessions[session].cancelled) {
                            usersFound += "<td class='active-status'>Yes</td>";
                        } else {
                            usersFound += "<td class='suspended'>No</td>";
                        }

                        if (sessions[session].rating == "") {
                            usersFound += "<td>N/A</td></tr>";
                        } else {
                            usersFound += "<td>" + sessions[session].rating + "</td></tr>";
                        }
                    }

                    $('#user-found').html(usersFound);

                    paginate(page, pages);

                    var paginationLinks = document.querySelectorAll('.paginate');

                    for (var i = 0; i < paginationLinks.length; i++) {
                        paginationLinks[i].addEventListener('click', function (event) {
                            var keyword = document.getElementById('search-text').value;
                            slide(keyword, this.id);
                        });
                    }
                } else {
                    $('#user-found').html(
                        "<tr><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-6'>Oops! No Record Found</td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td></tr>"
                    );
                    $('.pagination').html("");
                }
            },
            error: function (er) {
                $('#user-found').html(
                    "<tr><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-6'>" + er.responseText + "</td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td></tr>"
                );
                $('.pagination').html("");
            }
        });
}
