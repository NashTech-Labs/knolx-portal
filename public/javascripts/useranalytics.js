$(function(){
    ko.applyBindings(new UserAnalytics());
});

function UserAnalytics() {
    var self = this;
    self.emailList = ko.observableArray([]);
    self.sessionList = ko.observableArray([]);

    FetchEmailList(null);

    self.userDataHandler = function (email) {
        console.log("email--->" + email);
        jsRoutes.controllers.KnolxUserAnalysisController.userAnalysis(email).ajax({
            type: "POST",
            processData: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (values) {
                console.log("user data--->" + JSON.stringify(values));

            },error: function (er) {
                console.log(er);
            }
        })
    };

    $('#search-user').keyup(function () {
        FetchEmailList(this.value);
    });
    function FetchEmailList(email) {
        jsRoutes.controllers.KnolxUserAnalysisController.sendUserList(email).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    console.log(values);
                    self.emailList(values);
                }
            }
        )
    }


}