function slide() {
    $('#search-bar').animate({marginTop: '30px'}, 500);

    var email = document.getElementById('search-text').value;

    var errorCommon = "<div class='col-md-3'></div>" +
        "<div class='col-md-6'>" +
        "<div class='col-md-12'>" +
        "<div id='parent' style='width: 100% ;'>" +
        "<div class='card-body custom-manage-user-body-error custom-fadein'>" +
        "<div class='feedback-card-head stick-out manage-user-custom-stick-out'> &#9650</div>";

    if (email === "") {

        $('#found-user-details').html(
            errorCommon +
            "</div></div><div class='col-md-12'><p class='empty-email'>Field can't be empty !</p></div></div></div>" +
            "<div class='col-md-3'></div>"
        );
    }
    else {

        var formData = new FormData();
        formData.append("email", email);

        jsRoutes.controllers.UsersController.searchUser().ajax(
            {
                type: 'POST',
                processData: false,
                contentType: false,
                data: formData,
                success: function (data) {
                    var userINfo = JSON.parse(data);
                    var payloadHead = "<div class='col-md-3'></div>" +
                        "<div class='col-md-6'>" +
                        "<div class='col-md-12'>" +
                        "<div id='parent' style='width: 100% ;'>" +
                        "<div class='card-body custom-manage-user-body custom-fadein'>" +
                        "<div class='feedback-card-head stick-out manage-user-custom-stick-out'> &#9650</div>" +
                        "<div class='col-md-12 feedback-card-author manage-user-container-email'><p>" + userINfo.email + "</p></div>" +
                        "<input type='hidden' id='id' value='" + userINfo._id + "'/>" +
                        "<div class='col-md-12 suspend-ckb-outer'>" +
                        "<label class='checkbox-outer'>" +
                        "<p class='manage-user-checkbox-text'>Suspend</p>";

                    var payloadTail = "<span class='manage-user-label_text'></span>" +
                        "</label>" +
                        "</div>" +
                        "<div class='col-md-12'>" +
                        "<div class='col-md-2'></div>" +
                        "<div class='col-md-8 password-bar'>" +
                        "<input type='password' id='password-text' class='password-text'  placeholder='New Password'/>" +
                        "</div>" +
                        "<div class='col-md-2'></div>" +
                        "</div>" +
                        "<button type='button' onclick='updateDetails()' class='btn  feedback-card-button  manage-user-container-custom-btn' >Save</button>" +
                        "</div>" +
                        "</div>" +
                        "<div id='successmsg'></div>" +
                        "<div id='errmsg'></div>" +
                        "</div>" +
                        "</div>" +
                        "<div class='col-md-3'></div>";

                    var finalPayLoad = "";

                    if (userINfo.active == true) {
                        finalPayLoad += payloadHead + "<input type='checkbox' checked name='suspend' id='suspend' class='custom-checkbox' />" + payloadTail;
                    }
                    else {
                        finalPayLoad += payloadHead + "<input type='checkbox' name='suspend' id='suspend' class='custom-checkbox' value='false'/>" + payloadTail;

                    }

                    $('#found-user-details').html(finalPayLoad);


                },
                error: function (er) {
                    if (er.status == 400) {
                        $('#found-user-details').html(
                            errorCommon +
                            "</div></div><div class='col-md-12'><p class='empty-email'>Invalid Email</p></div></div></div>" +
                            "<div class='col-md-3'></div>"
                        );
                    }
                    else {
                        $('#found-user-details').html(
                            errorCommon +
                            "</div></div><div class='col-md-12'><p class='empty-email'>No record found !</p></div></div></div>" +
                            "<div class='col-md-3'></div>"
                        );
                    }

                }
            });
    }
}

function updateDetails() {

    var userid = document.getElementById('id').value;
    var suspended = !!document.getElementById('suspend').checked;
    var password = document.getElementById('password-text').value;

    var formData = new FormData();
    formData.append("id", userid);
    formData.append("active", suspended);
    formData.append("password", password);
    jsRoutes.controllers.UsersController.updateUser().ajax(
        {
            type: 'POST',
            processData: false,
            contentType: false,
            data: formData,
            success: function (data) {
                $("#errmsg").css("display", "none");
                $("#successmsg").css("display", "block");
                $('#successmsg').html(
                    "<div class='alert alert-success alert-dismissable fade in manage-user-alerts'>" +
                    "<a class='close' data-dismiss='alert' aria-label='close'>&times;</a>" +
                    "<strong>Success!</strong>User details successfully updated</div>"
                );
            },
            error: function (er) {
                $("#successmsg").css("display", "none");
                $("#errmsg").css("display", "block");
                if (er.status == 400) {
                    $('#errmsg').html(
                        "<div class='alert alert-danger alert-dismissable fade in manage-user-alerts'>" +
                        "<a class='close' data-dismiss='alert' aria-label='close'>&times;</a>" +
                        er.responseText + "</div>"
                    );
                }

            }


        });
}