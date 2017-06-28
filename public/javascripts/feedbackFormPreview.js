function  hello() {

    jsRoutes.controllers.FeedbackFormsController.getFeedbackFormPreview().ajax(
        {
            type: "POST",
            processData: false,
            contentType: 'application/json',
            data: '{"id":"12345"}',
            success: function (data) {
                var values = JSON.parse(data);
                $('#datao').html(values['name']);
                $('#myModal').modal('toggle');

            },
            error: function (er) {

                 alert(er);

            }

        });




}