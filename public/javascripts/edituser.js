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

    jsRoutes.controllers.UsersController.searchUser().ajax(
        {
            type: 'POST',
            processData: false,
            contentType: false,
            data: formData,
            success: function (data) {
                var userInfo = JSON.parse(data);
                var users = userInfo["users"];
                var page = userInfo["page"];
                var pages = userInfo["pages"];
                var usersFound = "";
                if (users.length > 0) {
                    for (var user = 0; user < users.length; user++) {
                        usersFound += "<tr><td align='center'>" +
                            "<a href='/user/update?email="+users[user].email+"' class='btn btn-default'>" +
                            "<em class='fa fa-pencil'></em>" +
                            "</a> " +
                            "<a href='' class='btn btn-danger delete'>" +
                            "<em class='fa fa-trash'></em>" +
                            "</a>" +
                            "</td>" +
                            "<td>" + users[user].email + "</td>"
                        if (users[user].active) {
                            usersFound += "<td class='active-status'>Active</td></tr>"
                        }
                        else {
                            usersFound += "<td class='suspended'>Suspended</td></tr>"
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


