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

    jsRoutes.controllers.SessionsController.searchSession().ajax(
        {
            type: 'POST',
            processData: false,
            contentType: false,
            data: formData,
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
                            usersFound += "<td><div><span>Completed</span></div><td></tr>";
                        }
                        else {
                            if(sessions[session].feedbackFormScheduled){

                                usersFound += "<td><div><span>Scheduled</span><br/>"+
                                              "<a href='/session/"+sessions[session].id+"/cancel'>"+
                                              "Cancel</a>"+
                                              "</div><td></tr>";
                            }
                            else{
                                usersFound += "<td><div><span>Pending</span><br/>"+
                                    "<a href='/session/"+sessions[session].id+"/schedule'>"+
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
            },

            error: function (er) {
                alert("failure");
                if (er.status == 400) {
                    $('#found-user-details').html("");
                }
                else {
                    $('#found-user-details').html("");
                }

            }
        });
}


