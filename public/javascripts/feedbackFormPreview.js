function generatePreview() {

    var id = document.getElementById('feedbackFormId').value;

    jsRoutes.controllers.FeedbackFormsController.getFeedbackFormPreview().ajax(
        {
            type: "POST",
            processData: false,
            contentType: 'application/json',
            data: '{"id":"' + id + '"}',
            success: function (data) {

                var values = JSON.parse(data);

                if(values['status'] == "success") {

                    var formName = values['name'];
                    var questions = values['ques'];
                    var optionsLoad = "";

                    $('#feedFormName').html(formName);
                    $('#datao').html("");

                    for (var i = 0; i < questions.length; i++) {

                        var options = values[questions[i]];

                        for (var j = 0; j < options.length; j++) {

                            optionsLoad = optionsLoad + "<h2>" + options[j] + "</h2><br/>";
                        }

                        $('#datao').append(
                            "<h1>" + questions[i] + "</h1><br/>" + optionsLoad
                        );

                        optionsLoad = "";
                    }
                }
                else{

                    $('#datao').html("Sorry ! No such record exists");

                }
                $('#myModal').modal('toggle');

            },
            error: function (er) {

                $('#datao').html("Sorry ! No such record exists");
                $('#myModal').modal('toggle');
            }

        });

}