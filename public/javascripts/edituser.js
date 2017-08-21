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
                        usersFound += "<tr><td align='center'>" +
                            "<a href='" + jsRoutes.controllers.UsersController.getByEmail(users[user].email)['url'] + "' class='btn btn-default'>" +
                            "<em class='fa fa-pencil'></em>" +
                            "</a> " +
                            "<a href='" + jsRoutes.controllers.UsersController.deleteUser(users[user].email)['url'] + "' class='btn btn-danger delete'>" +
                            "<em class='fa fa-trash'></em>" +
                            "</a>" +
                            "</td>" +
                            "<td>" + users[user].email + "</td>";

                        if (users[user].active) {
                            usersFound += "<td class='active-status'><span class='label label-info'>Subscribed</span></td></tr>"
                        } else {
                            usersFound += "<td class='suspended'><span class='label label-danger'>unsubscribed</span></td></tr>"
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
                        "<tr><td align='center'></td><td align='center'>Oops! No Record Found</td><td align='center'></td></tr>"
                    );

                    $('.pagination').html("");
                }
            },
            error: function (er) {
                $('#user-found').html(
                    "<tr><td align='center'></td><td align='center'>" + er.responseText + "</td><td align='center'></td></tr>"
                );

                $('.pagination').html("");

            }
        });
}
