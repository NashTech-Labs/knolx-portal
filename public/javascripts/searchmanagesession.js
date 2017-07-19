$(function(){
    $('#search-text').keyup(function() {
        slide(this.value,1);
    });

});

function slide(keyword, pageNumber) {
    var email = keyword;

    var formData = new FormData();
    formData.append("email", email);
    formData.append("page", pageNumber);

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
                            "<a href='/session/update?id="+sessions[session].id+"' class='btn btn-default'>" +
                            "<em class='fa fa-pencil'></em>" +
                            "</a> " +
                            "<a href='/session/delete?id="+sessions[session].id+"&pageNumber="+sessionInfo['page']+"' class='btn btn-danger delete'>" +
                            "<em class='fa fa-trash'></em>" +
                            "</a>" +
                            "</td>" +
                            "<td>"+sessions[session].dateString+"</td>"+
                            "<td>"+sessions[session].session+"</td>"+
                            "<td>"+sessions[session].topic+"</td>"+
                            "<td>"+sessions[session].email+"</td>";

                        if (sessions[session].meetup) {
                            usersFound += "<td class='active-status'>Yes</td>";
                        }
                        else {
                            usersFound += "<td class='suspended'>No</td>";
                        }
                        if (sessions[session].cancelled) {
                            usersFound += "<td class='active-status'>Yes</td>";
                        }
                        else {
                            usersFound += "<td class='suspended'>No</td>";
                        }
                        if (sessions[session].rating == "") {
                            usersFound += "<td class='active-status'>N/A</td>";
                        }
                        else {
                            usersFound += "<td class='suspended'>"+sessions[session].rating+"</td>";
                        }

                        if (sessions[session].completed) {
                            usersFound += "<td><div><span class='label label-default' >Completed</span></div><td></tr>";
                        }
                        else {
                            if(sessions[session].feedbackFormScheduled){

                                usersFound += "<td><div><span class='label label-success' >Scheduled</span><br/>"+
                                              "<a href='/session/"+sessions[session].id+"/cancel' class='cancel-red'>"+
                                              "Cancel</a>"+
                                              "</div><td></tr>";
                            }
                            else{
                                usersFound += "<td><div><span class='label label-warning' >Pending</span><br/>"+
                                    "<a href='/session/"+sessions[session].id+"/schedule' class='Schedule-green'>"+
                                    "Schedule</a>"+
                                    "</div><td></tr>";
                            }
                        }
                    }
                    $('#user-found').html(
                        usersFound
                    );
                    paginate(page, pages);

                    var paginationLinks = document.querySelectorAll('.paginate');
                    for (var i = 0; i < paginationLinks.length; i++) {
                        paginationLinks[i].addEventListener('click', function (event) {
                            var keyword = document.getElementById('search-text').value;
                            slide(keyword, this.id);
                        });
                    }

                }
                else{
                    $('#user-found').html(
                        "<tr><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'>Oops! No Record Found</td><td align='center'></td><td align='center'></td><td align='center'></td></tr>"
                    );
                    $('.pagination').html("");
                }
            },

            error: function (er) {
                $('#user-found').html(
                    "<tr><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'>"+er.responseText+"</td><td align='center'></td><td align='center'></td><td align='center'></td></tr>"
                );
                $('.pagination').html("");
            }
        });
}


