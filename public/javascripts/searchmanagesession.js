$(function () {

    slide("", 1, "completed", 10);

    $('#search-text').keyup(function () {
        var filter = $('input[name="session-filter"]:checked').val();
        var pageSize = $('#show-entries').val();
        slide(this.value, 1, filter, pageSize);
    });

    $('.custom-checkbox').click(function () {
        var filter = $('input[name="session-filter"]:checked').val();
        var pageSize = $('#show-entries').val();
        slide($('#search-text').val(), 1, filter, pageSize);
    });

    $('#search-text-mobile').keyup(function () {
        var filter = $('input[name="session-filter"]:checked').val();
        var pageSize = $('#show-entries-mobile').val();
        slide(this.value, 1, filter, pageSize);
    });

    $('#show-entries').on('change', function () {
        var filter = $('input[name="session-filter"]:checked').val();
        var keyword = $('#search-text').val();
        slide(keyword, 1, filter, this.value);
    });

    document.getElementById("default-check").checked = true;
});

var mobileSessionsFound = "";

function createNewButtons(){
 mobileSessionsFound = "";
 mobileSessionsFound = "<tr class='new-button-tr'><td class='col-xs-12 table-buttons new-button-td' colspan='2'><div class='col-xs-12 text-right new-button'>" +
        "<div class='col-xs-6 remove-padding-left'><a href='" + jsRoutes.controllers.SessionsController.create()['url'] + "' class='btn btn-sm btn-primary btn-create float-left'>" +
        "<i class='fa fa-plus' aria-hidden='true'></i>" +
        " New" +
        "</a></div>" +
        "<div class='col-xs-6' style='white-space: nowrap; float:right;'><label class='customize-entries' style='font-weight: normal; display: inline-block;'>" +
        "Show" +
        "<select name ='Show' id='show-entries-mobile' class='search-text' style='background-color: #FFFFFF; padding-right: 2px !important; margin: 0px 4px'>";
    var pageSize = $('#show-entries-mobile').val();
        for(i = 10; i <= 50; i = i+10) {
        if(i == pageSize){
            mobileSessionsFound+="<option value ="+ i +" selected>" +i+"</option>"
        }else{
            mobileSessionsFound+="<option value ="+ i +">" +i+"</option>"
        }
    }
    mobileSessionsFound+="</select>" +
        "Entries" +
        "</label></div>"+
        "</div></td></tr>" +
        "<tr class='row-space'></tr>";

}

function slide(keyword, pageNumber, filter, pageSize) {
    var email = keyword;

    var formData = new FormData();
    formData.append("email", email);
    formData.append("page", pageNumber);
    formData.append("filter", filter);
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

                // function goes here
                createNewButtons();
                if (sessions.length > 0) {
                    for (var session = 0; session < sessions.length; session++) {
                        var rating = "";
                        mobileSessionsFound += "<tr class='session-topic'><td class='session-topic' colspan='2'>" + sessions[session].topic + "<sup class='rating'></sup></td></tr>" +
                            "<tr class='session-info'><td>" +
                            "<p>" + sessions[session].email + "</p>" +
                            "<p>" + sessions[session].dateString + "</p>" +
                            "</td>" + "<td>";

                        usersFound += "<tr><td align='center' class='manage-session-btn'>" +
                            "<a href='" + jsRoutes.controllers.SessionsController.update(sessions[session].id)['url'] + "' class='btn btn-default manage-btn' " +
                            "style='margin-right: 5px;' data-toggle='tooltip' data-placement='top' title='Edit' >" +
                            "<em class='fa fa-pencil'></em>" +
                            "</a> " +
                            "<a href='" + jsRoutes.controllers.SessionsController.deleteSession(sessions[session].id, sessionInfo['page'])['url'] + "' class='btn btn-danger delete manage-btn'" +
                            "style='margin-right: 5px;' data-toggle='tooltip' data-placement='top' title='Delete'>" +
                            "<em class='fa fa-trash'></em>" +
                            "</a> " +
                            "<a href='" + jsRoutes.controllers.SessionsController.sendEmailToPresenter(sessions[session].id)['url'] + "' class='btn btn-info manage-btn' " +
                            "data-toggle='tooltip' data-placement='top' title='Send Instructions Email'>" +
                            "<em class='fa fa-envelope-o'></em>" +
                            "</a>" +
                            "</td>" +
                            "<td>" + sessions[session].dateString + "</td>" +
                            "<td>" + sessions[session].session + "</td>" +
                            "<td>" + sessions[session].topic + "</td>" +
                            "<td>" + sessions[session].email + "</td>";

                        if (sessions[session].meetup) {
                            usersFound += '<td><span class="label label-info meetup-session ">Meetup</span></td>';
                            mobileSessionsFound += '<span class="label label-info meetup-session ">Meetup</span>';
                        } else {
                            usersFound += '<td><span class="label label-info knolx-session ">Knolx</span></td>';
                            mobileSessionsFound += '<span class="label label-info knolx-session ">Knolx</span>';
                        }

                        if (sessions[session].cancelled) {
                            usersFound += "<td class='suspended'>Yes</td>";
                        } else {
                            usersFound += "<td class='active-status'>No</td>";
                        }

                        if (sessions[session].rating === "" || !sessions[session].expired) {
                            usersFound += "<td>N/A</td>";
                            rating = "N/A";
                        } else {
                            usersFound += "<td>" + sessions[session].rating + "</td>";
                            rating = sessions[session].rating;
                        }

                        if (sessions[session].completed && !sessions[session].cancelled) {
                            usersFound += "<td><div><span class='label label-success' >Completed</span></div></td>";
                            mobileSessionsFound += "<div><span class='label label-success' >Completed</span></div>";
                        } else if (sessions[session].cancelled) {
                            usersFound += "<td title='The session has been cancelled'><span class='label label-warning cancelled-session'>Cancelled</span>";
                            mobileSessionsFound += "<div><span class='label label-warning cancelled-session'>Cancelled</span></div>";
                        } else {
                            if (sessions[session].feedbackFormScheduled) {
                                usersFound += "<td><div><span class='label label-success' >Scheduled</span><br/>" +
                                    "<a href='/session/" + sessions[session].id + "/cancel' class='cancel-red'>" +
                                    "Cancel</a>" +
                                    "</div></td>";

                                mobileSessionsFound += "<div><span class='label label-success' >Scheduled</span><br/>" +
                                    "<a href='/session/" + sessions[session].id + "/cancel' class='cancel-red'>" +
                                    "Cancel</a>" +
                                    "</div>";
                            } else {
                                usersFound += "<td><div><span class='label label-warning' >Pending</span><br/>" +
                                    "<a href='/session/" + sessions[session].id + "/schedule' class='Schedule-green'>" +
                                    "Schedule</a>" +
                                    "</div></td>";

                                mobileSessionsFound += "<div><span class='label label-warning' >Pending</span><br/>" +
                                    "<a href='/session/" + sessions[session].id + "/schedule' class='Schedule-green'>" +
                                    "Schedule</a>" +
                                    "</div>";
                            }
                        }

                        if (sessions[session].completed && !sessions[session].cancelled) {
                            if (sessions[session].contentAvailable) {
                                usersFound += "<td  title='Click here for slides & videos' class='clickable-row'>" +
                                    "<a href='" + jsRoutes.controllers.SessionsController.shareContent(sessions[session].id)['url'] +
                                    "' style='text-decoration: none;' target='_blank'><span class='label more-detail-session'>Click here</span></a>";

                                mobileSessionsFound += "<a href='" + jsRoutes.controllers.SessionsController.shareContent(sessions[session].id)['url'] +
                                    "' style='text-decoration: none;' target='_blank'><span class='label more-detail-session'>Click here</span></a>";
                            } else {
                                usersFound += "<td><span class='label label-danger'>Not Available</span>";
                            }
                        } else if (sessions[session].cancelled) {
                            usersFound += "<td title='The session has been cancelled'><span class='label label-warning cancelled-session'>Cancelled</span>";
                        }
                        else if (!sessions[session].completed) {
                            usersFound += "<td title='Wait for session to be completed'><span class='label label-warning'>Pending</span>";
                        }
                        usersFound += "</td></tr>";

                        mobileSessionsFound += "<tr><td colspan='2' class='table-buttons'>" +
                        "<a href='" + jsRoutes.controllers.SessionsController.update(sessions[session].id)['url'] + "' class='btn btn-default manage-btn' " +
                        "style='margin-right: 5px;' data-toggle='tooltip' data-placement='top' title='Edit' >" +
                        "<em class='fa fa-pencil'></em>" +
                        "</a> " +
                        "<a href='" + jsRoutes.controllers.SessionsController.deleteSession(sessions[session].id, sessionInfo['page'])['url'] + "' class='btn btn-danger delete manage-btn'" +
                        "style='margin-right: 5px;' data-toggle='tooltip' data-placement='top' title='Delete'>" +
                        "<em class='fa fa-trash'></em>" +
                        "</a> " +
                        "<a href='" + jsRoutes.controllers.SessionsController.sendEmailToPresenter(sessions[session].id)['url'] + "' class='btn btn-info manage-btn' " +
                        "data-toggle='tooltip' data-placement='top' title='Send Instructions Email'>" +
                        "<em class='fa fa-envelope-o'></em>" +
                        "</a>" +
                        "</td></tr>";

                        mobileSessionsFound += "</td><tr class='row-space'></tr>";
                    }

                    $('#user-found').html(usersFound);
                    $('#manage-session-tbody-mobile').html(mobileSessionsFound);
                    $(".rating").text(rating);

                    $('#show-entries-mobile').on('change', function () {
                        var filter = $('input[name="session-filter"]:checked').val();
                        var keyword = $('#search-text-mobile').val();
                        slide(keyword, 1, filter, this.value);
                    });

                    var totalSessions = sessionInfo["totalSessions"];
                    var startingRange = (pageSize * (page - 1)) + 1;
                    var endRange = (pageSize * (page - 1)) + sessions.length;

                    $('#starting-range').html(startingRange);
                    $('#ending-range').html(endRange);
                    $('#total-range').html(totalSessions);

                    paginate(page, pages);

                    var paginationLinks = document.querySelectorAll('.paginate');

                    for (var i = 0; i < paginationLinks.length; i++) {
                        paginationLinks[i].addEventListener('click', function (event) {
                            var filter = $('input[name="session-filter"]:checked').val();
                            var keyword = document.getElementById('search-text').value;
                            slide(keyword, this.id, filter, pageSize);
                        });
                    }
                } else {
                    $('#user-found').html(
                        "<tr><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'><i class='fa fa-database' aria-hidden='true'></i><span class='no-record-found'>Oops! No Record Found</span></td><td align='center'></td><td align='center'></td><td align='center'></td></tr>"
                    );

                    $('#starting-range').html('0');
                    $('#ending-range').html('0');
                    $('#total-range').html('0');
                    $('.pagination').html("");

                    mobileSessionsFound+="<tr class='no-record-mobile'><td align='center' class='col-md-12' colspan='2'><i class='fa fa-database' aria-hidden='true'></i><span class='no-record-found'>Oops! No Record Found</span></td></tr>";

                    $('#manage-session-tbody-mobile').html(mobileSessionsFound);
                }
            },
            error: function (er) {
                $('#user-found').html(
                    "<tr><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'></td><td align='center'>" + er.responseText + "</td><td align='center'></td><td align='center'></td><td align='center'></td></tr>"
                );
                $('#manage-session-tbody-mobile').html(
                    "<tr><td align='center'>" + er.responseText + "</td></tr>"
                );
                $('#manage-session-tbody-mobile').html(mobileSessionsFound);
                $('.pagination').html("");
            }
        });
}
