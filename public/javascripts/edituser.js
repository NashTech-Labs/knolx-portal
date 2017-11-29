$(function () {
    $('#search-text').keyup(function () {
        var filter = $('input[name="user-filter"]:checked').val();
        var pageSize = $('#show-entries').val();
        slide(this.value, 1, filter, pageSize);
    });

    $('.custom-checkbox').click(function () {
        var filter = $('input[name="user-filter"]:checked').val();
        var pageSize = $('#show-entries').val();
        slide($('#search-text').val(), 1, filter, pageSize);
    });

    $('#show-entries').on('change', function () {
        var filter = $('input[name="user-filter"]:checked').val();
        var keyword = $('#search-text').val();
        slide(keyword, 1, filter, this.value);
    });
});

function slide(keyword, pageNumber, filter, pageSize) {
    var email = keyword;

    var formData = new FormData();
    formData.append("email", email);
    formData.append("page", pageNumber);
    formData.append("filter", filter);
    formData.append("pageSize", pageSize);

    jsRoutes.controllers.UsersController.searchUser().ajax(
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
                var userInfo = JSON.parse(data);
                var users = userInfo["users"];
                var page = userInfo["page"];
                var pages = userInfo["pages"];
                var superUser = userInfo["isSuperUser"];
                var usersFound = "";

                if (users.length > 0) {
                    for (var user = 0; user < users.length; user++) {
                        if (superUser) {
                        usersFound += "<tr><td align='center'>" +
                                      "<a href='" + jsRoutes.controllers.UsersController.getByEmail(users[user].email)['url'] + "' class='btn btn-default'>" +
                                      "<em class='fa fa-pencil'></em>" +
                                      "</a> ";
                            if (users[user].admin && users[user].superUser) {
                                usersFound += "<a href='" + jsRoutes.controllers.UsersController.deleteUser(users[user].email)['url'] + "' class='btn btn-danger delete disabled'>" +
                                              "<em class='fa fa-trash'></em>" +
                                              "</a>" +
                                              "</td>";
                            } else {
                                usersFound += "<a href='" + jsRoutes.controllers.UsersController.deleteUser(users[user].email)['url'] + "' class='btn btn-danger delete'>" +
                                    "<em class='fa fa-trash'></em>" +
                                    "</a>" +
                                    "</td>"
                            }
                        } else {
                        if (users[user].admin && !users[user].superUser) {
                            usersFound += "<td align='center'>" +
                                          "<a href='" + jsRoutes.controllers.UsersController.getByEmail(users[user].email)['url'] + "' class='btn btn-default'>" +
                                          "<em class='fa fa-pencil'></em>" +
                                          "</a> "+
                                          "<a href='" + jsRoutes.controllers.UsersController.deleteUser(users[user].email)['url'] + "' class='btn btn-danger delete disabled'>" +
                                          "<em class='fa fa-trash'></em>" +
                                          "</a>" +
                                          "</td>";
                        } else if (users[user].admin && users[user].superUser) {
                            usersFound += "<tr><td align='center'>" +
                                          "<a href='" + jsRoutes.controllers.UsersController.getByEmail(users[user].email)['url'] + "' class='btn btn-default disabled'>" +
                                          "<em class='fa fa-pencil'></em>" +
                                          "</a> "+
                                          "<a href='" + jsRoutes.controllers.UsersController.deleteUser(users[user].email)['url'] + "' class='btn btn-danger delete disabled'>" +
                                          "<em class='fa fa-trash'></em>" +
                                          "</a>" +
                                          "</td>";
                        } else {
                            usersFound += "<tr><td align='center'>" +
                                "<a href='" + jsRoutes.controllers.UsersController.getByEmail(users[user].email)['url'] + "' class='btn btn-default'>" +
                                "<em class='fa fa-pencil'></em>" +
                                "</a> " +
                                "<a href='" + jsRoutes.controllers.UsersController.deleteUser(users[user].email)['url'] + "' class='btn btn-danger delete'>" +
                                "<em class='fa fa-trash'></em>" +
                                "</a>" +
                                "</td>"
                         }
                        }
                        usersFound += "<td>" + users[user].email + "</td>";
                        if (users[user].active) {
                            usersFound += "<td class='active-status' style='white-space: nowrap;'><span class='label label-success'>Active</span></td>"
                        } else {
                            usersFound += "<td class='suspended' style='white-space: nowrap;'><span class='label label-danger'>Suspended</span></td>"
                        }
                        if (users[user].ban) {
                            usersFound += "<td class='active-status' style='white-space: nowrap;'><span class='label label-danger'>Banned</span><p class='ban-text'>" + users[user].banTill + "</p></td>"
                        } else {
                            usersFound += "<td class='suspended' style='white-space: nowrap;'><span class='label label-info'>Allowed</span></td>"
                        }
                        if (users[user].superUser && users[user].admin) {
                              usersFound += "<td class='active-status' style='white-space: nowrap;'><span class='label label-superUser'>SuperUser</span>"
                        } else if (users[user].admin && !users[user].superUser) {
                              usersFound += "<td class='active-status' style='white-space: nowrap;'><span class='label label-warning'>Admin</span>"
                        } else {
                              usersFound += "<td class='active-status' style='white-space: nowrap;'><span class='label label-normalUser'>Normal User</span>"
                        }
                        if (users[user].coreMember) {
                            usersFound += "<span class='label label-info meetup-session coreMember'>Core</span></td></tr>"
                        } else {
                            usersFound += "</td> </tr>"
                        }
                    }

                    $('#user-found').html(usersFound);

                    paginate(page, pages);

                    var paginationLinks = document.querySelectorAll('.paginate');

                    for (var i = 0; i < paginationLinks.length; i++) {
                        paginationLinks[i].addEventListener('click', function (event) {
                            var keyword = document.getElementById('search-text').value;
                            var filter = $('input[name="user-filter"]:checked').val();
                            slide(keyword, this.id, filter, pageSize);
                        });
                    }
                } else {
                    $('#user-found').html(
                        "<tr><td align='center' class='col-md-12' colspan='5'><i class='fa fa-database' aria-hidden='true'></i><span class='no-record-found'>Oops! No Record Found</span></td></tr>"
                    );

                    $('.pagination').html("");
                }
            },
            error: function (er) {
                $('#user-found').html(
                    "<tr><td align='center'></td><td align='center'>" + er.responseText + "</td><td align='center'></td><td align='center'></td></tr>"
                );

                $('.pagination').html("");

            }
        });
}
