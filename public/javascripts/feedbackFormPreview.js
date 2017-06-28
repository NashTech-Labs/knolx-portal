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

                if (values['status'] == "failure") {

                    $('#formData').html("Sorry ! unable to load feedback form template");

                }
                else {

                    $('#formData').html("");
                    var optionsLoaded = "";

                    var feedbackFormJson = values['status'];
                    var questions = feedbackFormJson['questions'];

                    $('#feedFormName').html(feedbackFormJson['name']);

                    for (var questionNumber = 0; questionNumber < questions.length; questionNumber++) {
                        var options = questions[questionNumber]['options'];

                        for (var optionNumber = 0; optionNumber < options.length; optionNumber++) {
                            optionsLoaded += "<h4>" + options[optionNumber] + "</h4><br/>"
                        }

                        $('#formData').append(
                            "<h1>" + questions[questionNumber]['question'] + "</h1><br/>" + optionsLoaded
                        );

                        optionsLoaded = "";
                    }

                }

                $('#myModal').modal('toggle');

            },
            error: function (er) {

                $('#formData').html("Sorry ! unable to load feedback form template");
                $('#myModal').modal('toggle');
            }

        });

}