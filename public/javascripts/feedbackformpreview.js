function generatePreview() {

    var id = document.getElementById('feedbackFormId').value;

    jsRoutes.controllers.FeedbackFormsController.getFeedbackFormPreview(id).ajax(
        {
            type: "GET",
            processData: false,
            contentType: 'application/json',
            success: function (data) {

                var values = JSON.parse(data);

                $('#formData').html("");
                var optionsLoaded = "";

                var questions = values['questions'];

                $('#feedFormName').html(values['name']);

                for (var questionNumber = 0; questionNumber < questions.length; questionNumber++) {
                    var options = questions[questionNumber]['options'];

                    for (var optionNumber = 0; optionNumber < options.length; optionNumber++) {
                        optionsLoaded += "<div class='col-md-12'><label class='preview-options'><i class='fa fa-circle-o' aria-hidden='true'></i> " + options[optionNumber] + "</label></div>"

                    }

                    $('#formData').append(
                        "<div class='row'>" +
                        "<div class='col-md-12'>" +
                        "<label class='preview-questions'>" + questions[questionNumber]['question'] + "</label>" +
                        "</div>" + optionsLoaded + "</div>"
                    );

                    optionsLoaded = "";

                }

                $('#myModal').modal('toggle');

            },
            error: function (er) {
                $('#formData').html("Sorry ! unable to load feedback form template");
                $('#myModal').modal('toggle');
            }

        });

}

