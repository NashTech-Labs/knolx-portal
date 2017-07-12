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
                        "<div class='col-md-12 suspend-ckb-outer'>" +
                        "<label class='checkbox-outer'>" +
                        "<p class='manage-user-checkbox-text'>Suspend</p>";

                    var payloadTail = "<span class='manage-user-label_text'></span>" +
                        "</label>" +
                        "</div>" +
                        "<div class='col-md-12'>" +
                        "<div class='col-md-2'></div>" +
                        "<div class='col-md-8 password-bar'>" +
                        "<input type='password' class='password-text' placeholder='New Password'/>" +
                        "</div>" +
                        "<div class='col-md-2'></div>" +
                        "</div>" +
                        "<button type='button' class='btn  feedback-card-button  manage-user-container-custom-btn' >Save</button>" +
                        "</div>" +
                        "</div>" +
                        "</div>" +
                        "</div>" +
                        "<div class='col-md-3'></div>";

                    var finalPayLoad = "";

                    if (userINfo.active == false) {
                        finalPayLoad += payloadHead + "<input type='checkbox' checked='checked' name='meetup' id='meetup' class='custom-checkbox' value='true'/>" + payloadTail;
                    }
                    else {
                        finalPayLoad += payloadHead + "<input type='checkbox' name='meetup' id='meetup' class='custom-checkbox' value='true'/>" + payloadTail;

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