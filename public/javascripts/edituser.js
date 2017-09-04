$(function () {
    $('#search-text').keyup(function () {
        var filter = $('input[name="user-filter"]:checked').val();
        slide(this.value, 1, filter);
    });

    $('.custom-checkbox').click(function () {
        var filter = $('input[name="user-filter"]:checked').val();
        slide($('#search-text').val(), 1, filter);
    });
});

function slide(keyword, pageNumber, filter) {
    var email = keyword;

    var formData = new FormData();
    formData.append("email", email);
    formData.append("page", pageNumber);
    formData.append("filter", filter);

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
                var usersFound = "";

                if (users.length > 0) {
                    for (var user = 0; user < users.length; user++) {

                        if (users[user].admin) {
                            usersFound += "<tr><td class='active-status'><span class='label label-warning'>Admin</span></td>"
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
                        usersFound += "<td>" + users[user].email + "</td>";
                        if (users[user].active) {
                            usersFound += "<td class='active-status'><span class='label label-success'>Active</span></td>"
                        } else {
                            usersFound += "<td class='suspended'><span class='label label-danger'>Suspended</span></td>"
                        }
                        if (users[user].ban) {
                            usersFound += "<td class='active-status'><span class='label label-danger'>Banned</span><p>" + users[user].banTill + "</p></td></tr>"
                        } else {
                            usersFound += "<td class='suspended'><span class='label label-info'>Allowed</span></td></tr>"
                        }
                    }

                    $('#user-found').html(usersFound);

                    paginate(page, pages);

                    var paginationLinks = document.querySelectorAll('.paginate');

                    for (var i = 0; i < paginationLinks.length; i++) {
                        paginationLinks[i].addEventListener('click', function (event) {
                            var keyword = document.getElementById('search-text').value;
                            var filter = $('input[name="user-filter"]:checked').val();
                            slide(keyword, this.id, filter);
                        });
                    }
                } else {
                    $('#user-found').html(
                        "<tr><td align='center' class='col-md-1'></td><td align='center' class='col-md-2'></td><td align='center' class='col-md-6'><i class='fa fa-database' aria-hidden='true'></i><span class='no-record-found'>Oops! No Record Found</span></td><td align='center' class='col-md-3'></td></tr>"
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
